package com.digitscore.app.data

import android.content.pm.ApplicationInfo
import com.digitscore.app.data.entity.DeviceInteractionEventEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.model.AppCategoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsHelperTest {
    @Test
    fun appRatingsExposeGeneralAndManagedChoices() {
        assertEquals(
            listOf(
                AppCategoryType.EXEMPT,
                AppCategoryType.NEUTRAL,
                AppCategoryType.DISTRACTING
            ),
            AppCategoryType.orderedEntries
        )
        assertEquals(AppCategoryType.NEUTRAL, AppCategoryType.MILDLY_PRODUCTIVE.canonical)
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
    fun gameOverlaysAndSystemControllers_areExcluded() {
        val ignored = listOf(
            "com.samsung.android.game.gametools",
            "com.samsung.android.game.gos",
            "com.samsung.android.game.gamehome",
            "com.samsung.android.app.cocktailbarservice",
            "com.google.android.permissioncontroller",
            "com.android.permissioncontroller"
        )
        for (pkg in ignored) {
            assertFalse("Package $pkg should be ignored", UsageStatsHelper.shouldIncludeUsagePackage(pkg, 60_000L))
        }
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
    fun gamesAndVideoAreManagedButAudioIsGeneral() {
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_GAME)
        )
        assertEquals(
            AppCategoryType.NEUTRAL,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_AUDIO)
        )
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_VIDEO)
        )
    }

    @Test
    fun productivityApps_defaultToGeneral() {
        assertEquals(
            AppCategoryType.NEUTRAL,
            UsageStatsHelper.defaultCategoryForApplicationCategory(ApplicationInfo.CATEGORY_PRODUCTIVITY)
        )
    }

    @Test
    fun shoppingPackages_defaultToDistracting() {
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
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.coupang.mobile",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
    }

    @Test
    fun educationAndFinancePackages_defaultToExempt() {
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.duolingo",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "org.khanacademy.android",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "kr.co.millie.millieshelf",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "viva.republica.toss",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.google.android.gm",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.slack",
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

    @Test
    fun messengerAndSocialMediaPackages_distinguishGeneralAndDistracting() {
        assertEquals(
            AppCategoryType.NEUTRAL,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.kakao.talk",
                ApplicationInfo.CATEGORY_SOCIAL
            )
        )
        assertEquals(
            AppCategoryType.EXEMPT,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.slack",
                ApplicationInfo.CATEGORY_SOCIAL
            )
        )
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.instagram.android",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
        assertEquals(
            AppCategoryType.DISTRACTING,
            UsageStatsHelper.defaultCategoryForPackage(
                "com.facebook.katana",
                ApplicationInfo.CATEGORY_UNDEFINED
            )
        )
    }

    @Test
    fun resolvedHabitualUnlockTimestamps_classifiesGlanceUnlocksWithinSevenSeconds() {
        val events = listOf(
            DeviceInteractionEventEntity(1_000L, DeviceInteractionEventEntity.SCREEN_INTERACTIVE),
            DeviceInteractionEventEntity(2_000L, DeviceInteractionEventEntity.KEYGUARD_HIDDEN), // Unlock 1 at 2_000L: no app -> habitual

            DeviceInteractionEventEntity(20_000L, DeviceInteractionEventEntity.SCREEN_INTERACTIVE),
            DeviceInteractionEventEntity(21_000L, DeviceInteractionEventEntity.USER_PRESENT), // Unlock 2 at 21_000L: app 3s session (<=7s), no chained -> habitual

            DeviceInteractionEventEntity(40_000L, DeviceInteractionEventEntity.SCREEN_INTERACTIVE),
            DeviceInteractionEventEntity(41_000L, DeviceInteractionEventEntity.KEYGUARD_HIDDEN), // Unlock 3 at 41_000L: app session > 7s (starts 42s, ends 55s) -> NOT habitual

            DeviceInteractionEventEntity(70_000L, DeviceInteractionEventEntity.SCREEN_INTERACTIVE),
            DeviceInteractionEventEntity(71_000L, DeviceInteractionEventEntity.USER_PRESENT), // Unlock 4 at 71_000L: app 2s session then chained app session starting at 75s (within 5s) -> NOT habitual

            DeviceInteractionEventEntity(100_000L, DeviceInteractionEventEntity.SCREEN_INTERACTIVE),
            DeviceInteractionEventEntity(101_000L, DeviceInteractionEventEntity.KEYGUARD_HIDDEN) // Unlock 5 at 101_000L: only systemui/launcher session -> habitual
        )

        val sessions = listOf(
            // Unlock 2 sessions: short user session (ends within 7s)
            ForegroundUsageSessionEntity(
                packageName = "com.kakao.talk",
                startTimeMillis = 22_000L,
                endTimeMillis = 25_000L,
                dateString = "2026-10-16",
                appName = "KakaoTalk",
                categoryLevel = 1,
                effectivePackageName = "com.kakao.talk",
                effectiveCategoryLevel = 1,
                concurrentAppCount = 1,
                isLateNight = false
            ),
            // Unlock 3 session: long user session (13s)
            ForegroundUsageSessionEntity(
                packageName = "com.example.chess",
                startTimeMillis = 42_000L,
                endTimeMillis = 55_000L,
                dateString = "2026-10-16",
                appName = "Chess",
                categoryLevel = 1,
                effectivePackageName = "com.example.chess",
                effectiveCategoryLevel = 1,
                concurrentAppCount = 1,
                isLateNight = false
            ),
            // Unlock 4 sessions: short session followed by chained session within 5s
            ForegroundUsageSessionEntity(
                packageName = "com.kakao.talk",
                startTimeMillis = 72_000L,
                endTimeMillis = 74_000L,
                dateString = "2026-10-16",
                appName = "KakaoTalk",
                categoryLevel = 1,
                effectivePackageName = "com.kakao.talk",
                effectiveCategoryLevel = 1,
                concurrentAppCount = 1,
                isLateNight = false
            ),
            ForegroundUsageSessionEntity(
                packageName = "com.instagram.android",
                startTimeMillis = 76_000L,
                endTimeMillis = 90_000L,
                dateString = "2026-10-16",
                appName = "Instagram",
                categoryLevel = 2,
                effectivePackageName = "com.instagram.android",
                effectiveCategoryLevel = 2,
                concurrentAppCount = 1,
                isLateNight = false
            ),
            // Unlock 5 session: system launcher / systemui
            ForegroundUsageSessionEntity(
                packageName = "com.sec.android.app.launcher",
                startTimeMillis = 102_000L,
                endTimeMillis = 105_000L,
                dateString = "2026-10-16",
                appName = "Launcher",
                categoryLevel = 0,
                effectivePackageName = "com.sec.android.app.launcher",
                effectiveCategoryLevel = 0,
                concurrentAppCount = 1,
                isLateNight = false
            )
        )

        val habitual = UsageStatsHelper.resolvedHabitualUnlockTimestamps(events, sessions)
        // Should include unlock 1 (2_000L), unlock 2 (21_000L), and unlock 5 (101_000L)
        assertEquals(listOf(2_000L, 21_000L, 101_000L), habitual)
    }
}
