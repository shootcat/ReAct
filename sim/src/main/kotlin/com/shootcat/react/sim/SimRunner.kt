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

    /**
     * One finished picture: the cells, the soft haze and glow layers and the flame layer. Only the cells from
     * ([x0], [y0]) to ([x1], [y1]) were drawn: the part of the world on screen.
     */
    class Frame(size: Int, layerSize: Int, flameSize: Int) {
        val pixels = IntArray(size)
        val haze = IntArray(layerSize)
        val light = IntArray(layerSize)
        val flames = IntArray(flameSize)
        var x0 = 0
        var y0 = 0
        var x1 = 0
        var y1 = 0
    }

    /**
     * The part of the world on screen (x0, y0, x1, y1), set by whoever draws; only that part is rendered.
     * The whole world until it is set.
     */
    @Volatile
    var viewport: IntArray? = null
        set(value) {
            field = value
            if (speed == Speed.PAUSE) thread?.let { LockSupport.unpark(it) }
        }

    /** The heat picture instead of the materials (3b.7); switching redraws at once, also in the pause. */
    @Volatile
    var thermal = false
        set(value) {
            if (field == value) return
            field = value
            redraw = true
            thread?.let { LockSupport.unpark(it) }
        }

    /** Light up every object the hand could take (3b.3), while the hand is the tool. */
    @Volatile
    var highlightGrabbable = false
        set(value) {
            if (field == value) return
            field = value
            redraw = true
            thread?.let { LockSupport.unpark(it) }
        }

    @Volatile
    private var redraw = false
    private val grabbableMask = BooleanArray(session.world.size)
    private var highlightAge = Int.MAX_VALUE

    /** What a finger held on the world found out (3b.1), and a counter that changes with each answer. */
    @Volatile
    var inspected: Session.CellInfo? = null
        private set

    @Volatile
    var inspectedCount = 0
        private set

    /** Looks at cell (x, y) between two steps, without it being an action; the answer comes in [inspected]. */
    fun inspect(x: Int, y: Int) = post { s ->
        inspected = s.inspect(x, y)
        inspectedCount++
    }

    private val lock = Any()
    val flameWidth get() = renderer.flameWidth
    val flameHeight get() = renderer.flameHeight
    private fun newFrame() = Frame(session.world.size, renderer.layerWidth * renderer.layerHeight, renderer.flameWidth * renderer.flameHeight)
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
        /** What the player has (Leitfaden V3, 4.3). */
        val supply: Session.Supply = Session.Supply.UNLIMITED,
        val bucketMaterial: Int = MaterialTable.AIR,
        val bucketAmount: Int = 0,
        val bucketSize: Int = 0,
        val bucketTemp: Float = 0f,
        /** What the hand holds (never changed once made, safe to read from the UI). */
        val held: Session.Held? = null,
        val stock: List<Int> = emptyList(),
        val notice: String = "",
        val noticeCount: Int = 0,
        val weather: Weather = Weather(),
    )

    /** The weather of the world (3c), for the weather controls of the sandbox. */
    data class Weather(
        /** The condensation height, -1: no clouds. */
        val cloudBase: Int = -1,
        val wind: Float = 0f,
        val gust: Float = 0f,
        val random: Boolean = false,
        val ambient: Float = 20f,
        /** A thunderstorm is going on. */
        val storm: Boolean = false,
    )

    @Volatile
    var stats = Stats()
        private set

    /** Watches for discoveries (Leitfaden V3, 4.3), set by the app; new ones wait in [found] until [takeFound]. */
    @Volatile
    var watch: DiscoveryWatch? = null
    private val found = ConcurrentLinkedQueue<Discoveries.Entry>()
    private var stepsSinceWatch = 0

    /** Discoveries made since the last call. */
    fun takeFound(): List<Discoveries.Entry> {
        val out = ArrayList<Discoveries.Entry>()
        while (true) out += found.poll() ?: break
        return out
    }

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
        var shown: IntArray? = null
        while (running) {
            val now = System.nanoTime()
            val dt = now - last
            last = now

            while (true) {
                val command = inbox.poll() ?: break
                command(session)
                dirty = true
                highlightAge = Int.MAX_VALUE
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
                    if (++stepsSinceWatch >= WATCH_EVERY) {
                        stepsSinceWatch = 0
                        watch?.let { found.addAll(it.check(world)) }
                    }
                }
                // too slow for real time: run slower instead of piling up steps
                if (k == MAX_STEPS_PER_LOOP) acc = 0L
            } else {
                acc = 0L
            }

            val view = viewport
            if (view !== shown) dirty = true
            if (redraw) {
                redraw = false
                dirty = true
                highlightAge = Int.MAX_VALUE
            }
            renderer.thermal = thermal
            if (highlightGrabbable) {
                // what the hand could take changes slowly: worked out about twice a second, or after input
                if (dirty && highlightAge >= HIGHLIGHT_EVERY) {
                    session.grabbable(grabbableMask)
                    highlightAge = 0
                }
                highlightAge++
                renderer.highlight = grabbableMask
            } else {
                renderer.highlight = null
            }
            val after = System.nanoTime()
            if (dirty && after - lastRender >= FRAME_NANOS) {
                shown = view
                val w = world.width
                val h = world.height
                back.x0 = (view?.get(0) ?: 0).coerceIn(0, w - 1)
                back.y0 = (view?.get(1) ?: 0).coerceIn(0, h - 1)
                back.x1 = (view?.get(2) ?: (w - 1)).coerceIn(back.x0, w - 1)
                back.y1 = (view?.get(3) ?: (h - 1)).coerceIn(back.y0, h - 1)
                renderer.render(back.pixels, frameNumber++, speed != Speed.PAUSE, back.x0, back.y0, back.x1, back.y1)
                renderer.renderLayers(back.haze, back.light)
                renderer.renderFlames(back.flames)
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
                supply = session.supply,
                bucketMaterial = session.bucketMaterial,
                bucketAmount = session.bucketAmount,
                bucketSize = session.bucketSize,
                bucketTemp = session.bucketTemp,
                held = session.held,
                stock = if (stats.stock.size == session.stock.size && stats.stock.indices.all { stats.stock[it] == session.stock[it] }) stats.stock else session.stock.toList(),
                notice = session.notice,
                noticeCount = session.noticeCount,
                weather = weatherOf(world, stats.weather),
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

    /** The weather as the controls show it; the old one again while nothing changed, so the UI sees no change. */
    private fun weatherOf(w: World, old: Weather): Weather {
        val storm = w.storm > 0
        if (old.cloudBase == w.cloudBase && old.wind == w.wind && old.gust == w.gust && old.random == w.randomWeather &&
            old.ambient == w.ambient && old.storm == storm
        ) {
            return old
        }
        return Weather(w.cloudBase, w.wind, w.gust, w.randomWeather, w.ambient, storm)
    }

    private companion object {
        const val MAX_STEPS_PER_LOOP = 4

        /** Loops between two looks at what the hand could take. */
        const val HIGHLIGHT_EVERY = 30
        const val WATCH_EVERY = 30
        const val FRAME_NANOS = 16_000_000L
    }
}
