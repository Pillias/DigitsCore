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

enum class ScoreGrade(
    private val koreanText: String,
    private val englishText: String,
    private val koreanDescription: String,
    private val englishDescription: String
) {
    S("S (최상)", "S (Excellent)", "매우 안정적인 디지털 사용 흐름입니다.", "Your digital-use pattern is very steady."),
    A("A (우수)", "A (Great)", "스마트폰을 균형 있게 사용하고 있습니다.", "You are using your phone in a balanced way."),
    B("B (양호)", "B (Balanced)", "좋은 흐름입니다. 관리 대상 앱을 조금만 줄여보세요.", "A good pattern. Try trimming managed apps a little."),
    C("C (주의)", "C (Watch)", "연속 사용과 잦은 화면 확인을 살펴보세요.", "Watch continuous use and frequent screen checks."),
    D("D (경고)", "D (Pause)", "잠시 스마트폰을 내려놓고 쉬어갈 때입니다.", "This is a good time to put your phone down for a break."),
    F("F (집중 관리)", "F (Refocus)", "사용 흐름을 전환할 충분한 휴식이 필요합니다.", "A meaningful break can help reset your usage pattern.");

    val gradeText: String get() = if (java.util.Locale.getDefault().language == "en") englishText else koreanText
    val description: String get() = if (java.util.Locale.getDefault().language == "en") englishDescription else koreanDescription

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

    fun calculateYesterdayPenalty(yesterdayScore: Int, rule: ScoreRule): Float {
        if (!rule.isYesterdayPenaltyEnabled) return 0f
        val scoreGap = max(0, rule.yesterdayPenaltyTriggerScore - yesterdayScore.coerceIn(0, 100))
        val rawPenalty = scoreGap * rule.yesterdayPenaltyRate.coerceAtLeast(0f)
        return min(rule.maxYesterdayPenalty.coerceAtLeast(0f), rawPenalty)
    }

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
            val mins = app.usageTimeMinutes.coerceAtLeast(0L)
            totalScreenMinutes += mins
            when {
                app.categoryType.isPenalty -> {
                    distractingMinutes += mins
                    val lateNightMins = app.lateNightUsageMinutes.coerceIn(0L, mins)
                    lateNightDistractingMinutes += lateNightMins
                    val ratingMultiplier = -app.categoryType.scoreMultiplier

                    // 1) 로그(Log) 기반 연속 사용 가속도 계수 계산
                    val accelerationThreshold = rule.logAccelerationThresholdMinutes.coerceAtLeast(0f)
                    val accelerationScale = rule.logAccelerationScaleMinutes.coerceAtLeast(1f)
                    val accelerationFactor = if (rule.isLogAccelerationEnabled && mins > accelerationThreshold) {
                        1.0f + kotlin.math.ln(1.0f + (mins - accelerationThreshold) / accelerationScale).toFloat()
                    } else {
                        1.0f
                    }

                    val appPenalty = mins * rule.distractingWeightPerMinute * accelerationFactor * ratingMultiplier
                    totalDistractingPenalty += appPenalty

                    // 2) 심야 시간(24시~05시) 추가 가속 페널티
                    if (lateNightMins > 0L && rule.lateNightMultiplier > 1.0f) {
                        val lateNightExtra = lateNightMins * rule.distractingWeightPerMinute *
                            (rule.lateNightMultiplier - 1.0f) * ratingMultiplier
                        totalLateNightPenalty += lateNightExtra
                    }
                }
                app.categoryType.isBonus -> {
                    productiveMinutes += mins
                    // 보너스 강도는 아래에서 앱별로 합산합니다.
                }
                else -> { /* 균형 등급은 페널티/보너스 없음 */ }
            }
        }

        // 화면 합계는 앱마다 분 단위로 먼저 잘라 더하지 않습니다. 여러 개의 짧은
        // 세션도 합산 후 1분이 되면 일별 집계·알림·위젯 시간에 반영됩니다.
        totalScreenMinutes = appsUsage.sumOf { it.usageTimeMillis.coerceAtLeast(0L) } / 60_000L
        distractingMinutes = appsUsage
            .filter { it.categoryType.isPenalty }
            .sumOf { it.usageTimeMillis.coerceAtLeast(0L) } / 60_000L
        productiveMinutes = appsUsage
            .filter { it.categoryType.isBonus }
            .sumOf { it.usageTimeMillis.coerceAtLeast(0L) } / 60_000L
        lateNightDistractingMinutes = appsUsage
            .filter { it.categoryType.isPenalty }
            .sumOf {
                it.lateNightUsageMillis.coerceIn(0L, it.usageTimeMillis.coerceAtLeast(0L))
            } / 60_000L

        // 방해 앱 총 페널티 (로그 가속 감점 + 심야 추가 감점)
        val overallDistractingPenalty = totalDistractingPenalty + totalLateNightPenalty

        // 2. 생산성 앱 보너스 (최대 상한선 적용)
        val rawProductiveBonus = appsUsage.sumOf { app ->
            if (app.categoryType.isBonus) {
                app.usageTimeMinutes.coerceAtLeast(0L).toDouble() *
                    rule.productiveBonusPerMinute * app.categoryType.scoreMultiplier
            } else {
                0.0
            }
        }.toFloat()
        val productiveBonus = min(rule.maxProductiveBonus, rawProductiveBonus)

        // 3. 화면 미사용(Idle) 회복 보너스 (10분 단위 계산, 최대 상한선 적용)
        val rawIdleBonus = (idleMinutes.coerceAtLeast(0L) / 10f) * rule.idleBonusPer10Minutes
        val idleBonus = min(rule.maxIdleBonus, rawIdleBonus)

        // 4. 언락 초과 페널티
        val excessUnlocks = max(0, unlockCount - rule.unlockPenaltyThreshold)
        val unlockPenalty = excessUnlocks * rule.unlockPenaltyPerCount

        // 5. 이전 사용량 이월
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
