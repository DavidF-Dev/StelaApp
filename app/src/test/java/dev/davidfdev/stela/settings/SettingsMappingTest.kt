package dev.davidfdev.stela.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsMappingTest {

    @Test
    fun emptyPreferences_yieldDefaults() {
        val settings = settingsFromPreferences(mutablePreferencesOf())

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertFalse(settings.hideOnLockScreen)
        assertTrue(settings.quickAddEnabled)
    }

    @Test
    fun storedValues_areReadBack() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.THEME_MODE to ThemeMode.LIGHT.name,
            SettingsKeys.HIDE_ON_LOCK_SCREEN to true,
            SettingsKeys.QUICK_ADD_ENABLED to false,
        )

        val settings = settingsFromPreferences(prefs)

        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertTrue(settings.hideOnLockScreen)
        assertFalse(settings.quickAddEnabled)
    }

    @Test
    fun unparsableThemeMode_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(SettingsKeys.THEME_MODE to "PURPLE")

        assertEquals(ThemeMode.SYSTEM, settingsFromPreferences(prefs).themeMode)
    }
}
