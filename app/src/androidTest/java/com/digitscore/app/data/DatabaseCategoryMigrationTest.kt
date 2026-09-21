package com.digitscore.app.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseCategoryMigrationTest {
    private val databaseName = "digitscore-category-migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DigitsDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun deleteDatabase() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(databaseName)
    }

    @Test
    fun migration18To19KeepsCheckpointAndCreatesHourlyArchive() {
        helper.createDatabase(databaseName, 18).apply {
            execSQL("INSERT INTO cumulative_score_state(id,payload) VALUES (1,'checkpoint-preserved')")
            close()
        }
        val db = helper.runMigrationsAndValidate(databaseName, 19, true, DigitsDatabase.MIGRATION_18_19)
        db.query("SELECT payload FROM cumulative_score_state").use {
            assertEquals(true, it.moveToFirst()); assertEquals("checkpoint-preserved", it.getString(0))
        }
        listOf("usage_hourly", "score_hourly", "score_impact_hourly", "statistics_state").forEach { table ->
            db.query("SELECT count(*) FROM $table").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        }
        db.close()
    }

    @Test
    fun migration17To18PreservesScoreHistoryAndAddsEncryptedCheckpointTable() {
        helper.createDatabase(databaseName, 17).apply {
            execSQL("INSERT INTO app_weights(packageName, appName, categoryType, customWeight, isUserModified) VALUES ('study.app','Study','PRODUCTIVE',NULL,1)")
            execSQL("INSERT INTO app_weights(packageName, appName, categoryType, customWeight, isUserModified) VALUES ('manual.app','Manual','DISTRACTING',NULL,1)")
            execSQL("INSERT INTO core_index_samples(bucketStartTimestamp,timestampMillis,dateString,score,exactScore,rollingLoad,acuteLoad,presetId) VALUES (1000,1000,'2026-09-20',75,75.0,10.0,2.0,'balanced')")
            close()
        }
        val db = helper.runMigrationsAndValidate(databaseName, 18, true, DigitsDatabase.MIGRATION_17_18)
        db.query("SELECT score, scoreModelVersion FROM core_index_samples").use {
            assertEquals(true, it.moveToFirst())
            assertEquals(75, it.getInt(0)); assertEquals(4, it.getInt(1))
        }
        db.query("SELECT categoryType FROM app_weights WHERE packageName='manual.app'").use {
            it.moveToFirst(); assertEquals("DISTRACTING", it.getString(0))
        }
        db.execSQL("INSERT INTO cumulative_score_state(id,payload) VALUES (1,'{}')")
        db.query("SELECT count(*) FROM cumulative_score_state").use {
            it.moveToFirst(); assertEquals(1, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration13To16CanonicalizesThreeTiersAndInitializesSessionFields() {
        helper.createDatabase(databaseName, 13).apply {
            execSQL(
                "INSERT INTO app_weights(packageName, appName, categoryType, customWeight, isUserModified) " +
                    "VALUES ('study.app', 'Study', 'MILDLY_PRODUCTIVE', NULL, 1)"
            )
            execSQL(
                "INSERT INTO app_weights(packageName, appName, categoryType, customWeight, isUserModified) " +
                    "VALUES ('video.app', 'Video', 'MILDLY_DISTRACTING', NULL, 1)"
            )
            execSQL(
                "INSERT INTO daily_app_usage(dateString, packageName, appName, usageMillis, sessionCount, " +
                    "shortSessionCount, longestSessionMillis, lateNightUsageMillis, categoryLevel, lastUpdatedTimestamp) " +
                    "VALUES ('2026-09-16', 'study.app', 'Study', 60000, 1, 0, 60000, 0, 2, 1)"
            )
            execSQL(
                "INSERT INTO daily_app_usage(dateString, packageName, appName, usageMillis, sessionCount, " +
                    "shortSessionCount, longestSessionMillis, lateNightUsageMillis, categoryLevel, lastUpdatedTimestamp) " +
                    "VALUES ('2026-09-16', 'video.app', 'Video', 120000, 47, 40, 60000, 0, 5, 1)"
            )
            execSQL(
                "INSERT INTO foreground_usage_sessions(packageName, startTimeMillis, endTimeMillis, dateString, " +
                    "appName, categoryLevel, isLateNight, lastUpdatedTimestamp) " +
                    "VALUES ('video.app', 1000, 61000, '2026-09-16', 'Video', 5, 0, 1)"
            )
            execSQL(
                "INSERT INTO foreground_usage_sessions(packageName, startTimeMillis, endTimeMillis, dateString, " +
                    "appName, categoryLevel, isLateNight, lastUpdatedTimestamp) " +
                    "VALUES ('video.app', 61000, 121000, '2026-09-16', 'Video', 5, 0, 1)"
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            databaseName,
            16,
            true,
            DigitsDatabase.MIGRATION_13_14,
            DigitsDatabase.MIGRATION_14_15,
            DigitsDatabase.MIGRATION_15_16
        )

        migrated.query(
            "SELECT categoryType FROM app_weights WHERE packageName = 'study.app'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("PRODUCTIVE", cursor.getString(0))
        }
        migrated.query(
            "SELECT categoryType FROM app_weights WHERE packageName = 'video.app'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals("DISTRACTING", cursor.getString(0))
        }
        migrated.query(
            "SELECT categoryLevel FROM daily_app_usage WHERE packageName = 'study.app'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            "SELECT categoryLevel, effectivePackageName, effectiveCategoryLevel, concurrentAppCount, " +
                "sessionStartTimeMillis " +
                "FROM foreground_usage_sessions WHERE packageName = 'video.app' " +
                "ORDER BY startTimeMillis"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(3, cursor.getInt(0))
            assertEquals("video.app", cursor.getString(1))
            assertEquals(3, cursor.getInt(2))
            assertEquals(1, cursor.getInt(3))
            assertEquals(1_000L, cursor.getLong(4))
            cursor.moveToNext()
            assertEquals(1_000L, cursor.getLong(4))
        }
        migrated.query(
            "SELECT sessionCount, shortSessionCount, longestSessionMillis " +
                "FROM daily_app_usage WHERE packageName = 'video.app'"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(120_000L, cursor.getLong(2))
        }
        migrated.close()
    }
}
