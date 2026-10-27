package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity

/**
 * 최근 24시간에서 화면을 가장 길게 꺼 둔 쉼과 그동안 오른 지수.
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

    fun minutes(samples: List<CoreIndexSampleEntity>, windowStartMillis: Long, nowMillis: Long): Long =
        find(samples, windowStartMillis, nowMillis)?.minutes ?: 0L
}
