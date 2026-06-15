package dev.davidfdev.stela.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.data.StelaDatabase
import dev.davidfdev.stela.notifications.AndroidNotificationController
import dev.davidfdev.stela.notifications.NotificationController
import dev.davidfdev.stela.pin.AlarmPinScheduler
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.pin.PinScheduler
import dev.davidfdev.stela.pin.PinServiceController
import dev.davidfdev.stela.pin.ServiceController
import dev.davidfdev.stela.settings.DataStoreSettingsRepository
import dev.davidfdev.stela.settings.SettingsRepository
import dev.davidfdev.stela.ui.editor.NoteDraft

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/// Process-wide singletons, built once by the Application. The UI and the pin
/// service read notes through this single [NoteRepository] instance, preferences
/// through this single [SettingsRepository], and pin through this single
/// [NotePinner].
class AppContainer(context: Context) {

    private val database: StelaDatabase =
        Room.databaseBuilder(context, StelaDatabase::class.java, DATABASE_NAME)
            .addMigrations(
                StelaDatabase.MIGRATION_1_2,
                StelaDatabase.MIGRATION_2_3,
                StelaDatabase.MIGRATION_3_4,
                StelaDatabase.MIGRATION_4_5,
            )
            .build()

    val noteRepository: NoteRepository = NoteRepository(database.noteDao())

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)

    val notificationController: NotificationController = AndroidNotificationController(context)

    val serviceController: ServiceController = PinServiceController(context)

    val pinScheduler: PinScheduler = AlarmPinScheduler(context)

    val notePinner: NotePinner =
        NotePinner(noteRepository, notificationController, serviceController, settingsRepository, pinScheduler)

    /// One-shot hand-off from the quick-note popup's Expand to the full editor: the popup writes the
    /// in-progress edit here, the editor's view-model reads and clears it on creation.
    @Volatile
    var pendingDraft: NoteDraft? = null

    /// Whether the main UI is currently on-screen (started). The quick-note popup reads this to route a
    /// notification/widget trigger into the open app instead of floating over it (which would open a second
    /// editor for the same note). Set from the activity's onStart/onStop.
    @Volatile
    var isMainActivityVisible: Boolean = false

    /// Whether the editor's "Advanced" section is expanded, remembered across editor opens within the
    /// process (it resets to collapsed on a cold start, by design — not persisted).
    @Volatile
    var editorAdvancedExpanded: Boolean = false

    private companion object {
        const val DATABASE_NAME = "stela.db"
    }
}
