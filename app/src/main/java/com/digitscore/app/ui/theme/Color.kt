package com.digitscore.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.digitscore.app.model.AppGrade

// DigitsCore 브랜드 컬러 — 점수 4단계(80/60/40)의 채우기 색 (링·그래프·아이콘)
val ScoreGreen = Color(ScorePalette.FILL[0])
val ScoreYellow = Color(ScorePalette.FILL[1])
val ScoreOrange = Color(ScorePalette.FILL[2])
val ScoreRed = Color(ScorePalette.FILL[3])

val DarkBackground = Color(0xFF0D1117)
val DarkSurface = Color(0xFF161B22)
val DarkCard = Color(0xFF21262D)
val AccentCyan = Color(0xFF58A6FF)

val LightBackground = Color(0xFFF6F7F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceMuted = Color(0xFFF0F2F5)
val LightPrimary = Color(0xFF1769AA)
val LightText = Color(0xFF191C20)
val LightOutline = Color(0xFF69717C)

/** 차트 계열 색: 화면 시간(청록), 관리 앱(빨강), 잠금 해제(하늘). 글자에는 쓰지 않고 막대·선·점에만 씁니다. */
val ChartScreen = Color(0xFF26A69A)
val ChartManaged = ScoreRed
val ChartUnlock = Color(0xFF38BDF8)

/**
 * 점수 색의 단일 기준표. 앱·위젯·상태바·화면 신호등이 모두 이 표를 씁니다.
 * - FILL: 링·그래프·아이콘 채우기
 * - TEXT_ON_LIGHT: 밝은 배경 위 글자 (흰 배경 대비 4.5:1 이상)
 * - TEXT_ON_DARK: 어두운 배경 위 글자
 */
object ScorePalette {
    val FILL = longArrayOf(0xFF2ECC71, 0xFFF1C40F, 0xFFE67E22, 0xFFE74C3C)
    val TEXT_ON_LIGHT = longArrayOf(0xFF087A46, 0xFF9A6200, 0xFFC34A00, 0xFFC62828)
    val TEXT_ON_DARK = longArrayOf(0xFF49E291, 0xFFFFD54F, 0xFFFFA05A, 0xFFFF6B62)

    /** 0 = 80점 이상, 1 = 60~79, 2 = 40~59, 3 = 39 이하 */
    fun tier(score: Int): Int = when {
        score >= 80 -> 0
        score >= 60 -> 1
        score >= 40 -> 2
        else -> 3
    }

    fun fillArgb(score: Int): Int = FILL[tier(score)].toInt()
    fun textArgb(score: Int, onDark: Boolean): Int =
        (if (onDark) TEXT_ON_DARK else TEXT_ON_LIGHT)[tier(score)].toInt()
}

/** 앱 등급 4색: 선택된 칸 배경(흰 글자)과 글자·점 색. */
object GradePalette {
    private val FILL = longArrayOf(0xFF15803D, 0xFF0277BD, 0xFF5F6B78, 0xFFC62828)
    private val ACCENT_ON_DARK = longArrayOf(0xFF49E291, 0xFF4FC3F7, 0xFFB0BAC5, 0xFFFF6B62)
    fun fill(grade: AppGrade): Color = Color(FILL[grade.ordinal])
    fun accent(grade: AppGrade, onDark: Boolean): Color =
        Color((if (onDark) ACCENT_ON_DARK else FILL)[grade.ordinal])
}

/** 현재 테마가 어두운지: 배경 밝기로 판단해 앱 설정(라이트/다크/시스템)을 그대로 따릅니다. */
val ColorScheme.isDark: Boolean get() = background.luminance() < 0.5f

/** 카드 안의 작은 칸 배경. 카드(surfaceVariant)와 항상 구분되는 색을 돌려줍니다. */
val ColorScheme.tile: Color get() = if (isDark) surface else surfaceContainerHigh

/** 점수 숫자·라벨 글자색 (테마에 맞춰 대비가 충분한 색). */
@Composable
@ReadOnlyComposable
fun scoreTextColor(score: Int): Color = Color(ScorePalette.textArgb(score, MaterialTheme.colorScheme.isDark))

@Composable
@ReadOnlyComposable
fun gradeAccentColor(grade: AppGrade): Color = GradePalette.accent(grade, MaterialTheme.colorScheme.isDark)

/** 점수 색을 글자·작은 아이콘에 쓸 때의 색 (테마별 대비 확보). 채우기에는 ScoreGreen 등을 그대로 씁니다. */
val ScoreGreenText: Color @Composable @ReadOnlyComposable get() = Color(ScorePalette.textArgb(100, MaterialTheme.colorScheme.isDark))
val ScoreYellowText: Color @Composable @ReadOnlyComposable get() = Color(ScorePalette.textArgb(70, MaterialTheme.colorScheme.isDark))
val ScoreOrangeText: Color @Composable @ReadOnlyComposable get() = Color(ScorePalette.textArgb(50, MaterialTheme.colorScheme.isDark))
val ScoreRedText: Color @Composable @ReadOnlyComposable get() = Color(ScorePalette.textArgb(0, MaterialTheme.colorScheme.isDark))
