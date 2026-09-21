package com.digitscore.app.data.backup

import com.digitscore.app.data.*
import com.digitscore.app.data.entity.*
import org.json.JSONArray
import org.json.JSONObject

/** Compact, versioned rows avoid repeating column names in a year of hourly history. */
internal object StatisticsBackup {
    suspend fun export(db: DigitsDatabase): JSONObject {
        val dao = db.statisticsDao()
        return JSONObject().apply {
            put("version", 1)
            put("usage", JSONArray().apply { dao.allUsage().forEach {
                put(JSONArray(listOf(it.hour, it.packageName, it.appName, it.usageMillis, it.managedMillis,
                    it.opens, it.shortOpens, it.unlocks ?: JSONObject.NULL, it.notifications ?: JSONObject.NULL)))
            } })
            put("scores", JSONArray().apply { dao.allScores().forEach {
                put(JSONArray(listOf(it.hour, it.model, it.firstAt, it.lastAt, it.first, it.last, it.low, it.high)))
            } })
            put("impacts", JSONArray().apply { dao.allImpacts().forEach {
                put(JSONArray(listOf(it.hour, it.packageName, it.loss, it.recovery, it.observedMillis, it.firstAt, it.lastAt)))
            } })
        }
    }

    suspend fun restore(db: DigitsDatabase, obj: JSONObject, now: Long) {
        require(obj.getInt("version") == 1)
        fun rows(key: String): List<JSONArray> {
            val array = obj.getJSONArray(key)
            require(array.length() <= 300_000) { "Too many hourly records" }
            return (0 until array.length()).map { array.getJSONArray(it) }
        }
        fun hour(row: JSONArray): Long = row.getLong(0).also {
            require(it >= 0 && it == statisticHour(it) && it <= now + STAT_HOUR) { "Invalid archive hour" }
        }
        fun count(row: JSONArray, index: Int): Int = row.getInt(index).also { require(it in 0..1_000_000) }
        fun duration(row: JSONArray, index: Int): Long = row.getLong(index).also { require(it in 0..STAT_HOUR) }
        fun finite(row: JSONArray, index: Int): Double = row.getDouble(index).also { require(it.isFinite() && it >= 0.0) }
        val usage = rows("usage").map { r ->
            require(r.length() == 9)
            UsageHourEntity(hour(r), r.getString(1).also { require(it.length <= 300) },
                r.getString(2).also { require(it.length <= 500) }, duration(r, 3), duration(r, 4),
                count(r, 5), count(r, 6), if (r.isNull(7)) null else count(r, 7),
                if (r.isNull(8)) null else count(r, 8)).also {
                    require(it.managedMillis <= it.usageMillis && it.shortOpens <= it.opens)
                }
        }
        val scores = rows("scores").map { r ->
            require(r.length() == 8)
            ScoreHourEntity(hour(r), r.getInt(1), r.getLong(2), r.getLong(3),
                finite(r, 4), finite(r, 5), finite(r, 6), finite(r, 7)).also {
                require(it.model in 1..5 && it.firstAt >= it.hour && it.lastAt >= it.firstAt && it.lastAt < it.hour + STAT_HOUR)
                require(it.low <= minOf(it.first, it.last) && it.high >= maxOf(it.first, it.last) && it.high <= 100)
            }
        }
        val impacts = rows("impacts").map { r ->
            require(r.length() == 7)
            ScoreImpactHourEntity(hour(r), r.getString(1).also { require(it.length <= 300) },
                finite(r, 2), finite(r, 3), duration(r, 4), r.getLong(5), r.getLong(6)).also {
                require(it.firstAt >= it.hour && it.lastAt >= it.firstAt && it.lastAt < it.hour + STAT_HOUR)
            }
        }
        val dao = db.statisticsDao()
        dao.restoreUsage(usage)
        dao.restoreScores(scores)
        dao.restoreImpacts(impacts)
        if (dao.state() == null && usage.isNotEmpty()) {
            dao.putState(StatisticsStateEntity(processedUntil = now - STAT_HOUR, interactionCoverageStart = now))
        }
    }
}
