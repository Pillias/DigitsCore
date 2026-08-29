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
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.engine.ScoreCalculator
import com.digitscore.app.model.ScoreRule
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

object UsageStatsHelper {

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

    /**
     * 오늘 0시부터 현재까지의 실제 화면 포그라운드(Activity Resumed) 앱별 사용 시간 및 정보 집계
     * (백그라운드 서비스나 오디오 재생 등 중복 실행 앱을 배제하고, 실제 화면에 떠있던 단일 앱만 카운팅)
     */
    fun getTodayAppUsageStats(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>
    ): List<AppUsage> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val startTime = getStartOfTodayMillis()
        val endTime = System.currentTimeMillis()
        val lateNightEndTime = startTime + (5 * 60 * 60 * 1000L) // 오늘 새벽 5시

        val appUsageMap = mutableMapOf<String, Long>()
        val lateNightMap = mutableMapOf<String, Long>()

        // 1. UsageEvents를 이용한 실제 화면 포그라운드(Single Active Window) 정밀 타임라인 파싱
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var currentForegroundPkg: String? = null
        var lastEventTime = startTime

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val eventTime = event.timeStamp
            val eventType = event.eventType

            if (currentForegroundPkg != null && eventTime > lastEventTime) {
                val duration = eventTime - lastEventTime
                // 비정상적인 긴 단일 인터벌(4시간 이상) 방어
                if (duration in 1 until (4 * 3600 * 1000L)) {
                    appUsageMap[currentForegroundPkg] = (appUsageMap[currentForegroundPkg] ?: 0L) + duration

                    // 심야 시간(00:00 ~ 05:00) 사용량 계산
                    if (lastEventTime < lateNightEndTime) {
                        val lateStart = lastEventTime
                        val lateEnd = kotlin.math.min(eventTime, lateNightEndTime)
                        val lateDuration = lateEnd - lateStart
                        if (lateDuration > 0) {
                            lateNightMap[currentForegroundPkg] = (lateNightMap[currentForegroundPkg] ?: 0L) + lateDuration
                        }
                    }
                }
            }

            when (eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    currentForegroundPkg = event.packageName
                    lastEventTime = eventTime
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    if (event.packageName == currentForegroundPkg) {
                        currentForegroundPkg = null
                        lastEventTime = eventTime
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    currentForegroundPkg = null
                    lastEventTime = eventTime
                }
            }
        }

        // 현재 시점까지 화면에 떠있는 앱 처리
        if (currentForegroundPkg != null && endTime > lastEventTime) {
            val duration = endTime - lastEventTime
            if (duration in 1 until (4 * 3600 * 1000L)) {
                appUsageMap[currentForegroundPkg] = (appUsageMap[currentForegroundPkg] ?: 0L) + duration
            }
        }

        // 2. 만약 UsageEvents 지원이 제한된 기기이거나 이벤트가 없는 경우 queryUsageStats 기반 폴백
        if (appUsageMap.isEmpty()) {
            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )
            if (!usageStatsList.isNullOrEmpty()) {
                val lateNightStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_BEST,
                    startTime,
                    kotlin.math.min(endTime, lateNightEndTime)
                )
                val lateNightStatsMap = lateNightStatsList?.associate { it.packageName to it.totalTimeInForeground } ?: emptyMap()
                for (stat in usageStatsList) {
                    if (stat.totalTimeInForeground > 10_000L) {
                        appUsageMap[stat.packageName] = stat.totalTimeInForeground
                        val late = lateNightStatsMap[stat.packageName] ?: 0L
                        if (late > 0) lateNightMap[stat.packageName] = late
                    }
                }
            }
        }

        val pm = context.packageManager
        val result = mutableListOf<AppUsage>()

        for ((pkgName, usageMillis) in appUsageMap) {
            if (usageMillis > 10_000L) { // 10초 이상 사용 앱만 유의미하게 처리
                val savedEntity = appWeightMap[pkgName]
                val appName = savedEntity?.appName ?: try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val category = savedEntity?.categoryType ?: AppCategoryType.NEUTRAL
                val lateNightTime = lateNightMap[pkgName] ?: 0L

                result.add(
                    AppUsage(
                        packageName = pkgName,
                        appName = appName,
                        usageTimeMillis = usageMillis,
                        categoryType = category,
                        lastTimeUsedMillis = endTime,
                        lateNightUsageMillis = lateNightTime
                    )
                )
            }
        }

        return result.sortedByDescending { it.usageTimeMillis }
    }

    /**
     * 과거 N일간(기본 30일)의 일별 사용량을 OS로부터 쿼리하여 일자별 DailyScoreHistoryEntity로 변환합니다.
     */
    fun syncPastDaysUsageStats(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>,
        scoreRule: ScoreRule = ScoreRule(),
        days: Int = 30
    ): List<DailyScoreHistoryEntity> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val pm = context.packageManager
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val result = mutableListOf<DailyScoreHistoryEntity>()

        val todayCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // 과거 days일 전부터 어제(1일 전)까지 30개 일자 순회
        for (i in days downTo 1) {
            val dayCalStart = (todayCalendar.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, -i)
            }
            val dayCalEnd = (dayCalStart.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
                set(Calendar.MILLISECOND, 999)
            }

            val startTime = dayCalStart.timeInMillis
            val endTime = dayCalEnd.timeInMillis
            val dateString = sdf.format(dayCalStart.time)

            // 1) 해당 일자의 사용량 조회 (INTERVAL_BEST 우선, 실패 시 INTERVAL_DAILY)
            var statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            )
            if (statsList.isNullOrEmpty()) {
                statsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startTime,
                    endTime
                )
            }

            val dayAppUsages = mutableListOf<AppUsage>()
            if (!statsList.isNullOrEmpty()) {
                for (stat in statsList) {
                    val fgTime = stat.totalTimeInForeground
                    if (fgTime > 1000L) { // 1초 이상 사용
                        val pkgName = stat.packageName
                        val savedEntity = appWeightMap[pkgName]
                        val appName = savedEntity?.appName ?: try {
                            val appInfo = pm.getApplicationInfo(pkgName, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkgName
                        }
                        val category = savedEntity?.categoryType ?: AppCategoryType.NEUTRAL
                        dayAppUsages.add(
                            AppUsage(
                                packageName = pkgName,
                                appName = appName,
                                usageTimeMillis = fgTime,
                                categoryType = category,
                                lastTimeUsedMillis = stat.lastTimeStamp
                            )
                        )
                    }
                }
            }

            // 동일 앱 패키지 중복 합산
            val combinedMap = mutableMapOf<String, AppUsage>()
            for (app in dayAppUsages) {
                val existing = combinedMap[app.packageName]
                if (existing != null) {
                    combinedMap[app.packageName] = existing.copy(
                        usageTimeMillis = existing.usageTimeMillis + app.usageTimeMillis
                    )
                } else {
                    combinedMap[app.packageName] = app
                }
            }
            val finalDayApps = combinedMap.values.toList()

            var totalScreenMinutes = finalDayApps.sumOf { it.usageTimeMinutes }
            // 하루 1440분(24시간) 초과 시 1440분으로 상한 클램핑
            if (totalScreenMinutes > 1440L) {
                totalScreenMinutes = 1440L
            }

            val distractingMinutes = finalDayApps
                .filter { it.categoryType == AppCategoryType.DISTRACTING }
                .sumOf { it.usageTimeMinutes }
            val productiveMinutes = finalDayApps
                .filter { it.categoryType == AppCategoryType.PRODUCTIVE }
                .sumOf { it.usageTimeMinutes }

            val estimatedIdleMinutes = max(0L, 1440L - totalScreenMinutes)
            val estimatedUnlockCount = if (totalScreenMinutes > 0) {
                (totalScreenMinutes / 12L).coerceIn(10L, 60L).toInt()
            } else {
                0
            }

            val scoreDetail = if (finalDayApps.isNotEmpty()) {
                ScoreCalculator.calculateScore(
                    appsUsage = finalDayApps,
                    idleMinutes = estimatedIdleMinutes,
                    unlockCount = estimatedUnlockCount,
                    rule = scoreRule
                )
            } else {
                // 사용 기록이 전혀 없는 날은 기본 100점 (완벽한 디톡스)
                ScoreCalculator.calculateScore(
                    appsUsage = emptyList(),
                    idleMinutes = 1440L,
                    unlockCount = 0,
                    rule = scoreRule
                )
            }

            result.add(
                DailyScoreHistoryEntity(
                    dateString = dateString,
                    finalScore = scoreDetail.finalScore,
                    totalScreenTimeMinutes = totalScreenMinutes,
                    distractingTimeMinutes = distractingMinutes,
                    productiveTimeMinutes = productiveMinutes,
                    idleMinutes = estimatedIdleMinutes,
                    unlockCount = estimatedUnlockCount,
                    lastUpdatedTimestamp = dayCalStart.timeInMillis
                )
            )
        }

        return result
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
            list.add(
                AppUsage(
                    packageName = pkgName,
                    appName = appName,
                    usageTimeMillis = 0L,
                    categoryType = AppCategoryType.NEUTRAL
                )
            )
        }

        return list.sortedBy { it.appName }
    }
}
