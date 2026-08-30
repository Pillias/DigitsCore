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
import com.digitscore.app.ui.MainActivity

object ScoreNotificationManager {

    const val CHANNEL_ID = "digitscore_status_channel"
    const val NOTIFICATION_ID = 1001

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notification_channel_description)
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
        unlockCount: Int
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val score = scoreDetail.finalScore
        val grade = scoreDetail.grade

        val title = "오늘의 디톡스 점수: ${score}점 (${grade.gradeText})"
        val contentText = "화면: ${formatMinutesToHoursAndMinutes(scoreDetail.totalScreenTimeMinutes)} | 언락: ${unlockCount}회 | 방해: ${formatMinutesToHoursAndMinutes(scoreDetail.distractingTimeMinutes)}"

        val iconCompat = DynamicIconGenerator.createScoreIconCompat(context, score)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconCompat)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$contentText\n\n💡 ${grade.description}")
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    fun updateScoreNotification(
        context: Context,
        scoreDetail: ScoreDetail,
        unlockCount: Int
    ) {
        val notification = buildScoreNotification(context, scoreDetail, unlockCount)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun formatMinutesToHoursAndMinutes(minutes: Long): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}시간 ${mins}분"
            hours > 0 -> "${hours}시간"
            else -> "${mins}분"
        }
    }
}
