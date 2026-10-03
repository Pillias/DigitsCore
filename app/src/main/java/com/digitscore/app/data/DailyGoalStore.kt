package com.digitscore.app.data

import android.content.Context
import com.digitscore.app.model.DailyGoal
import com.digitscore.app.model.DailyGoalType
import com.digitscore.app.model.YesterdayBriefingSummary
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 데일리 맞춤 목표 및 어제 브리핑 생성/저장 엔진
 */
object DailyGoalStore {

    private const val PREFS_NAME = "daily_goal_state"
    private const val KEY_DATE = "goal_date"
    private const val KEY_TYPE = "goal_type"
    private const val KEY_TARGET_PKG = "goal_target_pkg"
    private const val KEY_TARGET_APP = "goal_target_app"
    private const val KEY_TARGET_VAL = "goal_target_val"
    private const val KEY_CURRENT_VAL = "goal_current_val"
    private const val KEY_AUTO_ASSIGNED = "goal_auto_assigned"
    private const val KEY_DISMISSED = "goal_dismissed"
    private const val KEY_NOTIFIED_80 = "goal_notified_80"

    private const val KEY_YESTERDAY_SCORE = "y_score"
    private const val KEY_YESTERDAY_SCREEN_MINS = "y_screen_mins"
    private const val KEY_YESTERDAY_TOP_PKG = "y_top_pkg"
    private const val KEY_YESTERDAY_TOP_APP = "y_top_app"
    private const val KEY_YESTERDAY_TOP_MINS = "y_top_mins"
    private const val KEY_YESTERDAY_UNLOCK = "y_unlock"

    fun getGoal(context: Context, todayDate: String = getTodayDateString()): DailyGoal? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null) ?: return null
        if (savedDate != todayDate) return null

        val typeName = prefs.getString(KEY_TYPE, DailyGoalType.APP_USAGE_LIMIT.name)
            ?: DailyGoalType.APP_USAGE_LIMIT.name
        val type = runCatching { DailyGoalType.valueOf(typeName) }.getOrDefault(DailyGoalType.APP_USAGE_LIMIT)

        return DailyGoal(
            dateString = savedDate,
            type = type,
            targetPackageName = prefs.getString(KEY_TARGET_PKG, null),
            targetAppName = prefs.getString(KEY_TARGET_APP, "") ?: "",
            targetValue = prefs.getInt(KEY_TARGET_VAL, 30),
            currentValue = prefs.getInt(KEY_CURRENT_VAL, 0),
            isAutoAssigned = prefs.getBoolean(KEY_AUTO_ASSIGNED, true),
            isDismissed = prefs.getBoolean(KEY_DISMISSED, false),
            notifiedMilestone80 = prefs.getBoolean(KEY_NOTIFIED_80, false)
        )
    }

    fun saveGoal(context: Context, goal: DailyGoal) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DATE, goal.dateString)
            .putString(KEY_TYPE, goal.type.name)
            .putString(KEY_TARGET_PKG, goal.targetPackageName)
            .putString(KEY_TARGET_APP, goal.targetAppName)
            .putInt(KEY_TARGET_VAL, goal.targetValue)
            .putInt(KEY_CURRENT_VAL, goal.currentValue)
            .putBoolean(KEY_AUTO_ASSIGNED, goal.isAutoAssigned)
            .putBoolean(KEY_DISMISSED, goal.isDismissed)
            .putBoolean(KEY_NOTIFIED_80, goal.notifiedMilestone80)
            .apply()
    }

    fun updateProgress(context: Context, currentValue: Int, notifiedMilestone80: Boolean? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit().putInt(KEY_CURRENT_VAL, currentValue)
        if (notifiedMilestone80 != null) {
            editor.putBoolean(KEY_NOTIFIED_80, notifiedMilestone80)
        }
        editor.apply()
    }

    fun setDismissed(context: Context, dismissed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DISMISSED, dismissed).apply()
    }

    fun setUserAccepted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_ASSIGNED, false).apply()
    }

    fun getYesterdaySummary(context: Context, todayDate: String = getTodayDateString()): YesterdayBriefingSummary? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        if (savedDate != todayDate) return null

        return YesterdayBriefingSummary(
            score = prefs.getInt(KEY_YESTERDAY_SCORE, 80),
            totalScreenTimeMinutes = prefs.getLong(KEY_YESTERDAY_SCREEN_MINS, 0L),
            topAppPackageName = prefs.getString(KEY_YESTERDAY_TOP_PKG, null),
            topAppName = prefs.getString(KEY_YESTERDAY_TOP_APP, "") ?: "",
            topAppUsageMinutes = prefs.getLong(KEY_YESTERDAY_TOP_MINS, 0L),
            unlockCount = prefs.getInt(KEY_YESTERDAY_UNLOCK, 0)
        )
    }

    fun saveYesterdaySummary(context: Context, summary: YesterdayBriefingSummary) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_YESTERDAY_SCORE, summary.score)
            .putLong(KEY_YESTERDAY_SCREEN_MINS, summary.totalScreenTimeMinutes)
            .putString(KEY_YESTERDAY_TOP_PKG, summary.topAppPackageName)
            .putString(KEY_YESTERDAY_TOP_APP, summary.topAppName)
            .putLong(KEY_YESTERDAY_TOP_MINS, summary.topAppUsageMinutes)
            .putInt(KEY_YESTERDAY_UNLOCK, summary.unlockCount)
            .apply()
    }

    /**
     * 어제 데이터를 기반으로 오늘의 맞춤형 1가지 목표를 계산하고 저장합니다.
     */
    suspend fun generateOrGetGoal(
        context: Context,
        db: DigitsDatabase,
        todayDate: String = getTodayDateString()
    ): Pair<YesterdayBriefingSummary, DailyGoal> {
        val existingGoal = getGoal(context, todayDate)
        val existingSummary = getYesterdaySummary(context, todayDate)
        if (existingGoal != null && existingSummary != null) {
            return existingSummary to existingGoal
        }

        val yesterdayDate = getYesterdayDateString()
        val yesterdayScoreEntity = db.scoreDao().getScoreHistoryForDate(yesterdayDate)
        val yesterdayApps = db.dailyAppUsageDao().getAll().filter { it.dateString == yesterdayDate }

        val yesterdayScore = yesterdayScoreEntity?.finalScore ?: 80
        val yesterdayScreenTimeMins = yesterdayScoreEntity?.totalScreenTimeMinutes
            ?: (yesterdayApps.sumOf { it.usageMillis } / 60_000L)
        val yesterdayUnlock = yesterdayScoreEntity?.unlockCount ?: 0

        // 어제 사용량 최상위 앱 중 의미 있는 앱 (15분 이상 사용된 앱)
        val topApp = yesterdayApps
            .filter { it.usageMillis >= 15 * 60_000L }
            .maxByOrNull { it.usageMillis }

        val topAppName = topApp?.appName ?: ""
        val topAppPkg = topApp?.packageName
        val topAppMins = (topApp?.usageMillis ?: 0L) / 60_000L

        val summary = YesterdayBriefingSummary(
            score = yesterdayScore,
            totalScreenTimeMinutes = yesterdayScreenTimeMins,
            topAppPackageName = topAppPkg,
            topAppName = topAppName,
            topAppUsageMinutes = topAppMins,
            unlockCount = yesterdayUnlock
        )
        saveYesterdaySummary(context, summary)

        // 오늘의 추천 목표 선정 (어제 1위 과몰입 앱이 있으면 해당 앱 30% 감축, 없으면 70점 방어)
        val generatedGoal = if (topApp != null && topAppMins >= 20) {
            // 어제 사용량의 70% 수준 (예: 100분 -> 70분 이내)
            val targetLimit = ((topAppMins * 0.7f).toInt().coerceAtLeast(15) / 5) * 5
            DailyGoal(
                dateString = todayDate,
                type = DailyGoalType.APP_USAGE_LIMIT,
                targetPackageName = topAppPkg,
                targetAppName = topAppName,
                targetValue = targetLimit,
                currentValue = 0,
                isAutoAssigned = true, // 기본은 앱이 자율 설정(오토파일럿)
                isDismissed = false
            )
        } else {
            // 앱 과몰입이 없으면 코어 지수 목표 방어 (70점 이상 방어)
            DailyGoal(
                dateString = todayDate,
                type = DailyGoalType.SCORE_DEFENSE,
                targetAppName = "코어 지수 방어",
                targetValue = 70,
                currentValue = yesterdayScore,
                isAutoAssigned = true,
                isDismissed = false
            )
        }

        saveGoal(context, generatedGoal)
        return summary to generatedGoal
    }

    private fun getTodayDateString(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    private fun getYesterdayDateString(): String =
        LocalDate.now().minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}
