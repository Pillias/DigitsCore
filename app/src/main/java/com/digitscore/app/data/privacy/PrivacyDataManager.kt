package com.digitscore.app.data.privacy

import android.content.Context
import androidx.room.withTransaction
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.data.backup.DataBackupManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object PrivacyDataManager {
    suspend fun applyAppHistoryRetention(context: Context, retentionDays: Int) {
        val safeDays = retentionDays.coerceIn(30, 365)
        val cutoff = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -(safeDays - 1))
        }
        val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cutoff.time)
        DigitsDatabase.getInstance(context.applicationContext)
            .dailyAppUsageDao()
            .pruneBefore(cutoffDate)
    }

    /** 사용 기록만 삭제하고 앱 등급·점수 규칙 같은 사용자의 설정은 유지합니다. */
    suspend fun deleteAllUsageHistory(context: Context) {
        val appContext = context.applicationContext
        val db = DigitsDatabase.getInstance(appContext)
        val settings = db.settingsDao().getSettings()
        if (settings != null) {
            db.settingsDao().insertOrUpdateSettings(settings.copy(isTrackingEnabled = false))
        }
        TrackerForegroundService.stop(appContext)
        db.withTransaction {
            db.dailyAppUsageDao().deleteAllHistory()
            db.foregroundUsageSessionDao().deleteAll()
            db.coreIndexSampleDao().deleteAll()
            db.scoreDao().deleteAllScoreHistories()
        }
        appContext.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        ScoreRepository.updateAppsUsage(emptyList())
        ScoreRepository.updateUnlockCount(0)
        ScoreRepository.updateScoreDetail(null)
        ScoreRepository.updateRollingScoreDetail(null)
        DataBackupManager.cleanupStaleBackups(appContext, maxAgeMillis = 0L)
    }
}
