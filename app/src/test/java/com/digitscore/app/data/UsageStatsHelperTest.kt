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
    fun usageBelowTenSeconds_isExcluded() {
        assertFalse(
            UsageStatsHelper.shouldIncludeUsagePackage(
                packageName = "com.example.short",
                usageTimeMillis = 9_999L
            )
        )
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
