package com.digitscore.app.data

import com.digitscore.app.data.entity.ScoreImpactHourEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class CoreIndexBreakdownTest {
    private fun row(hour: Long, pkg: String, loss: Double, recovery: Double = 0.0) =
        ScoreImpactHourEntity(hour, pkg, loss, recovery, firstAt = hour, lastAt = hour)

    @Test
    fun categoriesSumToTheIndexChange() {
        val rows = listOf(
            row(0, "", loss = 7.0, recovery = 4.3), // 전체: 감점 7 (앱 6.6 + 잠금 해제 0.4), 회복 4.3 (휴식 4.0 + 잠금 해제 0.3)
            row(0, "youtube", 4.0), row(0, "insta", 1.6), row(0, "kakao", 1.0),
            row(0, StatisticsStore.UNLOCK_IMPACT_KEY, loss = 0.4, recovery = 0.3)
        )
        val b = CoreIndexBreakdown.from(rows) { it == "youtube" || it == "insta" }
        assertEquals(-5.6, b.managedApps, 1e-9)
        assertEquals(-1.0, b.otherApps, 1e-9)
        assertEquals(-0.1, b.unlocks, 1e-9)
        assertEquals(4.0, b.rest, 1e-9)
        assertEquals(4.3 - 7.0, b.total, 1e-9)
        assertEquals("youtube", b.perApp.first().first)
    }

    @Test
    fun balancedDayStillShowsEachCategory() {
        // 감점과 회복이 같아 순변화가 0이어도 각 칸은 그대로 나옵니다(비례 배분 아님).
        val rows = listOf(row(0, "", loss = 5.0, recovery = 5.0), row(0, "game", 5.0))
        val b = CoreIndexBreakdown.from(rows) { true }
        assertEquals(-5.0, b.appUse, 1e-9)
        assertEquals(5.0, b.rest, 1e-9)
        assertEquals(0.0, b.total, 1e-9)
    }

    @Test
    fun unlockKeyIsNotAnApp() {
        val rows = listOf(row(0, "", 0.5), row(0, StatisticsStore.UNLOCK_IMPACT_KEY, 0.5))
        val b = CoreIndexBreakdown.from(rows) { false }
        assertEquals(0, b.perApp.size)
        assertEquals(-0.5, b.unlocks, 1e-9)
        assertEquals(0.0, b.appUse, 1e-9)
    }
}
