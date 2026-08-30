package com.digitscore.app.engine

import com.digitscore.app.model.PresetMode
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoringBenchmarkTest {
    @Test
    fun audiencePresets_scoreNearSixtyAtTheirReferenceUsage() {
        val presetById = PresetMode.entries.associateBy { it.id }

        ScoringBenchmark.entries.forEach { benchmark ->
            val rule = requireNotNull(presetById[benchmark.presetModeId]).scoreRule
            val score = benchmark.evaluate(rule).finalScore
            assertTrue(
                "${benchmark.title} 기준 예상 점수($score)가 60점 보정 범위를 벗어났습니다.",
                score in 55..65
            )
        }
    }
}
