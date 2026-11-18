package com.stellar.simulcast.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.stellar.simulcast.data.Settings

data class Palette(val id: String, val name: String, val primary: Color, val secondary: Color)

val PALETTES = listOf(
    Palette("NEON", "Neon", Color(0xFF00E5FF), Color(0xFFFF2BD6)),
    Palette("AURORA", "Aurora", Color(0xFF1DE9B6), Color(0xFFB2FF59)),
    Palette("SOLAR", "Solar", Color(0xFFFFB300), Color(0xFFFF5252)),
    Palette("NEBULA", "Nebula", Color(0xFFB388FF), Color(0xFFFF80AB)),
    Palette("ICE", "Ice", Color(0xFF82B1FF), Color(0xFFE1F5FE)),
    Palette("CRIMSON", "Crimson", Color(0xFFFF1744), Color(0xFFFFD740)),
)

fun paletteFor(id: String): Palette = PALETTES.firstOrNull { it.id == id } ?: PALETTES[0]

/**
 * App colors. "Cyan" and "Magenta" are the accent pair and follow the chosen theme
 * (they read Compose state, so the whole app recolors instantly).
 */
object Space {
    val Bg = Color(0xFF05060F)
    val Surface = Color(0xFF0D1024)
    val Card = Color(0xCC0F1330)
    val Text = Color(0xFFE6F1FF)
    val Muted = Color(0xFF8A93B8)
    val Cyan: Color get() = paletteFor(Settings.display.accent).primary
    val Magenta: Color get() = paletteFor(Settings.display.accent).secondary
}

@Composable
fun SimulcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Space.Cyan,
            onPrimary = Space.Bg,
            secondary = Space.Magenta,
            onSecondary = Space.Bg,
            background = Space.Bg,
            onBackground = Space.Text,
            surface = Space.Surface,
            onSurface = Space.Text,
            surfaceVariant = Color(0xFF161A3A),
            onSurfaceVariant = Space.Muted,
            outline = Color(0xFF2A3166),
        ),
        content = content,
    )
}
