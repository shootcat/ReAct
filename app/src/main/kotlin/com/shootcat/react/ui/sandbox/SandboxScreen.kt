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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.audio.SoundManager
import com.shootcat.react.data.Settings
import com.shootcat.react.sim.Benchmark
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
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Materials in the bar, in this order (Leitfaden V3, Phase 1). */
private val BAR = listOf("sand", "earth", "water", "wood", "fire", "lava", "stone", "rock", "steam", "smoke")

/** Brush radii in cells. */
private val BRUSHES = listOf(1, 3, 6, 10)

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
    sound: SoundManager,
    onTool: (Int) -> Unit,
    onSettings: (Settings) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var speed by remember { mutableStateOf(runner.speed) }
    var stats by remember { mutableStateOf(runner.stats) }
    var menu by remember { mutableStateOf(false) }
    var benchmark by remember { mutableStateOf<String?>(null) }
    var measuring by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(runner) {
        val events = IntArray(Event.COUNT)
        while (true) {
            delay(120)
            stats = runner.stats
            runner.takeEvents(events)
            playEvents(sound, events)
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
                    Benchmark.report(results, render, device)
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
            overline = "Kosten ${stats.cost} · Vorrat ∞",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            RoundIconButton(Glyph.UNDO, "Rückgängig", { runner.post { it.undo() } }, size = 44.dp, enabled = stats.canUndo)
            RoundIconButton(Glyph.RESET, "Neu beginnen", { runner.post { it.reset() } }, size = 44.dp)
            Box {
                RoundIconButton(Glyph.GEAR, "Menü", { menu = true }, size = 44.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.surfaceHigh) {
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
            WorldView(runner, tool, radius, materials, sound, Modifier.fillMaxSize())
            if (settings.performance) PerformanceOverlay(stats, Modifier.align(Alignment.TopStart).padding(8.dp))
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
            BrushSelector(settings.brush) { onSettings(settings.copy(brush = it)) }
        }
        MaterialBar(materials, tool, onTool)
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
    materials: MaterialTable,
    sound: SoundManager,
    modifier: Modifier,
) {
    val w = runner.width
    val h = runner.height
    val lw = runner.layerWidth
    val lh = runner.layerHeight
    val bitmap = remember(runner) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    val hazeBitmap = remember(runner) { Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888) }
    val lightBitmap = remember(runner) { Bitmap.createBitmap(lw, lh, Bitmap.Config.ARGB_8888) }
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
        drawImage(lightImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
    }
}

private const val STROKE_DELAY_MILLIS = 70L
private const val POLL_MILLIS = 6L
private const val TAP = 1
private const val TWO = 2
private const val MOVED = 3
private const val MAX_ZOOM = 6f

private fun strokeSound(sound: SoundManager, materials: MaterialTable, tool: Int) {
    if (tool == Session.ERASER) return
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
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.outline else Palette.surfaceHigh)
                    .clickable { onSelect(r) }
                    .semantics { contentDescription = "Pinsel ${k + 1}" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size((6 + k * 5).dp)
                        .clip(CircleShape)
                        .background(if (selected) Palette.accent else Palette.textDim),
                )
            }
        }
    }
}

/** The supply bar: every material with its colour, the amount left (unlimited here) and its cost. */
@Composable
private fun MaterialBar(materials: MaterialTable, tool: Int, onTool: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (id in BAR) {
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
            .width(70.dp)
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
        Text(name, color = Palette.text, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, color = Palette.textDim, fontSize = 10.sp, maxLines = 1)
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
