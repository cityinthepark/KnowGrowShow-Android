package com.stoneofhope.stoneofhope.data.remote.firestore

import android.net.Uri
import android.util.Log
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.stoneofhope.stoneofhope.data.remote.NoteImageCloud
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FirebaseNoteImageCloud @Inject constructor(private val bootstrap: FirebaseBootstrap) : NoteImageCloud {

    private fun thumbnail(ownerUid: String, noteId: String) =
        bootstrap.storage.reference.child(path(ownerUid, noteId))

    override suspend fun uploadThumbnail(ownerUid: String, noteId: String, file: File) {
        val metadata = StorageMetadata.Builder().setContentType("image/jpeg").build()
        thumbnail(ownerUid, noteId).putFile(Uri.fromFile(file), metadata).await()
    }

    override suspend fun downloadThumbnail(ownerUid: String, noteId: String): ByteArray? =
        try {
            thumbnail(ownerUid, noteId).getBytes(MAX_DOWNLOAD_BYTES).await()
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) null else throw e
        }

    override suspend fun deleteThumbnail(ownerUid: String, noteId: String) {
        try {
            thumbnail(ownerUid, noteId).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
    }

    override suspend fun deleteAllThumbnails(ownerUid: String, knownNoteIds: Collection<String>): Boolean {
        var allGone = true
        for (id in knownNoteIds) allGone = delete(thumbnail(ownerUid, id)) && allGone
        if (!allGone) return false
        return try {
            val folders = bootstrap.storage.reference.child("users/$ownerUid/noteImages").listAll().await()
            for (item in folders.items) allGone = delete(item) && allGone
            for (folder in folders.prefixes) {
                for (item in folder.listAll().await().items) allGone = delete(item) && allGone
            }
            allGone
        } catch (e: StorageException) {
            when (e.errorCode) {
                // Nothing uploaded yet: no such folder, or the project has no bucket.
                StorageException.ERROR_OBJECT_NOT_FOUND, StorageException.ERROR_BUCKET_NOT_FOUND -> true
                // The rules do not allow listing noteImages/, so only the thumbnails we know of could be removed.
                StorageException.ERROR_NOT_AUTHORIZED -> {
                    Log.w("FirebaseNoteImageCloud", "Storage rules do not allow listing noteImages/; deleted the known thumbnails only")
                    true
                }
                else -> false
            }
        }
    }

    private suspend fun delete(ref: com.google.firebase.storage.StorageReference): Boolean = try {
        ref.delete().await()
        true
    } catch (e: StorageException) {
        e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND
    }

    companion object {
        private const val MAX_DOWNLOAD_BYTES = 40L * 1024 * 1024

        fun path(ownerUid: String, noteId: String) = "users/$ownerUid/noteImages/$noteId/thumb.jpg"
    }
}
