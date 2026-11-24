package com.digitscore.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackingStatusBannerTest {
    @Test
    fun userTurnedTrackingOff_showsNothing() {
        assertEquals(TrackingProblem.NONE, resolveTrackingProblem(trackingEnabled = false, hasPermission = false, serviceRunning = false))
    }

    @Test
    fun permissionOff_winsOverServiceState() {
        assertEquals(TrackingProblem.PERMISSION_OFF, resolveTrackingProblem(trackingEnabled = true, hasPermission = false, serviceRunning = true))
        assertEquals(TrackingProblem.PERMISSION_OFF, resolveTrackingProblem(trackingEnabled = true, hasPermission = false, serviceRunning = false))
    }

    @Test
    fun serviceStopped_withPermission() {
        assertEquals(TrackingProblem.SERVICE_STOPPED, resolveTrackingProblem(trackingEnabled = true, hasPermission = true, serviceRunning = false))
        assertEquals(TrackingProblem.NONE, resolveTrackingProblem(trackingEnabled = true, hasPermission = true, serviceRunning = true))
    }
}
