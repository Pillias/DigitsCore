package com.digitscore.app.data

import android.content.pm.ApplicationInfo
import com.digitscore.app.model.AppCategoryType
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

    @Test
    fun partialEventTimeline_keepsAppsMissingFromEventsUsingAggregateFallback() {
        val exclusiveUsage = ForegroundUsageResult(
            usageMillisByPackage = mapOf("files" to 30_000L),
            lateNightUsageMillisByPackage = mapOf("files" to 30_000L),
            lastUsedMillisByPackage = emptyMap(),
            hasForegroundEvidence = true
        )

        val result = ForegroundUsageReconciler.reconcile(
            exclusiveUsage = exclusiveUsage,
            aggregateUsageMillisByPackage = mapOf(
                "files" to 120_000L,
                "chess" to 660_000L
            ),
            aggregateLateNightMillisByPackage = mapOf(
                "files" to 120_000L,
                "chess" to 660_000L
            ),
            maximumUsageMillis = 3_600_000L
        )

        // 이벤트가 존재하는 앱은 과대 집계될 수 있는 OS 누적값으로 덮어쓰지 않습니다.
        assertEquals(30_000L, result.usageMillisByPackage["files"])
        // 이벤트에서 통째로 누락된 앱은 OS 누적값으로 반드시 복구합니다.
        assertEquals(660_000L, result.usageMillisByPackage["chess"])
        assertEquals(660_000L, result.lateNightUsageMillisByPackage["chess"])
    }

    @Test
    fun aggregateFallback_isClampedToElapsedDayAndLateNightToUsage() {
        val result = ForegroundUsageReconciler.reconcile(
            exclusiveUsage = ForegroundUsageResult(
                usageMillisByPackage = emptyMap(),
                lateNightUsageMillisByPackage = emptyMap(),
                lastUsedMillisByPackage = emptyMap(),
                hasForegroundEvidence = false
            ),
            aggregateUsageMillisByPackage = mapOf("video" to 7_200_000L),
            aggregateLateNightMillisByPackage = mapOf("video" to 6_000_000L),
            maximumUsageMillis = 3_600_000L
        )

        assertEquals(3_600_000L, result.usageMillisByPackage["video"])
        assertEquals(3_600_000L, result.lateNightUsageMillisByPackage["video"])
    }

    @Test
    fun audioAndVideoApps_defaultToDistractingCategory() {
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_AUDIO)
        )
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_VIDEO)
        )
    }

    @Test
    fun nonMediaApps_doNotReceiveAutomaticDistractingCategory() {
        assertEquals(
            AppCategoryType.NEUTRAL,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_PRODUCTIVITY)
        )
    }
}
