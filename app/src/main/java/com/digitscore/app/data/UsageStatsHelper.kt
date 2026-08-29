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
     * 오늘 0시부터 현재까지의 앱별 사용 시간 및 정보 집계
     */
    fun getTodayAppUsageStats(
        context: Context,
        appWeightMap: Map<String, AppWeightEntity>
    ): List<AppUsage> {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyList()

        val startTime = getStartOfTodayMillis()
        val endTime = System.currentTimeMillis()

        // 1. 오늘 전체 사용량 통계 조회
        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return emptyList()
        }

        // 2. 심야 시간(00:00 ~ 05:00) 구간 사용량 통계 조회
        val lateNightEndTime = startTime + (5 * 60 * 60 * 1000L) // 오늘 새벽 5시
        val lateNightStatsList = if (endTime > startTime) {
            usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                kotlin.math.min(endTime, lateNightEndTime)
            )
        } else {
            null
        }
        val lateNightMap = lateNightStatsList?.associate { it.packageName to it.totalTimeInForeground } ?: emptyMap()

        val pm = context.packageManager
        val result = mutableListOf<AppUsage>()

        for (stat in usageStatsList) {
            val totalTimeInForeground = stat.totalTimeInForeground
            // 사용 시간이 10초 이상인 앱만 유의미하게 처리
            if (totalTimeInForeground > 10_000L) {
                val pkgName = stat.packageName
                val savedEntity = appWeightMap[pkgName]

                val appName = savedEntity?.appName ?: try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    pkgName
                }

                val category = savedEntity?.categoryType ?: AppCategoryType.NEUTRAL
                val lateNightTime = lateNightMap[pkgName] ?: 0L

                result.add(
                    AppUsage(
                        packageName = pkgName,
                        appName = appName,
                        usageTimeMillis = totalTimeInForeground,
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

        // 과거 days일 전부터 어제(1일 전)까지 일별 순회
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

            val usageStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStatsList.isNullOrEmpty()) continue

            // 심야(00:00~05:00) 사용량
            val lateNightEndTime = startTime + (5 * 60 * 60 * 1000L)
            val lateNightStatsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                lateNightEndTime
            )
            val lateNightMap = lateNightStatsList?.associate { it.packageName to it.totalTimeInForeground } ?: emptyMap()

            val dayAppUsages = mutableListOf<AppUsage>()
            var totalScreenMillis = 0L

            for (stat in usageStatsList) {
                val fgTime = stat.totalTimeInForeground
                if (fgTime > 10_000L) {
                    totalScreenMillis += fgTime
                    val pkgName = stat.packageName
                    val savedEntity = appWeightMap[pkgName]

                    val appName = savedEntity?.appName ?: try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        pkgName
                    }

                    val category = savedEntity?.categoryType ?: AppCategoryType.NEUTRAL
                    val lateNightTime = lateNightMap[pkgName] ?: 0L

                    dayAppUsages.add(
                        AppUsage(
                            packageName = pkgName,
                            appName = appName,
                            usageTimeMillis = fgTime,
                            categoryType = category,
                            lastTimeUsedMillis = stat.lastTimeUsed,
                            lateNightUsageMillis = lateNightTime
                        )
                    )
                }
            }

            if (dayAppUsages.isEmpty()) continue

            val totalScreenMinutes = totalScreenMillis / (60 * 1000L)
            val distractingMinutes = dayAppUsages
                .filter { it.categoryType == AppCategoryType.DISTRACTING }
                .sumOf { it.usageTimeMinutes }
            val productiveMinutes = dayAppUsages
                .filter { it.categoryType == AppCategoryType.PRODUCTIVE }
                .sumOf { it.usageTimeMinutes }

            // 언락 횟수 및 Idle 시간 추정치 (하루 1440분 기준)
            val estimatedIdleMinutes = max(0L, 1440L - totalScreenMinutes)
            val estimatedUnlockCount = (totalScreenMinutes / 12L).coerceIn(10L, 60L).toInt()

            val scoreDetail = ScoreCalculator.calculateScore(
                appsUsage = dayAppUsages,
                idleMinutes = estimatedIdleMinutes,
                unlockCount = estimatedUnlockCount,
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
                    unlockCount = estimatedUnlockCount,
                    lastUpdatedTimestamp = endTime
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
