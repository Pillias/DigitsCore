package com.digitscore.app.engine

import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class CumulativeTimelineTest {
    private val minute = 60_000L
    private val rest = { _: Long -> CumulativeActivity.AWAKE_REST }
    private fun initial() = CumulativeCheckpoint(0L, CumulativeScoreState())

    @Test fun pollingOrRestartDoesNotReplayScore() {
        val uses = listOf(CumulativeUse(0, 240 * minute, true, "game:0"))
        val whole = CumulativeTimeline.advance(initial(), 480 * minute, uses, rest)
        var pieces = initial()
        for (i in 1..480) pieces = CumulativeTimeline.advance(pieces, i * minute, uses, rest)
        assertEquals(whole, pieces)
        assertEquals(whole, CumulativeTimeline.advance(whole, 480 * minute, uses, rest))
    }

    @Test fun pipUsesHighestTierWithoutDoubleTimeOrDuplicateFragmentOpen() {
        val normal = CumulativeUse(0, 60 * minute, false, "one-opening")
        val managed = normal.copy(managed = true)
        val a = CumulativeTimeline.advance(initial(), 60 * minute, listOf(managed), rest)
        val b = CumulativeTimeline.advance(initial(), 60 * minute, listOf(normal, managed), rest)
        assertEquals(a, b)
    }

    @Test fun oneMinuteCheckPreservesRestButLongerUseCountsFromBeginning() {
        val check = CumulativeUse(0, minute, true, "check")
        val short = CumulativeTimeline.advance(initial(), 3 * minute, listOf(check), rest)
        assertEquals(3.0, short.state.awakeRestMinutes, 0.0)
        val long = CumulativeTimeline.advance(initial(), 3 * minute, listOf(check.copy(end = 2 * minute)), rest)
        assertEquals(1.0, long.state.awakeRestMinutes, 0.0)
        assertTrue(long.state.signal < short.state.signal)
    }

    @Test fun shortBurstsCannotAvoidDurationPenaltyByRapidReopening() {
        val uses = listOf(CumulativeUse(0, 40_000, true, "a"), CumulativeUse(50_000, 90_000, true, "b"))
        val result = CumulativeTimeline.advance(initial(), 2 * minute, uses, rest)
        assertTrue(result.state.useMomentumMinutes > 0)
    }

    @Test fun morningConfirmationDoesNotDisableFollowingNightFreeze() {
        val zone = ZoneId.of("Asia/Kuching")
        fun time(hour: Int) = ZonedDateTime.of(2026, 9, 20, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(CumulativeActivity.AWAKE_REST, RestPhasePolicy.activityAt(time(6), RestWindow(), zone, time(5)))
        assertEquals(CumulativeActivity.SLEEP, RestPhasePolicy.activityAt(time(23), RestWindow(), zone, time(5)))
    }

    @Test fun daylightSavingsUsesLocalRestWindowNotFixedUtcHours() {
        val zone = ZoneId.of("America/New_York")
        for (month in listOf(1, 7)) {
            val night = ZonedDateTime.of(2026, month, 20, 3, 0, 0, 0, zone).toInstant().toEpochMilli()
            assertEquals(CumulativeActivity.SLEEP, RestPhasePolicy.activityAt(night, RestWindow(), zone))
        }
    }

    @Test fun sleepingAfterMorningGamingIsProtectedButWalkingCanOverride() {
        val zone = ZoneId.of("Asia/Kuching")
        fun time(hour: Int) = ZonedDateTime.of(2026, 9, 20, hour, 0, 0, 0, zone).toInstant().toEpochMilli()
        val anchor = RestPhasePolicy.lateSleepAnchor(listOf(CumulativeUse(time(3), time(7), true, "game")), zone)!!
        assertEquals(time(7), anchor)
        assertEquals(CumulativeActivity.SLEEP, RestPhasePolicy.activityAt(time(10), RestWindow(), zone, lateSleepAnchorAt = anchor))
        assertEquals(CumulativeActivity.AWAKE_REST, RestPhasePolicy.activityAt(time(10), RestWindow(), zone,
            wakeConfirmedAt = time(9), lateSleepAnchorAt = anchor))
        assertNull(RestPhasePolicy.lateSleepAnchor(listOf(CumulativeUse(time(7), time(11), true, "day")), zone))
    }
}
