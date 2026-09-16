package com.digitscore.app.data

import android.content.pm.ApplicationInfo
import com.digitscore.app.model.AppCategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsHelperTest {
    @Test
    fun appRatingsExposeOnlyThreeCanonicalChoices() {
        assertEquals(
            listOf(
                AppCategoryType.PRODUCTIVE,
                AppCategoryType.NEUTRAL,
                AppCategoryType.DISTRACTING
            ),
            AppCategoryType.orderedEntries
        )
        assertEquals(AppCategoryType.PRODUCTIVE, AppCategoryType.MILDLY_PRODUCTIVE.canonical)
        assertEquals(AppCategoryType.DISTRACTING, AppCategoryType.MILDLY_DISTRACTING.canonical)
    }

    @Test
    fun unregisteredUserAppWithUsage_isNotDropped() {
        assertTrue(
            UsageStatsHelper.shouldIncludeUsagePackage(
                packageName = "com.example.chess",
                usageTimeMillis = 34 * 60_000L
            )
        )
    }

    @Test
    fun ignoredSystemPackage_isExcluded() {
        assertFalse(
            UsageStatsHelper.shouldIncludeUsagePackage(
                packageName = "com.android.systemui",
                usageTimeMillis = 60_000L
            )
        )
    }

    @Test
    fun positiveUsageBelowTenSeconds_isIncluded() {
        assertTrue(
            UsageStatsHelper.shouldIncludeUsagePackage(
                packageName = "com.example.short",
                usageTimeMillis = 1L
            )
        )
    }

    @Test
    fun zeroLengthUsage_isExcluded() {
        assertFalse(
            UsageStatsHelper.shouldIncludeUsagePackage(
                packageName = "com.example.zero",
                usageTimeMillis = 0L
            )
        )
    }

    @Test
    fun unlockCandidates_pairScreenAndKeyguardWithoutDoubleCounting() {
        val events = listOf(
            com.digitscore.app.data.entity.DeviceInteractionEventEntity(
                1_000L,
                com.digitscore.app.data.entity.DeviceInteractionEventEntity.SCREEN_INTERACTIVE
            ),
            com.digitscore.app.data.entity.DeviceInteractionEventEntity(
                3_000L,
                com.digitscore.app.data.entity.DeviceInteractionEventEntity.KEYGUARD_HIDDEN
            ),
            com.digitscore.app.data.entity.DeviceInteractionEventEntity(
                60_000L,
                com.digitscore.app.data.entity.DeviceInteractionEventEntity.SCREEN_INTERACTIVE
            ),
            com.digitscore.app.data.entity.DeviceInteractionEventEntity(
                61_000L,
                com.digitscore.app.data.entity.DeviceInteractionEventEntity.USER_PRESENT
            )
        )

        assertEquals(listOf(3_000L, 61_000L), UsageStatsHelper.resolvedUnlockTimestamps(events))
    }

    @Test
    fun screenInteractiveWithoutUnlock_isNotCounted() {
        val events = listOf(
            com.digitscore.app.data.entity.DeviceInteractionEventEntity(
                1_000L,
                com.digitscore.app.data.entity.DeviceInteractionEventEntity.SCREEN_INTERACTIVE
            )
        )

        assertEquals(emptyList<Long>(), UsageStatsHelper.resolvedUnlockTimestamps(events))
    }

    @Test
    fun gameAudioAndVideoApps_defaultToImmersionManagement() {
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_GAME)
        )
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
    fun productivityApps_defaultToGrowth() {
        assertEquals(
            AppCategoryType.PRODUCTIVE,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_PRODUCTIVITY)
        )
    }

    @Test
    fun shoppingPackages_defaultToImmersionManagement() {
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.alibaba.aliexpresshd",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.shopee.my",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
    }

    @Test
    fun educationPackages_defaultToLevelOne() {
        assertEquals(
            AppCategoryType.PRODUCTIVE,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.duolingo",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.PRODUCTIVE,
            UsageStatsHelper.defaultCategoryForPackage(
                "org.khanacademy.android",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
    }

    @Test
    fun pipMetadataChanges_doNotSplitThePrimaryAppSession() {
        val segments = listOf(
            ForegroundUsageSegment("browser", 0L, 30_000L, "browser", 2, 1, 0L),
            ForegroundUsageSegment("video", 30_000L, 40_000L, "video", 3, 1, 30_000L),
            ForegroundUsageSegment("browser", 40_000L, 100_000L, "video", 3, 2, 0L),
            ForegroundUsageSegment("browser", 100_000L, 130_000L, "browser", 2, 1, 0L)
        )

        val durations = sessionDurationsByPackage(segments).getValue("browser")
        val summary = sessionSummariesByPackage(segments).getValue("browser")

        assertEquals(listOf(120_000L), durations)
        assertEquals(1, summary.sessionCount)
        assertEquals(120_000L, summary.longestSessionMillis)
        assertEquals(0, summary.shortSessionCount)
    }
}
