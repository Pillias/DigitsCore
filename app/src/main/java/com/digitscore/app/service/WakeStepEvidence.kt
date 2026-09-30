package com.digitscore.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 아침 기상 시 걸음 패턴을 감지하고,
 * 며칠간 축적된 걸음 수 대표값의 2/3으로 기상 임계치를 자동 보정하는 매니저
 */
object WakeStepAdaptiveManager {
    private const val PREF_NAME = "habit_sensor_settings"
    private const val KEY_STEP_SAMPLES = "morning_step_samples"
    private const val KEY_CALCULATED_THRESHOLD = "calculated_step_threshold"
    const val DEFAULT_THRESHOLD = 50

    fun getThreshold(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_CALCULATED_THRESHOLD, DEFAULT_THRESHOLD)
    }

    fun getSampleCount(context: Context): Int {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_STEP_SAMPLES, "") ?: ""
        if (historyStr.isBlank()) return 0
        return historyStr.split(",").count { it.isNotBlank() }
    }

    /**
     * 아침 기상 감지 시점의 15분간 걸음 수 변위를 저장하고,
     * 표본이 3일 이상 쌓이면 중앙값(median)의 2/3 지점으로 자동 최적화합니다.
     */
    fun recordSample(context: Context, stepsDelta: Float) {
        if (stepsDelta <= 0f) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val historyStr = prefs.getString(KEY_STEP_SAMPLES, "") ?: ""
        val history = historyStr.split(",")
            .mapNotNull { it.trim().toFloatOrNull() }
            .toMutableList()

        history.add(stepsDelta)
        // 최근 7일치 표본만 보존
        val trimmed = history.takeLast(7)
        prefs.edit().putString(KEY_STEP_SAMPLES, trimmed.joinToString(",")).apply()

        // 3일 이상 표본이 누적되면 2/3 적응형 알고리즘 적용
        if (trimmed.size >= 3) {
            val sorted = trimmed.sorted()
            val median = sorted[sorted.size / 2]
            // 중앙값의 2/3로 조정 (최소 20보, 최대 100보 범위 내 안전 클램핑)
            val adaptive = (median * (2f / 3f)).roundToInt().coerceIn(20, 100)
            prefs.edit().putInt(KEY_CALCULATED_THRESHOLD, adaptive).apply()
        }
    }
}

/** Optional hardware counter, not accelerometer polling. Raw step samples stay in memory. */
class WakeStepEvidence(private val context: Context, private val onWake: (Long) -> Unit) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private var registered = false
    private val samples = java.util.ArrayDeque<Pair<Long, Float>>()
    private var confirmedDate: String? = null

    fun refresh() {
        val optedIn = context.getSharedPreferences("habit_sensor_settings", Context.MODE_PRIVATE)
            .getBoolean("steps_enabled", false)
        val permission = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(context,
            Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        if (!optedIn || !permission) { stop(); return }
        if (registered) return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return
        registered = runCatching { manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL,
            60_000_000) }.getOrDefault(false)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val elapsed = event.timestamp / 1_000_000L
        // Ignore delayed samples older than fifteen minutes; never treat since-boot total as new steps.
        if (SystemClock.elapsedRealtime() - elapsed > 15 * 60_000L) return
        val value = event.values.firstOrNull() ?: return
        if (!value.isFinite()) return
        val last = samples.peekLast()
        if (last != null && (elapsed < last.first || value < last.second)) samples.clear()
        samples.addLast(elapsed to value)
        while (samples.isNotEmpty() && elapsed - samples.first.first > 15 * 60_000L) samples.removeFirst()
        val calendar = Calendar.getInstance()
        val date = "${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"

        val requiredThreshold = WakeStepAdaptiveManager.getThreshold(context).toFloat()
        val currentDelta = value - samples.first.second

        // Steps are supplementary, not proof of sleep when missing. The user can override on the dashboard.
        if (calendar.get(Calendar.HOUR_OF_DAY) in 4..12 && date != confirmedDate &&
            currentDelta >= requiredThreshold) {
            confirmedDate = date
            WakeStepAdaptiveManager.recordSample(context, currentDelta)
            onWake(System.currentTimeMillis())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    fun stop() { if (registered) manager.unregisterListener(this); registered = false; samples.clear() }
}
