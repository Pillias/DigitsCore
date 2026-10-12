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
            obj.put("recent_avg", c.recentAvgMinutes)
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
                            isRoutineHeavy = obj.optBoolean("heavy", true),
                            recentAvgMinutes = obj.optLong("recent_avg", 0L)
                        )
                    )
                }
            }
            list
        }.getOrDefault(emptyList())
    }


    /**
     * 어제 및 최근 14일 사용 트렌드를 종합 분석하여
     * 어제 특이사항이 없더라도 최근 과다 사용 앱, 도달 가능한 현실적 코어 지수 목표,
     * 적정 유지선 기반의 일일 잠금 해제 목표를 생성합니다.
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
        // 15회 미만의 극단적 이상치(설치 당일, 미사용일 등)를 제외한 정상 사용일 기준 통계
        val normalHistories = validHistories.filter { it.unlockCount >= 15 }
        val pastLowestUnlock = normalHistories.minOfOrNull { it.unlockCount }
            ?: validHistories.minOfOrNull { it.unlockCount } ?: 0
        val pastAvgUnlock = if (normalHistories.isNotEmpty()) {
            normalHistories.map { it.unlockCount }.average().toInt()
        } else if (validHistories.isNotEmpty()) {
            validHistories.map { it.unlockCount }.average().toInt()
        } else 0

        val yesterdayScore = yesterdayScoreEntity?.finalScore ?: 80
        val yesterdayScreenTimeMins = yesterdayScoreEntity?.totalScreenTimeMinutes
            ?: (yesterdayApps.sumOf { it.usageMillis } / 60_000L)
        val yesterdayUnlock = yesterdayScoreEntity?.unlockCount ?: 0

        // 최근 14일간 앱별 통계 종합 (사용 일수, 총 사용량, 일평균, 카테고리 등)
        val recentDateCutoff = java.time.LocalDate.now().minusDays(14)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val recent14DayUsage = allAppUsage.filter { it.dateString >= recentDateCutoff }

        val appStatsByPkg = recent14DayUsage
            .groupBy { it.packageName }
            .mapValues { (_, records) ->
                val activeDays = records.count { it.usageMillis >= 3 * 60_000L }.coerceAtLeast(1)
                val totalMillis = records.sumOf { it.usageMillis }
                val avgMins = (totalMillis / (14 * 60_000L)).coerceAtLeast(0L)
                val isHeavyCategory = records.any { it.categoryLevel >= 2 }
                val latestName = records.lastOrNull { it.appName.isNotBlank() }?.appName ?: ""
                Triple(activeDays, avgMins, isHeavyCategory) to latestName
            }

        // 어제 사용 앱을 우선으로 하되, 최근 14일간 고사용/몰입관리 앱도 반드시 후보군으로 포괄
        val allTargetPackages = (yesterdayApps.map { it.packageName } + appStatsByPkg.keys).distinct()

        val candidateApps = allTargetPackages.mapNotNull { pkg ->
            val yApp = yesterdayApps.firstOrNull { it.packageName == pkg }
            val yMins = (yApp?.usageMillis ?: 0L) / 60_000L
            val statsPair = appStatsByPkg[pkg]
            val activeDays = statsPair?.first?.first ?: 1
            val recentAvgMins = statsPair?.first?.second ?: 0L
            val isHeavyCategory = statsPair?.first?.third ?: false
            val appName = yApp?.appName?.ifBlank { null }
                ?: statsPair?.second?.ifBlank { null }
                ?: pkg.substringAfterLast('.')

            // 어제 5분 이상 썼거나, 최근 14일 일평균 5분 이상인 의미 있는 앱만 후보 편입
            if (yMins < 5 && recentAvgMins < 5) return@mapNotNull null

            AppCandidate(
                packageName = pkg,
                appName = appName,
                yesterdayUsageMinutes = yMins,
                isRoutineHeavy = activeDays >= 3 || isHeavyCategory,
                recentAvgMinutes = recentAvgMins
            )
        }.sortedWith(
            // 1순위: 어제 10분 이상 쓴 앱 (어제 실제 부하)
            // 2순위: 최근 일평균이 높은 앱 (평소 자주 쓰는 특이사항 앱)
            // 3순위: 몰입 관리 대상 앱
            compareByDescending<AppCandidate> { it.yesterdayUsageMinutes >= 10 }
                .thenByDescending { it.isRoutineHeavy }
                .thenByDescending { if (it.yesterdayUsageMinutes > 0) it.yesterdayUsageMinutes else it.recentAvgMinutes }
        ).take(6)

        val topApp = candidateApps.firstOrNull()
        val summaryTopAppName = topApp?.appName ?: ""
        val summaryTopAppMins = if (topApp != null) {
            if (topApp.yesterdayUsageMinutes > 0) topApp.yesterdayUsageMinutes else topApp.recentAvgMinutes
        } else 0L

        val summary = YesterdayBriefingSummary(
            score = yesterdayScore,
            totalScreenTimeMinutes = yesterdayScreenTimeMins,
            topAppPackageName = topApp?.packageName,
            topAppName = summaryTopAppName,
            topAppUsageMinutes = summaryTopAppMins,
            unlockCount = yesterdayUnlock,
            candidateApps = candidateApps,
            past14DaysLowestUnlock = pastLowestUnlock,
            past14DaysAverageUnlock = pastAvgUnlock
        )
        saveYesterdaySummary(context, summary)

        // 1. 코어 지수 목표 기본값 (현실적으로 도달 가능한 점진적 목표 프리셋)
        // 코어 지수는 최근 24시간 롤링 지수이므로 어제 40~50점대인 사용자에게 70점 고정은 불가능함.
        // 어제 점수를 기준으로 오늘 하루 절제 시 달성 가능한 현실적인 목표(+5~8점, 5점 단위)를 제시합니다.
        val baseScore = yesterdayScore.coerceIn(0, 100)
        val recommendedScoreTarget = when {
            baseScore < 45 -> 50
            baseScore < 50 -> 55 // 예: 어제 47점 -> 오늘 55점 목표
            baseScore < 60 -> ((baseScore + 6) / 5) * 5 // 예: 52점 -> 55점, 56점 -> 60점
            baseScore < 70 -> ((baseScore + 5) / 5) * 5 // 예: 62점 -> 65점, 67점 -> 70점
            baseScore < 80 -> 75 // 70점대 -> 75점 방어
            baseScore < 90 -> 80 // 80점대 -> 80점 유지
            else -> 85           // 90점대 -> 85점 유지
        }.coerceIn(40, 90)

        // 2. 특정 앱 목표 기본값: 어제 사용량 또는 최근 일평균의 75% 수준으로 제안
        val baseAppMins = if (topApp != null) {
            if (topApp.yesterdayUsageMinutes >= 15) topApp.yesterdayUsageMinutes
            else topApp.recentAvgMinutes
        } else 0L

        val defaultAppLimit = if (baseAppMins >= 15) {
            ((baseAppMins * 0.75f).toInt().coerceAtLeast(15) / 5) * 5
        } else 30

        // 3. 잠금해제 목표 기본값:
        // 매일 계속 0.9배로 감축되어 비현실적인 1회로 수렴하지 않도록,
        // 정상적인 일상 사용을 보장하는 적정 밴드(50~70회 유지선)를 기준점으로 프리셋합니다.
        val baseUnlock = when {
            yesterdayUnlock >= 20 -> yesterdayUnlock
            pastAvgUnlock >= 20 -> pastAvgUnlock
            else -> 65
        }

        val recommendedUnlockTarget = when {
            baseUnlock > 85 -> ((baseUnlock * 0.88f) / 5).toInt() * 5 // 과다 사용 시 점진적 10~15% 절제
            baseUnlock in 55..85 -> (baseUnlock / 5) * 5              // 적정 범위(55~85회)는 안정적 유지선
            else -> 50                                                // 이미 55회 미만인 경우 50회 하한 유지선
        }.coerceIn(45, 150)

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
