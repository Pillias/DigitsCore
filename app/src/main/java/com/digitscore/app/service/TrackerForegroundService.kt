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
import com.digitscore.app.data.ForegroundUsageSegment
import com.digitscore.app.data.MeasurementDiagnostics
import com.digitscore.app.data.summarizeRollingUsage
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.entity.DeviceInteractionEventEntity
import com.digitscore.app.data.entity.effectiveRapidUsageAlertConfig
import com.digitscore.app.data.entity.effectiveScoringConfig
import com.digitscore.app.model.defaultScoringConfig
import com.digitscore.app.engine.RapidUsageAlertDetector
import com.digitscore.app.engine.RapidUsageObservation
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.CumulativeScoreConfig
import com.digitscore.app.engine.RollingScoreCalculator
import com.digitscore.app.engine.RollingUsageSession
import com.digitscore.app.engine.CoreIndexCoach
import com.digitscore.app.model.AppUsage
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
    private var sessionBootstrapDone = false

    private var cumulativeCoverageStart = UsageStatsHelper.getStartOfTodayMillis()
    private var lastAppWeightSignature: Int? = null
    private var lastEdgeWarningAtMillis: Long = 0L
    private var lastEdgeWarningPackage: String = ""
    private var lastEdgeWarningIsStudy: Boolean? = null

    private val trackingPreferences by lazy {
        getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_STOP_TRACKING = "com.digitscore.app.action.STOP_TRACKING"
        const val ACTION_REFRESH_NOTIFICATION = "com.digitscore.app.action.REFRESH_NOTIFICATION"
        /** 아침 기상으로 확정하는 데 필요한 연속 사용 시간(잠깐 시간 확인은 제외). */
        private const val MORNING_WAKE_MIN_ACTIVE_MILLIS = 3 * 60_000L
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

        /** 추적 서비스가 실행 중일 때만 즉시 재계산을 요청합니다. 꺼져 있으면 새로 시작하지 않습니다. */
        fun refreshNotification(context: Context) {
            if (!ScoreRepository.isServiceRunning.value) return
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

        // 기기 재부팅 시 커스텀 집중/제외 앱 캐시 초기화
        com.digitscore.app.model.AppCategoryPolicy.getCustomStudyPackages(applicationContext)
        com.digitscore.app.model.AppCategoryPolicy.getExcludedStudyPackages(applicationContext)

        // 기본 홈 앱(런처)은 하드코딩 목록 대신 기기에서 확인해 사용 시간에서 제외합니다.
        UsageStatsHelper.refreshHomePackages(applicationContext)

        // 초기 알림: 첫 계산 전까지 기본 75점 대신 마지막으로 저장된 점수를 보여줍니다.
        val lastSnapshot = WidgetSnapshotStore.read(applicationContext)
        val initialScore = lastSnapshot?.score ?: CumulativeScoreConfig.CURRENT.initialScore.toInt()
        val initialDetail = ScoreDetail.usageSummary(emptyList(), initialScore).copy(
            totalScreenTimeMinutes = lastSnapshot?.screenMinutes ?: 0L,
            distractingTimeMinutes = lastSnapshot?.managedMinutes ?: 0L
        )
        val initialRollingDetail = com.digitscore.app.data.CumulativeScoreStore.detail(
            com.digitscore.app.data.CumulativeRecord(com.digitscore.app.engine.CumulativeCheckpoint(
                System.currentTimeMillis(), com.digitscore.app.engine.CumulativeScoreState(
                    signal = CumulativeScoreConfig.CURRENT.signalForScore(initialScore.toDouble())))))
        val notification = ScoreNotificationManager.buildScoreNotification(
            this,
            initialDetail,
            lastSnapshot?.unlockCount ?: 0,
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
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
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
            },
            onTimeOrTimezoneChanged = {
                handleTimeOrTimezoneChanged()
            }
        )

        ContextCompat.registerReceiver(
            this,
            screenEventReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun handleTimeOrTimezoneChanged() {
        checkDateRollover()
        cachedTodaySnapshot = null
        lastUsageQueryEndMillis = 0L
        com.digitscore.app.data.CumulativeScoreStore.clearWindowCache()
        recalculateAndNotify(immediate = true)
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
        com.digitscore.app.notification.EdgeLightingOverlayManager.dismiss(applicationContext, "screen off")
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
            val db = DigitsDatabase.getInstance(applicationContext)
            db.deviceInteractionEventDao().insertAll(
                listOf(
                    DeviceInteractionEventEntity(
                        timestampMillis = System.currentTimeMillis(),
                        eventType = DeviceInteractionEventEntity.USER_PRESENT
                    )
                )
            )

            // 아침 기상 확정·브리핑 알림은 실제 사용이 3분 이상 이어졌는지 재계산 단계에서 판단합니다.
            // 잠금 해제 후 이벤트 정착을 기다려 1회만 재계산합니다 (불필요한 4중 중복 호출 방지)
            recalculateAndNotify(delayMillis = USAGE_EVENT_SETTLE_DELAY_MILLIS)
        }
    }

    /**
     * 아침(05:00~11:59) 기상 확정과 브리핑 알림.
     * 새벽에 잠깐 시간만 확인한 경우는 제외하고, 간격 3분 이내로 이어진 실제 앱 사용이
     * 3분 이상일 때 그 사용 흐름의 시작 시각을 기상으로 확정합니다.
     */
    private suspend fun checkMorningWake(
        db: DigitsDatabase,
        segments: List<ForegroundUsageSegment>,
        logicalDayStart: Long,
        notificationsEnabled: Boolean
    ) {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour !in 5..11) return
        val prefs = applicationContext.getSharedPreferences("morning_prompt_state", Context.MODE_PRIVATE)
        val todayDate = DailyGoalStore.getLogicalDateString()
        // 시계 역행으로 미래 날짜가 남아 있으면 지워서 그 날짜의 기상 판별이 건너뛰어지지 않게 합니다.
        if ((prefs.getString("last_unlock_prompt_date", "") ?: "") > todayDate) {
            prefs.edit().remove("last_unlock_prompt_date").apply()
        }
        if (prefs.getString("last_unlock_prompt_date", "") == todayDate) return
        val wakeStart = com.digitscore.app.engine.RestPhasePolicy.morningWakeStart(
            segments = segments.map { it.startTimeMillis to it.endTimeMillis },
            windowStartMillis = logicalDayStart,
            minActiveMillis = MORNING_WAKE_MIN_ACTIVE_MILLIS
        ) ?: return
        // 오늘 요약(어제 기록)이 아직 만들어지지 않았다면 이번 주기에는 판단하지 않고 다음 주기에 다시 봅니다.
        // (새 날의 첫 계산에서는 요약 생성보다 이 검사가 먼저 올 수 있습니다)
        val summary = DailyGoalStore.getYesterdaySummary(applicationContext, todayDate) ?: return
        prefs.edit().putString("last_unlock_prompt_date", todayDate).apply()
        com.digitscore.app.data.CumulativeScoreStore.confirmWake(db, wakeStart, acknowledge = true)

        // 설치 첫날처럼 어제 기록이 없으면 빈 브리핑 대신 목표만 자동 지정합니다.
        if (!DailyGoalStore.hasYesterdayBaseline(summary)) {
            DailyGoalStore.markBriefingCompleted(applicationContext, todayDate)
            return
        }
        if (!DailyGoalStore.isBriefingCompleted(applicationContext) && notificationsEnabled) {
            // 백그라운드 startActivity 대신 헤드업 알림을 띄우고, 탭하거나 앱을 열면 브리핑이 열립니다.
            ScoreNotificationManager.showMorningBriefingHeadsUpNotification(applicationContext)
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

                // 커스텀 집중/제외 앱 캐시 로드
                com.digitscore.app.model.AppCategoryPolicy.getCustomStudyPackages(applicationContext)
                com.digitscore.app.model.AppCategoryPolicy.getExcludedStudyPackages(applicationContext)

                val settings = db.settingsDao().getSettings()
                if (settings?.isTrackingEnabled == false) {
                    stopSelf()
                    return@withLock
                }

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

                // 앱 기동 또는 업데이트 시 어제 구간(24시간 전 ~ 오늘 자정)의 세션이 DB에 없으면
                // UsageStatsManager에서 복원하여 어제 저녁 화면 시간 및 오픈 횟수가 0으로 표시되는 현상을 방지합니다.
                if (!sessionBootstrapDone) {
                    val rollingWindowStart = now - ROLLING_WINDOW_MILLIS
                    if (rollingWindowStart < todayStart) {
                        val existingYesterdaySessions = db.foregroundUsageSessionDao().getBetween(rollingWindowStart, todayStart)
                        if (existingYesterdaySessions.isEmpty()) {
                            val yesterdaySnapshot = UsageStatsHelper.getUsageSnapshotForRange(
                                applicationContext,
                                weightMap,
                                rollingWindowStart,
                                todayStart
                            )
                            val yesterdayDateString = getYesterdayDateString()
                            val pm = applicationContext.packageManager
                            val yesterdaySessions = yesterdaySnapshot.foregroundSegments.map { segment ->
                                val segHour = Calendar.getInstance().apply { timeInMillis = segment.startTimeMillis }.get(Calendar.HOUR_OF_DAY)
                                val appName = runCatching {
                                    pm.getApplicationLabel(pm.getApplicationInfo(segment.packageName, 0)).toString()
                                }.getOrDefault(segment.packageName)
                                ForegroundUsageSessionEntity(
                                    packageName = segment.packageName,
                                    startTimeMillis = segment.startTimeMillis,
                                    endTimeMillis = segment.endTimeMillis,
                                    dateString = yesterdayDateString,
                                    appName = appName,
                                    categoryLevel = segment.effectiveCategoryLevel,
                                    effectivePackageName = segment.effectivePackageName,
                                    effectiveCategoryLevel = segment.effectiveCategoryLevel,
                                    concurrentAppCount = segment.concurrentAppCount,
                                    sessionStartTimeMillis = segment.sessionStartTimeMillis,
                                    isLateNight = segHour >= 23 || segHour < 5,
                                    lastUpdatedTimestamp = now
                                )
                            }
                            if (yesterdaySessions.isNotEmpty()) {
                                db.foregroundUsageSessionDao().insertAll(yesterdaySessions)
                            }
                        }
                    }
                    sessionBootstrapDone = true
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
                ScoreRepository.updateUnlockCount(finalUnlockCount)

                // 화면 OFF 이벤트로 실제 휴식 시간을 누적한다. 수면/권한 누락 시간을
                // 단순히 "자정 이후 경과 시간 - 앱 사용 시간"으로 간주하지 않는다.
                val activeOffMinutes = if (lastScreenOffTimestamp > 0L) {
                    val offStart = maxOf(lastScreenOffTimestamp, UsageStatsHelper.getStartOfTodayMillis())
                    ((System.currentTimeMillis() - offStart).coerceAtLeast(0L) / 60_000L)
                } else {
                    0L
                }
                val realIdleMinutes = (accumulatedIdleMinutes + activeOffMinutes).coerceAtLeast(0L)

                // 사용 시간 합계만 담은 상세 정보 (이전 일일 점수 공식은 더 이상 계산하지 않습니다.
                // 점수는 아래 누적 코어 지수로 채웁니다.)
                val usageScoreDetail = ScoreDetail.usageSummary(todayAppsUsage, finalScore = 0)
                // 날짜별 장기 통계의 등급별 시간도 주 앱 이름이 아니라 PiP·분할 화면에
                // 실제 적용된 등급을 따릅니다. 실시간 표시는 아래 최근 24시간 집계를 사용합니다.
                val dailyScoreDetail = usageScoreDetail.copy(
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
                val queryStart = minOf(rollingWindowStart, todayStart)
                val recentSessionEntities = db.foregroundUsageSessionDao()
                    .getSince(queryStart)
                val todaySessionEntities = recentSessionEntities.filter { it.endTimeMillis > todayStart }
                val todayUsageSummary = summarizeRollingUsage(
                    records = todaySessionEntities,
                    startMillis = todayStart,
                    endMillis = now,
                    appWeightMap = weightMap
                )
                val rollingUsageSummary = summarizeRollingUsage(
                    records = recentSessionEntities.filter { it.endTimeMillis > rollingWindowStart },
                    startMillis = rollingWindowStart,
                    endMillis = now,
                    appWeightMap = weightMap
                )
                val rollingAppsUsage = rollingUsageSummary.appsUsage
                ScoreRepository.updateRollingUsageSummary(todayUsageSummary)
                val displayScoreDetail = usageScoreDetail.copy(
                    totalScreenTimeMinutes = todayUsageSummary.totalScreenTimeMillis / 60_000L,
                    distractingTimeMinutes = todayUsageSummary.managedTimeMillis / 60_000L,
                    productiveTimeMinutes = todayUsageSummary.growthTimeMillis / 60_000L
                )
                ScoreRepository.updateScoreDetail(displayScoreDetail)
                val recentSessions = recentSessionEntities
                    .filter { it.endTimeMillis > rollingWindowStart }
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

                // 3분 이내 간격으로 이어진 현재 연속 사용 사슬(chain) 계산
                val sortedSegments = usageSnapshot.foregroundSegments.sortedBy { it.startTimeMillis }
                val lastSeg = sortedSegments.lastOrNull()
                val isCurrentlyInForeground = isScreenInteractive() &&
                        !usageSnapshot.endingState.activePackage.isNullOrBlank() &&
                        lastSeg != null &&
                        lastSeg.endTimeMillis >= (collectionEnd - 20_000L)

                var realTimeContinuousMinutes = 0L
                var chainedManagedMinutes = 0.0
                var chainedStudyMinutes = 0.0
                var currentAppChainMinutes = 0.0

                if (isCurrentlyInForeground && lastSeg != null) {
                    val chain = mutableListOf<ForegroundUsageSegment>()
                    chain.add(lastSeg)
                    var current: ForegroundUsageSegment = lastSeg
                    for (i in (sortedSegments.size - 2) downTo 0) {
                        val prev = sortedSegments[i]
                        val gap = current.startTimeMillis - prev.endTimeMillis
                        if (gap <= 3 * 60_000L) {
                            chain.add(prev)
                            current = prev
                        } else {
                            break
                        }
                    }
                    val totalActiveMillis = chain.sumOf { it.durationMillis }
                    realTimeContinuousMinutes = (totalActiveMillis / 60_000L).coerceAtLeast(1L)

                    // 점수 계산과 같은 기준(사용자 지정 등급 반영)으로 신호등 대상 시간을 셉니다.
                    fun edgeKindOf(segment: ForegroundUsageSegment) =
                        com.digitscore.app.model.AppCategoryPolicy.edgeKindFor(
                            segment.effectivePackageName.ifBlank { segment.packageName },
                            segment.effectiveCategoryLevel,
                            applicationContext
                        )
                    val managedMillis = chain.filter {
                        edgeKindOf(it) == com.digitscore.app.model.AppCategoryPolicy.EdgeKind.MANAGED
                    }.sumOf { it.durationMillis }
                    chainedManagedMinutes = managedMillis / 60_000.0

                    val studyMillis = chain.filter {
                        edgeKindOf(it) == com.digitscore.app.model.AppCategoryPolicy.EdgeKind.STUDY
                    }.sumOf { it.durationMillis }
                    chainedStudyMinutes = studyMillis / 60_000.0

                    val activePkg = usageSnapshot.endingState.activePackage ?: lastSeg.effectivePackageName.ifBlank { lastSeg.packageName }
                    val currentAppMillis = chain.filter {
                        val pkg = it.effectivePackageName.ifBlank { it.packageName }
                        pkg == activePkg
                    }.sumOf { it.durationMillis }
                    currentAppChainMinutes = currentAppMillis / 60_000.0
                }

                // 시계 역행으로 남은 미래 날짜 목표·보상·자유권 기록을 정리합니다.
                DailyGoalStore.discardFutureState(applicationContext, now)

                // 기상 확정을 점수 정산보다 먼저 기록하여 첫 정산 주기에 즉시 반영되게 합니다.
                checkMorningWake(
                    db = db,
                    segments = usageSnapshot.foregroundSegments,
                    logicalDayStart = todayStart,
                    notificationsEnabled = settings?.isNotificationEnabled != false
                )

                // 15분 자유권 구간은 감점·가속 없이 처리합니다 (만료 후에도 정산이 끝날 때까지 전달).
                val freePassWindows = DailyGoalStore.freePassWindows(applicationContext, now)
                val (cumulativeRecord, cumulativeDetail) = com.digitscore.app.data.CumulativeScoreStore.update(
                    db, now, cumulativeCoverageStart,
                    penaltyFreeWindows = freePassWindows)

                val displayContinuousMinutes = if (isCurrentlyInForeground) {
                    maxOf(cumulativeDetail.continuousUsageMinutes, realTimeContinuousMinutes)
                } else {
                    com.digitscore.app.notification.EdgeLightingOverlayManager.dismiss(applicationContext, "not in foreground")
                    0L
                }

                val rollingScoreDetail = cumulativeDetail.copy(
                    recentUsageMinutes = todayUsageSummary.totalScreenTimeMillis / 60_000L,
                    continuousUsageMinutes = displayContinuousMinutes,
                    flow = if (displayContinuousMinutes > 0L) com.digitscore.app.engine.ScoreFlow.USING else cumulativeDetail.flow
                )
                ScoreRepository.updateRollingScoreDetail(rollingScoreDetail)

                val previousSample = db.coreIndexSampleDao().getLatestBefore(now)
                val guidance = CoreIndexCoach.create(
                    detail = rollingScoreDetail,
                    previousScore = previousSample?.score,
                    sessions = recentSessions,
                    apps = todayUsageSummary.appsUsage,
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
                        scoreModelVersion = com.digitscore.app.engine.ScoreModel.CURRENT_STORED
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

                // 데일리 맞춤 목표 (Daily Goal) 진행도 계산 및 80% 마일스톤 코칭
                var activeGoal: com.digitscore.app.model.DailyGoal? = null
                try {
                    val (ySummary, currentGoal) = com.digitscore.app.data.DailyGoalStore.generateOrGetGoal(
                        applicationContext, db, com.digitscore.app.data.DailyGoalStore.getLogicalDateString()
                    )
                    ScoreRepository.updateYesterdaySummary(ySummary)
                    ScoreRepository.updateYesterdayGoalResult(com.digitscore.app.data.DailyGoalStore.getYesterdayGoalResult(applicationContext))
                    ScoreRepository.updateRewardState(com.digitscore.app.data.DailyGoalStore.getRewardState(applicationContext))

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


                    // 80% 마일스톤 도달 시 목표별 1회성 알림 코칭 (앱 사용량 / 잠금 해제 각각)
                    val notificationsOn = settings?.isNotificationEnabled != false
                    val isAppAt80 = currentGoal.targetPackageName != null && updatedGoal.appProgressRatio >= 0.8f
                    val isUnlockAt80 = updatedGoal.unlockProgressRatio >= 0.8f
                    val notifyApp80 = notificationsOn && isAppAt80 && !updatedGoal.notifiedMilestone80
                    val notifyUnlock80 = notificationsOn && isUnlockAt80 && !updatedGoal.notifiedUnlockMilestone80
                    if (notifyApp80 || notifyUnlock80) {
                        val iconStyle = StatusIconStyle.fromId(settings?.statusIconStyleId)
                        if (notifyApp80) {
                            ScoreNotificationManager.showGoalMilestoneNotification(
                                applicationContext, updatedGoal, iconStyle, forUnlock = false)
                        }
                        if (notifyUnlock80) {
                            ScoreNotificationManager.showGoalMilestoneNotification(
                                applicationContext, updatedGoal, iconStyle, forUnlock = true)
                        }
                    }

                    val finalGoal = updatedGoal.copy(
                        notifiedMilestone80 = updatedGoal.notifiedMilestone80 || notifyApp80,
                        notifiedUnlockMilestone80 = updatedGoal.notifiedUnlockMilestone80 || notifyUnlock80
                    )
                    com.digitscore.app.data.DailyGoalStore.updateProgress(
                        applicationContext,
                        currentScore = finalGoal.currentScore,
                        currentAppMins = finalGoal.currentAppUsageMinutes,
                        currentUnlock = finalGoal.currentUnlockCount,
                        notifiedMilestone80 = if (notifyApp80) true else null,
                        notifiedUnlockMilestone80 = if (notifyUnlock80) true else null
                    )
                    ScoreRepository.updateDailyGoal(finalGoal)
                    activeGoal = finalGoal
                } catch (e: Exception) {
                    android.util.Log.w("DigitsCoreGoal", "Failed to update daily goal progress", e)
                }

                // Glance는 이 프로세스의 Repository가 초기화된 뒤 실행될 수 있으므로 계산 결과를
                // 먼저 영구 snapshot으로 확정합니다. 표시값이 바뀌면 즉시, 그대로여도 5분마다
                // launcher에 재전송하여 OEM이 놓친 갱신을 복구합니다.
                val recentScoresCsv = try {
                    val pastSamples = db.coreIndexSampleDao().getSince(now - 24 * 3600_000L)
                    val pastScores = pastSamples.map { it.exactScore.toFloat() }
                    val sampled = if (pastScores.size <= 47) {
                        pastScores
                    } else {
                        val step = (pastScores.size - 1).toDouble() / 46.0
                        (0..46).map { i ->
                            val idx = kotlin.math.round(i * step).toInt().coerceIn(0, pastScores.size - 1)
                            pastScores[idx]
                        }
                    }
                    (sampled + rollingScoreDetail.exactScore.toFloat()).joinToString(",") {
                        String.format(java.util.Locale.US, "%.1f", it)
                    }
                } catch (e: Exception) {
                    String.format(java.util.Locale.US, "%.1f", rollingScoreDetail.exactScore.toFloat())
                }

                val widgetSnapshot = WidgetSnapshot(
                    score = rollingScoreDetail.finalScore,
                    screenMinutes = displayScoreDetail.totalScreenTimeMinutes,
                    managedMinutes = displayScoreDetail.distractingTimeMinutes,
                    unlockCount = finalUnlockCount,
                    flow = rollingScoreDetail.flow,
                    continuousUsageMinutes = rollingScoreDetail.continuousUsageMinutes,
                    recommendation = guidance.recommendation,
                    recoveryMinutes = guidance.recoveryMinutes,
                    scoreTarget = activeGoal?.scoreTarget ?: 75,
                    targetAppName = activeGoal?.targetAppName ?: "",
                    targetAppUsageMinutes = activeGoal?.currentAppUsageMinutes ?: 0,
                    targetAppLimitMinutes = activeGoal?.appLimitMinutes ?: 30,
                    unlockLimitTarget = activeGoal?.unlockLimitTarget ?: 60,
                    recentScoresCsv = recentScoresCsv,
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


                // 알림 갱신
                if (settings?.isNotificationEnabled != false) {
                    val notification = ScoreNotificationManager.buildScoreNotification(
                        applicationContext,
                        displayScoreDetail,
                        finalUnlockCount,
                        settings?.hideSensitiveNotificationOnLockScreen ?: true,
                        rollingScoreDetail,
                        StatusIconStyle.fromId(settings?.statusIconStyleId),
                        guidance,
                        activeGoal
                    )
                    // 단순 notify 갱신 대신 foreground 연결을 다시 확인해 OEM 재시작이나
                    // 일시적인 알림 제거 뒤에도 상태 아이콘이 복원되도록 합니다.
                    startForeground(ScoreNotificationManager.NOTIFICATION_ID, notification)
                    val isFreePass = com.digitscore.app.data.DailyGoalStore.isFreePassActive(applicationContext)
                    maybeShowRapidUsageNotification(
                        enabled = settings?.isRapidUsageAlertEnabled != false && !isFreePass,
                        config = rapidUsageAlertConfig,
                        currentScore = rollingScoreDetail.finalScore,
                        continuousUsageMinutes = rollingScoreDetail.continuousUsageMinutes,
                        sessions = recentSessions,
                        guidance = guidance,
                        now = now
                    )
                }

                // 화면 신호등 알림 (공부 초록불 / 관리앱 빨간불). 15분 자유권 사용 중에는 띄우지 않습니다.
                val freePassActive = DailyGoalStore.isFreePassActive(applicationContext)
                if (com.digitscore.app.BuildConfig.DEBUG) {
                    android.util.Log.d(
                        "DigitsCoreEdge",
                        "fg=$isCurrentlyInForeground active=${usageSnapshot.endingState.activePackage} " +
                            "lastSeg=${lastSeg?.effectivePackageName}/${lastSeg?.effectiveCategoryLevel} " +
                            "lastEndLag=${lastSeg?.let { collectionEnd - it.endTimeMillis }} " +
                            "managed=${"%.1f".format(chainedManagedMinutes)} study=${"%.1f".format(chainedStudyMinutes)} " +
                            "freePass=$freePassActive enabled=${com.digitscore.app.notification.EdgeLightingOverlayManager.isEnabled(applicationContext)} " +
                            "overlay=${android.provider.Settings.canDrawOverlays(applicationContext)}"
                    )
                }
                if (freePassActive) {
                    com.digitscore.app.notification.EdgeLightingOverlayManager.dismiss(applicationContext, "free pass")
                }
                if (isCurrentlyInForeground && !freePassActive &&
                    com.digitscore.app.notification.EdgeLightingOverlayManager.isEnabled(applicationContext) &&
                    android.provider.Settings.canDrawOverlays(applicationContext)
                ) {
                    val activePkg = usageSnapshot.endingState.activePackage?.ifBlank { null }
                        ?: lastSeg?.effectivePackageName?.ifBlank { lastSeg.packageName }
                        ?: ""
                    if (activePkg.isNotBlank()) {
                        // 점수 계산과 같은 기준: 사용자 지정 등급이 반영된 현재 구간의 실제 등급을 씁니다.
                        val edgeKind = lastSeg?.let {
                            com.digitscore.app.model.AppCategoryPolicy.edgeKindFor(
                                it.effectivePackageName.ifBlank { it.packageName },
                                it.effectiveCategoryLevel,
                                applicationContext
                            )
                        } ?: com.digitscore.app.model.AppCategoryPolicy.EdgeKind.NONE
                        val isStudy = edgeKind == com.digitscore.app.model.AppCategoryPolicy.EdgeKind.STUDY
                        val isManaged = edgeKind == com.digitscore.app.model.AppCategoryPolicy.EdgeKind.MANAGED

                        val rawAppName = todayAppsUsage.firstOrNull { it.packageName == activePkg }?.appName
                            ?: try {
                                val pm = packageManager
                                val appInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    pm.getApplicationInfo(activePkg, android.content.pm.PackageManager.ApplicationInfoFlags.of(0))
                                } else {
                                    @Suppress("DEPRECATION")
                                    pm.getApplicationInfo(activePkg, 0)
                                }
                                pm.getApplicationLabel(appInfo).toString()
                            } catch (_: Exception) {
                                activePkg.substringAfterLast('.')
                            }
                        val activeAppName = if (rawAppName.length > 10) rawAppName.take(9) + "…" else rawAppName

                        val managedTargetMins = com.digitscore.app.notification.EdgeLightingOverlayManager.getManagedTriggerMinutes(applicationContext)
                        val studyTargetMins = com.digitscore.app.notification.EdgeLightingOverlayManager.getStudyTriggerMinutes(applicationContext)

                        val effectiveManagedMinutes = if (isManaged) chainedManagedMinutes else 0.0
                        val effectiveStudyMinutes = if (isStudy) chainedStudyMinutes else 0.0

                        val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(applicationContext) == com.digitscore.app.i18n.AppLocale.ENGLISH

                        if (isStudy && effectiveStudyMinutes >= studyTargetMins) {
                            val repeatIntervalMins = (studyTargetMins / 2).coerceAtLeast(1)
                            val cooldownMillis = repeatIntervalMins * 60_000L
                            // 재경고 간격은 앱이 아니라 경고 종류(공부/관리) 단위로 적용합니다.
                            val isSameWarningTarget = lastEdgeWarningIsStudy == true
                            val canTrigger = !isSameWarningTarget || (now - lastEdgeWarningAtMillis) >= cooldownMillis
                            if (canTrigger) {
                                val displayMins = maxOf(studyTargetMins, effectiveStudyMinutes.toInt())
                                val label = if (isEn) "● $activeAppName · ${displayMins}min focus · Rest eyes" else "● $activeAppName · ${displayMins}min 집중 · 휴식 권장"
                                com.digitscore.app.notification.EdgeLightingOverlayManager.showEdgeWarning(
                                    applicationContext,
                                    isStudyGreen = true,
                                    labelText = label
                                )
                                lastEdgeWarningAtMillis = now
                                lastEdgeWarningPackage = activePkg
                                lastEdgeWarningIsStudy = true
                            }
                        } else if (isManaged && effectiveManagedMinutes >= managedTargetMins) {
                            val repeatIntervalMins = (managedTargetMins / 2).coerceAtLeast(1)
                            val cooldownMillis = repeatIntervalMins * 60_000L
                            val isSameWarningTarget = lastEdgeWarningIsStudy == false
                            val canTrigger = !isSameWarningTarget || (now - lastEdgeWarningAtMillis) >= cooldownMillis
                            if (canTrigger) {
                                val displayMins = maxOf(managedTargetMins, effectiveManagedMinutes.toInt())
                                val isDominantApp = currentAppChainMinutes >= managedTargetMins * 0.5
                                val appTitle = if (isDominantApp) activeAppName else if (isEn) "Managed apps" else "관리앱 합산"
                                val label = if (isEn) "● $appTitle · ${displayMins}min continuous · Overuse" else "● $appTitle · ${displayMins}min 연속 · 과몰입 주의"
                                com.digitscore.app.notification.EdgeLightingOverlayManager.showEdgeWarning(
                                    applicationContext,
                                    isStudyGreen = false,
                                    labelText = label
                                )
                                lastEdgeWarningAtMillis = now
                                lastEdgeWarningPackage = activePkg
                                lastEdgeWarningIsStudy = false
                            }
                        }
                    }
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
                        scoreModelVersion = com.digitscore.app.engine.ScoreModel.CURRENT_STORED,
                        coreIndexPresetId = coreIndexPreset.id
                    )
                )

                // 화면이 꺼진 상태에서만 하루 최대 1회 점수 구간(녹/황/주/적)에 맞춰 런처 아이콘을 전환합니다.
                if (!isScreenInteractive()) {
                    com.digitscore.app.notification.LauncherIconManager.maybeUpdateOnScreenOff(
                        context = applicationContext,
                        score = rollingScoreDetail.finalScore,
                        logicalDateString = currentDateString
                    )
                }

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
        val calendar = Calendar.getInstance().apply {
            if (get(Calendar.HOUR_OF_DAY) < 5) {
                add(Calendar.DAY_OF_YEAR, -1)
            }
            add(Calendar.DAY_OF_YEAR, -(safeDays - 1))
        }
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
        recalculateAndNotify()
        return START_STICKY
    }

    override fun onDestroy() {
        com.digitscore.app.notification.EdgeLightingOverlayManager.dismiss(applicationContext, "service destroyed")
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
