package com.digitscore.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.Process
import androidx.core.content.ContextCompat
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.DailyGoalStore
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.TodayUsageSnapshot
import com.digitscore.app.data.MeasurementDiagnostics
import com.digitscore.app.data.summarizeRollingUsage
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.entity.DeviceInteractionEventEntity
import com.digitscore.app.data.entity.applyTo
import com.digitscore.app.data.entity.effectiveRapidUsageAlertConfig
import com.digitscore.app.data.entity.effectiveScoringConfig
import com.digitscore.app.model.defaultScoringConfig
import com.digitscore.app.engine.RapidUsageAlertDetector
import com.digitscore.app.engine.RapidUsageObservation
import com.digitscore.app.engine.ScoreCalculator
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.RollingScoreCalculator
import com.digitscore.app.engine.RollingUsageSession
import com.digitscore.app.engine.CoreIndexCoach
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.PresetMode
import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.RapidUsageAlertConfig
import com.digitscore.app.model.defaultRapidUsageAlertConfig
import com.digitscore.app.notification.ScoreNotificationManager
import com.digitscore.app.notification.StatusIconStyle
import com.digitscore.app.receiver.ScreenEventReceiver
import com.digitscore.app.widget.ScoreWidget
import com.digitscore.app.widget.WidgetSnapshot
import com.digitscore.app.widget.WidgetSnapshotStore
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class TrackerForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recalcMutex = Mutex()
    private var trackingJob: Job? = null
    private var screenEventReceiver: ScreenEventReceiver? = null

    private var lastScreenOffTimestamp: Long = 0L
    private var accumulatedIdleMinutes: Long = 0L
    private var todayUnlockCount: Int = 0
    private var currentDateString: String = getTodayDateString()
    private var lastRenderedWidgetSnapshot: WidgetSnapshot? = null
    private var lastWidgetUpdatedAt: Long = 0L
    private var lastSamplePrunedAt: Long = 0L
    private var cachedTodaySnapshot: TodayUsageSnapshot? = null
    private var lastUsageQueryEndMillis: Long = 0L
    private var interactionBootstrapDone = false
    private var pendingWakeTimestamp: Long = 0L
    private var pendingWakeBaseUsageMillis: Long = 0L

    private val wakeSteps by lazy { WakeStepEvidence(this) { timestamp ->
        pendingWakeTimestamp = timestamp
        pendingWakeBaseUsageMillis = cachedTodaySnapshot?.assignedUsageMillis ?: 0L
        ScoreNotificationManager.showMorningWakePromptNotification(this, timestamp)
        serviceScope.launch(Dispatchers.IO) {
            com.digitscore.app.data.CumulativeScoreStore.confirmActivity(
                DigitsDatabase.getInstance(applicationContext), timestamp, awake = true, acknowledge = false)
            recalculateAndNotify()
        }
    } }
    private var cumulativeCoverageStart = UsageStatsHelper.getStartOfTodayMillis()
    private var lastAppWeightSignature: Int? = null

    private val trackingPreferences by lazy {
        getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_STOP_TRACKING = "com.digitscore.app.action.STOP_TRACKING"
        const val ACTION_REFRESH_NOTIFICATION = "com.digitscore.app.action.REFRESH_NOTIFICATION"
        const val ACTION_CONFIRM_WAKE = "com.digitscore.app.action.CONFIRM_WAKE"
        const val ACTION_SNOOZE_WAKE = "com.digitscore.app.action.SNOOZE_WAKE"
        private const val ROLLING_WINDOW_MILLIS = 24 * 60 * 60 * 1_000L
        private const val DETAIL_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1_000L
        private const val CORE_INDEX_SAMPLE_BUCKET_MILLIS = 5 * 60_000L
        private const val SAMPLE_PRUNE_INTERVAL_MILLIS = 6 * 60 * 60_000L
        private const val INTERACTION_RETENTION_MILLIS = 48 * 60 * 60_000L
        private const val USAGE_EVENT_SETTLE_DELAY_MILLIS = 2_000L

        fun start(context: Context) {
            val intent = Intent(context, TrackerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).edit()
                .putBoolean("cumulative_tracking_paused", true).apply()
            val intent = Intent(context, TrackerForegroundService::class.java)
            context.stopService(intent)
        }

        fun refreshNotification(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackerForegroundService::class.java).apply {
                    action = ACTION_REFRESH_NOTIFICATION
                }
            )
        }

        private fun getTodayDateString(): String {
            return DailyGoalStore.getLogicalDateString()
        }

        private fun getYesterdayDateString(): String {
            return DailyGoalStore.getYesterdayLogicalDateString()
        }
    }


    override fun onCreate() {
        super.onCreate()
        ScoreRepository.setServiceRunning(true)
        ScoreNotificationManager.createNotificationChannel(this)

        // 초기 알림 띄우기
        val initialDetail = ScoreCalculator.calculateScore(emptyList(), 0, 0)
        val initialRollingDetail = com.digitscore.app.data.CumulativeScoreStore.detail(
            com.digitscore.app.data.CumulativeRecord(com.digitscore.app.engine.CumulativeCheckpoint(
                System.currentTimeMillis(), com.digitscore.app.engine.CumulativeScoreState())))
        val notification = ScoreNotificationManager.buildScoreNotification(
            this,
            initialDetail,
            0,
            rollingScoreDetail = initialRollingDetail
        )
        startForeground(ScoreNotificationManager.NOTIFICATION_ID, notification)

        registerScreenReceiver()
        restoreScreenState()
        if (lastScreenOffTimestamp > 0L) cumulativeCoverageStart = minOf(cumulativeCoverageStart, lastScreenOffTimestamp)
        if (trackingPreferences.getBoolean("cumulative_tracking_paused", false)) {
            cumulativeCoverageStart = System.currentTimeMillis()
            trackingPreferences.edit().putBoolean("cumulative_tracking_paused", false).apply()
        }
        restoreTodayHistory()
        startPeriodicTracking()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 밝은/어두운 시스템 모드가 바뀌면 코어 숫자형의 대비색을 즉시 다시 만듭니다.
        recalculateAndNotify()
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        screenEventReceiver = ScreenEventReceiver(
            onScreenOn = {
                onScreenTurnedOn()
            },
            onScreenOff = {
                onScreenTurnedOff()
            },
            onUserPresent = {
                onUserUnlocked()
            }
        )

        ContextCompat.registerReceiver(
            this,
            screenEventReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun onScreenTurnedOn() {
        checkDateRollover()
        if (lastScreenOffTimestamp > 0L) {
            val offStart = maxOf(lastScreenOffTimestamp, UsageStatsHelper.getStartOfTodayMillis())
            val offDurationMillis = (System.currentTimeMillis() - offStart).coerceAtLeast(0L)
            val offMinutes = offDurationMillis / 1000 / 60
            accumulatedIdleMinutes += offMinutes
            lastScreenOffTimestamp = 0L
            persistScreenState()
        }
        startPeriodicTracking()
        recalculateAndNotify(immediate = true)
    }

    private fun onScreenTurnedOff() {
        lastScreenOffTimestamp = System.currentTimeMillis()
        persistScreenState()
        // 화면이 꺼지면 배터리 절약을 위해 주기적 폴링 루프 중지
        trackingJob?.cancel()
        trackingJob = null
        // 화면 OFF 직전까지의 앱 사용 구간을 일일 기록에 확정합니다.
        recalculateAndNotify(immediate = true)
    }

    private fun onUserUnlocked() {
        checkDateRollover()
        // 실제 ACTION_USER_PRESENT 수신 시각을 암호화 DB에 먼저 남깁니다.
        // UsageEvents.KEYGUARD_HIDDEN과 겹쳐도 조회 단계에서 한 번의 해제로 합칩니다.
        serviceScope.launch(Dispatchers.IO) {
            DigitsDatabase.getInstance(applicationContext).deviceInteractionEventDao().insertAll(
                listOf(
                    DeviceInteractionEventEntity(
                        timestampMillis = System.currentTimeMillis(),
                        eventType = DeviceInteractionEventEntity.USER_PRESENT
                    )
                )
            )
            // 언락 후 이벤트 정착을 기다려 1회만 재계산합니다 (불필요한 4중 중복 호출 방지)
            recalculateAndNotify(delayMillis = USAGE_EVENT_SETTLE_DELAY_MILLIS)
        }
    }

    private fun startPeriodicTracking() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                checkDateRollover()
                recalculateAndNotify(immediate = true)
                delay(60_000L) // 1분 주기
            }
        }
    }


    private fun checkDateRollover() {
        val today = getTodayDateString()
        if (today != currentDateString) {
            currentDateString = today
            todayUnlockCount = 0
            accumulatedIdleMinutes = 0L
            cachedTodaySnapshot = null
            lastUsageQueryEndMillis = 0L
            lastScreenOffTimestamp = if (isScreenInteractive()) 0L else System.currentTimeMillis()
            persistScreenState()
        }
    }

    private fun restoreScreenState() {
        val storedDate = trackingPreferences.getString("date", currentDateString)
        if (storedDate == currentDateString) {
            accumulatedIdleMinutes = trackingPreferences.getLong("idle_minutes", 0L).coerceAtLeast(0L)
            lastScreenOffTimestamp = trackingPreferences.getLong("screen_off_timestamp", 0L)
        }
        if (!isScreenInteractive() && lastScreenOffTimestamp == 0L) {
            lastScreenOffTimestamp = System.currentTimeMillis()
        }
        persistScreenState()
    }

    private fun persistScreenState() {
        trackingPreferences.edit()
            .putString("date", currentDateString)
            .putLong("idle_minutes", accumulatedIdleMinutes)
            .putLong("screen_off_timestamp", lastScreenOffTimestamp)
            .apply()
    }

    private fun isScreenInteractive(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isInteractive
    }

    private fun restoreTodayHistory() {
        serviceScope.launch {
            val db = DigitsDatabase.getInstance(applicationContext)
            val history = db.scoreDao().getScoreHistoryForDate(currentDateString)
            if (history != null) {
                accumulatedIdleMinutes = history.idleMinutes
                persistScreenState()
            }

            recalculateAndNotify()
        }
    }

    private var recalcJob: Job? = null

    private fun recalculateAndNotify(immediate: Boolean = false, delayMillis: Long = 0L) {
        if (!immediate) {
            recalcJob?.cancel()
        }
        recalcJob = serviceScope.launch {
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
            recalcMutex.withLock {
                val cycleCpuStartedAt = Process.getElapsedCpuTime()
                if (!UsageStatsHelper.hasUsageStatsPermission(applicationContext)) {
                    cumulativeCoverageStart = System.currentTimeMillis()
                    return@launch
                }


                val db = DigitsDatabase.getInstance(applicationContext)
                val settings = db.settingsDao().getSettings()
                withContext(Dispatchers.Main) { wakeSteps.refresh() }
                if (settings?.isTrackingEnabled == false) {
                    stopSelf()
                    return@withLock
                }
                val presetMode = PresetMode.fromId(settings?.selectedPresetModeId ?: "balanced")
                val baseRule = presetMode.scoreRule

                // 사용자가 설정한 발동점·비율·상한으로 이전 사용량 이월을 계산합니다.
                val yesterdayDate = getYesterdayDateString()
                val yesterdayHistory = db.scoreDao().getScoreHistoryForDate(yesterdayDate)
                val yesterdayScore = yesterdayHistory?.finalScore ?: 100
                val configuredRule = settings?.applyTo(baseRule) ?: baseRule
                val yesterdayPenalty = ScoreCalculator.calculateYesterdayPenalty(
                    yesterdayScore = yesterdayScore,
                    rule = configuredRule
                )
                val effectiveRule = configuredRule.copy(yesterdayPenalty = yesterdayPenalty)

                // DB에 저장된 앱 가중치 맵 조회
                val appWeights = db.appDao().getAllAppWeights().firstOrNull() ?: emptyList()
                val weightMap = appWeights.associateBy { it.packageName }
                val weightSignature = appWeights.hashCode()
                if (lastAppWeightSignature != null && lastAppWeightSignature != weightSignature) {
                    // 등급 변경은 오늘 이미 저장된 모든 세션에도 즉시 반영해야 하므로 한 번만 재구성합니다.
                    cachedTodaySnapshot = null
                    lastUsageQueryEndMillis = 0L
                }
                lastAppWeightSignature = weightSignature

                // 프로세스 시작/날짜 변경 때 오늘 상태를 한 번 복원하고 이후에는 마지막 커서 이후의
                // UsageEvents만 읽습니다. 2초 정착 지연으로 늦게 게시되는 동일 시점 이벤트 누락을 줄입니다.
                val now = System.currentTimeMillis()
                val todayStart = UsageStatsHelper.getStartOfTodayMillis()
                val collectionEnd = (now - USAGE_EVENT_SETTLE_DELAY_MILLIS).coerceAtLeast(todayStart)
                val previousSnapshot = cachedTodaySnapshot
                val usageSnapshot = if (
                    previousSnapshot == null ||
                    lastUsageQueryEndMillis < todayStart ||
                    lastUsageQueryEndMillis > collectionEnd
                ) {
                    UsageStatsHelper.getTodayUsageSnapshot(
                        applicationContext,
                        weightMap,
                        endTime = collectionEnd
                    )
                } else {
                    val delta = UsageStatsHelper.getIncrementalUsageSnapshot(
                        context = applicationContext,
                        startTime = lastUsageQueryEndMillis,
                        endTime = collectionEnd,
                        initialState = previousSnapshot.endingState,
                        appWeightMap = weightMap
                    )
                    UsageStatsHelper.mergeSnapshots(previousSnapshot, delta)
                }
                cachedTodaySnapshot = usageSnapshot
                lastUsageQueryEndMillis = collectionEnd
                val todayAppsUsage = usageSnapshot.appsUsage

                // Android의 원본 UsageEvents 보존 기간과 무관하게 날짜별 앱 집계를 365일 보관합니다.
                // 전면 앱 증거가 전혀 없는 조회는 권한/제조사 이벤트 누락일 수 있으므로 0분으로 덮지 않습니다.
                if (usageSnapshot.hasForegroundEvidence || todayAppsUsage.isNotEmpty()) {
                    val appsByPackage = todayAppsUsage.associateBy { it.packageName }
                    val dailyRecords = todayAppsUsage.map { app ->
                        val sessions = usageSnapshot.sessionSummariesByPackage[app.packageName]
                        DailyAppUsageEntity(
                            dateString = currentDateString,
                            packageName = app.packageName,
                            appName = app.appName,
                            usageMillis = app.usageTimeMillis,
                            sessionCount = sessions?.sessionCount ?: 0,
                            shortSessionCount = app.shortSessionCount,
                            longestSessionMillis = sessions?.longestSessionMillis ?: 0L,
                            lateNightUsageMillis = app.lateNightUsageMillis,
                            categoryLevel = app.categoryType.level,
                            lastUpdatedTimestamp = now
                        )
                    }
                    db.dailyAppUsageDao().replaceDay(
                        dateString = currentDateString,
                        records = dailyRecords,
                        coverage = DailyUsageCoverageEntity(
                            dateString = currentDateString,
                            isComplete = false,
                            lastUpdatedTimestamp = now
                        )
                    )
                    db.dailyAppUsageDao().markPastDaysComplete(currentDateString)
                    db.dailyAppUsageDao().pruneBefore(
                        getAppHistoryCutoffDateString(365)
                    )

                    val sessionRecords = usageSnapshot.foregroundSegments.mapNotNull { segment ->
                        val app = appsByPackage[segment.packageName] ?: return@mapNotNull null
                        val segHour = Calendar.getInstance().apply { timeInMillis = segment.startTimeMillis }.get(Calendar.HOUR_OF_DAY)
                        ForegroundUsageSessionEntity(
                            packageName = segment.packageName,
                            startTimeMillis = segment.startTimeMillis,
                            endTimeMillis = segment.endTimeMillis,
                            dateString = currentDateString,
                            appName = app.appName,
                            categoryLevel = app.categoryType.level,
                            effectivePackageName = segment.effectivePackageName,
                            effectiveCategoryLevel = segment.effectiveCategoryLevel,
                            concurrentAppCount = segment.concurrentAppCount,
                            sessionStartTimeMillis = segment.sessionStartTimeMillis,
                            isLateNight = segHour >= 23 || segHour < 5,
                            lastUpdatedTimestamp = now
                        )
                    }
                    db.foregroundUsageSessionDao().replaceDay(currentDateString, sessionRecords)
                }

                // 최근 24시간 최초 복원은 프로세스당 한 번만 하고, 이후에는 위의 증분 조회에서 나온
                // 화면 활성화/키가드 해제 이벤트만 추가합니다. 두 이벤트가 연달아 오는 경우 한 건으로 합칩니다.
                val interactionDao = db.deviceInteractionEventDao()
                if (!interactionBootstrapDone) {
                    interactionDao.insertAll(
                        UsageStatsHelper.getInteractionEvents(
                            applicationContext,
                            now - ROLLING_WINDOW_MILLIS,
                            collectionEnd
                        )
                    )
                    interactionBootstrapDone = true
                }
                interactionDao.insertAll(usageSnapshot.interactionEvents)
                val rollingInteractionEvents = interactionDao.getBetween(
                    now - ROLLING_WINDOW_MILLIS,
                    now
                )
                val rollingUnlockTimestamps = UsageStatsHelper.resolvedUnlockTimestamps(
                    rollingInteractionEvents
                )
                val rollingUnlockCount = rollingUnlockTimestamps.size
                val todayInteractionEvents = interactionDao.getBetween(todayStart, now)
                val finalUnlockCount = UsageStatsHelper.resolvedUnlockTimestamps(
                    todayInteractionEvents
                ).size
                todayUnlockCount = finalUnlockCount
                ScoreRepository.updateUnlockCount(rollingUnlockCount)

                // 화면 OFF 이벤트로 실제 휴식 시간을 누적한다. 수면/권한 누락 시간을
                // 단순히 "자정 이후 경과 시간 - 앱 사용 시간"으로 간주하지 않는다.
                val activeOffMinutes = if (lastScreenOffTimestamp > 0L) {
                    val offStart = maxOf(lastScreenOffTimestamp, UsageStatsHelper.getStartOfTodayMillis())
                    ((System.currentTimeMillis() - offStart).coerceAtLeast(0L) / 60_000L)
                } else {
                    0L
                }
                val realIdleMinutes = (accumulatedIdleMinutes + activeOffMinutes).coerceAtLeast(0L)

                // 점수 계산
                val legacyScoreDetail = ScoreCalculator.calculateScore(
                    appsUsage = todayAppsUsage,
                    idleMinutes = realIdleMinutes,
                    unlockCount = finalUnlockCount,
                    rule = effectiveRule
                )
                // 날짜별 장기 통계의 등급별 시간도 주 앱 이름이 아니라 PiP·분할 화면에
                // 실제 적용된 등급을 따릅니다. 실시간 표시는 아래 최근 24시간 집계를 사용합니다.
                val dailyScoreDetail = legacyScoreDetail.copy(
                    distractingTimeMinutes = usageSnapshot.foregroundSegments
                        .filter { it.effectiveCategoryLevel >= 3 }
                        .sumOf { it.durationMillis } / 60_000L,
                    productiveTimeMinutes = usageSnapshot.foregroundSegments
                        .filter { it.effectiveCategoryLevel <= 1 }
                        .sumOf { it.durationMillis } / 60_000L
                )

                // 새 방식은 자정 경계 대신 최근 24시간의 저장된 상세 세션과
                // 실제 KEYGUARD_HIDDEN/USER_PRESENT 이벤트를 사용합니다.
                val rollingWindowStart = now - ROLLING_WINDOW_MILLIS
                val recentSessionEntities = db.foregroundUsageSessionDao()
                    .getSince(rollingWindowStart)
                val rollingUsageSummary = summarizeRollingUsage(
                    records = recentSessionEntities,
                    startMillis = rollingWindowStart,
                    endMillis = now,
                    appWeightMap = weightMap
                )
                val rollingAppsUsage = rollingUsageSummary.appsUsage
                ScoreRepository.updateRollingUsageSummary(rollingUsageSummary)
                val displayScoreDetail = legacyScoreDetail.copy(
                    totalScreenTimeMinutes = rollingUsageSummary.totalScreenTimeMillis / 60_000L,
                    distractingTimeMinutes = rollingUsageSummary.managedTimeMillis / 60_000L,
                    productiveTimeMinutes = rollingUsageSummary.growthTimeMillis / 60_000L
                )
                ScoreRepository.updateScoreDetail(displayScoreDetail)
                val recentSessions = recentSessionEntities
                    .map { session ->
                        RollingUsageSession(
                            packageName = session.packageName,
                            startTimeMillis = session.startTimeMillis,
                            endTimeMillis = session.endTimeMillis,
                            categoryLevel = session.effectiveCategoryLevel,
                            effectivePackageName = session.effectivePackageName,
                            sessionStartTimeMillis = session.sessionStartTimeMillis,
                            isLateNight = session.isLateNight
                        )
                    }
                val wasCalibrated = trackingPreferences.getBoolean("rolling_score_calibrated", false)
                val engineStartedAt = trackingPreferences.getLong("rolling_engine_started_at", 0L).let { stored ->
                    if (stored > 0L) stored else now.also {
                        trackingPreferences.edit().putLong("rolling_engine_started_at", it).apply()
                    }
                }
                val recordedUsageMillis = if (wasCalibrated) {
                    60 * 60 * 1_000L
                } else {
                    minOf(
                        db.foregroundUsageSessionDao().getTotalRecordedUsageMillis(),
                        (now - engineStartedAt).coerceAtLeast(0L)
                    )
                }
                if (!wasCalibrated && recordedUsageMillis >= 60 * 60 * 1_000L) {
                    trackingPreferences.edit().putBoolean("rolling_score_calibrated", true).apply()
                }
                val coreIndexPreset = CoreIndexPreset.fromId(settings?.selectedCoreIndexPresetId ?: "balanced")
                val rapidUsageAlertConfig = settings?.effectiveRapidUsageAlertConfig(coreIndexPreset)
                    ?: coreIndexPreset.defaultRapidUsageAlertConfig
                val scoringConfig = settings?.effectiveScoringConfig(coreIndexPreset)
                    ?: coreIndexPreset.defaultScoringConfig

                val (cumulativeRecord, cumulativeDetail) = com.digitscore.app.data.CumulativeScoreStore.update(
                    db, now, cumulativeCoverageStart)
                val rollingScoreDetail = cumulativeDetail.copy(
                    recentUsageMinutes = rollingUsageSummary.totalScreenTimeMillis / 60_000L)
                ScoreRepository.updateRollingScoreDetail(rollingScoreDetail)

                // 아침 기상 알림 무응답 시 자동 판정 (관찰 윈도우 15~30분)
                if (pendingWakeTimestamp > 0L) {
                    val wakeElapsed = now - pendingWakeTimestamp
                    val currentAssigned = cachedTodaySnapshot?.assignedUsageMillis ?: 0L
                    val usageDelta = currentAssigned - pendingWakeBaseUsageMillis
                    if (wakeElapsed >= 15 * 60_000L && usageDelta >= 2 * 60_000L) {
                        // 15분 경과 + 전면 앱 2분 이상 지속 사용: 기상 명백함 -> 기상 자동 확정
                        com.digitscore.app.data.CumulativeScoreStore.confirmActivity(
                            db, pendingWakeTimestamp, awake = true, acknowledge = true
                        )
                        ScoreNotificationManager.cancelMorningWakePromptNotification(applicationContext)
                        pendingWakeTimestamp = 0L
                    } else if (wakeElapsed >= 30 * 60_000L && usageDelta < 30_000L) {
                        // 30분 경과 + 폰 사용 없음: 잠결 뒤척임 또는 오인식 -> 수면 복귀 보호(Sleep Rollback)
                        com.digitscore.app.data.CumulativeScoreStore.confirmActivity(
                            db, now, awake = false, acknowledge = true
                        )
                        ScoreNotificationManager.cancelMorningWakePromptNotification(applicationContext)
                        pendingWakeTimestamp = 0L
                    }
                }

                val previousSample = db.coreIndexSampleDao().getLatestBefore(now)
                val guidance = CoreIndexCoach.create(
                    detail = rollingScoreDetail,
                    previousScore = previousSample?.score,
                    sessions = recentSessions,
                    apps = rollingAppsUsage,
                    rollingUnlockTimestamps = rollingUnlockTimestamps,
                    histories = db.scoreDao().getRecentCoreIndexHistories(14),
                    nowMillis = now,
                    preset = coreIndexPreset,
                    scoringConfig = scoringConfig,
                    cumulativeState = cumulativeRecord.checkpoint.state
                )
                ScoreRepository.updateCoreIndexGuidance(guidance)

                // 화면이 켜진 동안 1분마다 계산하되 DB에는 같은 5분 버킷을 갱신해
                // 하루 변화 그래프의 정밀도와 저장·배터리 비용을 함께 제한합니다.
                val sampleBucket = now - (now % CORE_INDEX_SAMPLE_BUCKET_MILLIS)
                db.coreIndexSampleDao().insertOrUpdate(
                    CoreIndexSampleEntity(
                        bucketStartTimestamp = sampleBucket,
                        timestampMillis = now,
                        dateString = currentDateString,
                        score = rollingScoreDetail.finalScore,
                        exactScore = rollingScoreDetail.exactScore,
                        rollingLoad = rollingScoreDetail.rollingLoad,
                        acuteLoad = rollingScoreDetail.acuteLoad,
                        presetId = coreIndexPreset.id,
                        scoreModelVersion = 5
                    )
                )
                com.digitscore.app.data.StatisticsStore.refresh(db, now)
                if (now - lastSamplePrunedAt >= SAMPLE_PRUNE_INTERVAL_MILLIS) {
                    db.foregroundUsageSessionDao().pruneBefore(now - DETAIL_RETENTION_MILLIS)
                    interactionDao.pruneBefore(now - INTERACTION_RETENTION_MILLIS)
                    db.coreIndexSampleDao().pruneBefore(now - DETAIL_RETENTION_MILLIS)
                    db.scoreDao().pruneBefore(java.time.Instant.ofEpochMilli(now)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDate().minusDays(364).toString())
                    lastSamplePrunedAt = now
                }

                // Glance는 이 프로세스의 Repository가 초기화된 뒤 실행될 수 있으므로 계산 결과를
                // 먼저 영구 snapshot으로 확정합니다. 표시값이 바뀌면 즉시, 그대로여도 5분마다
                // launcher에 재전송하여 OEM이 놓친 갱신을 복구합니다.
                val widgetSnapshot = WidgetSnapshot(
                    score = rollingScoreDetail.finalScore,
                    screenMinutes = displayScoreDetail.totalScreenTimeMinutes,
                    managedMinutes = displayScoreDetail.distractingTimeMinutes,
                    unlockCount = rollingUnlockCount,
                    flow = rollingScoreDetail.flow,
                    continuousUsageMinutes = rollingScoreDetail.continuousUsageMinutes,
                    recommendation = guidance.recommendation,
                    recoveryMinutes = guidance.recoveryMinutes,
                    updatedAtMillis = now
                ).sanitized()
                val snapshotPersisted = WidgetSnapshotStore.write(
                    applicationContext,
                    widgetSnapshot
                )
                val displayedValuesChanged = !widgetSnapshot.hasSameDisplayedValues(
                    lastRenderedWidgetSnapshot
                )
                if (
                    snapshotPersisted &&
                    (displayedValuesChanged || now - lastWidgetUpdatedAt >= 5 * 60_000L)
                ) {
                    try {
                        ScoreWidget().updateAll(applicationContext)
                        lastRenderedWidgetSnapshot = widgetSnapshot
                        lastWidgetUpdatedAt = now
                    } catch (error: Exception) {
                        // 런처 위젯 오류가 핵심 측정 및 알림 갱신을 중단하지 않게 합니다.
                        android.util.Log.w("DigitsCoreWidget", "Widget update request failed", error)
                    }
                }

                // 데일리 맞춤 목표 (Daily Goal) 진행도 계산 및 80% 마일스톤 코칭
                var activeGoal: com.digitscore.app.model.DailyGoal? = null
                try {
                    val (ySummary, currentGoal) = com.digitscore.app.data.DailyGoalStore.generateOrGetGoal(
                        applicationContext, db, com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
                    )
                    ScoreRepository.updateYesterdaySummary(ySummary)

                    val todayApp = currentGoal.targetPackageName?.let { pkg ->
                        todayAppsUsage.firstOrNull { it.packageName == pkg }
                    }
                    val appMins = ((todayApp?.usageTimeMillis ?: 0L) / 60_000L).toInt()

                    val updatedGoal = currentGoal.copy(
                        currentScore = rollingScoreDetail.finalScore,
                        currentAppUsageMinutes = appMins,
                        currentUnlockCount = todayUnlockCount,
                        currentValue = when (currentGoal.type) {
                            com.digitscore.app.model.DailyGoalType.APP_USAGE_LIMIT -> appMins
                            com.digitscore.app.model.DailyGoalType.UNLOCK_LIMIT -> todayUnlockCount
                            com.digitscore.app.model.DailyGoalType.SCORE_DEFENSE -> rollingScoreDetail.finalScore
                        }
                    )


                    // 80% 마일스톤 도달 시 1회성 알림 코칭 (앱 사용량 80% 또는 잠금해제 80%)
                    val isAppAt80 = currentGoal.targetPackageName != null && updatedGoal.appProgressRatio >= 0.8f
                    val isUnlockAt80 = updatedGoal.unlockProgressRatio >= 0.8f
                    val shouldNotify80 = !updatedGoal.notifiedMilestone80 &&
                            (isAppAt80 || isUnlockAt80) &&
                            settings?.isNotificationEnabled != false

                    if (shouldNotify80) {
                        val iconStyle = StatusIconStyle.fromId(settings?.statusIconStyleId)
                        ScoreNotificationManager.showGoalMilestoneNotification(applicationContext, updatedGoal, iconStyle)
                    }


                    val finalGoal = if (shouldNotify80) updatedGoal.copy(notifiedMilestone80 = true) else updatedGoal
                    com.digitscore.app.data.DailyGoalStore.updateProgress(
                        applicationContext,
                        currentScore = finalGoal.currentScore,
                        currentAppMins = finalGoal.currentAppUsageMinutes,
                        currentUnlock = finalGoal.currentUnlockCount,
                        notifiedMilestone80 = if (shouldNotify80) true else null
                    )
                    ScoreRepository.updateDailyGoal(finalGoal)
                    activeGoal = finalGoal
                } catch (e: Exception) {
                    android.util.Log.w("DigitsCoreGoal", "Failed to update daily goal progress", e)
                }

                // 알림 갱신
                if (settings?.isNotificationEnabled != false) {
                    val notification = ScoreNotificationManager.buildScoreNotification(
                        applicationContext,
                        displayScoreDetail,
                        rollingUnlockCount,
                        settings?.hideSensitiveNotificationOnLockScreen ?: true,
                        rollingScoreDetail,
                        StatusIconStyle.fromId(settings?.statusIconStyleId),
                        guidance,
                        activeGoal
                    )
                    // 단순 notify 갱신 대신 foreground 연결을 다시 확인해 OEM 재시작이나
                    // 일시적인 알림 제거 뒤에도 상태 아이콘이 복원되도록 합니다.
                    startForeground(ScoreNotificationManager.NOTIFICATION_ID, notification)
                    maybeShowRapidUsageNotification(
                        enabled = settings?.isRapidUsageAlertEnabled != false,
                        config = rapidUsageAlertConfig,
                        currentScore = rollingScoreDetail.finalScore,
                        continuousUsageMinutes = rollingScoreDetail.continuousUsageMinutes,
                        sessions = recentSessions,
                        guidance = guidance,
                        now = now
                    )
                }

                // DB 일일 히스토리 업데이트
                db.scoreDao().insertOrUpdateScoreHistory(
                    DailyScoreHistoryEntity(
                        dateString = currentDateString,
                        finalScore = rollingScoreDetail.finalScore,
                        totalScreenTimeMinutes = dailyScoreDetail.totalScreenTimeMinutes,
                        distractingTimeMinutes = dailyScoreDetail.distractingTimeMinutes,
                        productiveTimeMinutes = dailyScoreDetail.productiveTimeMinutes,
                        idleMinutes = realIdleMinutes,
                        unlockCount = finalUnlockCount,
                        scoreModelVersion = 5,
                        coreIndexPresetId = coreIndexPreset.id
                    )
                )

                // 조회뿐 아니라 DB·점수·알림·위젯까지 이번 측정 주기의 CPU 비용을 포함합니다.
                updateMeasurementDiagnostics(
                    snapshot = usageSnapshot,
                    todayUnlocks = finalUnlockCount,
                    rollingUnlocks = rollingUnlockCount,
                    cycleCpuMillis = (Process.getElapsedCpuTime() - cycleCpuStartedAt).coerceAtLeast(0L),
                    now = now
                )
            }
        }
    }

    private suspend fun maybeShowRapidUsageNotification(
        enabled: Boolean,
        config: RapidUsageAlertConfig,
        currentScore: Int,
        continuousUsageMinutes: Long,
        sessions: List<RollingUsageSession>,
        guidance: com.digitscore.app.engine.CoreIndexGuidance,
        now: Long
    ) {
        if (!enabled) return
        val sanitizedConfig = config.sanitized()
        val windowStart = now - sanitizedConfig.windowMinutes * 60_000L
        val sampleToleranceMillis = CORE_INDEX_SAMPLE_BUCKET_MILLIS + 2 * 60_000L
        val baseline = DigitsDatabase.getInstance(applicationContext)
            .coreIndexSampleDao()
            .getClosestTo(
                targetMillis = windowStart,
                rangeStartMillis = windowStart - sampleToleranceMillis,
                // 관찰 시간보다 짧은 구간을 점수 하락으로 오인하지 않도록
                // 목표 시각 이후의 표본은 기준점으로 사용하지 않습니다.
                rangeEndMillis = windowStart
            )
        val windowUsageMillis = sessions.sumOf { session ->
            (minOf(session.endTimeMillis, now) - maxOf(session.startTimeMillis, windowStart))
                .coerceAtLeast(0L)
        }
        val alert = RapidUsageAlertDetector.evaluate(
            config = sanitizedConfig,
            observation = RapidUsageObservation(
                currentScore = currentScore,
                baselineScore = baseline?.score,
                windowUsageMinutes = (windowUsageMillis / 60_000L).toInt(),
                continuousUsageMinutes = continuousUsageMinutes.toInt()
            )
        ) ?: return
        val lastAlertAt = trackingPreferences.getLong("last_rapid_usage_alert_at", 0L)
        if (now - lastAlertAt < sanitizedConfig.cooldownMinutes * 60_000L) return
        val iconStyle = StatusIconStyle.fromId(
            DigitsDatabase.getInstance(applicationContext).settingsDao().getSettings()?.statusIconStyleId
        )
        ScoreNotificationManager.showRapidUsageNotification(
            context = applicationContext,
            score = currentScore,
            config = sanitizedConfig,
            alert = alert,
            recoveryMinutes = guidance.recoveryMinutes,
            statusIconStyle = iconStyle
        )
        trackingPreferences.edit().putLong("last_rapid_usage_alert_at", now).apply()

    }

    private fun updateMeasurementDiagnostics(
        snapshot: TodayUsageSnapshot,
        todayUnlocks: Int,
        rollingUnlocks: Int,
        cycleCpuMillis: Long,
        now: Long
    ) {
        val metricsDate = trackingPreferences.getString("metrics_date", null)
        if (metricsDate != currentDateString) {
            trackingPreferences.edit()
                .putString("metrics_date", currentDateString)
                .putInt("metrics_cycles", 0)
                .putLong("metrics_query_ms", 0L)
                .putLong("metrics_cpu_ms", 0L)
                .apply()
        }
        val cycles = trackingPreferences.getInt("metrics_cycles", 0) + 1
        val queryTotal = trackingPreferences.getLong("metrics_query_ms", 0L) + snapshot.queryDurationMillis
        val cpuTotal = trackingPreferences.getLong("metrics_cpu_ms", 0L) + cycleCpuMillis
        trackingPreferences.edit()
            .putInt("metrics_cycles", cycles)
            .putLong("metrics_query_ms", queryTotal)
            .putLong("metrics_cpu_ms", cpuTotal)
            .apply()

        val coverage = snapshot.observableUnlockedMillis.takeIf { it > 0L }?.let {
            ((snapshot.assignedUsageMillis * 100.0) / it).toInt().coerceIn(0, 100)
        }
        ScoreRepository.updateMeasurementDiagnostics(
            MeasurementDiagnostics(
                isIncremental = snapshot.incremental,
                lastQueryWindowMillis = snapshot.queryWindowMillis,
                lastQueryDurationMillis = snapshot.queryDurationMillis,
                lastCycleCpuMillis = cycleCpuMillis,
                queriedEventCount = snapshot.queriedEventCount,
                foregroundCoveragePercent = coverage,
                todayUnlockCount = todayUnlocks,
                rolling24HourUnlockCount = rollingUnlocks,
                cyclesToday = cycles,
                totalQueryDurationTodayMillis = queryTotal,
                totalCpuTodayMillis = cpuTotal,
                updatedAtMillis = now
            )
        )
    }

    private fun getAppHistoryCutoffDateString(retentionDays: Int): String {
        val safeDays = retentionDays.coerceIn(30, 365)
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(safeDays - 1)) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_REFRESH_NOTIFICATION) {
            recalculateAndNotify()
            return START_STICKY
        }
        if (intent?.action == ACTION_STOP_TRACKING) {
            trackingPreferences.edit().putBoolean("cumulative_tracking_paused", true).apply()
            serviceScope.launch(Dispatchers.IO) {
                val dao = DigitsDatabase.getInstance(applicationContext).settingsDao()
                val settings = dao.getSettings()
                if (settings != null) {
                    dao.insertOrUpdateSettings(settings.copy(isTrackingEnabled = false))
                }
                withContext(Dispatchers.Main) {
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_CONFIRM_WAKE) {
            val ts = intent.getLongExtra("timestamp", System.currentTimeMillis())
            serviceScope.launch(Dispatchers.IO) {
                com.digitscore.app.data.CumulativeScoreStore.confirmActivity(
                    DigitsDatabase.getInstance(applicationContext), ts, awake = true, acknowledge = true
                )
                ScoreNotificationManager.cancelMorningWakePromptNotification(applicationContext)
                pendingWakeTimestamp = 0L
                recalculateAndNotify()
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_SNOOZE_WAKE) {
            val ts = intent.getLongExtra("timestamp", System.currentTimeMillis())
            serviceScope.launch(Dispatchers.IO) {
                com.digitscore.app.data.CumulativeScoreStore.confirmActivity(
                    DigitsDatabase.getInstance(applicationContext), ts, awake = false, acknowledge = true
                )
                ScoreNotificationManager.cancelMorningWakePromptNotification(applicationContext)
                pendingWakeTimestamp = 0L
                recalculateAndNotify()
            }
            return START_STICKY
        }
        recalculateAndNotify()
        return START_STICKY
    }

    override fun onDestroy() {
        wakeSteps.stop()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        super.onDestroy()
        ScoreRepository.setServiceRunning(false)
        serviceScope.cancel()
        trackingJob?.cancel()
        screenEventReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
