package com.digitscore.app.engine

import java.time.Instant
import java.time.ZoneId

data class RestWindow(val bedtimeMinute: Int = 23 * 60, val wakeMinute: Int = 7 * 60, val learnedNights: Int = 0)

/** Low confidence means a protective pause, never a diagnosis of sleep.
 * No automatic recovery merely because five hours elapsed.
 */
object RestPhasePolicy {
    fun learn(uses: List<CumulativeUse>, zone: ZoneId): RestWindow {
        // A brief night check is not a new bedtime or a confirmed waking event.
        val sorted = uses.filter { it.end - it.start > 60_000L }.sortedBy { it.start }
        val windows = mutableListOf<Pair<Int, Int>>()
        var previousEnd: Long? = null
        for (use in sorted) {
            previousEnd?.let { end ->
                val hours = (use.start - end) / 3_600_000.0
                val startMinute = minute(end, zone)
                val wakeMinute = minute(use.start, zone)
                if (hours in 3.0..12.0 && (startMinute >= 20 * 60 || startMinute < 5 * 60) &&
                    wakeMinute in 4 * 60..12 * 60 && use.end - use.start > 60_000L) {
                    windows.add((if (startMinute < 12 * 60) startMinute + 1440 else startMinute) to wakeMinute)
                }
            }
            previousEnd = maxOf(previousEnd ?: use.end, use.end)
        }
        if (windows.size < 3) return RestWindow()
        val nights = windows.takeLast(7)
        return RestWindow(nights.map { it.first }.sorted()[nights.size / 2] % 1440,
            nights.map { it.second }.sorted()[nights.size / 2], nights.size)
    }

    fun activityAt(
        timestamp: Long, window: RestWindow, zone: ZoneId,
        wakeConfirmedAt: Long = 0L, restingConfirmedUntil: Long = 0L,
        lateSleepAnchorAt: Long = 0L
    ): CumulativeActivity {
        if (timestamp < restingConfirmedUntil) return CumulativeActivity.SLEEP
        val local = Instant.ofEpochMilli(timestamp).atZone(zone)
        val minute = local.hour * 60 + local.minute
        val inWindow = if (window.bedtimeMinute > window.wakeMinute)
            minute >= window.bedtimeMinute || minute < window.wakeMinute
        else minute in window.bedtimeMinute until window.wakeMinute
        val confirmedToday = wakeConfirmedAt > 0 && timestamp >= wakeConfirmedAt &&
            Instant.ofEpochMilli(wakeConfirmedAt).atZone(zone).toLocalDate() == local.toLocalDate()
        // Confirmation only overrides the morning part; a morning tap does not disable tonight's sleep.
        val lateSleepProtection = lateSleepAnchorAt > 0 && timestamp >= lateSleepAnchorAt &&
            timestamp < lateSleepAnchorAt + 5 * 3_600_000L
        return if ((inWindow || lateSleepProtection) && !(confirmedToday && minute < 18 * 60)) CumulativeActivity.SLEEP
        else CumulativeActivity.AWAKE_REST
    }

    /** Protect the first five hours after substantial late-night use even if sleep begins
     * after the usual wake time. Explicit waking evidence overrides this soft fallback.
     */
    fun lateSleepAnchor(uses: List<CumulativeUse>, zone: ZoneId): Long? = uses.filter { use ->
        val end = Instant.ofEpochMilli(use.end).atZone(zone)
        val midnight = end.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
        val six = end.toLocalDate().atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        end.hour < 10 && minOf(use.end, six) - maxOf(use.start, midnight) >= 30 * 60_000L
    }.maxOfOrNull { it.end }

    private fun minute(timestamp: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(timestamp).atZone(zone).let { it.hour * 60 + it.minute }
}
