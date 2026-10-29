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

    /** 복원 중 고치거나 건너뛴 행 수. 기기에서 만들어진 백업에 어긋난 행이 있어도 전체 복원을 막지 않습니다. */
    data class RestoreReport(var clamped: Int = 0, var skipped: Int = 0)

    suspend fun restore(db: DigitsDatabase, obj: JSONObject, now: Long): RestoreReport {
        require(obj.getInt("version") == 1)
        val report = RestoreReport()
        fun rows(key: String): List<JSONArray> {
            val array = obj.optJSONArray(key) ?: return emptyList()
            require(array.length() <= 300_000) { "Too many hourly records" }
            return (0 until array.length()).mapNotNull { array.optJSONArray(it) }
        }
        fun validHour(h: Long): Boolean = h >= 0 && h == statisticHour(h) && h <= now + STAT_HOUR
        fun clampLong(v: Long, max: Long): Long = v.coerceIn(0L, max).also { if (it != v) report.clamped++ }
        fun clampInt(v: Int, max: Int): Int = v.coerceIn(0, max).also { if (it != v) report.clamped++ }
        // 잘못된 행 하나 때문에 전체 복원이 실패하지 않도록, 행 단위로 고치거나 건너뜁니다.
        // 구조가 깨진 행(열 수·타입·시간 범위)은 건너뛰고, 값만 어긋난 행은 범위 안으로 맞춥니다.
        fun <T> safeRow(block: () -> T?): T? = runCatching(block).getOrNull().also { if (it == null) report.skipped++ }

        val usage = rows("usage").mapNotNull { r -> safeRow {
            if (r.length() != 9) return@safeRow null
            val hour = r.getLong(0); if (!validHour(hour)) return@safeRow null
            val pkg = r.getString(1); val name = r.getString(2)
            if (pkg.length > 300 || name.length > 500) return@safeRow null
            val usageMs = clampLong(r.getLong(3), STAT_HOUR)
            val managedMs = clampLong(r.getLong(4), usageMs)
            val opens = clampInt(r.getInt(5), 1_000_000)
            val shortOpens = clampInt(r.getInt(6), opens)
            UsageHourEntity(hour, pkg, name, usageMs, managedMs, opens, shortOpens,
                if (r.isNull(7)) null else clampInt(r.getInt(7), 1_000_000),
                if (r.isNull(8)) null else clampInt(r.getInt(8), 1_000_000))
        } }
        val scores = rows("scores").mapNotNull { r -> safeRow {
            if (r.length() != 8) return@safeRow null
            val hour = r.getLong(0); if (!validHour(hour)) return@safeRow null
            val model = r.getInt(1)
            if (model !in 1..com.digitscore.app.engine.ScoreModel.CURRENT_STORED) return@safeRow null
            val values = (4..7).map { r.getDouble(it) }
            if (values.any { !it.isFinite() || it < 0.0 }) return@safeRow null
            val first = values[0].coerceAtMost(100.0); val last = values[1].coerceAtMost(100.0)
            val low = minOf(values[2], first, last); val high = maxOf(values[3], first, last).coerceAtMost(100.0)
            if (low != values[2] || high != values[3]) report.clamped++
            val firstAt = r.getLong(2).coerceIn(hour, hour + STAT_HOUR - 1)
            val lastAt = r.getLong(3).coerceIn(firstAt, hour + STAT_HOUR - 1)
            ScoreHourEntity(hour, model, firstAt, lastAt, first, last, low, high)
        } }
        val impacts = rows("impacts").mapNotNull { r -> safeRow {
            if (r.length() != 7) return@safeRow null
            val hour = r.getLong(0); if (!validHour(hour)) return@safeRow null
            val pkg = r.getString(1); if (pkg.length > 300) return@safeRow null
            val loss = r.getDouble(2); val recovery = r.getDouble(3)
            if (!loss.isFinite() || !recovery.isFinite() || loss < 0.0 || recovery < 0.0) return@safeRow null
            val firstAt = r.getLong(5).coerceIn(hour, hour + STAT_HOUR - 1)
            val lastAt = r.getLong(6).coerceIn(firstAt, hour + STAT_HOUR - 1)
            ScoreImpactHourEntity(hour, pkg, loss, recovery, clampLong(r.getLong(4), STAT_HOUR), firstAt, lastAt)
        } }
        if (report.clamped > 0 || report.skipped > 0) {
            android.util.Log.w("StatisticsBackup", "restore adjusted rows: clamped=${report.clamped} skipped=${report.skipped}")
        }
        val dao = db.statisticsDao()
        dao.restoreUsage(usage)
        dao.restoreScores(scores)
        dao.restoreImpacts(impacts)
        if (dao.state() == null && usage.isNotEmpty()) {
            dao.putState(StatisticsStateEntity(processedUntil = now - STAT_HOUR, interactionCoverageStart = now))
        }
        return report
    }
}
