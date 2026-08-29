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
import java.util.Calendar

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
