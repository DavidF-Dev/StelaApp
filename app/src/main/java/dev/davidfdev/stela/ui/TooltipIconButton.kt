package dev.davidfdev.stela.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/// Wraps an anchor in a plain Material 3 tooltip that appears above it on long-press (touch) or hover.
/// The encapsulation point for the experimental tooltip API; use it for buttons that aren't a plain
/// [IconButton] (e.g. a `FilledIconButton` or a FAB) — the icon-only common case has [TooltipIconButton].
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonTooltip(label: String, content: @Composable () -> Unit) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        // A tooltip is informational; it must not steal window focus from the anchor's window.
        focusable = false,
    ) {
        content()
    }
}

/// An [IconButton] whose [label] is the single source for both its long-press tooltip and the icon's
/// content description, so the two never drift. For a toggle, pass the current icon and label.
@Composable
fun TooltipIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    ButtonTooltip(label) {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) {
            Icon(icon, contentDescription = label)
        }
    }
}
