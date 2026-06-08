package dev.davidfdev.stela.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteDaoTest {

    private lateinit var db: StelaDatabase
    private lateinit var dao: NoteDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, StelaDatabase::class.java).build()
        dao = db.noteDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun note(
        title: String,
        updatedAt: Long,
        description: String = "",
        createdAt: Long = updatedAt,
        isPinned: Boolean = false,
    ) = Note(
        title = title,
        description = description,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isPinned = isPinned,
    )

    @Test
    fun upsertThenObserve_returnsInsertedNote() = runTest {
        val id = dao.upsert(note(title = "Milk", updatedAt = 1_000L))

        val all = dao.observeAll().first()

        assertEquals(1, all.size)
        assertEquals("Milk", all[0].title)
        assertEquals(id, all[0].id)
    }

    @Test
    fun observeAll_ordersByUpdatedAtDescending() = runTest {
        dao.upsert(note(title = "Older", updatedAt = 1_000L))
        dao.upsert(note(title = "Newer", updatedAt = 2_000L))

        val titles = dao.observeAll().first().map { it.title }

        assertEquals(listOf("Newer", "Older"), titles)
    }

    @Test
    fun upsertExistingNote_updatesInPlaceAndReorders() = runTest {
        val firstId = dao.upsert(note(title = "A", updatedAt = 1_000L))
        dao.upsert(note(title = "B", updatedAt = 2_000L))

        val touched = dao.getById(firstId)!!.copy(title = "A2", updatedAt = 3_000L)
        dao.upsert(touched)

        val all = dao.observeAll().first()
        assertEquals(2, all.size)
        assertEquals("A2", all[0].title)
        assertEquals(firstId, all[0].id)
    }

    @Test
    fun getById_returnsNull_whenAbsent() = runTest {
        assertNull(dao.getById(999L))
    }

    @Test
    fun setPinned_flipsFlag_withoutChangingUpdatedAt() = runTest {
        val id = dao.upsert(note(title = "Pin", updatedAt = 1_000L))

        dao.setPinned(id, true)

        val stored = dao.getById(id)!!
        assertEquals(true, stored.isPinned)
        assertEquals(1_000L, stored.updatedAt)
    }

    @Test
    fun countPinned_reflectsPinnedRows() = runTest {
        val id = dao.upsert(note(title = "A", updatedAt = 1_000L))
        dao.upsert(note(title = "B", updatedAt = 2_000L))
        assertEquals(0, dao.countPinned())

        dao.setPinned(id, true)
        assertEquals(1, dao.countPinned())
    }

    @Test
    fun delete_removesNote() = runTest {
        val id = dao.upsert(note(title = "Temp", updatedAt = 1_000L))

        dao.delete(dao.getById(id)!!)

        assertNull(dao.getById(id))
        assertTrue(dao.observeAll().first().isEmpty())
    }
}
