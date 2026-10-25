package com.digitscore.app.data

import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.model.GoalRewardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReportTest {

    @Test
    fun testWeeklyReportRangeCalculation() {
        // Monday 2026-10-26 -> Last week is Monday 2026-10-19 to Sunday 2026-10-25
        val monday = LocalDate.parse("2026-10-26")
        val (start, end) = WeeklyReportCalculator.getLastWeekRange(monday)
        assertEquals(LocalDate.parse("2026-10-19"), start)
        assertEquals(LocalDate.parse("2026-10-25"), end)

        // Prior week is Monday 2026-10-12 to Sunday 2026-10-18
        val (priorStart, priorEnd) = WeeklyReportCalculator.getPriorWeekRange(start)
        assertEquals(LocalDate.parse("2026-10-12"), priorStart)
        assertEquals(LocalDate.parse("2026-10-18"), priorEnd)
    }

    @Test
    fun testWeeklyReportCalculation_withData() {
        val range = LocalDate.parse("2026-10-19") to LocalDate.parse("2026-10-25")
        val histories = mapOf(
            "2026-10-19" to DailyScoreHistoryEntity("2026-10-19", 75, 120, 30, 20, 100, 35, 4, "balanced"),
            "2026-10-20" to DailyScoreHistoryEntity("2026-10-20", 80, 140, 40, 30, 90, 40, 4, "balanced"),
            "2026-10-21" to DailyScoreHistoryEntity("2026-10-21", 85, 100, 20, 40, 120, 25, 4, "balanced"),
            // Prior week history
            "2026-10-12" to DailyScoreHistoryEntity("2026-10-12", 70, 150, 50, 10, 80, 50, 4, "balanced")
        )

        val dailyApps = listOf(
            DailyAppUsageEntity("2026-10-19", "com.kakao.talk", "KakaoTalk", 60 * 60_000L, 10, 5, 10 * 60_000L, 0L, 2),
            DailyAppUsageEntity("2026-10-20", "com.kakao.talk", "KakaoTalk", 50 * 60_000L, 8, 4, 10 * 60_000L, 0L, 2),
            DailyAppUsageEntity("2026-10-12", "com.kakao.talk", "KakaoTalk", 30 * 60_000L, 5, 2, 8 * 60_000L, 0L, 2)
        )

        val report = WeeklyReportCalculator.calculate(
            lastWeekRange = range,
            histories = histories,
            dailyAppUsage = dailyApps,
            rewardState = GoalRewardState(perfectStamps = 3, availableFreePasses = 1),
            more15MinCount = 2,
            stopForTodayCount = 1
        )

        assertTrue(report.hasData)
        assertEquals(3, report.recordedDaysCount)
        assertNotNull(report.avgScore)
        assertEquals(80.0, report.avgScore!!, 0.01) // (75 + 80 + 85) / 3 = 80.0
        assertEquals(10.0, report.scoreDiffVsPriorWeek!!, 0.01) // 80 - 70 = +10
        assertEquals("2026-10-21", report.highestScoreDay?.first)
        assertEquals(85, report.highestScoreDay?.second)
        assertEquals("2026-10-19", report.lowestScoreDay?.first)
        assertEquals(75, report.lowestScoreDay?.second)

        assertEquals(120L, report.avgScreenTimeMinutes) // (120 + 140 + 100) / 3 = 120
        assertEquals(33, report.avgUnlockCount) // (35 + 40 + 25) / 3 = 33
        assertEquals(3, report.perfectStampsEarned)
        assertEquals(1, report.freePassesEarned)
        assertEquals(2, report.more15MinCount)
        assertEquals(1, report.stopForTodayCount)

        assertNotNull(report.topIncreasedApp)
        assertEquals("com.kakao.talk", report.topIncreasedApp?.packageName)
    }

    @Test
    fun testWeeklyReportCalculation_emptyData() {
        val range = LocalDate.parse("2026-10-19") to LocalDate.parse("2026-10-25")
        val report = WeeklyReportCalculator.calculate(
            lastWeekRange = range,
            histories = emptyMap(),
            dailyAppUsage = emptyList()
        )

        org.junit.Assert.assertFalse(report.hasData)
        assertEquals(0, report.recordedDaysCount)
        org.junit.Assert.assertNull(report.avgScore)
        org.junit.Assert.assertNull(report.scoreDiffVsPriorWeek)
    }
}
