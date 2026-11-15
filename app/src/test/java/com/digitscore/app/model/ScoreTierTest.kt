package com.digitscore.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScoreTierTest {
    @Test
    fun tierBoundariesMatchScoreColorBands() {
        assertEquals(ScoreTier.REST, ScoreTier.fromScore(1))
        assertEquals(ScoreTier.REST, ScoreTier.fromScore(39))
        assertEquals(ScoreTier.CAUTION, ScoreTier.fromScore(40))
        assertEquals(ScoreTier.CAUTION, ScoreTier.fromScore(59))
        assertEquals(ScoreTier.FAIR, ScoreTier.fromScore(60))
        assertEquals(ScoreTier.FAIR, ScoreTier.fromScore(79))
        assertEquals(ScoreTier.STABLE, ScoreTier.fromScore(80))
        assertEquals(ScoreTier.STABLE, ScoreTier.fromScore(99))
    }

    @Test
    fun nextTierStartIsTheBoundaryAbove() {
        assertEquals(80, ScoreTier.FAIR.nextTierMinScore)
        assertEquals(60, ScoreTier.CAUTION.nextTierMinScore)
        assertEquals(40, ScoreTier.REST.nextTierMinScore)
        assertNull(ScoreTier.STABLE.nextTierMinScore)
    }
}
