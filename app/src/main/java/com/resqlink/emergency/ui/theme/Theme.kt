package com.resqlink.emergency.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFDC2626),
    secondary = Color(0xFFEF4444),
    tertiary = Color(0xFF991B1B),
    background = Color(0xFF030712),
    surface = Color(0xFF111827),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFF9FAFB),
    onSurface = Color(0xFFF9FAFB)
)

@Composable
fun SirenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
