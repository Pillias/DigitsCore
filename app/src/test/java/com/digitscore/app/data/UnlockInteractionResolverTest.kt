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
            ForegroundUsageSegment("com.kakao.talk", 102_000L, 108_000L) // 6s duration -> quick confirmation (<7s)
        )

        val results = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = unlockTimestamps,
            segments = segments,
            ignoredPackages = ignoredPackages,
            now = 120_000L
        )

        assertEquals(1, results.size)
        assertEquals("com.kakao.talk", results[0].packageName)
        assertTrue(results[0].isQuick)
        assertEquals(100_000L, results[0].unlockTimestamp)
        assertEquals(102_000L, results[0].appOpenTimestamp)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_threshold_6sIsQuick_8sAnd14sAreNot() {
        // 6초 세션: 짧은 확인 (isQuick = true)
        val results6s = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = listOf(100_000L),
            segments = listOf(ForegroundUsageSegment("com.app", 102_000L, 108_000L)),
            ignoredPackages = ignoredPackages,
            now = 120_000L
        )
        assertEquals(1, results6s.size)
        assertTrue(results6s[0].isQuick)

        // 8초 세션: 일반 사용 (isQuick = false)
        val results8s = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = listOf(100_000L),
            segments = listOf(ForegroundUsageSegment("com.app", 102_000L, 110_000L)),
            ignoredPackages = ignoredPackages,
            now = 120_000L
        )
        assertEquals(1, results8s.size)
        org.junit.Assert.assertFalse(results8s[0].isQuick)

        // 14초 세션: 일반 사용 (isQuick = false)
        val results14s = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = listOf(100_000L),
            segments = listOf(ForegroundUsageSegment("com.app", 102_000L, 116_000L)),
            ignoredPackages = ignoredPackages,
            now = 120_000L
        )
        assertEquals(1, results14s.size)
        org.junit.Assert.assertFalse(results14s[0].isQuick)
    }

    @Test
    fun resolveFirstAppsAfterUnlock_ongoingSessionNotProcessedUntilEndedOrThreshold() {
        val unlockTime = 100_000L

        // 주기 1: 앱이 열린 지 3초(now = 105_000L, unlock 후 5초), 아직 7초 미만이고 세그먼트가 현재 시각까지 진행 중 (미종료)
        val ongoingSegmentsCycle1 = listOf(
            ForegroundUsageSegment("com.kakao.talk", 102_000L, 105_000L)
        )
        val resultsCycle1 = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = listOf(unlockTime),
            segments = ongoingSegmentsCycle1,
            ignoredPackages = ignoredPackages,
            now = 105_000L
        )
        // 진행 중이므로 아직 확정되지 않고 다음 주기로 미룸
        assertEquals(0, resultsCycle1.size)

        // 주기 2: 사용자가 계속 사용하여 총 10초(now = 112_000L)가 경과함 (기준 시간 7초 초과)
        val ongoingSegmentsCycle2 = listOf(
            ForegroundUsageSegment("com.kakao.talk", 102_000L, 112_000L)
        )
        val resultsCycle2 = UnlockInteractionResolver.resolveFirstAppsAfterUnlock(
            unlockTimestamps = listOf(unlockTime),
            segments = ongoingSegmentsCycle2,
            ignoredPackages = ignoredPackages,
            now = 112_000L
        )
        // 7초가 지났으므로 일반 사용(isQuick = false)으로 단 1회 확정
        assertEquals(1, resultsCycle2.size)
        assertEquals("com.kakao.talk", resultsCycle2[0].packageName)
        org.junit.Assert.assertFalse(resultsCycle2[0].isQuick)
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
