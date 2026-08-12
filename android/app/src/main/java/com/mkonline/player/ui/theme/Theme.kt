package com.mkonline.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.mkonline.player.data.ThemeMode

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF0D2B45),
    primaryContainer = Color(0xFF1E88E5),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFA5C4E8),
    background = Color(0xFF11151C),
    onBackground = Color(0xFFE6EAF2),
    surface = Color(0xFF171C26),
    onSurface = Color(0xFFE6EAF2),
    surfaceVariant = Color(0xFF232A38),
    onSurfaceVariant = Color(0xFF9AA5B8),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF4A0000),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1E88E5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBBDEFB),
    onPrimaryContainer = Color(0xFF0D2B45),
    secondary = Color(0xFF4A6E94),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF1A1F26),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1F26),
    surfaceVariant = Color(0xFFE3E8EF),
    onSurfaceVariant = Color(0xFF4A5462),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
)

@Composable
fun MKTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content,
    )
}
