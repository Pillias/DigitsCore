package com.digitscore.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * 글자 기준 (docs/DESIGN_SYSTEM.md "글자" 절).
 * - 글꼴: 기기 기본 글꼴을 그대로 씁니다.
 * - 굵기: Regular(Normal)·Medium·Bold 세 가지만 씁니다.
 * - 숫자: 고정폭 숫자(tnum)로 점수·시간이 바뀔 때 줄이 흔들리지 않게 합니다.
 * - 한글 본문 글자 간격은 0입니다.
 * - 줄바꿈: 어절 단위 (낱말 중간에서 끊지 않음).
 */
private const val TABULAR_NUMBERS = "tnum"

private fun style(size: Int, line: Int, weight: FontWeight) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    fontFeatureSettings = TABULAR_NUMBERS,
    // 한국어가 낱말 중간에서 줄바뀌지 않게 어절(띄어쓰기) 단위로 줄을 바꿉니다 (Android 13+, 그 이하는 기존 방식).
    lineBreak = LineBreak(
        strategy = LineBreak.Strategy.HighQuality,
        strictness = LineBreak.Strictness.Strict,
        wordBreak = LineBreak.WordBreak.Phrase
    )
)

val Typography = Typography(
    // 코어 지수 큰 숫자
    displaySmall = style(36, 42, FontWeight.Bold),
    // 화면 제목
    headlineSmall = style(24, 30, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.Bold),
    // 카드 제목
    titleMedium = style(16, 22, FontWeight.Bold),
    titleSmall = style(14, 20, FontWeight.Bold),
    // 본문
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    // 보조 (라벨·범례·날짜)
    bodySmall = style(12, 16, FontWeight.Normal),
    labelLarge = style(14, 20, FontWeight.Medium),
    labelMedium = style(12, 16, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium)
)
