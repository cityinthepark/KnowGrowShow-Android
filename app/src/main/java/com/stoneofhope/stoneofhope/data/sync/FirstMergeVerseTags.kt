package com.stoneofhope.stoneofhope.data.sync

import com.stoneofhope.stoneofhope.data.local.entity.UserVerseEntity
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireDecoder

/**
 * iOS `FirestoreUserDataPull.unionTagIdsOnFirstMerge`, the one exception to "local dirty wins, no
 * field-level merge" (USER_VERSE_CONTRACT §3.4): two devices tagged the same verse before both signed
 * in to one account. The local row still wins and is pushed next, but first it takes the cloud's
 * `tagIds` too, so the other device's tags aren't overwritten. Only adds, never removes; once a push
 * sets `lastSyncedAt`, the normal rule applies again (else a tag removed elsewhere would come back).
 */
object FirstMergeVerseTags {

    /** The local row with the cloud's missing tag ids appended, or null when nothing changes. */
    fun union(local: UserVerseEntity, remote: WireData): UserVerseEntity? {
        if (!local.needsSync || local.lastSyncedAt != null || WireDecoder.isRemoteTombstone(remote)) return null
        // Android keeps every id in `tagIds`, so a tag not on this device yet needs no separate list.
        val added = WireDecoder.tagIds(remote).filter { it !in local.tagIds }.distinct()
        if (added.isEmpty()) return null
        return local.copy(tagIds = local.tagIds + added)
    }
}
