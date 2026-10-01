package com.shootcat.react.ui.title

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.BuildConfig
import com.shootcat.react.engine.model.Position
import com.shootcat.react.engine.model.TypeCatalog
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.PillButton
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.components.drawGameObject
import com.shootcat.react.ui.theme.Palette
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** Animated start screen: a small reaction plays in a loop above the title. */
@Composable
fun TitleScreen(
    types: TypeCatalog,
    onPlay: () -> Unit,
    onDiscoveries: () -> Unit,
    onSettings: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "title")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
    )
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4600, easing = LinearEasing), RepeatMode.Restart),
        label = "cycle",
    )
    val flicker by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "flicker",
    )
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(900)) }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { drawBackdrop(time) }
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.size(280.dp, 170.dp)) { drawEmblem(types, cycle, flicker) }
            Spacer(Modifier.height(12.dp))
            Text(
                "REACT",
                style = TextStyle(
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 16.sp,
                    color = Palette.accent,
                    shadow = Shadow(color = Palette.accentDeep.copy(alpha = 0.6f), offset = Offset.Zero, blurRadius = 32f),
                ),
            )
            Spacer(Modifier.weight(1.2f))
            PillButton(Glyph.PLAY, "Spielen", onPlay, Modifier.fillMaxWidth(0.72f), primary = true)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                RoundIconButton(Glyph.LOG, "Entdeckungen", onDiscoveries, size = 56.dp, tint = Palette.accent)
                RoundIconButton(Glyph.GEAR, "Einstellungen", onSettings, size = 56.dp)
            }
            Spacer(Modifier.height(32.dp))
            Text(
                BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textDim.copy(alpha = 0.6f),
            )
        }
    }
}

/** Fire and ice drift together, melt into water, the water boils off as steam – then it starts over. */
private fun DrawScope.drawEmblem(types: TypeCatalog, p: Float, flicker: Float) {
    val c = size.height * 0.42f
    val cx = size.width / 2
    val top = size.height * 0.5f - c / 2
    fun ease(t: Float) = t * t * (3 - 2 * t)
    fun phase(from: Float, to: Float) = ((p - from) / (to - from)).coerceIn(0f, 1f)

    val approach = ease(phase(0f, 0.38f))
    val fadeOut = 1f - phase(0.88f, 1f)
    val fireX = cx - c * 2.1f + c * 1.1f * approach
    val rightX = cx + c * 1.1f - c * 1.05f * approach

    val melt = phase(0.4f, 0.55f)
    val boil = phase(0.6f, 0.78f)
    val rise = phase(0.6f, 1f)

    // Contact flash.
    val flash = 1f - kotlin.math.abs(p - 0.42f) / 0.08f
    if (flash > 0f) {
        val center = Offset(cx, top + c / 2)
        drawCircle(
            brush = Brush.radialGradient(listOf(Palette.signal.copy(alpha = 0.5f * flash), Color.Transparent), center, c * 1.6f),
            radius = c * 1.6f,
            center = center,
        )
    }

    drawGameObject(types.create("f", "FIRE", Position(0, 0)), Offset(fireX, top), c, fadeOut, flicker)
    if (melt < 1f) drawGameObject(types.create("i", "ICE", Position(0, 0)), Offset(rightX, top), c, (1f - melt) * fadeOut)
    if (melt > 0f && boil < 1f) {
        drawGameObject(types.create("w", "WATER", Position(0, 0)), Offset(rightX, top), c, melt * (1f - boil) * fadeOut, flicker)
    }
    if (boil > 0f) {
        val y = top - c * 0.9f * rise
        drawGameObject(types.create("s", "STEAM", Position(0, 0)), Offset(rightX, y), c, boil * (1f - phase(0.8f, 1f)), flicker)
    }
}

private fun DrawScope.drawBackdrop(time: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF111A28), Palette.background, Color(0xFF0C1310))))
    val glow = Offset(w / 2, h * 0.36f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Palette.accent.copy(alpha = 0.12f), Color.Transparent), glow, w * 0.8f),
        radius = w * 0.8f,
        center = glow,
    )
    // Embers rising from below.
    for (i in 0 until 26) {
        val seed = (i * 0.618034f) % 1f
        val speed = 1f + (i % 5) * 0.35f
        val ph = (time * speed * 2f + seed) % 1f
        val x = w * ((seed * 7.31f) % 1f) + w * 0.025f * sin(ph * TAU * 2f + i)
        val y = h * (1.05f - ph * 1.1f)
        val r = w * (0.003f + (i % 3) * 0.0018f)
        drawCircle(Palette.fire, r, Offset(x, y), alpha = 0.65f * sin(ph * PI.toFloat()))
    }
    // Ice motes drifting down.
    for (i in 0 until 18) {
        val seed = (i * 0.414214f + 0.3f) % 1f
        val ph = (time * (1.2f + (i % 4) * 0.3f) + seed) % 1f
        val x = w * ((seed * 5.17f) % 1f) + w * 0.03f * sin(ph * TAU + i * 1.7f)
        val y = h * (-0.05f + ph * 1.1f)
        drawCircle(Palette.ice, w * 0.0035f, Offset(x, y), alpha = 0.35f * sin(ph * PI.toFloat()))
    }
    // Dark hills at the bottom.
    val hills = Path().apply {
        moveTo(0f, h * 0.86f)
        cubicTo(w * 0.2f, h * 0.8f, w * 0.35f, h * 0.9f, w * 0.55f, h * 0.84f)
        cubicTo(w * 0.75f, h * 0.78f, w * 0.88f, h * 0.86f, w, h * 0.82f)
        lineTo(w, h)
        lineTo(0f, h)
        close()
    }
    drawPath(hills, Color(0xFF0A0F14))
}
