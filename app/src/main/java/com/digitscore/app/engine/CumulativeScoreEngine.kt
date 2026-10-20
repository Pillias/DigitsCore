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
 * - 단일 휴식 모델: 화면 꺼짐/미사용 시 speedUp() 스케일러에 따른 자연 회복 단일화
 */
data class CumulativeScoreConfig(
    val version: Int = 2,
    val initialScore: Double = 75.0,
    val targetEquilibriumScore: Double = 75.0,
    val normalLossPerMinute: Double = 0.012,
    val managedLossPerMinute: Double = 0.042,
    val baseRestPerMinute: Double = 0.0095,
    val sleepRestorationRate: Double = 0.0045,
    val openingLoss: Double = 0.0,
    val accelerationStartMinutes: Double = 30.0,
    val accelerationStepMinutes: Double = 15.0,
    val normalAcceleration: Double = 0.15,
    val managedAcceleration: Double = 0.18,
    val maxManagedMultiplier: Double = 2.2,
    val maxNormalMultiplier: Double = 2.0,
    val burdenHalfLifeAwakeHours: Double = 2.0,
    val burdenRecoveryDivisor: Double = 3.0,
    val momentumRestDecayRate: Double = 2.0,
    val awakeRestCapMinutes: Double = 180.0,
    val utilityExemptLimitMinutes: Double = 30.0,
    val studyExemptLimitMinutes: Double = 50.0
) {
    init {
        require(version > 0 && initialScore in 1.0..99.0)
        require(listOf(normalLossPerMinute, managedLossPerMinute, openingLoss,
            normalAcceleration, managedAcceleration, baseRestPerMinute, sleepRestorationRate,
            momentumRestDecayRate, awakeRestCapMinutes, utilityExemptLimitMinutes, studyExemptLimitMinutes).all { it.isFinite() && it >= 0 })
        require(accelerationStepMinutes > 0 && accelerationStartMinutes >= 0)
        require(burdenHalfLifeAwakeHours > 0 && burdenRecoveryDivisor > 0)
        require(momentumRestDecayRate > 0 && awakeRestCapMinutes > 0)
    }

    fun displayed(signal: Double): Double = signal.coerceIn(1.0, 99.0)
    fun signalForScore(score: Double): Double = score.coerceIn(1.0, 99.0)

    companion object {
        val CURRENT = CumulativeScoreConfig()
    }
}

/** The signal is the exact score (1.0..99.0). All fields survive midnight, rest and restart. */
data class CumulativeScoreState(
    val signal: Double = CumulativeScoreConfig.CURRENT.signalForScore(75.0),
    val useMomentumMinutes: Double = 0.0,
    val awakeRestMinutes: Double = 0.0,
    val recoveryBurden: Double = 0.0,
    val exemptMomentumMinutes: Double = 0.0
)

enum class CumulativeActivity {
    NORMAL_USE,
    MANAGED_USE,
    AWAKE_REST,
    SLEEP,
    EXEMPT_UNLIMITED,
    EXEMPT_UTILITY,
    EXEMPT_STUDY,
    UNKNOWN
}

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
            state.recoveryBurden, state.exemptMomentumMinutes).all { it.isFinite() })
        var s = state.signal
        var q = state.useMomentumMinutes
        var r = state.awakeRestMinutes
        var b = state.recoveryBurden
        var em = state.exemptMomentumMinutes

        // 75점 피벗 속도 스케일러 (정규화된 가동 배율)
        // 75점에서 1.0, 30점에서 2.8 (강력 반등), 90점에서 0.4 (과도 상승 방지)
        fun speedUp() = ((100.0 - s) / (100.0 - config.targetEquilibriumScore)).coerceIn(0.1, 4.0)
        // 75점에서 1.0, 30점에서 0.4 (하방 지지 저항), 90점에서 1.2 (하강 중력)
        fun speedDown() = (s / config.targetEquilibriumScore).coerceIn(0.1, 2.0)

        if (config.openingLoss > 0.0) {
            repeat(openings) {
                s = (s - config.openingLoss * speedDown()).coerceAtLeast(1.0)
            }
        }
        var remaining = minutes
        while (remaining > 1e-9) {
            val dt = minOf(1.0, remaining)
            when (activity) {
                CumulativeActivity.NORMAL_USE -> {
                    s -= config.normalLossPerMinute * speedDown() * dt
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
                CumulativeActivity.EXEMPT_UNLIMITED -> {
                    // 시간 제한 없는 순수 면제(내비/통화): 감점 0, 회복 0, r = 0.0
                    r = 0.0
                }
                CumulativeActivity.EXEMPT_UTILITY -> {
                    // 필수 면제: 30분 미만 감점 0, 30분 이상부터 일반 감점
                    if (em >= config.utilityExemptLimitMinutes) {
                        s -= config.normalLossPerMinute * speedDown() * dt
                    }
                    em += dt
                    r = 0.0
                }
                CumulativeActivity.EXEMPT_STUDY -> {
                    // 공부·독서 면제: 50분 미만 감점 0, 50분 이상부터 일반 감점
                    if (em >= config.studyExemptLimitMinutes) {
                        s -= config.normalLossPerMinute * speedDown() * dt
                    }
                    em += dt
                    r = 0.0
                }
                CumulativeActivity.AWAKE_REST -> {
                    b *= 0.5.pow(dt / (config.burdenHalfLifeAwakeHours * 60.0))
                    q = (q - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    em = (em - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    val restRate = if (r < config.awakeRestCapMinutes) config.baseRestPerMinute else config.sleepRestorationRate
                    val effectiveRest = (restRate / (1.0 + b / config.burdenRecoveryDivisor)) * speedUp()
                    s += effectiveRest * dt
                    r += dt
                }
                CumulativeActivity.SLEEP -> {
                    b *= 0.5.pow(dt / (config.burdenHalfLifeAwakeHours * 60.0))
                    q = (q - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    em = (em - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    val effectiveRest = (config.sleepRestorationRate / (1.0 + b / config.burdenRecoveryDivisor)) * speedUp()
                    s += effectiveRest * dt
                    r += dt
                }
                CumulativeActivity.UNKNOWN -> {
                    r = 0.0
                }
            }
            s = s.coerceIn(1.0, 99.0)
            remaining -= dt
        }
        return CumulativeScoreState(s, q, r, b, em)
    }
}
