package com.digitscore.app.ui.statistics

import com.digitscore.app.data.*
import com.digitscore.app.data.entity.*
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class ExplorerDataTest {
    @Test fun hourlyScoreBecomesDailyOhlcWithoutMixingModelsOrFillingMissingDays() {
        val h = STAT_HOUR
        val rows = listOf(ScoreHourEntity(0,4,0,1000,90.0,92.0,90.0,92.0),
            ScoreHourEntity(h,5,h,h+1000,75.0,70.0,68.0,76.0),
            ScoreHourEntity(2*h,5,2*h,2*h+1000,70.0,73.0,69.0,74.0))
        val buckets = explorerBuckets(0, 48*h, true, emptyList(), rows, emptyList(), zone=ZoneId.of("UTC"))
        val day = buckets.first().score!!
        assertEquals(5, day.model)
        assertEquals(75.0, day.first, 0.0); assertEquals(73.0, day.last, 0.0)
        assertEquals(68.0, day.low, 0.0); assertEquals(76.0, day.high, 0.0)
        assertNull(buckets.last().score); assertNull(buckets.last().usage)
    }

    @Test fun appRankingUsesActualLossAndNeverConvertsUsageIntoLoss() {
        val apps = explorerApps(listOf(UsageHourEntity(0,"video","Video",600_000,600_000,3,1),
            UsageHourEntity(0,"study","Study",900_000,0,2,0)),
            listOf(ScoreImpactHourEntity(0,"video",loss=1.25,firstAt=0,lastAt=1000),
                ScoreImpactHourEntity(0,"",loss=1.25,recovery=0.5,firstAt=0,lastAt=1000)),emptyList(),false)
        assertEquals(2, apps.size)
        assertNull(apps.single { it.pkg=="study" }.loss)
        assertEquals(1.25, apps.single { it.pkg=="video" }.loss!!,0.0)
    }

    @Test
    fun explorerWeekdayBuckets_aggregatesSevenWeekdaysCorrectly() {
        val zone = ZoneId.of("UTC")
        val monday1 = java.time.LocalDate.of(2026, 10, 12).atStartOfDay(zone).toInstant().toEpochMilli()
        val tuesday1 = monday1 + 24 * STAT_HOUR
        val monday2 = monday1 + 7 * 24 * STAT_HOUR

        val dayBuckets = listOf(
            ExplorerBucket(
                start = monday1, end = monday1 + 24 * STAT_HOUR,
                score = ScoreHourEntity(monday1, 5, monday1, monday1 + 1000, 70.0, 74.0, 68.0, 78.0),
                usage = 3_600_000L, managed = 1_800_000L, opens = 10, shortOpens = 2, unlocks = 40,
                loss = 2.0, recovery = 1.0
            ),
            ExplorerBucket(
                start = tuesday1, end = tuesday1 + 24 * STAT_HOUR,
                score = ScoreHourEntity(tuesday1, 5, tuesday1, tuesday1 + 1000, 80.0, 82.0, 79.0, 85.0),
                usage = 1_800_000L, managed = 600_000L, opens = 8, shortOpens = 1, unlocks = 20,
                loss = 1.0, recovery = 0.5
            ),
            ExplorerBucket(
                start = monday2, end = monday2 + 24 * STAT_HOUR,
                score = ScoreHourEntity(monday2, 5, monday2, monday2 + 1000, 74.0, 76.0, 72.0, 80.0),
                usage = 5_400_000L, managed = 2_400_000L, opens = 12, shortOpens = 4, unlocks = 60,
                loss = 3.0, recovery = 1.5
            )
        )

        val weekdays = explorerWeekdayBuckets(dayBuckets, zone)
        assertEquals(7, weekdays.size)

        val mon = weekdays[0]
        assertEquals(0L, mon.start)
        assertEquals(1L, mon.end)
        assertEquals(72.0, mon.score!!.first, 0.01)
        assertEquals(75.0, mon.score!!.last, 0.01)
        assertEquals(68.0, mon.score!!.low, 0.01)
        assertEquals(80.0, mon.score!!.high, 0.01)
        assertEquals(4_500_000L, mon.usage)
        assertEquals(2_100_000L, mon.managed)
        assertEquals(50, mon.unlocks)
        assertEquals(3, mon.shortOpens)

        val tue = weekdays[1]
        assertEquals(1L, tue.start)
        assertEquals(82.0, tue.score!!.last, 0.01)
        assertEquals(1_800_000L, tue.usage)
        assertEquals(20, tue.unlocks)

        val wed = weekdays[2]
        assertEquals(2L, wed.start)
        assertNull(wed.score)
        assertNull(wed.usage)
        assertNull(wed.unlocks)
        assertEquals(0, wed.shortOpens)
    }
}
