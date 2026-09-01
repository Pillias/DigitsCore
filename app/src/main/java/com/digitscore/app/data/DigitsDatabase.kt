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
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import com.digitscore.app.data.entity.UserSettingsEntity
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
        UserSettingsEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class DigitsDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun dailyAppUsageDao(): DailyAppUsageDao
    abstract fun scoreDao(): ScoreDao
    abstract fun settingsDao(): SettingsDao

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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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
                // 4단계: SNS·뉴스·쇼핑처럼 사용량 조절을 권장하는 앱
                AppWeightEntity("com.instagram.android", "Instagram", AppCategoryType.MILDLY_DISTRACTING),
                AppWeightEntity("com.facebook.katana", "Facebook", AppCategoryType.MILDLY_DISTRACTING),
                AppWeightEntity("com.twitter.android", "X (Twitter)", AppCategoryType.MILDLY_DISTRACTING),
                AppWeightEntity("com.alibaba.aliexpresshd", "AliExpress", AppCategoryType.MILDLY_DISTRACTING),

                // 5단계: 게임·동영상·음악처럼 몰입 시간이 길어지기 쉬운 앱
                AppWeightEntity("com.zhiliaoapp.musically", "TikTok", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.google.android.youtube", "YouTube", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.netflix.mediaclient", "Netflix", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.roblox.client", "Roblox", AppCategoryType.DISTRACTING),

                // 1단계: 교육·학습 앱
                AppWeightEntity("com.duolingo", "Duolingo", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.ichi2.anki", "AnkiDroid", AppCategoryType.PRODUCTIVE),

                // 2단계: 생산성과 목표 달성을 지원하는 앱
                AppWeightEntity("notion.id", "Notion", AppCategoryType.MILDLY_PRODUCTIVE),
                AppWeightEntity("com.todoist", "Todoist", AppCategoryType.MILDLY_PRODUCTIVE),
                AppWeightEntity("com.google.android.apps.docs", "Google Docs", AppCategoryType.MILDLY_PRODUCTIVE),
                AppWeightEntity("com.slack", "Slack", AppCategoryType.MILDLY_PRODUCTIVE)
            )
            db.appDao().insertAppWeights(initialAppWeights)
        }
    }
}
