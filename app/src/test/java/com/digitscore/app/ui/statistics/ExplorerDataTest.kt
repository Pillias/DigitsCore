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
}
