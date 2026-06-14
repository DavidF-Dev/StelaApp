package dev.davidfdev.stela.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/// The current wall-clock time as Compose state, refreshed when the screen resumes and every
/// [periodMillis] while it stays resumed — so relative timestamps stay current without re-entering the
/// screen. The loop runs only while RESUMED, so nothing ticks in the background.
@Composable
fun rememberCurrentTimeMillis(periodMillis: Long = 60_000L): Long {
    val lifecycleOwner = LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                now = System.currentTimeMillis()
                delay(periodMillis)
            }
        }
    }
    return now
}
