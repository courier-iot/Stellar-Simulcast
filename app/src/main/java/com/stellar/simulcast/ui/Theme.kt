package com.stellar.simulcast.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Space {
    val Bg = Color(0xFF05060F)
    val Surface = Color(0xFF0D1024)
    val Card = Color(0xCC0F1330)
    val Cyan = Color(0xFF00E5FF)
    val Magenta = Color(0xFFFF2BD6)
    val Violet = Color(0xFF7C4DFF)
    val Text = Color(0xFFE6F1FF)
    val Muted = Color(0xFF8A93B8)
}

@Composable
fun SimulcastTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Space.Cyan,
            onPrimary = Space.Bg,
            secondary = Space.Magenta,
            onSecondary = Space.Bg,
            tertiary = Space.Violet,
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
