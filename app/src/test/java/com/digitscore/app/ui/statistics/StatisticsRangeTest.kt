package com.digitscore.app.ui.statistics

import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsRangeTest {
    private fun history(
        date: String,
        scoreModelVersion: Int = 1,
        coreIndexPresetId: String = "balanced"
    ) = DailyScoreHistoryEntity(
        dateString = date,
        finalScore = 70,
        totalScreenTimeMinutes = 100,
        distractingTimeMinutes = 20,
        productiveTimeMinutes = 10,
        idleMinutes = 200,
        unlockCount = 30,
        scoreModelVersion = scoreModelVersion,
        coreIndexPresetId = coreIndexPresetId
    )

    @Test
    fun sevenDayAndFourWeekTabsUseCalendarRanges() {
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
            listOf("2026-08-31", "2026-09-01", "2026-09-07"),
            historiesInCalendarRange(histories, 28, today).map { it.dateString }
        )
    }

    @Test
    fun fourteenRecordedDaysOccupyHalfOfTheFourWeekAxis() {
        val today = LocalDate.of(2026, 9, 14)

        assertEquals(0, calendarDayOffset("2026-08-18", 28, today))
        assertEquals(14, calendarDayOffset("2026-09-01", 28, today))
        assertEquals(27, calendarDayOffset("2026-09-14", 28, today))
    }

    @Test
    fun scoreTrendOnlyUsesCoreIndexRecords() {
        val histories = listOf(
            history("2026-09-08"),
            history("2026-09-09", scoreModelVersion = 2),
            history("2026-09-10", scoreModelVersion = 3)
        )

        assertEquals(
            listOf("2026-09-09", "2026-09-10"),
            coreIndexHistories(histories).map { it.dateString }
        )
    }

    @Test
    fun presetChangesAreReportedAtTheFirstDayUsingTheNewPreset() {
        val histories = listOf(
            history("2026-09-08", 2, "balanced"),
            history("2026-09-09", 2, "balanced"),
            history("2026-09-10", 2, "focus"),
            history("2026-09-11", 2, "screen_rest")
        )

        assertEquals(
            listOf(
                CoreIndexPresetChange("2026-09-10", "focus"),
                CoreIndexPresetChange("2026-09-11", "screen_rest")
            ),
            coreIndexPresetChanges(histories)
        )
    }
}
