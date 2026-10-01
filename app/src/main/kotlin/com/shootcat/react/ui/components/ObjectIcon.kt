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
                drawWeight(w)
            } else {
                // Slightly smaller and lower than a board cell, so raised parts (button caps) stay visible.
                val obj = types.create("icon", type.id, Position(0, 0), state = state ?: type.defaultState)
                drawGameObject(obj, Offset(w * 0.14f, w * 0.2f), w * 0.72f, info = ObjectInfo(0, 0))
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
