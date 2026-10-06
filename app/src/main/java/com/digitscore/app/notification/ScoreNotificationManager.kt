package com.digitscore.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.digitscore.app.R
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.RollingScoreDetail
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.engine.CoreIndexCause
import com.digitscore.app.engine.CoreIndexGuidance
import com.digitscore.app.engine.CoreIndexRecommendation
import com.digitscore.app.engine.RapidUsageAlert
import com.digitscore.app.engine.RapidUsageAlertReason
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.localizedGrade
import com.digitscore.app.i18n.localizedGradeDescription
import com.digitscore.app.ui.MainActivity
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.model.RapidUsageAlertConfig
import com.digitscore.app.data.ScoreRepository

object ScoreNotificationManager {

    const val CHANNEL_ID = "digitscore_status_channel"
    const val GUIDANCE_CHANNEL_ID = "digitscore_guidance_channel"
    const val SOFT_GUIDANCE_CHANNEL_ID = "digitscore_soft_guidance_v2"
    const val WAKE_PROMPT_CHANNEL_ID = "digitscore_wake_prompt_channel"
    const val GOAL_CHANNEL_ID = "digitscore_goal_channel"
    const val NOTIFICATION_ID = 1001
    private const val GUIDANCE_NOTIFICATION_ID = 1002
    private const val WAKE_PROMPT_NOTIFICATION_ID = 1003
    private const val GOAL_NOTIFICATION_ID = 1004

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val strings = AppLocale.stringsContext(context)
            val channel = NotificationChannel(
                CHANNEL_ID,
                strings.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = strings.getString(R.string.notification_channel_description)
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(
                NotificationChannel(
                    GUIDANCE_CHANNEL_ID,
                    strings.getString(R.string.guidance_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = strings.getString(R.string.guidance_channel_description)
                    enableVibration(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    SOFT_GUIDANCE_CHANNEL_ID,
                    strings.getString(R.string.rapid_alert_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = strings.getString(R.string.rapid_alert_channel_description)
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    WAKE_PROMPT_CHANNEL_ID,
                    strings.getString(R.string.wake_prompt_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = strings.getString(R.string.wake_prompt_channel_description)
                    enableVibration(true)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    GOAL_CHANNEL_ID,
                    strings.getString(R.string.goal_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = strings.getString(R.string.goal_channel_description)
                    enableVibration(false)
                }
            )
        }
    }

    fun showGoalMilestoneNotification(
        context: Context,
        goal: com.digitscore.app.model.DailyGoal
    ) {
        val strings = AppLocale.stringsContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            20,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = strings.getString(R.string.goal_milestone_title)
        val content = if (goal.targetPackageName != null && goal.appProgressRatio >= 0.8f) {
            strings.getString(
                R.string.goal_milestone_content_app,
                goal.targetAppName,
                goal.currentAppUsageMinutes,
                goal.appLimitMinutes
            )
        } else if (goal.unlockProgressRatio >= 0.8f) {
            "${goal.currentUnlockCount}회 / 목표 ${goal.unlockLimitTarget}회 (80% 도달)"
        } else {
            strings.getString(
                R.string.goal_milestone_content_score,
                goal.currentScore,
                goal.scoreTarget
            )
        }

        val currentScore = ScoreRepository.rollingScoreDetail.value?.finalScore ?: 75
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, currentScore, StatusIconStyle.SCORE_PROPORTION)
        val largeIcon = DynamicIconGenerator.createScoreLargeIcon(context, currentScore, StatusIconStyle.SCORE_PROPORTION)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, currentScore)

        val notification = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setLargeIcon(largeIcon)
            .setColor(iconColor)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(GOAL_NOTIFICATION_ID, notification)
    }

    fun showMorningWakePromptNotification(context: Context, timestamp: Long) {
        val strings = AppLocale.stringsContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            10,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = PendingIntent.getService(
            context,
            11,
            Intent(context, TrackerForegroundService::class.java).apply {
                action = TrackerForegroundService.ACTION_CONFIRM_WAKE
                putExtra("timestamp", timestamp)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getService(
            context,
            12,
            Intent(context, TrackerForegroundService::class.java).apply {
                action = TrackerForegroundService.ACTION_SNOOZE_WAKE
                putExtra("timestamp", timestamp)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentScore = ScoreRepository.rollingScoreDetail.value?.finalScore ?: 80
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, currentScore, StatusIconStyle.SCORE_PROPORTION)
        val largeIcon = DynamicIconGenerator.createScoreLargeIcon(context, currentScore, StatusIconStyle.SCORE_PROPORTION)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, currentScore)

        val notification = NotificationCompat.Builder(context, WAKE_PROMPT_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setLargeIcon(largeIcon)
            .setColor(iconColor)
            .setContentTitle(strings.getString(R.string.wake_prompt_title))
            .setContentText(strings.getString(R.string.wake_prompt_content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_play,
                strings.getString(R.string.wake_prompt_action_start),
                confirmIntent
            )
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                strings.getString(R.string.wake_prompt_action_rest),
                snoozeIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(WAKE_PROMPT_NOTIFICATION_ID, notification)
    }

    fun cancelMorningWakePromptNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(WAKE_PROMPT_NOTIFICATION_ID)
    }

    fun showRapidUsageNotification(
        context: Context,
        score: Int,
        config: RapidUsageAlertConfig,
        alert: RapidUsageAlert,
        recoveryMinutes: Int?
    ) {
        val strings = AppLocale.stringsContext(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val body = when (alert.reason) {
            RapidUsageAlertReason.SCORE_DROP -> strings.getString(
                R.string.rapid_alert_score_drop,
                config.windowMinutes,
                alert.scoreDrop
            )
            RapidUsageAlertReason.HIGH_USAGE -> strings.getString(
                R.string.rapid_alert_high_usage,
                config.windowMinutes,
                alert.windowUsageMinutes
            )
            RapidUsageAlertReason.CONTINUOUS_USE -> strings.getString(
                R.string.rapid_alert_continuous,
                alert.continuousUsageMinutes
            )
        }
        val recovery = recoveryMinutes?.let {
            strings.getString(R.string.rapid_alert_recovery, it)
        }
        val expanded = listOfNotNull(body, recovery).joinToString("\n")
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, score, StatusIconStyle.SCORE_PROPORTION)
        val largeIcon = DynamicIconGenerator.createScoreLargeIcon(context, score, StatusIconStyle.SCORE_PROPORTION)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, score)
        val notification = NotificationCompat.Builder(context, SOFT_GUIDANCE_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setLargeIcon(largeIcon)
            .setColor(iconColor)
            .setContentTitle(strings.getString(R.string.rapid_alert_title, score))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(GUIDANCE_NOTIFICATION_ID, notification)
    }

    fun buildScoreNotification(
        context: Context,
        scoreDetail: ScoreDetail,
        unlockCount: Int,
        hideSensitiveOnLockScreen: Boolean = true,
        rollingScoreDetail: RollingScoreDetail? = null,
        statusIconStyle: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION,
        guidance: CoreIndexGuidance? = null,
        dailyGoal: com.digitscore.app.model.DailyGoal? = null
    ): Notification {
        val strings = AppLocale.stringsContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopTrackingIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, TrackerForegroundService::class.java).apply {
                action = TrackerForegroundService.ACTION_STOP_TRACKING
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val score = rollingScoreDetail?.finalScore ?: 75
        val grade = ScoreGrade.fromScore(score)

        val title = strings.getString(R.string.notification_title, score, strings.localizedGrade(grade))
        val contentText = strings.getString(
            R.string.notification_content,
            formatMinutesToHoursAndMinutes(strings, scoreDetail.totalScreenTimeMinutes),
            unlockCount,
            formatMinutesToHoursAndMinutes(strings, scoreDetail.distractingTimeMinutes)
        )
        val keyMessage = notificationActionMessage(strings, guidance, grade, dailyGoal, score)
        val causeMessage = notificationCauseMessage(strings, guidance)
        val recoveryMessage = guidance?.recoveryMinutes?.let { minutes ->
            guidance.recoveryTargetScore?.let { target ->
                strings.getString(R.string.notification_recovery_forecast, minutes, target)
            }
        }
        val expandedText = buildList {
            add("${strings.getString(R.string.notification_key_label)} · $keyMessage")
            causeMessage?.takeIf { it.isNotBlank() }?.let(::add)
            recoveryMessage?.takeIf { it.isNotBlank() }?.let(::add)
            add("")
            add(contentText)
        }.joinToString("\n")

        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, score, statusIconStyle)
        val largeIcon = DynamicIconGenerator.createScoreLargeIcon(context, score, statusIconStyle)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, score)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setLargeIcon(largeIcon)
            .setColor(iconColor)
            .setContentTitle(title)
            .setContentText(keyMessage)
            .setSubText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedText)
                    .setSummaryText(contentText)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setVisibility(
                if (hideSensitiveOnLockScreen) NotificationCompat.VISIBILITY_PRIVATE
                else NotificationCompat.VISIBILITY_PUBLIC
            )
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                strings.getString(R.string.tracking_stop),
                stopTrackingIntent
            )
        if (hideSensitiveOnLockScreen) {
            builder.setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(iconCompat)
                    .setLargeIcon(largeIcon)
                    .setColor(iconColor)
                    .setContentTitle(strings.getString(R.string.tracking_active))
                    .setContentText(strings.getString(R.string.unlock_for_details))
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
                    .setCategory(NotificationCompat.CATEGORY_SERVICE)
                    .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setContentIntent(pendingIntent)
                    .build()
            )
        }
        return builder.build()
    }

    fun updateScoreNotification(
        context: Context,
        scoreDetail: ScoreDetail,
        unlockCount: Int,
        hideSensitiveOnLockScreen: Boolean = true,
        rollingScoreDetail: RollingScoreDetail? = null,
        statusIconStyle: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION,
        guidance: CoreIndexGuidance? = null,
        dailyGoal: com.digitscore.app.model.DailyGoal? = null
    ) {
        val notification = buildScoreNotification(
            context,
            scoreDetail,
            unlockCount,
            hideSensitiveOnLockScreen,
            rollingScoreDetail,
            statusIconStyle,
            guidance,
            dailyGoal
        )
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatMinutesToHoursAndMinutes(context: Context, minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> context.getString(R.string.format_hours_minutes, hours, mins)
            hours > 0 -> context.getString(R.string.format_hours, hours)
            else -> context.getString(R.string.format_minutes, mins)
        }
    }

    private fun notificationActionMessage(
        strings: Context,
        guidance: CoreIndexGuidance?,
        grade: ScoreGrade,
        dailyGoal: com.digitscore.app.model.DailyGoal?,
        score: Int
    ): String {
        // 1. 오늘의 설정/배정된 목표가 있는 경우 목표 진행 상태를 최우선으로 안내
        if (dailyGoal != null && !dailyGoal.isDismissed) {
            // (1) 특정 앱이 초과되었거나 80%에 근접한 경우
            if (dailyGoal.targetPackageName != null) {
                if (dailyGoal.currentAppUsageMinutes > dailyGoal.appLimitMinutes) {
                    return strings.getString(
                        R.string.notification_goal_app_exceeded,
                        dailyGoal.targetAppName,
                        dailyGoal.currentAppUsageMinutes,
                        dailyGoal.appLimitMinutes
                    )
                } else if (dailyGoal.appProgressRatio >= 0.8f) {
                    val percent = (dailyGoal.appProgressRatio * 100).toInt()
                    return strings.getString(
                        R.string.notification_goal_app_pace,
                        dailyGoal.targetAppName,
                        dailyGoal.currentAppUsageMinutes,
                        dailyGoal.appLimitMinutes,
                        percent
                    )
                }
            }

            // (2) 잠금 해제가 80% 이상 소진된 경우
            if (dailyGoal.unlockProgressRatio >= 0.8f) {
                return if (dailyGoal.currentUnlockCount > dailyGoal.unlockLimitTarget) {
                    strings.getString(
                        R.string.notification_goal_unlock_exceeded,
                        dailyGoal.currentUnlockCount,
                        dailyGoal.unlockLimitTarget
                    )
                } else {
                    strings.getString(
                        R.string.notification_goal_unlock_pace,
                        dailyGoal.currentUnlockCount,
                        dailyGoal.unlockLimitTarget
                    )
                }
            }

            // (3) 코어 지수가 방어선 미만으로 내려간 경우
            if (dailyGoal.currentScore < dailyGoal.scoreTarget) {
                return strings.getString(
                    R.string.notification_goal_score_warning,
                    dailyGoal.currentScore,
                    dailyGoal.scoreTarget
                )
            }

            // (4) 평시: 특정 앱 진행 상황과 방어선 유지 안내
            if (dailyGoal.targetPackageName != null) {
                val percent = (dailyGoal.appProgressRatio * 100).toInt()
                return "${dailyGoal.targetAppName}: ${dailyGoal.currentAppUsageMinutes}분/${dailyGoal.appLimitMinutes}분 ($percent%) · 방어선 ${dailyGoal.scoreTarget}점 유지 중"
            }

            // (5) 특정 앱이 없을 때 코어 지수 정상 유지
            return strings.getString(
                R.string.notification_goal_score_good,
                dailyGoal.currentScore,
                dailyGoal.scoreTarget
            )
        }

        // 2. 목표가 없거나 처리 중일 때: 코칭 추천 메시지
        return when (guidance?.recommendation) {
            CoreIndexRecommendation.TAKE_TEN_MINUTE_BREAK ->
                strings.getString(R.string.notification_action_ten_minute_break)
            CoreIndexRecommendation.TAKE_QUIET_BREAK ->
                strings.getString(R.string.notification_action_quiet_break)
            CoreIndexRecommendation.BATCH_PHONE_CHECKS ->
                strings.getString(R.string.notification_action_batch_checks)
            CoreIndexRecommendation.WIND_DOWN ->
                strings.getString(R.string.notification_action_wind_down)
            CoreIndexRecommendation.KEEP_BALANCE ->
                strings.getString(R.string.notification_action_keep_balance)
            null -> strings.localizedGradeDescription(grade)
        }
    }

    private fun notificationCauseMessage(
        strings: Context,
        guidance: CoreIndexGuidance?
    ): String? = when (guidance?.cause) {
        CoreIndexCause.CALIBRATING -> strings.getString(R.string.notification_cause_calibrating)
        CoreIndexCause.CONTINUOUS_USE -> strings.getString(
            R.string.notification_cause_continuous,
            guidance.continuousUsageMinutes.coerceAtLeast(1L)
        )
        CoreIndexCause.MANAGED_APP_USE -> guidance.leadingAppName?.let {
            strings.getString(
                R.string.notification_cause_managed,
                it,
                guidance.leadingAppMinutes
            )
        }
        CoreIndexCause.FREQUENT_UNLOCKS -> strings.getString(
            R.string.notification_cause_unlocks,
            guidance.rollingUnlockCount,
            guidance.shortOpenCount
        )
        CoreIndexCause.RECOVERING -> strings.getString(R.string.notification_cause_recovering)
        CoreIndexCause.STEADY -> strings.getString(R.string.notification_cause_steady)
        null -> null
    }
}
