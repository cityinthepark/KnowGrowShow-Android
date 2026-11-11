package com.stoneofhope.stoneofhope.data.sync

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.crypto.CryptoSession
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.SyncConflictEntity
import com.stoneofhope.stoneofhope.data.model.ConflictResolution
import com.stoneofhope.stoneofhope.data.model.ObjectIds
import com.stoneofhope.stoneofhope.data.model.SyncConflictType
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.NoteBodyCodec
import com.stoneofhope.stoneofhope.data.remote.wire.RemoteBody
import com.stoneofhope.stoneofhope.data.remote.wire.Decoded
import com.stoneofhope.stoneofhope.data.remote.wire.FirestoreSchema
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireDecoder
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Port of iOS `SyncConflictResolver`: executes the user's choice for one [SyncConflictEntity].
 *
 * Pull skips the dirty local row and the push that follows uploads it over the other device's version
 * (iOS does the same). So by the time the user chooses, the cloud may no longer hold the other version;
 * for notes and comments it lives on in [SyncConflictEntity.remotePreview] (the full decrypted text, saved
 * when the conflict was detected). iOS re-fetches the cloud document instead, which then returns this
 * device's own text — "Use the other device's version" silently does nothing there (IOS_ISSUES list).
 *
 * - **Keep this device's version**: nothing to do; the row is already dirty and wins.
 * - **Use the other device's version**: the local row takes that text and is marked dirty, so the cloud
 *   and the other device end up with it too. No network needed.
 * - **Keep both (create a copy)**: the local row stays; a new dirty row (fresh id, no picture) carries the other text.
 * - Highlights/underlines have no text to keep: the cloud document is re-fetched and applied (iOS behaviour).
 */
@Singleton
class SyncConflictResolver @Inject constructor(
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val session: CryptoSession,
    private val identity: SyncIdentity,
    private val clock: Clock,
) {
    sealed class ResolveException(message: String) : Exception(message) {
        data object NotSignedIn : ResolveException("not signed in")
        data object RemoteDocumentMissing : ResolveException("remote document missing")
        data object Unreadable : ResolveException("remote document unreadable")
    }

    @Throws(ResolveException::class)
    suspend fun resolve(conflictId: String, resolution: ConflictResolution) {
        val conflict = db.syncConflictDao().getById(conflictId) ?: return
        if (conflict.isResolved) return
        when (resolution) {
            ConflictResolution.KeepLocal -> Unit
            ConflictResolution.KeepRemote -> applyRemote(conflict, asCopy = false)
            ConflictResolution.CreateCopy -> applyRemote(conflict, asCopy = true)
        }
        db.syncConflictDao().upsert(conflict.copy(resolution = resolution, resolvedAt = clock.instant()))
    }

    private suspend fun applyRemote(conflict: SyncConflictEntity, asCopy: Boolean) {
        when (conflict.recordType) {
            SyncConflictType.Note -> {
                val text = remoteText(conflict)
                val dao = db.noteDao()
                val existing = dao.getById(conflict.recordId)
                val now = clock.instant()
                db.withTransaction {
                    if (asCopy) {
                        val base = existing ?: return@withTransaction
                        dao.upsert(
                            base.copy(
                                id = ObjectIds.newId(), content = text, needsSync = true, lastSyncedAt = null,
                                updatedAt = now, imagePath = null, thumbnailPath = null, thumbnailUrl = null,
                                // The copy is a plain note: pinned/answered state and the thread stay with the original.
                                isPinned = null, isAnswered = null, answeredAt = null,
                            )
                        )
                    } else if (existing != null) {
                        dao.upsert(existing.copy(content = text, needsSync = true, updatedAt = now))
                    }
                }
            }
            SyncConflictType.NoteComment -> {
                val text = remoteText(conflict)
                val dao = db.noteCommentDao()
                val existing = dao.getById(conflict.recordId)
                db.withTransaction {
                    if (asCopy) {
                        val base = existing ?: return@withTransaction
                        dao.upsert(base.copy(id = ObjectIds.newId(), content = text, needsSync = true, lastSyncedAt = null))
                    } else if (existing != null) {
                        dao.upsert(existing.copy(content = text, needsSync = true))
                    }
                }
            }
            SyncConflictType.IndexedText -> applyRemoteMark(conflict, asCopy)
        }
    }

    /**
     * The other device's text: the copy saved at detection time. When the key was missing then (so it is only
     * a placeholder) the push was held back as well, so the cloud still has that version: read it now.
     */
    private suspend fun remoteText(conflict: SyncConflictEntity): String {
        if (conflict.remotePreview != SyncConflictDetector.UNREADABLE_PREVIEW) return conflict.remotePreview
        val uid = identity.currentAuthUid ?: throw ResolveException.NotSignedIn
        val collection = if (conflict.recordType == SyncConflictType.Note) FirestoreSchema.NOTES else FirestoreSchema.NOTE_COMMENTS
        val doc = remote.fetchDocument(uid, collection, conflict.recordId) ?: throw ResolveException.RemoteDocumentMissing
        return (NoteBodyCodec.decode(doc.data, session.keyFor(uid)) as? RemoteBody.Readable)?.text ?: throw ResolveException.Unreadable
    }

    private suspend fun applyRemoteMark(conflict: SyncConflictEntity, asCopy: Boolean) {
        val uid = identity.currentAuthUid ?: throw ResolveException.NotSignedIn
        val doc = remote.fetchDocument(uid, FirestoreSchema.INDEXED_TEXTS, conflict.recordId)
            ?: throw ResolveException.RemoteDocumentMissing
        if (WireDecoder.isRemoteTombstone(doc.data)) throw ResolveException.RemoteDocumentMissing
        val now = clock.instant()
        val dao = db.indexedTextDao()
        val targetId = if (asCopy) ObjectIds.newId() else conflict.recordId
        val mark = row(WireDecoder.indexedText(targetId, doc.data, if (asCopy) null else dao.getById(conflict.recordId), now))
        dao.upsert(if (asCopy) mark.copy(needsSync = true, lastSyncedAt = null) else mark)
    }

    private fun <T> row(decoded: Decoded<T>): T = when (decoded) {
        is Decoded.Row -> decoded.value
        Decoded.Tombstone -> throw ResolveException.RemoteDocumentMissing
        is Decoded.Skip -> throw ResolveException.Unreadable
    }
}
