package dev.davidfdev.stela.ui

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/// A light haptic tick for a pin toggle: a distinct cue for pinning vs unpinning. A no-op when the user
/// has system haptics off (the platform handles that), so no extra guard is needed.
fun HapticFeedback.performPinToggle(currentlyPinned: Boolean) =
    performHapticFeedback(if (currentlyPinned) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
