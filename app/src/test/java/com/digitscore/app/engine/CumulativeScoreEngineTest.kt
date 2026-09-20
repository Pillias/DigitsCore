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
            listOf(Use(120, 130, false), Use(480, 490, false), Use(780, 790, false)) to 91.0,
            listOf(30, 180, 330, 480, 630, 780).map { Use(it, it + 10, false) } to 89.39,
            (0 until 24).map { Use(it * 40, it * 40 + 10, it % 4 == 3) } to 75.15,
            listOf(Use(0, 240, true)) to 42.61,
            listOf(Use(0, 360, true)) to 33.22,
            listOf(Use(0, 600, true)) to 21.17
        )
        for ((plan, expected) in cases) {
            val result = run(90, plan)
            assertEquals(expected, result[6], 0.08)
            assertTrue("Day 7 must be close to long-run equilibrium", kotlin.math.abs(result[6] - result[89]) < 2)
        }
    }

    @Test fun sleepFreezesBothScoreAndRecoveryBurden() {
        val used = CumulativeScoreEngine.advance(CumulativeScoreState(), CumulativeActivity.MANAGED_USE, 240.0)
        val asleep = CumulativeScoreEngine.advance(used, CumulativeActivity.SLEEP, 480.0)
        assertEquals(used.signal, asleep.signal, 0.0)
        assertEquals(used.recoveryBurden, asleep.recoveryBurden, 0.0)
        assertEquals(0.0, asleep.awakeRestMinutes, 0.0)
        assertTrue(CumulativeScoreEngine.advance(asleep, CumulativeActivity.AWAKE_REST, 60.0).signal > asleep.signal)
    }

    @Test fun thirtyMinuteRestLowersSixtyMinuteUseByTwoStages() {
        val used = CumulativeScoreEngine.advance(CumulativeScoreState(), CumulativeActivity.NORMAL_USE, 60.0)
        val rested = CumulativeScoreEngine.advance(used, CumulativeActivity.AWAKE_REST, 30.0)
        assertEquals(30.0, rested.useMomentumMinutes, 0.0)
    }

    @Test fun shortCheckCostsOpeningButDoesNotResetRest() {
        val resting = CumulativeScoreState(awakeRestMinutes = 120.0)
        val checked = CumulativeScoreEngine.advance(resting, CumulativeActivity.AWAKE_REST, 1.0, 1)
        assertEquals(121.0, checked.awakeRestMinutes, 0.0)
        assertTrue(checked.signal < CumulativeScoreEngine.advance(resting, CumulativeActivity.AWAKE_REST, 1.0).signal)
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
}
