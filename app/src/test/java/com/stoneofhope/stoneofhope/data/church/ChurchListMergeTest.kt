package com.stoneofhope.stoneofhope.data.church

import com.stoneofhope.stoneofhope.data.local.entity.OnlineChurchEntity
import com.stoneofhope.stoneofhope.data.remote.wire.WireValue
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** `settings/preferences.churches` format and the two-way merge (docs/CHURCH_IMPORT_PLAN.md §2.6). */
class ChurchListMergeTest {

    private val t0 = Instant.parse("2026-10-30T08:00:00.123456Z")
    private fun t(min: Long) = t0.plusSeconds(min * 60)

    private fun row(
        key: String, primary: Boolean = false, importedAt: Instant = t0, version: String? = null,
        updatedAt: Instant = t0, synced: Instant? = t0, dirty: Boolean = false, deleted: Instant? = null,
    ) = OnlineChurchEntity(key, primary, importedAt, version, updatedAt, deleted, dirty, synced)

    private fun entry(key: String, primary: Boolean = false, importedAt: Instant = t0, version: String? = null) =
        ChurchListEntry(key, primary, importedAt, version)

    @Test
    fun wireFormat() {
        val patch = ChurchListWire.encode(listOf(entry("riverside", true, t0, "20260802"), entry("zion", importedAt = t(5))))
        assertEquals(
            listOf(
                mapOf("churchKey" to "riverside", "isPrimary" to true, "importedAt" to "2026-10-30T08:00:00.123Z", "lastImportedVersion" to "20260802"),
                mapOf("churchKey" to "zion", "isPrimary" to false, "importedAt" to "2026-10-30T08:05:00.123Z"),
            ),
            patch["churches"],
        )
        assertEquals("riverside", patch["primaryChurchKey"])
        assertEquals(WireValue.ServerTimestamp, patch["churchesUpdatedAt"])
        // Only our three fields: Web's preferredVersions / updatedAt are never in the patch.
        assertEquals(setOf("churches", "primaryChurchKey", "churchesUpdatedAt"), patch.keys)
        assertEquals(WireValue.DeleteField, ChurchListWire.encode(emptyList())["primaryChurchKey"])

        val stored = patch + ("preferredVersions" to listOf("KJV"))
        assertEquals(
            listOf(entry("riverside", true, Instant.parse("2026-10-30T08:00:00.123Z"), "20260802"), entry("zion", importedAt = Instant.parse("2026-10-30T08:05:00.123Z"))),
            ChurchListWire.decode(stored),
        )
        assertTrue(ChurchListWire.matches(stored, listOf(entry("riverside", true, t0, "20260802"), entry("zion", importedAt = t(5)))))
        assertFalse(ChurchListWire.matches(stored, listOf(entry("riverside", false, t0, "20260802"), entry("zion", true, t(5)))))
        assertFalse(ChurchListWire.matches(stored - "primaryChurchKey", listOf(entry("riverside", true, t0, "20260802"), entry("zion", importedAt = t(5)))))
    }

    @Test
    fun decodeIsTolerant() {
        val doc = mapOf(
            "churches" to listOf(
                mapOf("churchKey" to "riverside", "isPrimary" to true, "importedAt" to "2026-10-30T08:00:00Z"),
                mapOf("churchKey" to "zion", "importedAt" to t0), // a Timestamp from another client
                mapOf("churchKey" to "riverside", "importedAt" to "2026-01-01T00:00:00Z"), // repeated key
                mapOf("churchKey" to "", "importedAt" to "2026-10-30T08:00:00Z"),
                mapOf("churchKey" to "bad", "importedAt" to "yesterday"),
                "not a map",
            ),
        )
        assertEquals(listOf("riverside", "zion"), ChurchListWire.decode(doc).map { it.churchKey })
        assertTrue(ChurchListWire.decode(null).isEmpty())
        assertTrue(ChurchListWire.decode(mapOf("preferredVersions" to listOf("KJV"))).isEmpty())
    }

    @Test
    fun addsRemovesAndCombines() {
        val merged = ChurchListMerge.merge(
            listOf(
                row("new", dirty = true, synced = null, importedAt = t(9)), // imported here, never pushed
                row("goneElsewhere"), // synced before, now missing in the cloud
                row("unfollowed", dirty = true, deleted = t(3)), // removed here
                row("both", primary = true, importedAt = t(4), version = "20260802"),
            ),
            listOf(
                entry("both", true, t(1), "20260726"),
                entry("unfollowed"),
                entry("remoteOnly", importedAt = t(2), version = "20260901"),
            ),
        )
        assertEquals(setOf("goneElsewhere", "unfollowed"), merged.removedLocally)
        assertEquals(
            listOf(
                entry("both", true, t(1), "20260802"), // earliest import, latest version
                entry("remoteOnly", importedAt = t(2), version = "20260901"),
                entry("new", importedAt = t(9)),
            ),
            merged.entries,
        )
    }

    @Test
    fun primaryFollowsWhoeverMovedIt() {
        val cloud = listOf(entry("riverside", true), entry("zion", importedAt = t(1)))
        // This device didn't touch the mark: the cloud's choice stands.
        val untouched = listOf(row("riverside"), row("zion", primary = true, importedAt = t(1)))
        assertEquals("riverside", ChurchListMerge.merge(untouched, cloud).entries.single { it.isPrimary }.churchKey)
        // Moved here after the last sync: this device wins.
        val moved = listOf(row("riverside", updatedAt = t(5), dirty = true), row("zion", primary = true, importedAt = t(1), updatedAt = t(5), dirty = true))
        assertEquals("zion", ChurchListMerge.merge(moved, cloud).entries.single { it.isPrimary }.churchKey)
        // A first sign-in with a church imported here doesn't take the mark from the cloud.
        val fresh = listOf(row("grace", primary = true, importedAt = t(7), synced = null, updatedAt = t(7), dirty = true))
        val joined = ChurchListMerge.merge(fresh, cloud).entries
        assertEquals("riverside", joined.single { it.isPrimary }.churchKey)
        assertEquals(listOf("riverside", "zion", "grace"), joined.map { it.churchKey })
    }

    @Test
    fun primaryFallsBackWhenItsChurchIsGone() {
        // Cloud primary was unfollowed here: this device's primary (handed over locally) takes it.
        val local = listOf(row("riverside", dirty = true, deleted = t(3), updatedAt = t(3)), row("zion", primary = true, importedAt = t(2), updatedAt = t(3), dirty = true), row("grace", importedAt = t(1)))
        val cloud = listOf(entry("riverside", true), entry("zion", importedAt = t(2)), entry("grace", importedAt = t(1)))
        assertEquals("zion", ChurchListMerge.merge(local, cloud).entries.single { it.isPrimary }.churchKey)
        // The cloud primary was unfollowed here and no live row here is primary: the earliest import.
        val noLocalPrimary = listOf(row("riverside", dirty = true, deleted = t(3), updatedAt = t(3)), row("zion", importedAt = t(2)), row("grace", importedAt = t(1)))
        assertEquals("grace", ChurchListMerge.merge(noLocalPrimary, cloud).entries.single { it.isPrimary }.churchKey)
        // Nobody has a usable primary: the earliest import.
        val none = ChurchListMerge.merge(emptyList(), listOf(entry("zion", importedAt = t(2)), entry("grace", importedAt = t(1))))
        assertEquals(listOf("grace", "zion"), none.entries.map { it.churchKey })
        assertTrue(none.entries.first().isPrimary)
        assertTrue(ChurchListMerge.merge(emptyList(), emptyList()).entries.isEmpty())
    }
}
