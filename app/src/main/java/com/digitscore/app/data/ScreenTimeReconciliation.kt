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
    val yesterdayUsageMinutes: Int = 0,
    val launcherMinutes: Int,
    val splitScreenDeduplicatedMinutes: Int,
    val otherMinutes: Int,
    val differenceMinutes: Int,
    val isLateNightRange: Boolean = false
)

object ScreenTimeReconciliationCalculator {

    fun calculate(
        digitsCoreMillis: Long,
        osEstimatedMillis: Long,
        midnightToDawnMillis: Long,
        launcherMillis: Long,
        splitScreenDeduplicatedMillis: Long,
        yesterdayUsageMillis: Long = 0L,
        isLateNightRange: Boolean = false
    ): ScreenTimeReconciliation {
        val digitsCoreMins = (digitsCoreMillis / 60_000L).toInt().coerceAtLeast(0)
        val osMins = (osEstimatedMillis / 60_000L).toInt().coerceAtLeast(0)
        val dawnMins = (midnightToDawnMillis / 60_000L).toInt().coerceAtLeast(0)
        val launcherMins = (launcherMillis / 60_000L).toInt().coerceAtLeast(0)
        val splitMins = (splitScreenDeduplicatedMillis / 60_000L).toInt().coerceAtLeast(0)
        val yesterdayMins = (yesterdayUsageMillis / 60_000L).toInt().coerceAtLeast(0)

        val diffMins = osMins - digitsCoreMins
        val otherMins = if (isLateNightRange) {
            val accountedDiff = launcherMins + splitMins - yesterdayMins
            diffMins - accountedDiff
        } else {
            val accountedDiff = dawnMins + launcherMins + splitMins
            diffMins - accountedDiff
        }

        return ScreenTimeReconciliation(
            digitsCoreMinutes = digitsCoreMins,
            osEstimatedMinutes = osMins,
            midnightToDawnMinutes = dawnMins,
            yesterdayUsageMinutes = yesterdayMins,
            launcherMinutes = launcherMins,
            splitScreenDeduplicatedMinutes = splitMins,
            otherMinutes = otherMins,
            differenceMinutes = diffMins,
            isLateNightRange = isLateNightRange
        )
    }

    fun queryReconciliation(
        context: Context,
        digitsCoreScreenMillis: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): ScreenTimeReconciliation {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val midnightMillis = cal.timeInMillis
        val dawnMillis = midnightMillis + 5 * 3600_000L // 05:00 AM

        val eventResult = UsageStatsHelper.queryEventBasedUsage(context, midnightMillis, nowMillis)
        if (eventResult != null) {
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
            var todayDigitsCoreMillis = 0L

            for ((pkg, usage) in eventResult.usageMillisByPackage) {
                if (pkg !in UsageStatsHelper.TRANSIENT_SYSTEM_OVERLAY_PACKAGES) {
                    totalOsMillis += usage
                    if (pkg in launcherPackages) {
                        launcherMillis += usage
                    } else if (pkg !in UsageStatsHelper.IGNORED_SYSTEM_PACKAGES) {
                        todayDigitsCoreMillis += usage
                    }
                }
            }

            val splitDeduplicatedMillis = eventResult.segments
                .filter { it.concurrentAppCount > 1 }
                .sumOf { (it.concurrentAppCount - 1).toLong() * it.durationMillis }

            val isLateNight = nowMillis < dawnMillis
            val yesterdayUsageMillis = if (isLateNight) {
                (digitsCoreScreenMillis - todayDigitsCoreMillis).coerceAtLeast(0L)
            } else 0L

            val midnightToDawnMillis = if (!isLateNight) {
                eventResult.segments
                    .filter { it.startTimeMillis < dawnMillis && it.packageName !in launcherPackages && it.packageName !in UsageStatsHelper.IGNORED_SYSTEM_PACKAGES }
                    .sumOf { (minOf(it.endTimeMillis, dawnMillis) - it.startTimeMillis).coerceAtLeast(0L) }
            } else 0L

            return calculate(
                digitsCoreMillis = digitsCoreScreenMillis,
                osEstimatedMillis = totalOsMillis,
                midnightToDawnMillis = midnightToDawnMillis,
                launcherMillis = launcherMillis,
                splitScreenDeduplicatedMillis = splitDeduplicatedMillis,
                yesterdayUsageMillis = yesterdayUsageMillis,
                isLateNightRange = isLateNight
            )
        }

        return calculate(
            digitsCoreMillis = digitsCoreScreenMillis,
            osEstimatedMillis = digitsCoreScreenMillis,
            midnightToDawnMillis = 0L,
            launcherMillis = 0L,
            splitScreenDeduplicatedMillis = 0L
        )
    }
}
