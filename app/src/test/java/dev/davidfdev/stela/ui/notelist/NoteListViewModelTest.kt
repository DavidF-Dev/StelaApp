package dev.davidfdev.stela.ui.notelist

import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import dev.davidfdev.stela.pin.FakeServiceController
import dev.davidfdev.stela.pin.NotePinner
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
    ) = NoteListViewModel(repository, NotePinner(repository, controller, FakeServiceController()))

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
}
