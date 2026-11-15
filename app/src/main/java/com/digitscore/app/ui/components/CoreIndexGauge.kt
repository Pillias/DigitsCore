package com.digitscore.app.ui.components

import com.digitscore.app.ui.theme.scoreTextColor
import com.digitscore.app.ui.theme.DigitsTextSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.i18n.Text
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow

/**
 * 앱 아이콘과 같은 모양: 도룡뇽 몸이 점수 비율만큼 빨간 링을 덮고, 머리가 그 끝에서 빨간 링을 먹습니다.
 * 중앙 막대와 도룡뇽 몸 색·표정은 점수 단계를 따릅니다.
 * 점수와 등급 텍스트는 아이콘 아래에 깔끔하게 분리 배치합니다.
 */
@Composable
fun CoreIndexGauge(
    score: Int,
    grade: String,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    /** 달성 순간(단계 상승 등)에 링을 0부터 한 번 부드럽게 채웁니다 (평가 R-3). */
    celebrate: Boolean = false
) {
    val normalizedScore = score.coerceIn(0, 100)
    val anim = remember { Animatable(if (celebrate) 0f else normalizedScore.toFloat()) }
    LaunchedEffect(normalizedScore, celebrate) {
        if (celebrate) anim.snapTo(0f)
        anim.animateTo(normalizedScore.toFloat(), tween(durationMillis = if (celebrate) 1400 else 700))
    }
    val animatedScore = anim.value
    val tierColor = coreIndexTierColor(normalizedScore)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 앱 아이콘과 같은 도룡뇽 게이지: 몸이 지수만큼 링을 덮고, 아이콘의 머리 그림이 남은 빨간 링을 먹습니다.
        val heads = rememberGaugeHeads()
        Canvas(modifier = Modifier.size(iconSize)) {
            drawSalamanderGauge(animatedScore, com.digitscore.app.model.ScoreTier.fromScore(normalizedScore.coerceAtLeast(1)), heads)
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 아래로 내린 점수 글씨
        Text(
            text = normalizedScore.toString(),
            fontSize = if (iconSize > 60.dp) DigitsTextSize.hero else if (iconSize <= 42.dp) DigitsTextSize.headlineLarge else DigitsTextSize.display,
            fontWeight = FontWeight.Bold,
            color = scoreTextColor(normalizedScore),
            lineHeight = if (iconSize > 60.dp) 34.sp else if (iconSize <= 42.dp) 24.sp else 28.sp
        )

        // 아래로 내린 등급 글씨
        Text(
            text = grade,
            fontSize = if (iconSize > 60.dp) DigitsTextSize.label else if (iconSize <= 42.dp) DigitsTextSize.tiny else DigitsTextSize.caption,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = if (iconSize <= 42.dp) 12.sp else 14.sp,
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center
        )
    }
}

/** 링·그래프 채우기용 점수 색. 글자에는 [scoreTextColor]를 씁니다. */
fun coreIndexTierColor(score: Int): Color = Color(com.digitscore.app.ui.theme.ScorePalette.fillArgb(score))
