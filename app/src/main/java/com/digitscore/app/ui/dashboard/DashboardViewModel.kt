package com.digitscore.app.ui.dashboard

import androidx.lifecycle.ViewModel
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.RollingScoreDetail
import com.digitscore.app.data.MeasurementDiagnostics
import com.digitscore.app.data.RollingUsageSummary
import com.digitscore.app.engine.CoreIndexGuidance
import com.digitscore.app.model.AppUsage
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel : ViewModel() {
    val scoreDetail: StateFlow<ScoreDetail?> = ScoreRepository.currentScoreDetail
    val rollingScoreDetail: StateFlow<RollingScoreDetail?> = ScoreRepository.rollingScoreDetail
    val appsUsage: StateFlow<List<AppUsage>> = ScoreRepository.currentAppsUsage
    val rollingUsageSummary: StateFlow<RollingUsageSummary> = ScoreRepository.rollingUsageSummary
    val unlockCount: StateFlow<Int> = ScoreRepository.currentUnlockCount
    val isServiceRunning: StateFlow<Boolean> = ScoreRepository.isServiceRunning
    val measurementDiagnostics: StateFlow<MeasurementDiagnostics> = ScoreRepository.measurementDiagnostics
    val coreIndexGuidance: StateFlow<CoreIndexGuidance?> = ScoreRepository.coreIndexGuidance
    val dailyGoal: StateFlow<com.digitscore.app.model.DailyGoal?> = ScoreRepository.dailyGoal
    val yesterdaySummary: StateFlow<com.digitscore.app.model.YesterdayBriefingSummary?> = ScoreRepository.yesterdaySummary
}
