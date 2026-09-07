package com.digitscore.app.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.layout.ContentScale
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.R
import com.digitscore.app.ui.MainActivity
import com.digitscore.app.notification.DynamicIconGenerator
import com.digitscore.app.notification.StatusIconStyle

class ScoreWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scoreDetail = ScoreRepository.currentScoreDetail.value
        val rollingScoreDetail = ScoreRepository.rollingScoreDetail.value
        val strings = AppLocale.stringsContext(context)
        val unlockCount = ScoreRepository.currentUnlockCount.value
        val score = rollingScoreDetail?.finalScore ?: 80
        val grade = ScoreGrade.fromScore(score)
        val screenMinutes = scoreDetail?.totalScreenTimeMinutes ?: 0L

        val scoreColorHex = when {
            score >= 80 -> 0xFF2ECC71.toInt()
            score >= 60 -> 0xFFF1C40F.toInt()
            score >= 40 -> 0xFFE67E22.toInt()
            else -> 0xFFE74C3C.toInt()
        }

        val hours = screenMinutes / 60
        val mins = screenMinutes % 60
        val timeStr = when {
            hours > 0 && mins > 0 -> strings.getString(R.string.format_hours_minutes, hours, mins)
            hours > 0 -> strings.getString(R.string.format_hours, hours)
            else -> strings.getString(R.string.format_minutes, mins)
        }

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1E24))
                    .cornerRadius(16.dp)
                    .padding(12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 런처와 달리 위젯은 현재 점수만큼 녹색이 이동하는 동적 게이지를 표시합니다.
                Image(
                    provider = ImageProvider(
                        DynamicIconGenerator.createScoreBitmapIcon(
                            context,
                            score,
                            StatusIconStyle.SCORE_PROPORTION
                        )
                    ),
                    contentDescription = "DigitsCore Index $score",
                    modifier = GlanceModifier.size(56.dp),
                    contentScale = ContentScale.Fit
                )

                Text(
                    text = strings.getString(R.string.grade_label, grade.name),
                    style = TextStyle(
                        color = ColorProvider(Color(scoreColorHex)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                // Stats row
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📱 $timeStr",
                        style = TextStyle(
                            color = ColorProvider(Color.LightGray),
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "🔓 ${strings.getString(R.string.unlock_count_short, unlockCount)}",
                        style = TextStyle(
                            color = ColorProvider(Color.LightGray),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
