package com.digitscore.app.engine

import com.digitscore.app.model.CoreIndexPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreIndexPresetTest {
    private val now = 2_000_000_000L
    private val calibrated = 60 * 60_000L

    @Test
    fun focusIsMoreTolerantOfGrowthAppsThanBalanced() {
        val sessions = listOf(session(minutes = 120, level = 1))

        val balanced = score(sessions, CoreIndexPreset.BALANCED)
        val focus = score(sessions, CoreIndexPreset.FOCUS)

        assertTrue("balanced=$balanced focus=$focus", focus >= balanced)
    }

    @Test
    fun screenRestIsMoreSensitiveToTotalScreenTime() {
        val sessions = listOf(session(minutes = 120, level = 1))

        val balanced = score(sessions, CoreIndexPreset.BALANCED)
        val screenRest = score(sessions, CoreIndexPreset.SCREEN_REST)

        assertTrue("balanced=$balanced screenRest=$screenRest", screenRest < balanced)
    }

    @Test
    fun familyIsMoreSensitiveToLongManagedUse() {
        val sessions = listOf(session(minutes = 120, level = 3))

        val balanced = score(sessions, CoreIndexPreset.BALANCED)
        val family = score(sessions, CoreIndexPreset.FAMILY)

        assertTrue("balanced=$balanced family=$family", family < balanced)
    }

    @Test
    fun nightBalanceOnlyAddsMeaningfulPressureToLateNightManagedUse() {
        val sessions = listOf(session(minutes = 90, level = 3, isLateNight = true))

        val balanced = score(sessions, CoreIndexPreset.BALANCED)
        val night = score(sessions, CoreIndexPreset.NIGHT_BALANCE)

        assertTrue("balanced=$balanced night=$night", night < balanced)
    }

    @Test
    fun everyPresetKeepsPartOfLateNightContinuousLoadAcrossSleep() {
        assertEquals(0.20, CoreIndexPreset.BALANCED.lateNightCarryoverRatio, 0.001)
        assertEquals(0.20, CoreIndexPreset.FOCUS.lateNightCarryoverRatio, 0.001)
        assertEquals(0.25, CoreIndexPreset.SCREEN_REST.lateNightCarryoverRatio, 0.001)
        assertEquals(0.35, CoreIndexPreset.NIGHT_BALANCE.lateNightCarryoverRatio, 0.001)
        assertEquals(0.35, CoreIndexPreset.FAMILY.lateNightCarryoverRatio, 0.001)
    }

    private fun score(
        sessions: List<RollingUsageSession>,
        preset: CoreIndexPreset
    ): Int = RollingScoreCalculator.calculate(
        sessions = sessions,
        nowMillis = now,
        calibrationUsageMillis = calibrated,
        preset = preset
    ).finalScore

    private fun session(
        minutes: Long,
        level: Int,
        isLateNight: Boolean = false
    ) = RollingUsageSession(
        packageName = "test.app",
        startTimeMillis = now - minutes * 60_000L,
        endTimeMillis = now,
        categoryLevel = level,
        isLateNight = isLateNight
    )
}
