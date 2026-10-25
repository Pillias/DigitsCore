package com.digitscore.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RestPhasePolicyTest {
    private val minute = 60_000L
    private val zone = ZoneId.of("Asia/Kuching")
    private fun at(day: Int, hour: Int, min: Int = 0) =
        LocalDateTime.of(2026, 10, day, hour, min).atZone(zone).toInstant().toEpochMilli()

    @Test fun briefNightCheckDoesNotConfirmWake() {
        val start = at(20, 5)
        val segments = listOf(at(20, 5, 10) to at(20, 5, 11)) // 1분 시간 확인
        assertNull(RestPhasePolicy.morningWakeStart(segments, start))
    }

    @Test fun threeMinutesOfConnectedUseConfirmsWakeAtChainStart() {
        val start = at(20, 5)
        val segments = listOf(
            at(20, 5, 10) to at(20, 5, 11),           // 새벽 확인 (흐름 분리)
            at(20, 7, 0) to at(20, 7, 2),
            at(20, 7, 3) to at(20, 7, 4)              // 1분 간격으로 이어진 사용 → 합계 3분
        )
        assertEquals(at(20, 7, 0), RestPhasePolicy.morningWakeStart(segments, start))
    }

    @Test fun learnsLateSleeperWindowFromRecentNights() {
        val uses = (12..18).flatMap { day ->
            listOf(
                CumulativeUse(at(day, 20), at(day, 20, 30), false, "e$day"),
                CumulativeUse(at(day + 1, 1), at(day + 1, 1, 30), false, "n$day"),   // 01:30 취침
                CumulativeUse(at(day + 1, 9, 30), at(day + 1, 9, 45), false, "m$day") // 09:30 기상
            )
        }
        val window = RestPhasePolicy.learn(uses, zone)
        assertEquals(90, window.bedtimeMinute)
        assertEquals(9 * 60 + 30, window.wakeMinute)
    }
}
