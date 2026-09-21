package com.digitscore.app.ui.statistics

import com.digitscore.app.data.*
import com.digitscore.app.data.entity.*
import java.time.Instant
import java.time.ZoneId

internal data class ExplorerBucket(
    val start: Long, val end: Long, val score: ScoreHourEntity?,
    val usage: Long?, val managed: Long, val opens: Int?, val shortOpens: Int,
    val unlocks: Int?, val loss: Double?, val recovery: Double?
)

internal fun explorerBuckets(
    start: Long, end: Long, daily: Boolean, usage: List<UsageHourEntity>,
    scores: List<ScoreHourEntity>, impacts: List<ScoreImpactHourEntity>,
    histories: List<DailyScoreHistoryEntity> = emptyList(), zone: ZoneId = ZoneId.systemDefault()
): List<ExplorerBucket> {
    if (end <= start) return emptyList()
    fun key(time: Long): Long = if (daily) Instant.ofEpochMilli(time).atZone(zone).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli() else statisticHour(time)
    val usesBy = usage.filter { it.packageName.isEmpty() }.groupBy { key(it.hour) }
    val scoresBy = scores.groupBy { key(it.hour) }
    val impactsBy = impacts.filter { it.packageName.isEmpty() }.groupBy { key(it.hour) }
    val historyBy = histories.associateBy { it.dateString }
    val result = mutableListOf<ExplorerBucket>()
    var cursor = key(start)
    while (cursor < end) {
        val next = if (daily) Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate().plusDays(1)
            .atStartOfDay(zone).toInstant().toEpochMilli() else cursor + STAT_HOUR
        val rows = usesBy[cursor].orEmpty()
        val allScores = scoresBy[cursor].orEmpty().sortedBy { it.lastAt }
        val model = allScores.lastOrNull()?.model
        val matching = allScores.filter { it.model == model }.sortedBy { it.firstAt }
        val history = if (daily) historyBy[Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate().toString()] else null
        val score = matching.takeIf { it.isNotEmpty() }?.let {
            ScoreHourEntity(cursor, it.first().model, it.first().firstAt, it.last().lastAt,
                it.first().first, it.last().last, it.minOf { r -> r.low }, it.maxOf { r -> r.high })
        } ?: history?.takeIf { it.scoreModelVersion >= 2 }?.let {
            // A daily-only legacy reading is a point, never an invented OHLC range.
            ScoreHourEntity(cursor, it.scoreModelVersion, cursor, cursor, it.finalScore.toDouble(),
                it.finalScore.toDouble(), it.finalScore.toDouble(), it.finalScore.toDouble())
        }
        val impact = impactsBy[cursor].orEmpty()
        val known = rows.isNotEmpty() || impact.any { it.observedMillis > 0 }
        result.add(ExplorerBucket(maxOf(start, cursor), minOf(end, next), score,
            if (known) rows.sumOf { it.usageMillis } else history?.totalScreenTimeMinutes?.times(60_000),
            if (known) rows.sumOf { it.managedMillis } else (history?.distractingTimeMinutes ?: 0) * 60_000,
            if (known) rows.sumOf { it.opens } else null, rows.sumOf { it.shortOpens },
            rows.mapNotNull { it.unlocks }.takeIf { it.isNotEmpty() }?.sum() ?: history?.unlockCount,
            impact.takeIf { it.isNotEmpty() }?.sumOf { it.loss },
            impact.takeIf { it.isNotEmpty() }?.sumOf { it.recovery }))
        cursor = next
    }
    return result
}

internal data class ExplorerApp(
    val pkg: String, val name: String, val usage: Long, val opens: Int, val shortOpens: Int,
    val loss: Double?, val impactFrom: Long?, val impactUntil: Long?
)

internal fun explorerApps(
    hours: List<UsageHourEntity>, impacts: List<ScoreImpactHourEntity>,
    daily: List<DailyAppUsageEntity>, useDailyFallback: Boolean
): List<ExplorerApp> {
    val hourApps = hours.filter { it.packageName.isNotEmpty() }.groupBy { it.packageName }
    val lossApps = impacts.filter { it.packageName.isNotEmpty() }.groupBy { it.packageName }
    val days = daily.groupBy { it.packageName }
    // For long periods, daily rows supply historical dates whose hourly archive did not exist yet.
    val zone = ZoneId.systemDefault()
    val knownDates = hours.filter { it.packageName.isEmpty() }.map {
        Instant.ofEpochMilli(it.hour).atZone(zone).toLocalDate().toString()
    }.toSet()
    return (hourApps.keys + lossApps.keys + if (useDailyFallback) days.keys else emptySet()).map { pkg ->
        val rows = hourApps[pkg].orEmpty()
        val fallback = if (useDailyFallback) days[pkg].orEmpty().filter { it.dateString !in knownDates } else emptyList()
        val loss = lossApps[pkg].orEmpty()
        ExplorerApp(pkg, rows.lastOrNull()?.appName ?: fallback.lastOrNull()?.appName ?: pkg,
            rows.sumOf { it.usageMillis } + fallback.sumOf { it.usageMillis },
            rows.sumOf { it.opens } + fallback.sumOf { it.sessionCount },
            rows.sumOf { it.shortOpens } + fallback.sumOf { it.shortSessionCount },
            loss.takeIf { it.isNotEmpty() }?.sumOf { it.loss }, loss.minOfOrNull { it.firstAt }, loss.maxOfOrNull { it.lastAt })
    }
}
