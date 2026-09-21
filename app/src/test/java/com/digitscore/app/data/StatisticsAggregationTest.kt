package com.digitscore.app.data

import com.digitscore.app.data.entity.*
import com.digitscore.app.engine.*
import org.junit.Assert.*
import org.junit.Test

class StatisticsAggregationTest {
    private fun session(start: Long, end: Long, opened: Long = start, pkg: String = "video", tier: Int = 3) =
        ForegroundUsageSessionEntity(pkg, start, end, "2026-09-21", pkg, tier,
            sessionStartTimeMillis = opened, isLateNight = false)

    @Test fun hourAndMidnightFragmentsCountOnlyOneOpening() {
        val h = STAT_HOUR
        val rows = aggregateUsageHours(listOf(session(23*h+50*60_000, 24*h),
            session(24*h, 24*h+10*60_000, 23*h+50*60_000)), 23*h, 25*h, 26*h)
        val device = rows.filter { it.packageName.isEmpty() }
        assertEquals(20*60_000L, device.sumOf { it.usageMillis })
        assertEquals(1, device.sumOf { it.opens })
        assertEquals(0, device.sumOf { it.shortOpens })
        assertEquals(0, device.single { it.hour == 24*h }.opens)
    }

    @Test fun shortChecksIncludeExactlyOneMinuteButNotAnUnsettledOpening() {
        val rows = aggregateUsageHours(listOf(session(0, 60_000), session(120_000, 121_000),
            session(299_000, 300_000)), 0, 300_000, 240_000)
        val total = rows.single { it.packageName.isEmpty() }
        assertEquals(3, total.opens)
        assertEquals(2, total.shortOpens)
        assertEquals(62_000L, total.usageMillis)
    }

    @Test fun overlappingPipIsNotDoubleCountedAndRangeIsClipped() {
        val rows = aggregateUsageHours(listOf(session(0, 600_000, pkg="normal", tier=2),
            session(120_000, 300_000)), 60_000, 360_000, 600_000)
        val total = rows.single { it.packageName.isEmpty() }
        assertEquals(300_000L, total.usageMillis)
        assertEquals(180_000L, total.managedMillis)
        assertEquals(total.usageMillis, rows.filter { it.packageName.isNotEmpty() }.sumOf { it.usageMillis })
        assertEquals(1, total.opens)
    }

    @Test fun movementsReconcileWithScoreAndAttributePipToEffectiveOwner() {
        val initial = CumulativeScoreState()
        val moves = mutableListOf<ScoreMovement>()
        val end = CumulativeTimeline.advance(CumulativeCheckpoint(0, initial), 120*60_000L,
            listOf(CumulativeUse(0, 60*60_000L, true, "open", packageName="browser", effectivePackageName="video")),
            { CumulativeActivity.AWAKE_REST }, onMovement = { moves.add(it) })
        val config = CumulativeScoreConfig.CURRENT
        assertEquals(config.displayed(end.state.signal)-config.displayed(initial.signal),
            moves.sumOf { it.after-it.before }, 1e-9)
        assertEquals(setOf("video"), moves.filter { !it.opening && it.after < it.before }.map { it.packageName }.toSet())
        assertEquals(setOf("browser"), moves.filter { it.opening }.map { it.packageName }.toSet())
        assertEquals(120*60_000L, moves.sumOf { it.end-it.start })
        assertTrue(moves.filter { it.after > it.before }.all { it.packageName.isEmpty() })
    }
}
