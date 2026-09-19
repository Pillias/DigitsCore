package com.digitscore.app.engine

import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.CoreIndexScoringConfig
import com.digitscore.app.model.defaultScoringConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RollingScoreCalculatorTest {
    private val now: Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 14)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun startsAt80WhileThereIsNoMeasuredForegroundUsage() {
        val result = RollingScoreCalculator.calculate(emptyList(), now)
        assertEquals(80, result.finalScore)
        assertEquals(ScoreFlow.CALIBRATING, result.flow)
    }

    @Test
    fun nightRestRecoveryNeverReversesAtDetectionThreshold() {
        val start = timeAt(3, 0)
        val values = (0L..300L).map { minute ->
            RollingScoreCalculator.analyzeRestGap(start, start + minute * 60_000L,
                emptyList(), CoreIndexScoringConfig()).effectiveMinutes
        }
        assertTrue(values.zipWithNext().all { (a, b) -> b >= a })
    }

    @Test
    fun sleepingAfterFiveDoesNotBypassRecoveryLimit() {
        val values = listOf(timeAt(4, 59), timeAt(5, 0)).map { start ->
            RollingScoreCalculator.analyzeRestGap(start, start + 180 * 60_000L,
                emptyList(), CoreIndexScoringConfig()).effectiveMinutes
        }
        assertEquals(values[0], values[1], 0.001)
        assertTrue(values.all { it < 5 })
    }

    @Test
    fun oneMorningUnlockDoesNotConfirmWake() {
        val start = timeAt(3, 0)
        val result = RollingScoreCalculator.analyzeRestGap(start, timeAt(9, 0),
            listOf(timeAt(7, 0)), CoreIndexScoringConfig())
        assertEquals(null, result.estimatedWakeTimeMillis)
        assertEquals(0L, result.postWakeMinutes)
    }

    @Test
    fun repeatedUnlocksAcrossShortSessionsConfirmWake() {
        val nightEnd = timeAt(3, 0)
        val first = timeAt(7, 0)
        val second = timeAt(7, 20)
        val result = RollingScoreCalculator.calculate(
            listOf(session(180, 3, nightEnd), rawSession(first, first + 60_000L, 1, "check"),
                rawSession(second, second + 60_000L, 1, "check")),
            second + 121 * 60_000L,
            rollingUnlockTimestamps = listOf(first, second),
            calibrationUsageMillis = 3_600_000L
        )
        assertEquals(120L, result.postWakeRestMinutes)
        assertEquals(120L, result.effectiveRecoveryMinutes)
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
        val dayNow = timeAt(hour = 20, minute = 0)
        val afterTwoHours = RollingScoreCalculator.calculate(
            listOf(session(minutes = heavyUseMinutes, level = 3, end = dayNow - 120 * 60_000L)),
            dayNow
        )
        val afterEightHours = RollingScoreCalculator.calculate(
            listOf(session(minutes = heavyUseMinutes, level = 3, end = dayNow - 480 * 60_000L)),
            dayNow
        )

        assertTrue("two-hour score=${afterTwoHours.finalScore}", afterTwoHours.finalScore in 35..40)
        assertTrue("eight-hour score=${afterEightHours.finalScore}", afterEightHours.finalScore in 50..55)
        assertTrue(afterEightHours.finalScore > afterTwoHours.finalScore)
    }

    @Test
    fun balancedRecoverySimulationMatchesTheDocumentedCurve() {
        val restMinutes = listOf(0L, 120L, 180L, 360L, 480L)
        val dayNow = timeAt(hour = 20, minute = 0)

        fun curve(usageMinutes: Long): List<Int> = restMinutes.map { rest ->
            RollingScoreCalculator.calculate(
                listOf(session(minutes = usageMinutes, level = 3, end = dayNow - rest * 60_000L)),
                dayNow,
                calibrationUsageMillis = 60 * 60_000L
            ).finalScore
        }

        assertEquals(listOf(42, 56, 62, 72, 74), curve(180L))
        assertEquals(listOf(22, 37, 43, 52, 53), curve(300L))
    }

    @Test
    fun lateNightContinuousUseKeepsCarryoverLoadAfterRest() {
        val lateUseEnd = timeAt(hour = 4, minute = 30)
        val restMinutes = listOf(0L, 120L, 180L, 360L, 480L)
        val scores = restMinutes.map { rest ->
            RollingScoreCalculator.calculate(
                listOf(
                    session(
                        minutes = 300L,
                        level = 3,
                        end = lateUseEnd,
                        isLateNight = true
                    )
                ),
                lateUseEnd + rest * 60_000L,
                calibrationUsageMillis = 60 * 60_000L,
                config = CoreIndexPreset.BALANCED.defaultScoringConfig.copy(
                    isSleepFreezeEnabled = false
                )
            )
        }

        assertTrue(scores.zipWithNext().all { (before, after) -> after.finalScore >= before.finalScore })
        assertTrue(scores.last().lateNightCarryoverLoad > 0.0)
        assertTrue(scores.last().finalScore < 50)
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

        assertTrue("two-hour score=${twoHours.finalScore}", twoHours.finalScore in 65..90)
        assertTrue("three-hour score=${threeHours.finalScore}", threeHours.finalScore in 45..80)
        assertTrue("score gap=${twoHours.finalScore - threeHours.finalScore}",
            twoHours.finalScore - threeHours.finalScore >= 8)
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

    @Test
    fun sleepRecoveryFreezesLoadAndPreventsMassiveScoreJump() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 30)
            set(java.util.Calendar.SECOND, 0)
        }
        val sleepStart = cal.timeInMillis
        val wakeUp = sleepStart + 480 * 60_000L

        val beforeSleep = RollingScoreCalculator.calculate(
            listOf(session(minutes = 180, level = 3, end = sleepStart)),
            sleepStart,
            calibrationUsageMillis = 60 * 60_000L
        )
        val afterSleep = RollingScoreCalculator.calculate(
            listOf(session(minutes = 180, level = 3, end = sleepStart)),
            wakeUp,
            calibrationUsageMillis = 60 * 60_000L
        )

        assertTrue("before=${beforeSleep.finalScore}, after=${afterSleep.finalScore}",
            afterSleep.finalScore - beforeSleep.finalScore <= 15)
        assertTrue(afterSleep.acuteLoad > 0.0)
        assertEquals(90L, afterSleep.effectiveRecoveryMinutes)
    }

    @Test
    fun lateShortSleepCreatesAlmostNoRecovery() {
        val sleepStart = timeAt(hour = 3, minute = 0)
        val morning = sleepStart + 5 * 60 * 60_000L

        val rest = RollingScoreCalculator.analyzeRestGap(
            startMillis = sleepStart,
            endMillis = morning,
            unlockTimestamps = emptyList(),
            config = CoreIndexScoringConfig()
        )

        assertTrue(rest.isSleepRest)
        assertEquals(300L, rest.sleepMinutes)
        assertTrue("effective=${rest.effectiveMinutes}", rest.effectiveMinutes <= 15.0)
    }

    @Test
    fun morningUnlockStartsNormalRecoveryOnlyAfterWake() {
        val sleepStart = timeAt(hour = 23, minute = 30) - 24 * 60 * 60_000L
        val wake = timeAt(hour = 7, minute = 30)
        val twoHoursAfterWake = wake + 120 * 60_000L

        val atWake = RollingScoreCalculator.analyzeRestGap(
            startMillis = sleepStart,
            endMillis = wake,
            unlockTimestamps = listOf(wake - 10 * 60_000L, wake),
            config = CoreIndexScoringConfig()
        )
        val afterWake = RollingScoreCalculator.analyzeRestGap(
            startMillis = sleepStart,
            endMillis = twoHoursAfterWake,
            unlockTimestamps = listOf(wake - 10 * 60_000L, wake),
            config = CoreIndexScoringConfig()
        )

        assertEquals(wake, afterWake.estimatedWakeTimeMillis)
        assertEquals(0L, atWake.postWakeMinutes)
        assertEquals(120L, afterWake.postWakeMinutes)
        assertEquals(90.0, atWake.effectiveMinutes, 0.01)
        assertEquals(210.0, afterWake.effectiveMinutes, 0.01)
    }

    @Test
    fun screenOffTimeAfterObservedWakeSessionRecoversNormally() {
        val lateUseEnd = timeAt(hour = 23, minute = 30) - 24 * 60 * 60_000L
        val wakeStart = timeAt(hour = 7, minute = 30)
        val wakeEnd = wakeStart + 10 * 60_000L
        val sessions = listOf(
            session(minutes = 180, level = 3, end = lateUseEnd),
            rawSession(wakeStart, wakeEnd, 1, "wake.check")
        )

        val atWake = RollingScoreCalculator.calculate(
            sessions = sessions,
            nowMillis = wakeEnd,
            rollingUnlockTimestamps = listOf(wakeStart),
            calibrationUsageMillis = 60 * 60_000L
        )
        val afterTwoHours = RollingScoreCalculator.calculate(
            sessions = sessions,
            nowMillis = wakeEnd + 120 * 60_000L,
            rollingUnlockTimestamps = listOf(wakeStart),
            calibrationUsageMillis = 60 * 60_000L
        )

        assertTrue("wake=${atWake.finalScore}, later=${afterTwoHours.finalScore}",
            afterTwoHours.finalScore > atWake.finalScore)
        assertEquals(120L, afterTwoHours.effectiveRecoveryMinutes)
    }

    @Test
    fun appSwitchesDoNotHideLongContinuousUseAcceleration() {
        val contiguous = listOf(
            rawSession(now - 180 * 60_000L, now - 120 * 60_000L, 3, "game"),
            rawSession(now - 120 * 60_000L, now - 60 * 60_000L, 2, "browser"),
            rawSession(now - 60 * 60_000L, now, 1, "study")
        )
        val separated = listOf(
            rawSession(now - 200 * 60_000L, now - 140 * 60_000L, 3, "game"),
            rawSession(now - 130 * 60_000L, now - 70 * 60_000L, 2, "browser"),
            rawSession(now - 60 * 60_000L, now, 1, "study")
        )

        val contiguousResult = RollingScoreCalculator.calculate(
            contiguous,
            now,
            calibrationUsageMillis = 60 * 60_000L
        )
        val separatedResult = RollingScoreCalculator.calculate(
            separated,
            now,
            calibrationUsageMillis = 60 * 60_000L
        )

        assertEquals(180L, contiguousResult.continuousUsageMinutes)
        assertTrue(contiguousResult.acuteLoad > separatedResult.acuteLoad)
        assertTrue(contiguousResult.finalScore < separatedResult.finalScore)
    }

    @Test
    fun lateNightMultiplierIsWeightedAcrossTierBoundary() {
        val sessionEnd = timeAt(hour = 1, minute = 30)
        val crossing = session(minutes = 60, level = 3, end = sessionEnd)

        val multiplier = RollingScoreCalculator.getLateNightMultiplier(
            crossing,
            CoreIndexScoringConfig(
                lateNightTier1Multiplier = 1.4,
                lateNightTier2Multiplier = 2.0
            )
        )

        assertEquals(1.7, multiplier, 0.02)
    }

    @Test
    fun daytimeRestAllowsNormalHealthyRecovery() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }
        val restStart = cal.timeInMillis
        val restEnd = restStart + 180 * 60_000L

        val atEnd = RollingScoreCalculator.calculate(
            listOf(session(minutes = 120, level = 3, end = restStart)),
            restStart,
            calibrationUsageMillis = 60 * 60_000L
        )
        val afterDayRest = RollingScoreCalculator.calculate(
            listOf(session(minutes = 120, level = 3, end = restStart)),
            restEnd,
            calibrationUsageMillis = 60 * 60_000L
        )

        assertTrue(afterDayRest.finalScore > atEnd.finalScore)
    }

    @Test
    fun tieredLateNightMultipliersApplyDifferentiatedLoad() {
        val cal1 = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 23); set(java.util.Calendar.MINUTE, 30) }
        val cal2 = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 2); set(java.util.Calendar.MINUTE, 30) }

        val tier1Sess = session(minutes = 60, level = 3, end = cal1.timeInMillis)
        val tier2Sess = session(minutes = 60, level = 3, end = cal2.timeInMillis)

        val tier1Score = RollingScoreCalculator.calculate(listOf(tier1Sess), cal1.timeInMillis, calibrationUsageMillis = 60 * 60_000L)
        val tier2Score = RollingScoreCalculator.calculate(listOf(tier2Sess), cal2.timeInMillis, calibrationUsageMillis = 60 * 60_000L)

        assertTrue("tier1=${tier1Score.finalScore}, tier2=${tier2Score.finalScore}", tier2Score.finalScore < tier1Score.finalScore)
    }

    private fun session(
        minutes: Long,
        level: Int,
        end: Long,
        isLateNight: Boolean = false
    ) = RollingUsageSession(
        packageName = "test.app",
        startTimeMillis = end - minutes * 60_000L,
        endTimeMillis = end,
        categoryLevel = level,
        isLateNight = isLateNight
    )

    private fun rawSession(start: Long, end: Long, level: Int, packageName: String) =
        RollingUsageSession(
            packageName = packageName,
            startTimeMillis = start,
            endTimeMillis = end,
            categoryLevel = level
        )

    private fun timeAt(hour: Int, minute: Int): Long = java.util.Calendar.getInstance().apply {
        timeInMillis = now
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
}
