package com.stoneofhope.stoneofhope.data.sync

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Edits upload about three seconds after the last change, once per burst, and only for a signed-in account. */
@OptIn(ExperimentalCoroutinesApi::class)
class EditPushTriggerTest {
    private val dirty = MutableStateFlow(0)
    private val pushed = mutableListOf<String>()
    private var uid: String? = "user-1"

    private fun kotlinx.coroutines.test.TestScope.start() =
        EditPushTrigger.start(backgroundScope, dirty, delayMs = 3_000, currentUid = { uid }, push = { pushed += it })

    @Test
    fun aSaveUploadsAfterTheQuietPeriod() = runTest {
        start()
        dirty.value = 1
        advanceTimeBy(2_900)
        assertEquals(emptyList<String>(), pushed)
        advanceTimeBy(200)
        assertEquals(listOf("user-1"), pushed)
    }

    @Test
    fun aBurstOfSavesIsOnePush() = runTest {
        start()
        dirty.value = 1
        advanceTimeBy(2_000)
        dirty.value = 2
        advanceTimeBy(2_000)
        dirty.value = 3
        advanceTimeBy(2_000)
        assertEquals(emptyList<String>(), pushed) // each save restarted the wait
        advanceTimeBy(1_500)
        assertEquals(listOf("user-1"), pushed)
    }

    @Test
    fun nothingWaitingMeansNoPush() = runTest {
        start()
        dirty.value = 1
        advanceTimeBy(3_100)
        dirty.value = 0 // the push cleaned everything
        advanceTimeBy(10_000)
        assertEquals(1, pushed.size)
    }

    @Test
    fun signedOutUsersNeverPush() = runTest {
        uid = null
        start()
        dirty.value = 1
        advanceTimeBy(10_000)
        assertEquals(emptyList<String>(), pushed)
    }

    @Test
    fun aLaterEditAfterAPushUploadsAgain() = runTest {
        start()
        dirty.value = 1
        advanceTimeBy(3_100)
        dirty.value = 0
        advanceTimeBy(1_000)
        dirty.value = 1
        advanceTimeBy(3_100)
        assertEquals(2, pushed.size)
    }
}
