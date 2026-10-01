package com.shootcat.react.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shootcat.react.ui.theme.Palette

/** Top bar used by every screen except the world map. */
@Composable
fun ScreenHeader(
    overline: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) { GlyphIcon(Glyph.BACK, color = Palette.text) }
        Column(Modifier.weight(1f)) {
            Text(overline, style = MaterialTheme.typography.labelMedium, color = Palette.textDim)
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Palette.text)
        }
        trailing()
    }
}

/** Pill-shaped button that opens the Discovery Log and shows how much is known. */
@Composable
fun DiscoveryBadge(found: Int, total: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Palette.surfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.LOG, color = Palette.accent, size = 18.dp)
        Spacer(Modifier.width(6.dp))
        Text("$found/$total", style = MaterialTheme.typography.labelLarge, color = Palette.text)
    }
}
