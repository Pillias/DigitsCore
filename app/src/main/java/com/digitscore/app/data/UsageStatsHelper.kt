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
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO -> AppCategoryType.DISTRACTING
            else -> AppCategoryType.NEUTRAL
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
        return defaultCategoryForApplicationCategory(applicationCategory)
    }

    internal fun shouldIncludeUsagePackage(packageName: String, usageTimeMillis: Long): Boolean =
        usageTimeMillis >= 10_000L && packageName !in IGNORED_SYSTEM_PACKAGES

    /**
     * 오늘 0시부터 현재까지의 실제 사용자 앱별 사용 시간 및 정보 집계
     * 제조사와 Android 버전에 따라 일부 차이가 날 수 있는 근사 집계입니다.
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

        // Android OS가 제공하는 앱별 전면 사용시간을 단일 기준으로 사용합니다.
        val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime).orEmpty()
        if (aggregatedStats.isEmpty()) return emptyList()
        val lateNightStats = if (endTime > startTime) {
            usageStatsManager.queryAndAggregateUsageStats(
                startTime,
                minOf(endTime, lateNightEndTime)
            ).orEmpty()
        } else {
            emptyMap()
        }

        val pm = context.packageManager
        val result = mutableListOf<AppUsage>()

        for ((pkgName, stat) in aggregatedStats) {
            val timeMillis = stat.totalTimeInForeground.coerceIn(0L, endTime - startTime)

            if (shouldIncludeUsagePackage(pkgName, timeMillis)) {
                val savedEntity = appWeightMap[pkgName]
                val appName = savedEntity?.appName ?: try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    pkgName
                }

                val category = resolveCategory(pm, pkgName, savedEntity)
                val lateNightTime = (lateNightStats[pkgName]?.totalTimeInForeground ?: 0L)
                    .coerceIn(0L, timeMillis)

                result.add(
                    AppUsage(
                        packageName = pkgName,
                        appName = appName,
                        usageTimeMillis = timeMillis,
                        categoryType = category,
                        lastTimeUsedMillis = stat.lastTimeUsed,
                        lateNightUsageMillis = lateNightTime
                    )
                )
            }
        }

        return result.sortedByDescending { it.usageTimeMillis }
    }

    /**
     * 오늘 0시부터 현재까지의 실제 기기 잠금 해제(Unlock) 횟수 조회
     * KEYGUARD_HIDDEN / SCREEN_INTERACTIVE 이벤트 기반 근사치입니다.
     */
    fun getTodayUnlockCount(context: Context): Int {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return 0
        val startTime = getStartOfTodayMillis()
        val endTime = System.currentTimeMillis()
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var unlockCount = 0

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            // KEYGUARD_HIDDEN (18) = 사용자가 잠금화면을 풀고 진입한 순간
            if (event.eventType == 18 /* KEYGUARD_HIDDEN */ || 
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN)) {
                unlockCount++
            }
        }

        // 만약 KEYGUARD_HIDDEN 이벤트를 지원하지 않는 기기인 경우 SCREEN_INTERACTIVE 이벤트로 대체
        if (unlockCount == 0) {
            val interactiveEvents = usageStatsManager.queryEvents(startTime, endTime)
            while (interactiveEvents.hasNextEvent()) {
                interactiveEvents.getNextEvent(event)
                if (event.eventType == 15 /* SCREEN_INTERACTIVE */ || 
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && event.eventType == UsageEvents.Event.SCREEN_INTERACTIVE)) {
                    unlockCount++
                }
            }
        }

        return unlockCount
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
                add(Calendar.DAY_OF_YEAR, 1)
            }

            val startTime = dayCalStart.timeInMillis
            val endTime = dayCalEnd.timeInMillis
            val dateString = sdf.format(dayCalStart.time)

            // 해당 일자의 집계 조회
            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
            val dayAppUsages = mutableListOf<AppUsage>()

            if (!aggregatedStats.isNullOrEmpty()) {
                for ((pkgName, stat) in aggregatedStats) {
                    // endTime은 UsageStatsManager 규약상 exclusive(다음 날 00:00)입니다.
                    val timeMillis = stat.totalTimeInForeground.coerceIn(0L, endTime - startTime)

                    if (shouldIncludeUsagePackage(pkgName, timeMillis)) {
                        val savedEntity = appWeightMap[pkgName]
                        val appName = savedEntity?.appName ?: try {
                            val appInfo = pm.getApplicationInfo(pkgName, 0)
                            pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            pkgName
                        }
                        val category = resolveCategory(pm, pkgName, savedEntity)
                        dayAppUsages.add(
                            AppUsage(
                                packageName = pkgName,
                                appName = appName,
                                usageTimeMillis = timeMillis,
                                categoryType = category,
                                lastTimeUsedMillis = stat.lastTimeUsed
                            )
                        )
                    }
                }
            }

            // 과거 해당 일자 언락 횟수 집계
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var pastUnlockCount = 0
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == 18 /* KEYGUARD_HIDDEN */ || event.eventType == 15 /* SCREEN_INTERACTIVE */) {
                    pastUnlockCount++
                }
            }

            var totalScreenMinutes = dayAppUsages.sumOf { it.usageTimeMinutes }
            // 하루 1440분(24시간) 초과 시 1440분으로 상한 클램핑
            if (totalScreenMinutes > 1440L) {
                totalScreenMinutes = 1440L
            }

            val distractingMinutes = dayAppUsages
                .filter { it.categoryType.isPenalty }
                .sumOf { it.usageTimeMinutes }
            val productiveMinutes = dayAppUsages
                .filter { it.categoryType.isBonus }
                .sumOf { it.usageTimeMinutes }

            // OS가 사용 기록을 반환하지 않은 날을 임의의 100점으로 만들지 않습니다.
            // 권한 부재, 제조사별 보존 기간, 실제 미사용을 구분할 수 없기 때문입니다.
            if (dayAppUsages.isEmpty() && pastUnlockCount == 0) {
                continue
            }

            val estimatedIdleMinutes = max(0L, 1440L - totalScreenMinutes)
            val finalUnlockCount = if (pastUnlockCount > 0) {
                pastUnlockCount
            } else if (totalScreenMinutes > 0) {
                (totalScreenMinutes / 12L).coerceIn(10L, 60L).toInt()
            } else {
                0
            }

            val scoreDetail = ScoreCalculator.calculateScore(
                appsUsage = dayAppUsages,
                idleMinutes = estimatedIdleMinutes,
                unlockCount = finalUnlockCount,
                rule = scoreRule
            )

            result.add(
                DailyScoreHistoryEntity(
                    dateString = dateString,
                    finalScore = scoreDetail.finalScore,
                    totalScreenTimeMinutes = totalScreenMinutes,
                    distractingTimeMinutes = distractingMinutes,
                    productiveTimeMinutes = productiveMinutes,
                    idleMinutes = estimatedIdleMinutes,
                    unlockCount = finalUnlockCount,
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

        return list
            .distinctBy { it.packageName }
            .sortedBy { it.appName }
    }
}
