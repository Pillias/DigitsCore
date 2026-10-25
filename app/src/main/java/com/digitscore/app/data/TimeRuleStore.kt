package com.digitscore.app.data

import android.content.Context
import com.digitscore.app.model.TimeRule
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

object TimeRuleStore {
    private const val PREFS_NAME = "com.digitscore.app.TIME_RULES"
    private const val KEY_RULES_JSON = "rules_json"
    const val MAX_RULES = 3

    fun getRules(context: Context): List<TimeRule> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_RULES_JSON, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<TimeRule>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val daysArray = obj.optJSONArray("daysOfWeek")
                val days = if (daysArray != null) {
                    (0 until daysArray.length()).mapNotNull { idx ->
                        try {
                            DayOfWeek.of(daysArray.getInt(idx))
                        } catch (_: Exception) {
                            null
                        }
                    }.toSet()
                } else {
                    setOf(
                        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                    )
                }

                list.add(
                    TimeRule(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", TimeRule.PRESET_WORK),
                        enabled = obj.optBoolean("enabled", true),
                        daysOfWeek = if (days.isEmpty()) DayOfWeek.values().toSet() else days,
                        startHour = obj.optInt("startHour", 9),
                        startMinute = obj.optInt("startMinute", 0),
                        endHour = obj.optInt("endHour", 18),
                        endMinute = obj.optInt("endMinute", 0),
                        lightingMinutes = obj.optInt("lightingMinutes", 10)
                    )
                )
            }
            list.take(MAX_RULES)
        } catch (e: Exception) {
            android.util.Log.w("TimeRuleStore", "Failed to parse time rules JSON", e)
            emptyList()
        }
    }

    fun saveRules(context: Context, rules: List<TimeRule>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val array = JSONArray()
        for (rule in rules.take(MAX_RULES)) {
            val obj = JSONObject()
            obj.put("id", rule.id)
            obj.put("name", rule.name)
            obj.put("enabled", rule.enabled)
            val daysArray = JSONArray()
            rule.daysOfWeek.forEach { daysArray.put(it.value) }
            obj.put("daysOfWeek", daysArray)
            obj.put("startHour", rule.startHour)
            obj.put("startMinute", rule.startMinute)
            obj.put("endHour", rule.endHour)
            obj.put("endMinute", rule.endMinute)
            obj.put("lightingMinutes", rule.lightingMinutes)
            array.put(obj)
        }
        prefs.edit().putString(KEY_RULES_JSON, array.toString()).apply()
    }

    fun addRule(context: Context, rule: TimeRule): Boolean {
        val current = getRules(context)
        if (current.size >= MAX_RULES) return false
        saveRules(context, current + rule)
        return true
    }

    fun updateRule(context: Context, rule: TimeRule) {
        val current = getRules(context)
        val index = current.indexOfFirst { it.id == rule.id }
        if (index != -1) {
            val updated = current.toMutableList()
            updated[index] = rule
            saveRules(context, updated)
        } else {
            addRule(context, rule)
        }
    }

    fun deleteRule(context: Context, ruleId: String) {
        val current = getRules(context)
        saveRules(context, current.filter { it.id != ruleId })
    }

    fun getDefaultPresetRules(
        isEn: Boolean,
        suggestedSleepStartHour: Int = 22,
        suggestedSleepStartMinute: Int = 0
    ): List<TimeRule> {
        return listOf(
            TimeRule(
                id = TimeRule.PRESET_WORK,
                name = TimeRule.PRESET_WORK,
                enabled = false,
                daysOfWeek = setOf(
                    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
                ),
                startHour = 9,
                startMinute = 0,
                endHour = 18,
                endMinute = 0,
                lightingMinutes = 10
            ),
            TimeRule(
                id = TimeRule.PRESET_SLEEP,
                name = TimeRule.PRESET_SLEEP,
                enabled = false,
                daysOfWeek = DayOfWeek.values().toSet(),
                startHour = suggestedSleepStartHour,
                startMinute = suggestedSleepStartMinute,
                endHour = 7,
                endMinute = 0,
                lightingMinutes = 5
            )
        )
    }
}
