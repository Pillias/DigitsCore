package com.digitscore.app.model

import org.junit.Assert.*
import org.junit.Test

class DailyGoalTest {

    @Test
    fun testProgressRatio() {
        val goal = DailyGoal(
            dateString = "2026-10-03",
            type = DailyGoalType.APP_USAGE_LIMIT,
            targetAppName = "YouTube",
            targetValue = 30,
            currentValue = 15
        )
        assertEquals(0.5f, goal.progressRatio, 0.001f)
        assertFalse(goal.isExceeded)
        assertTrue(goal.isAchieved)
    }

    @Test
    fun testProgressExceeded() {
        val goal = DailyGoal(
            dateString = "2026-10-03",
            type = DailyGoalType.APP_USAGE_LIMIT,
            targetAppName = "YouTube",
            targetValue = 30,
            currentValue = 35
        )
        assertEquals(1.0f, goal.progressRatio, 0.001f)
        assertTrue(goal.isExceeded)
        assertFalse(goal.isAchieved)
    }

    @Test
    fun testScoreDefenseGoal() {
        val goal = DailyGoal(
            dateString = "2026-10-03",
            type = DailyGoalType.SCORE_DEFENSE,
            targetAppName = "코어 지수 방어",
            targetValue = 70,
            currentValue = 75
        )
        assertTrue(goal.isAchieved)
        assertFalse(goal.isExceeded)

        val failedGoal = goal.copy(currentValue = 65)
        assertFalse(failedGoal.isAchieved)
    }

    @Test
    fun testMultiGoalEvaluation() {
        val multiGoal = DailyGoal(
            dateString = "2026-10-06",
            scoreTarget = 70,
            currentScore = 75,
            targetPackageName = "com.google.android.youtube",
            targetAppName = "YouTube",
            appLimitMinutes = 30,
            currentAppUsageMinutes = 20,
            unlockLimitTarget = 40,
            currentUnlockCount = 25
        )

        // All 3 goals are currently within target
        assertTrue(multiGoal.isScoreDefenseAchieved)
        assertTrue(multiGoal.isAppLimitAchieved)
        assertTrue(multiGoal.isUnlockLimitAchieved)
        assertEquals(20f / 30f, multiGoal.appProgressRatio, 0.001f)
        assertEquals(25f / 40f, multiGoal.unlockProgressRatio, 0.001f)

        // When app limit is exceeded
        val exceededAppGoal = multiGoal.copy(currentAppUsageMinutes = 35)
        assertFalse(exceededAppGoal.isAppLimitAchieved)
        assertEquals(35f / 30f, exceededAppGoal.appProgressRatio, 0.001f)

        // When score falls below target
        val failedScoreGoal = multiGoal.copy(currentScore = 65)
        assertFalse(failedScoreGoal.isScoreDefenseAchieved)

        // When unlock count is exceeded
        val exceededUnlockGoal = multiGoal.copy(currentUnlockCount = 45)
        assertFalse(exceededUnlockGoal.isUnlockLimitAchieved)
    }

    @Test
    fun testUnlockGoalEvaluation() {
        val unlockGoal = DailyGoal(
            dateString = "2026-10-06",
            unlockLimitTarget = 40,
            currentUnlockCount = 35
        )
        assertEquals(DailyGoalType.UNLOCK_LIMIT, unlockGoal.type)
        assertTrue(unlockGoal.isAchieved)
        assertFalse(unlockGoal.isExceeded)
        assertEquals(35f / 40f, unlockGoal.progressRatio, 0.001f)

        val exceededUnlock = unlockGoal.copy(currentUnlockCount = 42)
        assertFalse(exceededUnlock.isAchieved)
        assertTrue(exceededUnlock.isExceeded)
    }

    @Test
    fun testLogicalDateCutoff() {
        // DailyGoalStore logical date produces yyyy-MM-dd
        val logicalDate = com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
        assertTrue(logicalDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))

        val yesterdayLogical = com.digitscore.app.data.DailyGoalStore.getYesterdayLogicalDateString()
        assertTrue(yesterdayLogical.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
        assertNotEquals(logicalDate, yesterdayLogical)
    }

    @Test
    fun testAppCandidateRecentAverageSupport() {
        val candidate = AppCandidate(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            yesterdayUsageMinutes = 0L,
            isRoutineHeavy = true,
            recentAvgMinutes = 64L
        )
        assertEquals(0L, candidate.yesterdayUsageMinutes)
        assertEquals(64L, candidate.recentAvgMinutes)
        assertTrue(candidate.isRoutineHeavy)
    }

    @Test
    fun testGoalDifficultyAdjustment() {
        val baseGoal = DailyGoal(
            dateString = "2026-10-14",
            scoreTarget = 70,
            appLimitMinutes = 30,
            unlockLimitTarget = 50
        )

        // NORMAL: unchanged
        val normal = com.digitscore.app.data.DailyGoalStore.applyDifficulty(baseGoal, GoalDifficulty.NORMAL)
        assertEquals(70, normal.scoreTarget)
        assertEquals(30, normal.appLimitMinutes)
        assertEquals(50, normal.unlockLimitTarget)

        // EASY: score -5, app +10m, unlock +10x
        val easy = com.digitscore.app.data.DailyGoalStore.applyDifficulty(baseGoal, GoalDifficulty.EASY)
        assertEquals(65, easy.scoreTarget)
        assertEquals(40, easy.appLimitMinutes)
        assertEquals(60, easy.unlockLimitTarget)

        // HARD: score +5, app -10m, unlock -10x
        val hard = com.digitscore.app.data.DailyGoalStore.applyDifficulty(baseGoal, GoalDifficulty.HARD)
        assertEquals(75, hard.scoreTarget)
        assertEquals(20, hard.appLimitMinutes)
        assertEquals(40, hard.unlockLimitTarget)
    }

    @Test
    fun testYesterdayGoalResultEvaluation() {
        val resultPerfect = YesterdayGoalResult(
            dateString = "2026-10-13",
            hasGoal = true,
            scoreTarget = 70,
            finalScore = 74,
            isScoreAchieved = true,
            targetPackageName = "com.google.android.youtube",
            targetAppName = "YouTube",
            appLimitMinutes = 45,
            finalAppUsageMinutes = 38,
            isAppLimitAchieved = true,
            unlockLimitTarget = 60,
            finalUnlockCount = 55,
            isUnlockLimitAchieved = true,
            achievedCount = 3,
            totalGoalCount = 3,
            isStreakMaintained = true,
            isPerfectDay = true
        )
        assertTrue(resultPerfect.isStreakMaintained)
        assertTrue(resultPerfect.isPerfectDay)
        assertEquals(3, resultPerfect.achievedCount)

        // 2 out of 3 achieved: Streak maintained, but not perfect day
        val resultPartial = resultPerfect.copy(
            finalUnlockCount = 65,
            isUnlockLimitAchieved = false,
            achievedCount = 2,
            isPerfectDay = false
        )
        assertTrue(resultPartial.isStreakMaintained)
        assertFalse(resultPartial.isPerfectDay)

        // 1 out of 3 achieved: Streak lost
        val resultFailed = resultPerfect.copy(
            finalScore = 65,
            isScoreAchieved = false,
            finalUnlockCount = 70,
            isUnlockLimitAchieved = false,
            achievedCount = 1,
            isStreakMaintained = false,
            isPerfectDay = false
        )
        assertFalse(resultFailed.isStreakMaintained)
        assertFalse(resultFailed.isPerfectDay)
    }

    @Test
    fun testGoalRewardStateAndFreePass() {
        val now = System.currentTimeMillis()
        val rewardState = GoalRewardState(
            currentStreak = 5,
            perfectStamps = 2,
            availableFreePasses = 1,
            activeFreePassUntil = now + 10 * 60 * 1000L // 10 minutes from now
        )

        assertTrue(rewardState.isFreePassActive)
        assertTrue(rewardState.remainingFreePassSeconds > 0)
        assertEquals(5, rewardState.currentStreak)
        assertEquals(2, rewardState.perfectStamps)
        assertEquals(1, rewardState.availableFreePasses)

        val expiredState = rewardState.copy(activeFreePassUntil = now - 1000L)
        assertFalse(expiredState.isFreePassActive)
        assertEquals(0L, expiredState.remainingFreePassSeconds)
    }

    @Test
    fun otherGoalValuesDoNotLeakIntoProgress() {
        val goal = DailyGoal(
            dateString = "2026-10-20",
            targetPackageName = "com.google.android.youtube",
            appLimitMinutes = 30,
            currentAppUsageMinutes = 12,
            unlockLimitTarget = 40,
            currentUnlockCount = 0
        )
        assertEquals(0f, goal.unlockProgressRatio, 0.0001f)
        val noApp = DailyGoal(dateString = "2026-10-20", unlockLimitTarget = 40, currentUnlockCount = 55)
        assertTrue(noApp.isAppLimitAchieved)
        assertEquals(0f, noApp.appProgressRatio, 0.0001f)
    }

    @Test
    fun testSeparateWeekdayWeekendLogic() {
        // Saturday & Sunday are weekends
        assertTrue(com.digitscore.app.data.DailyGoalStore.isWeekend("2026-10-24"))
        assertTrue(com.digitscore.app.data.DailyGoalStore.isWeekend("2026-10-25"))

        // Monday & Friday are weekdays
        assertFalse(com.digitscore.app.data.DailyGoalStore.isWeekend("2026-10-26"))
        assertFalse(com.digitscore.app.data.DailyGoalStore.isWeekend("2026-10-23"))

        val weekdayTargets = CustomGoalTargets(
            scoreTarget = 75,
            targetPackageName = "com.kakao.talk",
            targetAppName = "KakaoTalk",
            appLimitMinutes = 20,
            unlockLimitTarget = 30
        )
        val weekendTargets = CustomGoalTargets(
            scoreTarget = 65,
            targetPackageName = "com.google.android.youtube",
            targetAppName = "YouTube",
            appLimitMinutes = 60,
            unlockLimitTarget = 50
        )

        assertEquals(75, weekdayTargets.scoreTarget)
        assertEquals(65, weekendTargets.scoreTarget)
        assertEquals("com.kakao.talk", weekdayTargets.targetPackageName)
        assertEquals("com.google.android.youtube", weekendTargets.targetPackageName)
        assertEquals(20, weekdayTargets.appLimitMinutes)
        assertEquals(60, weekendTargets.appLimitMinutes)
    }

    @Test
    fun testAllManagedAppsGoal() {
        val allManagedGoal = DailyGoal(
            dateString = "2026-10-25",
            scoreTarget = 70,
            currentScore = 78,
            targetPackageName = DailyGoal.PACKAGE_ALL_MANAGED,
            targetAppName = "관리 앱 전체",
            appLimitMinutes = 60,
            currentAppUsageMinutes = 45,
            unlockLimitTarget = 40,
            currentUnlockCount = 20
        )

        assertTrue(allManagedGoal.isAllManagedApps)
        assertEquals(DailyGoalType.APP_USAGE_LIMIT, allManagedGoal.type)
        assertTrue(allManagedGoal.isAppLimitAchieved)
        assertEquals(45f / 60f, allManagedGoal.appProgressRatio, 0.001f)

        val exceeded = allManagedGoal.copy(currentAppUsageMinutes = 65)
        assertFalse(exceeded.isAppLimitAchieved)
        assertEquals(65f / 60f, exceeded.appProgressRatio, 0.001f)

        val targets = CustomGoalTargets(
            scoreTarget = 70,
            targetPackageName = DailyGoal.PACKAGE_ALL_MANAGED,
            targetAppName = "관리 앱 전체",
            appLimitMinutes = 60
        )
        assertTrue(targets.isAllManagedApps)
    }
}
