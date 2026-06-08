package dev.davidfdev.stela.ui.editor

import androidx.lifecycle.SavedStateHandle
import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import dev.davidfdev.stela.pin.FakeServiceController
import dev.davidfdev.stela.pin.NotePinner
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
        val pinner = NotePinner(repository, controller, FakeServiceController())

        fun viewModel(noteId: Long? = null): EditorViewModel {
            val handle = if (noteId == null) SavedStateHandle() else SavedStateHandle(mapOf("noteId" to noteId))
            return EditorViewModel(repository, pinner, handle)
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
