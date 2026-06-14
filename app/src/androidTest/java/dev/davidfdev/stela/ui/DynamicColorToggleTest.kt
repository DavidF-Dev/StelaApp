package dev.davidfdev.stela.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.ui.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DynamicColorToggleTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The toggling-updates-state path is covered by SettingsViewModelTest; this verifies the row renders
    // (it is gated on API 31+, which the test device satisfies).
    @Test
    fun dynamicColorRow_isShown() {
        composeRule.setContent {
            StelaTheme(darkTheme = true) {
                SettingsScreen(
                    state = Settings(),
                    noteCount = 0,
                    snackbarHostState = SnackbarHostState(),
                    batteryExempt = false,
                    autostartAvailable = false,
                    onThemeModeChange = {},
                    onDynamicColorChange = {},
                    onHideOnLockScreenChange = {},
                    onSwipeToRemoveChange = {},
                    onRemovalPreferenceChange = {},
                    onQuickAddEnabledChange = {},
                    onAddTile = {},
                    onOpenBatterySettings = {},
                    onOpenAutostart = {},
                    onExport = {},
                    onImport = {},
                    onClearNotes = {},
                    onReplayOnboarding = {},
                    onOpenAbout = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("Use system colours").assertIsDisplayed()
    }
}
