package com.digitscore.app.data.backup

import android.content.Context
import com.digitscore.app.data.entity.DailyAppUsageEntity
import org.json.JSONArray
import org.json.JSONObject

/** 백업 복원 방식. */
enum class RestoreMode {
    /** 백업을 우선합니다. 이 기기의 사용 기록을 지우고 백업 기록으로 바꿉니다. */
    OVERWRITE,
    /** 이 기기 기록을 남기고 백업 기록을 더합니다(겹치는 구간은 섞일 수 있음). */
    MERGE
}

/**
 * 덮어쓰기 복원을 한 날에는 복원 전까지의 오늘 앱 사용(백업 값)을 기준값으로 남겨 둡니다.
 * 추적 서비스는 오늘 집계를 기기 이벤트로 다시 만드는데, 이 기준값이 없으면
 * 복원한 오늘 기록이 새 기기 이벤트로 덮어써집니다.
 */
object RestoreBaseline {
    private const val PREFS = "restore_baseline"
    private const val KEY_DATE = "date"
    private const val KEY_BOUNDARY = "boundary_millis"
    private const val KEY_ROWS = "rows_json"

    data class Baseline(val date: String, val boundaryMillis: Long, val rows: List<DailyAppUsageEntity>)

    fun save(context: Context, date: String, boundaryMillis: Long, rows: List<DailyAppUsageEntity>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_DATE, date)
            .putLong(KEY_BOUNDARY, boundaryMillis)
            .putString(KEY_ROWS, encode(rows))
            .commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** 오늘(논리 날짜)에 해당하는 기준값만 돌려주고, 지난 날짜 값은 지웁니다. */
    fun forDate(context: Context, date: String): Baseline? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stored = prefs.getString(KEY_DATE, null) ?: return null
        if (stored != date) {
            prefs.edit().clear().apply()
            return null
        }
        return Baseline(stored, prefs.getLong(KEY_BOUNDARY, 0L), decode(prefs.getString(KEY_ROWS, null)))
    }

    /** 복원 전 오늘 값(백업)과 복원 뒤 이 기기에서 쌓인 값을 앱별로 더합니다. */
    fun merge(base: List<DailyAppUsageEntity>, live: List<DailyAppUsageEntity>, now: Long): List<DailyAppUsageEntity> {
        val byPkg = linkedMapOf<String, DailyAppUsageEntity>()
        base.forEach { byPkg[it.packageName] = it }
        live.forEach { l ->
            val b = byPkg[l.packageName]
            byPkg[l.packageName] = if (b == null) l else l.copy(
                appName = l.appName.ifBlank { b.appName },
                usageMillis = b.usageMillis + l.usageMillis,
                sessionCount = b.sessionCount + l.sessionCount,
                shortSessionCount = b.shortSessionCount + l.shortSessionCount,
                longestSessionMillis = maxOf(b.longestSessionMillis, l.longestSessionMillis),
                lateNightUsageMillis = b.lateNightUsageMillis + l.lateNightUsageMillis,
                lastUpdatedTimestamp = now
            )
        }
        return byPkg.values.toList()
    }

    internal fun encode(rows: List<DailyAppUsageEntity>): String = JSONArray().apply {
        rows.forEach { r ->
            put(JSONObject()
                .put("d", r.dateString).put("p", r.packageName).put("n", r.appName)
                .put("u", r.usageMillis).put("s", r.sessionCount).put("ss", r.shortSessionCount)
                .put("l", r.longestSessionMillis).put("ln", r.lateNightUsageMillis)
                .put("c", r.categoryLevel).put("t", r.lastUpdatedTimestamp))
        }
    }.toString()

    internal fun decode(json: String?): List<DailyAppUsageEntity> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                DailyAppUsageEntity(
                    dateString = o.getString("d"), packageName = o.getString("p"), appName = o.optString("n"),
                    usageMillis = o.optLong("u"), sessionCount = o.optInt("s"), shortSessionCount = o.optInt("ss"),
                    longestSessionMillis = o.optLong("l"), lateNightUsageMillis = o.optLong("ln"),
                    categoryLevel = o.optInt("c"), lastUpdatedTimestamp = o.optLong("t")
                )
            }
        }.getOrDefault(emptyList())
    }
}
