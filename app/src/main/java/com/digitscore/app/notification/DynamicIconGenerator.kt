package com.digitscore.app.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/**
 * 상태바 smallIcon에 실시간 점수 숫자를 렌더링하는 동적 비트맵 생성기
 */
object DynamicIconGenerator {

    /**
     * 점수(0~100)를 입력받아 Android 상태바 규격(24dp~48dp 비트맵)에 최적화된 숫자 비트맵 아이콘을 생성합니다.
     */
    fun createScoreBitmapIcon(context: Context, score: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        // 실제 표시는 OS가 상태바 슬롯에 맞게 축소하므로 큰 원본으로 렌더링해 획을 선명하게 유지합니다.
        val size = (48 * density).roundToInt().coerceAtLeast(96)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 점수대별 배경 색상 결정 (초록 -> 노랑 -> 주황 -> 빨강)
        val colorHex = when {
            score >= 80 -> "#2ECC71" // Green (우수)
            score >= 60 -> "#F1C40F" // Yellow (양호)
            score >= 40 -> "#E67E22" // Orange (주의)
            else -> "#E74C3C"        // Red (위험)
        }
        val circleColor = Color.parseColor(colorHex)

        // 1. 원형 배경 그리기 (여백을 최소화하여 폰트 렌더링 영역 극대화)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = circleColor
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius, bgPaint)

        // 2. 점수 텍스트 그리기 (상태바에서 가장 크고 선명하게 보이도록 동적 최대화)
        val text = score.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // 원형 내에서 글자가 잘리지 않는 최대 허용 너비 및 높이
        val maxAllowedWidth = size * if (text.length == 3) 0.97f else 0.92f
        val maxAllowedHeight = size * 0.90f

        // 글자수 기준 기본 초대형 폰트 크기 지정
        var targetTextSize = size * when (text.length) {
            1 -> 0.94f
            2 -> 0.88f
            else -> 0.68f
        }
        textPaint.textSize = targetTextSize

        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val measuredWidth = textPaint.measureText(text)
        val measuredHeight = bounds.height().toFloat()

        // 허용 범위 초과 시 정밀 비례 축소
        if (measuredWidth > maxAllowedWidth) {
            targetTextSize *= (maxAllowedWidth / measuredWidth)
        }
        if (measuredHeight > maxAllowedHeight) {
            targetTextSize *= (maxAllowedHeight / measuredHeight)
        }
        textPaint.textSize = targetTextSize

        // 최종 텍스트 수직/수평 정밀 가운데 정렬
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val yPos = (size / 2f) - bounds.exactCenterY()

        canvas.drawText(text, size / 2f, yPos, textPaint)

        return bitmap
    }

    /**
     * IconCompat 형태로 변환
     */
    fun createScoreIconCompat(context: Context, score: Int): IconCompat {
        val bitmap = createScoreBitmapIcon(context, score)
        return IconCompat.createWithBitmap(bitmap)
    }
}
