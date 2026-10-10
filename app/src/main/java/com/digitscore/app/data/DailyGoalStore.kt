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
    private const val KEY_YESTERDAY_LOWEST_UNLOCK = "y_lowest_unlock"
    private const val KEY_YESTERDAY_AVG_UNLOCK = "y_avg_unlock"

    private const val KEY_BRIEFING_COMPLETED = "goal_briefing_completed"
    private const val KEY_BRIEFING_DATE = "goal_briefing_completed_date"

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

    private val storeLock = Any()

    fun updateProgress(
        context: Context,
        currentScore: Int? = null,
        currentAppMins: Int? = null,
        currentUnlock: Int? = null,
        notifiedMilestone80: Boolean? = null
    ) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        val todayDate = getLogicalDateString()
        if (savedDate != null && savedDate != todayDate) {
            return@synchronized
        }
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
    ) = synchronized(storeLock) {
        val todayDate = getLogicalDateString()
        val currentGoal = getGoal(context, todayDate) ?: DailyGoal(dateString = todayDate)
        val updated = currentGoal.copy(
            dateString = todayDate,
            scoreTarget = scoreTarget,
            targetPackageName = targetPackageName,
            targetAppName = targetAppName,
            appLimitMinutes = appLimitMinutes,
            unlockLimitTarget = unlockLimitTarget,
            isAutoAssigned = false
        )
        saveGoal(context, updated)
        markBriefingCompleted(context, todayDate)
        ScoreRepository.updateDailyGoal(updated)
    }

    fun setDismissed(context: Context, dismissed: Boolean) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_DISMISSED, dismissed).apply()
    }

    fun isBriefingCompleted(context: Context, todayDate: String = getLogicalDateString()): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val completedDate = prefs.getString(KEY_BRIEFING_DATE, null)
        return completedDate == todayDate && prefs.getBoolean(KEY_BRIEFING_COMPLETED, false)
    }

    fun markBriefingCompleted(context: Context, todayDate: String = getLogicalDateString()) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_BRIEFING_DATE, todayDate)
            .putBoolean(KEY_BRIEFING_COMPLETED, true)
            .apply()
    }

    fun setUserAccepted(context: Context) = synchronized(storeLock) {
        val todayDate = getLogicalDateString()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_AUTO_ASSIGNED, false)
            .putString(KEY_BRIEFING_DATE, todayDate)
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
            candidateApps = candidates,
            past14DaysLowestUnlock = prefs.getInt(KEY_YESTERDAY_LOWEST_UNLOCK, 0),
            past14DaysAverageUnlock = prefs.getInt(KEY_YESTERDAY_AVG_UNLOCK, 0)
        )
    }

    fun saveYesterdaySummary(context: Context, summary: YesterdayBriefingSummary) = synchronized(storeLock) {
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
            .putInt(KEY_YESTERDAY_LOWEST_UNLOCK, summary.past14DaysLowestUnlock)
            .putInt(KEY_YESTERDAY_AVG_UNLOCK, summary.past14DaysAverageUnlock)
            .apply()
    }

    private fun serializeCandidates(candidates: List<AppCandidate>): String {
        val array = JSONArray()
        candidates.forEach { c ->
            val obj = JSONObject()
            obj.put("pkg", c.packageName)
            obj.put("name", c.appName)
            obj.put("mins", c.yesterdayUsageMinutes)
            obj.put("heavy", c.isRoutineHeavy)
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
                val pkg = obj.optString("pkg")
                val name = obj.optString("name")
                if (pkg.isNotBlank()) {
                    list.add(
                        AppCandidate(
                            packageName = pkg,
                            appName = if (name.isNotBlank()) name else pkg,
                            yesterdayUsageMinutes = obj.optLong("mins", 0L),
                            isRoutineHeavy = obj.optBoolean("heavy", true)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }


    /**
     * 어제 및 과거 14일 트렌드를 기반으로 오늘의 맞춤형 복합 목표 세트(점수, 만성 과다 앱, 현실적 언락)를 생성 및 반환합니다.
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
        val allAppUsage = db.dailyAppUsageDao().getAll()
        val yesterdayApps = allAppUsage.filter { it.dateString == yesterdayDate }

        val pastHistories = db.scoreDao().getRecentCoreIndexHistories(14)
        val validHistories = pastHistories.filter { it.unlockCount > 0 }
        val pastLowestUnlock = validHistories.minOfOrNull { it.unlockCount } ?: 0
        val pastAvgUnlock = if (validHistories.isNotEmpty()) validHistories.map { it.unlockCount }.average().toInt() else 0

        val yesterdayScore = yesterdayScoreEntity?.finalScore ?: 80
        val yesterdayScreenTimeMins = yesterdayScoreEntity?.totalScreenTimeMinutes
            ?: (yesterdayApps.sumOf { it.usageMillis } / 60_000L)
        val yesterdayUnlock = yesterdayScoreEntity?.unlockCount ?: 0

        // 최근 14일간 앱별 사용 일수 집계 (만성 과다 사용 vs 1회성 일시 급증 구분)
        val appDayCounts = allAppUsage
            .filter { it.usageMillis >= 5 * 60_000L }
            .groupBy { it.packageName }
            .mapValues { entry -> entry.value.map { it.dateString }.distinct().size }

        // 어제 사용량 상위 앱들 추출 (만성 과다 앱 우선 정렬)
        val candidateApps = yesterdayApps
            .filter { it.usageMillis >= 5 * 60_000L }
            .sortedWith(
                compareByDescending<com.digitscore.app.data.entity.DailyAppUsageEntity> {
                    val days = appDayCounts[it.packageName] ?: 1
                    if (days >= 3) 1 else 0
                }.thenByDescending { it.usageMillis }
            )
            .take(6)
            .map { app ->
                val days = appDayCounts[app.packageName] ?: 1
                AppCandidate(
                    packageName = app.packageName,
                    appName = app.appName.ifBlank { app.packageName.substringAfterLast('.') },
                    yesterdayUsageMinutes = app.usageMillis / 60_000L,
                    isRoutineHeavy = days >= 2
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
            candidateApps = candidateApps,
            past14DaysLowestUnlock = pastLowestUnlock,
            past14DaysAverageUnlock = pastAvgUnlock
        )
        saveYesterdaySummary(context, summary)

        // 1. 코어 지수 목표 기본값 (어제 점수가 85 이상이면 75점, 그 외는 70점 방어)
        val recommendedScoreTarget = if (yesterdayScore >= 85) 75 else 70

        // 2. 특정 앱 목표 기본값 (어제 사용량의 70% 수준 시간으로 제안)
        val defaultAppLimit = if (topApp != null && topApp.yesterdayUsageMinutes >= 15) {
            ((topApp.yesterdayUsageMinutes * 0.7f).toInt().coerceAtLeast(15) / 5) * 5
        } else 30

        // 3. 잠금해제 목표 기본값:
        // 어제 언락 또는 과거 평균 기준 현실적 10% 감축값 권장 (과거 최저치 이상으로 가이드)
        val recommendedUnlockTarget = when {
            yesterdayUnlock >= 40 -> {
                val reduced = (yesterdayUnlock * 0.9f).toInt()
                val target = if (pastLowestUnlock in 20..reduced) {
                    reduced.coerceAtLeast(pastLowestUnlock)
                } else reduced
                ((target / 10) * 10).coerceIn(40, 200)
            }
            pastAvgUnlock >= 40 -> ((pastAvgUnlock * 0.9f).toInt() / 10 * 10).coerceIn(40, 200)
            else -> 100
        }

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
