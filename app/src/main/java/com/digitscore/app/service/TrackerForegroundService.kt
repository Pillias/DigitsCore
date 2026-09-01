package com.digitscore.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import com.digitscore.app.data.entity.applyTo
import com.digitscore.app.engine.ScoreCalculator
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.PresetMode
import com.digitscore.app.notification.ScoreNotificationManager
import com.digitscore.app.receiver.ScreenEventReceiver
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

    private val trackingPreferences by lazy {
        getSharedPreferences("tracking_state", Context.MODE_PRIVATE)
    }

    companion object {
        const val ACTION_STOP_TRACKING = "com.digitscore.app.action.STOP_TRACKING"

        fun start(context: Context) {
            val intent = Intent(context, TrackerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TrackerForegroundService::class.java)
            context.stopService(intent)
        }

        private fun getTodayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(Date())
        }

        private fun getYesterdayDateString(): String {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            return sdf.format(cal.time)
        }
    }

    override fun onCreate() {
        super.onCreate()
        ScoreRepository.setServiceRunning(true)
        ScoreNotificationManager.createNotificationChannel(this)

        // 초기 알림 띄우기
        val initialDetail = ScoreCalculator.calculateScore(emptyList(), 0, 0)
        val notification = ScoreNotificationManager.buildScoreNotification(this, initialDetail, 0)
        startForeground(ScoreNotificationManager.NOTIFICATION_ID, notification)

        registerScreenReceiver()
        restoreScreenState()
        restoreTodayHistory()
        startPeriodicTracking()
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
        recalculateAndNotify()
    }

    private fun onScreenTurnedOff() {
        lastScreenOffTimestamp = System.currentTimeMillis()
        persistScreenState()
        // 화면이 꺼지면 배터리 절약을 위해 주기적 폴링 루프 중지
        trackingJob?.cancel()
        trackingJob = null
        // 화면 OFF 직전까지의 앱 사용 구간을 일일 기록에 확정합니다.
        recalculateAndNotify()
    }

    private fun onUserUnlocked() {
        checkDateRollover()
        todayUnlockCount++
        ScoreRepository.updateUnlockCount(todayUnlockCount)
        recalculateAndNotify()
    }

    private fun startPeriodicTracking() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            while (isActive) {
                checkDateRollover()
                recalculateAndNotify()
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
            lastScreenOffTimestamp = if (isScreenInteractive()) 0L else System.currentTimeMillis()
            persistScreenState()
            ScoreRepository.updateUnlockCount(0)
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
                todayUnlockCount = history.unlockCount
                accumulatedIdleMinutes = history.idleMinutes
                persistScreenState()
                ScoreRepository.updateUnlockCount(todayUnlockCount)
            }

            recalculateAndNotify()
        }
    }

    private fun recalculateAndNotify() {
        serviceScope.launch {
            recalcMutex.withLock {
                if (!UsageStatsHelper.hasUsageStatsPermission(applicationContext)) {
                    return@launch
                }

                val db = DigitsDatabase.getInstance(applicationContext)
                val settings = db.settingsDao().getSettings()
                if (settings?.isTrackingEnabled == false) {
                    stopSelf()
                    return@withLock
                }
                val presetMode = PresetMode.fromId(settings?.selectedPresetModeId ?: "balanced")
                val baseRule = presetMode.scoreRule

                // 사용자가 설정한 발동점·비율·상한으로 디톡스 부채를 계산합니다.
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

                // 화면이 켜지고 잠금 해제된 동안 최상단 앱 하나만 이벤트 타임라인으로 집계합니다.
                // 앱 사용시간과 잠금 해제를 한 번의 UsageEvents 조회로 함께 계산합니다.
                val usageSnapshot = UsageStatsHelper.getTodayUsageSnapshot(applicationContext, weightMap)
                val appsUsage = usageSnapshot.appsUsage
                ScoreRepository.updateAppsUsage(appsUsage)

                // Android의 원본 UsageEvents 보존 기간과 무관하게 날짜별 앱 집계를 365일 보관합니다.
                // 전면 앱 증거가 전혀 없는 조회는 권한/제조사 이벤트 누락일 수 있으므로 0분으로 덮지 않습니다.
                if (usageSnapshot.hasForegroundEvidence || appsUsage.isNotEmpty()) {
                    val now = System.currentTimeMillis()
                    val dailyRecords = appsUsage.map { app ->
                        val sessions = usageSnapshot.sessionSummariesByPackage[app.packageName]
                        DailyAppUsageEntity(
                            dateString = currentDateString,
                            packageName = app.packageName,
                            appName = app.appName,
                            usageMillis = app.usageTimeMillis,
                            sessionCount = sessions?.sessionCount ?: 0,
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
                        getAppHistoryCutoffDateString(settings?.appHistoryRetentionDays ?: 365)
                    )
                }

                // 시스템 이벤트에서 얻은 언락 횟수와 동기화
                val systemUnlocks = usageSnapshot.unlockCount
                val finalUnlockCount = kotlin.math.max(todayUnlockCount, systemUnlocks)
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

                // 점수 계산
                val scoreDetail = ScoreCalculator.calculateScore(
                    appsUsage = appsUsage,
                    idleMinutes = realIdleMinutes,
                    unlockCount = finalUnlockCount,
                    rule = effectiveRule
                )
                ScoreRepository.updateScoreDetail(scoreDetail)

                // 알림 갱신
                if (settings?.isNotificationEnabled != false) {
                    ScoreNotificationManager.updateScoreNotification(
                        applicationContext,
                        scoreDetail,
                        finalUnlockCount,
                        settings.hideSensitiveNotificationOnLockScreen
                    )
                }

                // DB 일일 히스토리 업데이트
                db.scoreDao().insertOrUpdateScoreHistory(
                    DailyScoreHistoryEntity(
                        dateString = currentDateString,
                        finalScore = scoreDetail.finalScore,
                        totalScreenTimeMinutes = scoreDetail.totalScreenTimeMinutes,
                        distractingTimeMinutes = scoreDetail.distractingTimeMinutes,
                        productiveTimeMinutes = scoreDetail.productiveTimeMinutes,
                        idleMinutes = realIdleMinutes,
                        unlockCount = finalUnlockCount
                    )
                )
            }
        }
    }

    private fun getAppHistoryCutoffDateString(retentionDays: Int): String {
        val safeDays = retentionDays.coerceIn(30, 365)
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(safeDays - 1)) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_TRACKING) {
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
