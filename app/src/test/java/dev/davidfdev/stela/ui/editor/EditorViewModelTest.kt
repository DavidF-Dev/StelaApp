package dev.davidfdev.stela.ui.editor

import androidx.lifecycle.SavedStateHandle
import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import dev.davidfdev.stela.pin.FakeServiceController
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class Fixture {
        val dao = FakeNoteDao()
        val repository = NoteRepository(dao) { 1_000L }
        val controller = FakeNotificationController()
        val pinner = NotePinner(repository, controller, FakeServiceController(), FakeSettingsRepository())

        fun viewModel(
            noteId: Long? = null,
            pinOnSave: Boolean = false,
            canPost: Boolean = true,
        ): EditorViewModel {
            val map = buildMap<String, Any> {
                if (noteId != null) put("noteId", noteId)
                if (pinOnSave) put("pin", true)
            }
            return EditorViewModel(repository, pinner, SavedStateHandle(map)) { canPost }
        }
    }

    @Test
    fun newNote_save_createsNote() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel()

        assertFalse(viewModel.uiState.value.isEditing)
        viewModel.onTitleChange("Milk")
        viewModel.onDescriptionChange("2L")
        var completed = false
        viewModel.save { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        val notes = f.repository.notes.first()
        assertEquals(1, notes.size)
        assertEquals("Milk", notes[0].title)
        assertEquals("2L", notes[0].description)
    }

    @Test
    fun newNote_save_withPinFlag_pinsCreatedNote() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(pinOnSave = true)

        viewModel.onTitleChange("Pinned on create")
        viewModel.save { }
        advanceUntilIdle()

        val note = f.repository.notes.first().single()
        assertTrue(note.isPinned)
        assertEquals(listOf(note.id), f.controller.pinned.map { it.id })
    }

    @Test
    fun newNote_save_withEmoji_persistsEmoji() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel()

        viewModel.onTitleChange("Groceries")
        viewModel.onEmojiChange("🛒")
        viewModel.save { }
        advanceUntilIdle()

        val note = f.repository.notes.first().single()
        assertEquals("Groceries", note.title)
        assertEquals("🛒", note.emoji)
    }

    @Test
    fun newNote_save_withPinFlag_butNotificationsBlocked_savesUnpinned() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(pinOnSave = true, canPost = false)

        viewModel.onTitleChange("No permission")
        viewModel.save { }
        advanceUntilIdle()

        assertFalse(f.repository.notes.first().single().isPinned)
        assertTrue(f.controller.pinned.isEmpty())
    }

    @Test
    fun newNote_save_withoutPinFlag_doesNotPin() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel()

        viewModel.onTitleChange("Plain")
        viewModel.save { }
        advanceUntilIdle()

        assertFalse(f.repository.notes.first().single().isPinned)
        assertTrue(f.controller.pinned.isEmpty())
    }

    @Test
    fun existingNote_loads_thenUpdatePreservesCreatedAt() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "Old", description = "keep")
        val viewModel = f.viewModel(id)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("Old", viewModel.uiState.value.title)
        assertEquals("keep", viewModel.uiState.value.description)

        viewModel.onTitleChange("New")
        viewModel.save { }
        advanceUntilIdle()

        val updated = f.repository.getById(id)!!
        assertEquals("New", updated.title)
        assertEquals("keep", updated.description)
        assertEquals(1_000L, updated.createdAt)
    }

    @Test
    fun existingNote_exposesCreatedAndUpdatedTimestamps() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "T", description = "")
        val viewModel = f.viewModel(id)
        advanceUntilIdle()

        assertEquals(1_000L, viewModel.uiState.value.createdAt)
        assertEquals(1_000L, viewModel.uiState.value.updatedAt)
    }

    @Test
    fun newNote_hasNoTimestamps() = runTest(dispatcher) {
        val viewModel = Fixture().viewModel()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.createdAt)
        assertNull(viewModel.uiState.value.updatedAt)
    }

    @Test
    fun existingNote_delete_removesIt() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "Temp", description = "")
        val viewModel = f.viewModel(id)
        advanceUntilIdle()

        var completed = false
        viewModel.delete { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        assertNull(f.repository.getById(id))
    }

    @Test
    fun pin_setsFlagAndPostsNotification() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "Pin me", description = "")
        val viewModel = f.viewModel(id)
        advanceUntilIdle()

        viewModel.pin()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isPinned)
        assertTrue(f.repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), f.controller.pinned.map { it.id })
    }

    @Test
    fun savingPinnedNote_refreshesItsNotification() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "Pinned", description = "")
        f.repository.setPinned(id, true)
        val viewModel = f.viewModel(id)
        advanceUntilIdle()

        viewModel.onTitleChange("Edited")
        viewModel.save { }
        advanceUntilIdle()

        assertEquals("Edited", f.controller.refreshed.single().title)
    }
}
