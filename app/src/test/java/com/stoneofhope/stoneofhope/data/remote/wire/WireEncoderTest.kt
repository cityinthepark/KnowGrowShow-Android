package com.stoneofhope.stoneofhope.data.remote.wire

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
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Field-name contract with iOS: every expected key set below is transcribed from
 * `FirestoreUserDataPush.swift`. If one of these fails, Android drifted from iOS (or iOS changed
 * and this file + the encoder must follow).
 */
class WireEncoderTest {

    private val uid = "user-123"
    private val t0 = Instant.parse("2026-03-01T23:30:00.250Z")
    private val t1 = Instant.parse("2026-03-02T08:15:00.500Z")
    private val body = EncryptedBody(iv = "aXY=", ciphertext = "Y3Q=")
    private val tagA = "64a000000000000000000001"
    private val tagB = "64a000000000000000000002"

    @Test
    fun tagFields() {
        val w = WireEncoder.tag(TagEntity(id = tagA, name = "Hope", createdAt = t0), uid)
        assertEquals(FirestoreSchema.TAGS, w.collection)
        assertEquals(WriteMode.Merge, w.mode)
        assertEquals(
            mapOf(
                "ownerUid" to uid, "name" to "Hope", "createdAt" to t0,
                "schemaVersion" to 5, "updatedAt" to WireValue.ServerTimestamp,
            ),
            w.data,
        )
    }

    @Test
    fun noteFullPayloadMatchesIosPush() {
        val note = NoteEntity(
            id = "64a0000000000000000000aa", noteType = NoteType.Prayer, content = "secret",
            bookOrd = 43, chapterOrd = 3, verseStart = 16, verseEnd = 17, versionAbbr = "NKJV",
            refKey = "VS:43:3:16-17", chapterKey = "CH:43:3", createdAt = t0, updatedAt = t1,
            starLevel = 2, imagePath = "img.jpg", thumbnailPath = "thumb.jpg", thumbnailUrl = "https://x",
            isPinned = true, isAnswered = true, answeredAt = t1, tagIds = listOf(tagA, tagB),
            sermonId = "64a0000000000000000000bb", youtubeID = "dQw4w9WgXcQ",
        )
        val w = WireEncoder.note(note, uid, body, ZoneId.of("America/Los_Angeles"))
        assertEquals(WriteMode.Merge, w.mode)
        assertEquals(
            setOf(
                "ownerUid", "noteType", "iv", "ciphertext", "encVersion", "content", "createdAt",
                "calendarDay", "updatedAt", "contentUpdatedAt", "starLevel", "tagIds", "schemaVersion",
                "bookOrd", "chapterOrd", "verseStart", "verseEnd", "versionAbbr", "refKey", "chapterKey",
                "imagePath", "thumbnailPath", "thumbnailUrl", "youtubeID", "isPinned", "isOngoing",
                "isAnswered", "answeredAt", "sermonId", "isDeleted", "prayerOngoingIntervals",
            ),
            w.data.keys,
        )
        assertEquals("prayer", w.data["noteType"])
        assertEquals(6, w.data["schemaVersion"])
        assertEquals(1, w.data["encVersion"])
        assertEquals(WireValue.DeleteField, w.data["content"])
        assertEquals(WireValue.DeleteField, w.data["isOngoing"])
        assertEquals(WireValue.DeleteField, w.data["prayerOngoingIntervals"])
        assertEquals(t1, w.data["contentUpdatedAt"])
        assertEquals(false, w.data["isDeleted"])
        assertEquals(listOf(tagA, tagB), w.data["tagIds"])
    }

    @Test
    fun noteOmitsNullOptionals() {
        val note = NoteEntity(id = "64a0000000000000000000aa", createdAt = t0, updatedAt = t0)
        val w = WireEncoder.note(note, uid, body, ZoneId.of("UTC"))
        assertEquals(
            setOf(
                "ownerUid", "noteType", "iv", "ciphertext", "encVersion", "content", "createdAt",
                "calendarDay", "updatedAt", "contentUpdatedAt", "starLevel", "tagIds", "schemaVersion",
                "isOngoing", "isDeleted", "prayerOngoingIntervals",
            ),
            w.data.keys,
        )
        assertEquals("note", w.data["noteType"])
        assertEquals(emptyList<String>(), w.data["tagIds"])
    }

    @Test
    fun calendarDayIsLocalStartOfCreatedAt() {
        // 2026-03-01T23:30Z is still 2026-03-01 in Los Angeles but already 2026-03-02 in Shanghai.
        val note = NoteEntity(id = "64a0000000000000000000aa", createdAt = t0, updatedAt = t0)
        val la = WireEncoder.note(note, uid, body, ZoneId.of("America/Los_Angeles"))
        val sh = WireEncoder.note(note, uid, body, ZoneId.of("Asia/Shanghai"))
        assertEquals(Instant.parse("2026-03-01T08:00:00Z"), la.data["calendarDay"])
        assertEquals(Instant.parse("2026-03-01T16:00:00Z"), sh.data["calendarDay"])
    }

    @Test
    fun liveNoteWithoutBodyIsRejected() {
        val note = NoteEntity(id = "64a0000000000000000000aa", createdAt = t0, updatedAt = t0)
        assertThrows(IllegalArgumentException::class.java) {
            WireEncoder.note(note, uid, body = null, zone = ZoneId.of("UTC"))
        }
    }

    @Test
    fun deletedRowsBecomeSlimTombstones() {
        val expected = mapOf("isDeleted" to true, "updatedAt" to WireValue.ServerTimestamp)
        val writes = listOf(
            WireEncoder.note(
                NoteEntity(id = "64a0000000000000000000aa", createdAt = t0, updatedAt = t0, isDeleted = true),
                uid, body = null, zone = ZoneId.of("UTC"),
            ),
            WireEncoder.note(
                NoteEntity(id = "64a0000000000000000000ab", createdAt = t0, updatedAt = t0, deletedAt = t1),
                uid, body = null, zone = ZoneId.of("UTC"),
            ),
            WireEncoder.noteComment(
                NoteCommentEntity(id = "64a0000000000000000000ac", createdAt = t0, deletedAt = t1), uid, null,
            ),
            WireEncoder.tag(TagEntity(id = tagA, name = "x", createdAt = t0, deletedAt = t1), uid),
            WireEncoder.userVerse(
                UserVerseEntity(
                    id = "NKJV_1_1_1", versionAbbr = "NKJV", bookOrd = 1, chapterOrd = 1, verseOrd = 1,
                    createdAt = t0, updatedAt = t0, isDeleted = true,
                ),
                uid,
            ),
            WireEncoder.youtubeVideo(
                YouTubeVideoEntity(youtubeID = "abc", title = "t", createdAt = t0, updatedAt = t0, deletedAt = t1), uid,
            ),
        )
        for (w in writes) {
            assertEquals(WriteMode.Replace, w.mode)
            assertEquals(expected, w.data)
        }
        assertEquals("abc", writes.last().documentId)
    }

    @Test
    fun noteCommentFields() {
        val c = NoteCommentEntity(
            id = "64a0000000000000000000ac", noteId = "64a0000000000000000000aa", createdAt = t0,
            starLevel = 1, tagIds = listOf(tagA), bookOrd = 1, chapterOrd = 2,
        )
        val w = WireEncoder.noteComment(c, uid, body)
        assertEquals(
            setOf(
                "ownerUid", "iv", "ciphertext", "encVersion", "content", "createdAt", "starLevel",
                "tagIds", "schemaVersion", "updatedAt", "noteId", "bookOrd", "chapterOrd", "isDeleted",
            ),
            w.data.keys,
        )
        assertEquals(6, w.data["schemaVersion"])
    }

    @Test
    fun sermonSeriesAndSermonFields() {
        val series = SermonSeriesEntity(id = "64a0000000000000000000c1", title = "Romans", startedOn = "")
        val sw = WireEncoder.sermonSeries(series, uid, sermonIds = listOf("64a0000000000000000000c2"))
        assertEquals(
            setOf("ownerUid", "title", "isFinished", "sermonIds", "schemaVersion", "updatedAt"),
            sw.data.keys,
        )
        val sermon = SermonEntity(
            id = "64a0000000000000000000c2", title = "Grace", createdAt = t0, chapters = listOf(1, 2),
            preachedOn = "20260301", seriesId = series.id, bookOrd = 45,
        )
        val w = WireEncoder.sermon(sermon, uid)
        assertEquals(
            setOf(
                "ownerUid", "isUserData", "title", "chapters", "createdAt", "tagIds", "schemaVersion",
                "updatedAt", "bookOrd", "preachedOn", "seriesId",
            ),
            w.data.keys,
        )
        assertEquals(true, w.data["isUserData"])
    }

    @Test
    fun remainingCollectionsFields() {
        assertEquals(
            setOf(
                "ownerUid", "youtubeID", "title", "createdAt", "tagIds", "schemaVersion", "updatedAt",
                "channelName", "sermonId",
            ),
            WireEncoder.youtubeVideo(
                YouTubeVideoEntity(
                    youtubeID = "abc", title = "t", channelName = "ch", sermonId = "64a0000000000000000000c2",
                    createdAt = t0, updatedAt = t0,
                ),
                uid,
            ).data.keys,
        )
        assertEquals(
            setOf(
                "ownerUid", "versionAbbr", "bookOrd", "chapterOrd", "verseOrd", "content", "createdAt",
                "tagIds", "schemaVersion", "updatedAt", "isDeleted",
            ),
            WireEncoder.userVerse(
                UserVerseEntity(
                    id = "NKJV_60_1_3", versionAbbr = "NKJV", bookOrd = 60, chapterOrd = 1, verseOrd = 3,
                    content = "Blessed be", createdAt = t0, updatedAt = t0,
                ),
                uid,
            ).data.keys,
        )
        val mark = WireEncoder.indexedText(
            IndexedTextEntity(
                id = "64a0000000000000000000d1", sourceId = "NKJV_60_1_3", startOffset = 0, endOffset = 7,
                markStyle = MarkStyle.Underline, indexedText = "Blessed", createdAt = t0, updatedAt = t0,
            ),
            uid,
        )
        assertEquals(
            setOf(
                "ownerUid", "sourceType", "sourceId", "startOffset", "endOffset", "markStyle", "colorKey",
                "indexedText", "createdAt", "schemaVersion", "updatedAt", "isDeleted",
            ),
            mark.data.keys,
        )
        assertEquals("underline", mark.data["markStyle"])
        val card = WireEncoder.flashCard(
            FlashCardEntity(id = "64a0000000000000000000e1", topic = "Faith", createdAt = t0, updatedAt = t1), uid,
        )
        assertEquals(
            setOf(
                "ownerUid", "createdAt", "updatedAt", "contentUpdatedAt", "isArchived", "schemaVersion",
                "topic", "content",
            ),
            card.data.keys,
        )
        assertEquals(WireValue.DeleteField, card.data["content"])
        assertEquals(
            setOf("ownerUid", "bookOrd", "chapter", "readOn", "schemaVersion", "updatedAt"),
            WireEncoder.chapterLog(
                ChapterLogEntity(id = "64a0000000000000000000f1", bookOrd = 1, chapter = 1, readOn = t0), uid,
            ).data.keys,
        )
        assertEquals(
            setOf(
                "ownerUid", "bookOrd", "readChapters", "createdOn", "lastUpdatedOn", "schemaVersion",
                "updatedAt",
            ),
            WireEncoder.bookProgress(
                BookProgressEntity(
                    id = "64a0000000000000000000f2", bookOrd = 1, readChapters = listOf(1, 0),
                    createdOn = t0, lastUpdatedOn = t1,
                ),
                uid,
            ).data.keys,
        )
    }

    @Test
    fun pinLogWritesAndDeletes() {
        val w = WireEncoder.pinLog(PinLogEntity(noteId = "64a0000000000000000000aa", pinnedDays = listOf("2026-03-01")), uid)
        assertEquals(FirestoreSchema.PIN_LOG, w.collection)
        assertEquals("64a0000000000000000000aa", w.documentId)
        assertEquals(setOf("ownerUid", "noteId", "pinnedDays", "schemaVersion", "updatedAt"), w.data.keys)
        val d = WireEncoder.pinLogDelete("64a0000000000000000000aa")
        assertEquals(WriteMode.Delete, d.mode)
        assertFalse(d.data.isNotEmpty())
    }
}
