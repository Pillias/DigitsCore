package com.digitscore.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.i18n.Text
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow

/**
 * 전원 버튼(Power Symbol) 아이콘을 정확한 기하학적 비율로 구현하고
 * 현재 코어 지수 구간 색상을 적용하며,
 * 점수와 등급 텍스트는 아이콘 아래에 깔끔하게 배치합니다.
 */
@Composable
fun CoreIndexGauge(
    score: Int,
    grade: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp
) {
    val normalizedScore = score.coerceIn(0, 100)
    val tierColor = coreIndexTierColor(normalizedScore)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 정확한 전원 버튼 (Power Symbol) 아이콘
        Canvas(modifier = Modifier.size(iconSize)) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val strokeWidth = size.minDimension * 0.115f
            val r = (size.minDimension - strokeWidth * 2.2f) / 2f

            // 원형 아크: 상단에 60도 갭 (시작각 -60도에서 시계방향 300도 회전)
            val startAngle = -60f
            val sweepAngle = 300f
            val arcTopLeft = Offset(cx - r, cy - r)
            val arcSize = Size(r * 2f, r * 2f)

            // 부드러운 배경 링 (미세 발광 효과)
            drawArc(
                color = tierColor.copy(alpha = 0.15f),
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth * 1.4f, cap = StrokeCap.Round)
            )

            // 메인 전원 링
            drawArc(
                color = tierColor,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 상단 수직 스템 막대 (전원 버튼 중앙 선)
            val stemTop = cy - r * 1.15f
            val stemBottom = cy - r * 0.08f

            // 스템 배경 발광
            drawLine(
                color = tierColor.copy(alpha = 0.15f),
                start = Offset(cx, stemTop),
                end = Offset(cx, stemBottom),
                strokeWidth = strokeWidth * 1.4f,
                cap = StrokeCap.Round
            )

            // 메인 스템 선
            drawLine(
                color = tierColor,
                start = Offset(cx, stemTop),
                end = Offset(cx, stemBottom),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 아래로 내린 점수 글씨
        Text(
            text = normalizedScore.toString(),
            fontSize = if (iconSize > 60.dp) 32.sp else 26.sp,
            fontWeight = FontWeight.Black,
            color = tierColor,
            lineHeight = if (iconSize > 60.dp) 34.sp else 28.sp
        )

        // 아래로 내린 등급 글씨
        Text(
            text = grade,
            fontSize = if (iconSize > 60.dp) 12.sp else 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 14.sp
        )
    }
}

fun coreIndexTierColor(score: Int): Color = when {
    score >= 80 -> ScoreGreen
    score >= 60 -> ScoreYellow
    score >= 40 -> ScoreOrange
    else -> ScoreRed
}
