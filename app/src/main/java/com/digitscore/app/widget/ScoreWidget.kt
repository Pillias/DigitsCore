package com.digitscore.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.engine.ScoreGrade
import com.digitscore.app.ui.MainActivity

class ScoreWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val scoreDetail = ScoreRepository.currentScoreDetail.value
        val unlockCount = ScoreRepository.currentUnlockCount.value
        val score = scoreDetail?.finalScore ?: 100
        val grade = scoreDetail?.grade ?: ScoreGrade.S
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
            hours > 0 && mins > 0 -> "${hours}시간 ${mins}분"
            hours > 0 -> "${hours}시간"
            else -> "${mins}분"
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
                // Score
                Text(
                    text = "$score",
                    style = TextStyle(
                        color = ColorProvider(Color(scoreColorHex)),
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Text(
                    text = "GRADE ${grade.gradeText.take(1)}",
                    style = TextStyle(
                        color = ColorProvider(Color(scoreColorHex)),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = GlanceModifier.height(6.dp))

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
                        text = "🔓 ${unlockCount}회",
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
