package com.medivox.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val colorScheme = darkColorScheme(
    primary = Color(0xFF7BE4D2),
    secondary = Color(0xFF9CCBFF),
    tertiary = Color(0xFFB9A0FF),
    background = Color(0xFF07131F),
    surface = Color(0xFF102333),
    onPrimary = Color(0xFF001B18),
    onSecondary = Color(0xFF081A2D),
    onTertiary = Color(0xFF1E1035),
    onBackground = Color(0xFFEAF3F8),
    onSurface = Color(0xFFEAF3F8),
    error = Color(0xFFFF8D8D),
    onError = Color(0xFF2A0000)
)

@Composable
fun MediVoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
