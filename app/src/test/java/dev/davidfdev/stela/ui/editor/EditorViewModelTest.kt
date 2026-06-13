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
            initialPinned: Boolean = true,
            draft: NoteDraft? = null,
            canPost: Boolean = true,
            initialAdvancedExpanded: Boolean = false,
            onAdvancedExpandedChange: (Boolean) -> Unit = {},
            // Default to "no emoji anywhere": the real detector needs EmojiManager, unavailable in JVM tests.
            detectEmojiRanges: (String) -> List<IntRange> = { emptyList() },
        ): EditorViewModel {
            // Mirror the routes: the new-note route always supplies the pin arg; the edit route never does.
            val map = buildMap<String, Any> {
                if (noteId != null) put("noteId", noteId) else put("pin", initialPinned)
            }
            return EditorViewModel(
                repository,
                pinner,
                SavedStateHandle(map),
                draft,
                canPostNotifications = { canPost },
                initialAdvancedExpanded = initialAdvancedExpanded,
                onAdvancedExpandedChange = onAdvancedExpandedChange,
                detectEmojiRanges = detectEmojiRanges,
            )
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
    fun newNote_defaultsToPinnedIntent() = runTest(dispatcher) {
        val viewModel = Fixture().viewModel()
        assertFalse(viewModel.uiState.value.isEditing)
        assertTrue(viewModel.uiState.value.isPinned)
    }

    @Test
    fun newNote_save_whenPinned_pinsCreatedNote() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel()

        viewModel.onTitleChange("Pinned on create")
        viewModel.save { }
        advanceUntilIdle()

        val note = f.repository.notes.first().single()
        assertTrue(note.isPinned)
        assertEquals(listOf(note.id), f.controller.pinned.map { it.id })
    }

    @Test
    fun newNote_pin_recordsIntentWithoutPostingUntilSave() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(initialPinned = false)

        viewModel.pin()
        advanceUntilIdle()

        // Intent flips immediately, but nothing is posted for a note that does not exist yet.
        assertTrue(viewModel.uiState.value.isPinned)
        assertTrue(f.controller.pinned.isEmpty())

        viewModel.onTitleChange("Now real")
        viewModel.save { }
        advanceUntilIdle()
        assertEquals(listOf(f.repository.notes.first().single().id), f.controller.pinned.map { it.id })
    }

    @Test
    fun advancedExpanded_seedsFromInitialValue() = runTest(dispatcher) {
        val viewModel = Fixture().viewModel(initialAdvancedExpanded = true)
        assertTrue(viewModel.uiState.value.advancedExpanded)
    }

    @Test
    fun setAdvancedExpanded_updatesState_andWritesBack() = runTest(dispatcher) {
        var stored = false
        val viewModel = Fixture().viewModel(onAdvancedExpandedChange = { stored = it })

        viewModel.setAdvancedExpanded(true)

        // The choice is reflected in state and pushed to the process-wide store (here, the captured flag).
        assertTrue(viewModel.uiState.value.advancedExpanded)
        assertTrue(stored)
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
    fun newNote_save_whenPinned_butNotificationsBlocked_savesUnpinned() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(canPost = false)

        viewModel.onTitleChange("No permission")
        viewModel.save { }
        advanceUntilIdle()

        assertFalse(f.repository.notes.first().single().isPinned)
        assertTrue(f.controller.pinned.isEmpty())
    }

    @Test
    fun newNote_save_whenUnpinned_doesNotPin() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(initialPinned = false)

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
    fun pin_clearsPendingPinAt_inUiAndOnSave() = runTest(dispatcher) {
        val f = Fixture()
        val future = System.currentTimeMillis() + 86_400_000L
        val id = f.repository.create(title = "Scheduled", description = "")
        f.repository.setSchedule(id, pinAt = future, unpinAt = null)
        val viewModel = f.viewModel(id)
        advanceUntilIdle()
        assertEquals(future, viewModel.uiState.value.pinAt)

        viewModel.pin()
        advanceUntilIdle()

        // The Advanced row clears immediately, matching the pinner's clear of pinAt.
        assertNull(viewModel.uiState.value.pinAt)
        assertNull(f.repository.getById(id)!!.pinAt)

        // A later save must not re-apply the stale schedule.
        viewModel.save { }
        advanceUntilIdle()
        assertNull(f.repository.getById(id)!!.pinAt)
    }

    @Test
    fun unpin_clearsPendingUnpinAt_inUiAndOnSave() = runTest(dispatcher) {
        val f = Fixture()
        val future = System.currentTimeMillis() + 86_400_000L
        val id = f.repository.create(title = "Temporary", description = "")
        f.repository.setPinned(id, true)
        f.repository.setSchedule(id, pinAt = null, unpinAt = future)
        val viewModel = f.viewModel(id)
        advanceUntilIdle()
        assertEquals(future, viewModel.uiState.value.unpinAt)

        viewModel.unpin()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.unpinAt)
        assertNull(f.repository.getById(id)!!.unpinAt)

        viewModel.save { }
        advanceUntilIdle()
        assertNull(f.repository.getById(id)!!.unpinAt)
    }

    @Test
    fun newNoteDraft_seedsFields_andStaysACreate() = runTest(dispatcher) {
        val f = Fixture()
        val draft = NoteDraft(noteId = null, title = "Jot", description = "from popup", emoji = "📝", pinOnSave = true)
        val viewModel = f.viewModel(draft = draft)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isEditing)
        assertEquals("Jot", viewModel.uiState.value.title)
        assertEquals("from popup", viewModel.uiState.value.description)
        assertEquals("📝", viewModel.uiState.value.emoji)

        viewModel.save { }
        advanceUntilIdle()

        // The draft creates a brand-new note rather than updating an existing one.
        val note = f.repository.notes.first().single()
        assertEquals("Jot", note.title)
        assertTrue(note.isPinned)
    }

    @Test
    fun existingNoteDraft_overlaysUnsavedEditsOnLoadedNote() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "Stored", description = "stored body")
        val draft = NoteDraft(noteId = id, title = "Edited", description = "stored body", emoji = "", pinOnSave = false)
        val viewModel = f.viewModel(noteId = id, draft = draft)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditing)
        // The draft's edited title wins; the stored timestamps still load.
        assertEquals("Edited", viewModel.uiState.value.title)
        assertEquals(1_000L, viewModel.uiState.value.createdAt)

        viewModel.save { }
        advanceUntilIdle()

        val updated = f.repository.getById(id)!!
        assertEquals("Edited", updated.title)
        assertEquals(1, f.repository.notes.first().size)
    }

    @Test
    fun existingPinnedNote_snooze_unpinsNowAndSetsPinAt() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "Pinned", description = "")
        f.repository.setPinned(id, true)
        val viewModel = f.viewModel(id)
        advanceUntilIdle()

        val until = System.currentTimeMillis() + 3_600_000L
        viewModel.snooze(until)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isPinned)
        assertEquals(until, viewModel.uiState.value.pinAt)
        assertFalse(f.repository.getById(id)!!.isPinned)
        assertEquals(until, f.repository.getById(id)!!.pinAt)
    }

    @Test
    fun newNote_save_withFuturePinAt_persistsTheSchedule() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(initialPinned = false)
        val future = System.currentTimeMillis() + 86_400_000L

        viewModel.onTitleChange("Later")
        viewModel.onPinAtChange(future)
        viewModel.save { }
        advanceUntilIdle()

        val note = f.repository.notes.first().single()
        assertEquals(future, note.pinAt)
        assertFalse(note.isPinned)
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

    // 👑 occupies UTF-16 indices 0..1; the fake reports it only when the title leads with it.
    private val crownDetector: (String) -> List<IntRange> = { if (it.startsWith("👑")) listOf(0..1) else emptyList() }

    @Test
    fun newNote_save_promotesLeadingTitleEmoji() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(detectEmojiRanges = crownDetector)

        viewModel.onTitleChange("👑 Foo")
        viewModel.save { }
        advanceUntilIdle()

        val note = f.repository.notes.first().single()
        assertEquals("👑", note.emoji)
        assertEquals("Foo", note.title)
    }

    @Test
    fun newNote_save_withChosenEmoji_doesNotPromote() = runTest(dispatcher) {
        val f = Fixture()
        val viewModel = f.viewModel(detectEmojiRanges = crownDetector)

        viewModel.onTitleChange("👑 Foo")
        viewModel.onEmojiChange("🛒")
        viewModel.save { }
        advanceUntilIdle()

        val note = f.repository.notes.first().single()
        assertEquals("🛒", note.emoji)
        assertEquals("👑 Foo", note.title)
    }

    @Test
    fun existingNote_save_promotesLeadingTitleEmoji() = runTest(dispatcher) {
        val f = Fixture()
        val id = f.repository.create(title = "plain", description = "")
        val viewModel = f.viewModel(id, detectEmojiRanges = crownDetector)
        advanceUntilIdle()

        viewModel.onTitleChange("👑 Foo")
        viewModel.save { }
        advanceUntilIdle()

        val updated = f.repository.getById(id)!!
        assertEquals("👑", updated.emoji)
        assertEquals("Foo", updated.title)
    }
}
