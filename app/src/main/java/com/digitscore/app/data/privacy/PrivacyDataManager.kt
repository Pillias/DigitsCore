package com.digitscore.app.data.privacy

import android.content.Context
import androidx.room.withTransaction
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.RollingUsageSummary
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.widget.WidgetSnapshotStore
import com.digitscore.app.widget.ScoreWidget
import com.digitscore.app.data.backup.DataBackupManager
import androidx.glance.appwidget.updateAll
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
        val db = DigitsDatabase.getInstance(context.applicationContext)
        db.dailyAppUsageDao().pruneBefore(cutoffDate)
        db.appDailyInteractionDao().pruneBefore(cutoffDate)
        com.digitscore.app.data.GoalLimitChoiceStore.pruneBefore(context.applicationContext, cutoffDate)
        com.digitscore.app.data.DailyGoalStore.pruneGoalDayRecordsBefore(context.applicationContext, cutoffDate)
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
            db.appDailyInteractionDao().deleteAll()
            db.foregroundUsageSessionDao().deleteAll()
            db.coreIndexSampleDao().deleteAll()
            db.cumulativeScoreStateDao().deleteAll()
            db.statisticsDao().deleteUsage()
            db.statisticsDao().deleteScores()
            db.statisticsDao().deleteImpacts()
            db.statisticsDao().deleteState()
            db.deviceInteractionEventDao().deleteAll()
            db.scoreDao().deleteAllScoreHistories()
        }
        appContext.getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        appContext.getSharedPreferences("daily_goal_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        appContext.getSharedPreferences("morning_prompt_state", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        WidgetSnapshotStore.clear(appContext)
        com.digitscore.app.data.GoalLimitChoiceStore.clearAll(appContext)
        com.digitscore.app.data.DailyGoalStore.clearDailyGoalRecords(appContext)
        com.digitscore.app.data.UsageHistoryImporter.clearImportedDates(appContext)
        com.digitscore.app.data.backup.RestoreBaseline.clear(appContext)
        // 점수에 따라 바뀐 런처 아이콘은 기록에서 나온 상태이므로 기본 아이콘으로 되돌립니다.
        // (홈 화면 갱신으로 앱이 닫히지 않도록 다음 화면 꺼짐 시점에 적용합니다.)
        // 신호등·공부 앱 목록·동적 아이콘 사용 여부 같은 사용자 설정은 유지합니다.
        com.digitscore.app.notification.LauncherIconManager.scheduleResetToDefault(appContext)
        com.digitscore.app.data.CumulativeScoreStore.clearWindowCache()
        ScoreRepository.updateRollingUsageSummary(RollingUsageSummary(emptyList(), 0L, 0L, 0L))
        ScoreRepository.updateUnlockCount(0)
        ScoreRepository.updateScoreDetail(null)
        ScoreRepository.updateRollingScoreDetail(null)
        ScoreRepository.updateCoreIndexGuidance(null)
        ScoreRepository.updateDailyGoal(null)
        ScoreRepository.updateYesterdaySummary(null)
        ScoreRepository.updateYesterdayGoalResult(null)
        ScoreRepository.updateRewardState(com.digitscore.app.model.GoalRewardState())
        ScoreRepository.updateMeasurementDiagnostics(com.digitscore.app.data.MeasurementDiagnostics())
        runCatching { com.digitscore.app.widget.DigitsWidgets.updateAll(appContext) }
        DataBackupManager.cleanupStaleBackups(appContext, maxAgeMillis = 0L)
    }
}
