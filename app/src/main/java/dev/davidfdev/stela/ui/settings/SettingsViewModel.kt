package dev.davidfdev.stela.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.SettingsRepository
import dev.davidfdev.stela.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val uiState: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), Settings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setHideOnLockScreen(value: Boolean) {
        viewModelScope.launch { repository.setHideOnLockScreen(value) }
    }

    fun setQuickAddEnabled(value: Boolean) {
        viewModelScope.launch { repository.setQuickAddEnabled(value) }
    }

    fun setSwipeToUnpin(value: Boolean) {
        viewModelScope.launch { repository.setSwipeToUnpin(value) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StelaApp
                SettingsViewModel(app.container.settingsRepository)
            }
        }
    }
}
