package com.digitscore.app.model

/**
 * 앱 사용이 디지털 균형에 미치는 정도를 나타내는 5단계 등급입니다.
 *
 * 기존 PRODUCTIVE / NEUTRAL / DISTRACTING 식별자를 유지하여 이미 저장된 Room 데이터와
 * 백업 파일을 그대로 읽을 수 있게 합니다. 사용자에게는 성장(1)에서 몰입 관리(5) 순으로 표시합니다.
 */
enum class AppCategoryType(
    val level: Int,
    val displayName: String,
    val description: String,
    val scoreMultiplier: Float
) {
    PRODUCTIVE(
        level = 1,
        displayName = "성장",
        description = "학습과 성장에 직접 도움이 되는 앱 · +50%",
        scoreMultiplier = 0.5f
    ),
    MILDLY_PRODUCTIVE(
        level = 2,
        displayName = "집중 지원",
        description = "목표 달성과 생산성을 지원하는 앱 · +25%",
        scoreMultiplier = 0.25f
    ),
    NEUTRAL(
        level = 3,
        displayName = "균형",
        description = "점수에 보너스나 감점을 주지 않는 앱 · 0%",
        scoreMultiplier = 0.0f
    ),
    MILDLY_DISTRACTING(
        level = 4,
        displayName = "절제",
        description = "사용 시간을 의식하며 조절할 앱 · -50%",
        scoreMultiplier = -0.5f
    ),
    DISTRACTING(
        level = 5,
        displayName = "몰입 관리",
        description = "사용 시간이 길어지지 않도록 관리할 앱 · -100%",
        scoreMultiplier = -1.0f
    );

    val isPenalty: Boolean get() = scoreMultiplier < 0f
    val isBonus: Boolean get() = scoreMultiplier > 0f

    companion object {
        val orderedEntries: List<AppCategoryType> = entries.sortedBy { it.level }
    }
}
