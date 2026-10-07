package com.shootcat.react.ui.level

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shootcat.react.engine.Drop
import com.shootcat.react.engine.model.Area
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.LevelData
import com.shootcat.react.engine.model.LevelGoal
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props
import com.shootcat.react.engine.model.TargetCleared
import com.shootcat.react.engine.model.TargetContainerFilled
import com.shootcat.react.engine.model.TargetExtinguished
import com.shootcat.react.engine.model.TargetHeated
import com.shootcat.react.engine.model.TargetPreserved
import com.shootcat.react.engine.model.TargetRainTriggered
import com.shootcat.react.engine.model.TargetState
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.components.ObjectInfo
import com.shootcat.react.ui.components.drawGameObject
import com.shootcat.react.ui.theme.Palette
import com.shootcat.react.ui.theme.WorldLooks
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** Minimum clearance around the grid, so no object lies at the screen edge or in a system gesture zone. */
private val EDGE = 20.dp

/**
 * Where the grid lies on the canvas: cells of [cell] pixels, its top-left corner at [origin]. The grid
 * is as large as fits; spare room above becomes more sky, spare room below more ground.
 */
internal data class BoardLayout(val cell: Float, val origin: Offset) {
    fun cellAt(offset: Offset) = Position(floor((offset.x - origin.x) / cell).toInt(), floor((offset.y - origin.y) / cell).toInt())

    /**
     * This layout after a two-finger gesture: scaled by [factor] around [focus] and moved by [pan] (canvas
     * pixels). It never shrinks below the whole grid ([of]) nor grows beyond [MAX_ZOOM] times that, and a
     * grid larger than the canvas always covers it, so no empty edge opens up.
     */
    fun transformed(factor: Float, focus: Offset, pan: Offset, width: Float, height: Float, columns: Int, rows: Int): BoardLayout {
        val whole = of(width, height, columns, rows)
        val cell = (cell * factor).coerceIn(whole.cell, whole.cell * MAX_ZOOM)
        if (cell <= whole.cell) return whole
        val wanted = focus + pan - (focus - origin) * (cell / this.cell)
        fun place(wanted: Float, extent: Float, room: Float, spareShare: Float) =
            if (extent <= room) (room - extent) * spareShare else wanted.coerceIn(room - extent, 0f)
        return BoardLayout(
            cell,
            Offset(place(wanted.x, cell * columns, width, 0.5f), place(wanted.y, cell * rows, height, SKY_SHARE)),
        )
    }

    companion object {
        /** The share of spare height that goes to the sky above the grid. */
        private const val SKY_SHARE = 0.6f

        /** How far two fingers can zoom in: cells up to this many times their size with the whole grid shown. */
        const val MAX_ZOOM = 3f

        fun of(width: Float, height: Float, columns: Int, rows: Int): BoardLayout {
            val cell = minOf(width / columns, height / rows)
            return BoardLayout(cell, Offset((width - cell * columns) / 2f, (height - cell * rows) * SKY_SHARE))
        }
    }
}

/** A dragged object: [grab] keeps the finger where it touched the object, so it does not jump. */
private data class DragState(val objectId: String, val pointer: Offset, val grab: Offset, val hover: Position)

/**
 * The playing field: a cut through the ground of the level's world, drawn with a Compose Canvas.
 *
 * The grid keeps at least [EDGE] from every side (more where the system's back gesture reaches further
 * in) and scales to the largest cell size that fits inside, so it uses as much of the screen's height
 * and width as it can.
 *
 * While [interactive], movable objects (they sit on a soft shadow inside a light frame) can be dragged,
 * or tapped and then a target cell tapped. Touches do not have to be exact: the nearest movable object
 * within a finger's reach is picked. While dragging, the target cell shows what the drop would do
 * ([previewDrop]): place it, merge it, let it react next to its partner – or bounce it off.
 * [previous] and [progress] interpolate movement between two simulation steps.
 */
@Composable
fun Board(
    level: LevelData,
    state: GameState,
    previous: GameState?,
    progress: Float,
    types: TypeCatalog,
    interactive: Boolean,
    overload: Boolean,
    goalsMet: List<Boolean>,
    previewDrop: (String, Position) -> Drop?,
    onMove: (String, Position) -> Unit,
    onBounce: (String) -> Unit,
    modifier: Modifier = Modifier,
    showMarkers: Boolean = true,
    showPreview: Boolean = true,
    haptics: Boolean = true,
    /** Changes whenever the world jumped (undo, redo, reset): the water then snaps instead of flowing. */
    snapKey: Int = 0,
    /** Drives the water's animation instead of the frame clock (for tests that step time themselves). */
    clockNanos: Long? = null,
) {
    val haptic = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "board")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    // A slow clock for drifting scenery (snow, ash, fireflies).
    val slow by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(48_000, easing = LinearEasing), RepeatMode.Restart),
        label = "slow",
    )
    var drag by remember { mutableStateOf<DragState?>(null) }
    // The player's zoomed view (two fingers); null shows the whole grid.
    var view by remember(level.id) { mutableStateOf<BoardLayout?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val shake = remember { Animatable(0f) }
    var shaking by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val currentState by rememberUpdatedState(state)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnBounce by rememberUpdatedState(onBounce)
    val currentPreview by rememberUpdatedState(previewDrop)
    val currentHaptics by rememberUpdatedState(haptics)

    LaunchedEffect(interactive) {
        drag = null
        selected = null
    }
    // A selected object that melted, burnt or was undone away is no longer selected.
    val shownSelection = selected?.takeIf { state.objectById(it)?.canBePickedUp == true }

    // The water keeps its own smoothly moving picture of the simulation, driven by the frame clock.
    val water = remember(level.id) { WaterView() }
    var frameNanos by remember { mutableLongStateOf(0L) }
    if (clockNanos == null) {
        // An infinite animation: paused where infinite animations are (tests); the water then just shows the world.
        LaunchedEffect(Unit) {
            while (true) withInfiniteAnimationFrameNanos { frameNanos = it }
        }
    }

    val landscape = remember(state.width, state.height, state.walls, level.id) {
        Landscape(state, level.terrain, level.look, WorldLooks.of(level.look))
    }
    val hover = drag?.let { it.objectId to it.hover }
    val dropPreview = remember(hover, state) { hover?.let { (id, p) -> previewDrop(id, p) } }

    /** Drops [id] on [to]; if it would bounce off, it shakes in its place instead. */
    fun release(id: String, to: Position) {
        val obj = currentState.objectById(id) ?: return
        if (obj.position == to) return
        if (currentPreview(id, to) != null) {
            if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnMove(id, to)
        } else {
            if (currentHaptics) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            currentOnBounce(id)
            shaking = id
            scope.launch {
                shake.snapTo(1f)
                shake.animateTo(0f, tween(480, easing = LinearEasing))
            }
        }
    }

    val gestures = WindowInsets.systemGestures.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val edges = PaddingValues(
        start = maxOf(EDGE, gestures.calculateStartPadding(direction)),
        end = maxOf(EDGE, gestures.calculateEndPadding(direction)),
        top = EDGE,
        bottom = EDGE,
    )

    BoxWithConstraints(modifier.padding(edges), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(maxWidth, maxHeight)
                .clip(RoundedCornerShape(18.dp))
                .testTag("board")
                .pointerInput(level.id) {
                    // Two fingers zoom and pan. They take the touch away from the one-finger drag and tap below.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val focus = event.calculateCentroid(useCurrent = false)
                            if (event.changes.count { it.pressed } >= 2 && focus.isSpecified) {
                                view = layoutOf(currentState, view).transformed(
                                    event.calculateZoom(), focus, event.calculatePan(),
                                    size.width.toFloat(), size.height.toFloat(), currentState.width, currentState.height,
                                )
                                drag = null
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val layout = layoutOf(currentState, view)
                            val at = offset - layout.origin
                            val obj = pickMovable(currentState, at, layout.cell, exactFirst = true)
                            if (obj != null) {
                                selected = null
                                val center = cellCenter(obj.position, layout.cell)
                                drag = DragState(obj.id, at, center - at, obj.position)
                            }
                        },
                        onDrag = { change, amount ->
                            val d = drag
                            if (d != null) {
                                change.consume()
                                val cell = layoutOf(currentState, view).cell
                                val pointer = d.pointer + amount
                                drag = d.copy(pointer = pointer, hover = cellAt(dragCenter(pointer, d.grab, cell), cell))
                            }
                        },
                        onDragEnd = {
                            val d = drag
                            drag = null
                            if (d != null) release(d.objectId, d.hover)
                        },
                        onDragCancel = { drag = null },
                    )
                }
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    detectTapGestures(
                        onTap = { tap ->
                            val layout = layoutOf(currentState, view)
                            val offset = tap - layout.origin
                            val cell = layout.cell
                            val p = cellAt(offset, cell)
                            val exact = currentState.objectAt(p)?.takeIf { it.canBePickedUp }
                            val sel = selected?.takeIf { currentState.objectById(it)?.canBePickedUp == true }
                            when {
                                sel != null && exact?.id == sel -> selected = null
                                sel != null && currentPreview(sel, p) != null -> {
                                    release(sel, p)
                                    selected = null
                                }
                                exact != null -> selected = exact.id
                                sel != null && currentState.inBounds(p) && !currentState.isWall(p) -> {
                                    release(sel, p)
                                    selected = null
                                }
                                else -> {
                                    val near = pickMovable(currentState, offset, cell, exactFirst = false)
                                    selected = if (near == null || near.id == sel) null else near.id
                                }
                            }
                        },
                    )
                },
        ) {
            val layout = shownLayout(view, size.width, size.height, state.width, state.height)
            val cell = layout.cell
            if (cell <= 0f) return@Canvas
            water.update(state, previous, progress, clockNanos ?: frameNanos, snapKey)
            fun shownAt(o: GameObject) = objectOffset(o, previous, progress) + Offset(0f, water.sinkOf(o.id))
            val d = drag
            val placing = d != null || shownSelection != null
            // Everything below is drawn in grid coordinates.
            translate(layout.origin.x, layout.origin.y) {
                landscape.drawSurroundings(this, cell, -layout.origin.x, -layout.origin.y, size.width - layout.origin.x, size.height - layout.origin.y)
                landscape.drawSky(this, cell, time, slow)
                landscape.drawCaves(this, cell)
                landscape.drawGround(this, cell, time)
                drawNoBuild(state, cell, strong = interactive && placing)
                drawWind(level, cell, time)
                drawGoalAreas(level.goals, goalsMet, state, cell, time)
                val fields = state.placement
                if (fields != null) {
                    drawPlacementFields(fields, cell, holding = interactive && placing, time = time)
                } else if (interactive && placing) {
                    drawFreeCells(state, cell)
                }

                // Movable things rest on a soft shadow.
                for (o in state.objects) {
                    if (!o.isMovable || o.isAiry || o.id == d?.objectId || water.sinkOf(o.id) > 0f) continue
                    drawShadow(objectOffset(o, previous, progress), cell)
                }
                water.drawBehind(this, cell)
                for (o in state.objects) {
                    if (o.isLiquid) continue
                    val prev = previous?.objectById(o.id)
                    val appearing = previous != null && prev == null
                    val alpha = when {
                        o.id == d?.objectId -> 0.3f
                        appearing -> progress
                        else -> 1f
                    }
                    var at = shownAt(o)
                    if (o.id == shaking) at += Offset(sin(shake.value * TAU * 3f) * 0.14f * shake.value, 0f)
                    drawGameObject(o, at * cell, cell, alpha, time, infoFor(o, state))
                }
                if (previous != null) {
                    for (gone in previous.objects.filter { !it.isLiquid && state.objectById(it.id) == null }) {
                        val tl = Offset(gone.position.x * cell, gone.position.y * cell)
                        drawGameObject(gone, tl, cell, 1f - progress, time, infoFor(gone, previous))
                    }
                }

                water.drawInFront(this, cell, state, ::shownAt)

                if (interactive) {
                    drawMovableFrames(state, ::shownAt, cell, shownSelection, d?.objectId, time, showMarkers)
                    if (d != null) drawDrag(d, dropPreview, state, types, cell, time, showPreview)
                }
            }
            drawVignette()
            if (overload) drawOverload(time)
        }
    }
}

private const val LIFT = 0.6f

/** How far from an object's centre a touch still picks it up, in cells (at least [MIN_REACH_DP]). */
private const val REACH = 0.95f
private const val MIN_REACH_DP = 30

private fun PointerInputScope.layoutOf(state: GameState, view: BoardLayout?) =
    shownLayout(view, size.width.toFloat(), size.height.toFloat(), state.width, state.height)

/** The whole grid, or the player's zoomed [view] of it, fitted to the canvas as it is now. */
private fun shownLayout(view: BoardLayout?, width: Float, height: Float, columns: Int, rows: Int) =
    view?.transformed(1f, Offset.Zero, Offset.Zero, width, height, columns, rows) ?: BoardLayout.of(width, height, columns, rows)

private fun cellCenter(p: Position, cell: Float) = Offset((p.x + 0.5f) * cell, (p.y + 0.5f) * cell)

private fun cellAt(offset: Offset, cell: Float) = Position(floor(offset.x / cell).toInt(), floor(offset.y / cell).toInt())

/** Where a dragged object's centre is drawn: at the finger plus its grab offset, lifted above the fingertip. */
private fun dragCenter(pointer: Offset, grab: Offset, cell: Float) = pointer + grab - Offset(0f, cell * LIFT)

/** The object's top-left corner in cells, between its last and its current position. */
private fun objectOffset(o: GameObject, previous: GameState?, progress: Float): Offset {
    val from = previous?.objectById(o.id)?.position ?: o.position
    return Offset(from.x + (o.position.x - from.x) * progress, from.y + (o.position.y - from.y) * progress)
}

/** The movable object under the finger, or else the nearest one within reach of it. */
private fun PointerInputScope.pickMovable(state: GameState, offset: Offset, cell: Float, exactFirst: Boolean): GameObject? {
    if (exactFirst) {
        state.objectAt(cellAt(offset, cell))?.takeIf { it.canBePickedUp }?.let { return it }
    }
    val reach = max(cell * REACH, MIN_REACH_DP.dp.toPx())
    return state.objects
        .filter { it.canBePickedUp }
        .map { it to (cellCenter(it.position, cell) - offset).let { d -> hypot(d.x, d.y) } }
        .filter { it.second <= reach }
        .minByOrNull { it.second }
        ?.first
}

/** Sand and snow form one heap with their neighbours of the same kind. */
private fun infoFor(o: GameObject, state: GameState): ObjectInfo {
    if (!o.flag(Props.GRANULAR) && !o.isFluid) return ObjectInfo()
    val p = o.position
    fun same(n: Position) = state.objectAt(n)?.type == o.type
    return ObjectInfo(joinLeft = same(p.left()), joinRight = same(p.right()), joinBelow = same(p.down()), joinAbove = same(p.up()))
}

private operator fun Offset.times(cell: Float) = Offset(x * cell, y * cell)

private fun DrawScope.drawShadow(at: Offset, cell: Float) {
    val center = Offset((at.x + 0.5f) * cell, (at.y + 0.95f) * cell)
    drawOval(
        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), center, cell * 0.42f),
        Offset(center.x - cell * 0.42f, center.y - cell * 0.1f),
        Size(cell * 0.84f, cell * 0.2f),
    )
}

/** Cells the player may not drop anything into, faintly hatched (clearer while placing). */
private fun DrawScope.drawNoBuild(state: GameState, cell: Float, strong: Boolean) {
    for (p in state.noBuild) {
        if (state.isWall(p)) continue
        val tl = Offset(p.x * cell, p.y * cell)
        for (k in 0..2) {
            val o = cell * (k / 3f)
            drawLine(Color.Black, Offset(tl.x + o, tl.y + cell), Offset(tl.x + cell, tl.y + o), strokeWidth = cell * 0.03f, alpha = if (strong) 0.28f else 0.1f)
        }
    }
}

private fun DrawScope.drawFreeCells(state: GameState, cell: Float) {
    for (y in 0 until state.height) {
        for (x in 0 until state.width) {
            val p = Position(x, y)
            if (state.isBuildable(p)) drawCircle(Color.White, cell * 0.035f, cellCenter(p, cell), alpha = 0.16f)
        }
    }
}

/**
 * The placement fields of a level with marked placement: a faint cross while nothing is held, a soft
 * glowing square on each field while the player holds something.
 */
private fun DrawScope.drawPlacementFields(fields: Set<Position>, cell: Float, holding: Boolean, time: Float) {
    val pulse = 0.75f + 0.25f * sin(time * TAU * 2f)
    for (p in fields) {
        val tl = Offset(p.x * cell, p.y * cell)
        val inset = cell * 0.08f
        val box = Size(cell - 2 * inset, cell - 2 * inset)
        val c = cellCenter(p, cell)
        val arm = cell * 0.12f
        if (holding) {
            drawRoundRect(Palette.accent, tl + Offset(inset, inset), box, CornerRadius(cell * 0.2f), alpha = 0.16f * pulse)
            drawRoundRect(
                Palette.accent,
                tl + Offset(inset, inset),
                box,
                CornerRadius(cell * 0.2f),
                style = Stroke(width = cell * 0.045f),
                alpha = 0.75f * pulse,
            )
        } else {
            drawRoundRect(Color.White, tl + Offset(inset, inset), box, CornerRadius(cell * 0.2f), style = Stroke(width = cell * 0.02f), alpha = 0.12f)
        }
        val alpha = if (holding) 0.8f else 0.28f
        drawLine(Color.White, c - Offset(arm, 0f), c + Offset(arm, 0f), strokeWidth = cell * 0.035f, cap = StrokeCap.Round, alpha = alpha)
        drawLine(Color.White, c - Offset(0f, arm), c + Offset(0f, arm), strokeWidth = cell * 0.035f, cap = StrokeCap.Round, alpha = alpha)
    }
}

/** Streaks blowing through windy areas, in the wind's direction. */
private fun DrawScope.drawWind(level: LevelData, cell: Float, time: Float) {
    for (zone in level.wind) {
        val a = zone.area
        val width = (a.x1 - a.x0 + 1) * cell
        val dir = if (zone.dx >= 0) 1f else -1f
        for (y in a.y0..a.y1) {
            for (k in 0 until 2) {
                val phase = (time * 1.5f + k * 0.5f + y * 0.37f) % 1f
                val x = a.x0 * cell + width * (if (dir > 0) phase else 1f - phase)
                val yy = (y + 0.3f + 0.4f * k) * cell + cell * 0.06f * sin((phase + y) * TAU)
                val len = cell * 0.7f
                val fade = sin(phase * PI.toFloat())
                drawLine(Color.White, Offset(x - dir * len, yy), Offset(x, yy), strokeWidth = cell * 0.035f, cap = StrokeCap.Round, alpha = 0.35f * fade)
            }
        }
    }
}

/** Outlines one area along its border. */
private fun DrawScope.drawAreaOutline(area: Area, cell: Float, color: Color, alpha: Float, fill: Boolean) {
    val tl = Offset(area.x0 * cell, area.y0 * cell)
    val size = Size((area.x1 - area.x0 + 1) * cell, (area.y1 - area.y0 + 1) * cell)
    if (fill) drawRoundRect(color, tl, size, CornerRadius(cell * 0.2f), alpha = alpha * 0.12f)
    drawRoundRect(
        color,
        tl + Offset(cell * 0.04f, cell * 0.04f),
        Size(size.width - cell * 0.08f, size.height - cell * 0.08f),
        CornerRadius(cell * 0.2f),
        style = Stroke(width = cell * 0.045f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.16f, cell * 0.1f))),
        alpha = alpha,
    )
}

/** Where the tasks are: the basin to fill, the area to put out, the seed to wake. Green once done. */
private fun DrawScope.drawGoalAreas(goals: List<LevelGoal>, met: List<Boolean>, state: GameState, cell: Float, time: Float) {
    val pulse = 0.6f + 0.4f * sin(time * TAU)
    goals.forEachIndexed { i, goal ->
        val done = met.getOrElse(i) { false }
        val alpha = if (goal.optional) 0.45f else 0.75f
        when (goal) {
            is TargetContainerFilled -> drawAreaOutline(goal.area, cell, if (done) Palette.success else Palette.waterLight, alpha, fill = true)
            is TargetExtinguished -> goal.area?.let { drawAreaOutline(it, cell, if (done) Palette.success else Palette.fire, alpha, fill = false) }
            is TargetRainTriggered -> goal.area?.let { drawAreaOutline(it, cell, if (done) Palette.success else Palette.rain, alpha, fill = false) }
            is TargetCleared -> goal.area?.let { drawAreaOutline(it, cell, if (done) Palette.success else Palette.ice, alpha, fill = false) }
            is TargetHeated -> drawAreaOutline(goal.area, cell, if (done) Palette.success else Palette.fire, alpha, fill = false)
            is TargetPreserved -> {
                val area = goal.area
                if (area != null) {
                    drawAreaOutline(area, cell, if (done) Palette.success else Palette.accent, alpha, fill = false)
                } else {
                    for (kept in goal.things) state.objectById(kept.id)?.let { drawGoalRing(it.position, cell, done, alpha, pulse) }
                }
            }
            is TargetState -> state.objectById(goal.objectId)?.let { drawGoalRing(it.position, cell, done, alpha, pulse) }
            else -> Unit
        }
    }
}

/** A ring on the ground under the thing a task is about. */
private fun DrawScope.drawGoalRing(at: Position, cell: Float, done: Boolean, alpha: Float, pulse: Float) {
    val center = Offset((at.x + 0.5f) * cell, (at.y + 0.9f) * cell)
    drawOval(
        if (done) Palette.success else Palette.accent,
        Offset(center.x - cell * 0.45f, center.y - cell * 0.12f),
        Size(cell * 0.9f, cell * 0.24f),
        style = Stroke(width = cell * 0.04f),
        alpha = alpha * (if (done) 1f else pulse),
    )
}

/** A light frame around everything the player may move; the selected object glows. */
private fun DrawScope.drawMovableFrames(
    state: GameState,
    shownAt: (GameObject) -> Offset,
    cell: Float,
    selected: String?,
    dragged: String?,
    time: Float,
    showMarkers: Boolean,
) {
    val pulse = 0.5f + 0.5f * sin(time * TAU * 2f)
    // Glowing hot metal cannot be taken: no frame until it has cooled down.
    for (o in state.objects.filter { it.canBePickedUp && it.id != dragged }) {
        if (!showMarkers && o.id != selected) continue
        val at = shownAt(o) * cell
        val inset = cell * 0.04f
        val tl = at + Offset(inset, inset)
        val s = Size(cell - 2 * inset, cell - 2 * inset)
        if (o.id == selected) {
            drawRoundRect(Palette.accent, tl, s, CornerRadius(cell * 0.18f), style = Stroke(width = cell * 0.06f), alpha = 0.6f + 0.4f * pulse)
        } else {
            drawRoundRect(Color.White, tl, s, CornerRadius(cell * 0.18f), style = Stroke(width = cell * 0.05f), alpha = 0.1f)
            drawRoundRect(Color(0xFFFFF4DC), tl, s, CornerRadius(cell * 0.18f), style = Stroke(width = cell * 0.022f), alpha = 0.42f)
        }
    }
}

/** The target cell says what the drop would do; the object hangs under the finger. */
private fun DrawScope.drawDrag(
    d: DragState,
    drop: Drop?,
    state: GameState,
    types: TypeCatalog,
    cell: Float,
    time: Float,
    showPreview: Boolean,
) {
    val obj = state.objectById(d.objectId) ?: return
    val target = Offset(d.hover.x * cell, d.hover.y * cell)
    val home = obj.position == d.hover
    val glow = 0.6f + 0.4f * sin(time * TAU * 3f)
    if (state.inBounds(d.hover) && !home) {
        val color = when {
            drop == null -> Palette.danger
            drop is Drop.Merged && showPreview -> Palette.accent
            drop is Drop.NextTo && showPreview -> Palette.fire
            else -> Palette.success
        }
        drawRoundRect(color, target, Size(cell, cell), CornerRadius(cell * 0.18f), style = Stroke(width = cell * 0.06f), alpha = 0.85f)
        if (drop == null) {
            val c = target + Offset(cell / 2, cell / 2)
            val r = cell * 0.18f
            drawLine(Palette.danger, c - Offset(r, r), c + Offset(r, r), strokeWidth = cell * 0.06f, cap = StrokeCap.Round, alpha = 0.85f)
            drawLine(Palette.danger, c + Offset(r, -r), c + Offset(-r, r), strokeWidth = cell * 0.06f, cap = StrokeCap.Round, alpha = 0.85f)
        }
    }
    if (showPreview && drop is Drop.Merged) {
        // What the two become, floating over the target.
        val result = types.create("preview", drop.rule.result, d.hover)
        val center = target + Offset(cell / 2, -cell * 0.35f)
        drawCircle(Brush.radialGradient(listOf(Palette.accent.copy(alpha = 0.45f * glow), Color.Transparent), center, cell * 0.6f), cell * 0.6f, center)
        scale(0.6f, center) {
            drawGameObject(result, center - Offset(cell / 2, cell / 2), cell, 0.95f, time)
        }
    }
    if (showPreview && drop is Drop.NextTo) {
        // It will land next to what it reacts with.
        val partner = state.objectById(drop.partnerId)
        if (partner != null) {
            val pc = cellCenter(partner.position, cell)
            drawCircle(Brush.radialGradient(listOf(Palette.fire.copy(alpha = 0.35f * glow), Color.Transparent), pc, cell * 0.9f), cell * 0.9f, pc)
        }
        val landing = Offset(drop.landing.x * cell, drop.landing.y * cell)
        drawRoundRect(
            Palette.fire,
            landing,
            Size(cell, cell),
            CornerRadius(cell * 0.18f),
            style = Stroke(width = cell * 0.04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.12f, cell * 0.08f))),
            alpha = 0.8f,
        )
        if (obj.isLiquid) {
            drawCarriedLiquid(obj.type, obj.amount, obj.capacity, landing + Offset(cell / 2, cell * 0.62f), cell, time, alpha = 0.35f)
        } else {
            drawGameObject(obj, landing, cell, 0.35f, time)
        }
    }
    val center = dragCenter(d.pointer, d.grab, cell)
    drawShadow(Offset(center.x / cell - 0.5f, center.y / cell - 0.5f + 0.25f), cell)
    if (obj.isLiquid) {
        // Carried water is a sloshing handful, not a box.
        drawCarriedLiquid(obj.type, obj.amount, obj.capacity, center, cell, time)
    } else {
        drawGameObject(obj, center - Offset(cell / 2, cell / 2), cell, 0.95f, time, infoFor(obj, state))
    }
}

/** Darkened edges give the board a small diorama feel. */
private fun DrawScope.drawVignette() {
    val r = size.maxDimension * 0.8f
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.28f)),
            center = Offset(size.width / 2, size.height / 2),
            radius = r,
        ),
    )
}

/** A red pulse when the cascade protection stopped a world that would not come to rest. */
private fun DrawScope.drawOverload(time: Float) {
    drawRect(Palette.danger, alpha = 0.16f + 0.08f * sin(time * TAU * 4f))
}
