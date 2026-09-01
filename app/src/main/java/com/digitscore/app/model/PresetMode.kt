package com.digitscore.app.model

/**
 * 사전 정의된 디톡스 프리셋 모드
 */
enum class PresetMode(
    val id: String,
    val title: String,
    val description: String,
    val scoreRule: ScoreRule
) {
    STUDY(
        id = "study",
        title = "학생 기준 모드",
        description = "10대 평균 사용 시 약 60점이 되도록 완만하게 보정한 모드",
        scoreRule = ScoreRule(
            distractingWeightPerMinute = 0.44f,
            productiveBonusPerMinute = 0.25f,
            idleBonusPer10Minutes = 0.2f,
            maxIdleBonus = 12.0f,
            maxProductiveBonus = 12.0f,
            unlockPenaltyThreshold = 25,
            unlockPenaltyPerCount = 0.3f,
            lateNightMultiplier = 1.6f,
            logAccelerationThresholdMinutes = 60f,
            logAccelerationScaleMinutes = 120f,
            yesterdayPenaltyRate = 0.15f,
            maxYesterdayPenalty = 8f
        )
    ),
    EYE_HEALTH(
        id = "eye_health",
        title = "눈 건강 / 휴식 모드",
        description = "총 화면 사용 시간에 민감하며 충분한 화면 휴식(Idle)을 장려하는 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 0.5f,
            productiveBonusPerMinute = 0.1f,
            idleBonusPer10Minutes = 0.4f,
            maxIdleBonus = 20.0f,
            maxProductiveBonus = 10.0f,
            unlockPenaltyThreshold = 30,
            unlockPenaltyPerCount = 0.25f,
            lateNightMultiplier = 1.8f,
            logAccelerationThresholdMinutes = 45f,
            logAccelerationScaleMinutes = 150f,
            maxYesterdayPenalty = 8f
        )
    ),
    WORKER(
        id = "worker",
        title = "직장인 / 집중 모드",
        description = "국내 평균 사용시간 기준 약 60점이 되도록 보정한 모드",
        scoreRule = ScoreRule(
            distractingWeightPerMinute = 0.62f,
            productiveBonusPerMinute = 0.2f,
            idleBonusPer10Minutes = 0.25f,
            maxIdleBonus = 15.0f,
            maxProductiveBonus = 15.0f,
            unlockPenaltyThreshold = 30,
            unlockPenaltyPerCount = 0.3f,
            lateNightMultiplier = 1.5f,
            logAccelerationThresholdMinutes = 60f,
            logAccelerationScaleMinutes = 120f
        )
    ),
    KIDS(
        id = "kids",
        title = "어린이 / 청소년 모드",
        description = "가정 내 평균 허용시간 기준 약 60점이 되도록 보정한 모드",
        scoreRule = ScoreRule(
            distractingWeightPerMinute = 0.55f,
            productiveBonusPerMinute = 0.25f,
            idleBonusPer10Minutes = 0.2f,
            maxIdleBonus = 12.0f,
            maxProductiveBonus = 10.0f,
            unlockPenaltyThreshold = 20,
            unlockPenaltyPerCount = 0.3f,
            lateNightMultiplier = 1.8f,
            logAccelerationThresholdMinutes = 45f,
            logAccelerationScaleMinutes = 120f,
            yesterdayPenaltyTriggerScore = 65,
            maxYesterdayPenalty = 8f
        )
    ),
    BALANCED(
        id = "balanced",
        title = "기본 밸런스 모드",
        description = "일상적인 디지털 디톡스와 균형 잡힌 사용을 위한 표준 모드",
        scoreRule = ScoreRule()
    );

    companion object {
        fun fromId(id: String): PresetMode {
            return entries.firstOrNull { it.id == id } ?: BALANCED
        }
    }
}
