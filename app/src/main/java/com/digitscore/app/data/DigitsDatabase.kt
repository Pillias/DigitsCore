package com.digitscore.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.digitscore.app.data.dao.AppDao
import com.digitscore.app.data.dao.DailyAppUsageDao
import com.digitscore.app.data.dao.ScoreDao
import com.digitscore.app.data.dao.SettingsDao
import com.digitscore.app.data.dao.ForegroundUsageSessionDao
import com.digitscore.app.data.dao.CoreIndexSampleDao
import com.digitscore.app.data.dao.DeviceInteractionEventDao
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.entity.DeviceInteractionEventEntity
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.data.security.DatabaseEncryptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        AppWeightEntity::class,
        DailyAppUsageEntity::class,
        DailyUsageCoverageEntity::class,
        DailyScoreHistoryEntity::class,
        UserSettingsEntity::class,
        ForegroundUsageSessionEntity::class,
        CoreIndexSampleEntity::class,
        DeviceInteractionEventEntity::class
    ],
    version = 16,
    exportSchema = true
)
abstract class DigitsDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun scoreDao(): ScoreDao
    abstract fun settingsDao(): SettingsDao
    abstract fun foregroundUsageSessionDao(): ForegroundUsageSessionDao
    abstract fun coreIndexSampleDao(): CoreIndexSampleDao
    abstract fun deviceInteractionEventDao(): DeviceInteractionEventDao

    companion object {
        private const val DATABASE_NAME = "digitscore_database"
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN logAccelerationThresholdMinutes REAL NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN logAccelerationScaleMinutes REAL NOT NULL DEFAULT 120")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN yesterdayPenaltyTriggerScore INTEGER NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN yesterdayPenaltyRate REAL NOT NULL DEFAULT 0.2")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN maxYesterdayPenalty REAL NOT NULL DEFAULT 10")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 사용자가 직접 바꾼 등급은 보존하고, 앱이 제공한 초기 추천만 새 기준으로 정렬합니다.
                db.execSQL(
                    """UPDATE app_weights SET categoryType = 'MILDLY_DISTRACTING'
                       WHERE isUserModified = 0 AND packageName IN (
                           'com.instagram.android', 'com.facebook.katana', 'com.twitter.android',
                           'com.alibaba.aliexpresshd'
                       )""".trimIndent()
                )
                db.execSQL(
                    """UPDATE app_weights SET categoryType = 'DISTRACTING'
                       WHERE isUserModified = 0 AND packageName IN (
                           'com.zhiliaoapp.musically', 'com.google.android.youtube',
                           'com.netflix.mediaclient', 'com.roblox.client'
                       )""".trimIndent()
                )
                db.execSQL(
                    """UPDATE app_weights SET categoryType = 'PRODUCTIVE'
                       WHERE isUserModified = 0 AND packageName IN (
                           'com.duolingo', 'com.ichi2.anki', 'com.ankiandroid'
                       )""".trimIndent()
                )
                db.execSQL(
                    """UPDATE app_weights SET categoryType = 'MILDLY_PRODUCTIVE'
                       WHERE isUserModified = 0 AND packageName IN (
                           'notion.id', 'com.todoist', 'com.google.android.apps.docs', 'com.slack'
                       )""".trimIndent()
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `daily_app_usage` (
                       `dateString` TEXT NOT NULL,
                       `packageName` TEXT NOT NULL,
                       `appName` TEXT NOT NULL,
                       `usageMillis` INTEGER NOT NULL,
                       `sessionCount` INTEGER NOT NULL,
                       `longestSessionMillis` INTEGER NOT NULL,
                       `lateNightUsageMillis` INTEGER NOT NULL,
                       `categoryLevel` INTEGER NOT NULL,
                       `lastUpdatedTimestamp` INTEGER NOT NULL,
                       PRIMARY KEY(`dateString`, `packageName`))""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_packageName` ON `daily_app_usage` (`packageName`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_app_usage_dateString` ON `daily_app_usage` (`dateString`)")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `daily_usage_coverage` (
                       `dateString` TEXT NOT NULL,
                       `isComplete` INTEGER NOT NULL,
                       `lastUpdatedTimestamp` INTEGER NOT NULL,
                       PRIMARY KEY(`dateString`))""".trimIndent()
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_settings ADD COLUMN appHistoryRetentionDays INTEGER NOT NULL DEFAULT 365")
                db.execSQL("ALTER TABLE user_settings ADD COLUMN hideSensitiveNotificationOnLockScreen INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // v6부터 세부 30일/일별 집계 365일 정책으로 고정합니다.
                db.execSQL("UPDATE user_settings SET appHistoryRetentionDays = 365")
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `foreground_usage_sessions` (
                       `packageName` TEXT NOT NULL,
                       `startTimeMillis` INTEGER NOT NULL,
                       `endTimeMillis` INTEGER NOT NULL,
                       `dateString` TEXT NOT NULL,
                       `appName` TEXT NOT NULL,
                       `categoryLevel` INTEGER NOT NULL,
                       `isLateNight` INTEGER NOT NULL,
                       `lastUpdatedTimestamp` INTEGER NOT NULL,
                       PRIMARY KEY(`packageName`, `startTimeMillis`))""".trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foreground_usage_sessions_dateString` ON `foreground_usage_sessions` (`dateString`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_foreground_usage_sessions_endTimeMillis` ON `foreground_usage_sessions` (`endTimeMillis`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN statusIconStyleId TEXT NOT NULL DEFAULT 'score_tier'"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN widgetBackgroundStyleId TEXT NOT NULL DEFAULT 'dark'"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 기존 행의 finalScore는 일일 초기화 방식이므로 코어 지수 통계와 섞지 않습니다.
                db.execSQL(
                    "ALTER TABLE daily_score_history ADD COLUMN scoreModelVersion INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN selectedCoreIndexPresetId TEXT NOT NULL DEFAULT 'balanced'"
                )
                db.execSQL(
                    """UPDATE user_settings
                       SET selectedCoreIndexPresetId = CASE selectedPresetModeId
                           WHEN 'study' THEN 'focus'
                           WHEN 'worker' THEN 'focus'
                           WHEN 'eye_health' THEN 'screen_rest'
                           WHEN 'kids' THEN 'family'
                           ELSE 'balanced'
                       END""".trimIndent()
                )
                // v9의 코어 지수는 모두 현재의 일상 균형 계수로 계산됐습니다.
                db.execSQL(
                    "ALTER TABLE daily_score_history ADD COLUMN coreIndexPresetId TEXT NOT NULL DEFAULT 'balanced'"
                )
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `core_index_samples` (
                       `bucketStartTimestamp` INTEGER NOT NULL,
                       `timestampMillis` INTEGER NOT NULL,
                       `dateString` TEXT NOT NULL,
                       `score` INTEGER NOT NULL,
                       `exactScore` REAL NOT NULL,
                       `rollingLoad` REAL NOT NULL,
                       `acuteLoad` REAL NOT NULL,
                       `presetId` TEXT NOT NULL,
                       PRIMARY KEY(`bucketStartTimestamp`))""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_core_index_samples_dateString` ON `core_index_samples` (`dateString`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_core_index_samples_timestampMillis` ON `core_index_samples` (`timestampMillis`)"
                )
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `device_interaction_events` (
                       `timestampMillis` INTEGER NOT NULL,
                       `eventType` INTEGER NOT NULL,
                       PRIMARY KEY(`timestampMillis`, `eventType`))""".trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_device_interaction_events_timestampMillis` ON `device_interaction_events` (`timestampMillis`)"
                )
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE daily_app_usage ADD COLUMN shortSessionCount INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        internal val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 사용자 선택과 과거 상세 기록을 5단계에서 성장/균형/몰입 관리 3단계로 통합합니다.
                db.execSQL(
                    """UPDATE app_weights SET categoryType = CASE categoryType
                       WHEN 'MILDLY_PRODUCTIVE' THEN 'PRODUCTIVE'
                       WHEN 'MILDLY_DISTRACTING' THEN 'DISTRACTING'
                       ELSE categoryType END""".trimIndent()
                )
                db.execSQL(
                    """UPDATE daily_app_usage SET categoryLevel = CASE
                       WHEN categoryLevel <= 2 THEN 1
                       WHEN categoryLevel = 3 THEN 2
                       ELSE 3 END""".trimIndent()
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `foreground_usage_sessions_new` (
                       `packageName` TEXT NOT NULL,
                       `startTimeMillis` INTEGER NOT NULL,
                       `endTimeMillis` INTEGER NOT NULL,
                       `dateString` TEXT NOT NULL,
                       `appName` TEXT NOT NULL,
                       `categoryLevel` INTEGER NOT NULL,
                       `effectivePackageName` TEXT NOT NULL,
                       `effectiveCategoryLevel` INTEGER NOT NULL,
                       `concurrentAppCount` INTEGER NOT NULL,
                       `isLateNight` INTEGER NOT NULL,
                       `lastUpdatedTimestamp` INTEGER NOT NULL,
                       PRIMARY KEY(`packageName`, `startTimeMillis`))""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO `foreground_usage_sessions_new` (
                       `packageName`, `startTimeMillis`, `endTimeMillis`, `dateString`, `appName`,
                       `categoryLevel`, `effectivePackageName`, `effectiveCategoryLevel`,
                       `concurrentAppCount`, `isLateNight`, `lastUpdatedTimestamp`)
                       SELECT `packageName`, `startTimeMillis`, `endTimeMillis`, `dateString`, `appName`,
                       CASE WHEN `categoryLevel` <= 2 THEN 1 WHEN `categoryLevel` = 3 THEN 2 ELSE 3 END,
                       `packageName`,
                       CASE WHEN `categoryLevel` <= 2 THEN 1 WHEN `categoryLevel` = 3 THEN 2 ELSE 3 END,
                       1, `isLateNight`, `lastUpdatedTimestamp`
                       FROM `foreground_usage_sessions`""".trimIndent()
                )
                db.execSQL("DROP TABLE `foreground_usage_sessions`")
                db.execSQL("ALTER TABLE `foreground_usage_sessions_new` RENAME TO `foreground_usage_sessions`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_foreground_usage_sessions_dateString` " +
                        "ON `foreground_usage_sessions` (`dateString`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_foreground_usage_sessions_endTimeMillis` " +
                        "ON `foreground_usage_sessions` (`endTimeMillis`)"
                )

            }
        }

        internal val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS `foreground_usage_sessions_new` (
                       `packageName` TEXT NOT NULL,
                       `startTimeMillis` INTEGER NOT NULL,
                       `endTimeMillis` INTEGER NOT NULL,
                       `dateString` TEXT NOT NULL,
                       `appName` TEXT NOT NULL,
                       `categoryLevel` INTEGER NOT NULL,
                       `effectivePackageName` TEXT NOT NULL,
                       `effectiveCategoryLevel` INTEGER NOT NULL,
                       `concurrentAppCount` INTEGER NOT NULL,
                       `sessionStartTimeMillis` INTEGER NOT NULL,
                       `isLateNight` INTEGER NOT NULL,
                       `lastUpdatedTimestamp` INTEGER NOT NULL,
                       PRIMARY KEY(`packageName`, `startTimeMillis`))""".trimIndent()
                )
                db.execSQL(
                    """INSERT INTO `foreground_usage_sessions_new` (
                       `packageName`, `startTimeMillis`, `endTimeMillis`, `dateString`, `appName`,
                       `categoryLevel`, `effectivePackageName`, `effectiveCategoryLevel`,
                       `concurrentAppCount`, `sessionStartTimeMillis`, `isLateNight`, `lastUpdatedTimestamp`)
                       SELECT `packageName`, `startTimeMillis`, `endTimeMillis`, `dateString`, `appName`,
                       `categoryLevel`, `effectivePackageName`, `effectiveCategoryLevel`,
                       `concurrentAppCount`, `startTimeMillis`, `isLateNight`, `lastUpdatedTimestamp`
                       FROM `foreground_usage_sessions`""".trimIndent()
                )
                db.execSQL("DROP TABLE `foreground_usage_sessions`")
                db.execSQL("ALTER TABLE `foreground_usage_sessions_new` RENAME TO `foreground_usage_sessions`")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_foreground_usage_sessions_dateString` " +
                        "ON `foreground_usage_sessions` (`dateString`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_foreground_usage_sessions_endTimeMillis` " +
                        "ON `foreground_usage_sessions` (`endTimeMillis`)"
                )

                // v14의 전면 조각 수를 실행 횟수로 저장했던 값을 최대한 복구합니다.
                // 같은 package에서 1초 이내로 이어진 조각은 Activity/PiP 분할로 보고
                // 동일한 세션 시작 시각을 부여합니다. 새 기록은 ACTIVITY_RESUMED에서
                // 생성된 정확한 sessionStartTimeMillis를 직접 저장합니다.
                data class LegacyFragment(
                    val packageName: String,
                    val startTimeMillis: Long,
                    val endTimeMillis: Long
                )
                val legacyFragments = mutableListOf<LegacyFragment>()
                db.query(
                    """SELECT `packageName`, `startTimeMillis`, `endTimeMillis`
                       FROM `foreground_usage_sessions`
                       ORDER BY `packageName`, `startTimeMillis`""".trimIndent()
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        legacyFragments += LegacyFragment(
                            packageName = cursor.getString(0),
                            startTimeMillis = cursor.getLong(1),
                            endTimeMillis = cursor.getLong(2)
                        )
                    }
                }
                var previousPackage: String? = null
                var previousEnd = Long.MIN_VALUE
                var currentSessionStart = 0L
                legacyFragments.forEach { fragment ->
                    val continuesSession = fragment.packageName == previousPackage &&
                        fragment.startTimeMillis <= previousEnd + 1_000L
                    if (!continuesSession) currentSessionStart = fragment.startTimeMillis
                    db.execSQL(
                        """UPDATE `foreground_usage_sessions`
                           SET `sessionStartTimeMillis` = ?
                           WHERE `packageName` = ? AND `startTimeMillis` = ?""".trimIndent(),
                        arrayOf(currentSessionStart, fragment.packageName, fragment.startTimeMillis)
                    )
                    previousPackage = fragment.packageName
                    previousEnd = if (continuesSession) {
                        maxOf(previousEnd, fragment.endTimeMillis)
                    } else {
                        fragment.endTimeMillis
                    }
                }

                data class DailySessionKey(val dateString: String, val packageName: String)
                val sessionDurations = mutableMapOf<DailySessionKey, MutableList<Long>>()
                db.query(
                    """SELECT `dateString`, `packageName`, `sessionStartTimeMillis`,
                       SUM(`endTimeMillis` - `startTimeMillis`)
                       FROM `foreground_usage_sessions`
                       GROUP BY `dateString`, `packageName`, `sessionStartTimeMillis`""".trimIndent()
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = DailySessionKey(cursor.getString(0), cursor.getString(1))
                        sessionDurations.getOrPut(key) { mutableListOf() } += cursor.getLong(3)
                    }
                }
                sessionDurations.forEach { (key, durations) ->
                    db.execSQL(
                        """UPDATE `daily_app_usage`
                           SET `sessionCount` = ?, `shortSessionCount` = ?, `longestSessionMillis` = ?
                           WHERE `dateString` = ? AND `packageName` = ?""".trimIndent(),
                        arrayOf(
                            durations.size,
                            durations.count { it in 1 until 60_000L },
                            durations.maxOrNull() ?: 0L,
                            key.dateString,
                            key.packageName
                        )
                    )
                }
            }
        }

        internal val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN isRapidUsageAlertEnabled INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN usePresetRapidAlertDefaults INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN rapidAlertWindowMinutes INTEGER NOT NULL DEFAULT 30"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN rapidAlertScoreDrop INTEGER NOT NULL DEFAULT 5"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN rapidAlertUsageMinutes INTEGER NOT NULL DEFAULT 24"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN rapidAlertContinuousMinutes INTEGER NOT NULL DEFAULT 35"
                )
                db.execSQL(
                    "ALTER TABLE user_settings ADD COLUMN rapidAlertCooldownMinutes INTEGER NOT NULL DEFAULT 90"
                )
            }
        }

        @Volatile
        private var INSTANCE: DigitsDatabase? = null

        fun getInstance(context: Context): DigitsDatabase {
            return INSTANCE ?: synchronized(this) {
                val databaseName = DATABASE_NAME
                val appContext = context.applicationContext
                val passphrase = prepareEncryptionOrFallback(appContext, databaseName)
                val builder = Room.databaseBuilder(
                    context.applicationContext,
                    DigitsDatabase::class.java,
                    databaseName
                )
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                        MIGRATION_8_9,
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .fallbackToDestructiveMigrationOnDowngrade()
                if (passphrase != null) {
                    builder.openHelperFactory(SupportOpenHelperFactory(passphrase))
                }
                val instance = builder.build()
                INSTANCE = instance
                instance
            }
        }

        private fun prepareEncryptionOrFallback(
            context: Context,
            databaseName: String
        ): ByteArray? {
            if (DatabaseEncryptionManager.isPlaintextForcedForProcess()) return null
            return try {
                DatabaseEncryptionManager.prepare(context, databaseName)
            } catch (error: Exception) {
                usePlaintextFallbackOrThrow(context, databaseName, error)
            } catch (error: LinkageError) {
                usePlaintextFallbackOrThrow(context, databaseName, error)
            }
        }

        private fun usePlaintextFallbackOrThrow(
            context: Context,
            databaseName: String,
            error: Throwable
        ): ByteArray? {
            if (!DatabaseEncryptionManager.canUsePlaintextFallback(context, databaseName)) {
                DatabaseEncryptionManager.markUnavailable(error)
                throw error
            }
            DatabaseEncryptionManager.markPlaintextFallback(error)
            return null
        }

        fun finalizeSuccessfulOpen(context: Context) {
            DatabaseEncryptionManager.finalizeSuccessfulOpen(
                context.applicationContext,
                DATABASE_NAME
            )
        }

        fun restorePlaintextAfterOpenFailure(context: Context, error: Throwable): Boolean {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
                return DatabaseEncryptionManager.restorePendingPlaintextBackup(
                    context.applicationContext,
                    DATABASE_NAME,
                    error
                )
            }
        }

        private class DatabaseCallback(
            private val appContext: Context
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    val database = getInstance(appContext)
                    seedInitialData(database)
                }
            }
        }

        private suspend fun seedInitialData(db: DigitsDatabase) {
            // 1. 기본 사용자 설정
            db.settingsDao().insertOrUpdateSettings(
                UserSettingsEntity(
                    id = 1,
                    selectedPresetModeId = "balanced",
                    minimumScoreDefenseLine = 60,
                    targetUnlockCount = 30,
                    isTrackingEnabled = false,
                    isNotificationEnabled = true
                )
            )

            // 2. 대표적인 앱들에 대한 기본 카테고리 프리셋 시딩
            val initialAppWeights = listOf(
                // 몰입 관리: SNS·뉴스·쇼핑·게임·동영상·음악처럼 흐름이 길어지기 쉬운 앱
                AppWeightEntity("com.instagram.android", "Instagram", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.facebook.katana", "Facebook", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.twitter.android", "X (Twitter)", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.alibaba.aliexpresshd", "AliExpress", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.zhiliaoapp.musically", "TikTok", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.google.android.youtube", "YouTube", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.netflix.mediaclient", "Netflix", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.roblox.client", "Roblox", AppCategoryType.DISTRACTING),

                // 성장: 교육·학습·생산성 앱
                AppWeightEntity("com.duolingo", "Duolingo", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.ichi2.anki", "AnkiDroid", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("notion.id", "Notion", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.todoist", "Todoist", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.google.android.apps.docs", "Google Docs", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.slack", "Slack", AppCategoryType.PRODUCTIVE)
            )
            db.appDao().insertAppWeights(initialAppWeights)
        }
    }
}
