package dev.davidfdev.stela.ui.resilience

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.davidfdev.stela.R

/// A guidance dialog for an OEM keep-alive screen (battery optimisation or auto-start): manual [steps]
/// plus a "may not work on every device" caveat and a best-effort "open settings" shortcut. Shared by
/// Settings and onboarding, because the raw system intents are unreliable on some OEMs and the manual
/// steps are the dependable path.
@Composable
internal fun OemGuidanceDialog(
    title: String,
    steps: String,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(steps)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.oem_dialog_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.oem_dialog_open)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.oem_dialog_close)) }
        },
    )
}
