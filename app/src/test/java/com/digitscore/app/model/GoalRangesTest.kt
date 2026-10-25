package com.digitscore.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalRangesTest {
    @Test
    fun scoreRangeStaysNearCurrentScoreInOnePointSteps() {
        val spec = GoalRanges.score(currentScore = 75, recentFinalScores = listOf(72, 74, 70))
        assertEquals(57, spec.min) // 72(평균)-15
        assertEquals(85, spec.max) // 75+10
        assertEquals(1, spec.step)
        assertEquals(72, spec.suggested)
        assertEquals(27, spec.sliderSteps)
    }

    @Test
    fun scoreWithoutHistorySuggestsFiveBelowNow() {
        val spec = GoalRanges.score(75, emptyList())
        assertEquals(60, spec.min)
        assertEquals(70, spec.suggested)
    }

    @Test
    fun appLimitCannotGoBelowTodayAndTopIsNotThreeHoursForOneHourUse() {
        val spec = GoalRanges.appLimit(todayMinutes = 62, recentDailyMinutes = listOf(60, 70, 50))
        assertEquals(65, spec.min)
        assertEquals(95, spec.max) // 62*1.5=93 → 95
        assertEquals(65, spec.suggested) // 평균 60의 80%=48 → 하한 65
        assertTrue(spec.max < 180)
    }

    @Test
    fun appLimitInTheMorningSuggestsBelowAverage() {
        val spec = GoalRanges.appLimit(todayMinutes = 0, recentDailyMinutes = listOf(60, 70, 50))
        assertEquals(5, spec.min)
        assertEquals(90, spec.max)
        assertEquals(50, spec.suggested)
    }

    @Test
    fun oldGoalOutsideRangeIsReplacedBySuggestion() {
        val spec = GoalRanges.appLimit(62, listOf(60))
        assertEquals(spec.suggested, spec.initial(180))
        assertEquals(70, spec.initial(70))
    }

    @Test
    fun unlockRangeStartsFromTodayCount() {
        val spec = GoalRanges.unlockLimit(todayUnlocks = 33, recentDailyUnlocks = listOf(60, 80))
        assertEquals(35, spec.min)
        assertEquals(105, spec.max)
        assertEquals(65, spec.suggested)
        assertEquals(40, spec.snap(41f))
    }
}
