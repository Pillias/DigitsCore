package com.digitscore.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.digitscore.app.data.dao.AppDao
import com.digitscore.app.data.dao.ScoreDao
import com.digitscore.app.data.dao.SettingsDao
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.model.AppCategoryType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AppWeightEntity::class,
        DailyScoreHistoryEntity::class,
        UserSettingsEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DigitsDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun scoreDao(): ScoreDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        @Volatile
        private var INSTANCE: DigitsDatabase? = null

        fun getInstance(context: Context): DigitsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DigitsDatabase::class.java,
                    "digitscore_database"
                )
                    .addCallback(DatabaseCallback(context.applicationContext))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
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
                    targetUnlockCount = 25,
                    isTrackingEnabled = true,
                    isNotificationEnabled = true
                )
            )

            // 2. 대표적인 앱들에 대한 기본 카테고리 프리셋 시딩
            val initialAppWeights = listOf(
                // 방해 / SNS / 오락 앱
                AppWeightEntity("com.instagram.android", "Instagram", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.zhiliaoapp.musically", "TikTok", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.google.android.youtube", "YouTube", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.facebook.katana", "Facebook", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.twitter.android", "X (Twitter)", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.netflix.mediaclient", "Netflix", AppCategoryType.DISTRACTING),
                AppWeightEntity("com.roblox.client", "Roblox", AppCategoryType.DISTRACTING),

                // 생산성 / 학습 / 유틸리티 앱
                AppWeightEntity("com.duolingo", "Duolingo", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("notion.id", "Notion", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.todoist", "Todoist", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.google.android.apps.docs", "Google Docs", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.slack", "Slack", AppCategoryType.PRODUCTIVE),
                AppWeightEntity("com.ankiandroid", "AnkiDroid", AppCategoryType.PRODUCTIVE)
            )
            db.appDao().insertAppWeights(initialAppWeights)
        }
    }
}
