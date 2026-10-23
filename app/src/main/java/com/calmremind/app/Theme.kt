package com.calmremind.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DoneGreen = Color(0xFF3FA37A)
val MissedAmber = Color(0xFFFFEBC2)
val MissedAmberText = Color(0xFF7A5200)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F8F8B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EFEC),
    onPrimaryContainer = Color(0xFF0B3D3A),
    secondary = Color(0xFF5B8DB8),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAF6),
    onSecondaryContainer = Color(0xFF14324D),
    background = Color(0xFFF5FAF9),
    onBackground = Color(0xFF16302E),
    surface = Color.White,
    onSurface = Color(0xFF16302E),
    surfaceVariant = Color(0xFFE6F1F0),
    onSurfaceVariant = Color(0xFF47605D),
    outline = Color(0xFF9DB5B2),
    error = Color(0xFFC0564B)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF6FCFC9),
    onPrimary = Color(0xFF00322F),
    primaryContainer = Color(0xFF1F5E5A),
    onPrimaryContainer = Color(0xFFD5EFEC),
    secondary = Color(0xFF9CC3E6),
    onSecondary = Color(0xFF0B2A44),
    secondaryContainer = Color(0xFF2B4A66),
    onSecondaryContainer = Color(0xFFDCEAF6),
    background = Color(0xFF0F1F1E),
    onBackground = Color(0xFFDDEBE9),
    surface = Color(0xFF172A28),
    onSurface = Color(0xFFDDEBE9),
    surfaceVariant = Color(0xFF243A38),
    onSurfaceVariant = Color(0xFFB4CAC7),
    outline = Color(0xFF7F9996),
    error = Color(0xFFE8857B)
)

@Composable
fun CalmRemindTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
