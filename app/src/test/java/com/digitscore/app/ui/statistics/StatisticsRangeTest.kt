package com.digitscore.app.ui.statistics

import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsRangeTest {
    private fun history(date: String) = DailyScoreHistoryEntity(
        dateString = date,
        finalScore = 70,
        totalScreenTimeMinutes = 100,
        distractingTimeMinutes = 20,
        productiveTimeMinutes = 10,
        idleMinutes = 200,
        unlockCount = 30
    )

    @Test
    fun sevenAndThirtyDayTabsUseCalendarRanges() {
        val today = LocalDate.of(2026, 9, 7)
        val histories = listOf(
            history("2026-09-07"),
            history("2026-09-01"),
            history("2026-08-31"),
            history("2026-08-09"),
            history("not-a-date")
        )

        assertEquals(
            listOf("2026-09-01", "2026-09-07"),
            historiesInCalendarRange(histories, 7, today).map { it.dateString }
        )
        assertEquals(
            listOf("2026-08-09", "2026-08-31", "2026-09-01", "2026-09-07"),
            historiesInCalendarRange(histories, 30, today).map { it.dateString }
        )
    }
}
