package com.digitscore.app.data.backup

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * DB 밖(SharedPreferences)에 있는 사용자 설정·기록 중 백업이 필요한 값만 골라 담습니다.
 * 기기마다 달라지는 임시 상태(알림 스누즈, 재알림 횟수 등)는 넣지 않습니다.
 */
internal object PrefsBackup {

    data class Spec(
        val prefsName: String,
        val exactKeys: Set<String> = emptySet(),
        val prefixes: Set<String> = emptySet(),
        val allKeys: Boolean = false
    ) {
        fun includes(key: String): Boolean =
            allKeys || key in exactKeys || prefixes.any { key.startsWith(it) }
    }

    val SPECS = listOf(
        // 시간 규칙 (취침·집중 시간대)
        Spec("com.digitscore.app.TIME_RULES", allKeys = true),
        // 목표 초과 시 선택 기록 (스누즈/재알림 같은 당일 임시 상태는 제외)
        Spec("goal_limit_choices_prefs", exactKeys = setOf("limit_choice_records_json")),
        // 평일/주말 목표, 지난 목표 결과, 내 기록
        Spec(
            "daily_goal_state",
            exactKeys = setOf(
                "goal_separate_weekday_weekend",
                "goal_day_records_json",
                "personal_best_json",
                "personal_best_last_date",
                "personal_best_backfilled"
            ),
            prefixes = setOf("goal_weekday_", "goal_weekend_")
        )
    )

    fun export(context: Context): JSONObject {
        val root = JSONObject()
        SPECS.forEach { spec ->
            val prefs = context.getSharedPreferences(spec.prefsName, Context.MODE_PRIVATE)
            root.put(spec.prefsName, encode(prefs.all, spec))
        }
        return root
    }

    fun restore(context: Context, root: JSONObject) {
        SPECS.forEach { spec ->
            val obj = root.optJSONObject(spec.prefsName) ?: return@forEach
            val prefs = context.getSharedPreferences(spec.prefsName, Context.MODE_PRIVATE)
            val editor = prefs.edit()
            apply(editor, decode(obj, spec))
            editor.commit()
        }
    }

    /** 값마다 타입을 함께 저장해야 Int/Long/Float가 복원 후에도 같은 타입으로 읽힙니다. */
    internal fun encode(values: Map<String, *>, spec: Spec): JSONObject {
        val out = JSONObject()
        values.forEach { (key, value) ->
            if (!spec.includes(key) || value == null) return@forEach
            val entry = JSONObject()
            when (value) {
                is Boolean -> entry.put("t", "b").put("v", value)
                is Int -> entry.put("t", "i").put("v", value)
                is Long -> entry.put("t", "l").put("v", value)
                is Float -> entry.put("t", "f").put("v", value.toDouble())
                is String -> entry.put("t", "s").put("v", value)
                is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.filterIsInstance<String>()))
                else -> return@forEach
            }
            out.put(key, entry)
        }
        return out
    }

    internal fun decode(obj: JSONObject, spec: Spec): Map<String, Any> {
        val out = linkedMapOf<String, Any>()
        obj.keys().forEach { key ->
            if (!spec.includes(key)) return@forEach
            val entry = obj.optJSONObject(key) ?: return@forEach
            val value: Any? = when (entry.optString("t")) {
                "b" -> entry.optBoolean("v")
                "i" -> entry.optInt("v")
                "l" -> entry.optLong("v")
                "f" -> entry.optDouble("v").toFloat()
                "s" -> entry.optString("v")
                "ss" -> entry.optJSONArray("v")?.let { arr ->
                    (0 until arr.length()).map { arr.optString(it) }.toSet()
                }
                else -> null
            }
            if (value != null) out[key] = value
        }
        return out
    }

    private fun apply(editor: SharedPreferences.Editor, values: Map<String, Any>) {
        values.forEach { (key, value) ->
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
    }
}
