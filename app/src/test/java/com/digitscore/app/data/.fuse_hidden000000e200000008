package com.digitscore.app.data

import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.model.DailyGoalDayRecord
import com.digitscore.app.model.GoalRewardState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            // Prior week history (3 days)
            "2026-10-12" to DailyScoreHistoryEntity("2026-10-12", 70, 150, 50, 10, 80, 50, 4, "balanced"),
            "2026-10-13" to DailyScoreHistoryEntity("2026-10-13", 70, 150, 50, 10, 80, 50, 4, "balanced"),
            "2026-10-14" to DailyScoreHistoryEntity("2026-10-14", 70, 150, 50, 10, 80, 50, 4, "balanced")
        )

        val dailyApps = listOf(
            DailyAppUsageEntity("2026-10-19", "com.kakao.talk", "KakaoTalk", 60 * 60_000L, 10, 5, 10 * 60_000L, 0L, 2),
            DailyAppUsageEntity("2026-10-20", "com.kakao.talk", "KakaoTalk", 50 * 60_000L, 8, 4, 10 * 60_000L, 0L, 2),
            DailyAppUsageEntity("2026-10-12", "com.kakao.talk", "KakaoTalk", 30 * 60_000L, 5, 2, 8 * 60_000L, 0L, 2)
        )

        val goalRecords = listOf(
            DailyGoalDayRecord("2026-10-19", scoreMet = true, appMet = true, unlockMet = false, perfect = false, stampEarned = 0, passEarned = 0),
            DailyGoalDayRecord("2026-10-20", scoreMet = true, appMet = true, unlockMet = true, perfect = true, stampEarned = 1, passEarned = 0),
            DailyGoalDayRecord("2026-10-21", scoreMet = true, appMet = true, unlockMet = true, perfect = true, stampEarned = 1, passEarned = 1)
        )

        val report = WeeklyReportCalculator.calculate(
            lastWeekRange = range,
            histories = histories,
            dailyAppUsage = dailyApps,
            dailyGoalRecords = goalRecords,
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

        // Goal records assertions
        assertTrue(report.hasGoalHistory)
        assertEquals(3, report.scoreGoalMetDays)
        assertEquals(3, report.appGoalMetDays)
        assertEquals(2, report.unlockGoalMetDays)
        assertEquals(2, report.perfectDaysCount)
        assertEquals(2, report.perfectStampsEarned)
        assertEquals(1, report.freePassesEarned)

        assertEquals(120L, report.avgScreenTimeMinutes) // (120 + 140 + 100) / 3 = 120
        assertEquals(33, report.avgUnlockCount) // (35 + 40 + 25) / 3 = 33
        assertEquals(2, report.more15MinCount)
        assertEquals(1, report.stopForTodayCount)

        assertNotNull(report.topIncreasedApp)
        assertEquals("com.kakao.talk", report.topIncreasedApp?.packageName)
    }

    @Test
    fun testWeeklyReportCalculation_noPriorWeekData() {
        val range = LocalDate.parse("2026-10-19") to LocalDate.parse("2026-10-25")
        val histories = mapOf(
            "2026-10-19" to DailyScoreHistoryEntity("2026-10-19", 75, 120, 30, 20, 100, 35, 4, "balanced"),
            "2026-10-20" to DailyScoreHistoryEntity("2026-10-20", 80, 140, 40, 30, 90, 40, 4, "balanced")
        )
        val report = WeeklyReportCalculator.calculate(
            lastWeekRange = range,
            histories = histories,
            dailyAppUsage = emptyList()
        )

        assertTrue(report.hasData)
        assertEquals(2, report.recordedDaysCount)
        org.junit.Assert.assertNull(report.scoreDiffVsPriorWeek)
        org.junit.Assert.assertNull(report.screenTimeDiffMinutes)
        org.junit.Assert.assertNull(report.unlockDiffCount)
        org.junit.Assert.assertNull(report.shortSessionDiffCount)
        org.junit.Assert.assertNull(report.topIncreasedApp)
        org.junit.Assert.assertNull(report.topDecreasedApp)
    }

    @Test
    fun testWeeklyReportCalculation_emptyData() {
        val range = LocalDate.parse("2026-10-19") to LocalDate.parse("2026-10-25")
        val report = WeeklyReportCalculator.calculate(
            lastWeekRange = range,
            histories = emptyMap(),
            dailyAppUsage = emptyList()
        )

        assertFalse(report.hasData)
        assertFalse(report.hasGoalHistory)
        assertEquals(0, report.recordedDaysCount)
        assertEquals(0, report.scoreGoalMetDays)
        org.junit.Assert.assertNull(report.avgScore)
        org.junit.Assert.assertNull(report.scoreDiffVsPriorWeek)
    }
    @Test
    fun indexCentredSummaryComparesWithPriorFourWeeksAndSplitsCauses() {
        val range = LocalDate.parse("2026-10-19") to LocalDate.parse("2026-10-25")
        fun h(d: String, score: Int) = d to DailyScoreHistoryEntity(d, score, 120, 30, 20, 100, 35, 4, "balanced")
        val histories = mapOf(
            h("2026-10-19", 80), h("2026-10-20", 80),
            // 직전 4주: 9/21 ~ 10/18
            h("2026-10-18", 70), h("2026-10-05", 74), h("2026-09-21", 72)
        )
        val impacts = listOf(
            com.digitscore.app.data.entity.ScoreImpactHourEntity(0, "", loss = 10.0, recovery = 12.0, firstAt = 0, lastAt = 0),
            com.digitscore.app.data.entity.ScoreImpactHourEntity(0, "game", loss = 9.0, firstAt = 0, lastAt = 0),
            com.digitscore.app.data.entity.ScoreImpactHourEntity(0, StatisticsStore.UNLOCK_IMPACT_KEY, loss = 1.0, recovery = 0.4, firstAt = 0, lastAt = 0)
        )
        val report = WeeklyReportCalculator.calculate(range, histories, emptyList(), weekImpacts = impacts, isManaged = { it == "game" })
        assertEquals(8.0, report.scoreDiffVsPriorFourWeeks!!, 1e-9)
        val cause = report.causeBreakdownPerDay!!
        assertEquals(-4.5, cause.appUse, 1e-9) // 2일 기록: -9 / 2
        assertEquals(-0.3, cause.unlocks, 1e-9)
        assertEquals(5.8, cause.rest, 1e-9)
    }
}
