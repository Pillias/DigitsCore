package com.digitscore.app.model

/**
 * 앱 사용이 디지털 균형에 미치는 정도를 나타내는 등급입니다.
 *
 * EXEMPT: 지도, 내비, 통화, 도구 등 필수 앱 (감점 및 연속사용 시간 제외)
 * NEUTRAL: 일반적인 일상/업무/독서 앱 (기본 사용 부하)
 * DISTRACTING: 숏폼, SNS, 게임, 웹툰 등 (30분 초과 시 가속 감점 관리)
 */
enum class AppCategoryType(
    val level: Int,
    private val koreanName: String,
    private val englishName: String,
    private val koreanDescription: String,
    private val englishDescription: String,
    val scoreMultiplier: Float
) {
    EXEMPT(
        level = 0,
        koreanName = "면제",
        englishName = "Exempt",
        koreanDescription = "지도, 내비, 통화, 도구 등 필수 앱 · 감점 및 연속사용 시간 제외",
        englishDescription = "Maps, navigation, phone, tools · No penalty, excluded from usage streak",
        scoreMultiplier = 0.0f
    ),
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

    val isExempt: Boolean get() = this == EXEMPT
    val isPenalty: Boolean get() = scoreMultiplier < 0f
    val isBonus: Boolean get() = scoreMultiplier > 0f
    val displayName: String get() = if (java.util.Locale.getDefault().language == "en") englishName else koreanName
    val description: String get() = if (canonical == NEUTRAL) {
        if (java.util.Locale.getDefault().language == "en") "General use · Standard usage load"
        else "일반 사용 · 기본 사용 부하"
    } else if (canonical == EXEMPT) {
        if (java.util.Locale.getDefault().language == "en") "Exempt tool · No score deduction or streak"
        else "면제 도구 · 점수 감점 및 연속사용 시간 제외"
    } else if (java.util.Locale.getDefault().language == "en") englishDescription else koreanDescription
    val canonical: AppCategoryType
        get() = when (this) {
            EXEMPT -> EXEMPT
            PRODUCTIVE, MILDLY_PRODUCTIVE -> NEUTRAL
            NEUTRAL -> NEUTRAL
            MILDLY_DISTRACTING, DISTRACTING -> DISTRACTING
        }

    companion object {
        val orderedEntries: List<AppCategoryType> = listOf(EXEMPT, NEUTRAL, DISTRACTING)

        /** DB v13 이하와 백업 v6 이하에 저장된 5단계 숫자를 새 등급으로 변환합니다. */
        fun fromLegacyLevel(level: Int): Int = when (level) {
            0 -> 0
            1, 2 -> 1
            3 -> 2
            else -> 3
        }

        fun normalizeLevel(level: Int): Int = when {
            level <= 0 -> 0
            level == 1 -> 1
            level == 2 -> 2
            else -> 3
        }
    }
}
