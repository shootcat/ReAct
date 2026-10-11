package com.shootcat.react.ui.title

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.BuildConfig
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Renderer
import com.shootcat.react.sim.World
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.PillButton
import com.shootcat.react.ui.components.RoundIconButton
import com.shootcat.react.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private const val TAU = (2 * PI).toFloat()

/** Start screen: a tiny live world of the real simulation above the title. */
@Composable
fun TitleScreen(
    materials: MaterialTable,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "title")
    val time by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(18000, easing = LinearEasing), RepeatMode.Restart),
        label = "time",
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
            LiveScene(materials, Modifier.fillMaxWidth().aspectRatio(TitleScene.W / TitleScene.H.toFloat()))
            Spacer(Modifier.height(20.dp))
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
            PillButton(Glyph.PLAY, "Spielplatz", onPlay, Modifier.fillMaxWidth(0.72f), primary = true)
            Spacer(Modifier.height(24.dp))
            RoundIconButton(Glyph.GEAR, "Einstellungen", onSettings, size = 56.dp)
            Spacer(Modifier.height(32.dp))
            Text(
                BuildConfig.VERSION_NAME,
                style = MaterialTheme.typography.labelSmall,
                color = Palette.textDim.copy(alpha = 0.6f),
            )
        }
    }
}

/** The little world of the title, stepped once per frame on the UI thread (it is tiny). */
@Composable
private fun LiveScene(materials: MaterialTable, modifier: Modifier) {
    val scene = remember(materials) { TitleScene(materials) }
    var frame by remember { mutableIntStateOf(0) }
    LaunchedEffect(scene) {
        while (true) {
            scene.advance()
            frame++
            delay(16)
        }
    }
    Canvas(modifier.clip(RoundedCornerShape(18.dp))) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        val dst = IntSize(size.width.toInt(), size.height.toInt())
        drawImage(scene.image, dstSize = dst, filterQuality = FilterQuality.None)
        drawImage(scene.hazeImage, dstSize = dst, filterQuality = FilterQuality.Low)
        drawImage(scene.flameImage, dstSize = dst, filterQuality = FilterQuality.Low)
        drawImage(scene.lightImage, dstSize = dst, filterQuality = FilterQuality.Low)
    }
}

/** Lava runs down into a pond and sets, sand trickles onto a dune, a log burns: then it all begins again. */
private class TitleScene(materials: MaterialTable) {
    private val world = World(W, H, materials, 11L)
    private val start: World.Snapshot
    private val renderer: Renderer
    private val pixels = IntArray(W * H)
    private val haze: IntArray
    private val light: IntArray
    private val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val hazeBitmap: Bitmap
    private val lightBitmap: Bitmap
    val image = bitmap.asImageBitmap()
    val hazeImage: ImageBitmap
    val lightImage: ImageBitmap
    private val flames: IntArray
    private val flameBitmap: Bitmap
    val flameImage: ImageBitmap
    private val lava = materials.id("lava")
    private val sand = materials.id("sand")
    private val fire = materials.id("fire")

    init {
        val rock = materials.id("rock")
        val water = materials.id("water")
        val wood = materials.id("wood")
        val earth = materials.id("earth")
        for (x in 0 until W) {
            // a slope on the left, a pond in the middle, a shelf on the right
            val ground = when {
                x < 34 -> 22 + x
                x < 86 -> 56 + ((x - 60) * (x - 60)) / 260
                else -> 50 - (x - 86) / 3
            }.coerceIn(10, H - 1)
            for (y in ground until H) world.set(x, y, if (y < ground + 3 && x > 86) earth else rock)
        }
        for (x in 34 until 86) for (y in 50 until H) if (world.material(x, y) == MaterialTable.AIR) world.set(x, y, water)
        for (x in 52 until 70) for (y in 40 until 43) world.set(x, y, wood)
        for (x in 59 until 62) for (y in 43 until 50) world.set(x, y, wood)
        world.wakeAll()
        start = world.snapshot()
        renderer = Renderer(world, IntArray(W * H) { BACKGROUND })
        haze = IntArray(renderer.layerWidth * renderer.layerHeight)
        light = IntArray(renderer.layerWidth * renderer.layerHeight)
        hazeBitmap = Bitmap.createBitmap(renderer.layerWidth, renderer.layerHeight, Bitmap.Config.ARGB_8888)
        lightBitmap = Bitmap.createBitmap(renderer.layerWidth, renderer.layerHeight, Bitmap.Config.ARGB_8888)
        hazeImage = hazeBitmap.asImageBitmap()
        lightImage = lightBitmap.asImageBitmap()
        flames = IntArray(renderer.flameWidth * renderer.flameHeight)
        flameBitmap = Bitmap.createBitmap(renderer.flameWidth, renderer.flameHeight, Bitmap.Config.ARGB_8888)
        flameImage = flameBitmap.asImageBitmap()
    }

    fun advance() {
        val t = world.step
        if (t % 600 < 200 && t % 2 == 0) world.paint(lava, 6, 4, 1)
        if (t % 3 == 0) world.paint(sand, W - 14, 2, 1, 0.5f)
        if (t % 600 == 60) world.paint(fire, 60, 38, 2, 0.8f)
        world.step()
        if (world.step >= LOOP) world.restore(start)
        renderer.render(pixels, t, running = true)
        renderer.renderLayers(haze, light)
        renderer.renderFlames(flames)
        flameBitmap.setPixels(flames, 0, renderer.flameWidth, 0, 0, renderer.flameWidth, renderer.flameHeight)
        bitmap.setPixels(pixels, 0, W, 0, 0, W, H)
        hazeBitmap.setPixels(haze, 0, renderer.layerWidth, 0, 0, renderer.layerWidth, renderer.layerHeight)
        lightBitmap.setPixels(light, 0, renderer.layerWidth, 0, 0, renderer.layerWidth, renderer.layerHeight)
    }

    companion object {
        const val W = 120
        const val H = 64
        const val LOOP = 1800
        const val BACKGROUND = 0xFF141C28.toInt()
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
