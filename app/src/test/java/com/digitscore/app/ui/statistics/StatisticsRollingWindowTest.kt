package com.digitscore.app.ui.statistics

import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsRollingWindowTest {
    private val hour = 60 * 60_000L
    private val windowStart = 1_000_000L
    private val windowEnd = windowStart + 24 * hour

    @Test
    fun `summary clips sessions to the rolling 24 hour window`() {
        val sessions = listOf(
            session("video", "Video", windowStart - hour, windowStart + hour, 5),
            session("study", "Study", windowStart + 2 * hour, windowStart + 7 * hour / 2, 1),
            session("video", "Video", windowEnd - hour, windowEnd + hour, 5)
        )

        val summary = summarizeRollingUsage(sessions, windowStart, windowEnd)

        assertEquals(7 * hour / 2, summary.totalMillis)
        assertEquals(2 * hour, summary.managedMillis)
        assertEquals(3 * hour / 2, summary.longestSessionMillis)
        assertEquals("Study", summary.longestSessionAppName)
        assertEquals("Video", summary.topAppName)
        assertEquals(2 * hour, summary.topAppMillis)
        assertEquals(hour, summary.hourlyTotalMillis.first())
        assertEquals(hour, summary.hourlyTotalMillis.last())
    }

    @Test
    fun `summary distributes one session across hourly buckets`() {
        val sessions = listOf(
            session(
                "social",
                "Social",
                windowStart + hour / 2,
                windowStart + 2 * hour + hour / 2,
                4
            )
        )

        val summary = summarizeRollingUsage(sessions, windowStart, windowEnd)

        assertEquals(hour / 2, summary.hourlyTotalMillis[0])
        assertEquals(hour, summary.hourlyTotalMillis[1])
        assertEquals(hour / 2, summary.hourlyTotalMillis[2])
        assertEquals(summary.hourlyTotalMillis, summary.hourlyManagedMillis)
    }

    private fun session(
        packageName: String,
        appName: String,
        start: Long,
        end: Long,
        level: Int
    ) = ForegroundUsageSessionEntity(
        packageName = packageName,
        startTimeMillis = start,
        endTimeMillis = end,
        dateString = "2026-09-11",
        appName = appName,
        categoryLevel = level,
        isLateNight = false
    )
}
