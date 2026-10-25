package com.digitscore.app.data

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.util.Calendar

data class ScreenTimeReconciliation(
    val digitsCoreMinutes: Int,
    val osEstimatedMinutes: Int,
    val midnightToDawnMinutes: Int,
    val launcherMinutes: Int,
    val splitScreenDeduplicatedMinutes: Int,
    val otherMinutes: Int,
    val differenceMinutes: Int
)

object ScreenTimeReconciliationCalculator {

    fun calculate(
        digitsCoreMillis: Long,
        osEstimatedMillis: Long,
        midnightToDawnMillis: Long,
        launcherMillis: Long,
        splitScreenDeduplicatedMillis: Long
    ): ScreenTimeReconciliation {
        val digitsCoreMins = (digitsCoreMillis / 60_000L).toInt().coerceAtLeast(0)
        val osMins = (osEstimatedMillis / 60_000L).toInt().coerceAtLeast(0)
        val dawnMins = (midnightToDawnMillis / 60_000L).toInt().coerceAtLeast(0)
        val launcherMins = (launcherMillis / 60_000L).toInt().coerceAtLeast(0)
        val splitMins = (splitScreenDeduplicatedMillis / 60_000L).toInt().coerceAtLeast(0)

        val diffMins = osMins - digitsCoreMins
        val accountedDiff = dawnMins + launcherMins + splitMins
        val otherMins = diffMins - accountedDiff

        return ScreenTimeReconciliation(
            digitsCoreMinutes = digitsCoreMins,
            osEstimatedMinutes = osMins,
            midnightToDawnMinutes = dawnMins,
            launcherMinutes = launcherMins,
            splitScreenDeduplicatedMinutes = splitMins,
            otherMinutes = otherMins,
            differenceMinutes = diffMins
        )
    }

    fun queryReconciliation(
        context: Context,
        digitsCoreScreenMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): ScreenTimeReconciliation {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return calculate(digitsCoreScreenMillis, digitsCoreScreenMillis, 0L, 0L, 0L)

        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnightMillis = cal.timeInMillis
        val dawnMillis = midnightMillis + 5 * 3600_000L // 05:00 AM

        val launcherPackages = mutableSetOf<String>().apply {
            addAll(UsageStatsHelper.HOME_LAUNCHER_PACKAGES)
            runCatching {
                val pm = context.packageManager
                val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                val resolveInfos = pm.queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
                resolveInfos.forEach { add(it.activityInfo.packageName) }
            }
        }

        var totalOsMillis = 0L
        var launcherMillis = 0L

        runCatching {
            val stats = usageStatsManager.queryAndAggregateUsageStats(midnightMillis, nowMillis)
            stats.forEach { (pkg, usage) ->
                if (pkg !in UsageStatsHelper.TRANSIENT_SYSTEM_OVERLAY_PACKAGES) {
                    val time = usage.totalTimeInForeground
                    totalOsMillis += time
                    if (pkg in launcherPackages) {
                        launcherMillis += time
                    }
                }
            }
        }

        var midnightToDawnMillis = 0L
        if (nowMillis > midnightMillis) {
            val dawnQueryEnd = minOf(nowMillis, dawnMillis)
            runCatching {
                val dawnStats = usageStatsManager.queryAndAggregateUsageStats(midnightMillis, dawnQueryEnd)
                dawnStats.forEach { (pkg, usage) ->
                    if (pkg !in UsageStatsHelper.TRANSIENT_SYSTEM_OVERLAY_PACKAGES && pkg !in launcherPackages) {
                        midnightToDawnMillis += usage.totalTimeInForeground
                    }
                }
            }
        }

        return calculate(
            digitsCoreMillis = digitsCoreScreenMillis,
            osEstimatedMillis = totalOsMillis,
            midnightToDawnMillis = midnightToDawnMillis,
            launcherMillis = launcherMillis,
            splitScreenDeduplicatedMillis = 0L
        )
    }
}
