package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

class UsualScoreComparisonTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = 24 * 3_600_000L
    private val now = java.time.ZonedDateTime.of(2026, 10, 26, 15, 0, 0, 0, zone).toInstant().toEpochMilli()

    private fun sample(ts: Long, score: Double) = CoreIndexSampleEntity(ts, ts, "d", score.toInt(), score, 0.0, 0.0, "balanced")

    @Test
    fun comparesWithMedianOfSameTimeOverRecentDays() {
        val samples = (1..10).map { d -> sample(now - d * day + 10 * 60_000L, 70.0 + d % 3) } // 70~72
        assertEquals(71.0, UsualScoreComparison.usualScore(samples, now, zone)!!, 1e-9)
        assertEquals(5, UsualScoreComparison.difference(samples, 76.0, now, zone))
    }

    @Test
    fun hidesWhenFewerThanSevenDays() {
        val samples = (1..6).map { d -> sample(now - d * day, 70.0) }
        assertNull(UsualScoreComparison.usualScore(samples, now, zone))
    }

    @Test
    fun ignoresOtherTimesOfDayAndToday() {
        val samples = (1..8).map { d -> sample(now - d * day, 60.0) } +
            (1..8).map { d -> sample(now - d * day + 5 * 3_600_000L, 90.0) } + // 5시간 다른 시각
            listOf(sample(now - 3_600_000L, 99.0)) // 오늘
        assertEquals(60.0, UsualScoreComparison.usualScore(samples, now, zone)!!, 1e-9)
    }
}
