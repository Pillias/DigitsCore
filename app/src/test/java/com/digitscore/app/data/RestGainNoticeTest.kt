package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestGainNoticeTest {
    private val m = 60_000L
    private fun s(ts: Long, score: Double) = CoreIndexSampleEntity(ts, ts, "d", score.toInt(), score, 0.0, 0.0, "b")

    @Test
    fun showsGainAfterLongScreenOff() {
        val now = 200 * m
        val samples = listOf(s(100 * m, 70.0), s(105 * m, 69.5), s(195 * m, 73.0))
        val gain = RestGainNotice.latest(samples, 73.4, now)!!
        assertEquals(105 * m, gain.restStartMillis)
        assertEquals(3, gain.points)
    }

    @Test
    fun ignoresSmallGainOrShortBreak() {
        val now = 200 * m
        assertNull(RestGainNotice.latest(listOf(s(150 * m, 70.0), s(195 * m, 71.0)), 71.2, now))
        assertNull(RestGainNotice.latest(listOf(s(190 * m, 70.0), s(195 * m, 72.5)), 72.5, now))
    }

    @Test
    fun noticeExpiresAfterFifteenMinutesOfUse() {
        val samples = listOf(s(100 * m, 70.0), s(160 * m, 74.0)) + (165..200 step 5).map { s(it * m, 74.0) }
        assertNull(RestGainNotice.latest(samples, 74.0, 200 * m))
    }

    @Test
    fun justTurnedOnUsesNow() {
        val samples = listOf(s(100 * m, 70.0))
        assertEquals(4, RestGainNotice.latest(samples, 74.2, 160 * m)!!.points)
    }
}
