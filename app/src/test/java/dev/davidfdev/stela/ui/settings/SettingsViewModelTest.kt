package dev.davidfdev.stela.ui.settings

import dev.davidfdev.stela.data.FakeBackupIo
import dev.davidfdev.stela.data.FakeNoteDao
import dev.davidfdev.stela.data.NoteRepository
import dev.davidfdev.stela.notifications.FakeNotificationController
import dev.davidfdev.stela.pin.FakeServiceController
import dev.davidfdev.stela.pin.NotePinner
import dev.davidfdev.stela.settings.FakeSettingsRepository
import dev.davidfdev.stela.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
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
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        repository: NoteRepository = NoteRepository(FakeNoteDao()),
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        controller: FakeNotificationController = FakeNotificationController(),
    ): SettingsViewModel {
        val pinner = NotePinner(repository, controller, FakeServiceController(), settings)
        return SettingsViewModel(settings, repository, pinner, FakeBackupIo())
    }

    @Test
    fun setThemeMode_updatesState() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()

        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }

    @Test
    fun setHideOnLockScreen_updatesState() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setHideOnLockScreen(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hideOnLockScreen)
    }

    @Test
    fun setDynamicColor_updatesState() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setDynamicColor(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.dynamicColor)
    }

    @Test
    fun setQuickAddEnabled_updatesState() = runTest(dispatcher) {
        val viewModel = viewModel()
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setQuickAddEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.quickAddEnabled)
    }

    @Test
    fun clearAllNotes_deletesEveryNote_includingArchived_andEmitsClearedCount() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        repository.create(title = "A", description = "")
        val archived = repository.create(title = "B", description = "")
        repository.setArchived(archived, true)
        val viewModel = viewModel(repository)

        viewModel.clearAllNotes()
        advanceUntilIdle()

        assertEquals(BackupEvent.Cleared(2), viewModel.events.first())
        assertTrue(repository.notes.first().isEmpty())
    }

    @Test
    fun undoClear_restoresEveryNote_withPinAndArchiveState() = runTest(dispatcher) {
        val repository = NoteRepository(FakeNoteDao()) { 1_000L }
        val pinned = repository.create(title = "A", description = "")
        val archived = repository.create(title = "B", description = "")
        repository.setPinned(pinned, true)
        repository.setArchived(archived, true)
        val controller = FakeNotificationController()
        val viewModel = viewModel(repository, controller = controller)
        backgroundScope.launch { viewModel.events.collect {} }
        advanceUntilIdle()

        viewModel.clearAllNotes()
        advanceUntilIdle()
        assertTrue(repository.notes.first().isEmpty())
        controller.pinned.clear()

        viewModel.undoClear()
        advanceUntilIdle()

        val restored = repository.notes.first().associateBy { it.title }
        assertEquals(setOf("A", "B"), restored.keys)
        assertTrue(restored.getValue("A").isPinned)
        assertTrue(restored.getValue("B").isArchived)
        // The pinned note's notification is re-posted on restore; the archived one's is not.
        assertEquals(listOf(pinned), controller.pinned.map { it.id })
    }
}
