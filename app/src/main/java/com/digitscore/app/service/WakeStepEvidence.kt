package com.digitscore.app.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.*
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import java.util.Calendar

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
        // Steps are supplementary, not proof of sleep when missing. The user can override on the dashboard.
        if (calendar.get(Calendar.HOUR_OF_DAY) in 4..12 && date != confirmedDate &&
            value - samples.first.second >= 50f) {
            confirmedDate = date
            onWake(System.currentTimeMillis())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    fun stop() { if (registered) manager.unregisterListener(this); registered = false; samples.clear() }
}
