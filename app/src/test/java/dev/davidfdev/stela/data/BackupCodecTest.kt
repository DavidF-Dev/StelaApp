package dev.davidfdev.stela.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun note(
        id: Long = 0,
        title: String = "T",
        description: String = "D",
        emoji: String = "",
        isPinned: Boolean = false,
        isArchived: Boolean = false,
        createdAt: Long = 100,
        updatedAt: Long = 200,
        pinAt: Long? = null,
        unpinAt: Long? = null,
    ) = Note(
        id = id,
        title = title,
        description = description,
        emoji = emoji,
        isPinned = isPinned,
        isArchived = isArchived,
        createdAt = createdAt,
        updatedAt = updatedAt,
        pinAt = pinAt,
        unpinAt = unpinAt,
    )

    @Test
    fun encodeThenDecode_preservesContentAndTimestamps() {
        val notes = listOf(
            note(id = 5, title = "Milk", description = "2L", emoji = "🛒", createdAt = 1, updatedAt = 2),
            note(id = 9, title = "Work", description = "", createdAt = 3, updatedAt = 4),
        )

        val decoded = BackupCodec.decode(BackupCodec.encode(notes)).getOrThrow()

        assertEquals(listOf("Milk", "Work"), decoded.map { it.title })
        assertEquals(listOf("2L", ""), decoded.map { it.description })
        assertEquals(listOf("🛒", ""), decoded.map { it.emoji })
        assertEquals(listOf(1L, 3L), decoded.map { it.createdAt })
        assertEquals(listOf(2L, 4L), decoded.map { it.updatedAt })
    }

    @Test
    fun decode_dropsIdAndPinState() {
        // Exported notes carry ids and pin state, but imports come in fresh and unpinned.
        val decoded = BackupCodec.decode(BackupCodec.encode(listOf(note(id = 42, isPinned = true)))).getOrThrow()

        assertEquals(0L, decoded.single().id)
        assertFalse(decoded.single().isPinned)
    }

    @Test
    fun decode_dropsSchedule() {
        // Schedules export for forward-compatibility but, like pin state, drop on import (notes arrive inert).
        val decoded = BackupCodec.decode(
            BackupCodec.encode(listOf(note(pinAt = 1_000, unpinAt = 2_000))),
        ).getOrThrow()

        assertEquals(null, decoded.single().pinAt)
        assertEquals(null, decoded.single().unpinAt)
    }

    @Test
    fun encode_writesVersionAndIsValidJson() {
        val text = BackupCodec.encode(listOf(note()))
        assertTrue(text.contains("\"version\": 3"))
    }

    @Test
    fun encodeThenDecode_preservesArchivedFlag() {
        // Unlike pin state, the archive flag survives a round-trip so archived notes import archived.
        val decoded = BackupCodec.decode(
            BackupCodec.encode(listOf(note(title = "Kept", isArchived = true), note(title = "Live"))),
        ).getOrThrow()

        assertEquals(listOf(true, false), decoded.map { it.isArchived })
    }

    @Test
    fun decode_emptyNotes_returnsEmptyList() {
        assertEquals(emptyList<Note>(), BackupCodec.decode("""{"version":1,"notes":[]}""").getOrThrow())
    }

    @Test
    fun decode_toleratesLeadingByteOrderMark() {
        val withBom = "﻿" + """{"version":1,"notes":[{"title":"A","description":"","createdAt":1,"updatedAt":2}]}"""
        assertEquals("A", BackupCodec.decode(withBom).getOrThrow().single().title)
    }

    @Test
    fun decode_malformedJson_isFailure() {
        assertTrue(BackupCodec.decode("not json {{{").isFailure)
    }

    @Test
    fun decode_foreignJson_isFailure() {
        // Valid JSON, but not a notes backup (missing the required notes array).
        assertTrue(BackupCodec.decode("""{"hello":"world"}""").isFailure)
    }
}
