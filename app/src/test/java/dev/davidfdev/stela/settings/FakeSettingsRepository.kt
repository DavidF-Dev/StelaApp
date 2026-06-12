package dev.davidfdev.stela.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: Settings = Settings()) : SettingsRepository {

    private val state = MutableStateFlow(initial)
    override val settings: Flow<Settings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setHideOnLockScreen(value: Boolean) {
        state.update { it.copy(hideOnLockScreen = value) }
    }

    override suspend fun setQuickAddEnabled(value: Boolean) {
        state.update { it.copy(quickAddEnabled = value) }
    }

    override suspend fun setSwipeToRemove(value: Boolean) {
        state.update { it.copy(swipeToRemove = value) }
    }

    override suspend fun setRemovalPreference(value: RemovalPreference) {
        state.update { it.copy(removalPreference = value) }
    }

    override suspend fun setSortOrder(value: SortOrder) {
        state.update { it.copy(sortOrder = value) }
    }

    override suspend fun setSortReversed(value: Boolean) {
        state.update { it.copy(sortReversed = value) }
    }

    override suspend fun setNoteFilter(value: NoteFilter) {
        state.update { it.copy(noteFilter = value) }
    }
}
