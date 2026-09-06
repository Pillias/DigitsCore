package com.digitscore.app.model

/**
 * 앱 사용이 디지털 균형에 미치는 정도를 나타내는 5단계 등급입니다.
 *
 * 기존 PRODUCTIVE / NEUTRAL / DISTRACTING 식별자를 유지하여 이미 저장된 Room 데이터와
 * 백업 파일을 그대로 읽을 수 있게 합니다. 사용자에게는 성장(1)에서 몰입 관리(5) 순으로 표시합니다.
 */
enum class AppCategoryType(
    val level: Int,
    private val koreanName: String,
    private val englishName: String,
    private val koreanDescription: String,
    private val englishDescription: String,
    val scoreMultiplier: Float
) {
    PRODUCTIVE(
        level = 1,
        koreanName = "성장",
        englishName = "Growth",
        koreanDescription = "학습과 성장에 직접 도움이 되는 앱 · +50%",
        englishDescription = "Directly supports learning and growth · +50%",
        scoreMultiplier = 0.5f
    ),
    MILDLY_PRODUCTIVE(
        level = 2,
        koreanName = "집중 지원",
        englishName = "Focus Support",
        koreanDescription = "목표 달성과 생산성을 지원하는 앱 · +25%",
        englishDescription = "Supports goals and productivity · +25%",
        scoreMultiplier = 0.25f
    ),
    NEUTRAL(
        level = 3,
        koreanName = "균형",
        englishName = "Balanced",
        koreanDescription = "점수에 보너스나 감점을 주지 않는 앱 · 0%",
        englishDescription = "No score bonus or deduction · 0%",
        scoreMultiplier = 0.0f
    ),
    MILDLY_DISTRACTING(
        level = 4,
        koreanName = "절제",
        englishName = "Mindful Use",
        koreanDescription = "사용 시간을 의식하며 조절할 앱 · -50%",
        englishDescription = "An app to use with time awareness · -50%",
        scoreMultiplier = -0.5f
    ),
    DISTRACTING(
        level = 5,
        koreanName = "몰입 관리",
        englishName = "Deep-use Control",
        koreanDescription = "사용 시간이 길어지지 않도록 관리할 앱 · -100%",
        englishDescription = "An app whose long sessions need managing · -100%",
        scoreMultiplier = -1.0f
    );

    val isPenalty: Boolean get() = scoreMultiplier < 0f
    val isBonus: Boolean get() = scoreMultiplier > 0f
    val displayName: String get() = if (java.util.Locale.getDefault().language == "en") englishName else koreanName
    val description: String get() = if (java.util.Locale.getDefault().language == "en") englishDescription else koreanDescription

    companion object {
        val orderedEntries: List<AppCategoryType> = entries.sortedBy { it.level }
    }
}
