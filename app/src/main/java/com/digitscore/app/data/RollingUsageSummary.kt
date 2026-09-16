package com.digitscore.app.data

import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage

data class RollingUsageSummary(
    val appsUsage: List<AppUsage>,
    val totalScreenTimeMillis: Long,
    val managedTimeMillis: Long,
    val growthTimeMillis: Long
)

/** 저장된 전면 세션을 자정과 무관한 [startMillis, endMillis) 구간으로 잘라 표시용 집계를 만듭니다. */
fun summarizeRollingUsage(
    records: List<ForegroundUsageSessionEntity>,
    startMillis: Long,
    endMillis: Long,
    appWeightMap: Map<String, AppWeightEntity> = emptyMap()
): RollingUsageSummary {
    if (endMillis <= startMillis) {
        return RollingUsageSummary(emptyList(), 0L, 0L, 0L)
    }

    data class ClippedRecord(
        val source: ForegroundUsageSessionEntity,
        val start: Long,
        val end: Long
    ) {
        val duration: Long get() = (end - start).coerceAtLeast(0L)
    }

    val clipped = records.mapNotNull { record ->
        val start = maxOf(record.startTimeMillis, startMillis)
        val end = minOf(record.endTimeMillis, endMillis)
        if (end <= start) null else ClippedRecord(record, start, end)
    }
    val apps = clipped.groupBy { it.source.packageName }.map { (packageName, appRecords) ->
        val latest = appRecords.maxBy { it.end }.source
        val openedInWindow = appRecords.filter {
            it.source.sessionStartTimeMillis in startMillis until endMillis
        }
        val sessionDurations = openedInWindow
            .groupBy { it.source.sessionStartTimeMillis }
            .values
            .map { fragments -> fragments.sumOf { it.duration } }
        val storedCategory = appWeightMap[packageName]?.categoryType?.canonical
            ?: when (AppCategoryType.normalizeLevel(latest.categoryLevel)) {
                1 -> AppCategoryType.PRODUCTIVE
                3 -> AppCategoryType.DISTRACTING
                else -> AppCategoryType.NEUTRAL
            }
        AppUsage(
            packageName = packageName,
            appName = appWeightMap[packageName]?.appName ?: latest.appName,
            usageTimeMillis = appRecords.sumOf { it.duration },
            categoryType = storedCategory,
            lastTimeUsedMillis = appRecords.maxOf { it.end },
            lateNightUsageMillis = appRecords.filter { it.source.isLateNight }.sumOf { it.duration },
            sessionCount = sessionDurations.size,
            shortSessionCount = sessionDurations.count { it in 1 until 60_000L }
        )
    }.sortedByDescending { it.usageTimeMillis }

    return RollingUsageSummary(
        appsUsage = apps,
        totalScreenTimeMillis = clipped.sumOf { it.duration },
        managedTimeMillis = clipped.filter { it.source.effectiveCategoryLevel >= 3 }
            .sumOf { it.duration },
        growthTimeMillis = clipped.filter { it.source.effectiveCategoryLevel <= 1 }
            .sumOf { it.duration }
    )
}
