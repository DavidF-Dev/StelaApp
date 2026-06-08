package dev.davidfdev.stela.ui.notelist

import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import dev.davidfdev.stela.pin.FakeServiceController
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoteListViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private var now = 0L

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: NoteRepository,
        controller: FakeNotificationController = FakeNotificationController(),
    ) = NoteListViewModel(
        repository,
        NotePinner(repository, controller, FakeServiceController(), FakeSettingsRepository()),
    )

    @Test
    fun uiState_reflectsRepositoryNotesMostRecentFirst() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { now }
        now = 1_000L
        repository.create(title = "Older", description = "")
        now = 2_000L
        repository.create(title = "Newer", description = "")
        val viewModel = viewModel(repository)

        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("Newer", "Older"), viewModel.uiState.value.notes.map { it.title })
    }

    @Test
    fun pin_setsFlagAndPostsNotification() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val id = repository.create(title = "Pin me", description = "")
        val controller = FakeNotificationController()
        val viewModel = viewModel(repository, controller)

        viewModel.pin(repository.getById(id)!!)
        advanceUntilIdle()

        assertTrue(repository.getById(id)!!.isPinned)
        assertEquals(listOf(id), controller.pinned.map { it.id })
    }

    @Test
    fun toggleSelection_entersThenExitsSelectionMode() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val id = repository.create(title = "A", description = "")
        val viewModel = viewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection(id)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.inSelectionMode)
        assertEquals(setOf(id), viewModel.uiState.value.selectedIds)

        viewModel.toggleSelection(id)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.inSelectionMode)
    }

    @Test
    fun uiState_prunesSelectionForDeletedNotes() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val a = repository.create(title = "A", description = "")
        val b = repository.create(title = "B", description = "")
        val viewModel = viewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        advanceUntilIdle()
        assertEquals(setOf(a, b), viewModel.uiState.value.selectedIds)

        repository.delete(repository.getById(a)!!)
        advanceUntilIdle()

        assertEquals(setOf(b), viewModel.uiState.value.selectedIds)
    }

    @Test
    fun batchActionPins_trueWhenAnySelectedUnpinned_falseWhenAllPinned() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val a = repository.create(title = "A", description = "")
        val b = repository.create(title = "B", description = "")
        repository.setPinned(a, true)
        val viewModel = viewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        advanceUntilIdle()
        // b is unpinned, so the toggle would pin.
        assertTrue(viewModel.uiState.value.batchActionPins)

        viewModel.toggleSelection(b)
        advanceUntilIdle()
        // Only the already-pinned a remains selected, so the toggle would unpin.
        assertFalse(viewModel.uiState.value.batchActionPins)
    }

    @Test
    fun batchTogglePin_pinsAll_whenAnySelectedUnpinned_thenClears() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val a = repository.create(title = "A", description = "")
        val b = repository.create(title = "B", description = "")
        val viewModel = viewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        advanceUntilIdle()
        viewModel.batchTogglePin()
        advanceUntilIdle()

        assertTrue(repository.getById(a)!!.isPinned)
        assertTrue(repository.getById(b)!!.isPinned)
        assertFalse(viewModel.uiState.value.inSelectionMode)
    }

    @Test
    fun batchTogglePin_unpinsAll_whenEverySelectedPinned() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val a = repository.create(title = "A", description = "")
        val b = repository.create(title = "B", description = "")
        repository.setPinned(a, true)
        repository.setPinned(b, true)
        val viewModel = viewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        advanceUntilIdle()
        viewModel.batchTogglePin()
        advanceUntilIdle()

        assertFalse(repository.getById(a)!!.isPinned)
        assertFalse(repository.getById(b)!!.isPinned)
    }

    @Test
    fun batchDelete_removesSelected_andClearsSelection() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val a = repository.create(title = "A", description = "")
        val b = repository.create(title = "B", description = "")
        repository.create(title = "C", description = "")
        val viewModel = viewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.toggleSelection(a)
        viewModel.toggleSelection(b)
        advanceUntilIdle()
        viewModel.batchDelete()
        advanceUntilIdle()

        assertEquals(listOf("C"), viewModel.uiState.value.notes.map { it.title })
        assertFalse(viewModel.uiState.value.inSelectionMode)
    }
}
