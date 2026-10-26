package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity
import java.time.Instant
import java.time.ZoneId

/**
 * "평소 이 시간보다 +5P": 최근 14일 같은 시각(±30분) 지수의 중간값과 지금 지수의 차이.
 * 오늘(최근 12시간)은 빼고, 표본이 있는 날이 7일 미만이면 비교하지 않습니다. 새 저장 없이 5분 표본을 씁니다.
 */
object UsualScoreComparison {
    const val LOOKBACK_DAYS = 14
    const val MIN_DAYS = 7
    private const val WINDOW_MINUTES = 30
    private const val DAY_MILLIS = 24 * 3_600_000L

    /** 평소 같은 시각의 지수(중간값). 표본 부족이면 null. */
    fun usualScore(samples: List<CoreIndexSampleEntity>, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Double? {
        val nowMinute = minuteOfDay(nowMillis, zone)
        val earliest = nowMillis - LOOKBACK_DAYS * DAY_MILLIS - DAY_MILLIS / 24
        val latest = nowMillis - DAY_MILLIS / 2
        val perDay = samples.asSequence()
            .filter { it.timestampMillis in earliest..latest }
            .filter { circularDistance(minuteOfDay(it.timestampMillis, zone), nowMinute) <= WINDOW_MINUTES }
            .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .mapValues { (_, rows) -> median(rows.map { it.exactScore }) }
        if (perDay.size < MIN_DAYS) return null
        return median(perDay.values.toList())
    }

    /** 지금 − 평소 (정수 P). 표본 부족이면 null. */
    fun difference(samples: List<CoreIndexSampleEntity>, currentExactScore: Double, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Int? =
        usualScore(samples, nowMillis, zone)?.let { kotlin.math.round(currentExactScore - it).toInt() }

    private fun minuteOfDay(ts: Long, zone: ZoneId): Int =
        Instant.ofEpochMilli(ts).atZone(zone).let { it.hour * 60 + it.minute }

    private fun circularDistance(a: Int, b: Int): Int {
        val d = kotlin.math.abs(a - b)
        return minOf(d, 1440 - d)
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2.0
    }
}
