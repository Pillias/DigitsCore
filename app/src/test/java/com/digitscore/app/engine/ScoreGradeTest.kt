package com.digitscore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreGradeTest {

    @Test
    fun testScoreGrade_thresholds() {
        assertEquals(ScoreGrade.S, ScoreGrade.fromScore(100))
        assertEquals(ScoreGrade.S, ScoreGrade.fromScore(90))
        assertEquals(ScoreGrade.A, ScoreGrade.fromScore(89))
        assertEquals(ScoreGrade.A, ScoreGrade.fromScore(80))
        assertEquals(ScoreGrade.B, ScoreGrade.fromScore(79))
        assertEquals(ScoreGrade.B, ScoreGrade.fromScore(70))
        assertEquals(ScoreGrade.C, ScoreGrade.fromScore(69))
        assertEquals(ScoreGrade.C, ScoreGrade.fromScore(55))
        assertEquals(ScoreGrade.D, ScoreGrade.fromScore(54))
        assertEquals(ScoreGrade.D, ScoreGrade.fromScore(40))
        assertEquals(ScoreGrade.F, ScoreGrade.fromScore(39))
        assertEquals(ScoreGrade.F, ScoreGrade.fromScore(0))
    }
}
