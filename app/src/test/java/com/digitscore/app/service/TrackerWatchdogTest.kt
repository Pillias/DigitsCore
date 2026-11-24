package com.digitscore.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerWatchdogTest {
    private val hour = 60 * 60 * 1_000L

    @Test
    fun firstNotification_isAllowed() {
        assertTrue(TrackerWatchdogJobService.shouldNotify(0L, 10 * hour))
    }

    @Test
    fun sameReasonWithinSixHours_isSuppressed() {
        assertFalse(TrackerWatchdogJobService.shouldNotify(10 * hour, 15 * hour))
        assertTrue(TrackerWatchdogJobService.shouldNotify(10 * hour, 16 * hour))
    }

    @Test
    fun clockMovedBackwards_allowsNotification() {
        assertTrue(TrackerWatchdogJobService.shouldNotify(20 * hour, 10 * hour))
    }
}
