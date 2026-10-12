package com.shootcat.react.ui.level

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.data.LevelBest
import com.shootcat.react.level.Level
import com.shootcat.react.level.Medal
import com.shootcat.react.sim.Backdrop
import com.shootcat.react.sim.MaterialTable
import com.shootcat.react.sim.Renderer
import com.shootcat.react.sim.World
import com.shootcat.react.ui.components.Glyph
import com.shootcat.react.ui.components.GlyphIcon
import com.shootcat.react.ui.components.TopBar
import com.shootcat.react.ui.sandbox.MedalBadge
import com.shootcat.react.ui.sandbox.clock
import com.shootcat.react.ui.sandbox.medalColor
import com.shootcat.react.ui.theme.Palette

/**
 * The levels (Leitfaden V3, 5.4), in the order they are meant to be played: a picture of each, its region
 * and how hard it is, what it asks, and the best medal so far with its time and actions. All are open.
 */
@Composable
fun LevelsScreen(
    levels: List<Level>,
    materials: MaterialTable,
    best: Map<String, LevelBest>,
    onPlay: (Int) -> Unit,
    onBack: () -> Unit,
) {
    val pictures = remember(levels, materials) { levels.map { runCatching { picture(it, materials) }.getOrNull() } }
    Column(Modifier.fillMaxSize()) {
        val gold = levels.count { best[it.spec.id]?.medal == Medal.GOLD }
        TopBar(
            title = "Level",
            overline = "${best.keys.count { id -> levels.any { it.spec.id == id } }} von ${levels.size} gelöst · $gold Gold",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        if (levels.isEmpty()) {
            Text("Die Level konnten nicht geladen werden.", color = Palette.danger, modifier = Modifier.padding(24.dp))
        } else Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("level_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            for ((k, level) in levels.withIndex()) {
                LevelCard(k, level, pictures[k], best[level.spec.id]) { onPlay(k) }
            }
        }
    }
}

@Composable
private fun LevelCard(index: Int, level: Level, picture: ImageBitmap?, best: LevelBest?, onClick: () -> Unit) {
    val spec = level.spec
    Column(
        Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Palette.surface)
            .border(1.dp, if (best != null) medalColor(best.medal).copy(alpha = 0.6f) else Palette.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
            .testTag("level_${spec.id}"),
    ) {
        if (picture != null) {
            Image(
                picture,
                contentDescription = spec.name,
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(level.width / level.height.toFloat())
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.height(8.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${index + 1}. ${spec.name}", color = Palette.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("${spec.area} · ${spec.tier}", color = Palette.textDim, fontSize = 12.sp)
            }
            MedalBadge(best?.medal, size = 30.dp)
        }
        Spacer(Modifier.height(6.dp))
        for (goal in spec.goals) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.TARGET, color = Palette.accent, size = 14.dp)
                Spacer(Modifier.width(6.dp))
                Text(goal.text, color = Palette.text, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        val tools = buildList {
            if (spec.tools.bucket) add("Eimer (${spec.tools.bucketSize})")
            if (spec.tools.hand) add("Hand")
            if (spec.tools.build) add("Bauen aus dem Lager")
        }
        Text("Werkzeug: ${tools.joinToString(", ").ifEmpty { "keines" }}", color = Palette.textDim, fontSize = 12.sp)
        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                if (best == null) {
                    "Noch nicht gelöst"
                } else {
                    val medal = if (best.medal == Medal.NONE) "ohne Medaille" else best.medal.label
                    "Bestes: $medal · ${clock(best.seconds)} · ${best.actions} ${if (best.actions == 1) "Griff" else "Griffe"}"
                },
                color = if (best == null) Palette.textDim else medalColor(best.medal),
                fontSize = 12.sp,
                fontWeight = if (best == null) FontWeight.Normal else FontWeight.SemiBold,
            )
        }
    }
}

/** The level as it begins, drawn by the same renderer as the game. */
private fun picture(level: Level, materials: MaterialTable): ImageBitmap {
    val world = World(level.width, level.height, materials, level.spec.seed)
    level.build(world)
    val pixels = IntArray(level.width * level.height)
    Renderer(world, Backdrop.of(world)).render(pixels, 0)
    return Bitmap.createBitmap(pixels, level.width, level.height, Bitmap.Config.ARGB_8888).asImageBitmap()
}
