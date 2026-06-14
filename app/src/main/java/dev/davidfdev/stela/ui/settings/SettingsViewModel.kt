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
import dev.davidfdev.stela.data.Note
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.settings.RemovalPreference
import dev.davidfdev.stela.settings.Settings
import dev.davidfdev.stela.settings.SettingsRepository
import dev.davidfdev.stela.settings.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/// One-shot outcomes of a backup or clear action, surfaced to the UI as a snackbar.
sealed interface BackupEvent {
    data object Exported : BackupEvent
    data class Imported(val count: Int) : BackupEvent
    data object ExportFailed : BackupEvent
    data object ImportFailed : BackupEvent

    /// All notes were cleared; the UI offers an undo.
    data class Cleared(val count: Int) : BackupEvent
}

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val noteRepository: NoteRepository,
    private val notePinner: NotePinner,
    private val backupIo: BackupIo,
) : ViewModel() {

    val uiState: StateFlow<Settings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), Settings())

    /// Total note count (active + archived), so the clear-confirm dialog can show it.
    val noteCount: StateFlow<Int> = noteRepository.notes
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), 0)

    private val eventsChannel = Channel<BackupEvent>(Channel.BUFFERED)
    val events = eventsChannel.receiveAsFlow()
    // The last cleared batch (pre-delete snapshots), held so an Undo can restore it.
    private var recentlyCleared: List<Note> = emptyList()

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

    /// Permanently deletes every note, including archived, via the pin seam — so pinned
    /// notifications are cancelled and the service reconciles. Holds the batch so [undoClear]
    /// can restore it. Settings are left untouched. A no-op when there are no notes.
    fun clearAllNotes() {
        viewModelScope.launch {
            val cleared = noteRepository.notes.first()
            if (cleared.isEmpty()) return@launch
            recentlyCleared = cleared
            notePinner.deleteAll(cleared)
            eventsChannel.send(BackupEvent.Cleared(cleared.size))
        }
    }

    /// Restores the most recently cleared batch (the inverse of [clearAllNotes]): re-pins the
    /// ones that were pinned and keeps archived ones archived.
    fun undoClear() {
        val toRestore = recentlyCleared
        recentlyCleared = emptyList()
        if (toRestore.isNotEmpty()) {
            viewModelScope.launch { notePinner.restore(toRestore) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setHideOnLockScreen(value: Boolean) {
        viewModelScope.launch { repository.setHideOnLockScreen(value) }
    }

    fun setDynamicColor(value: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(value) }
    }

    fun setQuickAddEnabled(value: Boolean) {
        viewModelScope.launch { repository.setQuickAddEnabled(value) }
    }

    fun setSwipeToRemove(value: Boolean) {
        viewModelScope.launch { repository.setSwipeToRemove(value) }
    }

    fun setRemovalPreference(value: RemovalPreference) {
        viewModelScope.launch { repository.setRemovalPreference(value) }
    }

    /// Clears the onboarding-complete flag so the first-run flow shows again; the gate that wraps the app
    /// content re-displays it on the next recomposition.
    fun replayOnboarding() {
        viewModelScope.launch { repository.setOnboardingComplete(false) }
    }

    companion object {
        private const val STOP_TIMEOUT_MILLIS = 5_000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as StelaApp
                SettingsViewModel(
                    app.container.settingsRepository,
                    app.container.noteRepository,
                    app.container.notePinner,
                    AndroidBackupIo(app.contentResolver),
                )
            }
        }
    }
}
