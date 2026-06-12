package dev.davidfdev.stela.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.davidfdev.stela.R

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 60 * MINUTE_MILLIS
private const val DAY_MILLIS = 24 * HOUR_MILLIS

private val PRESETS = listOf(
    R.string.snooze_30_minutes to 30 * MINUTE_MILLIS,
    R.string.snooze_1_hour to HOUR_MILLIS,
    R.string.snooze_3_hours to 3 * HOUR_MILLIS,
    R.string.snooze_1_day to DAY_MILLIS,
)

/// The snooze chooser: a dialog of duration presets that, via "Custom…", switches to Days / Hours /
/// Minutes fields. [onPick] receives the absolute re-pin time (`now + duration`); [now] is injectable for
/// tests. Hosted by the shared overflow menu, so the editor and popup share it.
@Composable
internal fun SnoozeChooser(onPick: (Long) -> Unit, onDismiss: () -> Unit, now: () -> Long = System::currentTimeMillis) {
    var customMode by remember { mutableStateOf(false) }
    if (customMode) {
        CustomDurationDialog(onPick = { onPick(now() + it) }, onDismiss = onDismiss)
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.snooze_title)) },
            text = {
                Column {
                    PRESETS.forEach { (labelRes, duration) ->
                        PresetRow(stringResource(labelRes)) { onPick(now() + duration) }
                    }
                    PresetRow(stringResource(R.string.snooze_custom)) { customMode = true }
                }
            },
            // Presets act on tap, so there is no separate confirm.
            confirmButton = {},
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PresetRow(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}

@Composable
private fun CustomDurationDialog(onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("") }
    val total = days.digitsToLong() * DAY_MILLIS + hours.digitsToLong() * HOUR_MILLIS + minutes.digitsToLong() * MINUTE_MILLIS

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.snooze_title)) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DurationField(days, { days = it.toDigits(3) }, stringResource(R.string.snooze_days), Modifier.weight(1f))
                DurationField(hours, { hours = it.toDigits(2) }, stringResource(R.string.snooze_hours), Modifier.weight(1f))
                DurationField(minutes, { minutes = it.toDigits(2) }, stringResource(R.string.snooze_minutes), Modifier.weight(1f))
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(total) }, enabled = total > 0) {
                Text(stringResource(R.string.snooze_confirm))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun DurationField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        // The unit sits below the field (small) rather than as a floating label, which is too wide here.
        placeholder = { Text("0") },
        supportingText = { Text(label, maxLines = 1) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun String.toDigits(maxLength: Int): String = filter(Char::isDigit).take(maxLength)

private fun String.digitsToLong(): Long = toLongOrNull() ?: 0L
