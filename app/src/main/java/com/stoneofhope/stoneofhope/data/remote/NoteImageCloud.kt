package com.stoneofhope.stoneofhope.data.remote

import java.io.File

/**
 * Where an image note's thumbnail is mirrored (iOS `NoteImageCloudStorage`): Firebase Storage at
 * `users/{uid}/noteImages/{noteId}/thumb.jpg`. Full-size photos stay on the device. Tests use a fake.
 */
interface NoteImageCloud {
    suspend fun uploadThumbnail(ownerUid: String, noteId: String, file: File)

    /** The thumbnail's bytes, or null when Storage has none for this note. */
    suspend fun downloadThumbnail(ownerUid: String, noteId: String): ByteArray?

    /** A thumbnail that is already gone is fine. */
    suspend fun deleteThumbnail(ownerUid: String, noteId: String)

    /**
     * Every thumbnail of this account (iOS `deleteAllImages`). True when everything is gone or there was nothing.
     * The thumbnails of [knownNoteIds] are deleted by name first: the Storage rules may not allow listing
     * `noteImages/`, and then only those can be removed.
     */
    suspend fun deleteAllThumbnails(ownerUid: String, knownNoteIds: Collection<String>): Boolean
}
