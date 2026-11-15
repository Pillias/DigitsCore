package com.digitscore.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Locale
import androidx.core.app.NotificationCompat
import com.digitscore.app.R
import com.digitscore.app.engine.ScoreDetail
import com.digitscore.app.engine.RollingScoreDetail
import com.digitscore.app.model.ScoreTier
import com.digitscore.app.engine.CoreIndexCause
import com.digitscore.app.engine.CoreIndexGuidance
import com.digitscore.app.engine.CoreIndexRecommendation
import com.digitscore.app.engine.RapidUsageAlert
import com.digitscore.app.engine.RapidUsageAlertReason
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.localizedGrade
import com.digitscore.app.i18n.localizedGradeDescription
import com.digitscore.app.i18n.coachActionText
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
    const val WEEKLY_REPORT_CHANNEL_ID = "digitscore_weekly_report"
    const val NOTIFICATION_ID = 1001
    private const val GUIDANCE_NOTIFICATION_ID = 1002
    private const val GOAL_NOTIFICATION_ID = 1004
    private const val GOAL_UNLOCK_NOTIFICATION_ID = 1007
    const val NOTIFICATION_ID_WEEKLY_REPORT = 1008
    const val NOTIFICATION_ID_GOAL_LIMIT = 1009
    private const val MORNING_BRIEFING_REQUEST_CODE = 21
    private const val MORNING_BRIEFING_NOTIFICATION_ID = 1005
    const val ACTION_OPEN_WEEKLY_REPORT = "com.digitscore.app.action.OPEN_WEEKLY_REPORT"

    fun isWeeklyReportNotificationEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("weekly_report_enabled", true)
    }

    fun setWeeklyReportNotificationEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("notification_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("weekly_report_enabled", enabled).apply()
    }

    private const val TRACKING_RESUME_NOTIFICATION_ID = 7301

    /**
     * 부팅·업데이트 직후 OS가 추적 서비스 시작을 막았을 때 한 번 띄웁니다.
     * 탭하면 앱이 열리면서 서비스가 다시 연결됩니다.
     */
    fun showTrackingResumeNeeded(context: Context) {
        createNotificationChannel(context)
        val isEn = AppLocale.isEnglish(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            TRACKING_RESUME_NOTIFICATION_ID,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
            .setSmallIcon(DynamicIconGenerator.createScoreIconCompat(context, 75, StatusIconStyle.SCORE_PROPORTION))
            .setContentTitle(if (isEn) "Core Index paused" else "코어 지수 측정이 멈췄습니다")
            .setContentText(if (isEn) "Tap to resume tracking after the restart." else "기기가 다시 시작되었습니다. 탭하면 측정을 다시 시작합니다.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(TRACKING_RESUME_NOTIFICATION_ID, notification)
        }
    }

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
            manager.createNotificationChannel(
                NotificationChannel(
                    WEEKLY_REPORT_CHANNEL_ID,
                    strings.getString(R.string.weekly_report_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = strings.getString(R.string.weekly_report_channel_description)
                    enableVibration(false)
                }
            )
        }
    }

    fun showWeeklyReportNotification(
        context: Context,
        report: com.digitscore.app.data.WeeklyReportData
    ) {
        val strings = AppLocale.stringsContext(context)
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_WEEKLY_REPORT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_WEEKLY_REPORT,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val avgStr = report.avgScore?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--"
        val desc = if (report.hasGoalHistory) {
            strings.getString(R.string.weekly_report_notification_desc, avgStr, report.scoreGoalMetDays)
        } else {
            strings.getString(R.string.weekly_report_notification_desc_no_goals, avgStr)
        }

        val currentScore = report.avgScore?.toInt() ?: 80
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, currentScore, StatusIconStyle.SCORE_TIER)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, currentScore)

        val notification = NotificationCompat.Builder(context, WEEKLY_REPORT_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setColor(iconColor)
            .setContentTitle(strings.getString(R.string.weekly_report_notification_title))
            .setContentText(desc)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_WEEKLY_REPORT, notification)
    }

    fun showGoalMilestoneNotification(
        context: Context,
        goal: com.digitscore.app.model.DailyGoal,
        statusIconStyle: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION,
        forUnlock: Boolean = false
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
        val content = if (!forUnlock && goal.targetPackageName != null && goal.appProgressRatio >= 0.8f) {
            strings.getString(
                R.string.goal_milestone_content_app,
                goal.targetAppName,
                goal.currentAppUsageMinutes,
                goal.appLimitMinutes
            )
        } else if (forUnlock && goal.unlockProgressRatio >= 0.8f) {
            val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
            if (isEn) "Unlocks: ${goal.currentUnlockCount} / ${goal.unlockLimitTarget} (80% reached)"
            else "잠금 해제: ${goal.currentUnlockCount} / ${goal.unlockLimitTarget}회 (80% 도달)"
        } else {
            strings.getString(
                R.string.goal_milestone_content_score,
                goal.currentScore,
                goal.scoreTarget
            )
        }

        val currentScore = goal.currentScore
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, currentScore, statusIconStyle)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, currentScore)

        val notification = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setColor(iconColor)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 앱 목표와 잠금 해제 목표 알림이 같은 주기에 함께 와도 서로 덮어쓰지 않게 ID를 나눕니다.
        manager.notify(if (forUnlock) GOAL_UNLOCK_NOTIFICATION_ID else GOAL_NOTIFICATION_ID, notification)
    }

    fun showGoalLimitReachedNotification(
        context: Context,
        goal: com.digitscore.app.model.DailyGoal,
        resendCount: Int = 0
    ) {
        val strings = AppLocale.stringsContext(context)
        val pkg = goal.targetPackageName ?: return
        val rawAppName = if (goal.isAllManagedApps) {
            strings.getString(R.string.goal_all_managed_apps)
        } else {
            goal.targetAppName.ifEmpty { pkg.substringAfterLast('.') }
        }
        // 관리 앱 전체는 정해진 문구라 자르지 않습니다. 앱 이름만 길이를 줄입니다.
        val appName = if (!goal.isAllManagedApps && rawAppName.length > 10) rawAppName.take(9) + "…" else rawAppName

        val more15Intent = Intent(context, com.digitscore.app.receiver.GoalLimitActionReceiver::class.java).apply {
            action = com.digitscore.app.receiver.GoalLimitActionReceiver.ACTION_GOAL_LIMIT_MORE_15
            putExtra(com.digitscore.app.receiver.GoalLimitActionReceiver.EXTRA_PACKAGE_NAME, pkg)
            putExtra(com.digitscore.app.receiver.GoalLimitActionReceiver.EXTRA_APP_NAME, appName)
        }
        val more15PendingIntent = PendingIntent.getBroadcast(
            context,
            101,
            more15Intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopTodayIntent = Intent(context, com.digitscore.app.receiver.GoalLimitActionReceiver::class.java).apply {
            action = com.digitscore.app.receiver.GoalLimitActionReceiver.ACTION_GOAL_LIMIT_STOP_TODAY
            putExtra(com.digitscore.app.receiver.GoalLimitActionReceiver.EXTRA_PACKAGE_NAME, pkg)
            putExtra(com.digitscore.app.receiver.GoalLimitActionReceiver.EXTRA_APP_NAME, appName)
        }
        val stopTodayPendingIntent = PendingIntent.getBroadcast(
            context,
            102,
            stopTodayIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            103,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = strings.getString(R.string.goal_limit_reached_title, appName)
        val content = strings.getString(R.string.goal_limit_reached_content, appName, goal.appLimitMinutes)

        val currentScore = goal.currentScore
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, currentScore, StatusIconStyle.SCORE_TIER)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, currentScore)

        val builder = NotificationCompat.Builder(context, GOAL_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setColor(iconColor)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (resendCount < 3) {
            builder.addAction(
                0,
                strings.getString(R.string.goal_limit_action_more_15),
                more15PendingIntent
            )
        }
        builder.addAction(
            0,
            strings.getString(R.string.goal_limit_action_stop_today),
            stopTodayPendingIntent
        )

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_GOAL_LIMIT, builder.build())
    }

    fun showMorningBriefingHeadsUpNotification(context: Context) {
        val strings = AppLocale.stringsContext(context)
        val launchIntent = Intent(context, com.digitscore.app.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(com.digitscore.app.ui.MainActivity.EXTRA_SHOW_MORNING_BRIEFING, true)
        }
        // 목표 알림(요청 코드 20)과 같은 PendingIntent로 합쳐지면 브리핑 표시 extra가 덮어써지므로
        // 별도 요청 코드를 사용합니다.
        val pendingIntent = PendingIntent.getActivity(
            context,
            MORNING_BRIEFING_REQUEST_CODE,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val currentScore = ScoreRepository.rollingScoreDetail.value?.finalScore ?: 80
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, currentScore, StatusIconStyle.SCORE_TIER)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, currentScore)

        val title = strings.getString(R.string.morning_briefing_prompt_title)
        val content = strings.getString(R.string.morning_briefing_prompt_content)
        val actionText = strings.getString(R.string.morning_briefing_prompt_action)

        val notification = NotificationCompat.Builder(context, WAKE_PROMPT_CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setColor(iconColor)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_edit,
                actionText,
                pendingIntent
            )
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(MORNING_BRIEFING_NOTIFICATION_ID, notification)
    }

    fun cancelMorningBriefingNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(MORNING_BRIEFING_NOTIFICATION_ID)
    }

    fun showRapidUsageNotification(
        context: Context,
        score: Int,
        config: RapidUsageAlertConfig,
        alert: RapidUsageAlert,
        recoveryMinutes: Int?,
        statusIconStyle: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
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
        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, score, statusIconStyle)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, score)
        val notification = NotificationCompat.Builder(context, SOFT_GUIDANCE_CHANNEL_ID)
            .setSmallIcon(iconCompat)
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
        val grade = ScoreTier.fromScore(score)

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
        val isBelowDefense = dailyGoal != null && score < dailyGoal.scoreTarget
        val defenseLine = dailyGoal?.scoreTarget ?: 70
        val stepTarget = if (isBelowDefense) {
            guidance?.recoveryTargetScore?.takeIf { it > score && it <= defenseLine }
                ?: when {
                    score < 40 -> (score + 3).coerceAtLeast(40).coerceAtMost(45)
                    score < 50 -> (score + 4).coerceAtLeast(45).coerceAtMost(50)
                    score < 60 -> (score + 5).coerceAtLeast(55).coerceAtMost(60)
                    score < 70 -> (score + 5).coerceAtLeast(65).coerceAtMost(70)
                    else -> defenseLine
                }.coerceAtMost(defenseLine)
        } else {
            defenseLine
        }
        val pointsNeeded = (stepTarget - score).coerceAtLeast(1)
        val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
        val recMinutes = guidance?.recoveryMinutes
        val recoveryActionLong = if (recMinutes != null && recMinutes > 0) {
            strings.getString(R.string.action_rest_recovery_long, recMinutes, stepTarget)
        } else if (guidance?.leadingAppName != null && guidance.leadingAppMinutes > 15) {
            if (isEn) "Pausing managed apps like ${guidance.leadingAppName} (${guidance.leadingAppMinutes}min) will speed up recovery."
            else "${guidance.leadingAppName}(${guidance.leadingAppMinutes}min) 등 관리 앱을 잠시 멈추면 회복이 빨라집니다."
        } else {
            if (isEn) "Putting down the screen and resting will gradually restore your balance."
            else "화면 사용을 멈추고 휴식을 유지하면 점진적으로 회복됩니다."
        }

        val expandedText = buildList {
            if (dailyGoal != null && !dailyGoal.isDismissed) {
                add(if (isEn) "Today's 3 Key Goals" else "오늘의 목표")
                val targetScore = dailyGoal.scoreTarget
                val scoreDiff = (targetScore - score).coerceAtLeast(0)
                if (scoreDiff > 0) {
                    add(if (isEn) "• Core Index: ${score}P / Target ${targetScore}P (+${scoreDiff}P)" else "• 코어 지수: ${score}P / 목표 ${targetScore}P (+${scoreDiff}P)")
                } else {
                    add(if (isEn) "• Core Index: ${score}P / Target ${targetScore}P (Achieved ✓)" else "• 코어 지수: ${score}P / 목표 ${targetScore}P (달성 ✓)")
                }
                if (dailyGoal.targetPackageName != null) {
                    val appMins = dailyGoal.currentAppUsageMinutes
                    val appLimit = dailyGoal.appLimitMinutes
                    val appPercent = if (appLimit > 0) (appMins * 100 / appLimit) else 0
                    val warn = if (appMins > appLimit) (if (isEn) " [Over!]" else " [초과!]") else ""
                    add("• ${dailyGoal.targetAppName}: ${appMins} / ${appLimit}min (${appPercent}%)$warn")
                }
                val unlockTarget = dailyGoal.unlockLimitTarget
                val unlockPercent = if (unlockTarget > 0) (unlockCount * 100 / unlockTarget) else 0
                val warn = if (unlockCount > unlockTarget) (if (isEn) " [Over!]" else " [초과!]") else ""
                add(if (isEn) "• Unlocks: ${unlockCount} / ${unlockTarget} (${unlockPercent}%)$warn" else "• 잠금 해제: ${unlockCount} / ${unlockTarget}회 (${unlockPercent}%)$warn")
                add("")
                add(contentText)
            } else {
                add("${strings.getString(R.string.notification_key_label)} · $keyMessage")
                add(contentText)
            }
        }.joinToString("\n")

        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, score, statusIconStyle)
        val iconColor = DynamicIconGenerator.statusIconScoreColor(context, score)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconCompat)
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
        return "${minutes.coerceAtLeast(0L)}min"
    }

    private fun notificationActionMessage(
        strings: Context,
        guidance: CoreIndexGuidance?,
        grade: ScoreTier,
        dailyGoal: com.digitscore.app.model.DailyGoal?,
        score: Int
    ): String {
        // 1. 오늘의 설정/배정된 오늘의 목표(코어 지수, 앱 1개 제한, 잠금 해제 횟수) 진행 상태 안내
        if (dailyGoal != null && !dailyGoal.isDismissed) {
            val isEn = AppLocale.currentLanguage(strings) == AppLocale.ENGLISH
            // 지수 방어선까지 남은 여유(P). 방어선은 하루 마지막 점수로 판정하므로 낮에는 달성/미달 대신 여유로 말합니다.
            val margin = score - dailyGoal.scoreTarget
            val scorePart = if (margin >= 0) {
                if (isEn) "${margin}P above your defense line" else "방어선까지 여유 ${margin}P"
            } else {
                if (isEn) "${-margin}P to your defense line" else "방어선까지 ${-margin}P"
            }

            val appPart = if (dailyGoal.targetPackageName != null) {
                val appName = dailyGoal.targetAppName.ifBlank { if (isEn) "App" else "앱" }
                val isOver = dailyGoal.currentAppUsageMinutes > dailyGoal.appLimitMinutes
                val marker = if (isOver) "! " else ""
                "$marker$appName ${dailyGoal.currentAppUsageMinutes}/${dailyGoal.appLimitMinutes}min"
            } else null

            val isUnlockOver = dailyGoal.currentUnlockCount > dailyGoal.unlockLimitTarget
            val unlockMarker = if (isUnlockOver) "! " else ""
            val unlockPart = if (isEn) "${unlockMarker}Unlocks ${dailyGoal.currentUnlockCount}/${dailyGoal.unlockLimitTarget}"
            else "${unlockMarker}잠금 해제 ${dailyGoal.currentUnlockCount}/${dailyGoal.unlockLimitTarget}회"

            return listOfNotNull(scorePart, appPart, unlockPart).joinToString(" · ")
        }


        // 2. 목표가 없거나 처리 중일 때: 코칭 추천 메시지
        // 할 일 한 줄: 대시보드·위젯과 같은 문구(행동 + 결과 P)
        return guidance?.let {
            strings.coachActionText(
                com.digitscore.app.engine.CoachActionPlanner.plan(
                    recommendation = it.recommendation,
                    recoveryMinutes = it.recoveryMinutes,
                    recoveryTargetScore = it.recoveryTargetScore,
                    currentScore = score,
                    defenseLine = it.defenseLine,
                    shortRestGain = it.shortRestGain
                )
            )
        } ?: strings.localizedGradeDescription(grade)
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
