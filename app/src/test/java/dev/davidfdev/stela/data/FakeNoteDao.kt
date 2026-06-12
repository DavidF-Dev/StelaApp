package dev.davidfdev.stela.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/// In-memory [NoteDao] for JVM unit tests. Mirrors Room's autogenerate-on-insert
/// behavior and the most-recently-updated-first ordering of [observeAll].
class FakeNoteDao : NoteDao {

    private val rows = MutableStateFlow<List<Note>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<Note>> =
        rows.map { list -> list.sortedByDescending { it.updatedAt } }

    override suspend fun getById(id: Long): Note? =
        rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(note: Note): Long {
        val current = rows.value.toMutableList()
        return if (note.id == 0L) {
            val inserted = note.copy(id = nextId++)
            current.add(inserted)
            rows.value = current
            inserted.id
        } else {
            val index = current.indexOfFirst { it.id == note.id }
            if (index >= 0) current[index] = note else current.add(note)
            rows.value = current
            note.id
        }
    }

    override suspend fun setPinned(id: Long, isPinned: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(isPinned = isPinned) else it }
    }

    override suspend fun setArchived(id: Long, isArchived: Boolean) {
        rows.value = rows.value.map { if (it.id == id) it.copy(isArchived = isArchived) else it }
    }

    override suspend fun setSchedule(id: Long, pinAt: Long?, unpinAt: Long?) {
        rows.value = rows.value.map { if (it.id == id) it.copy(pinAt = pinAt, unpinAt = unpinAt) else it }
    }

    override suspend fun clearPinAt(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(pinAt = null) else it }
    }

    override suspend fun clearUnpinAt(id: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(unpinAt = null) else it }
    }

    override suspend fun countPinned(): Int = rows.value.count { it.isPinned }

    override suspend fun delete(note: Note) {
        rows.value = rows.value.filterNot { it.id == note.id }
    }
}
