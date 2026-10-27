package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LongestRestTest {
    private val m = 60_000L
    private fun s(ts: Long) = CoreIndexSampleEntity(ts, ts, "d", 70, 70.0, 0.0, 0.0, "b")

    @Test
    fun longestGapBetweenScreenOnSamples() {
        val samples = listOf(0L, 5, 10, 130, 135, 200, 205).map { s(it * m) }
        assertEquals(120L, LongestRest.minutes(samples, 0L, 300 * m))
    }

    @Test
    fun shortGapsAreNotRest() {
        val samples = listOf(0L, 5, 10, 15).map { s(it * m) }
        assertEquals(0L, LongestRest.minutes(samples, 0L, 20 * m))
    }

    @Test
    fun gainIsScoreChangeAcrossTheLongestRest() {
        val samples = listOf(
            CoreIndexSampleEntity(0, 0, "d", 70, 70.0, 0.0, 0.0, "b"),
            CoreIndexSampleEntity(60 * m, 60 * m, "d", 71, 71.2, 0.0, 0.0, "b")
        )
        val rest = LongestRest.find(samples, 0L, 100 * m)!!
        assertEquals(60L, rest.minutes)
        assertEquals(1.2, rest.gain, 1e-9)
    }
}
