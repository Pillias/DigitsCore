package com.digitscore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingScoreCalculatorTest {
    private val now = 2_000_000_000L

    @Test
    fun startsAt80WhileThereIsNoMeasuredForegroundUsage() {
        val result = RollingScoreCalculator.calculate(emptyList(), now)
        assertEquals(80, result.finalScore)
        assertEquals(ScoreFlow.CALIBRATING, result.flow)
    }

    @Test
    fun threeHoursOfLevel5UsageFallsNear40() {
        val result = RollingScoreCalculator.calculate(
            listOf(session(minutes = 180, level = 5, end = now)),
            now
        )
        assertTrue("score=${result.finalScore}", result.finalScore in 38..47)
    }

    @Test
    fun threeHoursRestAfterGamingRecoversNear70ButNotTo100() {
        val gameEnd = now - 180 * 60_000L
        val result = RollingScoreCalculator.calculate(
            listOf(session(minutes = 180, level = 5, end = gameEnd)),
            now
        )
        assertTrue("score=${result.finalScore}", result.finalScore in 68..78)
        assertEquals(ScoreFlow.RECOVERING, result.flow)
    }

    @Test
    fun productiveUsageDoesNotCreateBonusAboveItsLowLoadScore() {
        val result = RollingScoreCalculator.calculate(
            listOf(session(minutes = 120, level = 1, end = now)),
            now
        )
        assertTrue(result.finalScore < 100)
    }

    private fun session(minutes: Long, level: Int, end: Long) = RollingUsageSession(
        packageName = "test.app",
        startTimeMillis = end - minutes * 60_000L,
        endTimeMillis = end,
        categoryLevel = level
    )
}
