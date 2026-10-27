package com.digitscore.app.engine

import com.digitscore.app.model.ScoreTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachActionPlannerTest {
    @Test
    fun recoveryTargetUsesNearbyTierBoundary() {
        assertEquals(80, CoachActionPlanner.recoveryTarget(76))
        assertEquals(80, CoachActionPlanner.recoveryTarget(75))
        assertEquals(77, CoachActionPlanner.recoveryTarget(74))
        assertEquals(60, CoachActionPlanner.recoveryTarget(57))
        assertEquals(90, CoachActionPlanner.recoveryTarget(89))
    }

    @Test
    fun actionAlwaysCarriesTargetWhenRecoveryIsKnown() {
        val a = CoachActionPlanner.plan(CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK, 15, 78, 74)
        assertEquals(CoachAction.Kind.REST_FOR, a.kind)
        assertEquals(15, a.minutes)
        assertEquals(78, a.targetScore)
        assertNull(a.reachesTier)
    }

    @Test
    fun reachingNextTierNamesTheTier() {
        val a = CoachActionPlanner.plan(CoreIndexRecommendation.KEEP_BALANCE, 20, 80, 77)
        assertEquals(ScoreTier.STABLE, a.reachesTier)
    }

    @Test
    fun recommendationPicksActionWording() {
        assertEquals(CoachAction.Kind.WIND_DOWN_REST, CoachActionPlanner.plan(CoreIndexRecommendation.WIND_DOWN, 30, 70, 66).kind)
        assertEquals(CoachAction.Kind.DELAY_CHECKS_REST, CoachActionPlanner.plan(CoreIndexRecommendation.BATCH_PHONE_CHECKS, 12, 70, 67).kind)
        assertEquals(CoachAction.Kind.DELAY_CHECKS, CoachActionPlanner.plan(CoreIndexRecommendation.BATCH_PHONE_CHECKS, null, null, 67).kind)
        assertEquals(CoachAction.Kind.KEEP, CoachActionPlanner.plan(CoreIndexRecommendation.KEEP_BALANCE, null, null, 85).kind)
        assertEquals(CoachAction.Kind.REST_GENERIC, CoachActionPlanner.plan(CoreIndexRecommendation.TAKE_QUIET_BREAK, null, null, 50).kind)
    }

    @Test
    fun chooseRecoveryPrefersDefenseLineThenNearTargetsWithinAnHour() {
        // 1점에 15분씩 걸린다고 가정: 방어선 75P(+1)가 15분이면 방어선을 고릅니다.
        val perPoint = { cur: Int -> { t: Int -> (t - cur) * 15 } }
        assertEquals(75 to 15, CoachActionPlanner.chooseRecovery(74, 75, perPoint(74)))
        // 방어선이 없으면 단계 경계(80P, +6)는 90분이라 건너뛰고 +3P(45분)
        assertEquals(77 to 45, CoachActionPlanner.chooseRecovery(74, null, perPoint(74)))
        // 1점에 40분이면 +1P만 1시간 안
        assertEquals(75 to 40, CoachActionPlanner.chooseRecovery(74, null) { t -> (t - 74) * 40 })
        // 1시간 안에 닿는 목표가 없으면 null
        assertNull(CoachActionPlanner.chooseRecovery(74, null) { null })
    }

    @Test
    fun noNearTargetFallsBackToShortRestGain() {
        val a = CoachActionPlanner.plan(CoreIndexRecommendation.BATCH_PHONE_CHECKS, null, null, 74, shortRestGain = 0.6)
        assertEquals(CoachAction.Kind.REST_FOR_GAIN, a.kind)
        assertEquals(30, a.minutes)
        val d = CoachActionPlanner.plan(CoreIndexRecommendation.TAKE_QUIET_BREAK, 15, 75, 74, defenseLine = 75)
        assertEquals(true, d.reachesDefense)
    }
}
