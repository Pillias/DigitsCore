package com.digitscore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundUsageAggregatorTest {
    private fun event(
        second: Long,
        type: ForegroundTimelineEventType,
        packageName: String? = null,
        className: String? = null,
        instanceId: Int? = null
    ) = ForegroundTimelineEvent(
        timestampMillis = second * 1_000L,
        type = type,
        packageName = packageName,
        className = className,
        instanceId = instanceId
    )

    private fun aggregate(
        endSecond: Long,
        events: List<ForegroundTimelineEvent>,
        lateNightEndSecond: Long = 0L
    ) = ForegroundUsageAggregator.aggregate(
        startTimeMillis = 0L,
        endTimeMillis = endSecond * 1_000L,
        lateNightEndTimeMillis = lateNightEndSecond * 1_000L,
        events = events
    )

    @Test
    fun pausedAppFallsBackToAnotherStillVisibleAppWithoutDoubleCounting() {
        val result = aggregate(
            endSecond = 30,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Watch", 1),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "duolingo", "Lesson", 2),
                event(20, ForegroundTimelineEventType.APP_PAUSED, "duolingo", "Lesson", 2)
            )
        )

        assertEquals(20_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.usageMillisByPackage["duolingo"])
        assertEquals(30_000L, result.assignedUsageMillis)
    }

    @Test
    fun missingPause_isClosedByNextResume() {
        val result = aggregate(
            endSecond = 30,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(12, ForegroundTimelineEventType.APP_RESUMED, "browser"),
                event(30, ForegroundTimelineEventType.APP_PAUSED, "browser")
            )
        )

        assertEquals(12_000L, result.usageMillisByPackage["youtube"])
        assertEquals(18_000L, result.usageMillisByPackage["browser"])
    }

    @Test
    fun screenOffAndKeyguard_stopTimeUntilUnlock() {
        val result = aggregate(
            endSecond = 70,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(15, ForegroundTimelineEventType.KEYGUARD_SHOWN),
                event(15, ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE),
                event(50, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(55, ForegroundTimelineEventType.KEYGUARD_HIDDEN),
                event(70, ForegroundTimelineEventType.APP_PAUSED, "youtube")
            )
        )

        assertEquals(30_000L, result.usageMillisByPackage["youtube"])
        assertEquals(1, result.unlockCount)
    }

    @Test
    fun systemUiResume_ownsIntervalInsteadOfInflatingPreviousApp() {
        val result = aggregate(
            endSecond = 30,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "com.android.systemui"),
                event(20, ForegroundTimelineEventType.APP_RESUMED, "youtube")
            )
        )

        assertEquals(20_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.usageMillisByPackage["com.android.systemui"])
        assertEquals(30_000L, result.assignedUsageMillis)
    }

    @Test
    fun oldActivityPause_doesNotCloseNewActivityInSamePackage() {
        val result = aggregate(
            endSecond = 30,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Home", 1),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Watch", 2),
                event(11, ForegroundTimelineEventType.APP_PAUSED, "youtube", "Home", 1),
                event(30, ForegroundTimelineEventType.APP_PAUSED, "youtube", "Watch", 2)
            )
        )

        assertEquals(30_000L, result.usageMillisByPackage["youtube"])
    }

    @Test
    fun eventBeforeMidnight_restoresStateAndSplitsAtBoundary() {
        val result = aggregate(
            endSecond = 20,
            lateNightEndSecond = 20,
            events = listOf(
                event(-10, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(-9, ForegroundTimelineEventType.KEYGUARD_HIDDEN),
                event(-5, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(10, ForegroundTimelineEventType.APP_PAUSED, "youtube")
            )
        )

        assertEquals(10_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.lateNightUsageMillisByPackage["youtube"])
    }

    @Test
    fun lateNightTime_isOnlyWindowOverlap() {
        val result = aggregate(
            endSecond = 60,
            lateNightEndSecond = 30,
            events = listOf(
                event(20, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(40, ForegroundTimelineEventType.APP_PAUSED, "youtube")
            )
        )

        assertEquals(20_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.lateNightUsageMillisByPackage["youtube"])
    }

    @Test
    fun screenInteractive_isNotTreatedAsUnlock() {
        val fallback = aggregate(
            endSecond = 20,
            events = listOf(
                event(1, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(10, ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE),
                event(15, ForegroundTimelineEventType.SCREEN_INTERACTIVE)
            )
        )
        val keyguardBased = aggregate(
            endSecond = 20,
            events = listOf(
                event(1, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(2, ForegroundTimelineEventType.KEYGUARD_HIDDEN),
                event(10, ForegroundTimelineEventType.KEYGUARD_SHOWN),
                event(15, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(16, ForegroundTimelineEventType.KEYGUARD_HIDDEN)
            )
        )

        assertEquals(0, fallback.unlockCount)
        assertEquals(2, keyguardBased.unlockCount)
    }

    @Test
    fun keyguardStateBeforeDay_doesNotTurnScreenOnIntoUnlock() {
        val result = aggregate(
            endSecond = 20,
            events = listOf(
                event(-5, ForegroundTimelineEventType.KEYGUARD_HIDDEN),
                event(2, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(10, ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE),
                event(15, ForegroundTimelineEventType.SCREEN_INTERACTIVE)
            )
        )

        assertEquals(0, result.unlockCount)
    }

    @Test
    fun sessionsMergeAcrossActivityEventsButSplitAcrossScreenOff() {
        val result = aggregate(
            endSecond = 50,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Home"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Watch"),
                event(20, ForegroundTimelineEventType.KEYGUARD_SHOWN),
                event(20, ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE),
                event(30, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(32, ForegroundTimelineEventType.KEYGUARD_HIDDEN),
                event(50, ForegroundTimelineEventType.APP_PAUSED, "youtube", "Watch")
            )
        )

        assertEquals(listOf(20_000L, 18_000L), result.segments.map { it.durationMillis })
    }

    @Test
    fun totalAssignedTime_neverExceedsElapsedRange() {
        val result = aggregate(
            endSecond = 30,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "one"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "two"),
                event(20, ForegroundTimelineEventType.APP_RESUMED, "three")
            )
        )

        assertTrue(result.assignedUsageMillis <= 30_000L)
        assertEquals(result.assignedUsageMillis, result.usageMillisByPackage.values.sum())
        assertEquals(30_000L, result.observableUnlockedMillis)
    }

    @Test
    fun splitScreen_assignsTimeToInteractedAppButUsesHighestVisibleLevel() {
        val levels = mapOf("duolingo" to 1, "youtube" to 3)
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 30_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "duolingo", "Lesson"),
                event(5, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Watch"),
                event(10, ForegroundTimelineEventType.APP_INTERACTION, "duolingo", "Lesson"),
                event(20, ForegroundTimelineEventType.APP_STOPPED, "youtube", "Watch"),
                event(30, ForegroundTimelineEventType.APP_STOPPED, "duolingo", "Lesson")
            ),
            categoryLevelResolver = { levels[it] ?: 2 }
        )

        assertEquals(25_000L, result.usageMillisByPackage["duolingo"])
        assertEquals(5_000L, result.usageMillisByPackage["youtube"])
        assertEquals(30_000L, result.assignedUsageMillis)
        assertTrue(result.segments.any {
            it.concurrentAppCount == 2 && it.effectiveCategoryLevel == 3 &&
                it.effectivePackageName == "youtube"
        })
    }

    @Test
    fun pip_keepsPausedVideoAsScoreContextUntilStoppedWithoutAddingTime() {
        val levels = mapOf("youtube" to 3, "browser" to 1)
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 50_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "Watch"),
                event(10, ForegroundTimelineEventType.APP_PAUSED, "youtube", "Watch"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "browser", "Main"),
                event(40, ForegroundTimelineEventType.APP_STOPPED, "youtube", "Watch"),
                event(50, ForegroundTimelineEventType.APP_STOPPED, "browser", "Main")
            ),
            categoryLevelResolver = { levels[it] ?: 2 }
        )

        assertEquals(10_000L, result.usageMillisByPackage["youtube"])
        assertEquals(40_000L, result.usageMillisByPackage["browser"])
        assertEquals(50_000L, result.assignedUsageMillis)
        assertEquals(
            30_000L,
            result.segments.filter {
                it.packageName == "browser" && it.effectiveCategoryLevel == 3 &&
                    it.effectivePackageName == "youtube" && it.concurrentAppCount == 2
            }.sumOf { it.durationMillis }
        )
    }

    @Test
    fun incrementalState_continuesActiveAppWithoutAnotherResume() {
        val first = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 10_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube"))
        )
        val second = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 10_000L,
            endTimeMillis = 20_000L,
            lateNightEndTimeMillis = 0L,
            events = emptyList(),
            initialState = first.endingState
        )

        assertEquals(10_000L, first.usageMillisByPackage["youtube"])
        assertEquals(10_000L, second.usageMillisByPackage["youtube"])
    }

    @Test
    fun reboot_clearsAppStateFromBeforeShutdown() {
        val result = aggregate(
            endSecond = 40,
            events = listOf(
                event(-20, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(-10, ForegroundTimelineEventType.DEVICE_SHUTDOWN),
                event(5, ForegroundTimelineEventType.DEVICE_STARTUP),
                event(10, ForegroundTimelineEventType.SCREEN_INTERACTIVE),
                event(12, ForegroundTimelineEventType.KEYGUARD_HIDDEN),
                event(20, ForegroundTimelineEventType.APP_RESUMED, "duolingo"),
                event(40, ForegroundTimelineEventType.APP_PAUSED, "duolingo")
            )
        )

        assertEquals(null, result.usageMillisByPackage["youtube"])
        assertEquals(20_000L, result.usageMillisByPackage["duolingo"])
    }
}
