package com.stoneofhope.stoneofhope.data.remote.wire

import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pull semantics transcribed from iOS `FirestoreUserDataPull.swift` / `NoteBodyMerge.swift`. */
class WireDecoderTest {

    private val now = Instant.parse("2026-10-26T12:00:00Z")
    private val t0 = Instant.parse("2026-03-01T10:00:00.123456Z")
    private val t1 = Instant.parse("2026-03-02T10:00:00Z")
    private val noteId = "64a0000000000000000000aa"

    // CRYPTO_SYNC_CONTRACT §6 vector: this is exactly what iOS writes for that plaintext/key/IV.
    private val contractKey = StoneOfHopeCrypto.base64Decode("x6rXPx3Wd+qfUilhSN61MIjS+s5tIHFHI+HoXxq6yX4=")
    private val contractPlaintext = "🙏 2026 祈祷与重构：愿心中有平安。🌟"
    private val iosV6Note = mapOf(
        "ownerUid" to "u", "noteType" to "prayer", "schemaVersion" to 6L, "encVersion" to 1L,
        "iv" to "MTIzNDU2Nzg5MDEy",
        "ciphertext" to "Jqcecad3HWv41Xj0bXlgcQbIfk4ZeB7Tx/jNLAOCJ2Gbj9WayputB6lb2hNrVx4TIbgI//s3MwHQN0IvLw6NoQiXA+59",
        "createdAt" to t0, "contentUpdatedAt" to t1, "updatedAt" to now, "starLevel" to 3L,
        "tagIds" to listOf("64a000000000000000000001"), "isDeleted" to false,
    )

    private fun <T> row(d: Decoded<T>): T {
        assertTrue("expected Row but was $d", d is Decoded.Row)
        return (d as Decoded.Row).value
    }

    @Test
    fun decryptsIosEncryptedNote() {
        val note = row(WireDecoder.note(noteId, iosV6Note, existing = null, noteKey = contractKey, now = now))
        assertEquals(contractPlaintext, note.content)
        assertEquals(NoteType.Prayer, note.noteType)
        assertEquals(t0, note.createdAt)
        assertEquals(t1, note.updatedAt)
        assertEquals(3, note.starLevel)
        assertEquals(listOf("64a000000000000000000001"), note.tagIds)
        assertEquals(false, note.needsSync)
        assertEquals(now, note.lastSyncedAt)
    }

    @Test
    fun lockedSessionOrWrongKeySkipsWholeDocument() {
        assertEquals(
            Decoded.Skip(SkipReason.UnreadableBody),
            WireDecoder.note(noteId, iosV6Note, existing = null, noteKey = null, now = now),
        )
        assertEquals(
            Decoded.Skip(SkipReason.UnreadableBody),
            WireDecoder.note(noteId, iosV6Note, existing = null, noteKey = ByteArray(32), now = now),
        )
    }

    @Test
    fun v5PlaintextContentStillMerges() {
        val doc = mapOf("schemaVersion" to 5L, "content" to "old plaintext", "createdAt" to t0)
        val note = row(WireDecoder.note(noteId, doc, existing = null, noteKey = null, now = now))
        assertEquals("old plaintext", note.content)
        // No contentUpdatedAt → falls back to createdAt.
        assertEquals(t0, note.updatedAt)
    }

    @Test
    fun noBodyKeepsExistingContentAndUnknownTypeKeepsExistingType() {
        val existing = NoteEntity(id = noteId, noteType = NoteType.Image, content = "keep me", createdAt = t0, updatedAt = t0)
        val doc = mapOf("noteType" to "someFutureType", "createdAt" to t0)
        val note = row(WireDecoder.note(noteId, doc, existing, noteKey = contractKey, now = now))
        assertEquals("keep me", note.content)
        assertEquals(NoteType.Image, note.noteType)
    }

    @Test
    fun legacyIsOngoingMeansPinned() {
        val base = mapOf<String, Any?>("content" to "x", "createdAt" to t0)
        assertEquals(true, row(WireDecoder.note(noteId, base + ("isOngoing" to true), null, null, now)).isPinned)
        assertEquals(false, row(WireDecoder.note(noteId, base + ("isPinned" to false) + ("isOngoing" to true), null, null, now)).isPinned)
        assertNull(row(WireDecoder.note(noteId, base, null, null, now)).isPinned)
    }

    @Test
    fun absentOptionalsClearLocalValues() {
        val existing = NoteEntity(
            id = noteId, content = "x", createdAt = t0, updatedAt = t0, bookOrd = 1, verseStart = 2,
            sermonId = "64a0000000000000000000bb", tagIds = listOf("64a000000000000000000001"),
        )
        val note = row(WireDecoder.note(noteId, mapOf("content" to "x"), existing, null, now))
        assertNull(note.bookOrd)
        assertNull(note.verseStart)
        assertNull(note.sermonId)
        assertEquals(emptyList<String>(), note.tagIds)
    }

    @Test
    fun integerTolerance() {
        val doc = mapOf<String, Any?>("content" to "x", "starLevel" to 2.0, "bookOrd" to 43L, "chapterOrd" to 3.5)
        val note = row(WireDecoder.note(noteId, doc, null, null, now))
        assertEquals(2, note.starLevel)
        assertEquals(43, note.bookOrd)
        assertNull(note.chapterOrd)
    }

    @Test
    fun invalidIdsAndSchemaAreSkipped() {
        val doc = mapOf("content" to "x")
        assertEquals(Decoded.Skip(SkipReason.InvalidDocumentId), WireDecoder.note("autoGeneratedFirestoreId", doc, null, null, now))
        assertEquals(
            Decoded.Skip(SkipReason.UnsupportedSchema),
            WireDecoder.note(noteId, doc + ("schemaVersion" to 7L), null, null, now),
        )
        // Malformed ids inside reference arrays are dropped, valid ones kept.
        val withTags = doc + ("tagIds" to listOf("64a000000000000000000001", "nope"))
        assertEquals(listOf("64a000000000000000000001"), row(WireDecoder.note(noteId, withTags, null, null, now)).tagIds)
    }

    @Test
    fun tombstonesNewAndLegacy() {
        assertEquals(Decoded.Tombstone, WireDecoder.note(noteId, mapOf("isDeleted" to true), null, null, now))
        assertEquals(Decoded.Tombstone, WireDecoder.tag(noteId, mapOf("deletedAt" to t0, "name" to "x"), null, now))
        // A tombstone wins even when the encrypted body would be unreadable.
        assertEquals(Decoded.Tombstone, WireDecoder.note(noteId, iosV6Note + ("isDeleted" to true), null, null, now))
    }

    @Test
    fun requiredFieldsAreEnforced() {
        assertEquals(Decoded.Skip(SkipReason.MissingRequiredField), WireDecoder.tag(noteId, emptyMap(), null, now))
        assertEquals(Decoded.Skip(SkipReason.MissingRequiredField), WireDecoder.sermon(noteId, emptyMap(), null, now))
        assertEquals(Decoded.Skip(SkipReason.MissingRequiredField), WireDecoder.youtubeVideo("abc", emptyMap(), null, now))
        assertEquals(
            Decoded.Skip(SkipReason.MissingRequiredField),
            WireDecoder.indexedText(noteId, mapOf("sourceId" to "s", "startOffset" to 0L, "endOffset" to 1L, "markStyle" to "bold", "indexedText" to "x"), null, now),
        )
    }

    @Test
    fun churchImportSermonsNeverMerge() {
        assertEquals(
            Decoded.Skip(SkipReason.NotUserData),
            WireDecoder.sermon(noteId, mapOf("title" to "t", "isUserData" to false), null, now),
        )
        val churchRow = SermonEntity(id = noteId, title = "t", createdAt = t0, isUserData = false)
        assertEquals(Decoded.Skip(SkipReason.NotUserData), WireDecoder.sermon(noteId, mapOf("title" to "t"), churchRow, now))
    }

    @Test
    fun sermonKeepsChaptersWhenFieldMissing() {
        val existing = SermonEntity(id = noteId, title = "t", createdAt = t0, chapters = listOf(1, 2))
        assertEquals(listOf(1, 2), row(WireDecoder.sermon(noteId, mapOf("title" to "t"), existing, now)).chapters)
        assertEquals(listOf(3L.toInt()), row(WireDecoder.sermon(noteId, mapOf("title" to "t", "chapters" to listOf(3L)), existing, now)).chapters)
    }

    @Test
    fun seriesReturnsOrderedSermonIds() {
        val decoded = row(
            WireDecoder.sermonSeries(
                noteId,
                mapOf("title" to "Romans", "sermonIds" to listOf("64a0000000000000000000c3", "bad", "64a0000000000000000000c2")),
                null, now,
            )
        )
        assertEquals(listOf("64a0000000000000000000c3", "64a0000000000000000000c2"), decoded.sermonIds)
        assertEquals("", decoded.series.startedOn)
        assertEquals(false, decoded.series.isFinished)
    }

    @Test
    fun pinLogEmptyDaysMeansDelete() {
        assertEquals(Decoded.Tombstone, WireDecoder.pinLog(noteId, mapOf("pinnedDays" to emptyList<String>()), null, now))
        assertEquals(Decoded.Tombstone, WireDecoder.pinLog(noteId, emptyMap(), null, now))
        val log = row(WireDecoder.pinLog(noteId, mapOf("pinnedDays" to listOf("2026-03-02", "2026-03-01")), null, now))
        assertEquals(listOf("2026-03-01", "2026-03-02"), log.pinnedDays)
    }

    @Test
    fun bookProgressKeepsBookAndChaptersWhenMissing() {
        val existing = BookProgressEntity(id = noteId, bookOrd = 5, readChapters = listOf(1, 0), createdOn = t0, lastUpdatedOn = t0)
        val bp = row(WireDecoder.bookProgress(noteId, emptyMap(), existing, now))
        assertEquals(5, bp.bookOrd)
        assertEquals(listOf(1, 0), bp.readChapters)
    }

    @Test
    fun userVerseIdInvariant() {
        val doc = mapOf("versionAbbr" to "NKJV", "bookOrd" to 60L, "chapterOrd" to 1L, "verseOrd" to 3L, "content" to "Blessed")
        assertEquals("NKJV_60_1_3", row(WireDecoder.userVerse("NKJV_60_1_3", doc, null, now)).id)
        assertEquals(Decoded.Skip(SkipReason.IdInvariantViolated), WireDecoder.userVerse("NKJV_60_1_4", doc, null, now))
    }

    @Test
    fun indexedTextDefaults() {
        val doc = mapOf("sourceId" to "NKJV_60_1_3", "startOffset" to 0L, "endOffset" to 7L, "markStyle" to "highlight", "indexedText" to "Blessed")
        val mark = row(WireDecoder.indexedText(noteId, doc, null, now))
        assertEquals("verse", mark.sourceType)
        assertEquals("yellow", mark.colorKey)
        assertEquals(MarkStyle.Highlight, mark.markStyle)
    }

    @Test
    fun watermarkIsMaxServerUpdatedAt() {
        val docs = listOf(mapOf("updatedAt" to t0), mapOf("updatedAt" to t1), mapOf("noTimestamp" to 1L))
        assertEquals(t1, WireDecoder.watermark(docs))
        assertNull(WireDecoder.watermark(listOf(mapOf("x" to 1L))))
    }
}
