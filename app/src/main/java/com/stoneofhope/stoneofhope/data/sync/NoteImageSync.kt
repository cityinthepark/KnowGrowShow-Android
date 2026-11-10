package com.stoneofhope.stoneofhope.data.sync

import android.util.Log
import com.stoneofhope.stoneofhope.data.image.NoteImageStore
import com.stoneofhope.stoneofhope.data.local.entity.NoteEntity
import com.stoneofhope.stoneofhope.data.model.NoteType
import com.stoneofhope.stoneofhope.data.remote.NoteImageCloud
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Port of iOS `NoteImageCloudStorage`: Firestore carries an image note's file names; the pictures
 * themselves travel through Firebase Storage, thumbnail only (`users/{uid}/noteImages/{noteId}/thumb.jpg`).
 * A phone that never took the photo shows the thumbnail in both places, as iOS does.
 */
@Singleton
class NoteImageSync @Inject constructor(
    private val cloud: NoteImageCloud,
    private val store: NoteImageStore,
    private val clock: Clock,
) {
    /** One thumbnail to mirror to Storage for a note being pushed. */
    data class Upload(val noteId: String, val file: File)

    /**
     * The thumbnail to send with [note]'s push, or null when there is nothing to send (not an image
     * note, deleted, no thumbnail, or the local file is gone). Collected while the push queues the row,
     * before its `needsSync` is cleared (IOS_ISSUES S2).
     */
    fun uploadFor(note: NoteEntity): Upload? {
        if (note.noteType != NoteType.Image || note.deletedAt != null || note.isDeleted) return null
        val name = note.thumbnailPath?.takeIf { it.isNotEmpty() } ?: return null
        val file = store.file(name)
        return if (file.isFile) Upload(note.id, file) else null
    }

    /** Uploads each thumbnail; returns the note ids that failed, so they stay dirty and are tried again. One failure does not stop the rest. */
    suspend fun upload(jobs: List<Upload>, ownerUid: String): Set<String> {
        val failed = mutableSetOf<String>()
        for (job in jobs) {
            try {
                cloud.uploadThumbnail(ownerUid, job.noteId, job.file)
                missingUntil.remove(job.noteId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "upload failed note=${job.noteId}", e)
                failed += job.noteId
            }
        }
        return failed
    }

    /** Removes deleted notes' thumbnails from Storage. Failures are logged; the note is gone either way. */
    suspend fun delete(noteIds: List<String>, ownerUid: String) {
        for (id in noteIds) {
            try {
                cloud.deleteThumbnail(ownerUid, id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "thumbnail delete failed note=$id", e)
            }
        }
    }

    /**
     * After the notes are merged: fetches the thumbnail of every image note whose file is missing here.
     * The thumbnail also stands in for the original, which stays on the phone that took the photo.
     * A note Storage has nothing for is not asked again for an hour (IOS_ISSUES S8).
     */
    suspend fun downloadMissing(notes: List<NoteEntity>, ownerUid: String) {
        for (note in notes) {
            val full = note.imagePath?.takeIf { it.isNotEmpty() } ?: continue
            val thumb = note.thumbnailPath?.takeIf { it.isNotEmpty() }
            val needFull = !store.exists(full)
            val needThumb = thumb != null && !store.exists(thumb)
            if (!needFull && !needThumb) continue
            if (isKnownMissing(note.id)) continue
            try {
                val bytes = cloud.downloadThumbnail(ownerUid, note.id)
                if (bytes == null) {
                    missingUntil[note.id] = clock.instant().plus(RETRY_AFTER)
                    continue
                }
                if (needThumb && thumb != null) store.saveBytes(thumb, bytes)
                if (needFull) store.saveBytes(full, bytes)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "thumbnail download failed note=${note.id}", e)
            }
        }
    }

    private fun isKnownMissing(noteId: String): Boolean = missingUntil[noteId]?.isAfter(clock.instant()) == true

    private val missingUntil = ConcurrentHashMap<String, Instant>()

    private companion object {
        const val TAG = "NoteImageSync"
        val RETRY_AFTER: Duration = Duration.ofHours(1)
    }
}
