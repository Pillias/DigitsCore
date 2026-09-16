package com.digitscore.app.data

import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.RollingScoreDetail
import com.digitscore.app.engine.CoreIndexGuidance
import com.digitscore.app.model.AppUsage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 중앙 상태 저장소: Service와 UI를 분리하는 Repository
 * Service는 여기에 쓰고, ViewModel/UI는 여기서 읽는다.
 */
object ScoreRepository {

    private val _currentScoreDetail = MutableStateFlow<ScoreDetail?>(null)
    val currentScoreDetail = _currentScoreDetail.asStateFlow()

    private val _rollingScoreDetail = MutableStateFlow<RollingScoreDetail?>(null)
    val rollingScoreDetail = _rollingScoreDetail.asStateFlow()

    private val _currentAppsUsage = MutableStateFlow<List<AppUsage>>(emptyList())
    val currentAppsUsage = _currentAppsUsage.asStateFlow()

    private val _rollingUsageSummary = MutableStateFlow(
        RollingUsageSummary(emptyList(), 0L, 0L, 0L)
    )
    val rollingUsageSummary = _rollingUsageSummary.asStateFlow()

    private val _currentUnlockCount = MutableStateFlow(0)
    val currentUnlockCount = _currentUnlockCount.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning = _isServiceRunning.asStateFlow()

    private val _measurementDiagnostics = MutableStateFlow(MeasurementDiagnostics())
    val measurementDiagnostics = _measurementDiagnostics.asStateFlow()

    private val _coreIndexGuidance = MutableStateFlow<CoreIndexGuidance?>(null)
    val coreIndexGuidance = _coreIndexGuidance.asStateFlow()

    fun updateScoreDetail(detail: ScoreDetail?) {
        _currentScoreDetail.value = detail
    }

    fun updateRollingScoreDetail(detail: RollingScoreDetail?) {
        _rollingScoreDetail.value = detail
    }

    fun updateRollingUsageSummary(summary: RollingUsageSummary) {
        _rollingUsageSummary.value = summary
        _currentAppsUsage.value = summary.appsUsage
    }

    fun updateUnlockCount(count: Int) {
        _currentUnlockCount.value = count
    }

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
    }

    fun updateMeasurementDiagnostics(diagnostics: MeasurementDiagnostics) {
        _measurementDiagnostics.value = diagnostics
    }

    fun updateCoreIndexGuidance(guidance: CoreIndexGuidance?) {
        _coreIndexGuidance.value = guidance
    }
}
