package com.stoneofhope.stoneofhope.data.remote.wire

import java.time.Instant

/**
 * SDK-free representation of Firestore documents, so the mapping to and from Room entities can be
 * unit tested on the JVM. `FirestoreWireAdapter` converts to and from real Firestore types.
 *
 * Values in a wire map are: `String`, `Int`/`Long`, `Double`, `Boolean`, [Instant] (a Firestore
 * `Timestamp`), `List`, `Map`, or one of the [WireValue] write sentinels.
 */
typealias WireData = Map<String, Any?>

/** Write-only sentinels. */
sealed interface WireValue {
    /** `FieldValue.serverTimestamp()`. */
    data object ServerTimestamp : WireValue

    /** `FieldValue.delete()`; only meaningful in a [WriteMode.Merge] write. */
    data object DeleteField : WireValue
}

enum class WriteMode {
    /** `setData(data, merge: true)` — normal rows. Fields not in `data` are left untouched remotely. */
    Merge,

    /** `setData(data)` — replaces the whole document. Used for tombstones. */
    Replace,

    /** Physical document delete. Used only for empty `PinLog` rows (FIRESTORE_SYNC_CONTRACT §2.5). */
    Delete,
}

/** One pending write to `users/{ownerUid}/{collection}/{documentId}`. */
data class WireWrite(
    val collection: String,
    val documentId: String,
    val data: WireData,
    val mode: WriteMode,
)

/** Paths, collection names and schema versions shared with iOS `FirestoreUserDataPush/Pull`. */
object FirestoreSchema {
    const val USERS = "users"

    /** Default for collections without encrypted bodies. */
    const val SCHEMA_VERSION = 5

    /** `notes` / `noteComments` carry `iv` + `ciphertext` instead of plaintext `content`. */
    const val ENCRYPTED_BODY_SCHEMA_VERSION = 6

    /** Documents with a higher `schemaVersion` are skipped on pull (FIRESTORE_SYNC_CONTRACT §6). */
    const val SUPPORTED_SCHEMA_VERSION = 6

    const val TAGS = "tags"
    const val SERMON_SERIES = "sermonSeries"
    const val SERMONS = "sermons"
    const val YOUTUBE_VIDEOS = "youtubeVideos"
    const val NOTES = "notes"
    const val PIN_LOG = "PinLog"
    const val NOTE_COMMENTS = "noteComments"
    const val FLASH_CARDS = "flashCards"
    const val CHAPTER_LOGS = "chapterLogs"
    const val BOOK_PROGRESS = "bookProgress"
    const val USER_VERSES = "userVerses"
    const val INDEXED_TEXTS = "indexedTexts"

    /** Pull order used by iOS (tags before taggables, series before sermons, notes before comments). */
    val pullOrder = listOf(
        TAGS, SERMON_SERIES, SERMONS, YOUTUBE_VIDEOS, NOTES, PIN_LOG, NOTE_COMMENTS,
        FLASH_CARDS, CHAPTER_LOGS, BOOK_PROGRESS, USER_VERSES, INDEXED_TEXTS,
    )
}

// Readers with the same tolerance as the iOS `as? T` casts on `doc.data()`.

/** `as? Int`: Firestore integers arrive as `Long`; an integral `Double` (Web numbers) also counts. */
fun WireData.int(key: String): Int? = asWireInt(this[key])

fun WireData.string(key: String): String? = this[key] as? String

fun WireData.bool(key: String): Boolean? = this[key] as? Boolean

/** `(value as? Timestamp)?.dateValue()`. */
fun WireData.instant(key: String): Instant? = this[key] as? Instant

/** `as? [String]`: null unless every element is a String (Swift's array cast is all-or-nothing). */
fun WireData.stringList(key: String): List<String>? {
    val list = this[key] as? List<*> ?: return null
    return list.map { it as? String ?: return null }
}

/** `as? [Int]`: null unless every element is an integer. */
fun WireData.intList(key: String): List<Int>? {
    val list = this[key] as? List<*> ?: return null
    return list.map { asWireInt(it) ?: return null }
}

private fun asWireInt(value: Any?): Int? = when (value) {
    is Int -> value
    is Long -> if (value in Int.MIN_VALUE..Int.MAX_VALUE) value.toInt() else null
    is Double -> if (value % 1.0 == 0.0 && value >= Int.MIN_VALUE && value <= Int.MAX_VALUE) value.toInt() else null
    else -> null
}
