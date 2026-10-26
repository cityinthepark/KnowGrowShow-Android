package com.stoneofhope.stoneofhope.data.remote.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.stoneofhope.stoneofhope.data.remote.wire.WireData
import com.stoneofhope.stoneofhope.data.remote.wire.WireValue
import java.time.Instant
import java.util.Date

/** The only place wire maps meet Firestore SDK types. */
object FirestoreWireAdapter {

    /** Wire → Firestore: [Instant] → `Timestamp`, sentinels → `FieldValue`. */
    fun toFirestore(data: WireData): Map<String, Any?> = data.mapValues { (_, v) -> outbound(v) }

    /** Firestore → wire: `Timestamp` (and `Date`) → [Instant]; everything else unchanged. */
    fun fromFirestore(data: Map<String, Any?>): WireData = data.mapValues { (_, v) -> inbound(v) }

    private fun outbound(value: Any?): Any? = when (value) {
        is Instant -> Timestamp(value.epochSecond, value.nano)
        WireValue.ServerTimestamp -> FieldValue.serverTimestamp()
        WireValue.DeleteField -> FieldValue.delete()
        is List<*> -> value.map(::outbound)
        is Map<*, *> -> value.entries.associate { (k, v) -> k to outbound(v) }
        else -> value
    }

    private fun inbound(value: Any?): Any? = when (value) {
        is Timestamp -> Instant.ofEpochSecond(value.seconds, value.nanoseconds.toLong())
        is Date -> value.toInstant()
        is List<*> -> value.map(::inbound)
        is Map<*, *> -> value.entries.associate { (k, v) -> k to inbound(v) }
        else -> value
    }
}
