package com.digitscore.app.ui.statistics

import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatisticsMarketChartTest {
    @Test
    fun `moving average does not mix scoring models`() {
        val ranges = buildDailyCoreRanges(listOf(
            history("2026-09-10", 90),
            history("2026-09-11", 40).copy(scoreModelVersion = 4)
        ), emptyList())
        assertEquals(40f, sevenDayMovingAverages(ranges).last() ?: -1f, 0.001f)
    }

    @Test
    fun `daily range uses intraday open close low and high`() {
        val histories = listOf(history("2026-09-10", 66), history("2026-09-11", 75))
        val samples = listOf(
            sample("2026-09-10", 1L, 60),
            sample("2026-09-10", 2L, 42),
            sample("2026-09-10", 3L, 71)
        )

        val ranges = buildDailyCoreRanges(histories, samples)

        assertEquals(60, ranges[0].startScore)
        assertEquals(71, ranges[0].lastScore)
        assertEquals(42, ranges[0].low)
        assertEquals(71, ranges[0].high)
        assertTrue(ranges[0].hasIntradaySamples)
        assertEquals(75, ranges[1].startScore)
        assertEquals(75, ranges[1].lastScore)
        assertFalse(ranges[1].hasIntradaySamples)
    }

    @Test
    fun `moving average uses the trailing seven calendar days without fake zeroes`() {
        val ranges = buildDailyCoreRanges(
            listOf(
                history("2026-09-01", 10),
                history("2026-09-05", 50),
                history("2026-09-11", 80)
            ),
            emptyList()
        )

        val averages = sevenDayMovingAverages(ranges)

        assertEquals(10f, averages[0] ?: -1f, 0.001f)
        assertEquals(30f, averages[1] ?: -1f, 0.001f)
        assertEquals(65f, averages[2] ?: -1f, 0.001f)
    }

    private fun history(date: String, score: Int) = DailyScoreHistoryEntity(
        dateString = date,
        finalScore = score,
        totalScreenTimeMinutes = 100,
        distractingTimeMinutes = 20,
        productiveTimeMinutes = 10,
        idleMinutes = 200,
        unlockCount = 30,
        scoreModelVersion = 2
    )

    private fun sample(date: String, timestamp: Long, score: Int) = CoreIndexSampleEntity(
        bucketStartTimestamp = timestamp,
        timestampMillis = timestamp,
        dateString = date,
        score = score,
        exactScore = score.toDouble(),
        rollingLoad = 0.0,
        acuteLoad = 0.0,
        presetId = "balanced"
    )
}
