package com.ander.karttelemetry.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KartColorScheme = darkColorScheme(
    primary = Color(0xFFFF3D3D),
    secondary = Color(0xFFFFC400),
    background = Color.Black,
    surface = Color.Black,
    onPrimary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun KartTelemetryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KartColorScheme,
        content = content,
    )
}
