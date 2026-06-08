package dev.davidfdev.stela.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme()
private val LightColors = lightColorScheme()

/// Stela's app theme. The caller resolves [darkTheme] from the user's theme-mode
/// preference (falling back to the system setting for Follow System).
@Composable
fun StelaTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
}
