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
        title = "공부 / 수험생 모드",
        description = "방해 앱 페널티를 대폭 강화하고 화면 언락에 엄격한 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 1.2f,
            productiveBonusPerMinute = 0.3f,
            idleBonusPer10Minutes = 0.2f,
            maxIdleBonus = 10.0f,
            maxProductiveBonus = 15.0f,
            unlockPenaltyThreshold = 15,
            unlockPenaltyPerCount = 0.8f,
            lateNightMultiplier = 1.8f,
            isLogAccelerationEnabled = true
        )
    ),
    EYE_HEALTH(
        id = "eye_health",
        title = "눈 건강 / 휴식 모드",
        description = "총 화면 사용 시간에 민감하며 충분한 화면 휴식(Idle)을 장려하는 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 0.8f,
            productiveBonusPerMinute = 0.1f,
            idleBonusPer10Minutes = 0.4f,
            maxIdleBonus = 20.0f,
            maxProductiveBonus = 10.0f,
            unlockPenaltyThreshold = 30,
            unlockPenaltyPerCount = 0.4f,
            lateNightMultiplier = 2.0f,
            isLogAccelerationEnabled = true
        )
    ),
    WORKER(
        id = "worker",
        title = "직장인 / 집중 모드",
        description = "업무 시간 방해 앱 사용을 억제하고 적정 수준의 스마트폰 사용을 유지하는 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 0.8f,
            productiveBonusPerMinute = 0.2f,
            idleBonusPer10Minutes = 0.25f,
            maxIdleBonus = 15.0f,
            maxProductiveBonus = 15.0f,
            unlockPenaltyThreshold = 30,
            unlockPenaltyPerCount = 0.5f,
            lateNightMultiplier = 1.6f,
            isLogAccelerationEnabled = true
        )
    ),
    KIDS(
        id = "kids",
        title = "어린이 / 청소년 모드",
        description = "게임 및 영상 시청 시간을 집중 관리하는 부모 안심 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 1.0f,
            productiveBonusPerMinute = 0.25f,
            idleBonusPer10Minutes = 0.2f,
            maxIdleBonus = 12.0f,
            maxProductiveBonus = 12.0f,
            unlockPenaltyThreshold = 20,
            unlockPenaltyPerCount = 0.6f,
            lateNightMultiplier = 2.0f,
            isLogAccelerationEnabled = true
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
