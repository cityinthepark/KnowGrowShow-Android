package com.stoneofhope.stoneofhope.data.church

import androidx.room.withTransaction
import com.stoneofhope.stoneofhope.data.local.AppDatabase
import com.stoneofhope.stoneofhope.data.local.entity.OnlineChurchEntity
import com.stoneofhope.stoneofhope.data.remote.RemoteStore
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireValue
import com.stoneofhope.stoneofhope.data.sync.SyncGuard
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One element of `settings/preferences.churches` (docs/CHURCH_IMPORT_PLAN.md §2.6). */
data class ChurchListEntry(
    val churchKey: String,
    val isPrimary: Boolean,
    val importedAt: Instant,
    val lastImportedVersion: String?,
)

/**
 * The `churches` / `primaryChurchKey` / `churchesUpdatedAt` fields of Web's
 * `users/{uid}/settings/preferences`. Only these fields are ever written (merge), so Web's
 * `preferredVersions` and `updatedAt` are left alone.
 */
object ChurchListWire {
    const val COLLECTION = "settings"
    const val DOCUMENT = "preferences"
    const val CHURCHES = "churches"
    const val PRIMARY = "primaryChurchKey"
    const val UPDATED_AT = "churchesUpdatedAt"

    /** Elements without a key or a readable `importedAt` are skipped; a repeated key keeps the first. */
    fun decode(doc: WireData?): List<ChurchListEntry> {
        val list = doc?.get(CHURCHES) as? List<*> ?: return emptyList()
        return list.mapNotNull { element ->
            val m = element as? Map<*, *> ?: return@mapNotNull null
            val key = (m["churchKey"] as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val importedAt = when (val v = m["importedAt"]) {
                is String -> runCatching { Instant.parse(v) }.getOrNull()
                is Instant -> v // a Timestamp written by another client
                else -> null
            } ?: return@mapNotNull null
            ChurchListEntry(key, m["isPrimary"] == true, importedAt, (m["lastImportedVersion"] as? String)?.takeIf { it.isNotBlank() })
        }.distinctBy { it.churchKey }
    }

    /** The merge patch. ISO-8601 to the millisecond, which JavaScript's `Date` reads exactly. */
    fun encode(entries: List<ChurchListEntry>): WireData = mapOf(
        CHURCHES to entries.map { e ->
            buildMap {
                put("churchKey", e.churchKey)
                put("isPrimary", e.isPrimary)
                put("importedAt", isoMillis(e.importedAt))
                e.lastImportedVersion?.let { put("lastImportedVersion", it) }
            }
        },
        PRIMARY to (entries.firstOrNull { it.isPrimary }?.churchKey ?: WireValue.DeleteField),
        UPDATED_AT to WireValue.ServerTimestamp,
    )

    fun isoMillis(instant: Instant): String = instant.truncatedTo(ChronoUnit.MILLIS).toString()

    /** True when the stored fields already say exactly [entries] (so nothing needs writing). */
    fun matches(doc: WireData?, entries: List<ChurchListEntry>): Boolean {
        val stored = decode(doc)
        val roundTripped = entries.map { it.copy(importedAt = Instant.parse(isoMillis(it.importedAt))) }
        val primaryField = doc?.get(PRIMARY) as? String
        return stored == roundTripped && primaryField == entries.firstOrNull { it.isPrimary }?.churchKey &&
            (doc?.get(CHURCHES) as? List<*>)?.size == entries.size
    }
}

/**
 * Two-way merge of local "My churches" rows with the cloud list. Pure, so it can run inside a
 * Firestore transaction (which may retry it).
 *
 * - A local row the cloud lacks: new here (`needsSync`) → added; otherwise it was removed on
 *   another device → removed here too. A local tombstone still to push removes it from the cloud.
 * - A church in both: earliest `importedAt`, latest `lastImportedVersion`.
 * - Primary: this device's choice when it moved the mark since its last sync, else the cloud's;
 *   if that church is gone, this device's primary, else the earliest import.
 */
object ChurchListMerge {
    data class Result(
        /** The new cloud list, primary first, then by first import. */
        val entries: List<ChurchListEntry>,
        /** Keys followed elsewhere that are no longer followed: delete the local row. */
        val removedLocally: Set<String>,
    )

    fun merge(local: List<OnlineChurchEntity>, remote: List<ChurchListEntry>): Result {
        val localByKey = local.associateBy { it.churchKey }
        val remoteByKey = remote.associateBy { it.churchKey }
        val kept = mutableListOf<ChurchListEntry>()
        val removed = mutableSetOf<String>()
        for (key in (localByKey.keys + remoteByKey.keys)) {
            val l = localByKey[key]
            val r = remoteByKey[key]
            when {
                l != null && l.deletedAt != null && l.needsSync -> removed += key // unfollowed here
                l != null && l.deletedAt == null && r == null ->
                    if (l.needsSync) kept += ChurchListEntry(key, l.isPrimary, l.importedAt, l.lastImportedVersion) else removed += key
                l != null && l.deletedAt == null && r != null -> kept += ChurchListEntry(
                    key, false, minOf(l.importedAt, r.importedAt), maxVersion(l.lastImportedVersion, r.lastImportedVersion),
                )
                r != null -> kept += r.copy(isPrimary = false) // followed on another device only (or a synced tombstone revived)
                else -> removed += key
            }
        }

        val keptKeys = kept.map { it.churchKey }.toSet()
        val liveLocal = local.filter { it.deletedAt == null }
        val localPrimary = liveLocal.firstOrNull { it.isPrimary }?.churchKey
        val movedHere = liveLocal.any { row -> row.lastSyncedAt?.let { row.updatedAt > it } == true }
        val remotePrimary = remote.firstOrNull { it.isPrimary }?.churchKey
        val primary = listOfNotNull(if (movedHere) localPrimary else remotePrimary, localPrimary, remotePrimary)
            .firstOrNull { it in keptKeys }
            ?: kept.minWithOrNull(compareBy({ it.importedAt }, { it.churchKey }))?.churchKey

        val entries = kept.map { it.copy(isPrimary = it.churchKey == primary) }
            .sortedWith(compareBy({ !it.isPrimary }, { it.importedAt }, { it.churchKey }))
        return Result(entries, removed)
    }

    /** `yyyyMMdd` versions sort as text. */
    private fun maxVersion(a: String?, b: String?): String? = listOfNotNull(a, b).maxOrNull()
}

/**
 * Keeps `settings/preferences.churches` and the local `onlineChurches` table in step. Runs inside
 * CloudSync: reading the cloud list on every full / auto sync (Web writes there without stamping a
 * device), and on push only when a local row changed.
 */
@Singleton
class ChurchListSync @Inject constructor(
    private val db: AppDatabase,
    private val remote: RemoteStore,
    private val clock: Clock,
) {
    private val dao get() = db.onlineChurchDao()

    /** Returns true when the cloud list was written. */
    suspend fun sync(ownerUid: String, guard: SyncGuard, readRemote: Boolean): Boolean {
        val snapshot = dao.all()
        if (!readRemote && snapshot.none { it.needsSync }) return false
        guard.check()
        var wrote = false
        val seen = remote.updatePreferences(ownerUid) { doc ->
            val merged = ChurchListMerge.merge(snapshot, ChurchListWire.decode(doc))
            wrote = !ChurchListWire.matches(doc, merged.entries)
            if (wrote) ChurchListWire.encode(merged.entries) else null
        }
        // The transaction's last attempt saw `seen`; apply the same merge here.
        val merged = ChurchListMerge.merge(snapshot, ChurchListWire.decode(seen))
        guard.check()
        val syncedAt = clock.instant()
        db.withTransaction {
            val before = snapshot.associateBy { it.churchKey }
            for (key in merged.removedLocally) {
                // A row changed since the snapshot (e.g. imported again meanwhile) waits for the next sync.
                if (dao.getByKey(key) == before[key]) dao.hardDelete(key)
            }
            for (entry in merged.entries) {
                val old = before[entry.churchKey]
                if (dao.getByKey(entry.churchKey) != old) continue
                dao.upsert(
                    OnlineChurchEntity(
                        churchKey = entry.churchKey,
                        isPrimary = entry.isPrimary,
                        importedAt = entry.importedAt,
                        lastImportedVersion = entry.lastImportedVersion,
                        // A primary mark that came from the cloud is not this device's choice.
                        updatedAt = old?.takeIf { it.isPrimary == entry.isPrimary }?.updatedAt?.let { minOf(it, syncedAt) } ?: syncedAt,
                        needsSync = false,
                        lastSyncedAt = syncedAt,
                    ),
                )
            }
        }
        return wrote
    }
}
