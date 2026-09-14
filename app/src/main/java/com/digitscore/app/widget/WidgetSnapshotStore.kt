package com.digitscore.app.widget

import android.content.Context
import com.digitscore.app.engine.ScoreFlow

/**
 * Glance 갱신 작업은 서비스 계산이 끝난 뒤 별도 시점에 실행될 수 있으므로
 * 프로세스 메모리 대신 마지막으로 확정된 표시값을 앱 전용 저장소에 보관합니다.
 */
data class WidgetSnapshot(
    val score: Int,
    val screenMinutes: Long,
    val managedMinutes: Long,
    val unlockCount: Int,
    val flow: ScoreFlow,
    val updatedAtMillis: Long
) {
    fun sanitized(): WidgetSnapshot = copy(
        score = score.coerceIn(1, 100),
        screenMinutes = screenMinutes.coerceAtLeast(0L),
        managedMinutes = managedMinutes.coerceAtLeast(0L),
        unlockCount = unlockCount.coerceAtLeast(0),
        updatedAtMillis = updatedAtMillis.coerceAtLeast(0L)
    )

    fun hasSameDisplayedValues(other: WidgetSnapshot?): Boolean =
        other != null &&
            score == other.score &&
            screenMinutes == other.screenMinutes &&
            managedMinutes == other.managedMinutes &&
            unlockCount == other.unlockCount &&
            flow == other.flow
}

object WidgetSnapshotStore {
    private const val PREFS_NAME = "widget_snapshot"
    private const val KEY_HAS_VALUE = "has_value"
    private const val KEY_SCORE = "score"
    private const val KEY_SCREEN_MINUTES = "screen_minutes"
    private const val KEY_MANAGED_MINUTES = "managed_minutes"
    private const val KEY_UNLOCK_COUNT = "unlock_count"
    private const val KEY_FLOW = "flow"
    private const val KEY_UPDATED_AT = "updated_at"

    fun write(context: Context, snapshot: WidgetSnapshot): Boolean {
        val value = snapshot.sanitized()
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HAS_VALUE, true)
            .putInt(KEY_SCORE, value.score)
            .putLong(KEY_SCREEN_MINUTES, value.screenMinutes)
            .putLong(KEY_MANAGED_MINUTES, value.managedMinutes)
            .putInt(KEY_UNLOCK_COUNT, value.unlockCount)
            .putString(KEY_FLOW, value.flow.name)
            .putLong(KEY_UPDATED_AT, value.updatedAtMillis)
            .commit()
    }

    fun read(context: Context): WidgetSnapshot? {
        val preferences = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!preferences.getBoolean(KEY_HAS_VALUE, false)) return null
        val flow = runCatching {
            ScoreFlow.valueOf(preferences.getString(KEY_FLOW, null).orEmpty())
        }.getOrDefault(ScoreFlow.CALIBRATING)
        return WidgetSnapshot(
            score = preferences.getInt(KEY_SCORE, 80),
            screenMinutes = preferences.getLong(KEY_SCREEN_MINUTES, 0L),
            managedMinutes = preferences.getLong(KEY_MANAGED_MINUTES, 0L),
            unlockCount = preferences.getInt(KEY_UNLOCK_COUNT, 0),
            flow = flow,
            updatedAtMillis = preferences.getLong(KEY_UPDATED_AT, 0L)
        ).sanitized()
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
