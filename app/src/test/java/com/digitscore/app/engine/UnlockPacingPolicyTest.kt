package com.digitscore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** DEBATE 안건 18 · docs/CORE_INDEX_UX_BENCHMARK.md '잠금 해제 점수 규칙'의 상황표를 고정합니다. */
class UnlockPacingPolicyTest {
    private val minute = 60_000L
    private val cfg = CumulativeScoreConfig()
    private val s75 = 75.0

    @Test
    fun referenceGapIsNeutral() = assertEquals(0.0, UnlockPacingPolicy.rawDelta(12 * minute, s75, cfg), 1e-9)

    @Test
    fun underTenSecondsIsIgnored() = assertEquals(0.0, UnlockPacingPolicy.rawDelta(9_000L, s75, cfg), 1e-9)

    @Test
    fun situationTableAt75() {
        // 1시간에 20회 (3분 간격) ≈ −0.75P
        assertEquals(-0.75, 20 * UnlockPacingPolicy.rawDelta(3 * minute, s75, cfg), 0.02)
        // 10분 동안 10회 (1분 간격) ≈ −0.46P
        assertEquals(-0.46, 10 * UnlockPacingPolicy.rawDelta(minute, s75, cfg), 0.02)
        // 하루 150회, 8분 간격 ≈ −2.5P
        assertEquals(-2.5, 150 * UnlockPacingPolicy.rawDelta(8 * minute, s75, cfg), 0.1)
        // 2시간 쉬고 켬: 잠금 해제 몫 +0.02P (상한)
        assertEquals(0.02, UnlockPacingPolicy.rawDelta(120 * minute, s75, cfg), 1e-9)
        // 18분: 12~24분 사이 선형 → +0.01P
        assertEquals(0.01, UnlockPacingPolicy.rawDelta(18 * minute, s75, cfg), 1e-9)
    }

    @Test
    fun dailyCapsStopLossAndGain() {
        var ledger = UnlockPacingLedger()
        var total = 0.0
        repeat(200) {
            val (applied, next) = UnlockPacingPolicy.capped(-0.05, ledger, "2026-10-26", cfg)
            total += applied; ledger = next
        }
        assertEquals(-3.0, total, 1e-9)
        // 다음 날이면 다시 셉니다.
        val (applied, _) = UnlockPacingPolicy.capped(-0.05, ledger, "2026-10-27", cfg)
        assertEquals(-0.05, applied, 1e-9)
        var gain = 0.0
        ledger = UnlockPacingLedger()
        repeat(100) {
            val (g, next) = UnlockPacingPolicy.capped(0.02, ledger, "d", cfg); gain += g; ledger = next
        }
        assertEquals(1.0, gain, 1e-9)
    }

    @Test
    fun higherScoreLosesSlightlyMoreAndLowerScoreGainsMore() {
        assertTrue(UnlockPacingPolicy.rawDelta(minute, 90.0, cfg) < UnlockPacingPolicy.rawDelta(minute, 75.0, cfg))
        assertTrue(UnlockPacingPolicy.rawDelta(60 * minute, 40.0, cfg) > UnlockPacingPolicy.rawDelta(60 * minute, 75.0, cfg))
    }
}
