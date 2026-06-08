package dev.davidfdev.stela.di

import android.content.Context
import androidx.room.Room
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.data.StelaDatabase
import dev.davidfdev.stela.notifications.AndroidNotificationController
import dev.davidfdev.stela.notifications.NotificationController
import dev.davidfdev.stela.pin.NotePinner

/// Process-wide singletons, built once by the Application. The UI and the pin
/// service both read notes through this single [NoteRepository] instance and pin
/// through this single [NotePinner].
class AppContainer(context: Context) {

    private val database: StelaDatabase =
        Room.databaseBuilder(context, StelaDatabase::class.java, DATABASE_NAME).build()

    val noteRepository: NoteRepository = NoteRepository(database.noteDao())

    val notificationController: NotificationController = AndroidNotificationController(context)

    val notePinner: NotePinner = NotePinner(noteRepository, notificationController)

    private companion object {
        const val DATABASE_NAME = "stela.db"
    }
}
