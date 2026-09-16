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
    val dailyUsage: List<DailyAppUsage>
)

data class UnlockInsights(
    val unlockCount: Int,
    val hourlyUnlockCounts: List<Int>,
    val averageIntervalMinutes: Int?,
    val notificationCount: Int,
    val notificationEventsSupported: Boolean
)

/** 한 번의 실제 앱 진입에 속한 여러 전면 조각을 세션 ID로 묶어 사용시간을 합칩니다. */
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
        shortSessionCount = durations.count { it < 60_000L }
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
     * 오늘 0시 0분 0초 타임스탬프 반환
     */
    fun getStartOfTodayMillis(): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private val IGNORED_SYSTEM_PACKAGES = setOf(
        "android",
        "com.android.systemui",
        "com.sec.android.app.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.inputmethod",
        "com.google.android.inputmethod.latin",
        "com.samsung.android.honeyboard",
        "com.samsung.android.app.aodservice",
        "com.samsung.android.incallui",
        "com.android.phone",
        "com.digitscore.app"
    )

    internal fun defaultCategoryForApplicationCategory(applicationCategory: Int): AppCategoryType =
        when (applicationCategory) {
            ApplicationInfo.CATEGORY_GAME,
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO -> AppCategoryType.DISTRACTING

            ApplicationInfo.CATEGORY_SOCIAL,
            ApplicationInfo.CATEGORY_NEWS -> AppCategoryType.DISTRACTING

            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategoryType.PRODUCTIVE
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
    ): AppCategoryType {
        val normalizedPackage = packageName.lowercase()
        return when {
            EDUCATION_PACKAGE_PREFIXES.any(normalizedPackage::startsWith) -> AppCategoryType.PRODUCTIVE
            SHOPPING_PACKAGE_PREFIXES.any(normalizedPackage::startsWith) -> AppCategoryType.DISTRACTING
            else -> defaultCategoryForApplicationCategory(applicationCategory)
        }
    }

    private fun resolveCategory(
        pm: PackageManager,
        packageName: String,
        savedEntity: AppWeightEntity?
    ): AppCategoryType {
        // 사용자가 직접 지정한 분류를 최우선으로 존중합니다.
        savedEntity?.let { return it.categoryType.canonical }
        val applicationCategory = try {
            pm.getApplicationInfo(packageName, 0).category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return defaultCategoryForPackage(packageName, applicationCategory)
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
                0,
                SystemClock.elapsedRealtime() - queryStartedAt
            )
        val androidEvent = UsageEvents.Event()
        val timelineEvents = mutableListOf<ForegroundTimelineEvent>()
        val interactionEvents = mutableListOf<DeviceInteractionEventEntity>()
        var rawEventCount = 0

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(androidEvent)
            rawEventCount++
            if (androidEvent.eventType == EVENT_TYPE_NOTIFICATION_INTERRUPTION) {
                interactionEvents += DeviceInteractionEventEntity(
                    androidEvent.timeStamp,
                    DeviceInteractionEventEntity.NOTIFICATION_INTERRUPTION
                )
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
     * SCREEN_INTERACTIVE는 알림 확인처럼 잠금 화면만 켠 경우도 포함하므로 언락으로 세지 않습니다.
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
     * 오늘의 시간대·세션은 OS 이벤트로 계산하고, 장기 추세는 앱이 자체 저장한
     * 최대 365일 일별 집계에서 읽습니다.
     */
    suspend fun getAppUsageInsights(context: Context, packageName: String): AppUsageInsights {
        val now = System.currentTimeMillis()
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = today.timeInMillis
        val database = DigitsDatabase.getInstance(context)
        val appSegments = database.foregroundUsageSessionDao()
            .getBetween(todayStart, now)
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
        val sessionDurations = sessionDurationsByPackage(appSegments)[packageName].orEmpty()

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
                usageMillis = if (dateString == todayDate) {
                    appSegments.sumOf { it.durationMillis }
                } else {
                    storedByDate[dateString]?.usageMillis ?: 0L
                },
                sessionCount = if (dateString == todayDate) {
                    sessionDurations.size
                } else {
                    storedByDate[dateString]?.sessionCount ?: 0
                },
                shortSessionCount = if (dateString == todayDate) {
                    sessionDurations.count { it < 60_000L }
                } else {
                    storedByDate[dateString]?.shortSessionCount ?: 0
                },
                isToday = dateString == todayDate
            )
        }.takeLast(365)

        val hourly = LongArray(24)
        appSegments.forEach { segment ->
            var cursor = segment.startTimeMillis
            while (cursor < segment.endTimeMillis) {
                val calendar = Calendar.getInstance().apply { timeInMillis = cursor }
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                val nextHour = (calendar.clone() as Calendar).apply {
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    add(Calendar.HOUR_OF_DAY, 1)
                }.timeInMillis
                val intervalEnd = minOf(segment.endTimeMillis, nextHour)
                hourly[hour] += intervalEnd - cursor
                cursor = intervalEnd
            }
        }

        return AppUsageInsights(
            hourlyUsageMillis = hourly.toList(),
            sessionDurationsMillis = sessionDurations,
            dailyUsage = daily
        )
    }

    /** 오늘 언락 시간대와 OS UsageEvents가 제공하는 알림 interruption 수를 함께 계산합니다. */
    suspend fun getTodayUnlockInsights(context: Context): UnlockInsights = getUnlockInsights(
        context = context,
        start = getStartOfTodayMillis(),
        end = System.currentTimeMillis(),
        rollingBuckets = false
    )

    /** 현재 시각을 끝으로 하는 직전 24시간의 언락·알림 흐름을 24개 시간 버킷으로 계산합니다. */
    suspend fun getRolling24HourUnlockInsights(
        context: Context,
        end: Long = System.currentTimeMillis()
    ): UnlockInsights {
        return getUnlockInsights(
            context = context,
            start = end - 24 * 60 * 60_000L,
            end = end,
            rollingBuckets = true
        )
    }

    private suspend fun getUnlockInsights(
        context: Context,
        start: Long,
        end: Long,
        rollingBuckets: Boolean
    ): UnlockInsights {
        val events = DigitsDatabase.getInstance(context).deviceInteractionEventDao()
            .getBetween(start, end)
        val unlockEvents = resolvedUnlockTimestamps(
            events
        )
        val hourly = MutableList(24) { 0 }
        unlockEvents.forEach { timestamp ->
            val bucket = if (rollingBuckets) {
                (((timestamp - start) * 24) / (end - start).coerceAtLeast(1L))
                    .toInt()
                    .coerceIn(0, 23)
            } else {
                Calendar.getInstance().apply { timeInMillis = timestamp }
                    .get(Calendar.HOUR_OF_DAY)
            }
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

        return UnlockInsights(
            unlockCount = unlockEvents.size,
            hourlyUnlockCounts = hourly,
            averageIntervalMinutes = averageIntervalMinutes,
            notificationCount = notificationCount,
            notificationEventsSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
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
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return TodayUsageSnapshot(emptyList(), 0, 0L, false)

        val startTime = getStartOfTodayMillis()
        val lateNightEndTime = startTime + (5 * 60 * 60 * 1000L) // 오늘 새벽 5시
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
            queriedEventCount = queried.rawEventCount,
            queryDurationMillis = queried.queryDurationMillis,
            queryWindowMillis = endTime - queryStart,
            incremental = false
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
            queriedEventCount = delta.queriedEventCount,
            queryDurationMillis = delta.queryDurationMillis,
            queryWindowMillis = delta.queryWindowMillis,
            incremental = true
        )
    }

    /**
     * 기기에 설치된 실행 가능한 사용자 앱 목록 조회
     */
    fun getInstalledApps(context: Context): List<AppUsage> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(intent, 0)
        val list = mutableListOf<AppUsage>()

        for (resolveInfo in resolveInfos) {
            val pkgName = resolveInfo.activityInfo.packageName
            val appName = resolveInfo.loadLabel(pm).toString()
            val applicationCategory = try {
                pm.getApplicationInfo(pkgName, 0).category
            } catch (_: Exception) {
                ApplicationInfo.CATEGORY_UNDEFINED
            }
            list.add(
                AppUsage(
                    packageName = pkgName,
                    appName = appName,
                    usageTimeMillis = 0L,
                    categoryType = defaultCategoryForPackage(pkgName, applicationCategory)
                )
            )
        }

        return list
            .distinctBy { it.packageName }
            .sortedBy { it.appName }
    }
}
