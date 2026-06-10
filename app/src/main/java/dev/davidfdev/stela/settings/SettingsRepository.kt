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
    suspend fun setQuickAddEnabled(value: Boolean)
    suspend fun setSwipeToUnpin(value: Boolean)
    suspend fun setSortOrder(value: SortOrder)
    suspend fun setNoteFilter(value: NoteFilter)
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

    override suspend fun setQuickAddEnabled(value: Boolean) {
        dataStore.edit { it[SettingsKeys.QUICK_ADD_ENABLED] = value }
    }

    override suspend fun setSwipeToUnpin(value: Boolean) {
        dataStore.edit { it[SettingsKeys.SWIPE_TO_UNPIN] = value }
    }

    override suspend fun setSortOrder(value: SortOrder) {
        dataStore.edit { it[SettingsKeys.SORT_ORDER] = value.name }
    }

    override suspend fun setNoteFilter(value: NoteFilter) {
        dataStore.edit { it[SettingsKeys.NOTE_FILTER] = value.name }
    }
}

internal object SettingsKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val HIDE_ON_LOCK_SCREEN = booleanPreferencesKey("hide_on_lock_screen")
    val QUICK_ADD_ENABLED = booleanPreferencesKey("quick_add_enabled")
    val SWIPE_TO_UNPIN = booleanPreferencesKey("swipe_to_unpin")
    val SORT_ORDER = stringPreferencesKey("sort_order")
    val NOTE_FILTER = stringPreferencesKey("note_filter")
}

/// Pure mapping from stored preferences to [Settings], applying defaults for absent
/// or unparsable values. Extracted so it is unit-testable without DataStore.
fun settingsFromPreferences(prefs: Preferences): Settings {
    val defaults = Settings()
    val themeMode = prefs[SettingsKeys.THEME_MODE]
        ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
        ?: defaults.themeMode
    val sortOrder = prefs[SettingsKeys.SORT_ORDER]
        ?.let { runCatching { SortOrder.valueOf(it) }.getOrNull() }
        ?: defaults.sortOrder
    val noteFilter = prefs[SettingsKeys.NOTE_FILTER]
        ?.let { runCatching { NoteFilter.valueOf(it) }.getOrNull() }
        ?: defaults.noteFilter
    return Settings(
        themeMode = themeMode,
        hideOnLockScreen = prefs[SettingsKeys.HIDE_ON_LOCK_SCREEN] ?: defaults.hideOnLockScreen,
        quickAddEnabled = prefs[SettingsKeys.QUICK_ADD_ENABLED] ?: defaults.quickAddEnabled,
        swipeToUnpin = prefs[SettingsKeys.SWIPE_TO_UNPIN] ?: defaults.swipeToUnpin,
        sortOrder = sortOrder,
        noteFilter = noteFilter,
    )
}
