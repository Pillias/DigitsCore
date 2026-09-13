package com.digitscore.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.digitscore.app.i18n.Text
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow

/**
 * 런처·상태바·위젯에서 사용하는 전원 버튼형 코어 링을 앱 화면에도 동일하게 표현합니다.
 * 빨간 바탕을 점수 비율만큼 녹색이 덮으며, 중앙 막대는 네 단계 상태색을 사용합니다.
 */
@Composable
fun CoreIndexGauge(
    score: Int,
    grade: String,
    modifier: Modifier = Modifier
) {
    val normalizedScore = score.coerceIn(0, 100)
    val animatedScore by animateFloatAsState(
        targetValue = normalizedScore.toFloat(),
        animationSpec = tween(durationMillis = 700),
        label = "coreIndexGauge"
    )
    val tierColor = coreIndexTierColor(normalizedScore)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = size.minDimension * 0.085f
            val inset = strokeWidth * 0.72f
            val ringSize = size.minDimension - inset * 2f
            val left = (size.width - ringSize) / 2f
            val top = (size.height - ringSize) / 2f
            val ringBoundsTopLeft = Offset(left, top)
            val ringBoundsSize = Size(ringSize, ringSize)
            val ringStyle = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            val startAngle = -52f
            val fullSweep = 284f

            drawArc(
                color = ScoreRed,
                startAngle = startAngle,
                sweepAngle = fullSweep,
                useCenter = false,
                topLeft = ringBoundsTopLeft,
                size = ringBoundsSize,
                style = ringStyle
            )
            if (animatedScore > 0f) {
                drawArc(
                    color = ScoreGreen,
                    startAngle = startAngle,
                    sweepAngle = fullSweep * animatedScore / 100f,
                    useCenter = false,
                    topLeft = ringBoundsTopLeft,
                    size = ringBoundsSize,
                    style = ringStyle
                )
            }

            drawLine(
                color = tierColor,
                start = Offset(size.width / 2f, size.height * 0.075f),
                end = Offset(size.width / 2f, size.height * 0.285f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = normalizedScore.toString(),
                fontSize = 48.sp,
                fontWeight = FontWeight.Black,
                color = tierColor
            )
            Text(
                text = grade,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

fun coreIndexTierColor(score: Int): Color = when {
    score >= 80 -> ScoreGreen
    score >= 60 -> ScoreYellow
    score >= 40 -> ScoreOrange
    else -> ScoreRed
}
