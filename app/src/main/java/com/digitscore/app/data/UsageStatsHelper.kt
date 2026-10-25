package com.digitscore.app.data

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DeviceInteractionEventEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class TodayUsageSnapshot(
    val appsUsage: List<AppUsage>,
    val unlockCount: Int,
    val assignedUsageMillis: Long,
    val hasForegroundEvidence: Boolean,
    val sessionSummariesByPackage: Map<String, AppSessionSummary> = emptyMap(),
    val foregroundSegments: List<ForegroundUsageSegment> = emptyList(),
    val observableUnlockedMillis: Long = 0L,
    val endingState: ForegroundTrackerState = ForegroundTrackerState(),
    val interactionEvents: List<DeviceInteractionEventEntity> = emptyList(),
    val notificationEvents: List<AppNotificationEvent> = emptyList(),
    val queriedEventCount: Int = 0,
    val queryDurationMillis: Long = 0L,
    val queryWindowMillis: Long = 0L,
    val incremental: Boolean = false
)

data class AppSessionSummary(
    val sessionCount: Int,
    val longestSessionMillis: Long,
    val shortSessionCount: Int = 0
)

data class DailyAppUsage(
    val dayStartMillis: Long,
    val usageMillis: Long,
    val sessionCount: Int,
    val shortSessionCount: Int,
    val isToday: Boolean
)

data class AppUsageInsights(
    val hourlyUsageMillis: List<Long>,
    val sessionDurationsMillis: List<Long>,
    val dailyUsage: List<DailyAppUsage>,
    val windowStartMillis: Long,
    val windowEndMillis: Long
)

data class UnlockInsights(
    val unlockCount: Int,
    val hourlyUnlockCounts: List<Int>,
    val averageIntervalMinutes: Int?,
    val notificationCount: Int,
    val notificationEventsSupported: Boolean,
    val windowStartMillis: Long,
    val windowEndMillis: Long,
    val glanceUnlockCount: Int = 0,
    val glanceThresholdSeconds: Int = 7,
    val past14DaysLowestUnlock: Int = 0,
    val past14DaysAverageUnlock: Int = 0
)

/** 한 번의 실제 앱 진입에 속한 여러 전면 조각을 세션 ID로 묶어 사용 시간을 합칩니다. */
internal fun sessionDurationsByPackage(
    segments: List<ForegroundUsageSegment>
): Map<String, List<Long>> = segments
    .filter { it.durationMillis > 0L }
    .groupBy { it.packageName }
    .mapValues { (_, packageSegments) ->
        packageSegments
            .groupBy { it.sessionStartTimeMillis }
            .values
            .map { sessionSegments -> sessionSegments.sumOf { it.durationMillis } }
            .sortedDescending()
    }

internal fun sessionSummariesByPackage(
    segments: List<ForegroundUsageSegment>
): Map<String, AppSessionSummary> = sessionDurationsByPackage(segments).mapValues { (_, durations) ->
    AppSessionSummary(
        sessionCount = durations.size,
        longestSessionMillis = durations.maxOrNull() ?: 0L,
        shortSessionCount = durations.count { it < HABITUAL_CHECK_THRESHOLD_MILLIS }
    )
}

object UsageStatsHelper {

    private const val FOREGROUND_STATE_LOOKBACK_MILLIS = 6 * 60 * 60 * 1_000L
    // UsageEvents.Event.NOTIFICATION_INTERRUPTION은 event type 12이지만 일부 공개 SDK에서
    // 심볼이 노출되지 않으므로 플랫폼에 정의된 안정된 정수 값을 사용합니다.
    private const val EVENT_TYPE_NOTIFICATION_INTERRUPTION = 12
    private const val UNLOCK_PAIR_WINDOW_MILLIS = 15_000L
    private const val DUPLICATE_EVENT_WINDOW_MILLIS = 2_000L

    private data class QueriedUsageEvents(
        val timelineEvents: List<ForegroundTimelineEvent>,
        val interactionEvents: List<DeviceInteractionEventEntity>,
        val notificationEvents: List<AppNotificationEvent>,
        val rawEventCount: Int,
        val queryDurationMillis: Long
    )

    /**
     * PACKAGE_USAGE_STATS 권한이 허용되었는지 확인
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * 오늘 활동일 시작 타임스탬프 반환.
     * 활동일은 오전 05:00에 전환되므로, 새벽 00:00~04:59는 전날 05:00부터 시작된 활동일로 취급합니다.
     */
    fun getStartOfLogicalDayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            val hour = get(Calendar.HOUR_OF_DAY)
            if (hour < 5) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            set(Calendar.HOUR_OF_DAY, 5)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    fun getStartOfTodayMillis(nowMillis: Long = System.currentTimeMillis()): Long = getStartOfLogicalDayMillis(nowMillis)


    internal val HOME_LAUNCHER_PACKAGES = setOf(
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.huawei.android.launcher"
    )

    internal val TRANSIENT_SYSTEM_OVERLAY_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.sec.android.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.samsung.android.app.aodservice",
        "com.samsung.android.incallui",
        "com.samsung.android.game.gametools",
        "com.samsung.android.game.gos",
        "com.samsung.android.game.gamehome",
        "com.samsung.android.app.cocktailbarservice",
        "com.google.android.permissioncontroller",
        "com.android.permissioncontroller",
        "com.google.android.gms"
    )

    val SELF_PACKAGES = setOf("com.digitscore.app", com.digitscore.app.BuildConfig.APPLICATION_ID)

    private val BASE_IGNORED_SYSTEM_PACKAGES = HOME_LAUNCHER_PACKAGES +
        TRANSIENT_SYSTEM_OVERLAY_PACKAGES +
        setOf("com.android.phone") + SELF_PACKAGES

    /** 홈 화면 앱으로 등록돼 있어도 실제 앱 화면이므로 제외하면 안 되는 패키지 (예: 설정의 FallbackHome). */
    private val HOME_DETECTION_EXCLUDED = setOf("com.android.settings") + SELF_PACKAGES

    /**
     * 사용 시간에서 제외하는 시스템 화면 목록. 기본 목록에 기기에 설치된 홈 앱(런처)을 더합니다.
     * Nova·OnePlus·Oppo 등 목록에 없는 런처의 홈 화면 시간이 앱 사용으로 집계되지 않게 합니다.
     */
    @Volatile
    internal var IGNORED_SYSTEM_PACKAGES: Set<String> = BASE_IGNORED_SYSTEM_PACKAGES
        private set

    /** 설치된 홈 앱을 다시 확인합니다. 서비스 시작 시 1회 호출하면 충분합니다. */
    fun refreshHomePackages(context: Context) {
        val detected = runCatching {
            val homeIntent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_HOME)
            val pm = context.packageManager
            val defaultHome = pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName
            val allHomes = pm.queryIntentActivities(homeIntent, 0).mapNotNull { it.activityInfo?.packageName }
            (allHomes + listOfNotNull(defaultHome)).filterNot { it in HOME_DETECTION_EXCLUDED }.toSet()
        }.getOrDefault(emptySet())
        IGNORED_SYSTEM_PACKAGES = BASE_IGNORED_SYSTEM_PACKAGES + detected
    }

    internal fun defaultCategoryForApplicationCategory(applicationCategory: Int): AppCategoryType =
        when (applicationCategory) {
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_VIDEO -> AppCategoryType.DISTRACTING

            ApplicationInfo.CATEGORY_SOCIAL -> AppCategoryType.DISTRACTING

            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategoryType.NEUTRAL
            else -> AppCategoryType.NEUTRAL
        }

    private val EDUCATION_PACKAGE_PREFIXES = listOf(
        "com.duolingo",
        "com.ichi2.anki",
        "com.ankiandroid",
        "org.khanacademy",
        "com.coursera",
        "com.udemy",
        "com.quizlet",
        "com.google.android.apps.classroom",
        "org.edx"
    )

    private val SHOPPING_PACKAGE_PREFIXES = listOf(
        "com.alibaba.aliexpress",
        "com.amazon.mshop",
        "com.shopee",
        "com.ebay.mobile",
        "com.einnovation.temu",
        "com.contextlogic.wish",
        "com.coupang.mobile"
    )

    internal fun defaultCategoryForPackage(
        packageName: String,
        applicationCategory: Int
    ): AppCategoryType = com.digitscore.app.model.AppCategoryPolicy.resolveDefaultCategory(
        packageName = packageName,
        applicationCategory = applicationCategory
    )

    private fun resolveCategory(
        pm: PackageManager,
        packageName: String,
        savedEntity: AppWeightEntity?
    ): AppCategoryType {
        // 사용자가 직접 지정한 분류를 최우선으로 존중합니다.
        savedEntity?.takeIf { it.isUserModified }?.let { return it.categoryType.canonical }
        val appInfo = runCatching { pm.getApplicationInfo(packageName, 0) }.getOrNull()
        val isLegacyGame = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        val applicationCategory = when {
            appInfo == null -> ApplicationInfo.CATEGORY_UNDEFINED
            appInfo.category != ApplicationInfo.CATEGORY_UNDEFINED -> appInfo.category
            isLegacyGame -> ApplicationInfo.CATEGORY_GAME
            else -> ApplicationInfo.CATEGORY_UNDEFINED
        }
        val recommended = com.digitscore.app.model.AppCategoryPolicy.resolveDefaultCategory(
            packageName = packageName,
            applicationCategory = applicationCategory
        )
        return recommended
    }

    internal fun shouldIncludeUsagePackage(packageName: String, usageTimeMillis: Long): Boolean =
        usageTimeMillis > 0L && packageName !in IGNORED_SYSTEM_PACKAGES

    private fun queryUsageEvents(
        usageStatsManager: UsageStatsManager,
        queryStart: Long,
        endTime: Long
    ): QueriedUsageEvents {
        val queryStartedAt = SystemClock.elapsedRealtime()
        val usageEvents = usageStatsManager.queryEvents(queryStart, endTime)
            ?: return QueriedUsageEvents(
                emptyList(),
                emptyList(),
                emptyList(),
                0,
                SystemClock.elapsedRealtime() - queryStartedAt
            )
        val androidEvent = UsageEvents.Event()
        val timelineEvents = mutableListOf<ForegroundTimelineEvent>()
        val interactionEvents = mutableListOf<DeviceInteractionEventEntity>()
        val notificationEvents = mutableListOf<AppNotificationEvent>()
        var rawEventCount = 0

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(androidEvent)
            rawEventCount++
            if (androidEvent.eventType == EVENT_TYPE_NOTIFICATION_INTERRUPTION) {
                interactionEvents += DeviceInteractionEventEntity(
                    androidEvent.timeStamp,
                    DeviceInteractionEventEntity.NOTIFICATION_INTERRUPTION
                )
                val pkg = androidEvent.packageName
                if (!pkg.isNullOrEmpty() && pkg !in IGNORED_SYSTEM_PACKAGES) {
                    notificationEvents += AppNotificationEvent(pkg, androidEvent.timeStamp)
                }
                continue
            }
            val type = when (androidEvent.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundTimelineEventType.APP_RESUMED
                UsageEvents.Event.USER_INTERACTION -> ForegroundTimelineEventType.APP_INTERACTION
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundTimelineEventType.APP_PAUSED
                UsageEvents.Event.ACTIVITY_STOPPED -> ForegroundTimelineEventType.APP_STOPPED
                UsageEvents.Event.SCREEN_INTERACTIVE -> ForegroundTimelineEventType.SCREEN_INTERACTIVE
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE
                UsageEvents.Event.KEYGUARD_SHOWN -> ForegroundTimelineEventType.KEYGUARD_SHOWN
                UsageEvents.Event.KEYGUARD_HIDDEN -> ForegroundTimelineEventType.KEYGUARD_HIDDEN
                UsageEvents.Event.DEVICE_STARTUP -> ForegroundTimelineEventType.DEVICE_STARTUP
                UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundTimelineEventType.DEVICE_SHUTDOWN
                else -> null
            } ?: continue

            if ((type == ForegroundTimelineEventType.APP_RESUMED ||
                 type == ForegroundTimelineEventType.APP_INTERACTION ||
                 type == ForegroundTimelineEventType.APP_PAUSED ||
                 type == ForegroundTimelineEventType.APP_STOPPED) &&
                androidEvent.packageName in TRANSIENT_SYSTEM_OVERLAY_PACKAGES
            ) {
                continue
            }

            when (type) {
                ForegroundTimelineEventType.SCREEN_INTERACTIVE -> interactionEvents +=
                    DeviceInteractionEventEntity(
                        androidEvent.timeStamp,
                        DeviceInteractionEventEntity.SCREEN_INTERACTIVE
                    )
                ForegroundTimelineEventType.KEYGUARD_HIDDEN -> interactionEvents +=
                    DeviceInteractionEventEntity(
                        androidEvent.timeStamp,
                        DeviceInteractionEventEntity.KEYGUARD_HIDDEN
                    )
                else -> Unit
            }

            timelineEvents += ForegroundTimelineEvent(
                timestampMillis = androidEvent.timeStamp,
                type = type,
                packageName = androidEvent.packageName,
                className = androidEvent.className
            )
        }

        return QueriedUsageEvents(
            timelineEvents = timelineEvents,
            interactionEvents = interactionEvents,
            notificationEvents = notificationEvents,
            rawEventCount = rawEventCount,
            queryDurationMillis = SystemClock.elapsedRealtime() - queryStartedAt
        )
    }

    /** UsageEvents가 표현한 화면 활성화·키가드 해제·알림 이벤트를 읽습니다. */
    fun getInteractionEvents(
        context: Context,
        startMillis: Long,
        endMillis: Long
    ): List<DeviceInteractionEventEntity> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()
        return queryUsageEvents(manager, startMillis, endMillis).interactionEvents
            .filter { it.timestampMillis in startMillis..endMillis }
    }

    /**
     * ACTION_USER_PRESENT와 KEYGUARD_HIDDEN은 실제 잠금 해제 상태 전환만 나타냅니다.
     * 두 소스가 한 번의 해제에서 연달아 올 수 있으므로 15초 안의 이벤트는 한 건으로 합칩니다.
     * SCREEN_INTERACTIVE는 알림 확인처럼 잠금 화면만 켠 경우도 포함하므로 잠금 해제로 세지 않습니다.
     */
    fun resolvedUnlockTimestamps(events: List<DeviceInteractionEventEntity>): List<Long> {
        data class Candidate(val timestamp: Long, val type: Int)
        val resolved = mutableListOf<Candidate>()
        events.asSequence()
            .filter {
                it.eventType == DeviceInteractionEventEntity.KEYGUARD_HIDDEN ||
                    it.eventType == DeviceInteractionEventEntity.USER_PRESENT
            }
            .sortedBy { it.timestampMillis }
            .forEach { event ->
            val last = resolved.lastOrNull()
            if (last == null) {
                resolved += Candidate(event.timestampMillis, event.eventType)
                return@forEach
            }
            val gap = event.timestampMillis - last.timestamp
            if (event.eventType == last.type && gap <= DUPLICATE_EVENT_WINDOW_MILLIS) {
                return@forEach
            }
            if (event.eventType != last.type && gap <= UNLOCK_PAIR_WINDOW_MILLIS) {
                if (event.eventType == DeviceInteractionEventEntity.USER_PRESENT) {
                    resolved[resolved.lastIndex] = Candidate(event.timestampMillis, event.eventType)
                }
                return@forEach
            }
            resolved += Candidate(event.timestampMillis, event.eventType)
        }
        return resolved.map { it.timestamp }
    }

    /**
     * 잠금 해제 시각 중 7초(thresholdMillis) 이내에 앱 진입 없이 화면을 껐거나,
     * 7초 이내에 시작된 짧은 세션 종료 후 추가 사용 없이 화면을 끈 '짧은 확인' 시각 목록을 반환합니다.
     */
    fun resolvedHabitualUnlockTimestamps(
        events: List<DeviceInteractionEventEntity>,
        sessions: List<ForegroundUsageSessionEntity>,
        thresholdMillis: Long = HABITUAL_CHECK_THRESHOLD_MILLIS
    ): List<Long> {
        val unlockTimestamps = resolvedUnlockTimestamps(events)
        val validSessions = sessions.filter { s ->
            s.packageName !in SELF_PACKAGES && !IGNORED_SYSTEM_PACKAGES.contains(s.packageName)
        }
        val habitualUnlocks = mutableListOf<Long>()
        unlockTimestamps.forEachIndexed { index, unlockTime ->
            val nextUnlockTime = unlockTimestamps.getOrNull(index + 1) ?: Long.MAX_VALUE
            val windowEnd = minOf(unlockTime + thresholdMillis, nextUnlockTime)
            val startedInWindow = validSessions.filter { s ->
                s.startTimeMillis in unlockTime until windowEnd
            }
            if (startedInWindow.isEmpty()) {
                // (A) 해당 7초 구간 내에 시작된 유효 앱 세션이 아예 없음
                habitualUnlocks.add(unlockTime)
            } else {
                // (B) 유효 앱 세션이 있더라도 마지막 종료 시각이 7초 이하이고 직후 5초 이내 추가 세션 없음
                val lastSessionEnd = startedInWindow.maxOf { it.endTimeMillis }
                if (lastSessionEnd <= unlockTime + thresholdMillis) {
                    val hasChainedSession = validSessions.any { s ->
                        s !in startedInWindow && s.startTimeMillis in lastSessionEnd..(lastSessionEnd + 5_000L) && s.startTimeMillis < nextUnlockTime
                    }
                    if (!hasChainedSession) {
                        habitualUnlocks.add(unlockTime)
                    }
                }
            }
        }
        return habitualUnlocks
    }

    /** 최근 24시간 시간대·세션과 최대 365일의 날짜별 장기 추세를 함께 읽습니다. */
    suspend fun getAppUsageInsights(context: Context, packageName: String): AppUsageInsights {
        val now = System.currentTimeMillis()
        val rollingStart = getStartOfTodayMillis(now)
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            if (get(Calendar.HOUR_OF_DAY) < 5) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val database = DigitsDatabase.getInstance(context)
        val appSegments = database.foregroundUsageSessionDao()
            .getBetween(rollingStart, now)
            .filter { it.packageName == packageName }
            .map {
                ForegroundUsageSegment(
                    packageName = it.packageName,
                    startTimeMillis = it.startTimeMillis,
                    endTimeMillis = it.endTimeMillis,
                    effectivePackageName = it.effectivePackageName,
                    effectiveCategoryLevel = it.effectiveCategoryLevel,
                    concurrentAppCount = it.concurrentAppCount,
                    sessionStartTimeMillis = it.sessionStartTimeMillis
                )
            }
        val sessionDurations = sessionDurationsByPackage(
            appSegments.filter { it.sessionStartTimeMillis in rollingStart until now }
        )[packageName].orEmpty()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cutoff = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -364) }
        val cutoffDate = dateFormat.format(cutoff.time)
        val todayDate = dateFormat.format(today.time)
        val historyDao = database.dailyAppUsageDao()
        val storedByDate = historyDao.getForPackageSince(packageName, cutoffDate).associateBy { it.dateString }
        val coverageDates = historyDao.getCoverageSince(cutoffDate).map { it.dateString }.toMutableSet()
        coverageDates += todayDate
        val daily = coverageDates.sorted().mapNotNull { dateString ->
            val dayStart = runCatching { dateFormat.parse(dateString)?.time }.getOrNull() ?: return@mapNotNull null
            DailyAppUsage(
                dayStartMillis = dayStart,
                usageMillis = storedByDate[dateString]?.usageMillis ?: 0L,
                sessionCount = storedByDate[dateString]?.sessionCount ?: 0,
                shortSessionCount = storedByDate[dateString]?.shortSessionCount ?: 0,
                isToday = dateString == todayDate
            )
        }.takeLast(365)

        val hourly = LongArray(24)
        appSegments.forEach { segment ->
            var cursor = maxOf(segment.startTimeMillis, rollingStart)
            while (cursor < segment.endTimeMillis) {
                val bucket = ((cursor - rollingStart) / (60 * 60_000L)).toInt().coerceIn(0, 23)
                val nextBucket = rollingStart + (bucket + 1L) * 60 * 60_000L
                val intervalEnd = minOf(segment.endTimeMillis, nextBucket, now)
                hourly[bucket] += (intervalEnd - cursor).coerceAtLeast(0L)
                cursor = intervalEnd
            }
        }

        return AppUsageInsights(
            hourlyUsageMillis = hourly.toList(),
            sessionDurationsMillis = sessionDurations,
            dailyUsage = daily,
            windowStartMillis = rollingStart,
            windowEndMillis = now
        )
    }

    /** 현재 시각을 끝으로 하는 직전 24시간의 잠금 해제·알림 흐름을 24개 시간 버킷으로 계산합니다. */
    suspend fun getRolling24HourUnlockInsights(
        context: Context,
        end: Long = System.currentTimeMillis()
    ): UnlockInsights {
        val db = DigitsDatabase.getInstance(context)
        val pastHistories = db.scoreDao().getRecentCoreIndexHistories(14)
        val validHistories = pastHistories.filter { it.unlockCount > 0 }
        val pastLowest = validHistories.minOfOrNull { it.unlockCount } ?: 0
        val pastAvg = if (validHistories.isNotEmpty()) validHistories.map { it.unlockCount }.average().toInt() else 0

        return getUnlockInsights(
            context = context,
            start = getStartOfTodayMillis(end),
            end = end,
            pastLowest = pastLowest,
            pastAvg = pastAvg
        )
    }

    private suspend fun getUnlockInsights(
        context: Context,
        start: Long,
        end: Long,
        pastLowest: Int = 0,
        pastAvg: Int = 0
    ): UnlockInsights {
        val db = DigitsDatabase.getInstance(context)
        val events = db.deviceInteractionEventDao()
            .getBetween(start, end)
        val unlockEvents = resolvedUnlockTimestamps(
            events
        )
        val hourly = MutableList(24) { 0 }
        unlockEvents.forEach { timestamp ->
            val bucket = (((timestamp - start) * 24) / (end - start).coerceAtLeast(1L))
                .toInt()
                .coerceIn(0, 23)
            hourly[bucket]++
        }
        val averageIntervalMinutes = unlockEvents
            .zipWithNext { first, second -> (second - first).coerceAtLeast(0L) }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.div(60_000.0)
            ?.toInt()
        val notificationCount = events.count {
            it.eventType == DeviceInteractionEventEntity.NOTIFICATION_INTERRUPTION
        }

        // 7초 이내 앱 미진입 또는 단기 확인 무의미한 열람(Glance / 짧은 확인) 집계
        val sessions = db.foregroundUsageSessionDao().getBetween(start, end)
        val glanceThresholdMillis = HABITUAL_CHECK_THRESHOLD_MILLIS
        val glanceCount = resolvedHabitualUnlockTimestamps(
            events = events,
            sessions = sessions,
            thresholdMillis = glanceThresholdMillis
        ).size

        return UnlockInsights(
            unlockCount = unlockEvents.size,
            hourlyUnlockCounts = hourly,
            averageIntervalMinutes = averageIntervalMinutes,
            notificationCount = notificationCount,
            notificationEventsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P,
            windowStartMillis = start,
            windowEndMillis = end,
            glanceUnlockCount = glanceCount.coerceAtMost(unlockEvents.size),
            glanceThresholdSeconds = 7,
            past14DaysLowestUnlock = pastLowest,
            past14DaysAverageUnlock = pastAvg
        )
    }

    /**
     * 오늘 자정부터 현재까지 화면이 켜지고 잠금 해제된 상태에서 최상단 앱 하나만 집계합니다.
     * queryAndAggregateUsageStats의 누적값은 앱별 시간 계산에 사용하지 않습니다.
     */
    fun getTodayUsageSnapshot(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>,
        endTime: Long = System.currentTimeMillis()
    ): TodayUsageSnapshot {
        return getUsageSnapshotForRange(
            context = context,
            appWeightMap = appWeightMap,
            startTime = getStartOfTodayMillis(),
            endTime = endTime
        )
    }

    /**
     * 임의의 구간(예: 어제 24시간 전 ~ 오늘 자정)의 전면 앱 사용 세그먼트와 이벤트를 집계합니다.
     * 프로세스 재시작 또는 앱 업데이트 시 누락된 최근 24시간 세션을 완벽히 복원할 수 있습니다.
     */
    fun getUsageSnapshotForRange(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>,
        startTime: Long,
        endTime: Long
    ): TodayUsageSnapshot {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return TodayUsageSnapshot(emptyList(), 0, 0L, false)
        if (endTime <= startTime) {
            return TodayUsageSnapshot(emptyList(), 0, 0L, false)
        }

        val lateNightEndTime = startTime + (5 * 60 * 60 * 1000L) // 해당 날짜 새벽 5시
        val queryStart = (startTime - FOREGROUND_STATE_LOOKBACK_MILLIS).coerceAtLeast(0L)
        val queried = queryUsageEvents(usageStatsManager, queryStart, endTime)
        val categoryLevelResolver = categoryLevelResolver(context, appWeightMap)
        val exclusiveUsage = ForegroundUsageAggregator.aggregate(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            lateNightEndTimeMillis = minOf(endTime, lateNightEndTime),
            events = queried.timelineEvents,
            categoryLevelResolver = categoryLevelResolver
        )

        return buildSnapshot(
            context = context,
            appWeightMap = appWeightMap,
            exclusiveUsage = exclusiveUsage,
            interactionEvents = queried.interactionEvents.filter { it.timestampMillis in startTime..endTime },
            notificationEvents = queried.notificationEvents.filter { it.timestampMillis in startTime..endTime },
            queriedEventCount = queried.rawEventCount,
            queryDurationMillis = queried.queryDurationMillis,
            queryWindowMillis = endTime - queryStart,
            incremental = false
        )
    }

    /**
     * 자정부터 현재까지의 OS 추정치 집계용 이벤트 기반 전면 사용 결과를 조회합니다.
     */
    internal fun queryEventBasedUsage(
        context: Context,
        startTime: Long,
        endTime: Long
    ): ForegroundUsageResult? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null
        if (endTime <= startTime) return null

        val queryStart = (startTime - FOREGROUND_STATE_LOOKBACK_MILLIS).coerceAtLeast(0L)
        val queried = queryUsageEvents(usageStatsManager, queryStart, endTime)
        return ForegroundUsageAggregator.aggregate(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            lateNightEndTimeMillis = endTime,
            events = queried.timelineEvents
        )
    }

    /** 마지막 처리 시점 이후의 짧은 구간만 읽어 기존 일일 스냅샷에 합칠 조각을 만듭니다. */
    fun getIncrementalUsageSnapshot(
        context: Context,
        startTime: Long,
        endTime: Long,
        initialState: ForegroundTrackerState,
        appWeightMap: Map<String, AppWeightEntity>
    ): TodayUsageSnapshot {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return TodayUsageSnapshot(emptyList(), 0, 0L, false, endingState = initialState, incremental = true)
        if (endTime <= startTime) {
            return TodayUsageSnapshot(emptyList(), 0, 0L, false, endingState = initialState, incremental = true)
        }
        val queried = queryUsageEvents(usageStatsManager, startTime, endTime)
        val todayStart = getStartOfTodayMillis()
        val categoryLevelResolver = categoryLevelResolver(context, appWeightMap)
        val exclusiveUsage = ForegroundUsageAggregator.aggregate(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            lateNightEndTimeMillis = minOf(endTime, todayStart + 5 * 60 * 60 * 1_000L),
            events = queried.timelineEvents,
            initialState = initialState,
            categoryLevelResolver = categoryLevelResolver
        )
        return buildSnapshot(
            context = context,
            appWeightMap = appWeightMap,
            exclusiveUsage = exclusiveUsage,
            interactionEvents = queried.interactionEvents.filter { it.timestampMillis in startTime..endTime },
            notificationEvents = queried.notificationEvents.filter { it.timestampMillis in startTime..endTime },
            queriedEventCount = queried.rawEventCount,
            queryDurationMillis = queried.queryDurationMillis,
            queryWindowMillis = endTime - startTime,
            incremental = true
        )
    }

    private fun buildSnapshot(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>,
        exclusiveUsage: ForegroundUsageResult,
        interactionEvents: List<DeviceInteractionEventEntity>,
        notificationEvents: List<AppNotificationEvent> = emptyList(),
        queriedEventCount: Int,
        queryDurationMillis: Long,
        queryWindowMillis: Long,
        incremental: Boolean
    ): TodayUsageSnapshot {

        val pm = context.packageManager
        val result = mutableListOf<AppUsage>()
        val includedSegments = exclusiveUsage.segments.filter {
            shouldIncludeUsagePackage(it.packageName, it.durationMillis)
        }
        val sessionSummaries = sessionSummariesByPackage(includedSegments)

        for ((pkgName, rawTimeMillis) in exclusiveUsage.usageMillisByPackage) {
            // ForegroundUsageAggregator already clips every segment to the requested
            // window. Keep the helper independent of the caller's start/end values so
            // the same snapshot builder can serve both full and incremental queries.
            val timeMillis = rawTimeMillis.coerceAtLeast(0L)
            if (shouldIncludeUsagePackage(pkgName, timeMillis)) {
                val savedEntity = appWeightMap[pkgName]
                val appName = savedEntity?.appName ?: try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val category = resolveCategory(pm, pkgName, savedEntity)
                val lateNightTime = (exclusiveUsage.lateNightUsageMillisByPackage[pkgName] ?: 0L)
                    .coerceIn(0L, timeMillis)
                val summary = sessionSummaries[pkgName]

                result.add(
                    AppUsage(
                        packageName = pkgName,
                        appName = appName,
                        usageTimeMillis = timeMillis,
                        categoryType = category,
                        lastTimeUsedMillis = exclusiveUsage.lastUsedMillisByPackage[pkgName] ?: 0L,
                        lateNightUsageMillis = lateNightTime,
                        sessionCount = summary?.sessionCount ?: 0,
                        shortSessionCount = summary?.shortSessionCount ?: 0
                    )
                )
            }
        }

        return TodayUsageSnapshot(
            appsUsage = result.sortedByDescending { it.usageTimeMillis },
            unlockCount = resolvedUnlockTimestamps(interactionEvents).size,
            assignedUsageMillis = exclusiveUsage.assignedUsageMillis,
            hasForegroundEvidence = exclusiveUsage.hasForegroundEvidence,
            foregroundSegments = includedSegments,
            sessionSummariesByPackage = sessionSummaries,
            observableUnlockedMillis = exclusiveUsage.observableUnlockedMillis,
            endingState = exclusiveUsage.endingState,
            interactionEvents = interactionEvents,
            notificationEvents = notificationEvents,
            queriedEventCount = queriedEventCount,
            queryDurationMillis = queryDurationMillis,
            queryWindowMillis = queryWindowMillis,
            incremental = incremental
        )
    }

    private fun categoryLevelResolver(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>
    ): (String) -> Int {
        val packageManager = context.packageManager
        val cache = mutableMapOf<String, Int>()
        return { packageName ->
            cache.getOrPut(packageName) {
                resolveCategory(
                    packageManager,
                    packageName,
                    appWeightMap[packageName]
                ).canonical.level
            }
        }
    }

    /** 프로세스 시작 시 만든 오늘 스냅샷에 이후 증분 조각을 겹침 없이 합칩니다. */
    fun mergeSnapshots(base: TodayUsageSnapshot, delta: TodayUsageSnapshot): TodayUsageSnapshot {
        val apps = linkedMapOf<String, AppUsage>()
        (base.appsUsage + delta.appsUsage).forEach { app ->
            val previous = apps[app.packageName]
            apps[app.packageName] = if (previous == null) app else previous.copy(
                appName = app.appName,
                usageTimeMillis = previous.usageTimeMillis + app.usageTimeMillis,
                categoryType = app.categoryType,
                lastTimeUsedMillis = maxOf(previous.lastTimeUsedMillis, app.lastTimeUsedMillis),
                lateNightUsageMillis = previous.lateNightUsageMillis + app.lateNightUsageMillis
            )
        }
        val segments = mutableListOf<ForegroundUsageSegment>()
        (base.foregroundSegments + delta.foregroundSegments).sortedBy { it.startTimeMillis }.forEach { segment ->
            val previous = segments.lastOrNull()
            if (previous != null && previous.packageName == segment.packageName &&
                previous.effectivePackageName == segment.effectivePackageName &&
                previous.effectiveCategoryLevel == segment.effectiveCategoryLevel &&
                previous.concurrentAppCount == segment.concurrentAppCount &&
                previous.sessionStartTimeMillis == segment.sessionStartTimeMillis &&
                segment.startTimeMillis <= previous.endTimeMillis + 1_000L
            ) {
                segments[segments.lastIndex] = previous.copy(
                    endTimeMillis = maxOf(previous.endTimeMillis, segment.endTimeMillis)
                )
            } else {
                segments += segment
            }
        }
        val interactions = (base.interactionEvents + delta.interactionEvents)
            .distinctBy { it.timestampMillis to it.eventType }
            .sortedBy { it.timestampMillis }
        val notifs = (base.notificationEvents + delta.notificationEvents)
            .distinctBy { it.packageName to it.timestampMillis }
            .sortedBy { it.timestampMillis }
        val sessionSummaries = sessionSummariesByPackage(segments)
        return TodayUsageSnapshot(
            appsUsage = apps.values.map { app ->
                val summary = sessionSummaries[app.packageName]
                app.copy(
                    sessionCount = summary?.sessionCount ?: 0,
                    shortSessionCount = summary?.shortSessionCount ?: 0
                )
            }.sortedByDescending { it.usageTimeMillis },
            unlockCount = resolvedUnlockTimestamps(interactions).size,
            assignedUsageMillis = base.assignedUsageMillis + delta.assignedUsageMillis,
            hasForegroundEvidence = base.hasForegroundEvidence || delta.hasForegroundEvidence,
            sessionSummariesByPackage = sessionSummaries,
            foregroundSegments = segments,
            observableUnlockedMillis = base.observableUnlockedMillis + delta.observableUnlockedMillis,
            endingState = delta.endingState,
            interactionEvents = interactions,
            notificationEvents = notifs,
            queriedEventCount = delta.queriedEventCount,
            queryDurationMillis = delta.queryDurationMillis,
            queryWindowMillis = delta.queryWindowMillis,
            incremental = true
        )
    }

    /**
     * 기기에 설치된 실행 가능한 사용자 앱 목록 조회
     * 오늘 아침 05:00 AM (getTodayUsageSnapshot) 기준의 사용량을 반영하고,
     * 사용자 직접 변경(isUserModified)이 없는 앱은 최신 AppCategoryPolicy 기본값을 적용합니다.
     */
    fun getInstalledApps(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity> = emptyMap()
    ): List<AppUsage> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val list = mutableListOf<AppUsage>()

        val todaySnapshot = runCatching {
            getTodayUsageSnapshot(context, appWeightMap)
        }.getOrNull()
        val todayUsageMap = todaySnapshot?.appsUsage?.associate { it.packageName to it.usageTimeMillis } ?: emptyMap()

        for (resolveInfo in resolveInfos) {
            val pkgName = resolveInfo.activityInfo.packageName
            if (pkgName in SELF_PACKAGES || pkgName in IGNORED_SYSTEM_PACKAGES) continue
            val appName = resolveInfo.loadLabel(pm).toString()
            val appInfo = runCatching { pm.getApplicationInfo(pkgName, 0) }.getOrNull()
            val isLegacyGame = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
            val applicationCategory = when {
                appInfo == null -> ApplicationInfo.CATEGORY_UNDEFINED
                appInfo.category != ApplicationInfo.CATEGORY_UNDEFINED -> appInfo.category
                isLegacyGame -> ApplicationInfo.CATEGORY_GAME
                else -> ApplicationInfo.CATEGORY_UNDEFINED
            }

            val savedWeight = appWeightMap[pkgName]
            val resolvedCategory = if (savedWeight != null && savedWeight.isUserModified) {
                savedWeight.categoryType.canonical
            } else {
                com.digitscore.app.model.AppCategoryPolicy.resolveDefaultCategory(
                    packageName = pkgName,
                    applicationCategory = applicationCategory
                )
            }

            val usageMillis = todayUsageMap[pkgName] ?: 0L

            list.add(
                AppUsage(
                    packageName = pkgName,
                    appName = appName,
                    usageTimeMillis = usageMillis,
                    categoryType = resolvedCategory
                )
            )
        }

        return list
            .distinctBy { it.packageName }
            .sortedWith(compareByDescending<AppUsage> { it.usageTimeMillis }.thenBy { it.appName.lowercase() })
    }
}
