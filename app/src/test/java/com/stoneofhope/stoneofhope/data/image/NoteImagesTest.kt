package com.stoneofhope.stoneofhope.data.image

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stoneofhope.stoneofhope.data.model.NoteType
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteImagesTest {

    @Test fun aBigPhotoShrinksToTheLongerSideKeepingItsProportions() {
        assertEquals(480 to 360, ImageSizing.fit(4000, 3000, 480))
        assertEquals(360 to 480, ImageSizing.fit(3000, 4000, 480))
        assertEquals(480 to 1, ImageSizing.fit(4800, 5, 480))
    }

    @Test fun aSmallPhotoIsNeverEnlarged() {
        assertEquals(300 to 200, ImageSizing.fit(300, 200, 480))
        assertEquals(480 to 480, ImageSizing.fit(480, 480, 480))
        assertEquals(0 to 0, ImageSizing.fit(0, 0, 480))
    }

    @Test fun anImageNoteHasNoTextAndKeepsBothFileNames() {
        val saved = SavedImage("A.jpg", "B.jpg")
        val now = Instant.parse("2026-11-09T10:00:00Z")
        val note = ImageNotes.note("n1", saved, now, sermonId = null, youtubeID = null)
        assertEquals(NoteType.Image, note.noteType)
        assertEquals("", note.content)
        assertEquals("A.jpg", note.imagePath)
        assertEquals("B.jpg", note.thumbnailPath)
        assertTrue(note.needsSync)
        assertEquals(now, note.createdAt)
        assertEquals(now, note.updatedAt)
    }

    @Test fun aPhotoIsLinkedToItsSermonOrItsVideoNeverBoth() {
        val saved = SavedImage("A.jpg", "B.jpg")
        val now = Instant.parse("2026-11-09T10:00:00Z")
        val inSermon = ImageNotes.note("n1", saved, now, sermonId = "s1", youtubeID = null)
        assertEquals("s1", inSermon.sermonId)
        assertNull(inSermon.youtubeID)
        val inVideo = ImageNotes.note("n2", saved, now, sermonId = null, youtubeID = "abc")
        assertEquals("abc", inVideo.youtubeID)
        assertNull(inVideo.sermonId)
        // Given both, the sermon wins and the video id is cleared (iOS `linkNote`).
        val both = ImageNotes.note("n3", saved, now, sermonId = "s1", youtubeID = "abc")
        assertEquals("s1", both.sermonId)
        assertNull(both.youtubeID)
    }

    @Test fun aStoredNameFindsItsFileByTheLastPathComponentOnly() {
        val store = NoteImageStore(ApplicationProvider.getApplicationContext<Context>())
        val name = "ROUNDTRIP-TEST.jpg"
        val file = store.file(name)
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            assertTrue(store.exists(name))
            // iOS may have stored an absolute path; only the file name counts.
            assertEquals(file, store.file("/var/mobile/Containers/Data/images/$name"))
            assertEquals("images", file.parentFile?.name)
            store.delete(name)
            assertFalse(store.exists(name))
        } finally {
            File(file.path).delete()
        }
    }

}
