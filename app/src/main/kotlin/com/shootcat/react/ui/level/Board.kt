package com.shootcat.react.ui.level

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.shootcat.react.engine.Reactions
import com.shootcat.react.engine.model.GameObject
import com.shootcat.react.engine.model.GameState
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.Props
import com.shootcat.react.engine.model.Rule
import com.shootcat.react.engine.model.Trigger
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.components.ObjectInfo
import com.shootcat.react.ui.components.drawGameObject
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The part of the grid worth showing: every open cell plus a thin rim of wall. Outer rows and
 * columns that are only wall are cut away, so the playing field gets as much of the screen as possible.
 */
internal data class Viewport(val left: Float, val top: Float, val width: Float, val height: Float) {
    companion object {
        private const val RIM = 0.3f

        fun of(state: GameState): Viewport {
            var minX = state.width
            var maxX = -1
            var minY = state.height
            var maxY = -1
            for (y in 0 until state.height) {
                for (x in 0 until state.width) {
                    if (state.isWall(Position(x, y))) continue
                    minX = min(minX, x)
                    maxX = max(maxX, x)
                    minY = min(minY, y)
                    maxY = max(maxY, y)
                }
            }
            if (maxX < 0) return Viewport(0f, 0f, state.width.toFloat(), state.height.toFloat())
            val left = max(0f, minX - RIM)
            val top = max(0f, minY - RIM)
            val right = min(state.width.toFloat(), maxX + 1 + RIM)
            val bottom = min(state.height.toFloat(), maxY + 1 + RIM)
            return Viewport(left, top, right - left, bottom - top)
        }
    }
}

/** A dragged object: [grab] keeps the finger where it touched the object, so it does not jump. */
private data class DragState(val objectId: String, val type: String, val pointer: Offset, val grab: Offset, val hover: Position)

/**
 * The playing field, drawn with a Compose Canvas.
 *
 * While [interactive], movable objects can be dragged (or tapped, then a target cell tapped). Touches
 * do not have to be exact: the nearest movable object within a finger's reach is picked.
 * [previous] and [progress] interpolate movement between two simulation steps.
 */
@Composable
fun Board(
    state: GameState,
    previous: GameState?,
    progress: Float,
    rules: List<Rule>,
    types: TypeCatalog,
    interactive: Boolean,
    overload: Boolean,
    onMove: (String, Position) -> Unit,
    modifier: Modifier = Modifier,
    showMarkers: Boolean = true,
    showPreview: Boolean = true,
    haptics: Boolean = true,
) {
    val haptic = LocalHapticFeedback.current
    val transition = rememberInfiniteTransition(label = "board")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    var drag by remember { mutableStateOf<DragState?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    val currentState by rememberUpdatedState(state)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentHaptics by rememberUpdatedState(haptics)

    LaunchedEffect(interactive) {
        drag = null
        selected = null
    }
    // A selected object that melted, burnt or was undone away is no longer selected.
    val shownSelection = selected?.takeIf { state.objectById(it)?.movable == true }

    val thresholds = remember(rules) {
        rules.filter { it.trigger == Trigger.LOAD }.associate { it.conditions.target to it.conditions.minLoad }
    }
    val signalRules = remember(rules) { rules.filter { it.trigger == Trigger.SIGNAL } }
    val view = remember(state.width, state.height, state.walls) { Viewport.of(state) }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val cellDp = minOf(maxWidth / view.width, maxHeight / view.height)
        Canvas(
            Modifier
                .size(cellDp * view.width, cellDp * view.height)
                .clipToBounds()
                .testTag("board")
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    detectDragGestures(
                        onDragStart = { offset ->
                            val cell = size.width.toFloat() / view.width
                            val obj = pickMovable(currentState, view, offset, cell, exactFirst = true)
                            if (obj != null) {
                                selected = null
                                val center = cellCenter(obj.position, view, cell)
                                drag = DragState(obj.id, obj.type, offset, center - offset, obj.position)
                            }
                        },
                        onDrag = { change, amount ->
                            val d = drag
                            if (d != null) {
                                change.consume()
                                val cell = size.width.toFloat() / view.width
                                val pointer = d.pointer + amount
                                drag = d.copy(pointer = pointer, hover = cellAt(dragCenter(pointer, d.grab, cell), view, cell))
                            }
                        },
                        onDragEnd = {
                            val d = drag
                            drag = null
                            if (d != null) {
                                if (currentHaptics && currentState.isBuildable(d.hover)) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                currentOnMove(d.objectId, d.hover)
                            }
                        },
                        onDragCancel = { drag = null },
                    )
                }
                .pointerInput(interactive) {
                    if (!interactive) return@pointerInput
                    detectTapGestures(
                        onTap = { offset ->
                            val cell = size.width.toFloat() / view.width
                            val p = cellAt(offset, view, cell)
                            val exact = currentState.objectAt(p)?.takeIf { it.movable }
                            val sel = selected?.takeIf { currentState.objectById(it)?.movable == true }
                            when {
                                exact != null -> selected = if (sel == exact.id) null else exact.id
                                sel != null && currentState.isBuildable(p) -> {
                                    currentOnMove(sel, p)
                                    selected = null
                                }
                                else -> {
                                    val near = pickMovable(currentState, view, offset, cell, exactFirst = false)
                                    selected = if (near == null || near.id == sel) null else near.id
                                }
                            }
                        },
                    )
                },
        ) {
            val cell = size.width / view.width
            if (cell <= 0f) return@Canvas
            val d = drag
            val placing = d != null || shownSelection != null
            translate(-view.left * cell, -view.top * cell) {
                drawBackground(state, cell, showGrid = interactive && placing)
                drawWires(state, signalRules, cell, time)

                drawLiquids(state, previous, progress, cell, time)
                for (o in state.objects) {
                    if (o.isLiquid) continue
                    val prev = previous?.objectById(o.id)
                    val from = prev?.position ?: o.position
                    val x = from.x + (o.position.x - from.x) * progress
                    val y = from.y + (o.position.y - from.y) * progress
                    val appearing = previous != null && prev == null
                    val alpha = when {
                        o.id == d?.objectId -> 0.25f
                        appearing -> progress
                        else -> 1f
                    }
                    drawGameObject(o, Offset(x * cell, y * cell), cell, alpha, time, infoFor(o, state, thresholds))
                }
                if (previous != null) {
                    for (gone in previous.objects.filter { !it.isLiquid && state.objectById(it.id) == null }) {
                        val tl = Offset(gone.position.x * cell, gone.position.y * cell)
                        drawGameObject(gone, tl, cell, 1f - progress, time, infoFor(gone, previous, thresholds))
                    }
                }

                if (interactive) {
                    drawMovableHints(state, cell, shownSelection, d?.objectId, time, showMarkers)
                    if (d != null) drawDrag(d, view, state, rules, types, cell, time, showPreview)
                }
            }
            drawVignette()
            if (overload) drawOverload(cell, time)
        }
    }
}

private const val LIFT = 0.6f

/** How far from an object's centre a touch still picks it up, in cells (at least [MIN_REACH_DP]). */
private const val REACH = 0.95f
private const val MIN_REACH_DP = 30

private fun cellCenter(p: Position, view: Viewport, cell: Float) =
    Offset((p.x + 0.5f - view.left) * cell, (p.y + 0.5f - view.top) * cell)

private fun cellAt(offset: Offset, view: Viewport, cell: Float) =
    Position(floor(offset.x / cell + view.left).toInt(), floor(offset.y / cell + view.top).toInt())

/** Where a dragged object's centre is drawn: at the finger plus its grab offset, lifted above the fingertip. */
private fun dragCenter(pointer: Offset, grab: Offset, cell: Float) = pointer + grab - Offset(0f, cell * LIFT)

/** The movable object under the finger, or else the nearest one within reach of it. */
private fun PointerInputScope.pickMovable(
    state: GameState,
    view: Viewport,
    offset: Offset,
    cell: Float,
    exactFirst: Boolean,
): GameObject? {
    if (exactFirst) {
        state.objectAt(cellAt(offset, view, cell))?.takeIf { it.movable }?.let { return it }
    }
    val reach = max(cell * REACH, MIN_REACH_DP.dp.toPx())
    return state.objects
        .filter { it.movable }
        .map { it to (cellCenter(it.position, view, cell) - offset).let { d -> hypot(d.x, d.y) } }
        .filter { it.second <= reach }
        .minByOrNull { it.second }
        ?.first
}

private fun infoFor(o: GameObject, state: GameState, thresholds: Map<String, Int>): ObjectInfo {
    val threshold = thresholds[o.type]
    if (threshold != null) {
        return ObjectInfo(load = state.loadStack(o.position).sumOf { it.load }, threshold = threshold)
    }
    if (!o.isFluid && !o.conducts) return ObjectInfo()
    // Fluids and metal rods merge visually with neighbouring cells of the same kind.
    fun same(p: Position) = state.objectAt(p)?.type == o.type
    val p = o.position
    return ObjectInfo(
        joinLeft = same(p.left()),
        joinRight = same(p.right()),
        joinBelow = same(p.down()),
        joinAbove = same(p.up()),
    )
}

private fun DrawScope.drawBackground(state: GameState, cell: Float, showGrid: Boolean) {
    drawRect(
        Brush.verticalGradient(listOf(Palette.backgroundTop, Palette.background)),
        Offset.Zero,
        Size(state.width * cell, state.height * cell),
    )
    for (y in 0 until state.height) {
        for (x in 0 until state.width) {
            val p = Position(x, y)
            val tl = Offset(x * cell, y * cell)
            if (state.isWall(p)) {
                drawRect(if ((x * 7 + y * 3) % 4 == 0) Palette.wallAlt else Palette.wall, tl, Size(cell + 0.5f, cell + 0.5f))
                val above = p.up()
                if (state.inBounds(above) && !state.isWall(above)) {
                    drawRect(Palette.wallTop, tl, Size(cell + 0.5f, cell * 0.1f))
                }
                val below = p.down()
                if (state.inBounds(below) && !state.isWall(below)) {
                    drawRect(Palette.wallShadow, Offset(tl.x, tl.y + cell * 0.92f), Size(cell + 0.5f, cell * 0.08f))
                }
            } else if (showGrid && p in state.noBuild) {
                // Closed areas: the player cannot drop anything here.
                for (k in 0..2) {
                    val o = cell * (k / 3f)
                    drawLine(Color.Black, Offset(tl.x + o, tl.y + cell), Offset(tl.x + cell, tl.y + o), strokeWidth = cell * 0.03f, alpha = 0.22f)
                }
            } else if (showGrid && state.objectAt(p) == null) {
                drawCircle(Color.White, cell * 0.035f, tl + Offset(cell / 2, cell / 2), alpha = 0.1f)
            }
        }
    }
}

/** Darkened edges give the board a small diorama feel. */
private fun DrawScope.drawVignette() {
    val r = size.maxDimension * 0.75f
    drawRect(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)),
            center = Offset(size.width / 2, size.height / 2),
            radius = r,
        ),
    )
}

/** Dashed lines between signal sources and the objects they drive; bright while the signal is on. */
private fun DrawScope.drawWires(state: GameState, signalRules: List<Rule>, cell: Float, time: Float) {
    for (rule in signalRules) {
        val c = rule.conditions
        for (target in state.objects.filter { it.type == c.target }) {
            val channel = target.string(Props.CHANNEL) ?: continue
            for (source in state.objects.filter { it.type == c.source && it.string(Props.CHANNEL) == channel }) {
                val active = c.sourceState == null || source.state == c.sourceState
                val start = Offset((source.position.x + 0.5f) * cell, (source.position.y + 0.5f) * cell)
                val end = Offset((target.position.x + 0.5f) * cell, (target.position.y + 0.5f) * cell)
                val dash = cell * 0.14f
                drawLine(
                    color = if (active) Palette.signal else Palette.textDim,
                    start = start,
                    end = end,
                    strokeWidth = cell * (if (active) 0.06f else 0.035f),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash), if (active) -time * dash * 8 else 0f),
                    alpha = if (active) 0.85f else 0.22f,
                )
            }
        }
    }
}

private fun DrawScope.drawMovableHints(
    state: GameState,
    cell: Float,
    selected: String?,
    dragged: String?,
    time: Float,
    showMarkers: Boolean,
) {
    val pulse = 0.5f + 0.5f * sin(time * 2f * PI.toFloat() * 2f)
    for (o in state.objects.filter { it.movable && it.id != dragged }) {
        if (!showMarkers && o.id != selected) continue
        val inset = cell * 0.02f
        val tl = Offset(o.position.x * cell + inset, o.position.y * cell + inset)
        val s = Size(cell - 2 * inset, cell - 2 * inset)
        if (o.id == selected) {
            drawRoundRect(Palette.accent, tl, s, CornerRadius(cell * 0.14f), style = Stroke(width = cell * 0.06f), alpha = 0.6f + 0.4f * pulse)
        } else {
            drawRoundRect(
                Palette.accent,
                tl,
                s,
                CornerRadius(cell * 0.14f),
                style = Stroke(width = cell * 0.03f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cell * 0.1f, cell * 0.08f))),
                alpha = 0.45f,
            )
        }
    }
}

private fun DrawScope.drawDrag(
    d: DragState,
    view: Viewport,
    state: GameState,
    rules: List<Rule>,
    types: TypeCatalog,
    cell: Float,
    time: Float,
    showPreview: Boolean,
) {
    val target = Offset(d.hover.x * cell, d.hover.y * cell)
    val free = state.isBuildable(d.hover) || state.objectById(d.objectId)?.position == d.hover
    if (state.inBounds(d.hover)) {
        drawRoundRect(
            if (free) Palette.success else Palette.danger,
            target,
            Size(cell, cell),
            CornerRadius(cell * 0.14f),
            style = Stroke(width = cell * 0.05f),
            alpha = 0.8f,
        )
    }
    // Subtle reaction preview: hint that something *could* happen here, never what.
    if (free && showPreview) {
        val partners = Reactions.touchPartners(d.type, rules, types)
        val glow = 0.25f + 0.2f * sin(time * 2f * PI.toFloat() * 3f)
        for (n in d.hover.neighbours()) {
            val other = state.objectAt(n) ?: continue
            if (other.id == d.objectId || other.type !in partners) continue
            val center = Offset((n.x + 0.5f) * cell, (n.y + 0.5f) * cell)
            drawCircle(
                brush = Brush.radialGradient(listOf(Palette.accent.copy(alpha = glow), Color.Transparent), center, cell * 0.8f),
                radius = cell * 0.8f,
                center = center,
            )
        }
    }
    val obj = state.objectById(d.objectId) ?: return
    // The pointer is in canvas coordinates; this scope is shifted by the viewport.
    val center = dragCenter(d.pointer, d.grab, cell) + Offset(view.left * cell, view.top * cell)
    drawGameObject(obj, center - Offset(cell / 2, cell / 2), cell, 0.9f, time)
}

/** Short-circuit flash when the cascade protection stopped the simulation. */
private fun DrawScope.drawOverload(cell: Float, time: Float) {
    drawRect(Palette.danger, alpha = 0.16f + 0.08f * sin(time * 2f * PI.toFloat() * 4f))
    val bolt = Path().apply {
        val w = size.width
        val h = size.height
        moveTo(w * 0.55f, h * 0.1f)
        lineTo(w * 0.4f, h * 0.45f)
        lineTo(w * 0.55f, h * 0.45f)
        lineTo(w * 0.42f, h * 0.9f)
    }
    drawPath(bolt, Palette.danger, alpha = 0.8f, style = Stroke(width = cell * 0.12f, cap = StrokeCap.Round))
}
