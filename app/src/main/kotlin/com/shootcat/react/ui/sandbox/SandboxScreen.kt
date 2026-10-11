package com.shootcat.react.ui.sandbox

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.audio.SoundManager
import com.shootcat.react.data.Settings
import com.shootcat.react.sim.Benchmark
import com.shootcat.react.sim.Discoveries
import com.shootcat.react.sim.Event
import com.shootcat.react.sim.Kind
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Session
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.geometry.Size
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** The player's tools (Leitfaden V3, 4.3) and what was procured: categories of their own in the bar. */
private const val TOOLS = "Werkzeug"
private const val STORE = "Lager"

/** The materials in the bar, by kind. Rock is level ground and never in the player's hands (Leitfaden V3, 3.1.6). */
private val CATEGORIES = listOf(
    TOOLS to emptyList(),
    "Lose" to listOf("sand", "earth", "gravel", "clay", "salt", "snow", "rubble", "ore_rubble", "charcoal", "ash"),
    "Flüssig" to listOf("water", "saltwater", "lava", "molten_metal", "steam", "smoke"),
    "Bauen" to listOf("wood", "brick", "stone", "glass", "metal", "ice"),
    "Hitze" to listOf("fire", "ember", "lava"),
    "Leben" to listOf("seed", "grass", "moss", "vine", "fish", "firefly", "worm", "bird"),
)

/** Brush radii in cells; 0 places a single particle. */
private val BRUSHES = listOf(0, 1, 3, 6, 10)

/** How the finger builds (Leitfaden V3, 4.1): paint, a line, a filled rectangle, a frame. */
enum class BuildMode { BRUSH, LINE, RECT, FRAME }

/**
 * The free sandbox (Leitfaden V3, Phase 1 and 4.7): the world drawn as scaled-up pixels, painting with one
 * finger, zoom and pan with two, pause / normal / fast, undo per stroke, reset, and the bar of materials.
 */
@Composable
fun SandboxScreen(
    runner: SimRunner,
    materials: MaterialTable,
    settings: Settings,
    tool: Int,
    mode: BuildMode,
    sound: SoundManager,
    onTool: (Int) -> Unit,
    onMode: (BuildMode) -> Unit,
    onSettings: (Settings) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBook: () -> Unit,
    onDiscovered: (List<Discoveries.Entry>) -> Unit,
    onBack: () -> Unit,
) {
    var speed by remember { mutableStateOf(runner.speed) }
    var stats by remember { mutableStateOf(runner.stats) }
    var menu by remember { mutableStateOf(false) }
    var benchmark by remember { mutableStateOf<String?>(null) }
    var measuring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    /** Short notices over the world, one after the other: discoveries and what the hand or bucket did. */
    val notices = remember { ArrayDeque<String>() }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runner) {
        val events = IntArray(Event.COUNT)
        var lastNotice = runner.stats.noticeCount
        var shownFor = 0
        while (true) {
            delay(120)
            stats = runner.stats
            runner.takeEvents(events)
            playEvents(sound, events)
            val found = runner.takeFound()
            if (found.isNotEmpty()) {
                onDiscovered(found)
                found.forEach { notices.addLast("Neu: ${it.sentence}") }
                sound.play("condense", 0.6f)
            }
            if (stats.noticeCount != lastNotice) {
                lastNotice = stats.noticeCount
                if (stats.notice.isNotEmpty()) notices.addLast(stats.notice)
            }
            // each notice stays about 2.5 seconds
            if (notice != null && ++shownFor >= NOTICE_TICKS) notice = null
            if (notice == null && notices.isNotEmpty()) {
                notice = notices.removeFirst()
                shownFor = 0
            }
        }
    }

    fun measure() {
        menu = false
        measuring = true
        scope.launch {
            val device = "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), " +
                "${Runtime.getRuntime().availableProcessors()} Kerne"
            val text = withContext(Dispatchers.Default) {
                runner.runBlocking { s ->
                    val results = Benchmark.runAll(materials, s.width, s.height, steps = 300)
                    val render = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, s.width, s.height))
                    val paused = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, s.width, s.height), paused = true)
                    Benchmark.report(results, render, device, paused)
                }
            }
            Log.i("REACT", text)
            measuring = false
            benchmark = text
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Spielplatz",
            overline = if (stats.supply == Session.Supply.PROCURE) "Nur Beschaffen · Eimer ${stats.bucketAmount}/${stats.bucketSize}" else "Kosten ${stats.cost} · Vorrat ∞",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            RoundIconButton(Glyph.UNDO, "Rückgängig", { runner.post { it.undo() } }, size = 44.dp, enabled = stats.canUndo)
            RoundIconButton(Glyph.RESET, "Neu beginnen", { runner.post { it.reset() } }, size = 44.dp)
            Box {
                RoundIconButton(Glyph.GEAR, "Menü", { menu = true }, size = 44.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.surfaceHigh) {
                    val procuring = stats.supply == Session.Supply.PROCURE
                    DropdownMenuItem(
                        text = { Text(if (procuring) "Modus: unendlicher Vorrat" else "Modus: nur Beschaffen", color = Palette.text) },
                        onClick = {
                            menu = false
                            val next = if (procuring) Session.Supply.UNLIMITED else Session.Supply.PROCURE
                            runner.post { it.setSupply(next) }
                            if (next == Session.Supply.PROCURE) onTool(Session.HAND)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Entdeckungsbuch", color = Palette.text) },
                        onClick = {
                            menu = false
                            onOpenBook()
                        },
                    )
                    DropdownMenuItem(text = { Text("Leistung messen", color = Palette.text) }, onClick = ::measure)
                    DropdownMenuItem(
                        text = { Text(if (settings.performance) "Leistungsanzeige aus" else "Leistungsanzeige an", color = Palette.text) },
                        onClick = {
                            menu = false
                            onSettings(settings.copy(performance = !settings.performance))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Alles leeren", color = Palette.text) },
                        onClick = {
                            menu = false
                            runner.post { it.clear() }
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Einstellungen", color = Palette.text) },
                        onClick = {
                            menu = false
                            onOpenSettings()
                        },
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            val radius = if (settings.brush in BRUSHES) settings.brush else 3
            WorldView(runner, tool, radius, mode, materials, sound, stats.held, Modifier.fillMaxSize())
            if (settings.performance) PerformanceOverlay(stats, Modifier.align(Alignment.TopStart).padding(8.dp))
            notice?.let { NoticeBanner(it, Modifier.align(Alignment.TopCenter).padding(top = 40.dp, start = 16.dp, end = 16.dp)) }
            if (speed == SimRunner.Speed.PAUSE) {
                Text(
                    "Pause",
                    color = Palette.text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xAA0B0F15))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SpeedSelector(speed, Modifier.weight(1f)) {
                speed = it
                runner.speed = it
            }
            ModeSelector(mode, onMode)
        }
        // procuring: only the tools and what is in store (Leitfaden V3, 4.3)
        val categories = if (stats.supply == Session.Supply.PROCURE) listOf(TOOLS to emptyList(), STORE to emptyList()) else CATEGORIES
        var chosen by rememberSaveable {
            mutableStateOf(if (tool <= Session.SCOOP) TOOLS else CATEGORIES.firstOrNull { (_, ids) -> ids.any { materials.idOrNull(it) == tool } }?.first ?: "Lose")
        }
        val category = categories.indexOfFirst { it.first == chosen }.coerceAtLeast(0)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryTabs(categories.map { it.first }, category, Modifier.weight(1f)) { chosen = categories[it].first }
            BrushSelector(settings.brush) { onSettings(settings.copy(brush = it)) }
        }
        when (categories[category].first) {
            TOOLS -> ToolBar(materials, stats, tool, onTool) { runner.post { it.store() } }
            STORE -> StoreBar(materials, stats, tool, onTool)
            else -> MaterialBar(materials, categories[category].second, tool, onTool)
        }
    }

    if (measuring) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Palette.surface,
            title = { Text("Messung läuft", color = Palette.text) },
            text = { Text("Leere, voll aktive und belebte Welt mit je 300 Schritten. Das dauert einige Sekunden.", color = Palette.textDim) },
            confirmButton = {},
        )
    }
    benchmark?.let { text ->
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { benchmark = null },
            containerColor = Palette.surface,
            title = { Text("Leistung auf diesem Gerät", color = Palette.text) },
            text = { Text(text, color = Palette.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.testTag("benchmark")) },
            confirmButton = {
                TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Kopieren") }
            },
            dismissButton = {
                TextButton(onClick = { benchmark = null }) { Text("Schließen") }
            },
        )
    }
}

/** What happened in the world, as sounds: crackling fire, hissing water, boiling, setting lava. */
private fun playEvents(sound: SoundManager, e: IntArray) {
    fun volume(n: Int) = (0.35f + 0.15f * ln(n.toFloat())).coerceIn(0.3f, 1f)
    if (e[Event.EXTINGUISH] > 0) sound.play("hiss", volume(e[Event.EXTINGUISH]))
    if (e[Event.SOLIDIFY] > 0) sound.play("sizzle", volume(e[Event.SOLIDIFY]))
    if (e[Event.BOIL] > 2) sound.play("boil", volume(e[Event.BOIL]))
    if (e[Event.IGNITE] > 0) sound.play("whoosh", volume(e[Event.IGNITE]))
    if (e[Event.BURNT_OUT] > 0) sound.play("crackle", volume(e[Event.BURNT_OUT]))
    if (e[Event.MELT] > 0) sound.play("melt", volume(e[Event.MELT]))
    if (e[Event.CONDENSE] > 3) sound.play("condense", 0.5f)
    if (e[Event.SOAK] > 3) sound.play("soak", 0.4f)
}

@Composable
private fun WorldView(
    runner: SimRunner,
    tool: Int,
    radius: Int,
    mode: BuildMode,
    materials: MaterialTable,
    sound: SoundManager,
    held: Session.Held?,
    modifier: Modifier,
) {
    val w = runner.width
    val h = runner.height
    val lw = runner.layerWidth
    val lh = runner.layerHeight
    val bitmap = remember(runner) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    val hazeBitmap = remember(runner) { Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888) }
    val lightBitmap = remember(runner) { Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888) }
    val fw = runner.flameWidth
    val fh = runner.flameHeight
    val flameBitmap = remember(runner) { Bitmap.createBitmap(fw, fh, Bitmap.Config.ARGB_8888) }
    val flameImage = remember(flameBitmap) { flameBitmap.asImageBitmap() }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val hazeImage = remember(hazeBitmap) { hazeBitmap.asImageBitmap() }
    val lightImage = remember(lightBitmap) { lightBitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }

    // picks up each finished picture of the simulation thread; drawing follows on the next frame
    LaunchedEffect(runner) {
        while (true) {
            val f = runner.takeFrame()
            if (f != null) {
                bitmap.setPixels(f.pixels, 0, w, 0, 0, w, h)
                hazeBitmap.setPixels(f.haze, 0, lw, 0, 0, lw, lh)
                lightBitmap.setPixels(f.light, 0, lw, 0, 0, lw, lh)
                flameBitmap.setPixels(f.flames, 0, fw, 0, 0, fw, fh)
                frames++
            }
            delay(POLL_MILLIS)
        }
    }

    var view by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val currentTool by rememberUpdatedState(tool)
    val currentRadius by rememberUpdatedState(radius)
    val currentMode by rememberUpdatedState(mode)

    /** The shape being built: start and end cell, shown before the finger lets go (4.1.5). */
    var preview by remember { mutableStateOf<Pair<Pair<Int, Int>, Pair<Int, Int>>?>(null) }

    /** Where the held object would be put down, while the finger is on the world. */
    var handAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun scale(): Float = min(view.width / w.toFloat(), view.height / h.toFloat()) * zoom
    fun origin(): Offset {
        val s = scale()
        return Offset((view.width - w * s) / 2f + pan.x, (view.height - h * s) / 2f + pan.y)
    }
    fun clampPan(p: Offset): Offset {
        val s = scale()
        val mx = max(0f, (w * s - view.width) / 2f)
        val my = max(0f, (h * s - view.height) / 2f)
        return Offset(p.x.coerceIn(-mx, mx), p.y.coerceIn(-my, my))
    }
    fun cell(p: Offset): Pair<Int, Int> {
        val o = origin()
        val s = scale()
        return ((p.x - o.x) / s).toInt().coerceIn(0, w - 1) to ((p.y - o.y) / s).toInt().coerceIn(0, h - 1)
    }

    Canvas(
        modifier
            .onSizeChanged { view = it }
            .testTag("world")
            .semantics { contentDescription = "Welt" }
            .pointerInput(runner) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    var painting = false
                    var transforming = false
                    var last = cell(start)

                    /** Two fingers: zoom around the point between them and follow them. */
                    fun transform(e: PointerEvent) {
                        val centroid = e.calculateCentroid()
                        val factor = e.calculateZoom()
                        val panBy = e.calculatePan()
                        val before = origin()
                        val s0 = scale()
                        zoom = (zoom * factor).coerceIn(1f, MAX_ZOOM)
                        val s1 = scale()
                        // keep the point under the fingers where it is, then follow the fingers
                        val worldPoint = (centroid - before) / s0
                        val newOrigin = centroid - worldPoint * s1 + panBy
                        val centred = Offset((view.width - w * s1) / 2f, (view.height - h * s1) / 2f)
                        pan = clampPan(newOrigin - centred)
                        e.changes.forEach { it.consume() }
                    }

                    if (currentTool == Session.HAND) {
                        // the hand acts where the finger lets go: picks up there, or puts down what it holds
                        var at = cell(start)
                        handAt = at
                        var cancelled = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                cancelled = true
                                handAt = null
                                transform(e)
                                continue
                            }
                            if (cancelled) continue
                            at = cell(pressed.first().position)
                            handAt = at
                            pressed.first().consume()
                        }
                        handAt = null
                        if (!cancelled) {
                            val (x, y) = at
                            runner.post {
                                it.press(x, y, Session.HAND, 0)
                                it.release()
                            }
                            sound.play("place", 0.5f)
                        }
                        return@awaitEachGesture
                    }

                    if (currentMode != BuildMode.BRUSH && currentTool >= Session.ERASER) {
                        // a shape: drag from one corner (or end) to the other, see it, let go to build it
                        val from = cell(start)
                        preview = from to from
                        var cancelled = false
                        while (true) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                cancelled = true
                                preview = null
                                transform(e)
                                continue
                            }
                            if (cancelled) continue
                            preview = from to cell(pressed.first().position)
                            pressed.first().consume()
                        }
                        val shape = preview
                        preview = null
                        if (!cancelled && shape != null) {
                            val (a, b) = shape
                            val t = currentTool
                            val r = currentRadius
                            val kind = when (currentMode) {
                                BuildMode.LINE -> Session.ShapeKind.LINE
                                BuildMode.RECT -> Session.ShapeKind.RECT
                                else -> Session.ShapeKind.FRAME
                            }
                            runner.post { it.shape(kind, a.first, a.second, b.first, b.second, t, r) }
                            strokeSound(sound, materials, t)
                        }
                        return@awaitEachGesture
                    }

                    fun begin() {
                        painting = true
                        val (x, y) = cell(start)
                        last = x to y
                        val t = currentTool
                        val r = currentRadius
                        runner.post { it.press(x, y, t, r) }
                        strokeSound(sound, materials, t)
                    }

                    // a short moment to tell a stroke from the start of a two-finger gesture
                    val early = withTimeoutOrNull(STROKE_DELAY_MILLIS) {
                        var kind = 0
                        while (kind == 0) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.count { it.pressed }
                            kind = when {
                                pressed == 0 -> TAP
                                pressed >= 2 -> TWO
                                (e.changes.first().position - start).getDistance() > viewConfiguration.touchSlop -> MOVED
                                else -> 0
                            }
                        }
                        kind
                    }
                    when (early) {
                        TAP -> {
                            begin()
                            runner.post { it.release() }
                            return@awaitEachGesture
                        }
                        TWO -> transforming = true
                        else -> begin()
                    }

                    while (true) {
                        val e = awaitPointerEvent()
                        val pressed = e.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size >= 2) {
                            if (painting) {
                                runner.post { it.release() }
                                painting = false
                            }
                            transforming = true
                            transform(e)
                            continue
                        }
                        if (transforming) continue
                        val c = pressed.first()
                        val next = cell(c.position)
                        if (next != last) {
                            last = next
                            val (x, y) = next
                            runner.post { it.drag(x, y) }
                        }
                        c.consume()
                    }
                    if (painting) runner.post { it.release() }
                }
            },
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frames
        val s = scale()
        val o = origin()
        val dstOffset = IntOffset(o.x.roundToInt(), o.y.roundToInt())
        val dstSize = IntSize((w * s).roundToInt(), (h * s).roundToInt())
        drawImage(image, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.None)
        drawImage(hazeImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        drawImage(flameImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        drawImage(lightImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        preview?.let { (a, b) -> drawPreview(a, b, mode, radius, tool, materials, o, s) }
        val h = held
        handAt?.let { (x, y) -> if (h != null) drawHeld(h, x, y, materials, o, s) }
    }
}

/** The held object where it would be put down, half transparent. */
private fun DrawScope.drawHeld(h: Session.Held, x: Int, y: Int, materials: MaterialTable, o: Offset, s: Float) {
    for (k in 0 until h.size) {
        val c = Color(materials.color[h.mat[k].toInt()]).copy(alpha = 0.65f)
        drawRect(c, Offset(o.x + (x + h.dx[k]) * s, o.y + (y + h.dy[k]) * s), Size(s, s))
    }
}

/** A short notice over the world. */
@Composable
private fun NoticeBanner(text: String, modifier: Modifier) {
    Text(
        text,
        color = Palette.text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xE0141C28))
            .border(1.dp, Palette.accent, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("notice"),
    )
}

/** Bucket and hand (Leitfaden V3, 4.3), with what they hold; and the button that puts a held object into store. */
@Composable
private fun ToolBar(materials: MaterialTable, stats: SimRunner.Stats, tool: Int, onTool: (Int) -> Unit, onStore: () -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("tool_bar")
                .clipToBounds()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val bucket = if (stats.bucketAmount > 0) "${materials.spec(stats.bucketMaterial).name} ${stats.bucketAmount}" else "leer"
            MaterialChip("Schöpfen", bucket, tool == Session.SCOOP, { onTool(Session.SCOOP) }) { BucketIcon(stats, materials, false) }
            MaterialChip("Gießen", "${stats.bucketAmount}/${stats.bucketSize}", tool == Session.POUR, { onTool(Session.POUR) }) { BucketIcon(stats, materials, true) }
            val held = stats.held
            MaterialChip("Hand", if (held != null) "${materials.spec(held.main).name} ${held.size}" else "leer", tool == Session.HAND, { onTool(Session.HAND) }) {
                HandIcon(if (held != null) Color(materials.color[held.main]) else null)
            }
            if (held != null) {
                val splits = materials.splitInto[held.main] != held.main
                MaterialChip(if (splits) "Zu Brettern" else "Ins Lager", "aus der Hand", false, onStore) {
                    Swatch(materials.color[materials.splitInto[held.main]], 0.1f, held.main)
                }
            }
        }
    }
}

/** What was procured, to build with: every material in store and how much of it. */
@Composable
private fun StoreBar(materials: MaterialTable, stats: SimRunner.Stats, tool: Int, onTool: (Int) -> Unit) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("store_bar")
                .clipToBounds()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            val inStore = stats.stock.indices.filter { stats.stock[it] > 0 }
            if (inStore.isEmpty()) {
                Text(
                    "Leer. Greife mit der Hand einen Stamm, ein Stück Stein, Metall oder Glas und lege es ins Lager.",
                    color = Palette.textDim,
                    fontSize = 12.sp,
                    modifier = Modifier.width(320.dp).padding(8.dp),
                )
            }
            for (m in inStore) {
                MaterialChip(materials.spec(m).name, "${stats.stock[m]} Zellen", tool == m, { onTool(m) }) {
                    Swatch(materials.color[m], materials.colorVariance[m], m)
                }
            }
        }
    }
}

/** A bucket, filled up to how full it is, tipped over for pouring. */
@Composable
private fun BucketIcon(stats: SimRunner.Stats, materials: MaterialTable, tipped: Boolean) {
    Canvas(Modifier.size(30.dp)) {
        val u = size.width
        val fill = if (stats.bucketSize > 0) stats.bucketAmount / stats.bucketSize.toFloat() else 0f
        val content = if (stats.bucketAmount > 0) Color(materials.color[stats.bucketMaterial]) else Color.Transparent
        if (tipped) {
            drawRect(Palette.textDim, Offset(u * 0.1f, u * 0.3f), Size(u * 0.6f, u * 0.4f), style = Stroke(width = u * 0.08f))
            drawRect(content, Offset(u * 0.72f, u * 0.5f), Size(u * 0.18f, u * 0.45f * fill.coerceAtLeast(0.15f)))
        } else {
            drawRect(content, Offset(u * 0.24f, u * (0.85f - 0.6f * fill)), Size(u * 0.52f, u * 0.6f * fill))
            drawRect(Palette.textDim, Offset(u * 0.2f, u * 0.2f), Size(u * 0.6f, u * 0.68f), style = Stroke(width = u * 0.08f))
        }
    }
}

/** An open hand, holding a block of what it carries. */
@Composable
private fun HandIcon(holding: Color?) {
    Canvas(Modifier.size(30.dp)) {
        val u = size.width
        for (k in 0 until 4) drawLine(Palette.textDim, Offset(u * (0.25f + k * 0.16f), u * 0.55f), Offset(u * (0.25f + k * 0.16f), u * 0.15f), strokeWidth = u * 0.1f, cap = StrokeCap.Round)
        drawCircle(Palette.textDim, u * 0.28f, Offset(u * 0.5f, u * 0.68f))
        if (holding != null) drawRect(holding, Offset(u * 0.3f, u * 0.5f), Size(u * 0.4f, u * 0.3f))
    }
}

/** The shape about to be built, half transparent in the colour of its material (4.1.5). */
private fun DrawScope.drawPreview(
    a: Pair<Int, Int>,
    b: Pair<Int, Int>,
    mode: BuildMode,
    radius: Int,
    tool: Int,
    materials: MaterialTable,
    o: Offset,
    s: Float,
) {
    val color = if (tool == Session.ERASER) Color.White.copy(alpha = 0.5f) else Color(materials.color[tool]).copy(alpha = 0.6f)
    val left = minOf(a.first, b.first)
    val top = minOf(a.second, b.second)
    val width = (abs(a.first - b.first) + 1) * s
    val height = (abs(a.second - b.second) + 1) * s
    when (mode) {
        BuildMode.LINE -> drawLine(
            color,
            Offset(o.x + (a.first + 0.5f) * s, o.y + (a.second + 0.5f) * s),
            Offset(o.x + (b.first + 0.5f) * s, o.y + (b.second + 0.5f) * s),
            strokeWidth = (2 * radius + 1) * s,
            cap = StrokeCap.Square,
        )
        BuildMode.RECT -> drawRect(color, Offset(o.x + left * s, o.y + top * s), Size(width, height))
        BuildMode.FRAME -> {
            val t = minOf((radius + 1) * s, width / 2f, height / 2f)
            drawRect(color, Offset(o.x + left * s + t / 2f, o.y + top * s + t / 2f), Size(width - t, height - t), style = Stroke(width = t))
        }
        BuildMode.BRUSH -> {}
    }
    // a thin outline makes small shapes visible on any background
    drawRect(Color.White.copy(alpha = 0.35f), Offset(o.x + left * s, o.y + top * s), Size(width, height), style = Stroke(width = 1f))
}

/** Paint, line, filled rectangle, frame: one small icon each. */
@Composable
private fun ModeSelector(current: BuildMode, onSelect: (BuildMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.surfaceHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for ((m, label) in listOf(BuildMode.BRUSH to "Pinsel", BuildMode.LINE to "Linie", BuildMode.RECT to "Rechteck", BuildMode.FRAME to "Rahmen")) {
            val selected = m == current
            val tint = if (selected) Palette.background else Palette.text
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.accent else Palette.surfaceHigh)
                    .clickable { onSelect(m) }
                    .semantics { contentDescription = label }
                    .testTag("mode_$label"),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val u = size.width
                    when (m) {
                        BuildMode.BRUSH -> {
                            drawCircle(tint, u * 0.22f, Offset(u * 0.38f, u * 0.6f))
                            drawCircle(tint, u * 0.12f, Offset(u * 0.72f, u * 0.3f))
                        }
                        BuildMode.LINE -> drawLine(tint, Offset(u * 0.1f, u * 0.9f), Offset(u * 0.9f, u * 0.1f), strokeWidth = u * 0.18f, cap = StrokeCap.Round)
                        BuildMode.RECT -> drawRect(tint, Offset(u * 0.1f, u * 0.2f), Size(u * 0.8f, u * 0.6f))
                        BuildMode.FRAME -> drawRect(tint, Offset(u * 0.14f, u * 0.24f), Size(u * 0.72f, u * 0.52f), style = Stroke(width = u * 0.14f))
                    }
                }
            }
        }
    }
}

/** The kinds of material in the bar. */
@Composable
private fun CategoryTabs(names: List<String>, current: Int, modifier: Modifier, onSelect: (Int) -> Unit) {
    // scrolls on narrow screens instead of squeezing the last tab to nothing
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((k, name) in names.withIndex()) {
            val selected = k == current
            Text(
                name,
                color = if (selected) Palette.background else Palette.text,
                fontSize = 12.sp,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Palette.accent else Palette.surfaceHigh)
                    .clickable { onSelect(k) }
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag("category_$name"),
            )
        }
    }
}

private const val STROKE_DELAY_MILLIS = 70L
private val CHIP_WIDTH = 72.dp
private const val POLL_MILLIS = 6L
private const val TAP = 1
private const val TWO = 2
private const val MOVED = 3
private const val MAX_ZOOM = 6f

/** How many polls of 120 ms a notice stays. */
private const val NOTICE_TICKS = 21

private fun strokeSound(sound: SoundManager, materials: MaterialTable, tool: Int) {
    if (tool < 0) {
        if (tool == Session.SCOOP || tool == Session.POUR) sound.play("splash", 0.4f)
        return
    }
    when {
        materials.spawnBurning[tool] -> sound.play("whoosh", 0.6f)
        materials.kind[tool] == Kind.LIQUID -> sound.play("splash", 0.5f)
        materials.kind[tool] == Kind.GAS -> sound.play("condense", 0.4f)
        else -> sound.play("place", 0.5f)
    }
}

@Composable
private fun PerformanceOverlay(stats: SimRunner.Stats, modifier: Modifier) {
    Text(
        "%.0f Schritte/s · %.2f ms/Schritt\nBild %.2f ms · %d/%d Chunks wach\nSchritt %d".format(
            stats.stepsPerSecond, stats.msPerStep, stats.msPerRender, stats.awakeChunks, stats.chunks, stats.step,
        ),
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xAA000000))
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag("performance"),
    )
}

@Composable
private fun SpeedSelector(current: SimRunner.Speed, modifier: Modifier, onSelect: (SimRunner.Speed) -> Unit) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.surfaceHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((speed, label) in listOf(SimRunner.Speed.PAUSE to "Pause", SimRunner.Speed.NORMAL to "Normal", SimRunner.Speed.FAST to "Schnell")) {
            val selected = speed == current
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Palette.accent else Palette.surfaceHigh)
                    .clickable { onSelect(speed) }
                    .testTag("speed_$label"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Palette.background else Palette.text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BrushSelector(current: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.surfaceHigh)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((k, r) in BRUSHES.withIndex()) {
            val selected = r == current
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.outline else Palette.surfaceHigh)
                    .clickable { onSelect(r) }
                    .semantics { contentDescription = "Pinsel ${k + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size((4 + k * 4).dp)
                        .clip(CircleShape)
                        .background(if (selected) Palette.accent else Palette.textDim),
                )
            }
        }
    }
}

/** The supply bar: every material with its colour, the amount left (unlimited here) and its cost. */
@Composable
private fun MaterialBar(materials: MaterialTable, ids: List<String>, tool: Int, onTool: (Int) -> Unit) {
    // no stretching at the ends: the overscroll stretch squeezed the labels into each other
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        Row(
            Modifier
                .fillMaxWidth()
                .testTag("material_bar")
                .clipToBounds()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            MaterialChips(materials, ids, tool, onTool)
        }
    }
}

@Composable
private fun MaterialChips(materials: MaterialTable, ids: List<String>, tool: Int, onTool: (Int) -> Unit) {
    for (id in ids) {
        val m = materials.idOrNull(id) ?: continue
        MaterialChip(
            name = materials.spec(m).name,
            detail = "∞ · ${materials.cost[m]} P",
            selected = tool == m,
            onClick = { onTool(m) },
        ) { Swatch(materials.color[m], materials.colorVariance[m], m) }
    }
    MaterialChip(name = "Radierer", detail = "nur hier", selected = tool == Session.ERASER, onClick = { onTool(Session.ERASER) }) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, Palette.textDim, RoundedCornerShape(8.dp)))
    }
}

@Composable
private fun MaterialChip(
    name: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Column(
        Modifier
            .width(CHIP_WIDTH)
            .clipToBounds()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Palette.surfaceHigh else Palette.surface)
            .border(2.dp, if (selected) Palette.accent else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag("material_$name"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(
            name,
            color = Palette.text,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(CHIP_WIDTH - 8.dp),
            textAlign = TextAlign.Center,
        )
        Text(
            detail,
            color = Palette.textDim,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(CHIP_WIDTH - 8.dp),
            textAlign = TextAlign.Center,
        )
    }
}

/** A little square of the material's grain: its colour in the shades its particles get. */
@Composable
private fun Swatch(argb: Int, variance: Float, seed: Int) {
    Canvas(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp))) {
        val n = 6
        val cell = size.width / n
        for (y in 0 until n) for (x in 0 until n) {
            val h = ((x * 73 + y * 151 + seed * 37) * 2654435761L ushr 7 and 0xFF).toInt() / 255f
            val f = 1f + (h - 0.5f) * 2f * max(variance, 0.06f)
            val r = (((argb shr 16) and 0xFF) * f).toInt().coerceIn(0, 255)
            val g = (((argb shr 8) and 0xFF) * f).toInt().coerceIn(0, 255)
            val b = ((argb and 0xFF) * f).toInt().coerceIn(0, 255)
            drawRect(Color(r, g, b), topLeft = Offset(x * cell, y * cell), size = androidx.compose.ui.geometry.Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
