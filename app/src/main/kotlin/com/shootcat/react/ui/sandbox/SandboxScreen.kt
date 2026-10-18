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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
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
import com.shootcat.react.level.Play
import com.shootcat.react.level.Result
import com.shootcat.react.level.Tools
import com.shootcat.react.sim.Benchmark
import com.shootcat.react.sim.Discoveries
import com.shootcat.react.sim.Event
import com.shootcat.react.sim.Kind
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Renderer
import com.shootcat.react.sim.Session
import com.shootcat.react.sim.SimRunner
import com.shootcat.react.sim.Template
import com.shootcat.react.sim.Terrain
import com.shootcat.react.sim.World
import com.shootcat.react.sim.WorldSize
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
import kotlin.math.ceil
import kotlin.math.floor
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
    "Lose" to listOf("sand", "earth", "gravel", "clay", "salt", "snow", "rubble", "ore_rubble", "glass_shards", "charcoal", "ash"),
    "Flüssig" to listOf("water", "saltwater", "lava", "molten_metal", "steam", "cloud", "smoke", "firedamp"),
    "Bauen" to listOf("wood", "brick", "stone", "basalt", "glass", "metal", "ice"),
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
 *
 * With a [level] the same screen plays that level (Phase 3): only its tools, its goals over the world, the
 * time and the actions, and the medal at the end.
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
    onNewWorld: (Template, WorldSize) -> Unit,
    onBack: () -> Unit,
    level: LevelMode? = null,
) {
    var speed by remember(runner) { mutableStateOf(runner.speed) }
    var newWorld by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf(false) }
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

    /** What a finger held on the world found (3b.1): shown while the finger stays and a little after. */
    var info by remember(runner) { mutableStateOf<Session.CellInfo?>(null) }
    var infoHeld by remember(runner) { mutableStateOf(false) }
    var thermal by remember(runner) { mutableStateOf(runner.thermal) }
    LaunchedEffect(runner) {
        var seen = runner.inspectedCount
        var after = 0
        while (true) {
            delay(80)
            if (runner.inspectedCount != seen) {
                seen = runner.inspectedCount
                info = runner.inspected
                after = 0
            }
            if (!infoHeld && info != null && ++after >= INFO_TICKS) info = null
        }
    }
    // with the hand chosen, everything it could take lights up (3b.3)
    LaunchedEffect(runner, tool) { runner.highlightGrabbable = tool == Session.HAND }
    DisposableEffect(runner) { onDispose { runner.highlightGrabbable = false } }

    /** The end of the level, once it comes: the result (solved) or the goal that was not kept (lost). */
    var solved by remember { mutableStateOf<Result?>(null) }
    var lost by remember { mutableStateOf<String?>(null) }
    if (level != null) {
        LaunchedEffect(level.play) {
            var seen: Any? = null
            while (true) {
                delay(120)
                val play = level.play
                when (play.state) {
                    Play.State.WON -> {
                        val r = play.result
                        if (r != null && r !== seen) {
                            seen = r
                            level.onSolved(r)
                            sound.play("success", 0.8f)
                            solved = r
                        }
                    }
                    Play.State.LOST -> if (seen !== Play.State.LOST) {
                        seen = Play.State.LOST
                        lost = play.goals.firstOrNull { it.guards && !it.met(play.session.world) }?.text
                            ?: play.goals.firstOrNull { it.guards }?.text ?: ""
                        sound.play("crumble", 0.8f)
                    }
                    Play.State.PLAYING -> if (seen === Play.State.LOST) seen = null
                }
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
                    val results = Benchmark.runAll(materials, steps = 300)
                    val render = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, Terrain.WIDTH, Terrain.HEIGHT))
                    val paused = Benchmark.renderMillis(Benchmark.sandboxWorld(materials, Terrain.WIDTH, Terrain.HEIGHT), paused = true)
                    Benchmark.report(results, render, device, paused, Benchmark.renderLargest(materials))
                }
            }
            Log.i("REACT", text)
            measuring = false
            benchmark = text
        }
    }

    // procuring: only the tools and what is in store (Leitfaden V3, 4.3); a level: only its tools
    val tools = level?.play?.tools
    val categories = when {
        tools != null -> if (tools.build) listOf(TOOLS to emptyList(), STORE to emptyList()) else listOf(TOOLS to emptyList())
        stats.supply == Session.Supply.PROCURE -> listOf(TOOLS to emptyList(), STORE to emptyList())
        else -> CATEGORIES
    }
    var chosen by rememberSaveable {
        mutableStateOf(if (tool <= Session.SCOOP) TOOLS else CATEGORIES.firstOrNull { (_, ids) -> ids.any { materials.idOrNull(it) == tool } }?.first ?: "Lose")
    }
    val category = categories.indexOfFirst { it.first == chosen }.coerceAtLeast(0)
    val folded = settings.barFolded
    fun fold(on: Boolean) = onSettings(settings.copy(barFolded = on))

    /** The name of the level and its controls; [narrow] in the column beside the world: the name may wrap. */
    @Composable
    fun levelHeader(level: LevelMode, modifier: Modifier, narrow: Boolean) {
        val spec = level.play.level.spec
        TopBar(
            title = spec.name,
            overline = if (narrow) spec.area else "${spec.area} · ${spec.tier}",
            onBack = onBack,
            modifier = modifier,
            titleLines = if (narrow) 2 else 1,
        ) {
            RoundIconButton(Glyph.UNDO, "Rückgängig", { runner.post { it.undo() } }, size = 44.dp, enabled = stats.canUndo)
            RoundIconButton(Glyph.RESET, "Neu beginnen", level.onRestart, size = 44.dp)
            Box {
                RoundIconButton(Glyph.GEAR, "Menü", { menu = true }, size = 44.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.surfaceHigh) {
                    DropdownMenuItem(
                        text = { Text("Entdeckungsbuch", color = Palette.text) },
                        onClick = {
                            menu = false
                            onOpenBook()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (settings.performance) "Leistungsanzeige aus" else "Leistungsanzeige an", color = Palette.text) },
                        onClick = {
                            menu = false
                            onSettings(settings.copy(performance = !settings.performance))
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Einstellungen", color = Palette.text) },
                        onClick = {
                            menu = false
                            onOpenSettings()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Zur Levelauswahl", color = Palette.text) },
                        onClick = {
                            menu = false
                            onBack()
                        },
                    )
                }
            }
        }
    }

    @Composable
    fun header(modifier: Modifier, narrow: Boolean = false) {
        if (level != null) {
            levelHeader(level, modifier, narrow)
            return
        }
        TopBar(
            title = "Spielplatz",
            overline = if (stats.supply == Session.Supply.PROCURE) "Nur Beschaffen · Eimer ${stats.bucketAmount}/${stats.bucketSize}" else "Kosten ${stats.cost} · Vorrat ∞",
            onBack = onBack,
            modifier = modifier,
        ) {
            RoundIconButton(Glyph.UNDO, "Rückgängig", { runner.post { it.undo() } }, size = 44.dp, enabled = stats.canUndo)
            RoundIconButton(Glyph.RESET, "Neu beginnen", { runner.post { it.reset() } }, size = 44.dp)
            Box {
                RoundIconButton(Glyph.GEAR, "Menü", { menu = true }, size = 44.dp)
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Palette.surfaceHigh) {
                    DropdownMenuItem(
                        text = { Text("Neue Welt …", color = Palette.text) },
                        onClick = {
                            menu = false
                            newWorld = true
                        },
                    )
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
                        text = { Text("Wetter …", color = Palette.text) },
                        onClick = {
                            menu = false
                            weather = true
                        },
                        modifier = Modifier.testTag("menu_weather"),
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
    }

    /** The world; with [hud] the goal of the level lies over it (landscape with the column folded away). */
    @Composable
    fun world(modifier: Modifier, hud: Boolean = false) {
        Box(modifier) {
            val radius = if (settings.brush in BRUSHES) settings.brush else 3
            WorldView(
                runner, tool, radius, mode, materials, sound, stats.held, Modifier.fillMaxSize(),
                goalAreas = level?.play?.level?.spec?.goals?.map { it.area } ?: emptyList(),
                // a level begins with all of it on screen: the player sees what there is before zooming in
                fitOnStart = level != null,
                onInspect = { x, y ->
                    infoHeld = true
                    runner.inspect(x, y)
                },
                onInspectDone = { infoHeld = false },
            )
            if (level != null && hud) LevelHud(level.play, stats, Modifier.align(Alignment.TopStart).padding(8.dp).width(300.dp).blockTouches())
            info?.let { InfoCard(it, materials, stats.supply == Session.Supply.PROCURE, Modifier.align(Alignment.BottomStart).padding(8.dp).blockTouches()) }
            if (settings.performance) PerformanceOverlay(stats, Modifier.align(Alignment.TopStart).padding(8.dp))
            notice?.let { NoticeBanner(it, Modifier.align(Alignment.TopCenter).padding(top = 40.dp, start = 16.dp, end = 16.dp)) }
            // speed and the shape of the brush, small, in a corner of the world (4a.5)
            CornerControls(
                speed = speed,
                onSpeed = {
                    speed = it
                    runner.speed = it
                },
                mode = mode,
                onMode = onMode,
                brush = settings.brush,
                onBrush = { onSettings(settings.copy(brush = it)) },
                thermal = thermal,
                onThermal = {
                    thermal = it
                    runner.thermal = it
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            )
        }
    }

    @Composable
    fun bar() {
        when (categories[category].first) {
            TOOLS -> ToolBar(materials, stats, tool, onTool, tools) { runner.post { it.store() } }
            STORE -> StoreBar(materials, stats, tool, onTool)
            else -> MaterialBar(materials, categories[category].second, tool, onTool)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth > maxHeight) {
            // landscape: the world as large as it gets, the controls in a column beside it
            Row(Modifier.fillMaxSize()) {
                world(Modifier.weight(1f).fillMaxHeight(), hud = folded)
                Column(Modifier.width(if (folded) 64.dp else SIDE_PANEL).fillMaxHeight().background(Palette.background)) {
                    if (folded) {
                        FoldButton(folded = true, vertical = true, onClick = { fold(false) }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                        CurrentTool(materials, tool, stats, Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                    } else {
                        header(Modifier.padding(horizontal = 4.dp), narrow = true)
                        if (level != null) LevelHud(level.play, stats, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp))
                        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryTabs(categories.map { it.first }, category, Modifier.weight(1f)) { chosen = categories[it].first }
                            FoldButton(folded = false, vertical = true, onClick = { fold(true) })
                        }
                        Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                            when (categories[category].first) {
                                TOOLS, STORE -> bar()
                                else -> MaterialGrid(materials, categories[category].second, tool, onTool)
                            }
                        }
                    }
                }
            }
        } else {
            // portrait: the bar below the world, two rows at most when unfolded (4a.5)
            Column(Modifier.fillMaxSize()) {
                header(Modifier.padding(horizontal = 8.dp))
                if (level != null) LevelHud(level.play, stats, Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 4.dp))
                world(Modifier.weight(1f).fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (folded) {
                        CurrentTool(materials, tool, stats, Modifier.weight(1f))
                    } else {
                        CategoryTabs(categories.map { it.first }, category, Modifier.weight(1f)) { chosen = categories[it].first }
                    }
                    FoldButton(folded = folded, vertical = false, onClick = { fold(!folded) })
                }
                if (!folded) bar() else Spacer(Modifier.height(6.dp))
            }
        }
    }

    if (level != null && (solved != null || lost != null)) {
        LevelEndDialog(
            result = solved,
            lostGoal = lost,
            hasNext = level.hasNext,
            onLook = {
                solved = null
                lost = null
            },
            onRestart = level.onRestart,
            onNext = level.onNext,
            onLevels = level.onLevels,
        )
    }

    if (newWorld) {
        NewWorldDialog(
            onDismiss = { newWorld = false },
            onCreate = { t, size ->
                newWorld = false
                onNewWorld(t, size)
            },
        )
    }

    if (weather && level == null) {
        WeatherDialog(
            now = stats.weather,
            worldHeight = runner.height,
            onDismiss = { weather = false },
            onApply = { w ->
                weather = false
                runner.post { it.setWeather(w.cloudBase, w.wind, w.gust, w.random, w.ambient) }
            },
            onStorm = {
                weather = false
                val v = runner.viewport
                val cx = if (v != null) (v[0] + v[2]) / 2 else runner.width / 2
                runner.post { it.storm(cx) }
            },
        )
    }

    if (measuring) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Palette.surface,
            title = { Text("Messung läuft", color = Palette.text) },
            text = { Text("Leere, voll aktive und belebte Welt, dazu die größte Welt, mit je 300 Schritten. Das dauert etwa eine halbe Minute.", color = Palette.textDim) },
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

/** What happened in the world, as sounds: crackling fire, hissing water, boiling, setting lava, rain and thunder. */
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
    if (e[Event.RAIN] > 4) sound.play("rain", volume(e[Event.RAIN]) * 0.6f)
    if (e[Event.LIGHTNING] > 0) sound.play("thunder", 1f)
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
    goalAreas: List<List<List<Int>>> = emptyList(),
    fitOnStart: Boolean = false,
    onInspect: (Int, Int) -> Unit = { _, _ -> },
    onInspectDone: () -> Unit = {},
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
    val cw = runner.cloudWidth
    val ch = runner.cloudHeight
    val cloudBitmap = remember(runner) { Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888) }
    val cloudImage = remember(cloudBitmap) { cloudBitmap.asImageBitmap() }
    // the bodies as pictures of their own (Phase 4b): one image per body, made anew when its picture changes
    val bodyImages = remember(runner) { HashMap<Int, BodyImage>() }
    var shownFrame by remember(runner) { mutableStateOf<SimRunner.Frame?>(null) }
    var arrived by remember(runner) { mutableLongStateOf(0L) }
    var displayNanos by remember { mutableLongStateOf(0L) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val hazeImage = remember(hazeBitmap) { hazeBitmap.asImageBitmap() }
    val lightImage = remember(lightBitmap) { lightBitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }

    // picks up each finished picture of the simulation thread; drawing follows on the next frame
    LaunchedEffect(runner) {
        while (true) {
            val f = runner.takeFrame()
            if (f != null) {
                // only the part on screen was drawn
                bitmap.setPixels(f.pixels, f.y0 * w + f.x0, w, f.x0, f.y0, f.x1 - f.x0 + 1, f.y1 - f.y0 + 1)
                hazeBitmap.setPixels(f.haze, 0, lw, 0, 0, lw, lh)
                lightBitmap.setPixels(f.light, 0, lw, 0, 0, lw, lh)
                flameBitmap.setPixels(f.flames, 0, fw, 0, 0, fw, fh)
                cloudBitmap.setPixels(f.clouds, 0, cw, 0, 0, cw, ch)
                for (k in 0 until f.spriteCount) {
                    val sp = f.sprites[k]
                    var have = bodyImages[sp.id]
                    if (have == null || have.w != sp.w || have.h != sp.h) {
                        val bmp = Bitmap.createBitmap(sp.w, sp.h, Bitmap.Config.ARGB_8888)
                        have = BodyImage(sp.w, sp.h, bmp, bmp.asImageBitmap())
                        bodyImages[sp.id] = have
                    }
                    if (have.pixels !== sp.pixels) {
                        have.bitmap.setPixels(sp.pixels, 0, sp.w, 0, 0, sp.w, sp.h)
                        have.pixels = sp.pixels
                    }
                }
                shownFrame = f
                arrived = System.nanoTime()
                frames++
            }
            delay(POLL_MILLIS)
        }
    }

    // bodies move on smoothly between two pictures of the simulation: drawn anew on every frame of the display
    LaunchedEffect(runner) {
        while (true) withFrameNanos { displayNanos = it }
    }

    var view by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(runner) { mutableFloatStateOf(1f) }
    var pan by remember(runner) { mutableStateOf(Offset.Zero) }
    var fitted by remember(runner) { mutableStateOf(!fitOnStart) }
    val currentTool by rememberUpdatedState(tool)
    val currentRadius by rememberUpdatedState(radius)
    val currentMode by rememberUpdatedState(mode)
    val currentInspect by rememberUpdatedState(onInspect)
    val currentInspectDone by rememberUpdatedState(onInspectDone)

    /** The shape being built: start and end cell, shown before the finger lets go (4.1.5). */
    var preview by remember { mutableStateOf<Pair<Pair<Int, Int>, Pair<Int, Int>>?>(null) }

    /** Where the held object would be put down, while the finger is on the world. */
    var handAt by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    /** Counts the strokes begun, so the late release of a short touch never ends a newer stroke. */
    val strokes = remember { IntArray(1) }
    val tapScope = rememberCoroutineScope()

    // the world fills the height of the view and is wider than it (4a.5); two fingers pan and zoom, out to the whole world
    fun base(): Float = if (view.height > 0) view.height / h.toFloat() else 1f
    fun minZoom(): Float = min(1f, min(view.width / w.toFloat(), view.height / h.toFloat()) / base())
    fun scale(): Float = base() * zoom
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

    // the part of the world on screen, and a margin, is all the simulation thread draws
    LaunchedEffect(runner) {
        snapshotFlow { Triple(view, zoom, pan) }.collect {
            if (view.width == 0 || view.height == 0) return@collect
            val s = scale()
            val o = origin()
            runner.viewport = intArrayOf(
                (floor(-o.x / s).toInt() - VIEW_MARGIN).coerceIn(0, w - 1),
                (floor(-o.y / s).toInt() - VIEW_MARGIN).coerceIn(0, h - 1),
                (ceil((view.width - o.x) / s).toInt() + VIEW_MARGIN).coerceIn(0, w - 1),
                (ceil((view.height - o.y) / s).toInt() + VIEW_MARGIN).coerceIn(0, h - 1),
            )
        }
    }

    Canvas(
        modifier
            // the world stays inside its own box, however far it is zoomed in (3b.4)
            .clipToBounds()
            .onSizeChanged {
                view = it
                if (!fitted && it.width > 0 && it.height > 0) {
                    zoom = minZoom()
                    fitted = true
                }
                pan = clampPan(pan)
            }
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
                        zoom = (zoom * factor).coerceIn(minZoom(), MAX_ZOOM)
                        val s1 = scale()
                        // keep the point under the fingers where it is, then follow the fingers
                        val worldPoint = (centroid - before) / s0
                        val newOrigin = centroid - worldPoint * s1 + panBy
                        val centred = Offset((view.width - w * s1) / 2f, (view.height - h * s1) / 2f)
                        pan = clampPan(newOrigin - centred)
                        e.changes.forEach { it.consume() }
                    }

                    // a still finger held for a moment asks what is there (3b.1): no action, nothing changes;
                    // a tap, a stroke or two fingers are told apart before anything is done
                    if (currentTool == Session.HAND) handAt = cell(start)
                    var held = 0L
                    val first = withTimeoutOrNull(LONG_PRESS_MILLIS) {
                        var kind = 0
                        while (kind == 0) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.count { it.pressed }
                            held = e.changes.first().uptimeMillis - down.uptimeMillis
                            kind = when {
                                pressed == 0 -> TAP
                                pressed >= 2 -> TWO
                                (e.changes.first().position - start).getDistance() > viewConfiguration.touchSlop -> MOVED
                                else -> 0
                            }
                        }
                        kind
                    } ?: LONG
                    if (first == LONG) {
                        handAt = null
                        var at = cell(start)
                        currentInspect(at.first, at.second)
                        // the finger may wander on: what lies under it is shown; a second finger zooms
                        while (true) {
                            val e = awaitPointerEvent()
                            val pressed = e.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                transform(e)
                                continue
                            }
                            val next = cell(pressed.first().position)
                            if (next != at) {
                                at = next
                                currentInspect(at.first, at.second)
                            }
                            pressed.first().consume()
                        }
                        currentInspectDone()
                        return@awaitEachGesture
                    }

                    if (currentTool == Session.HAND) {
                        // the hand acts where the finger lets go: picks up there, or puts down what it holds
                        var at = cell(start)
                        var cancelled = first == TWO
                        if (cancelled) handAt = null
                        if (first != TAP) {
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
                        var cancelled = first == TWO
                        if (cancelled) preview = null
                        if (first != TAP) {
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
                        strokes[0]++
                        val (x, y) = cell(start)
                        last = x to y
                        val t = currentTool
                        val r = currentRadius
                        runner.post { it.press(x, y, t, r) }
                        strokeSound(sound, materials, t)
                    }

                    when (first) {
                        TAP -> {
                            // a short touch paints for as long as the finger was down, as holding did before the
                            // long press asked what is there; with the bucket it lasts a whole pass at the least:
                            // a quick tap with brush 1 would pour only half a bucket (3b.6)
                            begin()
                            val stroke = strokes[0]
                            val bucket = currentTool == Session.SCOOP || currentTool == Session.POUR
                            val hold = if (bucket) LONG_PRESS_MILLIS else held.coerceIn(0L, LONG_PRESS_MILLIS)
                            tapScope.launch {
                                delay(hold)
                                if (strokes[0] == stroke) runner.post { it.release() }
                            }
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
        @Suppress("UNUSED_EXPRESSION")
        displayNanos
        shownFrame?.let { f -> drawBodies(f, bodyImages, arrived, o, s) }
        drawImage(cloudImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        drawImage(hazeImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        drawImage(flameImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        drawImage(lightImage, dstOffset = dstOffset, dstSize = dstSize, filterQuality = FilterQuality.Low)
        if (goalAreas.isNotEmpty()) drawGoalAreas(goalAreas, o, s)
        preview?.let { (a, b) -> drawPreview(a, b, mode, radius, tool, materials, o, s) }
        val h = held
        handAt?.let { (x, y) -> if (h != null) drawHeld(h, x, y, materials, o, s) }
    }
}

/** The picture of one body in the app (Phase 4b): a bitmap of its size, and the pixels last put into it. */
private class BodyImage(val w: Int, val h: Int, val bitmap: Bitmap, val image: ImageBitmap) {
    var pixels: IntArray? = null
}

/**
 * The bodies as pictures of their own (Phase 4b), turned and smoothed, each where it is between where it was in
 * the picture before and where it is now, by how much of the time between the two pictures has passed.
 */
private fun DrawScope.drawBodies(f: SimRunner.Frame, images: Map<Int, BodyImage>, arrived: Long, o: Offset, s: Float) {
    val t = ((System.nanoTime() - arrived).toFloat() / maxOf(1L, f.interval)).coerceIn(0f, 1f)
    val k = s / Renderer.SPRITE_SCALE
    for (n in 0 until f.spriteCount) {
        val sp = f.sprites[n]
        val img = images[sp.id]?.image ?: continue
        val x = sp.lastX + (sp.x - sp.lastX) * t
        val y = sp.lastY + (sp.y - sp.lastY) * t
        val a = sp.lastAngle + (sp.angle - sp.lastAngle) * t
        withTransform({
            translate(o.x + (x + 0.5f) * s, o.y + (y + 0.5f) * s)
            rotate(Math.toDegrees(a.toDouble()).toFloat(), pivot = Offset.Zero)
            scale(k, k, pivot = Offset.Zero)
            translate(-sp.pivotX, -sp.pivotY)
        }) {
            drawImage(img, filterQuality = FilterQuality.Low)
        }
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
private fun ToolBar(materials: MaterialTable, stats: SimRunner.Stats, tool: Int, onTool: (Int) -> Unit, tools: Tools?, onStore: () -> Unit) {
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
            // a level gives only its own tools (Leitfaden V3, 5.1)
            if (tools == null || tools.bucket) {
                val bucket = if (stats.bucketAmount > 0) "${materials.spec(stats.bucketMaterial).name} ${stats.bucketAmount}" else "leer"
                MaterialChip("Schöpfen", bucket, tool == Session.SCOOP, { onTool(Session.SCOOP) }) { BucketIcon(stats, materials, false) }
                MaterialChip("Gießen", "${stats.bucketAmount}/${stats.bucketSize}", tool == Session.POUR, { onTool(Session.POUR) }) { BucketIcon(stats, materials, true) }
            }
            val held = stats.held
            if (tools == null || tools.hand) {
                MaterialChip("Hand", if (held != null) "${materials.spec(held.main).name} ${held.size}" else "leer", tool == Session.HAND, { onTool(Session.HAND) }) {
                    HandIcon(if (held != null) Color(materials.color[held.main]) else null)
                }
            }
            // loose material from the bucket into the store, to build with (3b.5)
            if (held == null && stats.supply == Session.Supply.PROCURE && stats.bucketAmount > 0 &&
                materials.kind[stats.bucketMaterial] == Kind.POWDER && (tools == null || tools.build)
            ) {
                MaterialChip("Ins Lager", "aus dem Eimer", false, onStore) {
                    Swatch(materials.color[stats.bucketMaterial], materials.colorVariance[stats.bucketMaterial], stats.bucketMaterial)
                }
            }
            if (held != null && (tools == null || tools.build)) {
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

/** The icon of a way of building: a brush, a line, a filled rectangle, a frame. */
@Composable
private fun ModeIcon(m: BuildMode, tint: Color, size: androidx.compose.ui.unit.Dp) {
    Canvas(Modifier.size(size)) {
        val u = this.size.width
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
                ModeIcon(m, tint, 18.dp)
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

/** A finger held still this long asks what lies under it instead of using the tool (3b.1). */
private const val LONG_PRESS_MILLIS = 450L
private val CHIP_WIDTH = 72.dp
private const val POLL_MILLIS = 6L
private const val TAP = 1
private const val TWO = 2
private const val MOVED = 3
private const val LONG = 4
private const val MAX_ZOOM = 6f

/** Width of the column beside the world in landscape. */
private val SIDE_PANEL = 300.dp

/** Cells drawn beyond the edges of the screen, so panning shows no stale picture before the next frame. */
private const val VIEW_MARGIN = 24

/** How many polls of 120 ms a notice stays. */
private const val NOTICE_TICKS = 21

/** The material info stays this many ticks (of 80 ms) after the finger let go. */
private const val INFO_TICKS = 40

/** Where clouds form when the sandbox switches them on: as on a meadow, a sixth of the way down. */
private const val CLOUD_HEIGHT = 0.16f

/** How far the gusts of the sandbox take the wind above and below what is set. */
private const val GUST = 0.25f

/** Touches on a control lying over the world never reach the world (3b.4). */
fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}

/** A thermometer: the heat picture. */
@Composable
private fun ThermometerIcon(tint: Color) {
    Canvas(Modifier.size(16.dp)) {
        val u = size.width
        drawRoundRect(tint, Offset(u * 0.38f, u * 0.05f), Size(u * 0.24f, u * 0.62f), androidx.compose.ui.geometry.CornerRadius(u * 0.12f), style = Stroke(width = u * 0.08f))
        drawCircle(tint, u * 0.2f, Offset(u * 0.5f, u * 0.76f))
        drawRect(tint, Offset(u * 0.46f, u * 0.32f), Size(u * 0.08f, u * 0.4f))
    }
}

/**
 * What lies under a finger held on the world (3b.1): the material, its temperature, how it is (hot,
 * glowing, frozen, wet, burning, anchored), and whether bucket and hand could take it.
 */
@Composable
private fun InfoCard(info: Session.CellInfo, materials: MaterialTable, procuring: Boolean, modifier: Modifier) {
    val states = buildList {
        when {
            info.temp > 500f -> add("glühend")
            info.temp > Session.HAND_TEMP -> add("heiß")
            info.temp < 0f -> add("unter null")
        }
        if (info.burning) add("brennt")
        if (info.wet) add("nass")
        if (info.anchored && materials.kind[info.material] != Kind.STATIC) add("verankert")
        if (procuring && !info.reachable) add("außer Reichweite")
    }
    Column(
        modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xEE141C28))
            .border(1.dp, Palette.outline, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("info"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (info.material != MaterialTable.AIR) {
                Swatch(materials.color[info.material], materials.colorVariance[info.material], info.material)
                Spacer(Modifier.width(8.dp))
            }
            Column {
                Text(materials.spec(info.material).name, color = Palette.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    "%.0f °C".format(info.temp) + if (states.isEmpty()) "" else " · " + states.joinToString(", "),
                    color = Palette.textDim,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Eimer: " + (info.bucket ?: "nimmt es"), color = if (info.bucket == null) Palette.success else Palette.textDim, fontSize = 12.sp)
        Text("Hand: " + (info.hand ?: "nimmt es"), color = if (info.hand == null) Palette.success else Palette.textDim, fontSize = 12.sp)
    }
}

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

/**
 * Speed and the shape of the brush, small, in a corner of the world (Leitfaden V3, 4a.5): pause, normal and
 * fast as three little buttons, and one button that opens the shapes and sizes of the brush.
 */
@Composable
private fun CornerControls(
    speed: SimRunner.Speed,
    onSpeed: (SimRunner.Speed) -> Unit,
    mode: BuildMode,
    onMode: (BuildMode) -> Unit,
    brush: Int,
    onBrush: (Int) -> Unit,
    thermal: Boolean,
    onThermal: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xCC0B0F15))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for ((s, label) in listOf(SimRunner.Speed.PAUSE to "Pause", SimRunner.Speed.NORMAL to "Normal", SimRunner.Speed.FAST to "Schnell")) {
            val selected = s == speed
            val tint = if (selected) Palette.background else Palette.text
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (selected) Palette.accent else Color.Transparent)
                    .clickable { onSpeed(s) }
                    .semantics { contentDescription = label }
                    .testTag("speed_$label"),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val u = size.width
                    fun play(left: Float, w: Float) {
                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(left, u * 0.1f)
                            lineTo(left + w, u * 0.5f)
                            lineTo(left, u * 0.9f)
                            close()
                        }
                        drawPath(path, tint)
                    }
                    when (s) {
                        SimRunner.Speed.PAUSE -> {
                            drawRect(tint, Offset(u * 0.15f, u * 0.1f), Size(u * 0.24f, u * 0.8f))
                            drawRect(tint, Offset(u * 0.61f, u * 0.1f), Size(u * 0.24f, u * 0.8f))
                        }
                        SimRunner.Speed.NORMAL -> play(u * 0.2f, u * 0.7f)
                        SimRunner.Speed.FAST -> {
                            play(0f, u * 0.5f)
                            play(u * 0.5f, u * 0.5f)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        // the heat picture on and off (3b.7)
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (thermal) Palette.accent else Color.Transparent)
                .clickable { onThermal(!thermal) }
                .semantics { contentDescription = "Wärmebild" }
                .testTag("thermal"),
            contentAlignment = Alignment.Center,
        ) {
            ThermometerIcon(if (thermal) Palette.background else Palette.text)
        }
        Spacer(Modifier.width(2.dp))
        Box {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (open) Palette.accent else Palette.surfaceHigh)
                    .clickable { open = !open }
                    .semantics { contentDescription = "Pinselform" }
                    .testTag("brush_shape"),
                contentAlignment = Alignment.Center,
            ) {
                ModeIcon(mode, if (open) Palette.background else Palette.text, 16.dp)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.surfaceHigh) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ModeSelector(mode) {
                        onMode(it)
                        open = false
                    }
                    BrushSelector(brush) {
                        onBrush(it)
                        open = false
                    }
                }
            }
        }
    }
}

/** Folds the bar away or out again: a small arrow. */
@Composable
private fun FoldButton(folded: Boolean, vertical: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = if (folded) "Leiste ausklappen" else "Leiste einklappen"
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(Palette.surfaceHigh)
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .testTag(if (folded) "unfold" else "fold"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(14.dp)) {
            val u = size.width
            // a chevron: down to fold below the world, up to unfold; sideways in the column beside it
            val (a, b, c) = when {
                !vertical && !folded -> Triple(Offset(0.1f, 0.3f), Offset(0.5f, 0.7f), Offset(0.9f, 0.3f))
                !vertical -> Triple(Offset(0.1f, 0.7f), Offset(0.5f, 0.3f), Offset(0.9f, 0.7f))
                !folded -> Triple(Offset(0.3f, 0.1f), Offset(0.7f, 0.5f), Offset(0.3f, 0.9f))
                else -> Triple(Offset(0.7f, 0.1f), Offset(0.3f, 0.5f), Offset(0.7f, 0.9f))
            }
            drawLine(Palette.text, a * u, b * u, strokeWidth = u * 0.16f, cap = StrokeCap.Round)
            drawLine(Palette.text, b * u, c * u, strokeWidth = u * 0.16f, cap = StrokeCap.Round)
        }
    }
}

/** What the finger works with now, small: shown while the bar is folded away. */
@Composable
private fun CurrentTool(materials: MaterialTable, tool: Int, stats: SimRunner.Stats, modifier: Modifier) {
    val name = when (tool) {
        Session.ERASER -> "Radierer"
        Session.SCOOP -> "Schöpfen"
        Session.POUR -> "Gießen"
        Session.HAND -> "Hand"
        else -> if (tool >= 0) materials.spec(tool).name else ""
    }
    Row(modifier.testTag("current_tool"), verticalAlignment = Alignment.CenterVertically) {
        when {
            tool >= 0 -> Swatch(materials.color[tool], materials.colorVariance[tool], tool)
            tool == Session.SCOOP || tool == Session.POUR -> BucketIcon(stats, materials, tool == Session.POUR)
            tool == Session.HAND -> HandIcon(stats.held?.let { Color(materials.color[it.main]) })
            else -> Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).border(2.dp, Palette.textDim, RoundedCornerShape(8.dp)))
        }
        Spacer(Modifier.width(8.dp))
        Text(name, color = Palette.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The materials of a kind in rows that wrap: the bar in the column beside the world (landscape). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialGrid(materials: MaterialTable, ids: List<String>, tool: Int, onTool: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().testTag("material_bar").padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MaterialChips(materials, ids, tool, onTool)
    }
}

/** A new world: one of the landscapes in one of the sizes (Leitfaden V3, 4a.5). */
@Composable
private fun NewWorldDialog(onDismiss: () -> Unit, onCreate: (Template, WorldSize) -> Unit) {
    var template by remember { mutableStateOf(Template.MEADOW) }
    var size by remember { mutableStateOf(WorldSize.MEDIUM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text("Neue Welt", color = Palette.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (t in Template.entries) {
                    val selected = t == template
                    Text(
                        t.label,
                        color = if (selected) Palette.background else Palette.text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Palette.accent else Palette.surfaceHigh)
                            .clickable { template = t }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("template_${t.name}"),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("Größe", color = Palette.textDim, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (z in WorldSize.entries) {
                        val selected = z == size
                        Column(
                            Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) Palette.accent else Palette.surfaceHigh)
                                .clickable { size = z }
                                .padding(vertical = 8.dp)
                                .testTag("size_${z.name}"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(z.label, color = if (selected) Palette.background else Palette.text, fontWeight = FontWeight.SemiBold)
                            Text("${z.width} × ${z.height}", color = if (selected) Palette.background else Palette.textDim, fontSize = 11.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(template, size) }, modifier = Modifier.testTag("create_world")) { Text("Anlegen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/**
 * The weather controls of the sandbox (3c.6): wind with or without gusts, the temperature of the air, clouds
 * on or off, weather of its own; and a thunderstorm over the middle of the screen. Changes count once, as one
 * action, when they are taken over.
 */
@Composable
private fun WeatherDialog(
    now: SimRunner.Weather,
    worldHeight: Int,
    onDismiss: () -> Unit,
    onApply: (SimRunner.Weather) -> Unit,
    onStorm: () -> Unit,
) {
    var wind by remember { mutableFloatStateOf(now.wind) }
    var gusts by remember { mutableStateOf(now.gust > 0f) }
    var ambient by remember { mutableFloatStateOf(now.ambient) }
    var clouds by remember { mutableStateOf(now.cloudBase >= 0) }
    var random by remember { mutableStateOf(now.random) }
    // clouds keep the height they have; switched on again, they come at the usual one
    val base = remember { if (now.cloudBase >= 0) now.cloudBase else (worldHeight * CLOUD_HEIGHT).toInt().coerceAtLeast(World.CLOUD_BAND + 2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.surface,
        title = { Text("Wetter", color = Palette.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("weather_panel"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val strength = abs(wind)
                Text(
                    when {
                        strength < 0.05f -> "Wind: still"
                        else -> "Wind: ${(strength * 10).roundToInt()} von 10, nach ${if (wind > 0f) "rechts" else "links"}"
                    },
                    color = Palette.text,
                )
                Slider(
                    value = wind,
                    onValueChange = { wind = (it * 10).roundToInt() / 10f },
                    valueRange = -1f..1f,
                    steps = 19,
                    colors = sliderColors(),
                    modifier = Modifier.testTag("weather_wind"),
                )
                WeatherToggle("Böen", gusts, "weather_gusts") { gusts = it }
                Text("Lufttemperatur: ${ambient.roundToInt()} °C", color = Palette.text)
                Slider(
                    value = ambient,
                    onValueChange = { ambient = it.roundToInt().toFloat() },
                    valueRange = -20f..40f,
                    colors = sliderColors(),
                    modifier = Modifier.testTag("weather_temp"),
                )
                WeatherToggle("Wolken", clouds, "weather_clouds") { clouds = it }
                WeatherToggle("Eigenes Wetter (Wind dreht, Wolken ziehen auf)", random, "weather_random") { random = it }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Gewitter auslösen",
                    color = Palette.background,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Palette.accent)
                        .clickable(onClick = onStorm)
                        .padding(vertical = 10.dp)
                        .testTag("weather_storm"),
                )
                if (now.storm) Text("Ein Gewitter ist im Gang.", color = Palette.textDim, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onApply(
                        SimRunner.Weather(
                            cloudBase = if (clouds) base else -1,
                            wind = wind,
                            gust = if (gusts) GUST else 0f,
                            random = random,
                            ambient = ambient,
                            storm = now.storm,
                        ),
                    )
                },
                modifier = Modifier.testTag("weather_apply"),
            ) { Text("Übernehmen") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Palette.accent,
    activeTrackColor = Palette.accent,
    inactiveTrackColor = Palette.surfaceHigh,
    activeTickColor = Palette.accentDeep,
    inactiveTickColor = Palette.outline,
)

@Composable
private fun WeatherToggle(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 2.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.text, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Palette.background,
                checkedTrackColor = Palette.accent,
                uncheckedThumbColor = Palette.textDim,
                uncheckedTrackColor = Palette.surfaceHigh,
                uncheckedBorderColor = Palette.outline,
            ),
        )
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
