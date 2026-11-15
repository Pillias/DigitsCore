package com.digitscore.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IndexShareCardTest {
    @Test
    fun shareTextCarriesScoreTierAndStoreLink() {
        val ko = IndexShareCard.shareText(82, "안정", isEn = false)
        assertTrue(ko.startsWith("지금 내 코어 지수 82P (안정)."))
        assertTrue(ko.endsWith(IndexShareCard.PLAY_URL))

        val en = IndexShareCard.shareText(55, "Caution", isEn = true)
        assertTrue(en.startsWith("My Core Index right now: 55P (Caution)."))
        assertTrue(en.endsWith(IndexShareCard.PLAY_URL))
        assertFalse(en.contains("코어"))
    }

    @Test
    fun streakLineShowsStageAndOnlyPositiveStreak() {
        assertEquals("도룡뇽 2단계 · 연속 9일", IndexShareCard.streakLine(2, 9, isEn = false))
        assertEquals("도룡뇽 1단계", IndexShareCard.streakLine(1, 0, isEn = false))
        assertEquals("Salamander stage 4 · 31-day streak", IndexShareCard.streakLine(4, 31, isEn = true))
        // 저장값이 범위를 벗어나도 1~4단계로 맞춤
        assertEquals("Salamander stage 4", IndexShareCard.streakLine(7, 0, isEn = true))
    }
}
