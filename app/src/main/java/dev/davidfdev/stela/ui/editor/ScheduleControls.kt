package dev.davidfdev.stela.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davidfdev.stela.R
import dev.davidfdev.stela.ui.TimeFormatter
import dev.davidfdev.stela.ui.TooltipIconButton
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/// The editor's auto-pin / auto-unpin controls (the contents of the Advanced section): a row each for
/// "Pin at" and "Unpin at", each opening a date-then-time picker. Times are epoch millis; null means unset.
/// "Pin at" is disabled while the note is pinned (no pending future pin). "Unpin at" is enabled only while
/// the note is pinned *or* a future "Pin at" is set — i.e. whenever there is (or will be) a pin to end;
/// disabled for an unpinned, unscheduled note (nothing to auto-unpin).
@Composable
fun ScheduleControls(
    pinAt: Long?,
    unpinAt: Long?,
    isPinned: Boolean,
    onPinAtChange: (Long?) -> Unit,
    onUnpinAtChange: (Long?) -> Unit,
    now: () -> Long = System::currentTimeMillis,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        ScheduleRow(
            label = stringResource(R.string.schedule_pin_at),
            timeMillis = pinAt,
            // Future only.
            earliestMillis = now(),
            enabled = !isPinned,
            onChange = { newPinAt ->
                onPinAtChange(newPinAt)
                // Clearing a pending future pin on an unpinned note also drops the window's end, which
                // would otherwise be stranded behind the now-disabled "Unpin at" row.
                if (newPinAt == null && !isPinned) onUnpinAtChange(null)
            },
        )
        ScheduleRow(
            label = stringResource(R.string.schedule_unpin_at),
            timeMillis = unpinAt,
            // After the pin time if one is set, else future.
            earliestMillis = pinAt ?: now(),
            enabled = isPinned || pinAt != null,
            onChange = onUnpinAtChange,
        )
    }
}

/// The editor's "Alert when pinned" toggle (an Advanced control): when on, the note's notification plays
/// the system default sound/vibration once each time it pins — manual or scheduled. Always enabled, since
/// it applies to any future pin.
@Composable
fun PinAlertControl(
    alertOnPin: Boolean,
    onAlertOnPinChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.alert_on_pin_label), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(R.string.alert_on_pin_supporting),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = alertOnPin, onCheckedChange = onAlertOnPinChange)
    }
}

@Composable
private fun ScheduleRow(
    label: String,
    timeMillis: Long?,
    earliestMillis: Long,
    enabled: Boolean,
    onChange: (Long?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val contentAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
            )
            Text(
                text = timeMillis?.let { TimeFormatter.absolute(it) } ?: stringResource(R.string.schedule_not_set),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
            )
        }
        if (enabled && timeMillis != null) {
            TooltipIconButton(
                icon = Icons.Filled.Close,
                label = stringResource(R.string.schedule_clear),
                onClick = { onChange(null) },
            )
        }
        TextButton(onClick = { showPicker = true }, enabled = enabled) {
            Text(stringResource(if (timeMillis == null) R.string.schedule_set else R.string.schedule_change))
        }
    }
    if (showPicker) {
        DateTimePickerDialog(
            initialMillis = (timeMillis ?: earliestMillis).coerceAtLeast(earliestMillis),
            earliestMillis = earliestMillis,
            onConfirm = { onChange(it); showPicker = false },
            onDismiss = { showPicker = false },
        )
    }
}

/// A two-step picker: a date dialog, then a time dialog, combined into one epoch-millis instant in the
/// device's zone. Days before [earliestMillis]'s day aren't selectable; a same-day earlier time is
/// allowed and simply takes effect immediately on save (a past time fires at once).
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimePickerDialog(
    initialMillis: Long,
    earliestMillis: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    // Picked date is held between the two steps; null means the date step is still showing.
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    if (pickedDateMillis == null) {
        val earliestDay = Instant.ofEpochMilli(earliestMillis).atZone(zone).toLocalDate()
        // DatePicker interprets its millis as UTC, so a raw local instant would highlight the day on the
        // UTC side of the zone offset (e.g. "yesterday", greyed out). Snap the local calendar day to UTC
        // midnight so the initial selection lands on the right day and stays selectable.
        val initialDay = Instant.ofEpochMilli(initialMillis).atZone(zone).toLocalDate()
        val initialSelectedUtc = initialDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = initialSelectedUtc,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isBefore(earliestDay)
            },
        )
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = { pickedDateMillis = dateState.selectedDateMillis },
                    enabled = dateState.selectedDateMillis != null,
                ) { Text(stringResource(R.string.schedule_next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        ) {
            DatePicker(state = dateState)
        }
    } else {
        val initial = Instant.ofEpochMilli(initialMillis).atZone(zone)
        val is24Hour = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
        val timeState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    // DatePicker reports UTC midnight of the chosen day; pair it with the picked time in
                    // the device's zone to get the final instant.
                    val date = Instant.ofEpochMilli(pickedDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                    val millis = date.atTime(timeState.hour, timeState.minute).atZone(zone).toInstant().toEpochMilli()
                    onConfirm(millis)
                }) { Text(stringResource(R.string.schedule_done)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
            text = { TimePicker(state = timeState) },
        )
    }
}
