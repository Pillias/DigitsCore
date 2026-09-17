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
    fun threeHoursOfImmersionManagementUsageFallsNear40() {
        val result = RollingScoreCalculator.calculate(
            listOf(session(minutes = 180, level = 3, end = now)),
            now
        )
        assertTrue("score=${result.finalScore}", result.finalScore in 38..47)
    }

    @Test
    fun threeHoursRestAfterGamingRecoversGraduallyWithoutErasingTheLoad() {
        val gameEnd = now - 180 * 60_000L
        val result = RollingScoreCalculator.calculate(
            listOf(session(minutes = 180, level = 3, end = gameEnd)),
            now
        )
        assertTrue("score=${result.finalScore}", result.finalScore in 60..65)
        assertEquals(ScoreFlow.RECOVERING, result.flow)
    }

    @Test
    fun heavyUseRecoveryDoesNotJumpDuringTheFirstHoursOfSleep() {
        val heavyUseMinutes = 300L
        val afterTwoHours = RollingScoreCalculator.calculate(
            listOf(session(minutes = heavyUseMinutes, level = 3, end = now - 120 * 60_000L)),
            now
        )
        val afterEightHours = RollingScoreCalculator.calculate(
            listOf(session(minutes = heavyUseMinutes, level = 3, end = now - 480 * 60_000L)),
            now
        )

        assertTrue("two-hour score=${afterTwoHours.finalScore}", afterTwoHours.finalScore in 35..40)
        assertTrue("eight-hour score=${afterEightHours.finalScore}", afterEightHours.finalScore in 50..55)
        assertTrue(afterEightHours.finalScore > afterTwoHours.finalScore)
    }

    @Test
    fun balancedRecoverySimulationMatchesTheDocumentedCurve() {
        val restMinutes = listOf(0L, 120L, 180L, 360L, 480L)

        fun curve(usageMinutes: Long): List<Int> = restMinutes.map { rest ->
            RollingScoreCalculator.calculate(
                listOf(session(minutes = usageMinutes, level = 3, end = now - rest * 60_000L)),
                now,
                calibrationUsageMillis = 60 * 60_000L
            ).finalScore
        }

        assertEquals(listOf(42, 56, 62, 72, 74), curve(180L))
        assertEquals(listOf(22, 37, 43, 52, 53), curve(300L))
    }

    @Test
    fun productiveUsageDoesNotCreateBonusAboveItsLowLoadScore() {
        val result = RollingScoreCalculator.calculate(
            listOf(session(minutes = 120, level = 1, end = now)),
            now
        )
        assertTrue(result.finalScore < 100)
    }

    @Test
    fun switchingAppsDoesNotResetContinuousUseAcceleration() {
        val sessions = listOf(
            RollingUsageSession(
                packageName = "study",
                startTimeMillis = now - 90 * 60_000L,
                endTimeMillis = now - 45 * 60_000L,
                categoryLevel = 1
            ),
            RollingUsageSession(
                packageName = "video",
                startTimeMillis = now - 45 * 60_000L,
                endTimeMillis = now,
                categoryLevel = 3
            )
        )

        val result = RollingScoreCalculator.calculate(
            sessions,
            now,
            calibrationUsageMillis = 60 * 60_000L
        )

        assertEquals(90L, result.continuousUsageMinutes)
        assertTrue(result.acuteLoad > 0.0)
    }

    @Test
    fun midRangeResponseKeepsBoundariesAndAmplifiesTheCenter() {
        assertEquals(50.0, RollingScoreCalculator.enhanceMidRangeResponse(50.0), 0.001)
        assertEquals(70.0, RollingScoreCalculator.enhanceMidRangeResponse(70.0), 0.001)
        assertEquals(90.0, RollingScoreCalculator.enhanceMidRangeResponse(90.0), 0.001)

        val responsive60 = RollingScoreCalculator.enhanceMidRangeResponse(60.0)
        val responsive80 = RollingScoreCalculator.enhanceMidRangeResponse(80.0)
        assertTrue("60 mapped to $responsive60", responsive60 < 60.0)
        assertTrue("80 mapped to $responsive80", responsive80 > 80.0)
        assertTrue("gap=${responsive80 - responsive60}", responsive80 - responsive60 > 25.0)
    }

    @Test
    fun ordinaryManagedUsageProducesClearerScoreMovement() {
        val twoHours = RollingScoreCalculator.calculate(
            splitSessions(totalMinutes = 120, level = 3),
            now,
            calibrationUsageMillis = 60 * 60_000L
        )
        val threeHours = RollingScoreCalculator.calculate(
            splitSessions(totalMinutes = 180, level = 3),
            now,
            calibrationUsageMillis = 60 * 60_000L
        )

        assertEquals(87, twoHours.finalScore)
        assertEquals(76, threeHours.finalScore)
        assertTrue("score gap=${twoHours.finalScore - threeHours.finalScore}",
            twoHours.finalScore - threeHours.finalScore >= 11)
    }

    private fun splitSessions(totalMinutes: Int, level: Int): List<RollingUsageSession> {
        val sessions = mutableListOf<RollingUsageSession>()
        var end = now - 180 * 60_000L
        repeat(totalMinutes / 30) { index ->
            sessions += RollingUsageSession(
                packageName = "test.app.$index",
                startTimeMillis = end - 30 * 60_000L,
                endTimeMillis = end,
                categoryLevel = level
            )
            end -= 32 * 60_000L
        }
        return sessions
    }

    private fun session(minutes: Long, level: Int, end: Long) = RollingUsageSession(
        packageName = "test.app",
        startTimeMillis = end - minutes * 60_000L,
        endTimeMillis = end,
        categoryLevel = level
    )
}
