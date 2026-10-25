package com.digitscore.app.data

import com.digitscore.app.engine.CumulativeCheckpoint
import com.digitscore.app.engine.CumulativeScoreState
import org.junit.Assert.*
import org.junit.Test

class ClockRollbackTest {
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun record(checkpointAt: Long, wake: Long = 0L) = CumulativeRecord(
        CumulativeCheckpoint(checkpointAt, CumulativeScoreState(signal = 83.5, useMomentumMinutes = 12.0,
            awakeRestMinutes = 30.0, recoveryBurden = 1.5)),
        startedAt = 0L, wakeConfirmedAt = wake, briefingHandledAt = wake, lateSleepAnchorAt = wake
    )

    @Test fun futureCheckpointRewindsTimeButKeepsScoreState() {
        val now = 100 * hour
        val settledEnd = now - 2 * minute
        val future = now + 31 * hour
        val original = record(future, wake = future - 3 * minute)
        val recovered = CumulativeScoreStore.recoverFromClockRollback(original, settledEnd, now)
        assertEquals(settledEnd, recovered.checkpoint.timestamp)
        assertEquals(original.checkpoint.state, recovered.checkpoint.state)
        assertEquals(0L, recovered.wakeConfirmedAt)
        assertEquals(0L, recovered.briefingHandledAt)
        assertEquals(0L, recovered.lateSleepAnchorAt)
    }

    @Test fun pastWakeIsKeptWhenRewinding() {
        val now = 100 * hour
        val wake = now - 4 * hour
        val recovered = CumulativeScoreStore.recoverFromClockRollback(record(now + 2 * hour, wake), now - 2 * minute, now)
        assertEquals(wake, recovered.wakeConfirmedAt)
    }

    @Test fun normalAndSlightlyAheadCheckpointsAreUntouched() {
        val now = 100 * hour
        val settledEnd = now - 2 * minute
        val normal = record(settledEnd - minute)
        assertSame(normal, CumulativeScoreStore.recoverFromClockRollback(normal, settledEnd, now))
        val withinTolerance = record(settledEnd + 5 * minute)
        assertSame(withinTolerance, CumulativeScoreStore.recoverFromClockRollback(withinTolerance, settledEnd, now))
    }
}
