package com.digitscore.app.data

import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RollingUsageSummaryTest {
    private val windowStart = 1_000_000L
    private val windowEnd = windowStart + 24 * 60 * 60_000L

    @Test
    fun `clips sessions to rolling window instead of calendar date`() {
        val records = listOf(
            session("video", windowStart - 10_000L, windowStart + 20_000L, sessionStart = windowStart - 10_000L),
            session("video", windowStart + 60_000L, windowStart + 180_000L),
            session("study", windowEnd - 90_000L, windowEnd + 90_000L, level = 1)
        )

        val result = summarizeRollingUsage(records, windowStart, windowEnd)

        assertEquals(20_000L + 120_000L + 90_000L, result.totalScreenTimeMillis)
        assertEquals(140_000L, result.appsUsage.first { it.packageName == "video" }.usageTimeMillis)
        assertEquals(1, result.appsUsage.first { it.packageName == "video" }.sessionCount)
        assertEquals(90_000L, result.growthTimeMillis)
    }

    @Test
    fun `managed time follows effective concurrent category without double counting`() {
        val records = listOf(
            session(
                packageName = "notes",
                start = windowStart,
                end = windowStart + 300_000L,
                level = 1,
                effectiveLevel = 3
            ),
            session(
                packageName = "notes",
                start = windowStart + 300_000L,
                end = windowStart + 600_000L,
                level = 1,
                effectiveLevel = 1
            )
        )

        val result = summarizeRollingUsage(records, windowStart, windowEnd)

        assertEquals(600_000L, result.totalScreenTimeMillis)
        assertEquals(300_000L, result.managedTimeMillis)
        assertEquals(300_000L, result.growthTimeMillis)
    }

    @Test
    fun `shortSessionCount_6sIsShort_8sAnd14sAreNot`() {
        val records = listOf(
            // 6s session (starts at windowStart, ends at windowStart + 6_000)
            session("app6s", windowStart, windowStart + 6_000L),
            // 8s session
            session("app8s", windowStart + 10_000L, windowStart + 18_000L),
            // 14s session
            session("app14s", windowStart + 20_000L, windowStart + 34_000L)
        )

        val result = summarizeRollingUsage(records, windowStart, windowEnd)
        val app6s = result.appsUsage.first { it.packageName == "app6s" }
        val app8s = result.appsUsage.first { it.packageName == "app8s" }
        val app14s = result.appsUsage.first { it.packageName == "app14s" }

        assertEquals(1, app6s.shortSessionCount)
        assertEquals(0, app8s.shortSessionCount)
        assertEquals(0, app14s.shortSessionCount)
    }

    private fun session(
        packageName: String,
        start: Long,
        end: Long,
        sessionStart: Long = start,
        level: Int = 3,
        effectiveLevel: Int = level
    ) = ForegroundUsageSessionEntity(
        packageName = packageName,
        startTimeMillis = start,
        endTimeMillis = end,
        dateString = "2026-09-17",
        appName = packageName,
        categoryLevel = level,
        effectiveCategoryLevel = effectiveLevel,
        sessionStartTimeMillis = sessionStart,
        isLateNight = false
    )
}
