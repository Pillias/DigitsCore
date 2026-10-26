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
}
