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
        assertFalse(settings.swipeToRemove)
        assertEquals(RemovalPreference.UNPIN, settings.removalPreference)
        assertEquals(SortOrder.MODIFIED, settings.sortOrder)
        assertFalse(settings.sortReversed)
        assertEquals(NoteFilter.ALL, settings.noteFilter)
        assertFalse(settings.onboardingComplete)
        assertFalse(settings.dynamicColor)
    }

    @Test
    fun storedValues_areReadBack() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.THEME_MODE to ThemeMode.LIGHT.name,
            SettingsKeys.HIDE_ON_LOCK_SCREEN to true,
            SettingsKeys.QUICK_ADD_ENABLED to false,
            SettingsKeys.SWIPE_TO_REMOVE to true,
            SettingsKeys.REMOVAL_PREFERENCE to RemovalPreference.ARCHIVE.name,
            SettingsKeys.SORT_ORDER to SortOrder.TITLE.name,
            SettingsKeys.SORT_REVERSED to true,
            SettingsKeys.NOTE_FILTER to NoteFilter.PINNED.name,
            SettingsKeys.ONBOARDING_COMPLETE to true,
            SettingsKeys.DYNAMIC_COLOR to true,
        )

        val settings = settingsFromPreferences(prefs)

        assertEquals(ThemeMode.LIGHT, settings.themeMode)
        assertTrue(settings.hideOnLockScreen)
        assertFalse(settings.quickAddEnabled)
        assertTrue(settings.swipeToRemove)
        assertEquals(RemovalPreference.ARCHIVE, settings.removalPreference)
        assertEquals(SortOrder.TITLE, settings.sortOrder)
        assertTrue(settings.sortReversed)
        assertEquals(NoteFilter.PINNED, settings.noteFilter)
        assertTrue(settings.onboardingComplete)
        assertTrue(settings.dynamicColor)
    }

    @Test
    fun unparsableThemeMode_fallsBackToDefault() {
        val prefs = mutablePreferencesOf(SettingsKeys.THEME_MODE to "PURPLE")

        assertEquals(ThemeMode.SYSTEM, settingsFromPreferences(prefs).themeMode)
    }

    @Test
    fun unparsableSortFilterAndRemoval_fallBackToDefaults() {
        val prefs = mutablePreferencesOf(
            SettingsKeys.SORT_ORDER to "SIDEWAYS",
            SettingsKeys.NOTE_FILTER to "MAYBE",
            SettingsKeys.REMOVAL_PREFERENCE to "SHRED",
        )

        val settings = settingsFromPreferences(prefs)

        assertEquals(SortOrder.MODIFIED, settings.sortOrder)
        assertEquals(NoteFilter.ALL, settings.noteFilter)
        assertEquals(RemovalPreference.UNPIN, settings.removalPreference)
    }
}
