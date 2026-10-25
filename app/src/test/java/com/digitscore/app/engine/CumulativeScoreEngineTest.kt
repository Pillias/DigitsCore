package com.digitscore.app.engine

import org.junit.Assert.*
import org.junit.Test

class CumulativeScoreEngineTest {
    private val config = CumulativeScoreConfig.CURRENT
    private data class Use(val start: Int, val end: Int, val managed: Boolean)
    private fun run(days: Int, uses: List<Use>): List<Double> {
        var state = CumulativeScoreState()
        val results = mutableListOf<Double>()
        repeat(days) {
            for (minute in 0 until 1440) {
                val use = uses.firstOrNull { minute in it.start until it.end }
                val check = use == null && minute in listOf(60, 270, 450, 660, 900)
                val activity = when {
                    use?.managed == true -> CumulativeActivity.MANAGED_USE
                    use != null -> CumulativeActivity.NORMAL_USE
                    minute >= 960 -> CumulativeActivity.SLEEP
                    else -> CumulativeActivity.AWAKE_REST
                }
                state = CumulativeScoreEngine.advance(state, activity, 1.0,
                    if (check || use?.start == minute) 1 else 0)
            }
            results.add(config.displayed(state.signal))
        }
        return results
    }

    @Test fun sevenDayCalibrationMatchesAgreedSyntheticBenchmarks() {
        val cases = listOf(
            listOf(Use(120, 130, false), Use(480, 490, false), Use(780, 790, false)) to 96.96,
            listOf(30, 180, 330, 480, 630, 780).map { Use(it, it + 10, false) } to 96.83,
            (0 until 24).map { Use(it * 40, it * 40 + 10, it % 4 == 3) } to 85.31,
            listOf(Use(0, 240, true)) to 50.81,
            listOf(Use(0, 360, true)) to 33.50,
            listOf(Use(0, 600, true)) to 13.08
        )
        for ((plan, expected) in cases) {
            val result = run(90, plan)
            assertEquals(expected, result[6], 0.1)
            assertTrue("Day 7 must be close to long-run equilibrium", kotlin.math.abs(result[6] - result[89]) < 2.5)
        }
    }

    @Test fun sleepRestoresScoreTowardsTargetAndDecaysBurden() {
        val used = CumulativeScoreEngine.advance(CumulativeScoreState(), CumulativeActivity.MANAGED_USE, 240.0)
        val asleep = CumulativeScoreEngine.advance(used, CumulativeActivity.SLEEP, 480.0)
        assertTrue("Sleep must restore score towards target when below 75", asleep.signal > used.signal)
        assertTrue("Sleep must decay recovery burden", asleep.recoveryBurden < used.recoveryBurden)
        assertTrue("Rest minutes should accumulate during sleep", asleep.awakeRestMinutes > 0)
        assertTrue(CumulativeScoreEngine.advance(asleep, CumulativeActivity.AWAKE_REST, 60.0).signal > asleep.signal)
    }

    @Test fun thirtyMinuteRestLowersSixtyMinuteUseByTwoStages() {
        val used = CumulativeScoreEngine.advance(CumulativeScoreState(), CumulativeActivity.MANAGED_USE, 60.0)
        val rested = CumulativeScoreEngine.advance(used, CumulativeActivity.AWAKE_REST, 15.0)
        assertEquals(30.0, rested.useMomentumMinutes, 0.0)
    }

    @Test fun shortCheckCostsOpeningButDoesNotResetRest() {
        val resting = CumulativeScoreState(awakeRestMinutes = 120.0)
        val checked = CumulativeScoreEngine.advance(resting, CumulativeActivity.AWAKE_REST, 1.0, 1)
        assertEquals(121.0, checked.awakeRestMinutes, 0.0)
        val withoutOpening = CumulativeScoreEngine.advance(resting, CumulativeActivity.AWAKE_REST, 1.0, 0)
        assertEquals(withoutOpening.signal, checked.signal, 1e-9)
    }

    @Test fun awakeRestRecoversFasterThanSleep() {
        val state = CumulativeScoreState(signal = 60.0)
        val awake = CumulativeScoreEngine.advance(state, CumulativeActivity.AWAKE_REST, 60.0)
        val sleep = CumulativeScoreEngine.advance(state, CumulativeActivity.SLEEP, 60.0)
        assertTrue("Awake rest should recover faster than sleep", awake.signal > sleep.signal)
    }

    @Test fun normalUseDoesNotAccumulateMomentum() {
        val state = CumulativeScoreState()
        val after = CumulativeScoreEngine.advance(state, CumulativeActivity.NORMAL_USE, 60.0)
        assertEquals(0.0, after.useMomentumMinutes, 0.0)
    }

    @Test fun unknownCoverageDoesNotGrantRecoveryOrClearBurden() {
        val state = CumulativeScoreState(recoveryBurden = 12.0)
        val after = CumulativeScoreEngine.advance(state, CumulativeActivity.UNKNOWN, 2880.0)
        assertEquals(state.signal, after.signal, 0.0)
        assertEquals(state.recoveryBurden, after.recoveryBurden, 0.0)
    }

    @Test fun noMidnightOrFortyEightHourResetAndMappingIsMonotonic() {
        var state = CumulativeScoreState()
        repeat(7 * 1440) { state = CumulativeScoreEngine.advance(state, CumulativeActivity.MANAGED_USE, 1.0) }
        assertTrue(config.displayed(state.signal) < 20)
        var previous = 0.0
        for (i in 0..1000) {
            val score = config.displayed(i / 10.0)
            assertTrue(score >= previous && score <= 99.0)
            previous = score
        }
        assertEquals(75.0, config.displayed(config.signalForScore(75.0)), 1e-9)
    }

    @Test fun exemptUtilityFreeForThirtyMinutesThenNormalDeduction() {
        val initial = CumulativeScoreState(signal = 75.0)
        // 30분 사용: 점수 변화 없음 (감점도 회복도 없음)
        val after30 = CumulativeScoreEngine.advance(initial, CumulativeActivity.EXEMPT_UTILITY, 30.0)
        assertEquals(75.0, after30.signal, 1e-9)
        assertEquals(30.0, after30.exemptMomentumMinutes, 1e-9)
        assertEquals(0.0, after30.awakeRestMinutes, 0.0)

        // 30분 초과 시점부터 일반 감점 시작
        val after60 = CumulativeScoreEngine.advance(after30, CumulativeActivity.EXEMPT_UTILITY, 30.0)
        assertTrue("Score should decrease after 30 minutes of utility exempt use", after60.signal < after30.signal)
        assertEquals(60.0, after60.exemptMomentumMinutes, 1e-9)
    }

    @Test fun exemptStudyFreeForFiftyMinutesThenNormalDeduction() {
        val initial = CumulativeScoreState(signal = 75.0)
        // 50분 사용: 점수 변화 없음 (감점도 회복도 없음)
        val after50 = CumulativeScoreEngine.advance(initial, CumulativeActivity.EXEMPT_STUDY, 50.0)
        assertEquals(75.0, after50.signal, 1e-9)
        assertEquals(50.0, after50.studyMomentumMinutes, 1e-9)
        assertEquals(0.0, after50.exemptMomentumMinutes, 1e-9)
        assertEquals(0.0, after50.awakeRestMinutes, 0.0)

        // 50분 초과 시점부터 일반 감점 시작
        val after70 = CumulativeScoreEngine.advance(after50, CumulativeActivity.EXEMPT_STUDY, 20.0)
        assertTrue("Score should decrease after 50 minutes of study exempt use", after70.signal < after50.signal)
        assertEquals(70.0, after70.studyMomentumMinutes, 1e-9)
    }

    @Test fun studyAndUtilityExemptCountersAreIndependent() {
        val studied = CumulativeScoreEngine.advance(CumulativeScoreState(signal = 75.0), CumulativeActivity.EXEMPT_STUDY, 40.0)
        val utility = CumulativeScoreEngine.advance(studied, CumulativeActivity.EXEMPT_UTILITY, 10.0)
        assertEquals(75.0, utility.signal, 1e-9)
        assertEquals(40.0, utility.studyMomentumMinutes, 1e-9)
        assertEquals(10.0, utility.exemptMomentumMinutes, 1e-9)
    }

    @Test fun scoreModelStoredVersionTracksConfigVersion() {
        assertEquals(ScoreModel.CURRENT_STORED, ScoreModel.current)
        assertEquals(5, ScoreModel.storedVersion(2))
        assertEquals(6, ScoreModel.storedVersion(3))
    }
}
