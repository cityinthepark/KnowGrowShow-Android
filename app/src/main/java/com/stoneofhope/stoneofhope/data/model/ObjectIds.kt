package com.stoneofhope.stoneofhope.data.model

import org.bson.types.ObjectId

/**
 * Document ids per FIRESTORE_SYNC_CONTRACT §1: every collection (except `youtubeVideos` and
 * `userVerses`) uses a 24-char lowercase hex MongoDB ObjectId. Never use Firestore auto-ids —
 * iOS pull silently drops documents whose id does not parse as an ObjectId.
 */
object ObjectIds {
    private val pattern = Regex("^[0-9a-f]{24}$")

    fun newId(): String = ObjectId().toHexString()

    fun isValid(id: String?): Boolean = id != null && pattern.matches(id)

    /** Same tolerance as iOS `try? ObjectId(string:)`: returns the canonical lowercase id or null. */
    fun parse(id: String?): String? {
        if (id == null || !ObjectId.isValid(id)) return null
        return ObjectId(id).toHexString()
    }
}
