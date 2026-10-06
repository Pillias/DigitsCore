package com.digitscore.app.model

/**
 * 데일리 맞춤 목표 타입
 */
enum class DailyGoalType {
    APP_USAGE_LIMIT,   // 특정 앱 사용 시간 제한 (분)
    SCORE_DEFENSE,     // 코어 지수 목표 점수 방어 (예: 70점)
    UNLOCK_LIMIT       // 잠금 해제 횟수 제한 (예: 40회)
}

/**
 * 어제 사용 앱 후보 (모닝 브리핑에서 선택용)
 */
data class AppCandidate(
    val packageName: String,
    val appName: String,
    val yesterdayUsageMinutes: Long
)

/**
 * 데일리 복합 맞춤 목표 세트
 * (코어 지수 방어, 특정 앱 시간 제한, 잠금해제 횟수 조절을 함께 관리)
 */
data class DailyGoal(
    val dateString: String,

    // 1. 코어 지수 방어 목표
    val scoreTarget: Int = 70,
    val currentScore: Int = 80,

    // 2. 특정 앱 사용 제한 목표
    val targetPackageName: String? = null,
    val targetAppName: String = "",
    val appLimitMinutes: Int = 30,
    val currentAppUsageMinutes: Int = 0,

    // 3. 잠금 해제 횟수 제한 목표
    val unlockLimitTarget: Int = 40,
    val currentUnlockCount: Int = 0,

    // 하위 호환 필드
    val type: DailyGoalType = if (targetPackageName != null) DailyGoalType.APP_USAGE_LIMIT else DailyGoalType.SCORE_DEFENSE,
    val targetValue: Int = if (targetPackageName != null) appLimitMinutes else scoreTarget,
    val currentValue: Int = if (targetPackageName != null) currentAppUsageMinutes else currentScore,

    val isAutoAssigned: Boolean = true,  // 사용자가 스킵하여 앱이 자동 지정했는지 여부
    val isDismissed: Boolean = false,    // 카드를 숨겼는지 여부
    val notifiedMilestone80: Boolean = false // 80% 근접 알림 발송 여부
) {
    // 특정 앱 진행 비율
    val appProgressRatio: Float
        get() {
            val limit = if (appLimitMinutes > 0) appLimitMinutes else targetValue
            val current = if (currentAppUsageMinutes > 0) currentAppUsageMinutes else currentValue
            return if (limit > 0) (current.toFloat() / limit).coerceIn(0f, 1.5f) else 0f
        }

    // 잠금해제 진행 비율
    val unlockProgressRatio: Float
        get() = if (unlockLimitTarget > 0) (currentUnlockCount.toFloat() / unlockLimitTarget).coerceIn(0f, 1.5f) else 0f

    // 코어 지수 방어 달성 여부
    val isScoreDefenseAchieved: Boolean
        get() = (if (currentScore > 0) currentScore else currentValue) >= (if (scoreTarget > 0) scoreTarget else targetValue)

    // 특정 앱 제한 준수 여부
    val isAppLimitAchieved: Boolean
        get() = (if (currentAppUsageMinutes > 0) currentAppUsageMinutes else currentValue) <= (if (appLimitMinutes > 0) appLimitMinutes else targetValue)

    // 잠금해제 제한 준수 여부
    val isUnlockLimitAchieved: Boolean
        get() = currentUnlockCount <= unlockLimitTarget

    // 하위 호환 진행도 (0.0 .. 1.0)
    val progressRatio: Float
        get() = if (targetValue > 0) (currentValue.toFloat() / targetValue).coerceIn(0f, 1f)
        else if (targetPackageName != null) appProgressRatio.coerceIn(0f, 1f)
        else if (scoreTarget > 0) (currentScore.toFloat() / scoreTarget).coerceIn(0f, 1f)
        else 0f

    val isExceeded: Boolean
        get() = when (type) {
            DailyGoalType.APP_USAGE_LIMIT, DailyGoalType.UNLOCK_LIMIT -> currentValue > targetValue
            DailyGoalType.SCORE_DEFENSE -> false
        }

    val isAchieved: Boolean
        get() = when (type) {
            DailyGoalType.APP_USAGE_LIMIT, DailyGoalType.UNLOCK_LIMIT -> currentValue <= targetValue
            DailyGoalType.SCORE_DEFENSE -> currentValue >= targetValue
        }
}

/**
 * 어제 하루 핵심 요약 (숫자 중심)
 */
data class YesterdayBriefingSummary(
    val score: Int = 80,
    val totalScreenTimeMinutes: Long = 0L,
    val topAppPackageName: String? = null,
    val topAppName: String = "",
    val topAppUsageMinutes: Long = 0L,
    val unlockCount: Int = 0,
    val candidateApps: List<AppCandidate> = emptyList()
)
