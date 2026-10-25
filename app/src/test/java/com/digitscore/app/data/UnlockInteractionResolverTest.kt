package com.digitscore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockInteractionResolverTest {

    private val ignoredPackages = setOf("com.sec.android.app.launcher", "com.digitscore.app")

    @Test
    fun resolveFirstAppsAfterUnlock_appOpenedWithin60s_isQuick() {
        val unlockTimestamps = listOf(100_000L)
        val segments = listOf(
            ForegroundUsageSegment("com.sec.android.app.launcher", 100_000L, 102_000L),
            ForegroundUsageSegment("com.kakao.talk", 102_000L, 125_000L) // 23s duration -> quick confirmation (<60s)
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages
        )

        assertEquals(1, results.size)
        assertEquals("com.kakao.talk", results[0].packageName)
        assertTrue(results[0].isQuick)
        assertEquals(100_000L, results[0].unlockTimestamp)
        assertEquals(102_000L, results[0].appOpenTimestamp)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_appOpenedWithin60s_notQuick() {
        val unlockTimestamps = listOf(100_000L)
        val segments = listOf(
            ForegroundUsageSegment("com.google.android.youtube", 105_000L, 250_000L) // 145s duration -> not quick
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages
        )

        assertEquals(1, results.size)
        assertEquals("com.google.android.youtube", results[0].packageName)
        org.junit.Assert.assertFalse(results[0].isQuick)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_appOpenedAfter60s_ignored() {
        val unlockTimestamps = listOf(100_000L)
        val segments = listOf(
            ForegroundUsageSegment("com.kakao.talk", 170_000L, 200_000L) // 70s after unlock (>60s)
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages
        )

        assertEquals(0, results.size)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_cursorSkipsPastUnlocks() {
        val unlockTimestamps = listOf(50_000L, 100_000L)
        val segments = listOf(
            ForegroundUsageSegment("com.app.old", 51_000L, 60_000L),
            ForegroundUsageSegment("com.app.new", 102_000L, 120_000L)
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages,
            lastProcessedUnlockTimestamp = 80_000L
        )

        assertEquals(1, results.size)
        assertEquals("com.app.new", results[0].packageName)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_notificationTriggeredUnlock_samePackageWithin60s() {
        val unlockTimestamps = listOf(100_000L)
        val segments = listOf(
            ForegroundUsageSegment("com.kakao.talk", 102_000L, 120_000L)
        )
        val notifications = listOf(
            AppNotificationEvent("com.kakao.talk", 85_000L) // 15s before unlock (within 60s)
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages,
            notifications = notifications
        )

        assertEquals(1, results.size)
        assertEquals("com.kakao.talk", results[0].packageName)
        assertTrue(results[0].isNotificationTriggered)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_notificationNotTriggered_differentPackageOrOld() {
        val unlockTimestamps = listOf(100_000L)
        val segments = listOf(
            ForegroundUsageSegment("com.kakao.talk", 102_000L, 120_000L)
        )
        // Notification for a different package within 60s, and notification for same package > 60s ago
        val notifications = listOf(
            AppNotificationEvent("com.instagram.android", 90_000L),
            AppNotificationEvent("com.kakao.talk", 30_000L) // 70s before unlock (> 60s)
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages,
            notifications = notifications
        )

        assertEquals(1, results.size)
        assertEquals("com.kakao.talk", results[0].packageName)
        org.junit.Assert.assertFalse(results[0].isNotificationTriggered)
    }
}
