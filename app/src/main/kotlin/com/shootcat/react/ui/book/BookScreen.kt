package com.shootcat.react.ui.book

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.sim.Discoveries
import com.shootcat.react.sim.Renderer
import com.shootcat.react.sim.World
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.sandbox.FolkIcon
import com.shootcat.react.ui.sandbox.drawFolk
import com.shootcat.react.ui.theme.Palette
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The discovery book (Leitfaden V3, 4.3): for each material the properties the player has seen for
 * themselves, as plain statements ("Sand behält Wärme"), never as "X + Y → Z". Each entry plays its little
 * scene when tapped. What has not been seen yet stays a question mark. The ways of the inhabitants (Phase 5) have a
 * group of their own.
 */
@Composable
fun BookScreen(book: Discoveries, found: Set<String>, onBack: () -> Unit) {
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val groups = remember(book) { book.all.groupBy { it.spec.subject ?: "m${it.material}" }.toList() }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = "Entdeckungsbuch",
            overline = "${found.count { id -> book.all.any { it.id == id } }} von ${book.all.size} entdeckt",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("book"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groups, key = { it.first }) { (_, entries) ->
                val seen = entries.filter { it.id in found }
                val m = entries.first().material
                val subject = entries.first().spec.subject
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Palette.surface)
                        .padding(12.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (subject != null) {
                            FolkIcon()
                        } else {
                            Canvas(Modifier.size(18.dp).clip(RoundedCornerShape(4.dp))) { drawRect(Color(book.materials.color[m])) }
                        }
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (seen.isEmpty()) "???" else subject ?: book.materials.spec(m).name,
                            color = Palette.text,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    for (e in entries) {
                        val known = e.id in found
                        Text(
                            if (known) e.spec.text else "…",
                            color = if (known) Palette.text else Palette.textDim,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .then(if (known) Modifier.clickable { open = if (open == e.id) null else e.id } else Modifier)
                                .padding(vertical = 4.dp, horizontal = 4.dp)
                                .testTag("entry_${e.id}"),
                        )
                        if (known && open == e.id) SceneView(book, e)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The entry's little scene, running in a loop: what the property looks like. */
@Composable
private fun SceneView(book: Discoveries, entry: Discoveries.Entry) {
    val w = Discoveries.SCENE_W
    val h = Discoveries.SCENE_H
    val bitmap = remember(entry.id) { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    var frames by remember { mutableIntStateOf(0) }
    // the scene's world, for its inhabitants (drawn over the picture, as in the sandbox)
    var scene by remember { mutableStateOf<World?>(null) }
    LaunchedEffect(entry.id) {
        val pixels = IntArray(w * h)
        val sky = IntArray(w * h) { if (it / w < h / 2) 0xFF9CC3E0.toInt() else 0xFF3A302A.toInt() }
        while (true) {
            val world = book.scene(entry)
            scene = world
            val renderer = Renderer(world, sky).also { it.ambient = BOOK_AMBIENT }
            while (world.step < Discoveries.SCENE_STEPS) {
                world.step()
                renderer.render(pixels, world.step, running = true)
                bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
                frames++
                delay(16)
            }
        }
    }
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .aspectRatio(w / h.toFloat())
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, Palette.outline, RoundedCornerShape(10.dp))
            .testTag("scene_${entry.id}"),
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frames
        val s = minOf(size.width / w, size.height / h)
        val o = IntOffset(((size.width - w * s) / 2f).roundToInt(), ((size.height - h * s) / 2f).roundToInt())
        drawImage(
            image,
            dstOffset = o,
            dstSize = IntSize((w * s).roundToInt(), (h * s).roundToInt()),
            filterQuality = FilterQuality.None,
        )
        scene?.let { if (it.folk.count > 0 || it.folk.homeX >= 0) drawFolk(it.folk, it.step, Offset(o.x.toFloat(), o.y.toFloat()), s) }
    }
}

/** Dark scenes (moss, fireflies) stay faintly visible in the book. */
private const val BOOK_AMBIENT = 0.3f
