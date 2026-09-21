package com.digitscore.app.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitscore.app.data.backup.StatisticsBackup
import com.digitscore.app.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatisticsArchiveTest {
    private fun database() = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
        DigitsDatabase::class.java).build()

    @Test fun refreshIsIdempotentAndArchiveOutlivesRawSessionsAndRestores() = runBlocking {
        val db = database(); val restored = database()
        try {
            val now = statisticHour(System.currentTimeMillis())
            val start = now - 2*STAT_HOUR
            db.foregroundUsageSessionDao().insertAll(listOf(ForegroundUsageSessionEntity(
                "video", start, start+50_000, "2026-09-21", "Video", 3, isLateNight=false)))
            StatisticsStore.refresh(db, now)
            val original = db.statisticsDao().allUsage()
            StatisticsStore.refresh(db, now)
            assertEquals(original, db.statisticsDao().allUsage())
            assertEquals(1, original.filter { it.packageName=="video" }.sumOf { it.opens })
            assertEquals(1, original.filter { it.packageName=="video" }.sumOf { it.shortOpens })
            db.foregroundUsageSessionDao().pruneBefore(now+STAT_HOUR)
            StatisticsStore.refresh(db, now+35L*24*STAT_HOUR)
            assertEquals(50_000L, db.statisticsDao().allUsage().filter { it.packageName=="video" }.sumOf { it.usageMillis })
            val backup = StatisticsBackup.export(db)
            restored.withTransaction { StatisticsBackup.restore(restored, backup, now+36L*24*STAT_HOUR) }
            assertEquals(db.statisticsDao().allUsage(), restored.statisticsDao().allUsage())
            // A second restore must not double archived counts.
            restored.withTransaction { StatisticsBackup.restore(restored, backup, now+36L*24*STAT_HOUR) }
            assertEquals(db.statisticsDao().allUsage(), restored.statisticsDao().allUsage())
        } finally { db.close(); restored.close() }
    }
}
