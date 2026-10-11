package com.shootcat.react.sim

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

/**
 * Runs a [Session] on its own thread at a fixed rate of 60 steps per second (Leitfaden V3, 4.1.5) and
 * hands finished pictures to whoever draws them. The drawing side never sees a half-updated world: the
 * thread renders into a back buffer and swaps it with the ready one under a short lock.
 *
 * Input is posted as commands and carried out between two steps, so it never races the simulation.
 */
class SimRunner(val session: Session, backdrop: IntArray) {

    enum class Speed(val stepsPerSecond: Int) { PAUSE(0), NORMAL(60), FAST(180) }

    @Volatile
    var speed = Speed.NORMAL

    private val renderer = Renderer(session.world, backdrop)
    private val inbox = ConcurrentLinkedQueue<(Session) -> Unit>()

    val width get() = session.world.width
    val height get() = session.world.height
    val layerWidth get() = renderer.layerWidth
    val layerHeight get() = renderer.layerHeight

    /** One finished picture: the cells, and the soft haze and glow layers ([Renderer.renderLayers]). */
    class Frame(size: Int, layerSize: Int) {
        val pixels = IntArray(size)
        val haze = IntArray(layerSize)
        val light = IntArray(layerSize)
    }

    private val lock = Any()
    private fun newFrame() = Frame(session.world.size, renderer.layerWidth * renderer.layerHeight)
    private var back = newFrame()
    private var ready = newFrame()
    private var front = newFrame()
    private var fresh = false

    @Volatile
    private var running = false
    private var frameNumber = 0
    private var thread: Thread? = null

    /** What the performance overlay shows. */
    data class Stats(
        val stepsPerSecond: Float = 0f,
        val msPerStep: Float = 0f,
        val msPerRender: Float = 0f,
        val awakeChunks: Int = 0,
        val chunks: Int = 0,
        val step: Int = 0,
        val cost: Int = 0,
        val canUndo: Boolean = false,
    )

    @Volatile
    var stats = Stats()
        private set

    /** Sound cues of the last frames, summed up per [Event] until [takeEvents] reads them. */
    private val eventSums = IntArray(Event.COUNT)

    fun post(command: (Session) -> Unit) {
        inbox += command
        if (speed == Speed.PAUSE) thread?.let { LockSupport.unpark(it) }
    }

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "react-sim").apply {
            priority = Thread.NORM_PRIORITY + 1
            isDaemon = true
            start()
        }
    }

    fun stop() {
        running = false
        thread?.let {
            LockSupport.unpark(it)
            it.join(500)
        }
        thread = null
    }

    /** The newest finished picture, or null if nothing changed since the last call (UI thread). */
    fun takeFrame(): Frame? {
        synchronized(lock) {
            if (!fresh) return null
            val t = front
            front = ready
            ready = t
            fresh = false
            return front
        }
    }

    fun takeEvents(into: IntArray) {
        synchronized(eventSums) {
            eventSums.copyInto(into)
            eventSums.fill(0)
        }
    }

    /** Runs [block] on the simulation thread, between two steps, and waits for its result. */
    fun <T> runBlocking(block: (Session) -> T): T {
        val result = AtomicReference<Result<T>?>(null)
        val caller = Thread.currentThread()
        post { s ->
            result.set(runCatching { block(s) })
            LockSupport.unpark(caller)
        }
        while (result.get() == null) LockSupport.parkNanos(2_000_000)
        return result.get()!!.getOrThrow()
    }

    private fun loop() {
        val world = session.world
        var last = System.nanoTime()
        var acc = 0L
        var lastRender = 0L
        var stepCount = 0
        var stepNanos = 0L
        var renderNanos = 0L
        var renders = 0
        var windowStart = last
        var shownSps = 0f
        var shownStep = 0f
        var shownRender = 0f
        var dirty = true
        while (running) {
            val now = System.nanoTime()
            val dt = now - last
            last = now

            while (true) {
                val command = inbox.poll() ?: break
                command(session)
                dirty = true
            }

            val sps = speed.stepsPerSecond
            if (sps > 0) {
                acc += dt
                val stepNs = 1_000_000_000L / sps
                var k = 0
                while (acc >= stepNs && k < MAX_STEPS_PER_LOOP) {
                    val t0 = System.nanoTime()
                    session.tick()
                    stepNanos += System.nanoTime() - t0
                    stepCount++
                    synchronized(eventSums) {
                        for (e in 0 until Event.COUNT) eventSums[e] += world.events[e]
                    }
                    world.events.fill(0)
                    acc -= stepNs
                    k++
                    dirty = true
                }
                // too slow for real time: run slower instead of piling up steps
                if (k == MAX_STEPS_PER_LOOP) acc = 0L
            } else {
                acc = 0L
            }

            val after = System.nanoTime()
            if (dirty && after - lastRender >= FRAME_NANOS) {
                renderer.render(back.pixels, frameNumber++)
                renderer.renderLayers(back.haze, back.light)
                synchronized(lock) {
                    val t = back
                    back = ready
                    ready = t
                    fresh = true
                }
                renderNanos += System.nanoTime() - after
                renders++
                lastRender = after
                dirty = false
            }

            if (after - windowStart >= 1_000_000_000L) {
                val secs = (after - windowStart) / 1e9f
                shownSps = stepCount / secs
                shownStep = if (stepCount > 0) stepNanos / 1e6f / stepCount else 0f
                shownRender = if (renders > 0) renderNanos / 1e6f / renders else shownRender
                stepCount = 0
                stepNanos = 0L
                renderNanos = 0L
                renders = 0
                windowStart = after
            }
            stats = Stats(
                stepsPerSecond = shownSps,
                msPerStep = shownStep,
                msPerRender = shownRender,
                awakeChunks = world.awakeChunks,
                chunks = world.chunksX * world.chunksY,
                step = world.step,
                cost = session.cost(),
                canUndo = session.canUndo,
            )

            // sleep until the next step is due (or a frame, or, when paused, until input arrives)
            val wait = if (sps > 0) {
                (1_000_000_000L / sps - acc).coerceIn(0L, FRAME_NANOS)
            } else {
                if (dirty) FRAME_NANOS else 50_000_000L
            }
            if (wait > 200_000L && inbox.isEmpty()) LockSupport.parkNanos(wait)
        }
    }

    private companion object {
        const val MAX_STEPS_PER_LOOP = 4
        const val FRAME_NANOS = 16_000_000L
    }
}
