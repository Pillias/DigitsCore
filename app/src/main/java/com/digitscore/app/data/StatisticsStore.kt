package com.digitscore.app.data

import androidx.room.withTransaction
import com.digitscore.app.data.entity.*
import com.digitscore.app.engine.ScoreMovement

/** All archive maintenance completes before raw records can be pruned. */
object StatisticsStore {
    const val RETENTION = 365L * 24 * STAT_HOUR

    suspend fun recordMovements(db: DigitsDatabase, movements: List<ScoreMovement>) {
        val dao = db.statisticsDao()
        val scores = linkedMapOf<Long, ScoreHourEntity>()
        val impacts = linkedMapOf<Pair<Long, String>, ScoreImpactHourEntity>()
        for (m in movements) {
            val hour = statisticHour(m.start)
            val last = maxOf(m.start, m.end - 1)
            val point = ScoreHourEntity(hour, 5, m.start, last, m.before, m.after,
                minOf(m.before, m.after), maxOf(m.before, m.after))
            scores[hour] = mergeScoreHours(scores[hour] ?: dao.score(hour, 5), point)
            val loss = (m.before - m.after).coerceAtLeast(0.0)
            val gain = (m.after - m.before).coerceAtLeast(0.0)
            for (pkg in listOf("") + listOf(m.packageName).filter { it.isNotBlank() && loss > 0 }) {
                val key = hour to pkg
                val old = impacts[key] ?: dao.impact(hour, pkg)
                    ?: ScoreImpactHourEntity(hour, pkg, firstAt = m.start, lastAt = last)
                impacts[key] = old.copy(
                    loss = old.loss + loss,
                    recovery = old.recovery + if (pkg.isEmpty()) gain else 0.0,
                    observedMillis = old.observedMillis + if (pkg.isEmpty()) m.end - m.start else 0,
                    firstAt = minOf(old.firstAt, m.start), lastAt = maxOf(old.lastAt, last)
                )
            }
        }
        dao.putScores(scores.values.toList())
        dao.putImpacts(impacts.values.toList())
    }

    suspend fun refresh(db: DigitsDatabase, now: Long) = db.withTransaction {
        val dao = db.statisticsDao()
        val state = dao.state()
        val coverageStart = state?.interactionCoverageStart ?: (now - 24 * STAT_HOUR)
        var from = statisticHour(maxOf(now - 30L * 24 * STAT_HOUR, (state?.processedUntil ?: 0L) - 2 * 60_000L))
        val existing = dao.deviceUsageSince(from).associateBy { it.hour }
        while (from < now) {
            val until = minOf(from + 24 * STAT_HOUR, now)
            val sessions = db.foregroundUsageSessionDao().getBetween(from - 60_000L, until + 2 * 60_000L)
            val records = aggregateUsageHours(sessions, from, until, now - 2 * 60_000L)
                .associateBy { it.hour to it.packageName }.toMutableMap()
            val eventStart = maxOf(coverageStart, now - 24 * STAT_HOUR)
            val events = db.deviceInteractionEventDao().getBetween(maxOf(from - 15_000L, eventStart), until)
            val unlocks = UsageStatsHelper.resolvedUnlockTimestamps(events).groupingBy { statisticHour(it) }.eachCount()
            val notifications = events.filter { it.eventType == DeviceInteractionEventEntity.NOTIFICATION_INTERRUPTION }
                .groupingBy { statisticHour(it.timestampMillis) }.eachCount()
            var hour = from
            while (hour < until) {
                val key = hour to ""
                val prior = existing[hour]
                val observed = hour >= eventStart
                if (observed || prior != null || records.containsKey(key)) {
                    records[key] = (records[key] ?: UsageHourEntity(hour, "", "")).copy(
                        unlocks = if (observed) unlocks[hour] ?: 0 else prior?.unlocks,
                        notifications = if (observed) notifications[hour] ?: 0 else prior?.notifications
                    )
                }
                hour += STAT_HOUR
            }
            dao.deleteUsageRange(from, statisticHour(until - 1) + STAT_HOUR)
            dao.putUsage(records.values.toList())
            from = until
        }
        if (state == null) {
            // Existing saved samples support a one-time OHLC backfill; impacts remain unknown.
            db.coreIndexSampleDao().getAll().groupBy { statisticHour(it.timestampMillis) to it.scoreModelVersion }
                .forEach { (key, samples) ->
                    val ordered = samples.sortedBy { it.timestampMillis }
                    val row = ScoreHourEntity(key.first, key.second, ordered.first().timestampMillis,
                        ordered.last().timestampMillis, ordered.first().exactScore, ordered.last().exactScore,
                        ordered.minOf { it.exactScore }, ordered.maxOf { it.exactScore })
                    dao.putScores(listOf(mergeScoreHours(dao.score(key.first, key.second), row)))
                }
        }
        dao.putState(StatisticsStateEntity(processedUntil = now, interactionCoverageStart = coverageStart))
        val cutoff = statisticHour(now - RETENTION)
        dao.pruneUsage(cutoff)
        dao.pruneScores(cutoff)
        dao.pruneImpacts(cutoff)
    }
}
