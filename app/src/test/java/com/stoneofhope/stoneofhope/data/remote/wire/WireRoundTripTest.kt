package com.stoneofhope.stoneofhope.data.remote.wire

import com.stoneofhope.stoneofhope.data.crypto.StoneOfHopeCrypto
import com.stoneofhope.stoneofhope.data.local.entity.BookProgressEntity
import com.stoneofhope.stoneofhope.data.local.entity.ChapterLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.FlashCardEntity
import com.stoneofhope.stoneofhope.data.local.entity.IndexedTextEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteCommentEntity
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.local.entity.PinLogEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonEntity
import com.stoneofhope.stoneofhope.data.local.entity.SermonSeriesEntity
import com.stoneofhope.stoneofhope.data.local.entity.TagEntity
import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.local.entity.YouTubeVideoEntity
import com.stoneofhope.stoneofhope.data.model.MarkStyle
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Push a dirty row through the fake server and pull it back as a fresh device would: the row
 * must come back identical (apart from sync bookkeeping). Timestamps carry microseconds to prove
 * nothing is truncated to millis.
 */
class WireRoundTripTest {

    private val uid = "user-123"
    private val zone = ZoneId.of("Asia/Shanghai")
    private val key = StoneOfHopeCrypto.generateNoteKey()
    private val server = FakeFirestore()
    private val pulledAt = Instant.parse("2026-10-27T00:00:00Z")
    private val t0 = Instant.parse("2026-03-01T23:30:00.123456Z")
    private val t1 = Instant.parse("2026-03-02T08:15:00.654321Z")
    private val tags = listOf("64a000000000000000000002", "64a000000000000000000001")

    private fun <T> row(d: Decoded<T>): T = (d as Decoded.Row).value

    private fun pushed(write: WireWrite): WireData {
        server.apply(write)
        return server.get(write.collection, write.documentId)!!
    }

    @Test
    fun noteSurvivesRoundTripIncludingEncryptedBody() {
        val note = NoteEntity(
            id = "64a0000000000000000000aa", noteType = NoteType.BibleVerse, content = "·",
            bookOrd = 60, chapterOrd = 1, verseStart = 3, verseEnd = 5, versionAbbr = "NKJV",
            createdAt = t0, updatedAt = t1, starLevel = 3, isPinned = false, isAnswered = true,
            answeredAt = t1, tagIds = tags, sermonId = "64a0000000000000000000bb", needsSync = true,
        )
        val doc = pushed(WireEncoder.note(note, uid, NoteBodyCodec.encrypt(note.content, key), zone))
        assertFalse("plaintext must never reach the server", doc.containsKey("content"))
        assertEquals(server.serverNow, doc["updatedAt"])

        val pulled = row(WireDecoder.note(note.id, doc, existing = null, noteKey = key, now = pulledAt))
        assertEquals(note.copy(needsSync = false, lastSyncedAt = pulledAt), pulled)
    }

    @Test
    fun pushOverLegacyV5DocumentStripsPlaintextAndOngoing() {
        val id = "64a0000000000000000000ab"
        server.put(
            FirestoreSchema.NOTES, id,
            mapOf("content" to "legacy plaintext", "isOngoing" to true, "prayerOngoingIntervals" to listOf("x"), "schemaVersion" to 5L),
        )
        val note = NoteEntity(id = id, content = "new", createdAt = t0, updatedAt = t1, isPinned = true)
        val doc = pushed(WireEncoder.note(note, uid, NoteBodyCodec.encrypt(note.content, key), zone))
        assertFalse(doc.containsKey("content"))
        assertFalse(doc.containsKey("isOngoing"))
        assertFalse(doc.containsKey("prayerOngoingIntervals"))
        assertEquals(6L, doc["schemaVersion"])
        assertEquals("new", row(WireDecoder.note(id, doc, null, key, pulledAt)).content)
    }

    @Test
    fun tombstoneReplacesDocumentAndPullsAsTombstone() {
        val id = "64a0000000000000000000ac"
        val live = NoteEntity(id = id, content = "x", createdAt = t0, updatedAt = t0)
        pushed(WireEncoder.note(live, uid, NoteBodyCodec.encrypt("x", key), zone))
        val doc = pushed(WireEncoder.note(live.copy(deletedAt = t1, isDeleted = true), uid, null, zone))
        assertEquals(setOf("isDeleted", "updatedAt"), doc.keys)
        assertEquals(Decoded.Tombstone, WireDecoder.note(id, doc, null, key, pulledAt))
    }

    @Test
    fun commentRoundTrip() {
        val c = NoteCommentEntity(
            id = "64a0000000000000000000ad", noteId = "64a0000000000000000000aa", content = "感谢主 🙏",
            createdAt = t0, starLevel = 1, tagIds = tags, chapterKey = "CH:1:1", youtubeID = "dQw4w9WgXcQ",
        )
        val doc = pushed(WireEncoder.noteComment(c, uid, NoteBodyCodec.encrypt(c.content, key)))
        assertEquals(c.copy(lastSyncedAt = pulledAt), row(WireDecoder.noteComment(c.id, doc, null, key, pulledAt)))
    }

    @Test
    fun plainCollectionsRoundTrip() {
        val tag = TagEntity(id = tags[0], name = "盼望", createdAt = t0)
        assertEquals(tag.copy(lastSyncedAt = pulledAt), row(WireDecoder.tag(tag.id, pushed(WireEncoder.tag(tag, uid)), null, pulledAt)))

        val series = SermonSeriesEntity(id = "64a0000000000000000000c1", title = "Romans", bookOrd = 45, startedOn = "20260301", isFinished = true)
        val sermonIds = listOf("64a0000000000000000000c2")
        val decodedSeries = row(WireDecoder.sermonSeries(series.id, pushed(WireEncoder.sermonSeries(series, uid, sermonIds)), null, pulledAt))
        assertEquals(series.copy(lastSyncedAt = pulledAt), decodedSeries.series)
        assertEquals(sermonIds, decodedSeries.sermonIds)

        val sermon = SermonEntity(
            id = "64a0000000000000000000c2", title = "Grace", preacher = "P", youtubeID = "abc", book = "Romans",
            bookOrd = 45, chapterRange = "5-6", chapters = listOf(5, 6), audioUrl = "a", videoUrl = "v", pdfUrl = "p",
            preachedOn = "20260301", createdAt = t0, seriesId = series.id, intro = "i", tagIds = tags,
        )
        assertEquals(sermon.copy(lastSyncedAt = pulledAt), row(WireDecoder.sermon(sermon.id, pushed(WireEncoder.sermon(sermon, uid)), null, pulledAt)))

        val video = YouTubeVideoEntity(
            youtubeID = "dQw4w9WgXcQ", title = "T", channelName = "C", thumbnailUrl = "u", channelId = "cid",
            bookOrd = 1, chapterOrd = 2, sermonId = sermon.id, tagIds = tags, createdAt = t0, updatedAt = pulledAt,
        )
        assertEquals(
            video.copy(lastSyncedAt = pulledAt),
            row(WireDecoder.youtubeVideo(video.youtubeID, pushed(WireEncoder.youtubeVideo(video, uid)), null, pulledAt)),
        )

        val card = FlashCardEntity(
            id = "64a0000000000000000000e1", bookOrd = 19, chapterOrd = 23, verseStart = 1, verseEnd = 6,
            versionAbbr = "CUV", refKey = "VS:19:23:1-6", topic = "Shepherd", createdAt = t0, updatedAt = t1, isArchived = true,
        )
        assertEquals(
            card.copy(lastSyncedAt = pulledAt),
            row(WireDecoder.flashCard(card.id, pushed(WireEncoder.flashCard(card, uid)), null, pulledAt)).card,
        )

        val log = ChapterLogEntity(id = "64a0000000000000000000f1", bookOrd = 1, chapter = 50, readOn = t0)
        assertEquals(log.copy(lastSyncedAt = pulledAt), row(WireDecoder.chapterLog(log.id, pushed(WireEncoder.chapterLog(log, uid)), null, pulledAt)))

        val bp = BookProgressEntity(
            id = "64a0000000000000000000f2", bookOrd = 57, readChapters = listOf(1, 0, 1),
            createdOn = t0, completedOn = t1, lastUpdatedOn = t1,
        )
        assertEquals(bp.copy(lastSyncedAt = pulledAt), row(WireDecoder.bookProgress(bp.id, pushed(WireEncoder.bookProgress(bp, uid)), null, pulledAt)))

        val verse = UserVerseEntity(
            id = "NKJV_60_1_3", versionAbbr = "NKJV", bookOrd = 60, chapterOrd = 1, verseOrd = 3,
            content = "Blessed be the God", tagIds = tags, createdAt = t0, updatedAt = pulledAt,
        )
        assertEquals(verse.copy(lastSyncedAt = pulledAt), row(WireDecoder.userVerse(verse.id, pushed(WireEncoder.userVerse(verse, uid)), null, pulledAt)))

        val mark = IndexedTextEntity(
            id = "64a0000000000000000000d1", sourceId = verse.id, startOffset = 0, endOffset = 7,
            markStyle = MarkStyle.Underline, colorKey = "blue", indexedText = "Blessed", createdAt = t0, updatedAt = pulledAt,
        )
        assertEquals(mark.copy(lastSyncedAt = pulledAt), row(WireDecoder.indexedText(mark.id, pushed(WireEncoder.indexedText(mark, uid)), null, pulledAt)))

        val pin = PinLogEntity(noteId = "64a0000000000000000000aa", pinnedDays = listOf("2026-03-01", "2026-03-02"))
        assertEquals(pin.copy(lastSyncedAt = pulledAt), row(WireDecoder.pinLog(pin.noteId, pushed(WireEncoder.pinLog(pin, uid)), null, pulledAt)))
        server.apply(WireEncoder.pinLogDelete(pin.noteId))
        assertNull(server.get(FirestoreSchema.PIN_LOG, pin.noteId))
    }
}
