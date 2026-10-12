package com.shootcat.react.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shootcat.react.ui.theme.Palette

/** Slim top bar: optional back arrow, a small title and action icons on the right. */
@Composable
fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    overline: String? = null,
    titleLines: Int = 1,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundIconButton(
                glyph = Glyph.BACK,
                description = "Zurück",
                onClick = onBack,
                size = 44.dp,
                background = Color.Transparent,
                modifier = Modifier.testTag("back"),
            )
            Spacer(Modifier.width(4.dp))
        }
        Column(Modifier.weight(1f)) {
            if (overline != null) {
                Text(
                    overline.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 2.sp,
                    color = Palette.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Palette.text,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            actions()
        }
    }
}

/** Circular icon-only button; [description] is read by screen readers and used by tests. */
@Composable
fun RoundIconButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    enabled: Boolean = true,
    tint: Color = Palette.text,
    background: Color = Palette.surfaceHigh,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .alpha(if (enabled) 1f else 0.35f),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, color = tint, size = size * 0.42f)
    }
}

/** Pill button with icon and a single-line label, shortened with "…" if it does not fit. */
@Composable
fun PillButton(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val content = if (primary) Color(0xFF1A1206) else Palette.text
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .then(
                if (primary) {
                    Modifier.background(Brush.verticalGradient(listOf(Palette.accent, Palette.accentDeep)))
                } else {
                    Modifier.background(Palette.surfaceHigh).border(1.dp, Palette.outline, RoundedCornerShape(50))
                },
            )
            .clickable(onClick = onClick)
            .semantics { role = Role.Button }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, color = if (primary) content else Palette.accent, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = content,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
