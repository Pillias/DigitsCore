package com.digitscore.app.model

/**
 * 사전 정의된 점수 프리셋 모드
 */
enum class PresetMode(
    val id: String,
    private val koreanTitle: String,
    private val englishTitle: String,
    private val koreanDescription: String,
    private val englishDescription: String,
    val scoreRule: ScoreRule
) {
    STUDY(
        id = "study",
        koreanTitle = "학생 기준 모드",
        englishTitle = "Student Mode",
        koreanDescription = "10대 평균 사용 시 약 60점이 되도록 완만하게 보정한 모드",
        englishDescription = "Gently calibrated around typical teen phone use",
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
        koreanTitle = "눈 건강 / 휴식 모드",
        englishTitle = "Eye Comfort / Break Mode",
        koreanDescription = "총 화면 사용 시간에 민감하며 충분한 화면 휴식(Idle)을 장려하는 모드",
        englishDescription = "More sensitive to total screen time and encourages breaks",
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
        koreanTitle = "직장인 / 집중 모드",
        englishTitle = "Work / Focus Mode",
        koreanDescription = "국내 평균 사용시간 기준 약 60점이 되도록 보정한 모드",
        englishDescription = "Calibrated around typical adult workday phone use",
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
        koreanTitle = "어린이 / 청소년 모드",
        englishTitle = "Kids / Teens Mode",
        koreanDescription = "가정 내 평균 허용시간 기준 약 60점이 되도록 보정한 모드",
        englishDescription = "Calibrated around typical family screen-time limits",
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
        koreanTitle = "기본 밸런스 모드",
        englishTitle = "Balanced Mode",
        koreanDescription = "일상적인 디지털 균형을 위한 표준 모드",
        englishDescription = "A standard mode for balanced everyday digital use",
        scoreRule = ScoreRule()
    );

    val title: String get() = if (java.util.Locale.getDefault().language == "en") englishTitle else koreanTitle
    val description: String get() = if (java.util.Locale.getDefault().language == "en") englishDescription else koreanDescription

    companion object {
        fun fromId(id: String): PresetMode {
            return entries.firstOrNull { it.id == id } ?: BALANCED
        }
    }
}
