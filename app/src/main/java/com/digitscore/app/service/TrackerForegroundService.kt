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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
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
                ScoreRepository.updateAppsUsage(appsUsage)

                // 시스템 이벤트에서 얻은 언락 횟수 근사치와 동기화
                val systemUnlocks = UsageStatsHelper.getTodayUnlockCount(applicationContext)
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
                        finalUnlockCount
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        recalculateAndNotify()
        return START_STICKY
    }

    override fun onDestroy() {
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
