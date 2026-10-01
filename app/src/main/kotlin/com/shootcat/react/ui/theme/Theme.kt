package com.shootcat.react.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val background = Color(0xFF0E1116)
    val backgroundTop = Color(0xFF18202C)
    val surface = Color(0xFF171C24)
    val surfaceHigh = Color(0xFF222A36)
    val outline = Color(0xFF2E3746)
    val text = Color(0xFFE8EDF3)
    val textDim = Color(0xFF8A96A8)
    val accent = Color(0xFFFFB347)
    val success = Color(0xFF5BD48A)
    val danger = Color(0xFFFF5C6C)
    val wall = Color(0xFF29303B)
    val wallAlt = Color(0xFF2D3440)
    val wallTop = Color(0xFF3D4656)
    val fire = Color(0xFFFF7A2F)
    val fireCore = Color(0xFFFFD166)
    val ice = Color(0xFFB5ECFC)
    val iceDeep = Color(0xFF5BBFE0)
    val water = Color(0xFF3D8BFF)
    val waterLight = Color(0xFF8CC0FF)
    val stone = Color(0xFF9A9FA8)
    val stoneDark = Color(0xFF5E636D)
    val wood = Color(0xFFA0703C)
    val woodDark = Color(0xFF6E4A26)
    val brass = Color(0xFFC9A227)
    val buttonUp = Color(0xFFE0644B)
    val signal = Color(0xFFFFD166)
}

@Composable
fun ReactTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            onPrimary = Color(0xFF1A1206),
            secondary = Palette.ice,
            onSecondary = Color(0xFF06141A),
            background = Palette.background,
            onBackground = Palette.text,
            surface = Palette.surface,
            onSurface = Palette.text,
            surfaceVariant = Palette.surfaceHigh,
            onSurfaceVariant = Palette.textDim,
            outline = Palette.outline,
        ),
        content = content,
    )
}
