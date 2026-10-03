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
}
