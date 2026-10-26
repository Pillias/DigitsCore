package com.digitscore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockPacingTimelineTest {
    private val minute = 60_000L
    private val awake: (Long) -> CumulativeActivity = { CumulativeActivity.AWAKE_REST }
    private fun start(at: Long = 0L) = CumulativeCheckpoint(at, CumulativeScoreState())

    private fun unlockMovements(
        uses: List<CumulativeUse>, unlocks: List<Long>, through: Long,
        ledger: UnlockPacingLedger = UnlockPacingLedger(lastUseEndAt = 1L),
        rest: (Long) -> CumulativeActivity = awake,
        config: CumulativeScoreConfig = CumulativeScoreConfig()
    ): Pair<List<ScoreMovement>, UnlockPacingLedger> {
        val moves = mutableListOf<ScoreMovement>()
        var out = ledger
        CumulativeTimeline.advance(start(), through, uses, rest, config, onMovement = { moves += it },
            unlocks = unlocks, unlockLedger = ledger, onUnlockLedger = { out = it }, dayKeyOf = { "d" })
        return moves.filter { it.unlock } to out
    }

    @Test
    fun sixtyMinutesOfGameplayHasNoUnlockEffect() {
        val (moves, _) = unlockMovements(listOf(CumulativeUse(0, 60 * minute, true, "game")), emptyList(), 60 * minute)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun quickReUnlockAfterShortScreenOffCosts() {
        // 10분 사용 → 2분 꺼짐 → 잠금 해제 → 다시 사용
        val uses = listOf(CumulativeUse(0, 10 * minute, false, "a"), CumulativeUse(12 * minute, 20 * minute, false, "b"))
        val (moves, _) = unlockMovements(uses, listOf(12 * minute), 20 * minute)
        assertEquals(1, moves.size)
        assertTrue(moves.single().after < moves.single().before)
    }

    @Test
    fun longScreenOffGivesSmallGain() {
        val uses = listOf(CumulativeUse(0, 10 * minute, false, "a"), CumulativeUse(130 * minute, 135 * minute, false, "b"))
        val (moves, _) = unlockMovements(uses, listOf(130 * minute), 135 * minute)
        assertEquals(1, moves.size)
        val m = moves.single()
        assertTrue(m.after > m.before && m.after - m.before <= 0.02 * 4.0 + 1e-9)
    }

    @Test
    fun firstUnlockAfterSleepIsNeutral() {
        val uses = listOf(CumulativeUse(0, 10 * minute, false, "a"), CumulativeUse(300 * minute, 310 * minute, false, "b"))
        val sleepThenAwake: (Long) -> CumulativeActivity = { ts -> if (ts < 250 * minute) CumulativeActivity.SLEEP else CumulativeActivity.AWAKE_REST }
        val (moves, _) = unlockMovements(uses, listOf(300 * minute), 310 * minute, rest = sleepThenAwake)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun callOrNavigationUnlockIsNeutral() {
        val uses = listOf(CumulativeUse(0, 10 * minute, false, "a"),
            CumulativeUse(11 * minute, 20 * minute, false, "call", exempt = true, unlimitedExempt = true))
        val (moves, _) = unlockMovements(uses, listOf(11 * minute), 20 * minute)
        assertTrue(moves.isEmpty())
    }

    @Test
    fun unknownPreviousUseIsNeutral() {
        val uses = listOf(CumulativeUse(5 * minute, 10 * minute, false, "a"))
        val (moves, _) = unlockMovements(uses, listOf(5 * minute), 10 * minute, ledger = UnlockPacingLedger())
        assertTrue(moves.isEmpty())
    }

    @Test
    fun ledgerCarriesLastUseEndAcrossCalls() {
        val uses1 = listOf(CumulativeUse(0, 10 * minute, false, "a"))
        var ledger = UnlockPacingLedger(lastUseEndAt = 1L)
        CumulativeTimeline.advance(start(), 20 * minute, uses1, awake, unlocks = emptyList(),
            unlockLedger = ledger, onUnlockLedger = { ledger = it }, dayKeyOf = { "d" })
        assertEquals(10 * minute, ledger.lastUseEndAt)
        // 다음 호출에는 이전 사용이 목록에 없어도 장부로 간격(2분)을 압니다.
        val moves = mutableListOf<ScoreMovement>()
        CumulativeTimeline.advance(start(20 * minute), 30 * minute, emptyList(), awake, onMovement = { moves += it },
            unlocks = listOf(22 * minute), unlockLedger = ledger.copy(lastUseEndAt = 20 * minute), dayKeyOf = { "d" })
        assertEquals(1, moves.count { it.unlock && it.after < it.before })
    }

    @Test
    fun v2ConfigIgnoresUnlocks() {
        val uses = listOf(CumulativeUse(0, 10 * minute, false, "a"), CumulativeUse(12 * minute, 20 * minute, false, "b"))
        val (moves, _) = unlockMovements(uses, listOf(12 * minute), 20 * minute, config = CumulativeScoreConfig(version = 2))
        assertTrue(moves.isEmpty())
    }

    @Test
    fun noLedgerMeansNoUnlockEffect() {
        val uses = listOf(CumulativeUse(0, 10 * minute, false, "a"), CumulativeUse(12 * minute, 20 * minute, false, "b"))
        val plain = CumulativeTimeline.advance(start(), 20 * minute, uses, awake)
        val withUnlocksNoLedger = CumulativeTimeline.advance(start(), 20 * minute, uses, awake, unlocks = listOf(12 * minute))
        assertEquals(plain, withUnlocksNoLedger)
    }
}
