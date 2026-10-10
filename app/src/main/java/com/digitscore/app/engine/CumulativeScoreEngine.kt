package com.digitscore.app.engine

import kotlin.math.floor
import kotlin.math.pow

/**
 * 75점 타겟 중심 피벗 모델 (CumulativeScoreConfig v2)
 *
 * - 기준 균형점: 75.0점
 * - 하루 최대 변동폭 (M = 10점):
 *   - 상방 가동폭: (100 - S) * 40% (75점일 때 +10점, 30점일 때 +28점)
 *   - 하방 가동폭: S * 13.3% (75점일 때 -10점, 90점일 때 -12점)
 * - 수면 복원력: 75점 미만인 경우 밤새 약 15~20% 복원하여 30점대 고착 탈출 보장
 */
data class CumulativeScoreConfig(
    val version: Int = 2,
    val initialScore: Double = 75.0,
    val targetEquilibriumScore: Double = 75.0,
    val normalLossPerMinute: Double = 0.014,
    val managedLossPerMinute: Double = 0.055,
    val baseRestPerMinute: Double = 0.0075,
    val sleepRestorationRate: Double = 0.00030,
    val openingLoss: Double = 0.05,
    val accelerationStartMinutes: Double = 30.0,
    val accelerationStepMinutes: Double = 15.0,
    val normalAcceleration: Double = 0.15,
    val managedAcceleration: Double = 0.18,
    val maxManagedMultiplier: Double = 3.0,
    val maxNormalMultiplier: Double = 2.0,
    val burdenHalfLifeAwakeHours: Double = 2.0,
    val burdenRecoveryDivisor: Double = 3.0
) {
    init {
        require(version > 0 && initialScore in 1.0..99.0)
        require(listOf(normalLossPerMinute, managedLossPerMinute, openingLoss,
            normalAcceleration, managedAcceleration, baseRestPerMinute, sleepRestorationRate).all { it.isFinite() && it >= 0 })
        require(accelerationStepMinutes > 0 && accelerationStartMinutes >= 0)
        require(burdenHalfLifeAwakeHours > 0 && burdenRecoveryDivisor > 0)
    }

    fun displayed(signal: Double): Double = signal.coerceIn(1.0, 99.0)
    fun signalForScore(score: Double): Double = score.coerceIn(1.0, 99.0)

    companion object {
        val CURRENT = CumulativeScoreConfig()
    }
}

/** The signal is the exact score (1.0..99.0). All four fields survive midnight, rest and restart. */
data class CumulativeScoreState(
    val signal: Double = CumulativeScoreConfig.CURRENT.signalForScore(75.0),
    val useMomentumMinutes: Double = 0.0,
    val awakeRestMinutes: Double = 0.0,
    val recoveryBurden: Double = 0.0
)

enum class CumulativeActivity { NORMAL_USE, MANAGED_USE, AWAKE_REST, SLEEP, UNKNOWN }

/**
 * 순수 상태 전이 함수.
 * 75점 피벗 속도 스케일링을 통해 50~90점대 중앙 반응 및 75점 균형점을 유지합니다.
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

        // 75점 피벗 속도 스케일러 (정규화된 가동 배율)
        // 75점에서 1.0, 30점에서 2.8 (강력 반등), 90점에서 0.4 (과도 상승 방지)
        fun speedUp() = ((100.0 - s) / (100.0 - config.targetEquilibriumScore)).coerceIn(0.1, 4.0)
        // 75점에서 1.0, 30점에서 0.4 (하방 지지 저항), 90점에서 1.2 (하강 중력)
        fun speedDown() = (s / config.targetEquilibriumScore).coerceIn(0.1, 2.0)

        repeat(openings) {
            s = (s - config.openingLoss * speedDown()).coerceAtLeast(1.0)
        }
        var remaining = minutes
        while (remaining > 1e-9) {
            val dt = minOf(1.0, remaining)
            when (activity) {
                CumulativeActivity.NORMAL_USE -> {
                    val stage = if (q < 60.0) 0.0 else
                        floor((q - 60.0) / 30.0) + 1.0
                    val mult = minOf(config.maxNormalMultiplier, 1.0 + config.normalAcceleration * stage)
                    s -= config.normalLossPerMinute * mult * speedDown() * dt
                    q += dt
                    r = 0.0
                }
                CumulativeActivity.MANAGED_USE -> {
                    val stage = if (q < config.accelerationStartMinutes) 0.0 else
                        floor((q - config.accelerationStartMinutes) / config.accelerationStepMinutes) + 1.0
                    val mult = minOf(config.maxManagedMultiplier, 1.0 + config.managedAcceleration * stage)
                    s -= config.managedLossPerMinute * mult * speedDown() * dt
                    b += config.managedLossPerMinute * 0.1 * stage * dt
                    q += dt
                    r = 0.0
                }
                CumulativeActivity.AWAKE_REST -> {
                    b *= 0.5.pow(dt / (config.burdenHalfLifeAwakeHours * 60.0))
                    q = (q - dt).coerceAtLeast(0.0)
                    val effectiveRest = (config.baseRestPerMinute / (1.0 + b / config.burdenRecoveryDivisor)) * speedUp()
                    s += effectiveRest * dt
                    r += dt
                }
                CumulativeActivity.SLEEP -> {
                    q = (q - dt).coerceAtLeast(0.0)
                    b *= 0.5.pow(dt / (4.0 * 60.0))
                    r = 0.0
                    if (s < config.targetEquilibriumScore) {
                        s += (config.targetEquilibriumScore - s) * config.sleepRestorationRate * dt
                    } else {
                        s -= (s - config.targetEquilibriumScore) * 0.00005 * dt
                    }
                }
                CumulativeActivity.UNKNOWN -> {
                    r = 0.0
                }
            }
            s = s.coerceIn(1.0, 99.0)
            remaining -= dt
        }
        return CumulativeScoreState(s, q, r, b)
    }
}
