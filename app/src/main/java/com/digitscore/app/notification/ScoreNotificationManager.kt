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
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.localizedGrade
import com.digitscore.app.i18n.localizedGradeDescription
import com.digitscore.app.ui.MainActivity
import com.digitscore.app.service.TrackerForegroundService

object ScoreNotificationManager {

    const val CHANNEL_ID = "digitscore_status_channel"
    const val NOTIFICATION_ID = 1001

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
        }
    }

    fun buildScoreNotification(
        context: Context,
        scoreDetail: ScoreDetail,
        unlockCount: Int,
        hideSensitiveOnLockScreen: Boolean = true,
        rollingScoreDetail: RollingScoreDetail? = null,
        statusIconStyle: StatusIconStyle = StatusIconStyle.SCORE_TIER
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

        val score = rollingScoreDetail?.finalScore ?: 80
        val grade = ScoreGrade.fromScore(score)

        val title = strings.getString(R.string.notification_title, score, strings.localizedGrade(grade))
        val contentText = strings.getString(
            R.string.notification_content,
            formatMinutesToHoursAndMinutes(strings, scoreDetail.totalScreenTimeMinutes),
            unlockCount,
            formatMinutesToHoursAndMinutes(strings, scoreDetail.distractingTimeMinutes)
        )

        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, score, statusIconStyle)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\n💡 ${strings.localizedGradeDescription(grade)}")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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
                    .setContentTitle(strings.getString(R.string.tracking_active))
                    .setContentText(strings.getString(R.string.unlock_for_details))
                    .setOngoing(true)
                    .setPriority(NotificationCompat.PRIORITY_LOW)
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
        statusIconStyle: StatusIconStyle = StatusIconStyle.SCORE_TIER
    ) {
        val notification = buildScoreNotification(
            context,
            scoreDetail,
            unlockCount,
            hideSensitiveOnLockScreen,
            rollingScoreDetail,
            statusIconStyle
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
}
