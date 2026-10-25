package com.digitscore.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeReconciliationTest {

    @Test
    fun calculate_exactMatch_zeroOther() {
        // DigitsCore = 120min (2 hours since 05:00)
        // OS = 180min (since midnight)
        // Midnight to dawn = 30min
        // Launcher = 20min
        // Split screen deduplicated = 10min
        // Accounted = 30 + 20 + 10 = 60min
        // Diff = 180 - 120 = 60min
        // Other = 0min
        val result = ScreenTimeReconciliationCalculator.calculate(
            digitsCoreMillis = 120 * 60_000L,
            osEstimatedMillis = 180 * 60_000L,
            midnightToDawnMillis = 30 * 60_000L,
            launcherMillis = 20 * 60_000L,
            splitScreenDeduplicatedMillis = 10 * 60_000L
        )

        assertEquals(120, result.digitsCoreMinutes)
        assertEquals(180, result.osEstimatedMinutes)
        assertEquals(30, result.midnightToDawnMinutes)
        assertEquals(20, result.launcherMinutes)
        assertEquals(10, result.splitScreenDeduplicatedMinutes)
        assertEquals(0, result.otherMinutes)
        assertEquals(60, result.differenceMinutes)
    }

    @Test
    fun calculate_withRemainder_allocatesToOther() {
        // DigitsCore = 100min
        // OS = 150min -> Diff = 50min
        // Dawn = 20min, Launcher = 15min, Split = 5min -> Accounted = 40min
        // Other = 50 - 40 = 10min
        val result = ScreenTimeReconciliationCalculator.calculate(
            digitsCoreMillis = 100 * 60_000L,
            osEstimatedMillis = 150 * 60_000L,
            midnightToDawnMillis = 20 * 60_000L,
            launcherMillis = 15 * 60_000L,
            splitScreenDeduplicatedMillis = 5 * 60_000L
        )

        assertEquals(100, result.digitsCoreMinutes)
        assertEquals(150, result.osEstimatedMinutes)
        assertEquals(50, result.differenceMinutes)
        assertEquals(10, result.otherMinutes)
    }

    @Test
    fun calculate_zeroValues_handledSafely() {
        val result = ScreenTimeReconciliationCalculator.calculate(0L, 0L, 0L, 0L, 0L)
        assertEquals(0, result.digitsCoreMinutes)
        assertEquals(0, result.osEstimatedMinutes)
        assertEquals(0, result.differenceMinutes)
        assertEquals(0, result.otherMinutes)
    }
}
