package com.digitscore.app.ui.statistics

import com.digitscore.app.data.*
import com.digitscore.app.data.entity.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** 논리적 하루는 05:00에 시작합니다 (00:00~04:59는 전날로 집계). */
internal const val LOGICAL_DAY_START_HOUR = 5

internal fun logicalDate(time: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(time).atZone(zone).minusHours(LOGICAL_DAY_START_HOUR.toLong()).toLocalDate()

internal fun logicalDayStartMillis(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atTime(LOGICAL_DAY_START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()

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
    fun key(time: Long): Long = if (daily) logicalDayStartMillis(logicalDate(time, zone), zone) else statisticHour(time)
    val usesBy = usage.filter { it.packageName.isEmpty() }.groupBy { key(it.hour) }
    val scoresBy = scores.groupBy { key(it.hour) }
    val impactsBy = impacts.filter { it.packageName.isEmpty() }.groupBy { key(it.hour) }
    val historyBy = histories.associateBy { it.dateString }
    val result = mutableListOf<ExplorerBucket>()
    var cursor = key(start)
    while (cursor < end) {
        val next = if (daily) logicalDayStartMillis(logicalDate(cursor, zone).plusDays(1), zone) else cursor + STAT_HOUR
        val rows = usesBy[cursor].orEmpty()
        val allScores = scoresBy[cursor].orEmpty().sortedBy { it.lastAt }
        val model = allScores.lastOrNull()?.model
        val matching = allScores.filter { it.model == model }.sortedBy { it.firstAt }
        val history = if (daily) historyBy[logicalDate(cursor, zone).toString()] else null
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

/**
 * 기간 화면의 모든 '일 평균' 분모: 사용 또는 잠금 해제 기록이 있는 날 수(오늘 포함).
 * 측정하지 않은 날(설치 전·추적 중지)을 0분 사용으로 세지 않습니다.
 */
internal fun recordedDayCount(buckets: List<ExplorerBucket>): Int =
    buckets.count { it.usage != null || it.unlocks != null }.coerceAtLeast(1)

internal fun explorerWeekdayBuckets(
    dailyBuckets: List<ExplorerBucket>,
    zone: ZoneId = ZoneId.systemDefault()
): List<ExplorerBucket> {
    val byDayOfWeek = (1..7).associateWith { dayVal ->
        dailyBuckets.filter { b ->
            Instant.ofEpochMilli(b.start).atZone(zone).dayOfWeek.value == dayVal
        }
    }
    return (1..7).map { dayVal ->
        val days = byDayOfWeek[dayVal].orEmpty()
        val scoredDays = days.filter { it.score != null }
        val score = if (scoredDays.isNotEmpty()) {
            val model = scoredDays.last().score?.model ?: 5
            val avgFirst = scoredDays.mapNotNull { it.score?.first }.average()
            val avgLast = scoredDays.mapNotNull { it.score?.last }.average()
            val minLow = scoredDays.mapNotNull { it.score?.low }.minOrNull() ?: avgFirst
            val maxHigh = scoredDays.mapNotNull { it.score?.high }.maxOrNull() ?: avgLast
            ScoreHourEntity(
                hour = (dayVal - 1).toLong(),
                model = model,
                firstAt = (dayVal - 1).toLong(),
                lastAt = (dayVal - 1).toLong(),
                first = avgFirst,
                last = avgLast,
                low = minLow,
                high = maxHigh
            )
        } else null

        val usageDays = days.filter { it.usage != null }
        val avgUsage = if (usageDays.isNotEmpty()) {
            usageDays.mapNotNull { it.usage }.sum() / usageDays.size
        } else null

        val avgManaged = if (usageDays.isNotEmpty()) {
            usageDays.sumOf { it.managed } / usageDays.size
        } else 0L

        val unlockDays = days.filter { it.unlocks != null }
        val avgUnlocks = if (unlockDays.isNotEmpty()) {
            kotlin.math.round((unlockDays.mapNotNull { it.unlocks }.sum().toFloat() / unlockDays.size)).toInt()
        } else null

        val activeDays = days.filter { it.usage != null || it.unlocks != null || it.score != null }
        val avgShortOpens = if (activeDays.isNotEmpty()) {
            kotlin.math.round((activeDays.sumOf { it.shortOpens }.toFloat() / activeDays.size)).toInt()
        } else 0

        val lossDays = days.filter { it.loss != null }
        val avgLoss = if (lossDays.isNotEmpty()) {
            lossDays.mapNotNull { it.loss }.average()
        } else null

        val recoveryDays = days.filter { it.recovery != null }
        val avgRecovery = if (recoveryDays.isNotEmpty()) {
            days.mapNotNull { it.recovery }.average()
        } else null

        val weekdayIndex = (dayVal - 1).toLong() // 0 = Mon, 6 = Sun
        ExplorerBucket(
            start = weekdayIndex,
            end = weekdayIndex + 1L,
            score = score,
            usage = avgUsage,
            managed = avgManaged,
            opens = null,
            shortOpens = avgShortOpens,
            unlocks = avgUnlocks,
            loss = avgLoss,
            recovery = avgRecovery
        )
    }
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
        logicalDate(it.hour, zone).toString()
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
