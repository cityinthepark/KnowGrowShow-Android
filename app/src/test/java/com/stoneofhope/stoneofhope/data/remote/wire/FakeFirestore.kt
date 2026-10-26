package com.stoneofhope.stoneofhope.data.remote.wire

import java.time.Instant

/**
 * In-memory stand-in for one user's Firestore tree, applying [WireWrite]s with Firestore's
 * semantics: merge keeps unspecified fields, `DeleteField` removes a field, replace overwrites
 * the whole document, and `ServerTimestamp` resolves to [serverNow].
 */
class FakeFirestore(var serverNow: Instant = Instant.parse("2026-10-26T12:00:00.123456Z")) {

    private val docs = mutableMapOf<Pair<String, String>, Map<String, Any?>>()

    fun apply(write: WireWrite) {
        val key = write.collection to write.documentId
        when (write.mode) {
            WriteMode.Delete -> docs.remove(key)
            WriteMode.Replace -> docs[key] = resolve(write.data, base = emptyMap())
            WriteMode.Merge -> docs[key] = resolve(write.data, base = docs[key].orEmpty())
        }
    }

    fun get(collection: String, documentId: String): WireData? = docs[collection to documentId]

    /** `where updatedAt >= since` (documents without `updatedAt` never match a filtered query). */
    fun query(collection: String, since: Instant?): List<Pair<String, WireData>> =
        docs.filterKeys { it.first == collection }
            .filterValues { data -> since == null || (data["updatedAt"] as? Instant)?.let { it >= since } == true }
            .map { (key, data) -> key.second to data }

    fun count(collection: String): Int = docs.keys.count { it.first == collection }

    fun clear() = docs.clear()

    /** Seed a document as another client (iOS / Web) would have left it. */
    fun put(collection: String, documentId: String, data: WireData) {
        docs[collection to documentId] = data
    }

    private fun resolve(data: WireData, base: Map<String, Any?>): Map<String, Any?> {
        val out = base.toMutableMap()
        for ((k, v) in data) {
            when (v) {
                WireValue.DeleteField -> out.remove(k)
                WireValue.ServerTimestamp -> out[k] = serverNow
                // Firestore stores every integer as int64; reads come back as Long.
                is Int -> out[k] = v.toLong()
                is List<*> -> out[k] = v.map { if (it is Int) it.toLong() else it }
                else -> out[k] = v
            }
        }
        return out
    }
}
