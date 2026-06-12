package dev.davidfdev.stela.ui

import android.accessibilityservice.AccessibilityService
import androidx.test.platform.app.InstrumentationRegistry

/// Presses system Back at the OS level (via `UiAutomation`), bypassing Espresso's `RootViewPicker`.
/// A transient tooltip popup window (added by `TooltipBox` after a programmatic click) can otherwise
/// confuse the picker into waiting on the wrong, unfocused root; a real OS back has no such issue.
fun pressSystemBack() {
    InstrumentationRegistry.getInstrumentation().uiAutomation
        .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
}
