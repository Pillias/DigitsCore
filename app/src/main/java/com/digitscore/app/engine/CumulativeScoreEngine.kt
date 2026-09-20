package com.digitscore.app.engine

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow

/** Versioned calibration, deliberately independent of Android and UI settings.
 * Targets describe synthetic usage schedules, not medical or population norms.
 * Never change an existing version in place after release.
 */
data class CumulativeScoreConfig(
    val version: Int = 1,
    val initialScore: Double = 75.0,
    val normalLossPerMinute: Double = 0.04,
    val managedLossPerMinute: Double = 0.07,
    val openingLoss: Double = 0.1,
    val accelerationStartMinutes: Double = 30.0,
    val accelerationStepMinutes: Double = 15.0,
    val normalAcceleration: Double = 0.06,
    val managedAcceleration: Double = 0.12,
    val recoveryScale: Double = 1.02,
    val burdenHalfLifeAwakeHours: Double = 4.5,
    val burdenRecoveryDivisor: Double = 2.0,
    val responseAnchors: List<Pair<Double, Double>> = listOf(
        0.0 to 5.0, 20.0 to 5.0, 30.0 to 1.0, 40.0 to 0.65,
        55.0 to 0.45, 100.0 to 0.45
    ),
    val scoreAnchors: List<Pair<Double, Double>> = listOf(
        0.0 to 1.0, 14.775 to 20.0, 26.735 to 30.0, 33.95 to 40.0,
        70.43 to 75.0, 79.595 to 90.0, 90.0 to 95.0, 95.0 to 99.0, 100.0 to 99.0
    )
) {
    init {
        require(version > 0 && initialScore in 1.0..99.0)
        require(listOf(normalLossPerMinute, managedLossPerMinute, openingLoss,
            normalAcceleration, managedAcceleration, recoveryScale).all { it.isFinite() && it >= 0 })
        require(accelerationStepMinutes > 0 && accelerationStartMinutes >= 0)
        require(burdenHalfLifeAwakeHours > 0 && burdenRecoveryDivisor > 0)
        for (anchors in listOf(responseAnchors, scoreAnchors)) {
            require(anchors.size >= 2 && anchors.all { it.first.isFinite() && it.second.isFinite() })
            require(anchors.zipWithNext().all { (a, b) -> b.first > a.first })
        }
        require(responseAnchors.all { it.second > 0 })
        require(scoreAnchors.zipWithNext().all { (a, b) -> b.second >= a.second })
    }

    fun displayed(signal: Double): Double = interpolate(signal, scoreAnchors).coerceIn(1.0, 99.0)
    fun signalForScore(score: Double): Double {
        val unique = scoreAnchors.distinctBy { it.second }.map { it.second to it.first }
        return interpolate(score.coerceIn(1.0, 99.0), unique).coerceIn(0.0, 100.0)
    }

    companion object {
        val CURRENT = CumulativeScoreConfig()
        internal fun interpolate(x: Double, anchors: List<Pair<Double, Double>>): Double {
            if (x <= anchors.first().first) return anchors.first().second
            for (i in 1 until anchors.size) {
                val a = anchors[i - 1]
                val b = anchors[i]
                if (x <= b.first) return a.second + (b.second - a.second) * (x - a.first) / (b.first - a.first)
            }
            return anchors.last().second
        }
    }
}

/** The signal is NOT the displayed score. All four fields survive midnight, rest and restart. */
data class CumulativeScoreState(
    val signal: Double = CumulativeScoreConfig.CURRENT.signalForScore(75.0),
    val useMomentumMinutes: Double = 0.0,
    val awakeRestMinutes: Double = 0.0,
    val recoveryBurden: Double = 0.0
)

enum class CumulativeActivity { NORMAL_USE, MANAGED_USE, AWAKE_REST, SLEEP, UNKNOWN }

/** Pure transition function. Caller owns event deduplication, coverage and durable cursor.
 * Short checks are AWAKE_REST/SLEEP plus [openings], not a use duration.
 * An unknown interval awards nothing and never erases accumulated burden.
 */
object CumulativeScoreEngine {
    fun advance(
        state: CumulativeScoreState,
        activity: CumulativeActivity,
        minutes: Double,
        openings: Int = 0,
        config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT
    ): CumulativeScoreState {
        require(minutes.isFinite() && minutes >= 0.0 && openings >= 0)
        require(listOf(state.signal, state.useMomentumMinutes, state.awakeRestMinutes,
            state.recoveryBurden).all { it.isFinite() })
        var s = state.signal
        var q = state.useMomentumMinutes
        var r = state.awakeRestMinutes
        var b = state.recoveryBurden
        fun speed() = CumulativeScoreConfig.interpolate(s, config.responseAnchors)
        fun lowerEase() = exp(-0.25 * (40.0 - s).coerceAtLeast(0.0))
        repeat(openings) { s = (s - speed() * config.openingLoss * lowerEase()).coerceAtLeast(1.0) }
        var remaining = minutes
        while (remaining > 1e-9) {
            val dt = minOf(1.0, remaining)
            when (activity) {
                CumulativeActivity.NORMAL_USE, CumulativeActivity.MANAGED_USE -> {
                    val managed = activity == CumulativeActivity.MANAGED_USE
                    val stage = if (q < config.accelerationStartMinutes) 0.0 else
                        floor((q - config.accelerationStartMinutes) / config.accelerationStepMinutes) + 1.0
                    val base = if (managed) config.managedLossPerMinute else config.normalLossPerMinute
                    val acceleration = if (managed) config.managedAcceleration else config.normalAcceleration
                    s -= speed() * base * (1.0 + acceleration * stage) * lowerEase() * dt
                    if (managed) b += base * acceleration * stage * dt
                    q += dt
                    r = 0.0
                }
                CumulativeActivity.AWAKE_REST -> {
                    b *= 0.5.pow(dt / (config.burdenHalfLifeAwakeHours * 60.0))
                    q = (q - dt).coerceAtLeast(0.0)
                    val recovery = (restCurve((r + dt) / 60.0) - restCurve(r / 60.0)) *
                        config.recoveryScale / (1.0 + b / config.burdenRecoveryDivisor) *
                        exp(-(s - 70.0).coerceAtLeast(0.0) / 5.0 - 0.7 * (s - 90.0).coerceAtLeast(0.0))
                    s += recovery * speed()
                    r += dt
                }
                CumulativeActivity.SLEEP -> {
                    q = (q - dt).coerceAtLeast(0.0)
                    r = 0.0 // Sleep neither restores score nor silently clears recovery burden.
                }
                CumulativeActivity.UNKNOWN -> { r = 0.0 }
            }
            s = s.coerceIn(1.0, 100.0)
            remaining -= dt
        }
        return CumulativeScoreState(s, q, r, b)
    }

    private fun restCurve(hours: Double): Double =
        if (hours <= 3.0) 0.15 * hours * hours + 1.2 * hours else 4.95 + 2.1 * (hours - 3.0)
}
