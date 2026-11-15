package com.digitscore.app.data

import com.digitscore.app.data.entity.ScoreImpactHourEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayMovementHighlightsTest {
    private fun row(hour: Long, pkg: String, loss: Double, recovery: Double = 0.0) =
        ScoreImpactHourEntity(hour, pkg, loss, recovery, firstAt = hour, lastAt = hour)

    @Test
    fun picksTwoBiggestDropsAndOneRiseAtMost() {
        val rows = listOf(
            row(1, "", 3.0), row(1, "youtube", 2.5), row(1, "kakao", 0.5),
            row(2, "", 1.5), row(2, "insta", 1.5),
            row(3, "", 1.2), row(3, "game", 1.2),
            row(4, "", 0.0, 4.0),
            row(5, "", 0.0, 2.0),
            row(6, "", 0.2), row(6, StatisticsStore.UNLOCK_IMPACT_KEY, 0.2)
        )
        val h = DayMovementHighlights.from(rows)
        assertEquals(3, h.size)
        assertEquals(listOf(1L, 2L, 4L), h.map { it.hour })
        assertEquals("youtube", h[0].packageName)
        assertTrue(h[2].isRise)
    }

    @Test
    fun smallMovesAreNotShown() {
        assertTrue(DayMovementHighlights.from(listOf(row(1, "", 0.5), row(2, "", 0.0, 0.8))).isEmpty())
    }
}
