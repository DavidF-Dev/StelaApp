package dev.davidfdev.stela.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.davidfdev.stela.StelaApp
import dev.davidfdev.stela.data.AndroidBackupIo
import dev.davidfdev.stela.data.BackupCodec
import dev.davidfdev.stela.data.BackupIo
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.SettingsRepository
import dev.davidfdev.stela.settings.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/// One-shot outcomes of an export/import, surfaced to the UI as a snackbar.
sealed interface BackupEvent {
    data object Exported : BackupEvent
    data class Imported(val count: Int) : BackupEvent
    data object ExportFailed : BackupEvent
    data object ImportFailed : BackupEvent
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val noteRepository: NoteRepository,
    private val backupIo: BackupIo,
) : ViewModel() {

    val uiState: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), Settings())

    private val eventsChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()

    /// Writes all notes as JSON to the picked document Uri.
    fun export(uri: Uri) {
        viewModelScope.launch {
            val event = try {
                backupIo.write(uri, BackupCodec.encode(noteRepository.notes.first()))
                BackupEvent.Exported
            } catch (e: Exception) {
                BackupEvent.ExportFailed
            }
            eventsChannel.send(event)
        }
    }

    /// Reads the picked document Uri and appends its notes (a malformed file fails cleanly).
    fun import(uri: Uri) {
        viewModelScope.launch {
            val event = try {
                val notes = BackupCodec.decode(backupIo.read(uri)).getOrThrow()
                noteRepository.importNotes(notes)
                BackupEvent.Imported(notes.size)
            } catch (e: Exception) {
                BackupEvent.ImportFailed
            }
            eventsChannel.send(event)
        }
    }

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
                SettingsViewModel(
                    app.container.settingsRepository,
                    app.container.noteRepository,
                    AndroidBackupIo(app.contentResolver),
                )
            }
        }
    }
}
