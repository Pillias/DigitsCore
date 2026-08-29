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
    fun testDistractingApp_penaltyDeduction() {
        val apps = listOf(
            AppUsage(
                packageName = "com.instagram.android",
                appName = "Instagram",
                usageTimeMillis = 60 * 60 * 1000L, // 60분
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // 기본 룰: 60분 * 0.8 = 48점 감점 -> 100 - 48 = 52점
        val detail = ScoreCalculator.calculateScore(apps, 0, 0)
        assertEquals(52, detail.finalScore)
        assertEquals(48.0f, detail.distractingPenalty, 0.01f)
        assertEquals(ScoreGrade.D, detail.grade)
    }

    @Test
    fun testProductiveBonus_andIdleBonus_withBalancedWeights() {
        val apps = listOf(
            AppUsage(
                packageName = "com.duolingo",
                appName = "Duolingo",
                usageTimeMillis = 20 * 60 * 1000L, // 20분
                categoryType = AppCategoryType.PRODUCTIVE
            ),
            AppUsage(
                packageName = "com.instagram.android",
                appName = "Instagram",
                usageTimeMillis = 30 * 60 * 1000L, // 30분
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // initial: 100
        // distracting: 30 * 0.8 = 24점 감점
        // productive: 20 * 0.2 = 4점 가산 (상한 15 이하)
        // idle: 120분 -> (120/10)*0.25 = 3점 가산 (상한 15 이하)
        // 총합: 100 - 24 + 4 + 3 = 83점
        val detail = ScoreCalculator.calculateScore(
            appsUsage = apps,
            idleMinutes = 120,
            unlockCount = 10
        )
        assertEquals(83, detail.finalScore)
        assertEquals(24.0f, detail.distractingPenalty, 0.01f)
        assertEquals(4.0f, detail.productiveBonus, 0.01f)
        assertEquals(3.0f, detail.idleBonus, 0.01f)
        assertEquals(ScoreGrade.A, detail.grade)
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
    fun testProductiveBonus_cappedAtMaximum() {
        // 생산성 앱을 200분(3시간 20분) 켜두어도 최대 15점으로 캡핑
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
    fun testUnlockPenalty_whenThresholdExceeded() {
        // threshold 25회, unlock 45회 -> excess 20회 * 0.5 = 10점 감점
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = 0,
            unlockCount = 45
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
        // 200 * 0.8 = 160점 감점 -> 100 - 160 = -60 -> 하한 0으로 클램핑
        val detail = ScoreCalculator.calculateScore(apps, 0, 50)
        assertEquals(0, detail.finalScore)
        assertEquals(ScoreGrade.F, detail.grade)
    }

    @Test
    fun testStudyPresetMode_stricterPenalty() {
        val studyRule = PresetMode.STUDY.scoreRule
        val apps = listOf(
            AppUsage(
                packageName = "com.youtube",
                appName = "YouTube",
                usageTimeMillis = 30 * 60 * 1000L, // 30분
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // Study 모드: 30분 * 1.2 = 36점 감점
        // 언락: 25회 (threshold 15회 초과 10회 * 0.8 = 8점 감점)
        // 100 - 36 - 8 = 56점 -> C등급 (55점 이상)
        val detail = ScoreCalculator.calculateScore(
            appsUsage = apps,
            idleMinutes = 0,
            unlockCount = 25,
            rule = studyRule
        )
        assertEquals(56, detail.finalScore)
        assertEquals(ScoreGrade.C, detail.grade)
    }
}

