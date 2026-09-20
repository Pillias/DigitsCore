package com.digitscore.app.model

/**
 * 앱 사용이 디지털 균형에 미치는 정도를 나타내는 3단계 등급입니다.
 *
 * MILDLY_PRODUCTIVE / MILDLY_DISTRACTING은 과거 5단계 데이터와 백업을 읽기 위한
 * 호환 식별자입니다. UI와 새 기록에는 성장 / 균형 / 몰입 관리 세 등급만 사용합니다.
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
        koreanName = "일반",
        englishName = "General",
        koreanDescription = "학습과 성장에 직접 도움이 되는 앱 · 가장 낮은 사용 부하",
        englishDescription = "Directly supports learning and growth · Lowest usage load",
        scoreMultiplier = 0.5f
    ),
    MILDLY_PRODUCTIVE(
        level = 1,
        koreanName = "일반",
        englishName = "General",
        koreanDescription = "학습과 생산성을 지원하는 앱 · 가장 낮은 사용 부하",
        englishDescription = "Supports learning and productivity · Lowest usage load",
        scoreMultiplier = 0.25f
    ),
    NEUTRAL(
        level = 2,
        koreanName = "일반",
        englishName = "General",
        koreanDescription = "일반적인 사용 앱 · 기본 사용 부하",
        englishDescription = "An app for general use · Standard usage load",
        scoreMultiplier = 0.0f
    ),
    MILDLY_DISTRACTING(
        level = 3,
        koreanName = "관리",
        englishName = "Managed",
        koreanDescription = "사용 흐름이 길어지지 않도록 관리할 앱 · 가장 높은 사용 부하",
        englishDescription = "An app whose usage flow needs managing · Highest usage load",
        scoreMultiplier = -0.5f
    ),
    DISTRACTING(
        level = 3,
        koreanName = "관리",
        englishName = "Managed",
        koreanDescription = "장시간 사용을 특히 관리할 앱 · 가장 높은 사용 부하",
        englishDescription = "An app whose long sessions need managing · Highest usage load",
        scoreMultiplier = -1.0f
    );

    val isPenalty: Boolean get() = scoreMultiplier < 0f
    val isBonus: Boolean get() = scoreMultiplier > 0f
    val displayName: String get() = if (java.util.Locale.getDefault().language == "en") englishName else koreanName
    val description: String get() = if (java.util.Locale.getDefault().language == "en") englishDescription else koreanDescription
    val canonical: AppCategoryType
        get() = when (this) {
            PRODUCTIVE, MILDLY_PRODUCTIVE -> NEUTRAL
            NEUTRAL -> NEUTRAL
            MILDLY_DISTRACTING, DISTRACTING -> DISTRACTING
        }

    companion object {
        val orderedEntries: List<AppCategoryType> = listOf(NEUTRAL, DISTRACTING)

        /** DB v13 이하와 백업 v6 이하에 저장된 5단계 숫자를 새 3단계로 변환합니다. */
        fun fromLegacyLevel(level: Int): Int = when (level) {
            1, 2 -> 1
            3 -> 2
            else -> 3
        }

        fun normalizeLevel(level: Int): Int = when {
            level <= 1 -> 1
            level == 2 -> 2
            else -> 3
        }
    }
}
