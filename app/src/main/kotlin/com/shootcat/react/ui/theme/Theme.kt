package com.shootcat.react.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val background = Color(0xFF0B0F15)
    val backgroundTop = Color(0xFF141C28)
    val surface = Color(0xFF151B24)
    val surfaceHigh = Color(0xFF1F2733)
    val outline = Color(0xFF2E3746)
    val text = Color(0xFFE8EDF3)
    val textDim = Color(0xFF7F8B9D)
    val accent = Color(0xFFFFB347)
    val accentDeep = Color(0xFFE8892B)
    val success = Color(0xFF5BD48A)
    val danger = Color(0xFFFF5C6C)
    val wall = Color(0xFF262D38)
    val wallAlt = Color(0xFF2A323E)
    val wallTop = Color(0xFF3D4757)
    val wallShadow = Color(0xFF191E26)
    val fire = Color(0xFFFF7A2F)
    val fireCore = Color(0xFFFFD166)
    val ice = Color(0xFFB5ECFC)
    val iceDeep = Color(0xFF5BBFE0)
    val water = Color(0xFF2F7BEF)
    val waterLight = Color(0xFF7DB6FF)
    val steam = Color(0xFFE6EEF6)
    val stone = Color(0xFF9A9FA8)
    val stoneDark = Color(0xFF5E636D)
    val wood = Color(0xFFA0703C)
    val woodDark = Color(0xFF6E4A26)
    val woodLight = Color(0xFFC89A5E)
    val charcoal = Color(0xFF2F2724)
    val metal = Color(0xFF9AA6B6)
    val metalDark = Color(0xFF515B69)
    val glow = Color(0xFFFF5A1F)
    val lava = Color(0xFFFF6A1A)
    val lavaDeep = Color(0xFFB3261E)
    val lavaCrust = Color(0xFF3A1E17)
    val oil = Color(0xFF5A3A12)
    val oilLight = Color(0xFF9C6A22)
    val sand = Color(0xFFD8B878)
    val sandDark = Color(0xFFA8844A)
    val sandWet = Color(0xFF8A6A3A)
    val power = Color(0xFF7FD8FF)
    val copper = Color(0xFFC77B4A)
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
