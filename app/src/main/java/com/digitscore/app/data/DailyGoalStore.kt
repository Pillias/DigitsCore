package com.digitscore.app.data

import android.content.Context
import com.digitscore.app.model.AppCandidate
import com.digitscore.app.model.DailyGoal
import com.digitscore.app.model.DailyGoalType
import com.digitscore.app.model.GoalDifficulty
import com.digitscore.app.model.GoalRewardState
import com.digitscore.app.model.YesterdayBriefingSummary
import com.digitscore.app.model.YesterdayGoalResult
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
    private const val KEY_NOTIFIED_UNLOCK_80 = "goal_notified_unlock_80"

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

    // 어제 목표 아카이브 키
    private const val KEY_Y_GOAL_DATE = "y_goal_date"
    private const val KEY_Y_GOAL_SCORE_TARGET = "y_goal_score_target"
    private const val KEY_Y_GOAL_TARGET_PKG = "y_goal_target_pkg"
    private const val KEY_Y_GOAL_TARGET_APP = "y_goal_target_app"
    private const val KEY_Y_GOAL_APP_LIMIT = "y_goal_app_limit"
    private const val KEY_Y_GOAL_UNLOCK_TARGET = "y_goal_unlock_target"
    private const val KEY_Y_GOAL_EXISTS = "y_goal_exists"

    // 어제 목표 결과 스냅샷 캐시
    private const val KEY_Y_RESULT_JSON = "y_result_json"

    // 평일/주말 개별 목표 키
    private const val KEY_SEPARATE_WEEKDAY_WEEKEND = "goal_separate_weekday_weekend"

    private const val KEY_WEEKDAY_SCORE_TARGET = "goal_weekday_score_target"
    private const val KEY_WEEKDAY_APP_LIMIT = "goal_weekday_app_limit"
    private const val KEY_WEEKDAY_UNLOCK_TARGET = "goal_weekday_unlock_target"
    private const val KEY_WEEKDAY_TARGET_PKG = "goal_weekday_target_pkg"
    private const val KEY_WEEKDAY_TARGET_APP = "goal_weekday_target_app"

    private const val KEY_WEEKEND_SCORE_TARGET = "goal_weekend_score_target"
    private const val KEY_WEEKEND_APP_LIMIT = "goal_weekend_app_limit"
    private const val KEY_WEEKEND_UNLOCK_TARGET = "goal_weekend_unlock_target"
    private const val KEY_WEEKEND_TARGET_PKG = "goal_weekend_target_pkg"
    private const val KEY_WEEKEND_TARGET_APP = "goal_weekend_target_app"

    // 연속 달성 & 자유권 보상 키
    private const val KEY_REWARD_STREAK = "reward_current_streak"
    private const val KEY_REWARD_PERFECT_STAMPS = "reward_perfect_stamps"
    private const val KEY_REWARD_FREE_PASSES = "reward_free_passes"
    private const val KEY_REWARD_ACTIVE_UNTIL = "reward_active_until"
    private const val KEY_REWARD_WINDOWS = "reward_free_pass_windows"
    private const val KEY_REWARD_LAST_PROCESSED_DATE = "reward_last_processed_date"


    /**
     * 새벽 5시 이전(00:00~04:59)은 전날 밤의 연장(심야 활동)으로 취급하여
     * 05:00 AM에 비로소 새로운 활동일(Logical Date)이 시작됩니다.
     */
    fun getLogicalDateString(): String {
        val now = LocalDateTime.now()
        val logicalDate = if (now.hour < 5) now.minusDays(1).toLocalDate() else now.toLocalDate()
        return logicalDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    }

    /**
     * 시계가 뒤로 바뀐 경우 미래 날짜로 저장된 목표·브리핑·보상 처리 기록과 미래 자유권을 정리합니다.
     * 그대로 두면 실제 그 날짜가 왔을 때 목표 생성·브리핑·보상 정산이 건너뛰어집니다. 매 주기 호출해도 가볍습니다.
     */
    fun discardFutureState(context: Context, nowMillis: Long = System.currentTimeMillis(), todayDate: String = getLogicalDateString()) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fun isFuture(key: String) = prefs.getString(key, null)?.let { it > todayDate } == true
        val editor = prefs.edit()
        var changed = false
        if (isFuture(KEY_DATE)) { editor.remove(KEY_DATE); changed = true }
        if (isFuture(KEY_BRIEFING_DATE)) {
            editor.remove(KEY_BRIEFING_DATE).putBoolean(KEY_BRIEFING_COMPLETED, false); changed = true
        }
        if (isFuture(KEY_Y_GOAL_DATE)) {
            editor.remove(KEY_Y_GOAL_DATE).putBoolean(KEY_Y_GOAL_EXISTS, false); changed = true
        }
        val windows = prefs.getString(KEY_REWARD_WINDOWS, null)
        if (windows != null) {
            val kept = windows.split(",").filter { token ->
                token.split(":").firstOrNull()?.toLongOrNull()?.let { it <= nowMillis } == true
            }.joinToString(",")
            if (kept != windows) { editor.putString(KEY_REWARD_WINDOWS, kept); changed = true }
        }
        if (changed) editor.apply()
        val reward = getRewardState(context)
        val fixedReward = reward.copy(
            lastProcessedDate = reward.lastProcessedDate?.takeIf { it <= todayDate },
            activeFreePassUntil = reward.activeFreePassUntil.takeIf { it <= nowMillis + FREE_PASS_DURATION_MILLIS } ?: 0L
        )
        if (fixedReward != reward) saveRewardState(context, fixedReward)
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
            notifiedMilestone80 = prefs.getBoolean(KEY_NOTIFIED_80, false),
            notifiedUnlockMilestone80 = prefs.getBoolean(KEY_NOTIFIED_UNLOCK_80, false)
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
            .putBoolean(KEY_NOTIFIED_UNLOCK_80, goal.notifiedUnlockMilestone80)
            .apply()
    }

    private val storeLock = Any()

    fun updateProgress(
        context: Context,
        currentScore: Int? = null,
        currentAppMins: Int? = null,
        currentUnlock: Int? = null,
        notifiedMilestone80: Boolean? = null,
        notifiedUnlockMilestone80: Boolean? = null
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
        notifiedUnlockMilestone80?.let { editor.putBoolean(KEY_NOTIFIED_UNLOCK_80, it) }
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
        if (isSeparateWeekdayWeekend(context)) {
            val targets = com.digitscore.app.model.CustomGoalTargets(
                scoreTarget = scoreTarget,
                targetPackageName = targetPackageName,
                targetAppName = targetAppName,
                appLimitMinutes = appLimitMinutes,
                unlockLimitTarget = unlockLimitTarget
            )
            if (isWeekend(todayDate)) {
                saveWeekendGoalTargets(context, targets)
            } else {
                saveWeekdayGoalTargets(context, targets)
            }
        }
        saveGoal(context, updated)
        markBriefingCompleted(context, todayDate)
        ScoreRepository.updateDailyGoal(updated)
    }

    fun isSeparateWeekdayWeekend(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SEPARATE_WEEKDAY_WEEKEND, false)
    }

    fun setSeparateWeekdayWeekend(context: Context, enabled: Boolean) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SEPARATE_WEEKDAY_WEEKEND, enabled).apply()
    }

    fun getWeekdayGoalTargets(context: Context): com.digitscore.app.model.CustomGoalTargets? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_WEEKDAY_SCORE_TARGET)) return null
        return com.digitscore.app.model.CustomGoalTargets(
            scoreTarget = prefs.getInt(KEY_WEEKDAY_SCORE_TARGET, 70),
            targetPackageName = prefs.getString(KEY_WEEKDAY_TARGET_PKG, null),
            targetAppName = prefs.getString(KEY_WEEKDAY_TARGET_APP, "") ?: "",
            appLimitMinutes = prefs.getInt(KEY_WEEKDAY_APP_LIMIT, 30),
            unlockLimitTarget = prefs.getInt(KEY_WEEKDAY_UNLOCK_TARGET, 40)
        )
    }

    fun saveWeekdayGoalTargets(context: Context, targets: com.digitscore.app.model.CustomGoalTargets) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_WEEKDAY_SCORE_TARGET, targets.scoreTarget)
            .putString(KEY_WEEKDAY_TARGET_PKG, targets.targetPackageName)
            .putString(KEY_WEEKDAY_TARGET_APP, targets.targetAppName)
            .putInt(KEY_WEEKDAY_APP_LIMIT, targets.appLimitMinutes)
            .putInt(KEY_WEEKDAY_UNLOCK_TARGET, targets.unlockLimitTarget)
            .apply()
    }

    fun getWeekendGoalTargets(context: Context): com.digitscore.app.model.CustomGoalTargets? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_WEEKEND_SCORE_TARGET)) return null
        return com.digitscore.app.model.CustomGoalTargets(
            scoreTarget = prefs.getInt(KEY_WEEKEND_SCORE_TARGET, 70),
            targetPackageName = prefs.getString(KEY_WEEKEND_TARGET_PKG, null),
            targetAppName = prefs.getString(KEY_WEEKEND_TARGET_APP, "") ?: "",
            appLimitMinutes = prefs.getInt(KEY_WEEKEND_APP_LIMIT, 30),
            unlockLimitTarget = prefs.getInt(KEY_WEEKEND_UNLOCK_TARGET, 40)
        )
    }

    fun saveWeekendGoalTargets(context: Context, targets: com.digitscore.app.model.CustomGoalTargets) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_WEEKEND_SCORE_TARGET, targets.scoreTarget)
            .putString(KEY_WEEKEND_TARGET_PKG, targets.targetPackageName)
            .putString(KEY_WEEKEND_TARGET_APP, targets.targetAppName)
            .putInt(KEY_WEEKEND_APP_LIMIT, targets.appLimitMinutes)
            .putInt(KEY_WEEKEND_UNLOCK_TARGET, targets.unlockLimitTarget)
            .apply()
    }

    fun isWeekend(dateString: String = getLogicalDateString()): Boolean {
        return runCatching {
            val date = java.time.LocalDate.parse(dateString)
            date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
        }.getOrDefault(false)
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


    // -----------------------------------------------------------------------------------------
    // 연속 달성 및 15분 자유권(Free Pass) 보상 엔진
    // -----------------------------------------------------------------------------------------

    fun getRewardState(context: Context): GoalRewardState {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return GoalRewardState(
            currentStreak = prefs.getInt(KEY_REWARD_STREAK, 0),
            perfectStamps = prefs.getInt(KEY_REWARD_PERFECT_STAMPS, 0),
            availableFreePasses = prefs.getInt(KEY_REWARD_FREE_PASSES, 0),
            activeFreePassUntil = prefs.getLong(KEY_REWARD_ACTIVE_UNTIL, 0L),
            lastProcessedDate = prefs.getString(KEY_REWARD_LAST_PROCESSED_DATE, null)
        )
    }

    fun saveRewardState(context: Context, state: GoalRewardState) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_REWARD_STREAK, state.currentStreak)
            .putInt(KEY_REWARD_PERFECT_STAMPS, state.perfectStamps)
            .putInt(KEY_REWARD_FREE_PASSES, state.availableFreePasses)
            .putLong(KEY_REWARD_ACTIVE_UNTIL, state.activeFreePassUntil)
            .putString(KEY_REWARD_LAST_PROCESSED_DATE, state.lastProcessedDate)
            .apply()
        ScoreRepository.updateRewardState(state)
    }

    /**
     * 15분 자유권 사용 활성화
     * (보유 1장 차감, 지금부터 15분간 감점 없음 적용)
     */
    fun activateFreePass(context: Context): Boolean = synchronized(storeLock) {
        val state = getRewardState(context)
        if (state.availableFreePasses <= 0 || state.isFreePassActive) return false
        val now = System.currentTimeMillis()
        val until = now + FREE_PASS_DURATION_MILLIS
        val newState = state.copy(
            availableFreePasses = (state.availableFreePasses - 1).coerceAtLeast(0),
            activeFreePassUntil = until
        )
        saveRewardState(context, newState)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = freePassWindows(context, now) + listOf(now..until)
        val serialized = existing.distinctBy { it.first to it.last }
            .joinToString(",") { "${it.first}:${it.last}" }
        prefs.edit().putString(KEY_REWARD_WINDOWS, serialized).apply()
        return true
    }

    fun isFreePassActive(context: Context): Boolean {
        return getRewardState(context).isFreePassActive
    }

    /** 최근 24시간 내에 사용한 모든 15분 자유권 구간을 반환합니다. 연속 사용 시에도 이전 미정산 구간이 보존됩니다. */
    fun freePassWindows(context: Context, nowMillis: Long = System.currentTimeMillis()): List<LongRange> {
        val cutoff = nowMillis - 24 * 3_600_000L
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val parsed = prefs.getString(KEY_REWARD_WINDOWS, null).orEmpty()
            .split(",")
            .mapNotNull { token ->
                val parts = token.split(":")
                if (parts.size == 2) {
                    val start = parts[0].toLongOrNull()
                    val end = parts[1].toLongOrNull()
                    if (start != null && end != null && end > start && end >= cutoff) start..end else null
                } else null
            }
        val single = getRewardState(context).activeFreePassUntil.takeIf { it > 0L && it >= cutoff }
            ?.let { (it - FREE_PASS_DURATION_MILLIS)..it }
        return (parsed + listOfNotNull(single)).distinctBy { it.first to it.last }.sortedBy { it.first }
    }

    /** 가장 최근에 사용한 15분 자유권 구간입니다. 만료 후에도 점수 정산이 끝날 때까지 필요합니다. */
    fun latestFreePassWindow(context: Context, nowMillis: Long = System.currentTimeMillis()): LongRange? =
        freePassWindows(context, nowMillis).lastOrNull()

    const val FREE_PASS_DURATION_MILLIS = 15 * 60 * 1000L

    /** 어제 사용 기록이 있어 아침 브리핑에 보여줄 기준값이 있는지 확인합니다. */
    fun hasYesterdayBaseline(summary: YesterdayBriefingSummary?): Boolean =
        summary != null && (summary.totalScreenTimeMinutes > 0L || summary.unlockCount > 0)

    // -----------------------------------------------------------------------------------------
    // 어제 목표 아카이브 및 결과 스냅샷 관리
    // -----------------------------------------------------------------------------------------

    fun archiveYesterdayGoal(context: Context, goal: DailyGoal) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_Y_GOAL_DATE, goal.dateString)
            .putInt(KEY_Y_GOAL_SCORE_TARGET, goal.scoreTarget)
            .putString(KEY_Y_GOAL_TARGET_PKG, goal.targetPackageName)
            .putString(KEY_Y_GOAL_TARGET_APP, goal.targetAppName)
            .putInt(KEY_Y_GOAL_APP_LIMIT, goal.appLimitMinutes)
            .putInt(KEY_Y_GOAL_UNLOCK_TARGET, goal.unlockLimitTarget)
            .putBoolean(KEY_Y_GOAL_EXISTS, true)
            .apply()
    }

    fun getYesterdayGoal(context: Context, yesterdayDate: String = getYesterdayLogicalDateString()): DailyGoal? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_Y_GOAL_DATE, null)
        val exists = prefs.getBoolean(KEY_Y_GOAL_EXISTS, false)
        if (!exists || savedDate != yesterdayDate) return null

        return DailyGoal(
            dateString = savedDate,
            scoreTarget = prefs.getInt(KEY_Y_GOAL_SCORE_TARGET, 70),
            targetPackageName = prefs.getString(KEY_Y_GOAL_TARGET_PKG, null),
            targetAppName = prefs.getString(KEY_Y_GOAL_TARGET_APP, "") ?: "",
            appLimitMinutes = prefs.getInt(KEY_Y_GOAL_APP_LIMIT, 30),
            unlockLimitTarget = prefs.getInt(KEY_Y_GOAL_UNLOCK_TARGET, 40)
        )
    }

    fun getYesterdayGoalResult(context: Context): YesterdayGoalResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_Y_RESULT_JSON, null) ?: return null
        return runCatching {
            val obj = JSONObject(jsonStr)
            YesterdayGoalResult(
                dateString = obj.optString("date"),
                hasGoal = obj.optBoolean("has_goal"),
                scoreTarget = obj.optInt("score_target"),
                finalScore = obj.optInt("final_score"),
                isScoreAchieved = obj.optBoolean("score_achieved"),
                targetPackageName = if (obj.has("target_pkg") && !obj.isNull("target_pkg")) obj.optString("target_pkg") else null,
                targetAppName = obj.optString("target_app"),
                appLimitMinutes = obj.optInt("app_limit"),
                finalAppUsageMinutes = obj.optInt("final_app_mins"),
                isAppLimitAchieved = obj.optBoolean("app_achieved"),
                unlockLimitTarget = obj.optInt("unlock_target"),
                finalUnlockCount = obj.optInt("final_unlock"),
                isUnlockLimitAchieved = obj.optBoolean("unlock_achieved"),
                achievedCount = obj.optInt("achieved_count"),
                totalGoalCount = obj.optInt("total_count"),
                isStreakMaintained = obj.optBoolean("streak_maintained"),
                isPerfectDay = obj.optBoolean("perfect_day")
            )
        }.getOrNull()
    }

    fun saveYesterdayGoalResult(context: Context, result: YesterdayGoalResult) = synchronized(storeLock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val obj = JSONObject().apply {
            put("date", result.dateString)
            put("has_goal", result.hasGoal)
            put("score_target", result.scoreTarget)
            put("final_score", result.finalScore)
            put("score_achieved", result.isScoreAchieved)
            if (result.targetPackageName != null) put("target_pkg", result.targetPackageName)
            put("target_app", result.targetAppName)
            put("app_limit", result.appLimitMinutes)
            put("final_app_mins", result.finalAppUsageMinutes)
            put("app_achieved", result.isAppLimitAchieved)
            put("unlock_target", result.unlockLimitTarget)
            put("final_unlock", result.finalUnlockCount)
            put("unlock_achieved", result.isUnlockLimitAchieved)
            put("achieved_count", result.achievedCount)
            put("total_count", result.totalGoalCount)
            put("streak_maintained", result.isStreakMaintained)
            put("perfect_day", result.isPerfectDay)
        }
        prefs.edit().putString(KEY_Y_RESULT_JSON, obj.toString()).apply()
        ScoreRepository.updateYesterdayGoalResult(result)
    }

    /**
     * 난이도에 따른 목표치 동적 변환
     */
    fun applyDifficulty(baseGoal: DailyGoal, difficulty: GoalDifficulty): DailyGoal {
        return when (difficulty) {
            GoalDifficulty.EASY -> baseGoal.copy(
                scoreTarget = (baseGoal.scoreTarget - 5).coerceAtLeast(40),
                appLimitMinutes = baseGoal.appLimitMinutes + 10,
                unlockLimitTarget = baseGoal.unlockLimitTarget + 10
            )
            GoalDifficulty.NORMAL -> baseGoal
            GoalDifficulty.HARD -> baseGoal.copy(
                scoreTarget = (baseGoal.scoreTarget + 5).coerceAtMost(90),
                appLimitMinutes = (baseGoal.appLimitMinutes - 10).coerceAtLeast(15),
                unlockLimitTarget = (baseGoal.unlockLimitTarget - 10).coerceAtLeast(40)
            )
        }
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
            val yResult = getYesterdayGoalResult(context)
            if (yResult != null) {
                ScoreRepository.updateYesterdayGoalResult(yResult)
            }
            ScoreRepository.updateRewardState(getRewardState(context))
            return existingSummary to existingGoal
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedDate = prefs.getString(KEY_DATE, null)
        val yesterdayDate = getYesterdayLogicalDateString()

        // 1. 만약 오늘 날짜로 바뀌기 전, 이전 날짜의 goal이 저장되어 있었다면 어제 목표로 아카이브
        if (savedDate != null && savedDate != todayDate) {
            val previousGoal = DailyGoal(
                dateString = savedDate,
                scoreTarget = prefs.getInt(KEY_SCORE_TARGET, 70),
                currentScore = prefs.getInt(KEY_CURRENT_SCORE, 80),
                targetPackageName = prefs.getString(KEY_TARGET_PKG, null),
                targetAppName = prefs.getString(KEY_TARGET_APP, "") ?: "",
                appLimitMinutes = prefs.getInt(KEY_APP_LIMIT, 30),
                currentAppUsageMinutes = prefs.getInt(KEY_CURRENT_APP, 0),
                unlockLimitTarget = prefs.getInt(KEY_UNLOCK_TARGET, 40),
                currentUnlockCount = prefs.getInt(KEY_CURRENT_UNLOCK, 0)
            )
            archiveYesterdayGoal(context, previousGoal)
        }

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

        // 2. 어제 목표 달성 여부 평가 (YesterdayGoalResult 생성)
        val yGoal = getYesterdayGoal(context, yesterdayDate)
        val yTargetApp = yGoal?.targetPackageName?.let { pkg -> yesterdayApps.firstOrNull { it.packageName == pkg } }
        val yAppMins = ((yTargetApp?.usageMillis ?: 0L) / 60_000L).toInt()

        val yGoalResult = if (yGoal != null) {
            val isScoreAchieved = yesterdayScore >= yGoal.scoreTarget
            val isAppAchieved = if (yGoal.targetPackageName != null) yAppMins <= yGoal.appLimitMinutes else true
            val isUnlockAchieved = yesterdayUnlock <= yGoal.unlockLimitTarget
            val totalCount = if (yGoal.targetPackageName != null) 3 else 2
            val achieved = (if (isScoreAchieved) 1 else 0) +
                (if (yGoal.targetPackageName != null && isAppAchieved) 1 else 0) +
                (if (isUnlockAchieved) 1 else 0)
            val isStreakMaintained = achieved >= 2
            val isPerfectDay = achieved == totalCount

            YesterdayGoalResult(
                dateString = yesterdayDate,
                hasGoal = true,
                scoreTarget = yGoal.scoreTarget,
                finalScore = yesterdayScore,
                isScoreAchieved = isScoreAchieved,
                targetPackageName = yGoal.targetPackageName,
                targetAppName = yGoal.targetAppName,
                appLimitMinutes = yGoal.appLimitMinutes,
                finalAppUsageMinutes = yAppMins,
                isAppLimitAchieved = isAppAchieved,
                unlockLimitTarget = yGoal.unlockLimitTarget,
                finalUnlockCount = yesterdayUnlock,
                isUnlockLimitAchieved = isUnlockAchieved,
                achievedCount = achieved,
                totalGoalCount = totalCount,
                isStreakMaintained = isStreakMaintained,
                isPerfectDay = isPerfectDay
            )
        } else {
            YesterdayGoalResult(
                dateString = yesterdayDate,
                hasGoal = false,
                finalScore = yesterdayScore,
                finalUnlockCount = yesterdayUnlock
            )
        }
        saveYesterdayGoalResult(context, yGoalResult)

        // 3. 연속 달성 및 자유권 보상 정산 (해당 날짜 1회만 처리)
        val currentRewardState = getRewardState(context)
        if (currentRewardState.lastProcessedDate != yesterdayDate) {
            if (yGoalResult.hasGoal) {
                val newStreak = if (yGoalResult.isStreakMaintained) currentRewardState.currentStreak + 1 else 0
                var newStamps = currentRewardState.perfectStamps
                var newPasses = currentRewardState.availableFreePasses
                if (yGoalResult.isPerfectDay) {
                    newStamps += 1
                    if (newStamps >= 3) {
                        newStamps = 0
                        newPasses = minOf(newPasses + 1, 2) // 최대 2장 스택
                    }
                }
                val updatedReward = currentRewardState.copy(
                    currentStreak = newStreak,
                    perfectStamps = newStamps,
                    availableFreePasses = newPasses,
                    lastProcessedDate = yesterdayDate
                )
                saveRewardState(context, updatedReward)
            } else {
                saveRewardState(context, currentRewardState.copy(lastProcessedDate = yesterdayDate))
            }
        } else {
            ScoreRepository.updateRewardState(currentRewardState)
        }

        // 최근 14일간 앱별 통계 종합 (05:00 논리적 날짜 기준)
        val recentDateCutoff = runCatching { java.time.LocalDate.parse(todayDate) }
            .getOrElse { java.time.LocalDate.now() }
            .minusDays(14)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val recent14DayUsage = allAppUsage.filter { it.dateString >= recentDateCutoff }

        val appStatsByPkg = recent14DayUsage
            .groupBy { it.packageName }
            .mapValues { (_, records) ->
                val activeDays = records.count { it.usageMillis >= 3 * 60_000L }.coerceAtLeast(1)
                val totalMillis = records.sumOf { it.usageMillis }
                val avgMins = (totalMillis / (14 * 60_000L)).coerceAtLeast(0L)
                val isHeavyCategory = records.any { it.categoryLevel >= 3 }
                val isExempt = records.lastOrNull()?.categoryLevel?.let { it <= 0 } ?: false
                val latestName = records.lastOrNull { it.appName.isNotBlank() }?.appName ?: ""
                Triple(activeDays, avgMins, isHeavyCategory) to (latestName to isExempt)
            }

        // 어제 사용 앱을 우선으로 하되, 최근 14일간 고사용/관리 앱도 반드시 후보군으로 포괄
        val allTargetPackages = (yesterdayApps.map { it.packageName } + appStatsByPkg.keys).distinct()

        val candidateApps = allTargetPackages.mapNotNull { pkg ->
            val yApp = yesterdayApps.firstOrNull { it.packageName == pkg }
            val yMins = (yApp?.usageMillis ?: 0L) / 60_000L
            val statsPair = appStatsByPkg[pkg]
            val activeDays = statsPair?.first?.first ?: 1
            val recentAvgMins = statsPair?.first?.second ?: 0L
            val isHeavyCategory = statsPair?.first?.third ?: false
            val appName = yApp?.appName?.ifBlank { null }
                ?: statsPair?.second?.first?.ifBlank { null }
                ?: pkg.substringAfterLast('.')
            // 면제 앱(내비·통화·공부·업무 도구)은 '줄일 앱' 후보에서 제외합니다.
            val isExemptApp = yApp?.categoryLevel?.let { it <= 0 } ?: (statsPair?.second?.second ?: false)
            if (isExemptApp) return@mapNotNull null

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

        // 1. 지수 방어선 기본값 (현실적으로 도달 가능한 점진적 목표 점수 모드)
        val baseScore = yesterdayScore.coerceIn(0, 100)
        val recommendedScoreTarget = when {
            baseScore < 45 -> 50
            baseScore < 50 -> 55
            baseScore < 60 -> ((baseScore + 6) / 5) * 5
            baseScore < 70 -> ((baseScore + 5) / 5) * 5
            baseScore < 80 -> 75
            baseScore < 90 -> 80
            else -> 85
        }.coerceIn(40, 90)

        // 2. 특정 앱 목표 기본값: 어제 사용량 또는 최근 일평균의 75% 수준으로 제안
        val baseAppMins = if (topApp != null) {
            if (topApp.yesterdayUsageMinutes >= 15) topApp.yesterdayUsageMinutes
            else topApp.recentAvgMinutes
        } else 0L

        val defaultAppLimit = if (baseAppMins >= 15) {
            ((baseAppMins * 0.75f).toInt().coerceAtLeast(15) / 5) * 5
        } else 30

        // 3. 잠금 해제 목표 기본값:
        val baseUnlock = when {
            yesterdayUnlock >= 20 -> yesterdayUnlock
            pastAvgUnlock >= 20 -> pastAvgUnlock
            else -> 65
        }

        val recommendedUnlockTarget = when {
            baseUnlock > 85 -> ((baseUnlock * 0.88f) / 5).toInt() * 5
            baseUnlock in 55..85 -> (baseUnlock / 5) * 5
            else -> 50
        }.coerceIn(45, 150)

        val customTargets = if (isSeparateWeekdayWeekend(context)) {
            if (isWeekend(todayDate)) getWeekendGoalTargets(context) else getWeekdayGoalTargets(context)
        } else null

        val generatedGoal = if (customTargets != null) {
            DailyGoal(
                dateString = todayDate,
                scoreTarget = customTargets.scoreTarget,
                currentScore = yesterdayScore,
                targetPackageName = customTargets.targetPackageName,
                targetAppName = customTargets.targetAppName,
                appLimitMinutes = customTargets.appLimitMinutes,
                currentAppUsageMinutes = 0,
                unlockLimitTarget = customTargets.unlockLimitTarget,
                currentUnlockCount = 0,
                isAutoAssigned = false,
                isDismissed = false
            )
        } else {
            DailyGoal(
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
        }

        saveGoal(context, generatedGoal)
        return summary to generatedGoal
    }
}

