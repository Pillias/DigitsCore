package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity

/**
 * 오늘(05:00 이후) 화면을 가장 길게 꺼 둔 쉼과 그동안 오른 지수.
 * 05:00 이후 첫 표본부터 세므로 밤잠은 들어가지 않습니다(V0_9_39 평가 U-2).
 * 5분 표본은 화면이 켜진 동안만 쌓이므로 표본 사이 10분 이상 빈 곳을 화면 꺼짐으로 봅니다(5분 단위 근사).
 */
object LongestRest {
    const val MIN_GAP_MILLIS = 10 * 60_000L

    data class Rest(val minutes: Long, val gain: Double)

    fun find(samples: List<CoreIndexSampleEntity>, windowStartMillis: Long, nowMillis: Long): Rest? {
        val sorted = samples.filter { it.timestampMillis in windowStartMillis..nowMillis }.sortedBy { it.timestampMillis }
        return sorted.zipWithNext()
            .filter { (a, b) -> b.timestampMillis - a.timestampMillis >= MIN_GAP_MILLIS }
            .maxByOrNull { (a, b) -> b.timestampMillis - a.timestampMillis }
            ?.let { (a, b) -> Rest((b.timestampMillis - a.timestampMillis) / 60_000L, b.exactScore - a.exactScore) }
    }

    /** 논리적 하루 시작(05:00) */
    fun todayStartMillis(nowMillis: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Long {
        val t = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone)
        val day = t.minusHours(5).toLocalDate()
        return day.atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
    }

    fun minutes(samples: List<CoreIndexSampleEntity>, windowStartMillis: Long, nowMillis: Long): Long =
        find(samples, windowStartMillis, nowMillis)?.minutes ?: 0L
}
