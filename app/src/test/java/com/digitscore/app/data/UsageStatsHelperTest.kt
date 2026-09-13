package com.digitscore.app.data

import android.content.pm.ApplicationInfo
import com.digitscore.app.model.AppCategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsHelperTest {
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
    fun gameAudioAndVideoApps_defaultToLevelFive() {
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
    fun productivityApps_defaultToLevelTwo() {
        assertEquals(
            AppCategoryType.MILDLY_PRODUCTIVE,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_PRODUCTIVITY)
        )
    }

    @Test
    fun shoppingPackages_defaultToLevelFour() {
        assertEquals(
            AppCategoryType.MILDLY_DISTRACTING,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.alibaba.aliexpresshd",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.MILDLY_DISTRACTING,
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
}
