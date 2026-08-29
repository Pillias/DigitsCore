package com.digitscore.app.engine

import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.ScoreRule
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 점수 계산 결과 상세 내역
 */
data class ScoreDetail(
    val finalScore: Int,
    val distractingPenalty: Float,
    val productiveBonus: Float,
    val idleBonus: Float,
    val unlockPenalty: Float,
    val lateNightPenalty: Float = 0f,
    val yesterdayPenalty: Float = 0f,
    val grade: ScoreGrade,
    val totalScreenTimeMinutes: Long,
    val distractingTimeMinutes: Long,
    val productiveTimeMinutes: Long,
    val lateNightDistractingMinutes: Long = 0L
)

enum class ScoreGrade(val gradeText: String, val description: String) {
    S("S (최상)", "완벽한 디지털 디톡스를 실천 중입니다!"),
    A("A (우수)", "스마트폰을 매우 균형 있게 사용하고 있습니다."),
    B("B (양호)", "좋은 흐름입니다. 조금만 더 방해 앱을 줄여보세요."),
    C("C (주의)", "방해 앱 사용과 잦은 화면 언락에 주의하세요."),
    D("D (경고)", "디지털 디톡스가 시급합니다! 잠시 스마트폰을 내려놓으세요."),
    F("F (위험)", "과도한 스마트폰 중독 상태입니다. 즉시 휴식이 필요합니다.");

    companion object {
        fun fromScore(score: Int): ScoreGrade = when {
            score >= 90 -> S
            score >= 80 -> A
            score >= 70 -> B
            score >= 55 -> C
            score >= 40 -> D
            else -> F
        }
    }
}

/**
 * 순수 비즈니스 로직으로 구성된 스코어링 엔진
 */
object ScoreCalculator {

    /**
     * 앱 사용 목록, 화면 꺼짐(Idle) 시간, 언락 횟수를 기반으로 점수를 계산합니다.
     */
    fun calculateScore(
        appsUsage: List<AppUsage>,
        idleMinutes: Long,
        unlockCount: Int,
        rule: ScoreRule = ScoreRule()
    ): ScoreDetail {
        var distractingMinutes = 0L
        var productiveMinutes = 0L
        var totalScreenMinutes = 0L
        var lateNightDistractingMinutes = 0L
        var totalDistractingPenalty = 0f
        var totalLateNightPenalty = 0f

        for (app in appsUsage) {
            val mins = app.usageTimeMinutes
            totalScreenMinutes += mins
            when (app.categoryType) {
                AppCategoryType.DISTRACTING -> {
                    distractingMinutes += mins
                    val lateNightMins = app.lateNightUsageMinutes
                    lateNightDistractingMinutes += lateNightMins

                    // 1) 로그(Log) 기반 연속 사용 가속도 계수 계산
                    val accelerationFactor = if (rule.isLogAccelerationEnabled && mins > rule.logAccelerationThresholdMinutes) {
                        1.0f + kotlin.math.ln(1.0f + (mins - rule.logAccelerationThresholdMinutes) / rule.logAccelerationScaleMinutes).toFloat()
                    } else {
                        1.0f
                    }

                    val appPenalty = mins * rule.distractingWeightPerMinute * accelerationFactor
                    totalDistractingPenalty += appPenalty

                    // 2) 심야 시간(24시~05시) 추가 가속 페널티
                    if (lateNightMins > 0L && rule.lateNightMultiplier > 1.0f) {
                        val lateNightExtra = lateNightMins * rule.distractingWeightPerMinute * (rule.lateNightMultiplier - 1.0f)
                        totalLateNightPenalty += lateNightExtra
                    }
                }
                AppCategoryType.PRODUCTIVE -> {
                    productiveMinutes += mins
                }
                AppCategoryType.NEUTRAL -> { /* 중립 앱은 페널티/보너스 없음 */ }
            }
        }

        // 방해 앱 총 페널티 (로그 가속 감점 + 심야 추가 감점)
        val overallDistractingPenalty = totalDistractingPenalty + totalLateNightPenalty

        // 2. 생산성 앱 보너스 (최대 상한선 적용)
        val rawProductiveBonus = productiveMinutes * rule.productiveBonusPerMinute
        val productiveBonus = min(rule.maxProductiveBonus, rawProductiveBonus)

        // 3. 화면 미사용(Idle) 회복 보너스 (10분 단위 계산, 최대 상한선 적용)
        val rawIdleBonus = (idleMinutes / 10f) * rule.idleBonusPer10Minutes
        val idleBonus = min(rule.maxIdleBonus, rawIdleBonus)

        // 4. 언락 초과 페널티
        val excessUnlocks = max(0, unlockCount - rule.unlockPenaltyThreshold)
        val unlockPenalty = excessUnlocks * rule.unlockPenaltyPerCount

        // 5. 전날 과사용 페널티 (디톡스 부채)
        val appliedYesterdayPenalty = if (rule.isYesterdayPenaltyEnabled) rule.yesterdayPenalty else 0f

        // 6. 총합 계산 및 경계값(0~100) 클램핑
        val rawScore = rule.initialScore - appliedYesterdayPenalty - overallDistractingPenalty + productiveBonus + idleBonus - unlockPenalty
        val clampedScore = min(rule.maxScoreBoundary, max(rule.minScoreBoundary, rawScore))
        val finalScore = clampedScore.roundToInt()

        return ScoreDetail(
            finalScore = finalScore,
            distractingPenalty = overallDistractingPenalty,
            productiveBonus = productiveBonus,
            idleBonus = idleBonus,
            unlockPenalty = unlockPenalty,
            lateNightPenalty = totalLateNightPenalty,
            yesterdayPenalty = appliedYesterdayPenalty,
            grade = ScoreGrade.fromScore(finalScore),
            totalScreenTimeMinutes = totalScreenMinutes,
            distractingTimeMinutes = distractingMinutes,
            productiveTimeMinutes = productiveMinutes,
            lateNightDistractingMinutes = lateNightDistractingMinutes
        )
    }
}
