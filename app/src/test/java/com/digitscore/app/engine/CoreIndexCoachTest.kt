package com.digitscore.app.engine

import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.CoreIndexPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreIndexCoachTest {
    @Test
    fun repeatedShortSessions_areCountedAsAppOpens() {
        val now = 10 * 60_000L
        val sessions = listOf(
            RollingUsageSession("chat", 1_000L, 21_000L, 3),
            RollingUsageSession("chat", 60_000L, 105_000L, 3),
            RollingUsageSession("chat", 120_000L, 240_000L, 3)
        )
        val detail = RollingScoreCalculator.calculate(
            sessions,
            now,
            calibrationUsageMillis = 60 * 60_000L
        )

        val guidance = CoreIndexCoach.create(
            detail = detail,
            previousScore = detail.finalScore,
            sessions = sessions,
            apps = listOf(
                AppUsage(
                    "chat",
                    "Chat",
                    185_000L,
                    AppCategoryType.DISTRACTING,
                    sessionCount = 3,
                    shortSessionCount = 2
                )
            ),
            rollingUnlockTimestamps = emptyList(),
            histories = emptyList(),
            nowMillis = now,
            preset = CoreIndexPreset.BALANCED
        )

        assertEquals(3, guidance.todayOpenCount)
        assertEquals(2, guidance.shortOpenCount)
    }

    @Test
    fun pipLoadChange_doesNotCreateAnExtraAppOpen() {
        val now = 10 * 60_000L
        val sessions = listOf(
            RollingUsageSession("browser", 1_000L, 31_000L, 2, "browser"),
            RollingUsageSession("browser", 31_000L, 91_000L, 3, "video"),
            RollingUsageSession("browser", 91_000L, 121_000L, 2, "browser")
        )
        val detail = RollingScoreCalculator.calculate(
            sessions,
            now,
            calibrationUsageMillis = 60 * 60_000L
        )

        val guidance = CoreIndexCoach.create(
            detail = detail,
            previousScore = detail.finalScore,
            sessions = sessions,
            apps = listOf(
                AppUsage(
                    "browser",
                    "Browser",
                    120_000L,
                    AppCategoryType.NEUTRAL,
                    sessionCount = 1
                )
            ),
            rollingUnlockTimestamps = emptyList(),
            histories = emptyList(),
            nowMillis = now,
            preset = CoreIndexPreset.BALANCED
        )

        assertEquals(1, guidance.todayOpenCount)
        assertEquals(0, guidance.shortOpenCount)
    }

    @Test
    fun personalBaseline_usesTwoSevenDayWindows() {
        val histories = (1..14).map { day ->
            DailyScoreHistoryEntity(
                dateString = "2026-09-${day.toString().padStart(2, '0')}",
                finalScore = if (day <= 7) 70 else 80,
                totalScreenTimeMinutes = 0,
                distractingTimeMinutes = 0,
                productiveTimeMinutes = 0,
                idleMinutes = 0,
                unlockCount = 0,
                scoreModelVersion = 2
            )
        }
        val detail = RollingScoreCalculator.calculate(emptyList(), 1_000L, calibrationUsageMillis = 60 * 60_000L)

        val guidance = CoreIndexCoach.create(
            detail = detail,
            previousScore = 80,
            sessions = emptyList(),
            apps = emptyList(),
            rollingUnlockTimestamps = emptyList(),
            histories = histories,
            nowMillis = 1_000L,
            preset = CoreIndexPreset.BALANCED
        )

        assertEquals(80, guidance.recentSevenDayAverage)
        assertEquals(70, guidance.previousSevenDayAverage)
        assertTrue(guidance.scoreChange >= 0)
        assertNotNull(guidance.recommendation)
    }
}
