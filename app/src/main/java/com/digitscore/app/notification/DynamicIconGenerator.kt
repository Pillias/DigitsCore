package com.digitscore.app.notification

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.IconCompat
import kotlin.math.roundToInt

/**
 * 상태바 알림 아이콘 및 위젯용 고해상도 동적 아이콘을 생성합니다.
 * 24dp 상태바 극소형 캔버스에서도 선명하게 보이도록 여백을 최소화(크기 140% 극대화)하고,
 * 볼드 스트로크와 외곽 고대비 컨투어 림을 적용하여 밝은/어두운 배경 모두에 대응합니다.
 */
object DynamicIconGenerator {
    // 점수 색은 ScorePalette(ui/theme/Color.kt) 한 곳에서 정합니다.
    private val RED = String.format("#%06X", com.digitscore.app.ui.theme.ScorePalette.FILL[3].toInt() and 0xFFFFFF)
    private val GREEN = String.format("#%06X", com.digitscore.app.ui.theme.ScorePalette.FILL[0].toInt() and 0xFFFFFF)

    fun createScoreBitmapIcon(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        // OS 축소 후에도 선명하도록 충분히 큰 고해상도(최소 128px) 원본으로 그립니다.
        val size = (64 * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tierColor = scoreTierColor(normalizedScore)

        when (style) {
            StatusIconStyle.BIG_NUMBER -> {
                // 옵션 2: 볼드 전원 링 + 센터 대형 고대비 숫자
                drawBigNumberFrame(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size / 2f,
                    centerY = size * 0.62f,
                    maxWidth = size * 0.68f,
                    scaleForDigits = floatArrayOf(0.48f, 0.44f, 0.36f)
                )
            }
            StatusIconStyle.NUMBER_FOCUS -> {
                // 숫자 분리형: 좌측 미니 볼드 전원 심볼 + 우측 대형 고대비 숫자
                drawSeparatedPowerSymbol(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size * 0.67f,
                    centerY = size * 0.52f,
                    maxWidth = size * 0.56f,
                    scaleForDigits = floatArrayOf(0.56f, 0.48f, 0.38f)
                )
            }
            StatusIconStyle.SCORE_TIER -> {
                // 전원 단계형: 볼드 풀사이즈 전원 심볼 전체에 점수 티어 단일 색상 적용 (숫자 겹침 제거, 크기 극대화)
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = false)
            }
            StatusIconStyle.SCORE_PROPORTION -> {
                // 옵션 1 (기본 권장): 볼드 풀사이즈 전원 심볼 (빨간 베이스 + 녹색 점수 아크 + 티어 스템)
                // 여백을 최소화하여 24dp 상태바 캔버스를 140% 꽉 채우고, 외곽 컨투어로 밝은/어두운 배경 동시 대응
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = true)
            }
        }

        return bitmap
    }

    /**
     * 옵션 1 (기본 권장) / 전원 단계형:
     * 캔버스 꽉 찬 볼드 풀사이즈 전원 심볼을 렌더링합니다.
     * 여백 0% 최소화로 24dp 상태바에서 압도적인 크기를 가지며,
     * 외곽 반투명 섀도우 컨투어 림으로 흰색/검은색 배경 모두에서 선명합니다.
     */
    private fun drawFullPowerSymbol(
        canvas: Canvas,
        size: Int,
        score: Int,
        tierColor: Int,
        isProportionMode: Boolean
    ) {
        val cx = size / 2f
        val cy = size * 0.51f
        val strokeWidth = size * 0.14f
        val r = size * 0.38f
        val ringBounds = RectF(cx - r, cy - r, cx + r, cy + r)

        val arcStart = -50f
        val fullSweep = 280f
        val stemTop = size * 0.10f
        val stemBottom = cy - r * 0.05f

        // 1. 외곽 고대비 반투명 섀도우 컨투어 (라이트/다크 배경 동시 대비 확보)
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth * 1.25f
            color = Color.argb(90, 0, 0, 0)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, contourPaint)
        canvas.drawLine(cx, stemTop, cx, stemBottom, contourPaint)

        // 2. 원호 렌더링
        if (isProportionMode) {
            // 빨간 바탕 원호
            val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = Color.parseColor(RED)
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep, false, redPaint)

            // 점수 비율 녹색 호
            if (score > 0) {
                val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    this.strokeWidth = strokeWidth
                    color = Color.parseColor(GREEN)
                }
                canvas.drawArc(ringBounds, arcStart, fullSweep * (score / 100f), false, greenPaint)
            }
        } else {
            // 티어 단일 색상 원호
            val tierPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = tierColor
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep, false, tierPaint)
        }

        // 3. 상단 스템 (티어 색상)
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = tierColor
        }
        canvas.drawLine(cx, stemTop, cx, stemBottom, stemPaint)
    }

    /**
     * 옵션 2: 볼드 전원 링 프레임
     * 상단 스템을 컴팩트하게 축소하여 원 내부 공간을 확보하고,
     * 센터 숫자가 스템과 겹치지 않도록 구성합니다.
     */
    private fun drawBigNumberFrame(
        canvas: Canvas,
        size: Int,
        score: Int,
        tierColor: Int
    ) {
        val cx = size / 2f
        val cy = size * 0.52f
        val strokeWidth = size * 0.105f
        val r = size * 0.36f
        val ringBounds = RectF(cx - r, cy - r, cx + r, cy + r)

        val arcStart = -50f
        val fullSweep = 280f
        val stemTop = size * 0.06f
        val stemBottom = size * 0.22f

        // 고대비 컨투어 림
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth * 1.35f
            color = Color.argb(80, 0, 0, 0)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, contourPaint)
        canvas.drawLine(cx, stemTop, cx, stemBottom, contourPaint)

        // 빨간 바탕 원호
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = Color.parseColor(RED)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, redPaint)

        // 녹색 점수 호
        if (score > 0) {
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = Color.parseColor(GREEN)
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep * (score / 100f), false, greenPaint)
        }

        // 컴팩트 스템
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = tierColor
        }
        canvas.drawLine(cx, stemTop, cx, stemBottom, stemPaint)
    }

    /**
     * 숫자 분리형: 좌측 미니 전원 심볼
     */
    private fun drawSeparatedPowerSymbol(
        canvas: Canvas,
        size: Int,
        score: Int,
        tierColor: Int
    ) {
        val cx = size * 0.23f
        val cy = size * 0.50f
        val strokeWidth = size * 0.085f
        val r = size * 0.19f
        val ringBounds = RectF(cx - r, cy - r, cx + r, cy + r)

        val arcStart = -50f
        val fullSweep = 280f
        val stemTop = cy - r * 1.15f
        val stemBottom = cy - r * 0.05f

        // 고대비 컨투어
        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth * 1.35f
            color = Color.argb(80, 0, 0, 0)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, contourPaint)
        canvas.drawLine(cx, stemTop, cx, stemBottom, contourPaint)

        // 빨간 바탕
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = Color.parseColor(RED)
        }
        canvas.drawArc(ringBounds, arcStart, fullSweep, false, redPaint)

        // 녹색 점수 호
        if (score > 0) {
            val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                this.strokeWidth = strokeWidth
                color = Color.parseColor(GREEN)
            }
            canvas.drawArc(ringBounds, arcStart, fullSweep * (score / 100f), false, greenPaint)
        }

        // 스템
        val stemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.strokeWidth = strokeWidth
            color = tierColor
        }
        canvas.drawLine(cx, stemTop, cx, stemBottom, stemPaint)
    }

    /**
     * 밝고 어두운 상태바 배경 모두에서 숫자가 선명하도록 굵은 다크 외곽선과 화이트 채움을 함께 그립니다.
     */
    private fun drawScoreText(
        canvas: Canvas,
        size: Int,
        score: Int,
        centerX: Float,
        centerY: Float,
        maxWidth: Float,
        scaleForDigits: FloatArray,
        @ColorInt solidColor: Int? = null
    ) {
        val text = score.toString()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = size * scaleForDigits[(text.length - 1).coerceIn(0, 2)]
        }
        val measuredWidth = textPaint.measureText(text)
        if (measuredWidth > maxWidth) {
            textPaint.textSize *= maxWidth / measuredWidth
        }
        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val baseline = centerY - bounds.exactCenterY()

        if (solidColor != null) {
            textPaint.style = Paint.Style.FILL
            textPaint.color = solidColor
            canvas.drawText(text, centerX, baseline, textPaint)
            return
        }

        // 굵은 다크 아웃라인 (흰 배경 및 밝은 상태바에서도 또렷한 경계선 형성)
        textPaint.style = Paint.Style.STROKE
        textPaint.strokeWidth = size * 0.08f
        textPaint.strokeJoin = Paint.Join.ROUND
        textPaint.color = Color.argb(220, 15, 15, 15)
        canvas.drawText(text, centerX, baseline, textPaint)

        // 화이트 채움
        textPaint.style = Paint.Style.FILL
        textPaint.color = Color.WHITE
        canvas.drawText(text, centerX, baseline, textPaint)
    }

    @ColorInt
    internal fun scoreTierColor(score: Int): Int =
        com.digitscore.app.ui.theme.ScorePalette.fillArgb(score)

    @ColorInt
    internal fun statusIconScoreColor(context: Context, score: Int): Int =
        statusIconScoreColor(score, context.resources.configuration.uiMode)

    @ColorInt
    internal fun statusIconScoreColor(score: Int, uiMode: Int): Int {
        val darkMode = uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return com.digitscore.app.ui.theme.ScorePalette.textArgb(score, onDark = darkMode)
    }

    fun createScoreIconCompat(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): IconCompat = IconCompat.createWithBitmap(createScoreBitmapIcon(context, score, style))

    /**
     * 위젯 전용: 숫자 텍스트가 겹치지 않는 순수 전원 버튼(Power Symbol) 게이지 비트맵을 생성합니다.
     * 상태바의 풀사이즈 전원 심볼(옵션 1)과 100% 동일한 비례와 색상을 공유합니다.
     */
    fun createPurePowerGaugeBitmap(
        context: Context,
        score: Int,
        sizeDp: Int = 64
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        val size = (sizeDp * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val tierColor = scoreTierColor(normalizedScore)

        drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = true)
        return bitmap
    }

    /**
     * 알림창 대형 아이콘(Large Icon)을 생성합니다.
     * Samsung OneUI 및 Android 12+의 알림창 좌측 원형 슬롯에 표시되며,
     * 고유 브랜드 다크 원형 배경(#101820) 위에 실시간 점수(%) 게이지 또는 숫자를 렌더링합니다.
     */
    fun createScoreLargeIcon(
        context: Context,
        score: Int,
        style: StatusIconStyle = StatusIconStyle.SCORE_PROPORTION
    ): Bitmap {
        val normalizedScore = score.coerceIn(0, 100)
        val density = context.resources.displayMetrics.density
        val size = (64 * density).roundToInt().coerceAtLeast(128)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 브랜드 다크 원형 배경 (#101820)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.FILL
            color = Color.parseColor("#101820")
        }
        val cx = size / 2f
        val cy = size / 2f
        canvas.drawCircle(cx, cy, size / 2f, bgPaint)

        val tierColor = scoreTierColor(normalizedScore)
        when (style) {
            StatusIconStyle.BIG_NUMBER -> {
                drawBigNumberFrame(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size / 2f,
                    centerY = size * 0.62f,
                    maxWidth = size * 0.68f,
                    scaleForDigits = floatArrayOf(0.48f, 0.44f, 0.36f)
                )
            }
            StatusIconStyle.NUMBER_FOCUS -> {
                drawSeparatedPowerSymbol(canvas, size, normalizedScore, tierColor)
                drawScoreText(
                    canvas = canvas,
                    size = size,
                    score = normalizedScore,
                    centerX = size * 0.67f,
                    centerY = size * 0.52f,
                    maxWidth = size * 0.56f,
                    scaleForDigits = floatArrayOf(0.56f, 0.48f, 0.38f)
                )
            }
            StatusIconStyle.SCORE_TIER -> {
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = false)
            }
            StatusIconStyle.SCORE_PROPORTION -> {
                drawFullPowerSymbol(canvas, size, normalizedScore, tierColor, isProportionMode = true)
            }
        }

        return bitmap
    }

    /**
     * 위젯용 고해상도 '24시간 미니 코어 지수 추세선(Sparkline)' 비트맵을 생성합니다.
     * 75P 기준 대시선, 부드러운 스플라인 곡선 및 그라디언트 채우기, 종단 강조점, 최고/최저치 라벨을 포함합니다.
     */
    fun createWidgetSparklineBitmap(
        scores: List<Float>,
        currentScore: Int,
        isDarkSurface: Boolean,
        widthPx: Int = 320,
        heightPx: Int = 110,
        showMinMaxLabels: Boolean = true
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx.coerceAtLeast(64), heightPx.coerceAtLeast(32), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val effectiveScores = when {
            scores.isEmpty() -> listOf(currentScore.toFloat(), currentScore.toFloat())
            scores.size == 1 -> listOf(scores[0], currentScore.toFloat())
            else -> scores
        }

        val padX = 14f
        val padTop = if (showMinMaxLabels) 18f else 12f
        val padBottom = if (showMinMaxLabels) 16f else 10f
        val chartWidth = (widthPx - padX * 2).coerceAtLeast(1f)
        val chartHeight = (heightPx - padTop - padBottom).coerceAtLeast(1f)

        val minScoreVal = effectiveScores.minOrNull() ?: 75f
        val maxScoreVal = effectiveScores.maxOrNull() ?: 75f
        val minY = (minOf(minScoreVal, 70f) - 4f).coerceAtLeast(1f)
        val maxY = (maxOf(maxScoreVal, 80f) + 4f).coerceAtMost(99f)
        val rangeY = (maxY - minY).coerceAtLeast(1f)

        fun getX(i: Int): Float = padX + i.toFloat() / (effectiveScores.size - 1).coerceAtLeast(1) * chartWidth
        fun getY(score: Float): Float = padTop + (maxY - score) / rangeY * chartHeight

        // 75P 기준선(Baseline)
        val y75 = getY(75f)
        if (y75 in padTop..(padTop + chartHeight)) {
            val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.5f
                pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
                color = if (isDarkSurface) Color.WHITE else Color.BLACK
                alpha = if (isDarkSurface) 50 else 60
            }
            canvas.drawLine(padX, y75, widthPx - padX, y75, dashPaint)
        }

        // 곡선 및 영역 채우기 Path
        val linePath = Path()
        val fillPath = Path()

        val firstX = getX(0)
        val firstY = getY(effectiveScores[0])
        linePath.moveTo(firstX, firstY)
        fillPath.moveTo(firstX, heightPx.toFloat())
        fillPath.lineTo(firstX, firstY)

        for (i in 1 until effectiveScores.size) {
            val prevX = getX(i - 1)
            val prevY = getY(effectiveScores[i - 1])
            val curX = getX(i)
            val curY = getY(effectiveScores[i])
            val midX = (prevX + curX) / 2f
            linePath.cubicTo(midX, prevY, midX, curY, curX, curY)
            fillPath.cubicTo(midX, prevY, midX, curY, curX, curY)
        }

        val lastX = getX(effectiveScores.size - 1)
        fillPath.lineTo(lastX, heightPx.toFloat())
        fillPath.close()

        val tierColor = scoreTierColor(currentScore)

        // 면적 그라디언트 (Fill)
        val startColor = Color.argb(55, Color.red(tierColor), Color.green(tierColor), Color.blue(tierColor))
        val endColor = Color.argb(0, Color.red(tierColor), Color.green(tierColor), Color.blue(tierColor))
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            shader = LinearGradient(0f, padTop, 0f, heightPx.toFloat(), startColor, endColor, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(fillPath, fillPaint)

        // 곡선 선분 (Stroke)
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4.5f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = tierColor
        }
        canvas.drawPath(linePath, strokePaint)

        // 종단점(Current Point Dot)
        val endX = getX(effectiveScores.size - 1)
        val endY = getY(effectiveScores.last())
        val outerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = tierColor
        }
        canvas.drawCircle(endX, endY, 5.5f, outerDotPaint)

        val innerDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (isDarkSurface) Color.WHITE else Color.parseColor("#1C1B1F")
        }
        canvas.drawCircle(endX, endY, 2.5f, innerDotPaint)

        // 미니 라벨(Min/Max)
        if (showMinMaxLabels && effectiveScores.size >= 2) {
            val maxIdx = effectiveScores.indices.maxByOrNull { effectiveScores[it] } ?: 0
            val minIdx = effectiveScores.indices.minByOrNull { effectiveScores[it] } ?: 0
            val maxScoreInt = effectiveScores[maxIdx].roundToInt()
            val minScoreInt = effectiveScores[minIdx].roundToInt()

            if (maxScoreInt != minScoreInt) {
                val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 10.5f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    color = if (isDarkSurface) Color.argb(160, 255, 255, 255) else Color.argb(160, 60, 60, 60)
                    textAlign = Paint.Align.CENTER
                }
                val maxX = getX(maxIdx).coerceIn(padX + 12f, widthPx - padX - 12f)
                val maxYPos = (getY(effectiveScores[maxIdx]) - 5f).coerceAtLeast(11f)
                canvas.drawText("$maxScoreInt", maxX, maxYPos, labelPaint)

                val minX = getX(minIdx).coerceIn(padX + 12f, widthPx - padX - 12f)
                val minYPos = (getY(effectiveScores[minIdx]) + 13f).coerceAtMost(heightPx - 3f)
                canvas.drawText("$minScoreInt", minX, minYPos, labelPaint)
            }
        }

        return bitmap
    }
}
