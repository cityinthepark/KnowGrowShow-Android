package com.stoneofhope.stoneofhope.data.sync

import java.time.LocalDate
import org.junit.Test
import org.junit.Assert.assertEquals

/** Ports of iOS `PinLogUnpinRuleTests` (the pure rule); same cases as the web's `pinDaysToRemoveOnUnpin`. */
class PinLogUnpinRuleTest {
    private val oct28 = LocalDate.of(2026, 10, 28)
    private fun rule(days: List<String>, today: LocalDate = oct28) = LocalSyncOps.daysToRemoveOnUnpin(days, today)

    @Test fun keepsTodayAndHistoryWhenYesterdayIsLogged() =
        assertEquals(emptyList<String>(), rule(listOf("2026-10-18", "2026-10-27", "2026-10-28")))

    @Test fun dropsTodayWhenPinOnlyStartedToday() =
        assertEquals(listOf("2026-10-28"), rule(listOf("2026-10-10", "2026-10-28")))

    @Test fun alwaysDropsLaterDaysAndSkipsInvalidEntries() =
        assertEquals(listOf("2026-10-30"), rule(listOf("2026-10-27", "20261028", "2026-10-30", "oops")))

    @Test fun dropsTodayAndLaterDaysTogether() =
        assertEquals(listOf("2026-10-28", "2026-10-29"), rule(listOf("2026-10-28", "2026-10-29")))

    @Test fun neverTouchesEarlierDays() {
        assertEquals(emptyList<String>(), rule(listOf("2026-09-30", "2026-10-26")))
        assertEquals(emptyList<String>(), rule(emptyList()))
    }

    @Test fun yesterdayAcrossMonthBoundary() =
        assertEquals(emptyList<String>(), rule(listOf("2026-10-31", "2026-11-01"), LocalDate.of(2026, 11, 1)))
}
