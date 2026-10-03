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
 * 데일리 목표 및 진행 상태
 */
data class DailyGoal(
    val dateString: String,
    val type: DailyGoalType = DailyGoalType.APP_USAGE_LIMIT,
    val targetPackageName: String? = null,
    val targetAppName: String = "",
    val targetValue: Int = 30,          // 목표치 (분, 점수, 회수)
    val currentValue: Int = 0,           // 현재 진행치
    val isAutoAssigned: Boolean = true,  // 사용자가 스킵해도 앱이 스스로 지정했는지 여부
    val isDismissed: Boolean = false,    // 카드를 숨겼는지 여부
    val notifiedMilestone80: Boolean = false // 80% 근접 알림 발송 여부
) {
    val progressRatio: Float
        get() = if (targetValue > 0) (currentValue.toFloat() / targetValue).coerceIn(0f, 1f) else 0f

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
    val unlockCount: Int = 0
)
