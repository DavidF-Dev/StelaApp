package dev.davidfdev.stela.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme()

/// Stela's app theme. The app is dark-only by design, so it always applies the
/// Material 3 dark color scheme.
@Composable
fun StelaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, content = content)
}
