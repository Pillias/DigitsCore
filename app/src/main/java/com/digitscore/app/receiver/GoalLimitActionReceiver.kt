package com.digitscore.app.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.digitscore.app.data.DailyGoalStore
import com.digitscore.app.data.GoalLimitChoiceStore
import com.digitscore.app.notification.ScoreNotificationManager
import com.digitscore.app.service.TrackerForegroundService

class GoalLimitActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_GOAL_LIMIT_MORE_15 = "com.digitscore.app.action.GOAL_LIMIT_MORE_15"
        const val ACTION_GOAL_LIMIT_STOP_TODAY = "com.digitscore.app.action.GOAL_LIMIT_STOP_TODAY"
        const val EXTRA_PACKAGE_NAME = "com.digitscore.app.extra.PACKAGE_NAME"
        const val EXTRA_APP_NAME = "com.digitscore.app.extra.APP_NAME"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return

        // 알림 닫기
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ScoreNotificationManager.NOTIFICATION_ID_GOAL_LIMIT)

        val todayDate = DailyGoalStore.getLogicalDateString()
        val now = System.currentTimeMillis()

        when (action) {
            ACTION_GOAL_LIMIT_MORE_15 -> {
                GoalLimitChoiceStore.recordChoice(
                    context = context,
                    dateString = todayDate,
                    packageName = packageName,
                    choice = GoalLimitChoiceStore.CHOICE_MORE_15,
                    timestampMillis = now
                )
                // 15분 뒤 같은 앱을 계속 쓰고 있으면 화면 신호등을 한 번 켜고 같은 알림을 다시 보냄
                GoalLimitChoiceStore.setMore15Snooze(
                    context = context,
                    packageName = packageName,
                    snoozeUntilMillis = now + 15 * 60_000L
                )
            }
            ACTION_GOAL_LIMIT_STOP_TODAY -> {
                GoalLimitChoiceStore.recordChoice(
                    context = context,
                    dateString = todayDate,
                    packageName = packageName,
                    choice = GoalLimitChoiceStore.CHOICE_STOP_TODAY,
                    timestampMillis = now
                )
                // 그날 남은 시간 동안 그 앱의 화면 신호등 재알림 간격을 5min으로 줄임
                GoalLimitChoiceStore.setStopToday(
                    context = context,
                    dateString = todayDate,
                    packageName = packageName,
                    active = true
                )
                GoalLimitChoiceStore.clearMore15Snooze(context, packageName)
            }
        }

        // 서비스가 갱신할 수 있도록 알림 새로고침 인텐트 전송
        val refreshIntent = Intent(context, TrackerForegroundService::class.java).apply {
            this.action = TrackerForegroundService.ACTION_REFRESH_NOTIFICATION
        }
        try {
            context.startService(refreshIntent)
        } catch (_: Exception) {}
    }
}
