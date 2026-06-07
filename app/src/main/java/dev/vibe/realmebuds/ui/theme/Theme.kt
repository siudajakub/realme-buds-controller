package dev.vibe.realmebuds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF006875),
    onPrimary = Color.White,
    secondary = Color(0xFF6B5E00),
    tertiary = Color(0xFF8A3A42),
    background = Color(0xFFF8F9FA),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE6EDF0),
    onSurface = Color(0xFF172023),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF7BD4E4),
    secondary = Color(0xFFE0C85A),
    tertiary = Color(0xFFFFB2BB),
    background = Color(0xFF101416),
    surface = Color(0xFF171C1F),
    surfaceVariant = Color(0xFF263238),
    onSurface = Color(0xFFE7ECEF),
)

@Composable
fun RealmeBudsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
