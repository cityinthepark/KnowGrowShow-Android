package com.stoneofhope.stoneofhope.data.remote.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.stoneofhope.stoneofhope.data.remote.wire.WireValue
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FirestoreWireAdapterTest {

    private val instant = Instant.parse("2026-03-01T23:30:00.123456Z")

    @Test
    fun outboundConvertsInstantsAndSentinels() {
        val out = FirestoreWireAdapter.toFirestore(
            mapOf(
                "createdAt" to instant,
                "updatedAt" to WireValue.ServerTimestamp,
                "content" to WireValue.DeleteField,
                "tagIds" to listOf("a"),
                "n" to 3,
            )
        )
        assertEquals(Timestamp(instant.epochSecond, instant.nano), out["createdAt"])
        assertEquals(FieldValue.serverTimestamp(), out["updatedAt"])
        assertEquals(FieldValue.delete(), out["content"])
        assertEquals(listOf("a"), out["tagIds"])
        assertEquals(3, out["n"])
    }

    @Test
    fun inboundTimestampKeepsMicroseconds() {
        val wire = FirestoreWireAdapter.fromFirestore(
            mapOf("createdAt" to Timestamp(instant.epochSecond, instant.nano), "n" to 3L)
        )
        assertEquals(instant, wire["createdAt"])
        assertEquals(3L, wire["n"])
    }
}
