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
        // 기본 룰: 60분 * 0.5 = 30점 감점 -> 70점
        val detail = ScoreCalculator.calculateScore(apps, 0, 0)
        assertEquals(70, detail.finalScore)
        assertEquals(30.0f, detail.distractingPenalty, 0.01f)
        assertEquals(ScoreGrade.B, detail.grade)
    }

    @Test
    fun testProductiveBonus_andIdleBonus() {
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
                usageTimeMillis = 40 * 60 * 1000L, // 40분
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // initial: 100
        // distracting: 40 * 0.5 = 20점 감점
        // productive: 20 * 0.3 = 6점 가산
        // idle: 120분 -> (120/10)*1.0 = 12점 가산
        // 총합: 100 - 20 + 6 + 12 = 98점 (100 상한선에 의해 98점)
        val detail = ScoreCalculator.calculateScore(
            appsUsage = apps,
            idleMinutes = 120,
            unlockCount = 10
        )
        assertEquals(98, detail.finalScore)
        assertEquals(20.0f, detail.distractingPenalty, 0.01f)
        assertEquals(6.0f, detail.productiveBonus, 0.01f)
        assertEquals(12.0f, detail.idleBonus, 0.01f)
    }

    @Test
    fun testUnlockPenalty_whenThresholdExceeded() {
        // threshold 30회, unlock 50회 -> excess 20회 * 0.2 = 4점 감점
        val detail = ScoreCalculator.calculateScore(
            appsUsage = emptyList(),
            idleMinutes = 0,
            unlockCount = 50
        )
        assertEquals(96, detail.finalScore)
        assertEquals(4.0f, detail.unlockPenalty, 0.01f)
    }

    @Test
    fun testScoreClamping_minimumZero() {
        val apps = listOf(
            AppUsage(
                packageName = "com.tiktok.android",
                appName = "TikTok",
                usageTimeMillis = 300 * 60 * 1000L, // 300분 (5시간)
                categoryType = AppCategoryType.DISTRACTING
            )
        )
        // 300 * 0.5 = 150점 감점 -> 100 - 150 = -50 -> 하한 0으로 클램핑
        val detail = ScoreCalculator.calculateScore(apps, 0, 100)
        assertEquals(0, detail.finalScore)
        assertEquals(ScoreGrade.F, detail.grade)
    }

    @Test
    fun testScoreClamping_maximumOneHundred() {
        // 생산성 앱 100분, idle 500분으로 보너스가 넘쳐도 100점 상한 유지
        val apps = listOf(
            AppUsage(
                packageName = "com.study.app",
                appName = "Study",
                usageTimeMillis = 100 * 60 * 1000L,
                categoryType = AppCategoryType.PRODUCTIVE
            )
        )
        val detail = ScoreCalculator.calculateScore(apps, 500, 5)
        assertEquals(100, detail.finalScore)
        assertEquals(ScoreGrade.S, detail.grade)
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
        // Study 모드: 30분 * 1.0 = 30점 감점 (일반 모드는 15점)
        // 언락: 30회 (threshold 20회 초과 10회 * 0.5 = 5점 감점)
        // 100 - 30 - 5 = 65점
        val detail = ScoreCalculator.calculateScore(
            appsUsage = apps,
            idleMinutes = 0,
            unlockCount = 30,
            rule = studyRule
        )
        assertEquals(65, detail.finalScore)
        assertEquals(ScoreGrade.C, detail.grade)
    }
}
