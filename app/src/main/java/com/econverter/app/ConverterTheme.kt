package com.econverter.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9BD4BC),
    onPrimary = Color(0xFF003828),
    primaryContainer = Color(0xFF20513F),
    onPrimaryContainer = Color(0xFFB6F1D7),
    secondary = Color(0xFFE7BDBE),
    secondaryContainer = Color(0xFF503638),
    onSecondaryContainer = Color(0xFFFFDADB),
    background = Color(0xFF121513),
    surface = Color(0xFF121513),
    surfaceContainer = Color(0xFF1E2320),
    surfaceContainerLow = Color(0xFF191D1A),
    surfaceContainerHigh = Color(0xFF282D29),
    surfaceContainerHighest = Color(0xFF333834),
    surfaceVariant = Color(0xFF3E4943),
    onSurfaceVariant = Color(0xFFBEC9C1),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF21664C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0EBDD),
    onPrimaryContainer = Color(0xFF103B2A),
    secondary = Color(0xFF805354),
    secondaryContainer = Color(0xFFF6DEDF),
    onSecondaryContainer = Color(0xFF402627),
    background = Color(0xFFF8FAF8),
    surface = Color(0xFFF8FAF8),
    surfaceContainer = Color(0xFFEDF1EE),
    surfaceContainerLow = Color(0xFFF2F5F2),
    surfaceContainerHigh = Color(0xFFE7EDE8),
    surfaceContainerHighest = Color(0xFFE1E9E3),
    surfaceVariant = Color(0xFFE1E9E3),
    onSurfaceVariant = Color(0xFF424E46),
)

@Composable
internal fun ConverterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}
