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
    val version: Int = 3,
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
    val studyExemptLimitMinutes: Double = 50.0,
    // v3: 잠금 해제 간격 (DEBATE 안건 18). 직전에 화면을 꺼 둔 시간이 기준보다 짧으면 감점, 길면 미세 가점.
    val unlockReferenceMinutes: Double = 12.0,
    val unlockMaxLoss: Double = 0.05,
    val unlockMaxGain: Double = 0.02,
    val unlockDailyLossCap: Double = 3.0,
    val unlockDailyGainCap: Double = 1.0
) {
    init {
        require(version > 0 && initialScore in 1.0..99.0)
        require(listOf(normalLossPerMinute, managedLossPerMinute, openingLoss,
            normalAcceleration, managedAcceleration, baseRestPerMinute, sleepRestorationRate,
            momentumRestDecayRate, awakeRestCapMinutes, utilityExemptLimitMinutes, studyExemptLimitMinutes).all { it.isFinite() && it >= 0 })
        require(accelerationStepMinutes > 0 && accelerationStartMinutes >= 0)
        require(burdenHalfLifeAwakeHours > 0 && burdenRecoveryDivisor > 0)
        require(momentumRestDecayRate > 0 && awakeRestCapMinutes > 0)
        require(unlockReferenceMinutes > 0 && listOf(unlockMaxLoss, unlockMaxGain, unlockDailyLossCap, unlockDailyGainCap)
            .all { it.isFinite() && it >= 0 })
    }

    /** v3부터 잠금 해제 간격을 점수에 반영합니다. */
    val unlockPacingEnabled: Boolean get() = version >= 3

    fun displayed(signal: Double): Double = signal.coerceIn(1.0, 99.0)
    fun signalForScore(score: Double): Double = score.coerceIn(1.0, 99.0)

    companion object {
        val CURRENT = CumulativeScoreConfig()
    }
}

/**
 * 저장소(DB·통계·백업)에 기록하는 점수 모델 번호.
 * - 1~4: 이전 일일/롤링 모델, 5: 누적 모델 v2(75점 피벗), 6: v3(잠금 해제 간격 반영, 현재)
 * - 계수 미세 조정은 번호를 올리지 않습니다. 기준점·감점/회복 구조처럼 점수의 의미가 바뀌는
 *   변경일 때만 [CumulativeScoreConfig.version]을 올리고, 저장 번호는 그에 맞춰 1씩 증가합니다.
 *   (차트는 번호가 다른 기록을 회색으로 구분하므로, 잦은 변경은 피합니다.)
 */
object ScoreModel {
    /** 누적 모델 v2의 저장 번호. v1 누적 기록도 같은 번호를 썼으므로 v2를 기준으로 셉니다. */
    private const val V2_STORED_VERSION = 5
    fun storedVersion(configVersion: Int): Int = V2_STORED_VERSION + (configVersion - 2).coerceAtLeast(0)
    /** 현재 계산 설정의 저장 번호. 컴파일 상수가 필요한 곳은 [CURRENT_STORED]를 사용합니다. */
    const val CURRENT_STORED = 6
    val current: Int get() = storedVersion(CumulativeScoreConfig.CURRENT.version)
}

/** The signal is the exact score (1.0..99.0). All fields survive midnight, rest and restart. */
data class CumulativeScoreState(
    val signal: Double = CumulativeScoreConfig.CURRENT.signalForScore(75.0),
    val useMomentumMinutes: Double = 0.0,
    val awakeRestMinutes: Double = 0.0,
    val recoveryBurden: Double = 0.0,
    /** 도구(유틸리티) 면제 앱의 연속 사용 분. 공부 앱과 별도로 셉니다. */
    val exemptMomentumMinutes: Double = 0.0,
    /** 공부·독서 면제 앱의 연속 사용 분. */
    val studyMomentumMinutes: Double = 0.0
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
    // 75점 피벗 속도 스케일러 (정규화된 가동 배율)
    // 75점에서 1.0, 30점에서 2.8 (강력 반등), 90점에서 0.4 (과도 상승 방지)
    fun speedUp(signal: Double, config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT): Double =
        ((100.0 - signal) / (100.0 - config.targetEquilibriumScore)).coerceIn(0.1, 4.0)
    // 75점에서 1.0, 30점에서 0.4 (하방 지지 저항), 90점에서 1.2 (하강 중력)
    fun speedDown(signal: Double, config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT): Double =
        (signal / config.targetEquilibriumScore).coerceIn(0.1, 2.0)

    fun advance(
        state: CumulativeScoreState,
        activity: CumulativeActivity,
        minutes: Double,
        openings: Int = 0,
        config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT
    ): CumulativeScoreState {
        require(minutes.isFinite() && minutes >= 0.0 && openings >= 0)
        require(listOf(state.signal, state.useMomentumMinutes, state.awakeRestMinutes,
            state.recoveryBurden, state.exemptMomentumMinutes, state.studyMomentumMinutes).all { it.isFinite() })
        var s = state.signal
        var q = state.useMomentumMinutes
        var r = state.awakeRestMinutes
        var b = state.recoveryBurden
        var em = state.exemptMomentumMinutes
        var sm = state.studyMomentumMinutes

        fun speedUp() = CumulativeScoreEngine.speedUp(s, config)
        fun speedDown() = CumulativeScoreEngine.speedDown(s, config)

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
                    // 공부·독서 면제: 50분 미만 감점 0, 50분 이상부터 일반 감점 (도구 앱과 별도 카운터)
                    if (sm >= config.studyExemptLimitMinutes) {
                        s -= config.normalLossPerMinute * speedDown() * dt
                    }
                    sm += dt
                    r = 0.0
                }
                CumulativeActivity.AWAKE_REST -> {
                    b *= 0.5.pow(dt / (config.burdenHalfLifeAwakeHours * 60.0))
                    q = (q - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    em = (em - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    sm = (sm - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    val restRate = if (r < config.awakeRestCapMinutes) config.baseRestPerMinute else config.sleepRestorationRate
                    val effectiveRest = (restRate / (1.0 + b / config.burdenRecoveryDivisor)) * speedUp()
                    s += effectiveRest * dt
                    r += dt
                }
                CumulativeActivity.SLEEP -> {
                    b *= 0.5.pow(dt / (config.burdenHalfLifeAwakeHours * 60.0))
                    q = (q - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    em = (em - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
                    sm = (sm - config.momentumRestDecayRate * dt).coerceAtLeast(0.0)
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
        return CumulativeScoreState(s, q, r, b, em, sm)
    }
}
