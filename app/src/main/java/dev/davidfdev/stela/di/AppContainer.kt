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
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.pin.PinServiceController
import dev.davidfdev.stela.pin.ServiceController
import dev.davidfdev.stela.settings.DataStoreSettingsRepository
import dev.davidfdev.stela.settings.SettingsRepository

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/// Process-wide singletons, built once by the Application. The UI and the pin
/// service read notes through this single [NoteRepository] instance, preferences
/// through this single [SettingsRepository], and pin through this single
/// [NotePinner].
class AppContainer(context: Context) {

    private val database: StelaDatabase =
        Room.databaseBuilder(context, StelaDatabase::class.java, DATABASE_NAME)
            .addMigrations(StelaDatabase.MIGRATION_1_2, StelaDatabase.MIGRATION_2_3)
            .build()

    val noteRepository: NoteRepository = NoteRepository(database.noteDao())

    val settingsRepository: SettingsRepository = DataStoreSettingsRepository(context.settingsDataStore)

    val notificationController: NotificationController = AndroidNotificationController(context)

    val serviceController: ServiceController = PinServiceController(context)

    val notePinner: NotePinner =
        NotePinner(noteRepository, notificationController, serviceController, settingsRepository)

    private companion object {
        const val DATABASE_NAME = "stela.db"
    }
}
