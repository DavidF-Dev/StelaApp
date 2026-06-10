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
        assertFalse(settings.swipeToUnpin)
        assertEquals(SortOrder.MODIFIED, settings.sortOrder)
        assertFalse(settings.sortReversed)
        assertEquals(NoteFilter.ALL, settings.noteFilter)
    }

    @Test
    fun storedValues_areReadBack() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.THEME_MODE to ThemeMode.LIGHT.name,
            SettingsKeys.HIDE_ON_LOCK_SCREEN to true,
            SettingsKeys.QUICK_ADD_ENABLED to false,
            SettingsKeys.SWIPE_TO_UNPIN to true,
            SettingsKeys.SORT_ORDER to SortOrder.TITLE.name,
            SettingsKeys.SORT_REVERSED to true,
            SettingsKeys.NOTE_FILTER to NoteFilter.PINNED.name,
        )

        val settings = settingsFromPreferences(prefs)

        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertTrue(settings.hideOnLockScreen)
        assertFalse(settings.quickAddEnabled)
        assertTrue(settings.swipeToUnpin)
        assertEquals(SortOrder.TITLE, settings.sortOrder)
        assertTrue(settings.sortReversed)
        assertEquals(NoteFilter.PINNED, settings.noteFilter)
    }

    @Test
    fun unparsableThemeMode_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(SettingsKeys.THEME_MODE to "PURPLE")

        assertEquals(ThemeMode.SYSTEM, settingsFromPreferences(prefs).themeMode)
    }

    @Test
    fun unparsableSortAndFilter_fallBackToDefaults() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.SORT_ORDER to "SIDEWAYS",
            SettingsKeys.NOTE_FILTER to "MAYBE",
        )

        val settings = settingsFromPreferences(prefs)

        assertEquals(SortOrder.MODIFIED, settings.sortOrder)
        assertEquals(NoteFilter.ALL, settings.noteFilter)
    }
}
