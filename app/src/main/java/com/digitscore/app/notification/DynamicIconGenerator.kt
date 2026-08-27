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
        // 상태바 아이콘 크기 (48x48 px or density에 따른 96x96 px)
        val size = (32 * density).roundToInt().coerceAtLeast(64)
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

        // 1. 원형 배경 그리기
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = circleColor
            style = Paint.Style.FILL
        }
        val radius = size / 2f
        canvas.drawCircle(radius, radius, radius - (2 * density), bgPaint)

        // 2. 외곽 테두리 (가독성 향상)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawCircle(radius, radius, radius - (2 * density), strokePaint)

        // 3. 점수 텍스트 그리기
        val text = score.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            // 글자수에 따라 폰트 크기 조절 (100점은 3자리이므로 작게)
            textSize = when (text.length) {
                3 -> size * 0.42f
                2 -> size * 0.52f
                else -> size * 0.60f
            }
            textAlign = Paint.Align.CENTER
        }

        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        // 수직 가운데 정렬 계산
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
