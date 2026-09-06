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
import com.digitscore.app.data.entity.AppWeightEntity
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
    val foregroundSegments: List<ForegroundUsageSegment> = emptyList()
)

data class AppSessionSummary(
    val sessionCount: Int,
    val longestSessionMillis: Long
)

data class DailyAppUsage(
    val dayStartMillis: Long,
    val usageMillis: Long,
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

object UsageStatsHelper {

    private const val FOREGROUND_STATE_LOOKBACK_MILLIS = 6 * 60 * 60 * 1_000L
    // UsageEvents.Event.NOTIFICATION_INTERRUPTION은 event type 12이지만 일부 공개 SDK에서
    // 심볼이 노출되지 않으므로 플랫폼에 정의된 안정된 정수 값을 사용합니다.
    private const val EVENT_TYPE_NOTIFICATION_INTERRUPTION = 12

    private data class QueriedUsageEvents(
        val timelineEvents: List<ForegroundTimelineEvent>,
        val notificationTimestamps: List<Long>
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
            ApplicationInfo.CATEGORY_NEWS -> AppCategoryType.MILDLY_DISTRACTING

            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategoryType.MILDLY_PRODUCTIVE
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
            SHOPPING_PACKAGE_PREFIXES.any(normalizedPackage::startsWith) -> AppCategoryType.MILDLY_DISTRACTING
            else -> defaultCategoryForApplicationCategory(applicationCategory)
        }
    }

    private fun resolveCategory(
        pm: PackageManager,
        packageName: String,
        savedEntity: AppWeightEntity?
    ): AppCategoryType {
        // 사용자가 직접 지정한 분류를 최우선으로 존중합니다.
        savedEntity?.let { return it.categoryType }
        val applicationCategory = try {
            pm.getApplicationInfo(packageName, 0).category
        } catch (_: Exception) {
            ApplicationInfo.CATEGORY_UNDEFINED
        }
        return defaultCategoryForPackage(packageName, applicationCategory)
    }

    internal fun shouldIncludeUsagePackage(packageName: String, usageTimeMillis: Long): Boolean =
        usageTimeMillis >= 10_000L && packageName !in IGNORED_SYSTEM_PACKAGES

    private fun queryExclusiveForegroundUsage(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long,
        lateNightEndTime: Long
    ): ForegroundUsageResult {
        val queryStart = (startTime - FOREGROUND_STATE_LOOKBACK_MILLIS).coerceAtLeast(0L)
        val queriedEvents = queryUsageEvents(usageStatsManager, queryStart, endTime)
        return ForegroundUsageAggregator.aggregate(
            startTimeMillis = startTime,
            endTimeMillis = endTime,
            lateNightEndTimeMillis = lateNightEndTime,
            events = queriedEvents.timelineEvents
        )
    }

    private fun queryUsageEvents(
        usageStatsManager: UsageStatsManager,
        queryStart: Long,
        endTime: Long
    ): QueriedUsageEvents {
        val usageEvents = usageStatsManager.queryEvents(queryStart, endTime)
            ?: return QueriedUsageEvents(emptyList(), emptyList())
        val androidEvent = UsageEvents.Event()
        val timelineEvents = mutableListOf<ForegroundTimelineEvent>()
        val notificationTimestamps = mutableListOf<Long>()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(androidEvent)
            if (androidEvent.eventType == EVENT_TYPE_NOTIFICATION_INTERRUPTION) {
                notificationTimestamps += androidEvent.timeStamp
                continue
            }
            val type = when (androidEvent.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundTimelineEventType.APP_RESUMED
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

            timelineEvents += ForegroundTimelineEvent(
                timestampMillis = androidEvent.timeStamp,
                type = type,
                packageName = androidEvent.packageName,
                className = androidEvent.className
            )
        }

        return QueriedUsageEvents(timelineEvents, notificationTimestamps)
    }

    /**
     * 오늘의 시간대·세션은 OS 이벤트로 계산하고, 장기 추세는 앱이 자체 저장한
     * 최대 365일 일별 집계에서 읽습니다.
     */
    suspend fun getAppUsageInsights(context: Context, packageName: String): AppUsageInsights {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
        val now = System.currentTimeMillis()
        val today = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = today.timeInMillis
        val queried = manager?.let {
            queryUsageEvents(
                it,
                (todayStart - FOREGROUND_STATE_LOOKBACK_MILLIS).coerceAtLeast(0L),
                now
            )
        }
        val todayResult = queried?.let {
            ForegroundUsageAggregator.aggregate(
                startTimeMillis = todayStart,
                endTimeMillis = now,
                lateNightEndTimeMillis = todayStart,
                events = it.timelineEvents
            )
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cutoff = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -364) }
        val cutoffDate = dateFormat.format(cutoff.time)
        val todayDate = dateFormat.format(today.time)
        val historyDao = DigitsDatabase.getInstance(context).dailyAppUsageDao()
        val storedByDate = historyDao.getForPackageSince(packageName, cutoffDate).associateBy { it.dateString }
        val coverageDates = historyDao.getCoverageSince(cutoffDate).map { it.dateString }.toMutableSet()
        coverageDates += todayDate
        val daily = coverageDates.sorted().mapNotNull { dateString ->
            val dayStart = runCatching { dateFormat.parse(dateString)?.time }.getOrNull() ?: return@mapNotNull null
            DailyAppUsage(
                dayStartMillis = dayStart,
                usageMillis = if (dateString == todayDate) {
                    todayResult?.usageMillisByPackage?.get(packageName)
                        ?: storedByDate[dateString]?.usageMillis
                        ?: 0L
                } else {
                    storedByDate[dateString]?.usageMillis ?: 0L
                },
                isToday = dateString == todayDate
            )
        }.takeLast(365)

        val appSegments = todayResult?.segments?.filter { it.packageName == packageName }.orEmpty()
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
            sessionDurationsMillis = appSegments.map { it.durationMillis },
            dailyUsage = daily
        )
    }

    /** 오늘 언락 시간대와 OS UsageEvents가 제공하는 알림 interruption 수를 함께 계산합니다. */
    fun getTodayUnlockInsights(context: Context): UnlockInsights {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UnlockInsights(0, List(24) { 0 }, null, 0, false)
        val start = getStartOfTodayMillis()
        val end = System.currentTimeMillis()
        val queried = queryUsageEvents(
            manager,
            (start - FOREGROUND_STATE_LOOKBACK_MILLIS).coerceAtLeast(0L),
            end
        )
        val todayEvents = queried.timelineEvents.filter { it.timestampMillis in start..end }
        val hiddenEvents = todayEvents.filter { it.type == ForegroundTimelineEventType.KEYGUARD_HIDDEN }
        val unlockEvents = if (hiddenEvents.isNotEmpty()) {
            hiddenEvents
        } else {
            todayEvents.filter { it.type == ForegroundTimelineEventType.SCREEN_INTERACTIVE }
        }
        val hourly = MutableList(24) { 0 }
        unlockEvents.forEach { event ->
            val hour = Calendar.getInstance().apply { timeInMillis = event.timestampMillis }
                .get(Calendar.HOUR_OF_DAY)
            hourly[hour]++
        }
        val averageIntervalMinutes = unlockEvents.map { it.timestampMillis }
            .zipWithNext { first, second -> (second - first).coerceAtLeast(0L) }
            .takeIf { it.isNotEmpty() }
            ?.average()
            ?.div(60_000.0)
            ?.toInt()
        val notificationCount = queried.notificationTimestamps.count { it in start..end }

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
        appWeightMap: Map<String, AppWeightEntity>
    ): TodayUsageSnapshot {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return TodayUsageSnapshot(emptyList(), 0, 0L, false)

        val startTime = getStartOfTodayMillis()
        val endTime = System.currentTimeMillis()
        val lateNightEndTime = startTime + (5 * 60 * 60 * 1000L) // 오늘 새벽 5시
        val exclusiveUsage = queryExclusiveForegroundUsage(
            usageStatsManager = usageStatsManager,
            startTime = startTime,
            endTime = endTime,
            lateNightEndTime = minOf(endTime, lateNightEndTime)
        )

        val pm = context.packageManager
        val result = mutableListOf<AppUsage>()

        for ((pkgName, rawTimeMillis) in exclusiveUsage.usageMillisByPackage) {
            val timeMillis = rawTimeMillis.coerceIn(0L, endTime - startTime)
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

                result.add(
                    AppUsage(
                        packageName = pkgName,
                        appName = appName,
                        usageTimeMillis = timeMillis,
                        categoryType = category,
                        lastTimeUsedMillis = exclusiveUsage.lastUsedMillisByPackage[pkgName] ?: 0L,
                        lateNightUsageMillis = lateNightTime
                    )
                )
            }
        }

        return TodayUsageSnapshot(
            appsUsage = result.sortedByDescending { it.usageTimeMillis },
            unlockCount = exclusiveUsage.unlockCount,
            assignedUsageMillis = exclusiveUsage.assignedUsageMillis,
            hasForegroundEvidence = exclusiveUsage.hasForegroundEvidence,
            foregroundSegments = exclusiveUsage.segments.filter {
                shouldIncludeUsagePackage(it.packageName, it.durationMillis)
            },
            sessionSummariesByPackage = exclusiveUsage.segments
                .groupBy { it.packageName }
                .mapValues { (_, segments) ->
                    AppSessionSummary(
                        sessionCount = segments.size,
                        longestSessionMillis = segments.maxOfOrNull { it.durationMillis } ?: 0L
                    )
                }
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
