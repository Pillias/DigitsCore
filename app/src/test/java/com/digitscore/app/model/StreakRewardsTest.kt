package com.digitscore.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakRewardsTest {
    @Test
    fun stagesGrowAtSevenFourteenThirty() {
        assertEquals(1, StreakRewards.stageForStreak(6))
        assertEquals(2, StreakRewards.stageForStreak(7))
        assertEquals(3, StreakRewards.stageForStreak(14))
        assertEquals(4, StreakRewards.stageForStreak(30))
        assertEquals(1, StreakRewards.daysToNextStage(6, 1))
    }

    @Test
    fun threePerfectDaysEarnAShieldThatKeepsTheStreak() {
        var s = GoalRewardState(currentStreak = 6)
        repeat(3) { s = StreakRewards.settle(s, maintained = true, perfect = true, date = "d$it").state }
        assertEquals(9, s.currentStreak)
        assertEquals(1, s.streakShields)
        assertEquals(2, s.salamanderStage)
        val miss = StreakRewards.settle(s, maintained = false, perfect = false, date = "x")
        assertTrue(miss.shieldUsed)
        assertEquals(9, miss.state.currentStreak)
        assertEquals(0, miss.state.streakShields)
        assertEquals(2, miss.state.salamanderStage)
    }

    @Test
    fun breakingWithoutShieldDropsOneStage() {
        val s = GoalRewardState(currentStreak = 20, salamanderStage = 3, bestStreak = 20)
        val r = StreakRewards.settle(s, maintained = false, perfect = false, date = "x")
        assertFalse(r.shieldUsed)
        assertEquals(0, r.state.currentStreak)
        assertEquals(2, r.state.salamanderStage)
        assertEquals(20, r.state.bestStreak)
    }

    @Test
    fun personalBestIgnoresThinOrImportedDays() {
        val (a, k1) = PersonalBest.update(PersonalBest(), "d1", 120, 40, 50, imported = false)
        assertEquals(setOf(PersonalBest.Kind.MANAGED, PersonalBest.Kind.UNLOCKS), k1)
        val (b, k2) = PersonalBest.update(a, "d2", 10, 0, 3, imported = false) // 화면 10min: 제외
        assertEquals(a, b); assertTrue(k2.isEmpty())
        val (c, k3) = PersonalBest.update(a, "d3", 200, 25, 70, imported = false)
        assertEquals(25L, c.minManagedMinutes); assertEquals(setOf(PersonalBest.Kind.MANAGED), k3)
        val (_, k4) = PersonalBest.update(c, "d4", 200, 5, 10, imported = true)
        assertTrue(k4.isEmpty())
    }
}
