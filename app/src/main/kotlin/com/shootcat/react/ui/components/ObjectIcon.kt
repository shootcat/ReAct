package com.shootcat.react.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.engine.ReactionSymbol
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.theme.Palette

/**
 * A single object drawn as an icon. With [silhouette] only its dark outline shape is shown,
 * which is how unknown reactions appear in the Discovery Log.
 */
@Composable
fun ObjectIcon(
    typeId: String?,
    types: TypeCatalog,
    modifier: Modifier = Modifier,
    state: String? = null,
    silhouette: Boolean = false,
    size: Dp = 32.dp,
    background: Color = Palette.surfaceHigh,
    symbol: ReactionSymbol? = null,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.25f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        val type = typeId?.let { types[it] }
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val w = this.size.width
            if (type == null) {
                when (symbol) {
                    ReactionSymbol.PRESSURE -> drawPressure(w)
                    ReactionSymbol.SIGNAL -> drawSignal(w)
                    ReactionSymbol.HEAT -> drawHeat(w)
                    ReactionSymbol.POWER -> drawPower(w)
                    else -> drawWeight(w)
                }
            } else {
                // Slightly smaller and lower than a board cell, so what reaches above its cell (tree crowns) stays visible.
                val obj = types.create("icon", type.id, Position(0, 0), state = state ?: type.defaultState)
                drawGameObject(obj, Offset(w * 0.14f, w * 0.2f), w * 0.72f, info = ObjectInfo())
            }
            if (silhouette) drawRect(Color(0xFF07090C), blendMode = BlendMode.SrcAtop)
        }
        if (silhouette) {
            Text("?", color = Palette.textDim, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Generic "weight" symbol for load conditions. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWeight(c: Float) {
    val body = Path().apply {
        moveTo(c * 0.3f, c * 0.38f)
        lineTo(c * 0.7f, c * 0.38f)
        lineTo(c * 0.82f, c * 0.82f)
        lineTo(c * 0.18f, c * 0.82f)
        close()
    }
    drawCircle(Palette.stoneDark, c * 0.12f, Offset(c * 0.5f, c * 0.3f))
    drawCircle(Palette.surfaceHigh, c * 0.06f, Offset(c * 0.5f, c * 0.3f))
    drawPath(body, Palette.stone)
    drawRect(Palette.stoneDark, Offset(c * 0.3f, c * 0.55f), Size(c * 0.4f, c * 0.06f))
}

/** Steam pressure: arrows pushing up. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPressure(c: Float) {
    for (i in 0..2) {
        val x = c * (0.28f + 0.22f * i)
        val arrow = Path().apply {
            moveTo(x, c * 0.22f)
            lineTo(x + c * 0.1f, c * 0.38f)
            lineTo(x - c * 0.1f, c * 0.38f)
            close()
        }
        drawPath(arrow, Palette.steam)
        drawRect(Palette.steam, Offset(x - c * 0.03f, c * 0.38f), Size(c * 0.06f, c * 0.36f))
    }
}

/** A signal: a small lightning bolt. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSignal(c: Float) {
    val bolt = Path().apply {
        moveTo(c * 0.58f, c * 0.12f)
        lineTo(c * 0.28f, c * 0.55f)
        lineTo(c * 0.48f, c * 0.55f)
        lineTo(c * 0.4f, c * 0.88f)
        lineTo(c * 0.74f, c * 0.42f)
        lineTo(c * 0.53f, c * 0.42f)
        close()
    }
    drawPath(bolt, Palette.signal)
}

/** Heat: three wavy lines rising. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeat(c: Float) {
    for (i in 0..2) {
        val x = c * (0.3f + 0.2f * i)
        val wave = Path().apply {
            moveTo(x, c * 0.82f)
            cubicTo(x - c * 0.12f, c * 0.66f, x + c * 0.12f, c * 0.5f, x, c * 0.36f)
            cubicTo(x - c * 0.08f, c * 0.28f, x + c * 0.04f, c * 0.2f, x, c * 0.16f)
        }
        drawPath(
            wave,
            if (i == 1) Palette.glow else Palette.fire,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = c * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
    }
}

/** Electric current: a zigzag spark between two poles. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPower(c: Float) {
    val spark = Path().apply {
        moveTo(c * 0.18f, c * 0.5f)
        lineTo(c * 0.36f, c * 0.32f)
        lineTo(c * 0.48f, c * 0.62f)
        lineTo(c * 0.62f, c * 0.34f)
        lineTo(c * 0.82f, c * 0.5f)
    }
    drawCircle(Palette.woodLight, c * 0.08f, Offset(c * 0.14f, c * 0.5f))
    drawCircle(Palette.woodLight, c * 0.08f, Offset(c * 0.86f, c * 0.5f))
    drawPath(
        spark,
        Palette.signal,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = c * 0.07f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            join = androidx.compose.ui.graphics.StrokeJoin.Round,
        ),
    )
}
