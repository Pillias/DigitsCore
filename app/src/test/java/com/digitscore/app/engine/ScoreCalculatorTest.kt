package com.digitscore.app.engine

import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.PresetMode
import com.digitscore.app.model.ScoreRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreCalculatorTest {

    @Test
    fun testInitialScore_withNoUsage() {
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = 0,
            unlockCount = 0
        )
        assertEquals(100, detail.finalScore)
        assertEquals(ScoreGrade.S, detail.grade)
    }

    @Test
    fun testDistractingApp_briefUsageNoAcceleration() {
        val apps = listOf(
            AppUsage(
                packageName = "com.instagram.android",
                appName = "Instagram",
                usageTimeMillis = 15 * 60 * 1000L, // 15분 (임계치 이하)
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // 15분 이하: 가속도 없음 (1.0x) -> 15분 * 0.8 = 12점 감점 -> 88점 (A등급)
        val detail = ScoreCalculator.calculateScore(
            apps,
            0,
            0,
            ScoreRule(distractingWeightPerMinute = 0.8f)
        )
        assertEquals(88, detail.finalScore)
        assertEquals(12.0f, detail.distractingPenalty, 0.01f)
        assertEquals(ScoreGrade.A, detail.grade)
    }

    @Test
    fun testDistractingApp_prolongedUsageLogAcceleration() {
        val apps = listOf(
            AppUsage(
                packageName = "com.instagram.android",
                appName = "Instagram",
                usageTimeMillis = 60 * 60 * 1000L, // 60분 연속 사용
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // 60분: 15분 초과로 로그 가속도 적용: 1 + ln(1 + 45/30) = 1 + ln(2.5) = 1.9163x
        // 감점: 60 * 0.8 * 1.9163 = 약 91.98점 감점 -> 최종 약 8점 (F등급)
        val detail = ScoreCalculator.calculateScore(
            apps,
            0,
            0,
            ScoreRule(
                distractingWeightPerMinute = 0.8f,
                logAccelerationThresholdMinutes = 15f,
                logAccelerationScaleMinutes = 30f
            )
        )
        assertTrue(detail.distractingPenalty > 90f)
        assertEquals(8, detail.finalScore)
        assertEquals(ScoreGrade.F, detail.grade)
    }

    @Test
    fun testLateNightPenalty_acceleratedDeduction() {
        val apps = listOf(
            AppUsage(
                packageName = "com.youtube",
                appName = "YouTube",
                usageTimeMillis = 30 * 60 * 1000L, // 30분
                categoryType = AppCategoryType.DISTRACTING,
                lateNightUsageMillis = 30 * 60 * 1000L // 30분 모두 심야(00~05시) 사용
            )
        )
        // 로그 가속: 30분 -> 1 + ln(1 + 15/30) = 1.4055x -> 30 * 0.8 * 1.4055 = 33.73점
        // 심야 추가 감점: 30분 * 0.8 * (1.6 - 1.0) = 14.4점
        // 총 감점: 33.73 + 14.4 = 48.13점 -> 100 - 48.13 = 52점 (D등급)
        val detail = ScoreCalculator.calculateScore(
            apps,
            0,
            0,
            ScoreRule(
                distractingWeightPerMinute = 0.8f,
                lateNightMultiplier = 1.6f,
                logAccelerationThresholdMinutes = 15f,
                logAccelerationScaleMinutes = 30f
            )
        )
        assertEquals(14.4f, detail.lateNightPenalty, 0.01f)
        assertEquals(48.13f, detail.distractingPenalty, 0.1f)
        assertEquals(52, detail.finalScore)
        assertEquals(ScoreGrade.D, detail.grade)
    }

    @Test
    fun testIdleBonus_cappedAtMaximum() {
        // 수면 시간 8시간(480분) 및 장시간 미사용(1200분) 시에도 최대 15점으로 캡핑
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = 1200,
            unlockCount = 0
        )
        assertEquals(15.0f, detail.idleBonus, 0.01f)
        assertEquals(100, detail.finalScore)
    }

    @Test
    fun testInvalidNegativeIdle_isClampedToZero() {
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = -30,
            unlockCount = 0
        )
        assertEquals(0.0f, detail.idleBonus, 0.01f)
        assertEquals(100, detail.finalScore)
    }

    @Test
    fun testLateNightUsage_cannotExceedTotalUsage() {
        val apps = listOf(
            AppUsage(
                packageName = "com.example.video",
                appName = "Video",
                usageTimeMillis = 10 * 60 * 1000L,
                categoryType = AppCategoryType.DISTRACTING,
                lateNightUsageMillis = 60 * 60 * 1000L
            )
        )
        val detail = ScoreCalculator.calculateScore(apps, 0, 0)
        assertEquals(10L, detail.lateNightDistractingMinutes)
    }

    @Test
    fun briefApps_areSummedBeforeMinuteDisplayAggregation() {
        val apps = listOf(
            AppUsage("first", "First", 35_000L),
            AppUsage("second", "Second", 35_000L)
        )

        val detail = ScoreCalculator.calculateScore(apps, 0, 0)

        assertEquals(1L, detail.totalScreenTimeMinutes)
    }

    @Test
    fun testProductiveBonus_cappedAtMaximum() {
        // 1단계 성장 앱은 50%만 적용되며, 장시간 사용해도 최대 15점으로 제한됩니다.
        val apps = listOf(
            AppUsage(
                packageName = "notion.id",
                appName = "Notion",
                usageTimeMillis = 200 * 60 * 1000L,
                categoryType = AppCategoryType.PRODUCTIVE
            )
        )
        val detail = ScoreCalculator.calculateScore(apps, 0, 0)
        assertEquals(15.0f, detail.productiveBonus, 0.01f)
        assertEquals(100, detail.finalScore)
    }

    @Test
    fun testFiveLevelRatings_useAsymmetricBonusAndPenalty() {
        val apps = listOf(
            AppUsage(
                packageName = "level.two",
                appName = "Level 2",
                usageTimeMillis = 20 * 60_000L,
                categoryType = AppCategoryType.MILDLY_DISTRACTING
            ),
            AppUsage(
                packageName = "level.four",
                appName = "Level 4",
                usageTimeMillis = 20 * 60_000L,
                categoryType = AppCategoryType.MILDLY_PRODUCTIVE
            )
        )

        val detail = ScoreCalculator.calculateScore(
            appsUsage = apps,
            idleMinutes = 0,
            unlockCount = 0,
            rule = ScoreRule(
                distractingWeightPerMinute = 1f,
                productiveBonusPerMinute = 1f,
                maxProductiveBonus = 100f,
                isLogAccelerationEnabled = false
            )
        )

        assertEquals(10f, detail.distractingPenalty, 0.01f)
        assertEquals(5f, detail.productiveBonus, 0.01f)
        assertEquals(95, detail.finalScore)
        assertEquals(20L, detail.distractingTimeMinutes)
        assertEquals(20L, detail.productiveTimeMinutes)
    }

    @Test
    fun levelOneAndFive_sameMinutesNeverFullyCancel() {
        val detail = ScoreCalculator.calculateScore(
            appsUsage = listOf(
                AppUsage(
                    packageName = "education",
                    appName = "Education",
                    usageTimeMillis = 30 * 60_000L,
                    categoryType = AppCategoryType.PRODUCTIVE
                ),
                AppUsage(
                    packageName = "video",
                    appName = "Video",
                    usageTimeMillis = 30 * 60_000L,
                    categoryType = AppCategoryType.DISTRACTING
                )
            ),
            idleMinutes = 0,
            unlockCount = 0,
            rule = ScoreRule(
                distractingWeightPerMinute = 1f,
                productiveBonusPerMinute = 1f,
                maxProductiveBonus = 100f,
                isLogAccelerationEnabled = false
            )
        )

        assertEquals(30f, detail.distractingPenalty, 0.01f)
        assertEquals(15f, detail.productiveBonus, 0.01f)
        assertEquals(85, detail.finalScore)
    }

    @Test
    fun testUnlockPenalty_whenThresholdExceeded() {
        // threshold 25회, unlock 45회 -> excess 20회 * 0.5 = 10점 감점
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = 0,
            unlockCount = 45,
            rule = ScoreRule(unlockPenaltyThreshold = 25, unlockPenaltyPerCount = 0.5f)
        )
        assertEquals(90, detail.finalScore)
        assertEquals(10.0f, detail.unlockPenalty, 0.01f)
        assertEquals(ScoreGrade.S, detail.grade)
    }

    @Test
    fun testScoreClamping_minimumZero() {
        val apps = listOf(
            AppUsage(
                packageName = "com.tiktok.android",
                appName = "TikTok",
                usageTimeMillis = 200 * 60 * 1000L, // 200분
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        val detail = ScoreCalculator.calculateScore(apps, 0, 50)
        assertEquals(0, detail.finalScore)
        assertEquals(ScoreGrade.F, detail.grade)
    }

    @Test
    fun testYesterdayPenalty_reducesStartingScore() {
        // 전날 점수가 50점(D등급)이어서 15점의 시작 페널티(부채)를 안고 시작하는 상황
        val ruleWithYesterdayPenalty = ScoreRule(
            yesterdayPenalty = 15.0f,
            isYesterdayPenaltyEnabled = true
        )
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = 0,
            unlockCount = 0,
            rule = ruleWithYesterdayPenalty
        )
        assertEquals(15.0f, detail.yesterdayPenalty, 0.01f)
        assertEquals(85, detail.finalScore)
        assertEquals(ScoreGrade.A, detail.grade)
    }

    @Test
    fun testYesterdayPenalty_usesCustomTriggerRateAndCap() {
        val rule = ScoreRule(
            yesterdayPenaltyTriggerScore = 60,
            yesterdayPenaltyRate = 0.2f,
            maxYesterdayPenalty = 10f
        )

        assertEquals(4f, ScoreCalculator.calculateYesterdayPenalty(40, rule), 0.01f)
        assertEquals(10f, ScoreCalculator.calculateYesterdayPenalty(0, rule), 0.01f)
        assertEquals(0f, ScoreCalculator.calculateYesterdayPenalty(70, rule), 0.01f)
    }

    @Test
    fun testLogAcceleration_customThresholdChangesWhenAccelerationStarts() {
        val apps = listOf(
            AppUsage(
                packageName = "media.app",
                appName = "Media",
                usageTimeMillis = 45 * 60_000L,
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        val gentle = ScoreCalculator.calculateScore(
            apps,
            0,
            0,
            ScoreRule(
                distractingWeightPerMinute = 0.5f,
                logAccelerationThresholdMinutes = 60f,
                logAccelerationScaleMinutes = 120f
            )
        )
        val early = ScoreCalculator.calculateScore(
            apps,
            0,
            0,
            ScoreRule(
                distractingWeightPerMinute = 0.5f,
                logAccelerationThresholdMinutes = 30f,
                logAccelerationScaleMinutes = 120f
            )
        )

        assertEquals(22.5f, gentle.distractingPenalty, 0.01f)
        assertTrue(early.distractingPenalty > gentle.distractingPenalty)
    }
}
