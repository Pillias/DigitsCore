package com.digitscore.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitscore.app.data.entity.CumulativeScoreStateEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.engine.CumulativeCheckpoint
import com.digitscore.app.engine.CumulativeScoreState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class CumulativeScoreStoreTest {
    @Test fun repeatedCollectionAndDatabaseRoundTripDoNotDoubleCharge() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            DigitsDatabase::class.java).build()
        try {
            val start = Instant.parse("2026-09-20T09:00:00Z").toEpochMilli()
            db.cumulativeScoreStateDao().put(CumulativeScoreStateEntity(payload = CumulativeRecord(
                CumulativeCheckpoint(start, CumulativeScoreState())).encode()))
            db.foregroundUsageSessionDao().insertAll(listOf(ForegroundUsageSessionEntity(
                packageName = "test.game", startTimeMillis = start, endTimeMillis = start + 60 * 60_000L,
                dateString = "2026-09-20", appName = "Game", categoryLevel = 3, isLateNight = false)))
            val now = start + 122 * 60_000L
            val first = CumulativeScoreStore.update(db, now, start - 60_000L, ZoneId.of("UTC"))
            val impacts = db.statisticsDao().allImpacts()
            val second = CumulativeScoreStore.update(db, now, start - 60_000L, ZoneId.of("UTC"))
            assertEquals(impacts, db.statisticsDao().allImpacts())
            assertTrue(impacts.any { it.packageName == "test.game" && it.loss > 0 })
            assertEquals(first.first.checkpoint, second.first.checkpoint)
            assertEquals(first.first, CumulativeRecord.decode(db.cumulativeScoreStateDao().get()!!.payload))
            assertTrue(first.first.checkpoint.state.recoveryBurden > 0.0)
            val beforeCorrection = first.first.checkpoint.state
            CumulativeScoreStore.confirmActivity(db, now, false)
            assertEquals(beforeCorrection, CumulativeRecord.decode(db.cumulativeScoreStateDao().get()!!.payload).checkpoint.state)
        } finally { db.close() }
    }
}
