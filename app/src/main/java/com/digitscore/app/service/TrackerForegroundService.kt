package com.digitscore.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.engine.ScoreCalculator
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.PresetMode
import com.digitscore.app.notification.ScoreNotificationManager
import com.digitscore.app.receiver.ScreenEventReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrackerForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var trackingJob: Job? = null
    private var screenEventReceiver: ScreenEventReceiver? = null

    private var lastScreenOffTimestamp: Long = 0L
    private var accumulatedIdleMinutes: Long = 0L
    private var todayUnlockCount: Int = 0
    private var currentDateString: String = getTodayDateString()

    companion object {
        private val _currentScoreDetail = MutableStateFlow<ScoreDetail?>(null)
        val currentScoreDetail = _currentScoreDetail.asStateFlow()

        private val _currentAppsUsage = MutableStateFlow<List<AppUsage>>(emptyList())
        val currentAppsUsage = _currentAppsUsage.asStateFlow()

        private val _currentUnlockCount = MutableStateFlow(0)
        val currentUnlockCount = _currentUnlockCount.asStateFlow()

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning = _isServiceRunning.asStateFlow()

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
        _isServiceRunning.value = true
        ScoreNotificationManager.createNotificationChannel(this)

        // 초기 알림 띄우기
        val initialDetail = ScoreCalculator.calculateScore(emptyList(), 0, 0)
        val notification = ScoreNotificationManager.buildScoreNotification(this, initialDetail, 0)
        startForeground(ScoreNotificationManager.NOTIFICATION_ID, notification)

        registerScreenReceiver()
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

        registerReceiver(screenEventReceiver, filter)
    }

    private fun onScreenTurnedOn() {
        if (lastScreenOffTimestamp > 0L) {
            val offDurationMillis = System.currentTimeMillis() - lastScreenOffTimestamp
            val offMinutes = offDurationMillis / 1000 / 60
            accumulatedIdleMinutes += offMinutes
            lastScreenOffTimestamp = 0L
        }
        startPeriodicTracking()
        recalculateAndNotify()
    }

    private fun onScreenTurnedOff() {
        lastScreenOffTimestamp = System.currentTimeMillis()
        // 화면이 꺼지면 배터리 절약을 위해 주기적 폴링 루프 중지
        trackingJob?.cancel()
        trackingJob = null
    }

    private fun onUserUnlocked() {
        checkDateRollover()
        todayUnlockCount++
        _currentUnlockCount.value = todayUnlockCount
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
            _currentUnlockCount.value = 0
        }
    }

    private fun restoreTodayHistory() {
        serviceScope.launch {
            val db = DigitsDatabase.getInstance(applicationContext)
            val history = db.scoreDao().getScoreHistoryForDate(currentDateString)
            if (history != null) {
                todayUnlockCount = history.unlockCount
                accumulatedIdleMinutes = history.idleMinutes
                _currentUnlockCount.value = todayUnlockCount
            }

            // 과거 히스토리가 부족한 경우 최초 1회 자동 30일치 소급 분석 실행
            val allHistories = db.scoreDao().getAllScoreHistories().firstOrNull() ?: emptyList()
            if (allHistories.size < 7 && UsageStatsHelper.hasUsageStatsPermission(applicationContext)) {
                val appWeights = db.appDao().getAllAppWeights().firstOrNull() ?: emptyList()
                val weightMap = appWeights.associateBy { it.packageName }
                val settings = db.settingsDao().getSettings()
                val presetMode = PresetMode.fromId(settings?.selectedPresetModeId ?: "balanced")
                val pastHistories = UsageStatsHelper.syncPastDaysUsageStats(
                    context = applicationContext,
                    appWeightMap = weightMap,
                    scoreRule = presetMode.scoreRule,
                    days = 30
                )
                for (h in pastHistories) {
                    db.scoreDao().insertOrUpdateScoreHistory(h)
                }
            }

            recalculateAndNotify()
        }
    }

    private fun recalculateAndNotify() {
        serviceScope.launch {
            if (!UsageStatsHelper.hasUsageStatsPermission(applicationContext)) {
                return@launch
            }

            val db = DigitsDatabase.getInstance(applicationContext)
            val settings = db.settingsDao().getSettings()
            val presetMode = PresetMode.fromId(settings?.selectedPresetModeId ?: "balanced")
            val baseRule = presetMode.scoreRule

            // 전날 과사용 페널티 (디톡스 부채) 산출: 전날 점수가 80점 미만일 때 (80 - 점수) * 0.5 감점 (최대 30점)
            val yesterdayDate = getYesterdayDateString()
            val yesterdayHistory = db.scoreDao().getScoreHistoryForDate(yesterdayDate)
            val yesterdayScore = yesterdayHistory?.finalScore ?: 100
            val yesterdayPenalty = if (settings?.isYesterdayPenaltyEnabled != false && yesterdayScore < 80) {
                kotlin.math.min(30.0f, (80 - yesterdayScore) * 0.5f)
            } else {
                0f
            }

            val effectiveRule = if (settings != null) {
                baseRule.copy(
                    distractingWeightPerMinute = settings.distractingWeightPerMinute,
                    productiveBonusPerMinute = settings.productiveBonusPerMinute,
                    idleBonusPer10Minutes = settings.idleBonusPer10Minutes,
                    maxIdleBonus = settings.maxIdleBonus,
                    maxProductiveBonus = settings.maxProductiveBonus,
                    unlockPenaltyThreshold = settings.targetUnlockCount,
                    unlockPenaltyPerCount = settings.unlockPenaltyPerCount,
                    lateNightMultiplier = settings.lateNightMultiplier,
                    isLogAccelerationEnabled = settings.isLogAccelerationEnabled,
                    isYesterdayPenaltyEnabled = settings.isYesterdayPenaltyEnabled,
                    yesterdayPenalty = yesterdayPenalty
                )
            } else {
                baseRule.copy(yesterdayPenalty = yesterdayPenalty)
            }

            // DB에 저장된 앱 가중치 맵 조회
            val appWeights = db.appDao().getAllAppWeights().firstOrNull() ?: emptyList()
            val weightMap = appWeights.associateBy { it.packageName }

            // 오늘 사용량 통계 조회
            val appsUsage = UsageStatsHelper.getTodayAppUsageStats(applicationContext, weightMap)
            _currentAppsUsage.value = appsUsage

            // 점수 계산
            val scoreDetail = ScoreCalculator.calculateScore(
                appsUsage = appsUsage,
                idleMinutes = accumulatedIdleMinutes,
                unlockCount = todayUnlockCount,
                rule = effectiveRule
            )
            _currentScoreDetail.value = scoreDetail

            // 알림 갱신
            if (settings?.isNotificationEnabled != false) {
                ScoreNotificationManager.updateScoreNotification(
                    applicationContext,
                    scoreDetail,
                    todayUnlockCount
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
                    idleMinutes = accumulatedIdleMinutes,
                    unlockCount = todayUnlockCount
                )
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        recalculateAndNotify()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
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
