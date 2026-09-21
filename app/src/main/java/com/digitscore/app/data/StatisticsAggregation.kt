package com.digitscore.app.data

import com.digitscore.app.data.entity.*

const val STAT_HOUR = 3_600_000L
fun statisticHour(time: Long): Long = time - Math.floorMod(time, STAT_HOUR)

/** Splits intervals at real hour boundaries. A session's opening belongs only to its original hour. */
internal fun aggregateUsageHours(
    sessions: List<ForegroundUsageSessionEntity>, start: Long, end: Long, settledThrough: Long
): List<UsageHourEntity> {
    val rows = linkedMapOf<Pair<Long, String>, UsageHourEntity>()
    fun update(hour: Long, pkg: String, name: String, change: (UsageHourEntity) -> UsageHourEntity) {
        val key = hour to pkg
        rows[key] = change(rows[key] ?: UsageHourEntity(hour, pkg, name))
    }
    val usable = sessions.filter { it.endTimeMillis > start && it.startTimeMillis < end }
    // Sweep concurrent fragments once. The persisted primary owner receives the screen time.
    val boundaries = sortedMapOf<Long, MutableList<Pair<Int, Boolean>>>()
    usable.forEachIndexed { index, s ->
        boundaries.getOrPut(maxOf(start, s.startTimeMillis)) { mutableListOf() }.add(index to true)
        boundaries.getOrPut(minOf(end, s.endTimeMillis)) { mutableListOf() }.add(index to false)
    }
    val active = linkedSetOf<Int>()
    val times = boundaries.keys.toList()
    times.forEachIndexed { index, time ->
        boundaries.getValue(time).forEach { (id, entering) -> if (entering) active.add(id) else active.remove(id) }
        val next = times.getOrNull(index + 1) ?: return@forEachIndexed
        val owner = active.map { usable[it] }.maxWithOrNull(compareBy<ForegroundUsageSessionEntity> {
            it.effectiveCategoryLevel
        }.thenBy { it.startTimeMillis }) ?: return@forEachIndexed
        var cursor = time
        while (cursor < next) {
            val hour = statisticHour(cursor)
            val stop = minOf(next, hour + STAT_HOUR)
            val duration = stop - cursor
            val managed = if (owner.effectiveCategoryLevel >= 3) duration else 0L
            for ((pkg, name) in listOf("" to "", owner.packageName to owner.appName)) {
                update(hour, pkg, name) { it.copy(usageMillis = it.usageMillis + duration, managedMillis = it.managedMillis + managed) }
            }
            cursor = stop
        }
    }
    sessions.groupBy { it.packageName to it.sessionStartTimeMillis }.forEach { (key, parts) ->
        val opened = key.second
        if (opened < start || opened >= end) return@forEach
        val ordered = parts.sortedBy { it.startTimeMillis }
        var lastEnd = Long.MIN_VALUE
        var duration = 0L
        ordered.forEach {
            duration += (it.endTimeMillis - maxOf(lastEnd, it.startTimeMillis)).coerceAtLeast(0L)
            lastEnd = maxOf(lastEnd, it.endTimeMillis)
        }
        val short = if (duration <= 60_000 && lastEnd - opened <= 60_000 && opened + 60_000 <= settledThrough) 1 else 0
        for ((pkg, name) in listOf("" to "", key.first to parts.first().appName)) {
            update(statisticHour(opened), pkg, name) { it.copy(opens = it.opens + 1, shortOpens = it.shortOpens + short) }
        }
    }
    return rows.values.toList()
}

internal fun mergeScoreHours(a: ScoreHourEntity?, b: ScoreHourEntity): ScoreHourEntity {
    if (a == null) return b
    require(a.hour == b.hour && a.model == b.model)
    return a.copy(
        firstAt = minOf(a.firstAt, b.firstAt), lastAt = maxOf(a.lastAt, b.lastAt),
        first = if (b.firstAt < a.firstAt) b.first else a.first,
        last = if (b.lastAt >= a.lastAt) b.last else a.last,
        low = minOf(a.low, b.low), high = maxOf(a.high, b.high)
    )
}
