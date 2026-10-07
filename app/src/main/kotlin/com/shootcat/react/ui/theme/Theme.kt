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
    val signal = Color(0xFFFFD166)

    // Nature.
    val leaf = Color(0xFF5DAE4E)
    val leafLight = Color(0xFF8BD06A)
    val leafDark = Color(0xFF2F6B35)
    val bark = Color(0xFF6B4A2E)
    val barkDark = Color(0xFF3F2A1A)
    val sea = Color(0xFF1E8C9A)
    val seaLight = Color(0xFF5CC6C8)
    val snow = Color(0xFFF2F7FB)
    val snowShade = Color(0xFFBCD0E0)
    val salt = Color(0xFFF4F1EA)
    val pumice = Color(0xFFCFC8BC)
    val pumiceDark = Color(0xFF9A9286)
    val basalt = Color(0xFF3A3436)
    val ember = Color(0xFFFF8A3D)
    val cloud = Color(0xFFF4F7FA)
    val cloudDark = Color(0xFF8F9BA8)
    val rain = Color(0xFF9CC9FF)
}

/** The landscape of one world: sky, soil, rock and what grows on top. */
data class WorldLook(
    val skyTop: Color,
    val skyBottom: Color,
    val cave: Color,
    val earth: Color,
    val earthDark: Color,
    val rock: Color,
    val rockDark: Color,
    /** Grass, sand, ash or snow on soil that meets the open air. */
    val cover: Color,
    val coverDark: Color,
    val horizon: Color,
)

object WorldLooks {
    val forest = WorldLook(
        skyTop = Color(0xFF1C3350), skyBottom = Color(0xFF4F7A8C), cave = Color(0xFF14110F),
        earth = Color(0xFF5A3F2B), earthDark = Color(0xFF3B2A1E), rock = Color(0xFF6B6F75), rockDark = Color(0xFF45484E),
        cover = Color(0xFF5DAE4E), coverDark = Color(0xFF2F6B35), horizon = Color(0xFF1F3D2E),
    )
    val coast = WorldLook(
        skyTop = Color(0xFF2A5D8F), skyBottom = Color(0xFF9CC9E0), cave = Color(0xFF17191C),
        earth = Color(0xFFB08E5E), earthDark = Color(0xFF7C6240), rock = Color(0xFF8A8F96), rockDark = Color(0xFF5A5F66),
        cover = Color(0xFFE2C98E), coverDark = Color(0xFFB89A5E), horizon = Color(0xFF2B7F98),
    )
    val volcano = WorldLook(
        skyTop = Color(0xFF1A1016), skyBottom = Color(0xFF6B2E26), cave = Color(0xFF120C0C),
        earth = Color(0xFF3D302C), earthDark = Color(0xFF251C1A), rock = Color(0xFF3A3436), rockDark = Color(0xFF221E20),
        cover = Color(0xFF6B6461), coverDark = Color(0xFF443F3D), horizon = Color(0xFF2A1715),
    )
    val frost = WorldLook(
        skyTop = Color(0xFF26385A), skyBottom = Color(0xFFB7CCE0), cave = Color(0xFF111620),
        earth = Color(0xFF4E5A6B), earthDark = Color(0xFF353E4B), rock = Color(0xFF7C8796), rockDark = Color(0xFF55606F),
        cover = Color(0xFFF2F7FB), coverDark = Color(0xFFBCD0E0), horizon = Color(0xFF8DA4BF),
    )

    fun of(world: Int): WorldLook = when (world) {
        2 -> coast
        3 -> volcano
        4 -> frost
        else -> forest
    }
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
