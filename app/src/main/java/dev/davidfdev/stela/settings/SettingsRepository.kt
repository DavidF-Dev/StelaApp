package dev.davidfdev.stela.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/// Single source of truth for user preferences, parallel to the note repository.
/// Backed by DataStore; a fake backs unit tests.
interface SettingsRepository {
    val settings: Flow<Settings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setHideOnLockScreen(value: Boolean)
}

class DataStoreSettingsRepository(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<Settings> = dataStore.data.map(::settingsFromPreferences)

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[SettingsKeys.THEME_MODE] = mode.name }
    }

    override suspend fun setHideOnLockScreen(value: Boolean) {
        dataStore.edit { it[SettingsKeys.HIDE_ON_LOCK_SCREEN] = value }
    }
}

internal object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val HIDE_ON_LOCK_SCREEN = booleanPreferencesKey("hide_on_lock_screen")
    val QUICK_ADD_ENABLED = booleanPreferencesKey("quick_add_enabled")
}

/// Pure mapping from stored preferences to [Settings], applying defaults for absent
/// or unparsable values. Extracted so it is unit-testable without DataStore.
fun settingsFromPreferences(prefs: Preferences): Settings {
    val defaults = Settings()
    val themeMode = prefs[SettingsKeys.THEME_MODE]
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: defaults.themeMode
    return Settings(
        themeMode = themeMode,
        hideOnLockScreen = prefs[SettingsKeys.HIDE_ON_LOCK_SCREEN] ?: defaults.hideOnLockScreen,
        quickAddEnabled = prefs[SettingsKeys.QUICK_ADD_ENABLED] ?: defaults.quickAddEnabled,
    )
}
