package com.digitscore.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 화면에서 직접 쓰는 글자 크기 (docs/DESIGN_SYSTEM.md "글자" 절).
 * 테마 스타일(MaterialTheme.typography)로 표현하기 어려운 곳만 이 값을 씁니다.
 * 화면 코드에 sp 숫자를 직접 쓰지 않습니다(scripts/design_check.sh가 셉니다).
 */
object DigitsTextSize {
    /** 작은 차트 안 숫자 (대시보드 24시간 차트) */
    val chart = 8.5.sp
    /** 차트 축·날짜 */
    val micro = 9.sp
    /** 작은 배지·보조 단위 */
    val tiny = 10.sp
    /** 보조 설명·범례 */
    val caption = 11.sp
    /** 라벨·작은 본문 */
    val label = 12.sp
    /** 기본 본문(카드 안) */
    val body = 13.sp
    /** 강조 본문 */
    val bodyLarge = 14.sp
    /** 행 제목 */
    val emphasis = 15.sp
    /** 카드 제목 */
    val title = 16.sp
    /** 큰 카드 제목·요약 수치 */
    val titleLarge = 18.sp
    /** 화면 안 큰 수치 */
    val headline = 20.sp
    val headlineLarge = 22.sp
    /** 점수 링 */
    val display = 26.sp
    val displayLarge = 28.sp
    val hero = 32.sp
    val heroLarge = 34.sp
}

/**
 * 모서리 크기. 화면 코드에 `RoundedCornerShape(12.dp)`처럼 숫자를 직접 쓰지 않습니다.
 */
object DigitsShape {
    /** 얇은 막대·선 */
    val bar = RoundedCornerShape(2.dp)
    /** 차트 막대·작은 표시 */
    val xs = RoundedCornerShape(4.dp)
    /** 칩·작은 칸 */
    val sm = RoundedCornerShape(8.dp)
    /** 카드 안 칸·버튼 */
    val md = RoundedCornerShape(12.dp)
    /** 카드 */
    val lg = RoundedCornerShape(16.dp)
    /** 큰 시트 */
    val xl = RoundedCornerShape(24.dp)
    /** 다이얼로그 */
    val dialog = RoundedCornerShape(28.dp)
}
