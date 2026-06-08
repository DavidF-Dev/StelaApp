package dev.davidfdev.stela.ui.editor

import androidx.lifecycle.SavedStateHandle
import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
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

    @Test
    fun newNote_save_createsNote() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val viewModel = EditorViewModel(repository, SavedStateHandle())

        assertFalse(viewModel.uiState.value.isEditing)
        viewModel.onTitleChange("Milk")
        viewModel.onDescriptionChange("2L")
        var completed = false
        viewModel.save { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        val notes = repository.notes.first()
        assertEquals(1, notes.size)
        assertEquals("Milk", notes[0].title)
        assertEquals("2L", notes[0].description)
    }

    @Test
    fun existingNote_loads_thenUpdatePreservesCreatedAt() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val id = repository.create(title = "Old", description = "keep")
        val viewModel = EditorViewModel(repository, SavedStateHandle(mapOf("noteId" to id)))
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("Old", viewModel.uiState.value.title)
        assertEquals("keep", viewModel.uiState.value.description)

        viewModel.onTitleChange("New")
        viewModel.save { }
        advanceUntilIdle()

        val updated = repository.getById(id)!!
        assertEquals("New", updated.title)
        assertEquals("keep", updated.description)
        assertEquals(1_000L, updated.createdAt)
    }

    @Test
    fun existingNote_delete_removesIt() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val id = repository.create(title = "Temp", description = "")
        val viewModel = EditorViewModel(repository, SavedStateHandle(mapOf("noteId" to id)))
        advanceUntilIdle()

        var completed = false
        viewModel.delete { completed = true }
        advanceUntilIdle()

        assertTrue(completed)
        assertNull(repository.getById(id))
    }
}
