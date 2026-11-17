package com.digitscore.app.service

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.notification.ScoreNotificationManager
import com.digitscore.app.notification.ScoreNotificationManager.TrackingStopReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 15분마다 측정이 살아 있는지 확인합니다.
 *
 * 사용자가 추적을 켜 두었는데 서비스가 꺼져 있으면 묻지 않고 다시 시작합니다. OS가 이번에
 * 시작을 막으면 다음 주기에 다시 시도하고, 앱을 여는 순간에도 홈 화면이 다시 시작합니다.
 * 사용자만 고칠 수 있는 사용 정보 접근 꺼짐만 알림으로 알리며, [NOTIFY_INTERVAL_MILLIS]에 한 번입니다.
 * 별도 라이브러리 없이 프레임워크 JobScheduler를 씁니다.
 */
class TrackerWatchdogJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        running = scope.launch {
            try {
                check(applicationContext)
            } catch (error: Exception) {
                Log.w(TAG, "Tracker watchdog check failed", error)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TrackerWatchdog"
        private const val JOB_ID = 7310
        private const val PERIOD_MILLIS = 15 * 60 * 1_000L
        internal const val NOTIFY_INTERVAL_MILLIS = 6 * 60 * 60 * 1_000L
        private const val PREFS = "tracker_watchdog"

        /** 이미 등록되어 있으면 다시 등록하지 않아 주기가 계속 밀리지 않게 합니다. */
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            if (scheduler.getPendingJob(JOB_ID) != null) return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, TrackerWatchdogJobService::class.java))
                .setPeriodic(PERIOD_MILLIS)
                .setPersisted(true)
                .build()
            runCatching { scheduler.schedule(job) }
                .onFailure { Log.w(TAG, "Failed to schedule tracker watchdog", it) }
        }

        private suspend fun check(context: Context) {
            val settings = DigitsDatabase.getInstance(context).settingsDao().getSettings()
            // 사용자가 추적을 끈 상태면 아무것도 하지 않습니다.
            if (settings?.isTrackingEnabled != true) return

            if (!UsageStatsHelper.hasUsageStatsPermission(context)) {
                notifyThrottled(context, TrackingStopReason.PERMISSION)
                return
            }
            if (ScoreRepository.isServiceRunning.value) return

            runCatching { TrackerForegroundService.start(context) }
                .onFailure { error ->
                    // Android 12+ 백그라운드 시작 제한 등으로 막힌 경우입니다. 다음 주기에 다시 시도합니다.
                    Log.w(TAG, "Tracker restart blocked; retrying next period", error)
                }
        }

        /** 같은 이유로는 6시간에 한 번만 알립니다. 권한 알림은 서비스도 같이 씁니다. */
        internal fun notifyThrottled(context: Context, reason: TrackingStopReason) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "last_notified_${reason.name}"
            val now = System.currentTimeMillis()
            if (!shouldNotify(prefs.getLong(key, 0L), now)) return
            prefs.edit().putLong(key, now).apply()
            ScoreNotificationManager.showTrackingResumeNeeded(context, reason)
        }

        internal fun shouldNotify(lastNotifiedMillis: Long, nowMillis: Long): Boolean =
            lastNotifiedMillis <= 0L ||
                nowMillis < lastNotifiedMillis ||
                nowMillis - lastNotifiedMillis >= NOTIFY_INTERVAL_MILLIS
    }
}
