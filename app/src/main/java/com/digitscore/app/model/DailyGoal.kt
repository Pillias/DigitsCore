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
    val yesterdayUsageMinutes: Long,
    val isRoutineHeavy: Boolean = true,
    val recentAvgMinutes: Long = 0L
)

data class CustomGoalTargets(
    val scoreTarget: Int = 70,
    val targetPackageName: String? = null,
    val targetAppName: String = "",
    val appLimitMinutes: Int = 30,
    val unlockLimitTarget: Int = 40
)

/**
 * 데일리 복합 맞춤 목표 세트
 * (코어 지수 방어, 특정 앱 시간 제한, 잠금 해제 횟수 조절을 함께 관리)
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
    val type: DailyGoalType = when {
        targetPackageName != null -> DailyGoalType.APP_USAGE_LIMIT
        unlockLimitTarget in 1..99 -> DailyGoalType.UNLOCK_LIMIT
        else -> DailyGoalType.SCORE_DEFENSE
    },
    val targetValue: Int = when {
        targetPackageName != null -> appLimitMinutes
        unlockLimitTarget in 1..99 -> unlockLimitTarget
        else -> scoreTarget
    },
    val currentValue: Int = when {
        targetPackageName != null -> currentAppUsageMinutes
        unlockLimitTarget in 1..99 -> currentUnlockCount
        else -> currentScore
    },

    val isAutoAssigned: Boolean = true,  // 사용자가 스킵하여 앱이 자동 지정했는지 여부
    val isDismissed: Boolean = false,    // 카드를 숨겼는지 여부
    val notifiedMilestone80: Boolean = false, // 특정 앱 80% 근접 알림 발송 여부
    val notifiedUnlockMilestone80: Boolean = false // 잠금 해제 80% 근접 알림 발송 여부
) {
    // 하위 호환(단일 목표) 값은 같은 종류의 목표일 때만 대체값으로 사용합니다.
    // 다른 목표의 값(예: 앱 사용 분)이 잠금 해제 진행률에 섞이지 않게 합니다.
    private val appLimitValue: Int
        get() = if (appLimitMinutes > 0 || type != DailyGoalType.APP_USAGE_LIMIT) appLimitMinutes else targetValue
    private val appUsageValue: Int
        get() = if (type == DailyGoalType.APP_USAGE_LIMIT && currentAppUsageMinutes == 0) currentValue else currentAppUsageMinutes
    private val unlockLimitValue: Int
        get() = if (unlockLimitTarget > 0 || type != DailyGoalType.UNLOCK_LIMIT) unlockLimitTarget else targetValue
    private val unlockCountValue: Int
        get() = if (type == DailyGoalType.UNLOCK_LIMIT && currentUnlockCount == 0) currentValue else currentUnlockCount

    // 특정 앱 진행 비율
    val appProgressRatio: Float
        get() = if (appLimitValue > 0) (appUsageValue.toFloat() / appLimitValue).coerceIn(0f, 1.5f) else 0f

    // 잠금 해제 진행 비율
    val unlockProgressRatio: Float
        get() = if (unlockLimitValue > 0) (unlockCountValue.toFloat() / unlockLimitValue).coerceIn(0f, 1.5f) else 0f

    // 코어 지수 방어 달성 여부 (그날 마지막으로 기록된 점수 기준)
    val isScoreDefenseAchieved: Boolean
        get() {
            val target = if (type == DailyGoalType.SCORE_DEFENSE) (if (targetValue > 0) targetValue else scoreTarget) else scoreTarget
            val current = if (type == DailyGoalType.SCORE_DEFENSE) (if (currentValue > 0) currentValue else currentScore) else currentScore
            return current >= target
        }

    // 특정 앱 제한 준수 여부 (대상 앱이 없으면 평가하지 않음)
    val isAppLimitAchieved: Boolean
        get() {
            if (targetPackageName == null && type != DailyGoalType.APP_USAGE_LIMIT) return true
            return appUsageValue <= appLimitValue
        }

    // 잠금 해제 제한 준수 여부
    val isUnlockLimitAchieved: Boolean
        get() = unlockCountValue <= unlockLimitValue

    // 하위 호환 진행도 (0.0 .. 1.0)
    val progressRatio: Float
        get() = when (type) {
            DailyGoalType.APP_USAGE_LIMIT -> appProgressRatio.coerceIn(0f, 1f)
            DailyGoalType.UNLOCK_LIMIT -> unlockProgressRatio.coerceIn(0f, 1f)
            DailyGoalType.SCORE_DEFENSE -> {
                val target = if (scoreTarget > 0) scoreTarget else targetValue
                val current = if (currentScore > 0) currentScore else currentValue
                if (target > 0) (current.toFloat() / target).coerceIn(0f, 1f) else 0f
            }
        }

    val isExceeded: Boolean
        get() = when (type) {
            DailyGoalType.APP_USAGE_LIMIT -> !isAppLimitAchieved
            DailyGoalType.UNLOCK_LIMIT -> !isUnlockLimitAchieved
            DailyGoalType.SCORE_DEFENSE -> !isScoreDefenseAchieved
        }

    val isAchieved: Boolean
        get() = when (type) {
            DailyGoalType.APP_USAGE_LIMIT -> isAppLimitAchieved
            DailyGoalType.UNLOCK_LIMIT -> isUnlockLimitAchieved
            DailyGoalType.SCORE_DEFENSE -> isScoreDefenseAchieved
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
    val candidateApps: List<AppCandidate> = emptyList(),
    val past14DaysLowestUnlock: Int = 0,
    val past14DaysAverageUnlock: Int = 0
)

/**
 * 목표 설정 난이도 점수 모드
 */
enum class GoalDifficulty {
    EASY,   // 여유
    NORMAL, // 표준 (시스템 추천 기본값)
    HARD    // 도전
}

/**
 * 어제의 목표 달성 결과 상세 스냅샷
 */
data class YesterdayGoalResult(
    val dateString: String,
    val hasGoal: Boolean = false,
    // 1. 코어 지수 방어 목표
    val scoreTarget: Int = 70,
    val finalScore: Int = 80,
    val isScoreAchieved: Boolean = true,
    // 2. 특정 앱 사용 제한 목표
    val targetPackageName: String? = null,
    val targetAppName: String = "",
    val appLimitMinutes: Int = 30,
    val finalAppUsageMinutes: Int = 0,
    val isAppLimitAchieved: Boolean = true,
    // 3. 잠금 해제 조절 목표
    val unlockLimitTarget: Int = 40,
    val finalUnlockCount: Int = 0,
    val isUnlockLimitAchieved: Boolean = true,
    // 종합 달성 계산
    val achievedCount: Int = 0,
    val totalGoalCount: Int = 3,
    val isStreakMaintained: Boolean = false, // 2개 이상 달성 시 메인 연속 달성 유지
    val isPerfectDay: Boolean = false        // 유효 목표 모두 달성 시
)

/**
 * 연속 달성 및 자유권 보상 관리 상태
 */
data class GoalRewardState(
    val currentStreak: Int = 0,              // 연속 방어 연속 달성 일수 (2개 이상 달성 시 유지)
    val perfectStamps: Int = 0,              // 모두 달성한 날 스탬프 (0..2, 3개 모이면 자유권 1장 지급)
    val availableFreePasses: Int = 0,        // 보유 중인 15분 자유권 (최대 2장)
    val activeFreePassUntil: Long = 0L,      // 현재 활성화된 자유권 만료 시각 (Epoch millis)
    val lastProcessedDate: String? = null    // 보상이 정산된 마지막 활동일 (yyyy-MM-dd)
) {
    val isFreePassActive: Boolean
        get() = activeFreePassUntil > System.currentTimeMillis()

    val remainingFreePassSeconds: Long
        get() = if (isFreePassActive) ((activeFreePassUntil - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L) else 0L
}

