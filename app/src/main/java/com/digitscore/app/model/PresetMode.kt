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
            distractingWeightPerMinute = 1.0f,
            productiveBonusPerMinute = 0.5f,
            idleBonusPer10Minutes = 1.5f,
            unlockPenaltyThreshold = 20,
            unlockPenaltyPerCount = 0.5f
        )
    ),
    EYE_HEALTH(
        id = "eye_health",
        title = "눈 건강 / 휴식 모드",
        description = "총 화면 사용 시간에 민감하며 충분한 화면 휴식(Idle)을 장려하는 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 0.6f,
            productiveBonusPerMinute = 0.2f,
            idleBonusPer10Minutes = 2.0f,
            unlockPenaltyThreshold = 40,
            unlockPenaltyPerCount = 0.2f
        )
    ),
    WORKER(
        id = "worker",
        title = "직장인 / 집중 모드",
        description = "업무 시간 방해 앱 사용을 억제하고 적정 수준의 스마트폰 사용을 유지하는 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 0.5f,
            productiveBonusPerMinute = 0.3f,
            idleBonusPer10Minutes = 1.0f,
            unlockPenaltyThreshold = 35,
            unlockPenaltyPerCount = 0.3f
        )
    ),
    KIDS(
        id = "kids",
        title = "어린이 / 청소년 모드",
        description = "게임 및 영상 시청 시간을 집중 관리하는 부모 안심 모드",
        scoreRule = ScoreRule(
            initialScore = 100f,
            distractingWeightPerMinute = 0.8f,
            productiveBonusPerMinute = 0.4f,
            idleBonusPer10Minutes = 1.2f,
            unlockPenaltyThreshold = 25,
            unlockPenaltyPerCount = 0.4f
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
