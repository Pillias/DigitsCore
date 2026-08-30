package com.digitscore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundUsageAggregatorTest {
    private fun event(
        second: Long,
        type: ForegroundTimelineEventType,
        packageName: String? = null,
        className: String? = null
    ) = ForegroundTimelineEvent(second * 1_000L, type, packageName, className)

    @Test
    fun appSwitch_assignsEachIntervalToOnlyOneForegroundApp() {
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 30_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "WatchActivity"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "duolingo", "LessonActivity"),
                event(20, ForegroundTimelineEventType.APP_PAUSED, "duolingo", "LessonActivity")
            )
        )

        assertEquals(10_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.usageMillisByPackage["duolingo"])
        assertEquals(20_000L, result.usageMillisByPackage.values.sum())
    }

    @Test
    fun pictureInPicture_doesNotKeepCountingPausedVideoApp() {
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 40_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "WatchActivity"),
                event(10, ForegroundTimelineEventType.APP_PAUSED, "youtube", "WatchActivity"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "duolingo", "LessonActivity"),
                event(30, ForegroundTimelineEventType.APP_STOPPED, "youtube", "WatchActivity"),
                event(40, ForegroundTimelineEventType.APP_PAUSED, "duolingo", "LessonActivity")
            )
        )

        assertEquals(10_000L, result.usageMillisByPackage["youtube"])
        assertEquals(30_000L, result.usageMillisByPackage["duolingo"])
    }

    @Test
    fun screenOff_closesForegroundIntervalUntilAnotherResume() {
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 60_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(15, ForegroundTimelineEventType.SCREEN_NON_INTERACTIVE),
                event(50, ForegroundTimelineEventType.APP_RESUMED, "youtube")
            )
        )

        assertEquals(25_000L, result.usageMillisByPackage["youtube"])
    }

    @Test
    fun oldActivityPause_doesNotCloseNewActivityInSamePackage() {
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 30_000L,
            lateNightEndTimeMillis = 0L,
            events = listOf(
                event(0, ForegroundTimelineEventType.APP_RESUMED, "youtube", "HomeActivity"),
                event(10, ForegroundTimelineEventType.APP_RESUMED, "youtube", "WatchActivity"),
                event(11, ForegroundTimelineEventType.APP_PAUSED, "youtube", "HomeActivity"),
                event(30, ForegroundTimelineEventType.APP_PAUSED, "youtube", "WatchActivity")
            )
        )

        assertEquals(30_000L, result.usageMillisByPackage["youtube"])
    }

    @Test
    fun eventBeforeMidnight_seedsForegroundStateAtDayBoundary() {
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 20_000L,
            lateNightEndTimeMillis = 20_000L,
            events = listOf(
                event(-5, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(10, ForegroundTimelineEventType.APP_PAUSED, "youtube")
            )
        )

        assertTrue(result.hasForegroundEvidence)
        assertEquals(10_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.lateNightUsageMillisByPackage["youtube"])
    }

    @Test
    fun lateNightDuration_isOnlyTheOverlapWithLateNightWindow() {
        val result = ForegroundUsageAggregator.aggregate(
            startTimeMillis = 0L,
            endTimeMillis = 60_000L,
            lateNightEndTimeMillis = 30_000L,
            events = listOf(
                event(20, ForegroundTimelineEventType.APP_RESUMED, "youtube"),
                event(40, ForegroundTimelineEventType.APP_PAUSED, "youtube")
            )
        )

        assertEquals(20_000L, result.usageMillisByPackage["youtube"])
        assertEquals(10_000L, result.lateNightUsageMillisByPackage["youtube"])
    }
}
