package com.digitscore.app.model

import org.junit.Assert.*
import org.junit.Test

class DailyGoalTest {

    @Test
    fun testProgressRatio() {
        val goal = DailyGoal(
            dateString = "2026-10-03",
            type = DailyGoalType.APP_USAGE_LIMIT,
            targetAppName = "YouTube",
            targetValue = 30,
            currentValue = 15
        )
        assertEquals(0.5f, goal.progressRatio, 0.001f)
        assertFalse(goal.isExceeded)
        assertTrue(goal.isAchieved)
    }

    @Test
    fun testProgressExceeded() {
        val goal = DailyGoal(
            dateString = "2026-10-03",
            type = DailyGoalType.APP_USAGE_LIMIT,
            targetAppName = "YouTube",
            targetValue = 30,
            currentValue = 35
        )
        assertEquals(1.0f, goal.progressRatio, 0.001f)
        assertTrue(goal.isExceeded)
        assertFalse(goal.isAchieved)
    }

    @Test
    fun testScoreDefenseGoal() {
        val goal = DailyGoal(
            dateString = "2026-10-03",
            type = DailyGoalType.SCORE_DEFENSE,
            targetAppName = "코어 지수 방어",
            targetValue = 70,
            currentValue = 75
        )
        assertTrue(goal.isAchieved)
        assertFalse(goal.isExceeded)

        val failedGoal = goal.copy(currentValue = 65)
        assertFalse(failedGoal.isAchieved)
    }

    @Test
    fun testMultiGoalEvaluation() {
        val multiGoal = DailyGoal(
            dateString = "2026-10-06",
            scoreTarget = 70,
            currentScore = 75,
            targetPackageName = "com.google.android.youtube",
            targetAppName = "YouTube",
            appLimitMinutes = 30,
            currentAppUsageMinutes = 20,
            unlockLimitTarget = 40,
            currentUnlockCount = 25
        )

        // All 3 goals are currently within target
        assertTrue(multiGoal.isScoreDefenseAchieved)
        assertTrue(multiGoal.isAppLimitAchieved)
        assertTrue(multiGoal.isUnlockLimitAchieved)
        assertEquals(20f / 30f, multiGoal.appProgressRatio, 0.001f)
        assertEquals(25f / 40f, multiGoal.unlockProgressRatio, 0.001f)

        // When app limit is exceeded
        val exceededAppGoal = multiGoal.copy(currentAppUsageMinutes = 35)
        assertFalse(exceededAppGoal.isAppLimitAchieved)
        assertEquals(35f / 30f, exceededAppGoal.appProgressRatio, 0.001f)

        // When score falls below target
        val failedScoreGoal = multiGoal.copy(currentScore = 65)
        assertFalse(failedScoreGoal.isScoreDefenseAchieved)

        // When unlock count is exceeded
        val exceededUnlockGoal = multiGoal.copy(currentUnlockCount = 45)
        assertFalse(exceededUnlockGoal.isUnlockLimitAchieved)
    }

    @Test
    fun testUnlockGoalEvaluation() {
        val unlockGoal = DailyGoal(
            dateString = "2026-10-06",
            unlockLimitTarget = 40,
            currentUnlockCount = 35
        )
        assertEquals(DailyGoalType.UNLOCK_LIMIT, unlockGoal.type)
        assertTrue(unlockGoal.isAchieved)
        assertFalse(unlockGoal.isExceeded)
        assertEquals(35f / 40f, unlockGoal.progressRatio, 0.001f)

        val exceededUnlock = unlockGoal.copy(currentUnlockCount = 42)
        assertFalse(exceededUnlock.isAchieved)
        assertTrue(exceededUnlock.isExceeded)
    }

    @Test
    fun testLogicalDateCutoff() {
        // DailyGoalStore logical date produces yyyy-MM-dd
        val logicalDate = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
        assertTrue(logicalDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))

        val yesterdayLogical = com.digitscore.app.data.DailyGoalStore.getYesterdayLogicalDateString()
        assertTrue(yesterdayLogical.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertNotEquals(logicalDate, yesterdayLogical)
    }
}


