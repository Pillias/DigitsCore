package com.digitscore.app.data

import android.content.Context
import com.digitscore.app.model.AppCandidate
import com.digitscore.app.model.DailyGoal
import com.digitscore.app.model.DailyGoalType
import com.digitscore.app.model.YesterdayBriefingSummary
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/**
 * 데일리 맞춤 목표 및 어제 브리핑 생성/저장 엔진
 * - 하루의 활동 주기(Logical Day)는 05:00 AM 기준으로 전환되어
 *   자정~새벽 4:59 심야 사용 중에 오인식되거나 리셋되지 않습니다.
 */
object DailyGoalStore {

    private const val PREFS_NAME = "daily_goal_state"
    private const val KEY_DATE = "goal_date"

    // 1. 코어 지수 방어 목표
    private const val KEY_SCORE_TARGET = "goal_score_target"
    private const val KEY_CURRENT_SCORE = "goal_current_score"

    // 2. 특정 앱 제한 목표
    private const val KEY_TARGET_PKG = "goal_target_pkg"
    private const val KEY_TARGET_APP = "goal_target_app"
    private const val KEY_APP_LIMIT = "goal_app_limit"
    private const val KEY_CURRENT_APP = "goal_current_app"

    // 3. 잠금 해제 목표
    private const val KEY_UNLOCK_TARGET = "goal_unlock_target"
    private const val KEY_CURRENT_UNLOCK = "goal_current_unlock"

    private const val KEY_AUTO_ASSIGNED = "goal_auto_assigned"
    private const val KEY_DISMISSED = "goal_dismissed"
    private const val KEY_NOTIFIED_80 = "goal_notified_80"

    private const val KEY_YESTERDAY_SCORE = "y_score"
    private const val KEY_YESTERDAY_SCREEN_MINS = "y_screen_mins"
    private const val KEY_YESTERDAY_TOP_PKG = "y_top_pkg"
    private const val KEY_YESTERDAY_TOP_APP = "y_top_app"
    private const val KEY_YESTERDAY_TOP_MINS = "y_top_mins"
    private const val KEY_YESTERDAY_UNLOCK = "y_unlock"
    private const val KEY_YESTERDAY_CANDIDATES = "y_candidates"

    private const val KEY_BRIEFING_COMPLETED = "goal_briefing_completed"

    /**
     * 새벽 5시 이전(00:00~04:59)은 전날 밤의 연장(심야 활동)으로 취급하여
     * 05:00 AM에 비로소 새로운 활동일(Logical Date)이 시작됩니다.
     */
    fun getLogicalDateString(): String {
        val now = LocalDateTime.now()
        val logicalDate = if (now.hour < 5) now.minusDays(1).toLocalDate() else now.toLocalDate()
        return logicalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    fun getYesterdayLogicalDateString(): String {
        val now = LocalDateTime.now()
        val logicalDate = if (now.hour < 5) now.minusDays(1).toLocalDate() else now.toLocalDate()
        return logicalDate.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    fun getGoal(context: Context, todayDate: String = getLogicalDateString()): DailyGoal? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null) ?: return null
        if (savedDate != todayDate) return null

        return DailyGoal(
            dateString = savedDate,
            scoreTarget = prefs.getInt(KEY_SCORE_TARGET, 70),
            currentScore = prefs.getInt(KEY_CURRENT_SCORE, 80),
            targetPackageName = prefs.getString(KEY_TARGET_PKG, null),
            targetAppName = prefs.getString(KEY_TARGET_APP, "") ?: "",
            appLimitMinutes = prefs.getInt(KEY_APP_LIMIT, 30),
            currentAppUsageMinutes = prefs.getInt(KEY_CURRENT_APP, 0),
            unlockLimitTarget = prefs.getInt(KEY_UNLOCK_TARGET, 40),
            currentUnlockCount = prefs.getInt(KEY_CURRENT_UNLOCK, 0),
            isAutoAssigned = prefs.getBoolean(KEY_AUTO_ASSIGNED, true),
            isDismissed = prefs.getBoolean(KEY_DISMISSED, false),
            notifiedMilestone80 = prefs.getBoolean(KEY_NOTIFIED_80, false)
        )
    }

    fun saveGoal(context: Context, goal: DailyGoal) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DATE, goal.dateString)
            .putInt(KEY_SCORE_TARGET, goal.scoreTarget)
            .putInt(KEY_CURRENT_SCORE, goal.currentScore)
            .putString(KEY_TARGET_PKG, goal.targetPackageName)
            .putString(KEY_TARGET_APP, goal.targetAppName)
            .putInt(KEY_APP_LIMIT, goal.appLimitMinutes)
            .putInt(KEY_CURRENT_APP, goal.currentAppUsageMinutes)
            .putInt(KEY_UNLOCK_TARGET, goal.unlockLimitTarget)
            .putInt(KEY_CURRENT_UNLOCK, goal.currentUnlockCount)
            .putBoolean(KEY_AUTO_ASSIGNED, goal.isAutoAssigned)
            .putBoolean(KEY_DISMISSED, goal.isDismissed)
            .putBoolean(KEY_NOTIFIED_80, goal.notifiedMilestone80)
            .apply()
    }

    fun updateProgress(
        context: Context,
        currentScore: Int? = null,
        currentAppMins: Int? = null,
        currentUnlock: Int? = null,
        notifiedMilestone80: Boolean? = null
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        currentScore?.let { editor.putInt(KEY_CURRENT_SCORE, it) }
        currentAppMins?.let { editor.putInt(KEY_CURRENT_APP, it) }
        currentUnlock?.let { editor.putInt(KEY_CURRENT_UNLOCK, it) }
        notifiedMilestone80?.let { editor.putBoolean(KEY_NOTIFIED_80, it) }
        editor.apply()
    }

    fun setCustomGoal(
        context: Context,
        scoreTarget: Int,
        targetPackageName: String?,
        targetAppName: String,
        appLimitMinutes: Int,
        unlockLimitTarget: Int
    ) {
        val currentGoal = getGoal(context) ?: DailyGoal(dateString = getLogicalDateString())
        val updated = currentGoal.copy(
            scoreTarget = scoreTarget,
            targetPackageName = targetPackageName,
            targetAppName = targetAppName,
            appLimitMinutes = appLimitMinutes,
            unlockLimitTarget = unlockLimitTarget,
            isAutoAssigned = false
        )
        saveGoal(context, updated)
        markBriefingCompleted(context)
    }

    fun setDismissed(context: Context, dismissed: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DISMISSED, dismissed).apply()
    }

    fun isBriefingCompleted(context: Context, todayDate: String = getLogicalDateString()): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        return savedDate == todayDate && prefs.getBoolean(KEY_BRIEFING_COMPLETED, false)
    }

    fun markBriefingCompleted(context: Context, todayDate: String = getLogicalDateString()) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_DATE, todayDate)
            .putBoolean(KEY_BRIEFING_COMPLETED, true)
            .apply()
    }

    fun setUserAccepted(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_ASSIGNED, false)
            .putBoolean(KEY_BRIEFING_COMPLETED, true)
            .apply()
    }

    fun getYesterdaySummary(context: Context, todayDate: String = getLogicalDateString()): YesterdayBriefingSummary? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        if (savedDate != todayDate) return null

        val candidatesJson = prefs.getString(KEY_YESTERDAY_CANDIDATES, null)
        val candidates = parseCandidates(candidatesJson)

        return YesterdayBriefingSummary(
            score = prefs.getInt(KEY_YESTERDAY_SCORE, 80),
            totalScreenTimeMinutes = prefs.getLong(KEY_YESTERDAY_SCREEN_MINS, 0L),
            topAppPackageName = prefs.getString(KEY_YESTERDAY_TOP_PKG, null),
            topAppName = prefs.getString(KEY_YESTERDAY_TOP_APP, "") ?: "",
            topAppUsageMinutes = prefs.getLong(KEY_YESTERDAY_TOP_MINS, 0L),
            unlockCount = prefs.getInt(KEY_YESTERDAY_UNLOCK, 0),
            candidateApps = candidates
        )
    }

    fun saveYesterdaySummary(context: Context, summary: YesterdayBriefingSummary) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val candidatesJson = serializeCandidates(summary.candidateApps)

        prefs.edit()
            .putInt(KEY_YESTERDAY_SCORE, summary.score)
            .putLong(KEY_YESTERDAY_SCREEN_MINS, summary.totalScreenTimeMinutes)
            .putString(KEY_YESTERDAY_TOP_PKG, summary.topAppPackageName)
            .putString(KEY_YESTERDAY_TOP_APP, summary.topAppName)
            .putLong(KEY_YESTERDAY_TOP_MINS, summary.topAppUsageMinutes)
            .putInt(KEY_YESTERDAY_UNLOCK, summary.unlockCount)
            .putString(KEY_YESTERDAY_CANDIDATES, candidatesJson)
            .apply()
    }

    private fun serializeCandidates(candidates: List<AppCandidate>): String {
        val array = JSONArray()
        candidates.forEach { c ->
            val obj = JSONObject()
            obj.put("pkg", c.packageName)
            obj.put("name", c.appName)
            obj.put("mins", c.yesterdayUsageMinutes)
            array.put(obj)
        }
        return array.toString()
    }

    private fun parseCandidates(jsonStr: String?): List<AppCandidate> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<AppCandidate>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    AppCandidate(
                        packageName = obj.getString("pkg"),
                        appName = obj.getString("name"),
                        yesterdayUsageMinutes = obj.getLong("mins")
                    )
                )
            }
            list
        }.getOrDefault(emptyList())
    }

    /**
     * 어제 데이터를 기반으로 오늘의 맞춤형 복합 목표 세트(점수, 특정 앱, 언락)를 생성 및 반환합니다.
     */
    suspend fun generateOrGetGoal(
        context: Context,
        db: DigitsDatabase,
        todayDate: String = getLogicalDateString()
    ): Pair<YesterdayBriefingSummary, DailyGoal> {
        val existingGoal = getGoal(context, todayDate)
        val existingSummary = getYesterdaySummary(context, todayDate)
        if (existingGoal != null && existingSummary != null) {
            return existingSummary to existingGoal
        }

        val yesterdayDate = getYesterdayLogicalDateString()
        val yesterdayScoreEntity = db.scoreDao().getScoreHistoryForDate(yesterdayDate)
        val yesterdayApps = db.dailyAppUsageDao().getAll().filter { it.dateString == yesterdayDate }

        val yesterdayScore = yesterdayScoreEntity?.finalScore ?: 80
        val yesterdayScreenTimeMins = yesterdayScoreEntity?.totalScreenTimeMinutes
            ?: (yesterdayApps.sumOf { it.usageMillis } / 60_000L)
        val yesterdayUnlock = yesterdayScoreEntity?.unlockCount ?: 0

        // 어제 사용량 상위 앱들 추출 (5분 이상 사용된 앱, 최대 6개)
        val candidateApps = yesterdayApps
            .filter { it.usageMillis >= 5 * 60_000L }
            .sortedByDescending { it.usageMillis }
            .take(6)
            .map { app ->
                AppCandidate(
                    packageName = app.packageName,
                    appName = app.appName.ifBlank { app.packageName.substringAfterLast('.') },
                    yesterdayUsageMinutes = app.usageMillis / 60_000L
                )
            }

        val topApp = candidateApps.firstOrNull()
        val summary = YesterdayBriefingSummary(
            score = yesterdayScore,
            totalScreenTimeMinutes = yesterdayScreenTimeMins,
            topAppPackageName = topApp?.packageName,
            topAppName = topApp?.appName ?: "",
            topAppUsageMinutes = topApp?.yesterdayUsageMinutes ?: 0L,
            unlockCount = yesterdayUnlock,
            candidateApps = candidateApps
        )
        saveYesterdaySummary(context, summary)

        // 1. 코어 지수 목표 기본값 (어제 점수가 85 이상이면 75점, 그 외는 70점 방어)
        val recommendedScoreTarget = if (yesterdayScore >= 85) 75 else 70

        // 2. 특정 앱 목표 기본값 (어제 1위 앱의 70% 수준 시간으로 제안)
        val defaultAppLimit = if (topApp != null && topApp.yesterdayUsageMinutes >= 15) {
            ((topApp.yesterdayUsageMinutes * 0.7f).toInt().coerceAtLeast(15) / 5) * 5
        } else 30

        // 3. 잠금해제 목표 기본값 (어제 언락이 30 이상이면 어제보다 5회 줄인 값, 기본 40회)
        val recommendedUnlockTarget = if (yesterdayUnlock >= 30) {
            ((yesterdayUnlock - 5).coerceIn(20, 60) / 5) * 5
        } else 40

        val generatedGoal = DailyGoal(
            dateString = todayDate,
            scoreTarget = recommendedScoreTarget,
            currentScore = yesterdayScore,
            targetPackageName = topApp?.packageName,
            targetAppName = topApp?.appName ?: "",
            appLimitMinutes = defaultAppLimit,
            currentAppUsageMinutes = 0,
            unlockLimitTarget = recommendedUnlockTarget,
            currentUnlockCount = 0,
            isAutoAssigned = true,
            isDismissed = false
        )

        saveGoal(context, generatedGoal)
        return summary to generatedGoal
    }
}
