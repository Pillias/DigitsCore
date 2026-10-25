package com.digitscore.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 한도 도달 시 선택 카드("15min 더", "오늘은 여기까지") 상태 및 기록 관리
 * - 선택 기록은 최근 35일간 보관되어 주간 리포트 및 목표 상세에 표시됩니다.
 */
data class GoalLimitChoiceRecord(
    val dateString: String,
    val packageName: String,
    val choice: String,
    val timestampMillis: Long
)

object GoalLimitChoiceStore {

    const val CHOICE_MORE_15 = "MORE_15"
    const val CHOICE_STOP_TODAY = "STOP_TODAY"

    private const val PREFS_NAME = "goal_limit_choices_prefs"
    private const val KEY_RECORDS_JSON = "limit_choice_records_json"
    private const val PREFIX_SNOOZE = "snooze_until_"
    private const val PREFIX_RESEND_COUNT = "resend_count_"
    private const val PREFIX_STOP_TODAY = "stop_today_"

    private const val MAX_RETENTION_DAYS = 35L

    private val lock = Any()

    fun recordChoice(
        context: Context,
        dateString: String,
        packageName: String,
        choice: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = parseRecords(prefs.getString(KEY_RECORDS_JSON, null))

        val newRecord = GoalLimitChoiceRecord(
            dateString = dateString,
            packageName = packageName,
            choice = choice,
            timestampMillis = timestampMillis
        )

        val cutoffDate = runCatching { LocalDate.parse(dateString).minusDays(MAX_RETENTION_DAYS).toString() }
            .getOrDefault("")

        val updated = (existing + newRecord).filter { it.dateString >= cutoffDate }
        prefs.edit().putString(KEY_RECORDS_JSON, serializeRecords(updated)).apply()
    }

    fun getRecords(context: Context): List<GoalLimitChoiceRecord> = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return parseRecords(prefs.getString(KEY_RECORDS_JSON, null))
    }

    fun getCountsForRange(
        context: Context,
        startDate: LocalDate,
        endDate: LocalDate
    ): Pair<Int, Int> = synchronized(lock) {
        val records = getRecords(context)
        val startStr = startDate.toString()
        val endStr = endDate.toString()
        val rangeRecords = records.filter { it.dateString in startStr..endStr }

        val more15Count = rangeRecords.count { it.choice == CHOICE_MORE_15 }
        val stopTodayCount = rangeRecords.count { it.choice == CHOICE_STOP_TODAY }
        return more15Count to stopTodayCount
    }

    fun getTodaySummaryText(
        context: Context,
        dateString: String,
        packageName: String,
        isEn: Boolean
    ): String? = synchronized(lock) {
        val records = getRecords(context).filter { it.dateString == dateString && it.packageName == packageName }
        if (records.isEmpty()) return null

        val last = records.last()
        val timeStr = runCatching {
            val localTime = Instant.ofEpochMilli(last.timestampMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
            DateTimeFormatter.ofPattern("HH:mm", Locale.US).format(localTime)
        }.getOrDefault("")

        return if (last.choice == CHOICE_STOP_TODAY) {
            if (isEn) "Decision: Done for today ($timeStr)"
            else "선택: 오늘은 여기까지 ($timeStr)"
        } else {
            val moreCount = records.count { it.choice == CHOICE_MORE_15 }
            if (isEn) "Decision: 15min more ($moreCount times, last $timeStr)"
            else "선택: 15min 더 (${moreCount}회, 최근 $timeStr)"
        }
    }

    // 15min 더 연장 대기 관리
    fun setMore15Snooze(context: Context, packageName: String, snoozeUntilMillis: Long) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(PREFIX_SNOOZE + packageName, snoozeUntilMillis).apply()
    }

    fun getMore15Snooze(context: Context, packageName: String): Long = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(PREFIX_SNOOZE + packageName, 0L)
    }

    fun clearMore15Snooze(context: Context, packageName: String) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(PREFIX_SNOOZE + packageName).apply()
    }

    // 15min 더 재발송 횟수 관리 (하루 최대 3회)
    fun getMore15ResendCount(context: Context, dateString: String, packageName: String): Int = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("${PREFIX_RESEND_COUNT}${dateString}_$packageName", 0)
    }

    fun incrementMore15ResendCount(context: Context, dateString: String, packageName: String): Int = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt("${PREFIX_RESEND_COUNT}${dateString}_$packageName", 0)
        val next = current + 1
        prefs.edit().putInt("${PREFIX_RESEND_COUNT}${dateString}_$packageName", next).apply()
        return next
    }

    // 오늘은 여기까지 관리
    fun setStopToday(context: Context, dateString: String, packageName: String, active: Boolean) = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("${PREFIX_STOP_TODAY}${dateString}_$packageName", active).apply()
    }

    fun isStopTodayActive(context: Context, dateString: String, packageName: String): Boolean = synchronized(lock) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean("${PREFIX_STOP_TODAY}${dateString}_$packageName", false)
    }

    // 순수 직렬화/역직렬화 및 정리 함수
    fun parseRecords(jsonStr: String?): List<GoalLimitChoiceRecord> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val result = ArrayList<GoalLimitChoiceRecord>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                result.add(
                    GoalLimitChoiceRecord(
                        dateString = obj.getString("date"),
                        packageName = obj.getString("pkg"),
                        choice = obj.getString("choice"),
                        timestampMillis = obj.getLong("time")
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun serializeRecords(records: List<GoalLimitChoiceRecord>): String {
        val array = JSONArray()
        records.forEach { r ->
            val obj = JSONObject()
            obj.put("date", r.dateString)
            obj.put("pkg", r.packageName)
            obj.put("choice", r.choice)
            obj.put("time", r.timestampMillis)
            array.put(obj)
        }
        return array.toString()
    }
}
