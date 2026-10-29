package com.digitscore.app.data.backup

import com.digitscore.app.data.entity.DailyAppUsageEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreBaselineTest {
    private fun row(pkg: String, usage: Long, sessions: Int, longest: Long) = DailyAppUsageEntity(
        dateString = "2026-10-29", packageName = pkg, appName = pkg, usageMillis = usage,
        sessionCount = sessions, shortSessionCount = 1, longestSessionMillis = longest,
        lateNightUsageMillis = 0L, categoryLevel = 3, lastUpdatedTimestamp = 1L
    )

    @Test
    fun `backup rows and live rows add per app`() {
        val merged = RestoreBaseline.merge(
            base = listOf(row("cafe", 48 * 60_000L, 10, 20 * 60_000L), row("yt", 43 * 60_000L, 6, 15 * 60_000L)),
            live = listOf(row("cafe", 5 * 60_000L, 1, 5 * 60_000L), row("files", 60_000L, 1, 60_000L)),
            now = 9L
        ).associateBy { it.packageName }
        assertEquals(53 * 60_000L, merged.getValue("cafe").usageMillis)
        assertEquals(11, merged.getValue("cafe").sessionCount)
        assertEquals(2, merged.getValue("cafe").shortSessionCount)
        assertEquals(20 * 60_000L, merged.getValue("cafe").longestSessionMillis)
        assertEquals(43 * 60_000L, merged.getValue("yt").usageMillis)
        assertEquals(60_000L, merged.getValue("files").usageMillis)
    }

    @Test
    fun `rows survive encode and decode`() {
        val rows = listOf(row("cafe", 1L, 2, 3L))
        assertEquals(rows, RestoreBaseline.decode(RestoreBaseline.encode(rows)))
    }
}
