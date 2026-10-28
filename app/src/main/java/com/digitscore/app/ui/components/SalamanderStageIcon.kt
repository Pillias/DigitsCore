package com.digitscore.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digitscore.app.ui.theme.ScoreGreen

/**
 * 연속 기록 단계별 도룡뇽 (1~4). 색은 같고 모양으로 구분합니다.
 * 1 새끼(몸통·꼬리·아가미) → 2 다리가 남 → 3 몸이 길어지고 등 무늬 → 4 다 자람(등 지느러미·말린 꼬리).
 */
@Composable
fun SalamanderStageIcon(stage: Int, modifier: Modifier = Modifier, size: Dp = 32.dp, color: Color = ScoreGreen) {
    val s = stage.coerceIn(1, 4)
    Canvas(modifier = modifier.size(size)) { drawSalamander(s, color) }
}

private fun DrawScope.drawSalamander(stage: Int, color: Color) {
    val w = size.width
    val h = size.height
    val dark = Color(
        red = color.red * 0.62f, green = color.green * 0.62f, blue = color.blue * 0.62f, alpha = 1f
    )
    val bodyLen = when (stage) { 1 -> 0.30f; 2 -> 0.36f; 3 -> 0.44f; else -> 0.48f } * w
    val bodyH = when (stage) { 1 -> 0.20f; 2 -> 0.21f; 3 -> 0.20f; else -> 0.22f } * h
    val cy = h * 0.56f
    val headR = h * (if (stage == 1) 0.15f else 0.14f)
    val headCx = w * 0.80f - (if (stage == 1) w * 0.08f else 0f)
    val bodyRight = headCx - headR * 0.5f
    val bodyLeft = bodyRight - bodyLen

    // 꼬리
    val tail = Path().apply {
        moveTo(bodyLeft + bodyH * 0.4f, cy - bodyH * 0.25f)
        if (stage == 4) {
            cubicTo(bodyLeft - w * 0.10f, cy - h * 0.05f, bodyLeft - w * 0.12f, cy + h * 0.26f, bodyLeft + w * 0.02f, cy + h * 0.22f)
            cubicTo(bodyLeft - w * 0.04f, cy + h * 0.16f, bodyLeft - w * 0.02f, cy + h * 0.05f, bodyLeft + bodyH * 0.4f, cy + bodyH * 0.25f)
        } else {
            val tipX = (bodyLeft - w * (0.10f + 0.03f * stage)).coerceAtLeast(w * 0.03f)
            quadraticBezierTo(tipX + w * 0.04f, cy - h * 0.04f, tipX, cy + h * 0.02f)
            quadraticBezierTo(tipX + w * 0.05f, cy + h * 0.05f, bodyLeft + bodyH * 0.4f, cy + bodyH * 0.25f)
        }
        close()
    }
    drawPath(tail, color)

    // 등 지느러미 (4단계)
    if (stage == 4) {
        val crest = Path().apply {
            moveTo(bodyLeft + bodyLen * 0.05f, cy - bodyH * 0.45f)
            val n = 4
            for (i in 0 until n) {
                val x0 = bodyLeft + bodyLen * (0.05f + 0.85f * i / n)
                val x1 = bodyLeft + bodyLen * (0.05f + 0.85f * (i + 0.5f) / n)
                val x2 = bodyLeft + bodyLen * (0.05f + 0.85f * (i + 1f) / n)
                lineTo(x1, cy - bodyH * 0.45f - h * 0.08f)
                lineTo(x2, cy - bodyH * 0.45f)
                if (x0 < 0f) break
            }
            close()
        }
        drawPath(crest, dark)
    }

    // 다리 (2단계부터)
    if (stage >= 2) {
        val legW = h * 0.055f
        val legL = h * (if (stage >= 3) 0.13f else 0.10f)
        listOf(bodyRight - bodyLen * 0.32f to 1f, bodyLeft + bodyLen * 0.20f to -1f).forEach { (x, dir) ->
            drawLine(dark, Offset(x, cy - bodyH * 0.3f), Offset(x + dir * legL * 0.4f, cy - bodyH * 0.3f - legL), legW, StrokeCap.Round)
            drawLine(dark, Offset(x, cy + bodyH * 0.3f), Offset(x + dir * legL * 0.4f, cy + bodyH * 0.3f + legL), legW, StrokeCap.Round)
        }
    }

    // 몸통
    drawOval(color, Offset(bodyLeft, cy - bodyH / 2f), Size(bodyLen + headR * 0.6f, bodyH))

    // 등 무늬 (3단계부터)
    if (stage >= 3) {
        val spotR = h * 0.025f
        val count = if (stage == 4) 4 else 3
        for (i in 0 until count) {
            drawCircle(dark, spotR, Offset(bodyLeft + bodyLen * (0.22f + 0.6f * i / count), cy - bodyH * 0.05f))
        }
    }

    // 머리
    drawCircle(color, headR, Offset(headCx, cy))

    // 아가미 (모든 단계, 새끼일수록 크게)
    val gillL = h * (if (stage == 1) 0.12f else 0.09f)
    val gillW = h * 0.03f
    for (side in listOf(-1f, 1f)) {
        for (k in 0..2) {
            val ang = Math.toRadians((100 + k * 28).toDouble())
            val sx = headCx - headR * 0.15f
            val sy = cy + side * headR * 0.8f
            drawLine(
                dark,
                Offset(sx, sy),
                Offset(sx + (gillL * Math.cos(ang)).toFloat(), sy + side * (gillL * Math.sin(ang)).toFloat()),
                gillW,
                StrokeCap.Round
            )
        }
    }

    // 눈
    val eyeR = headR * 0.16f
    drawCircle(Color.Black.copy(alpha = 0.85f), eyeR, Offset(headCx + headR * 0.35f, cy - headR * 0.42f))
    drawCircle(Color.Black.copy(alpha = 0.85f), eyeR, Offset(headCx + headR * 0.35f, cy + headR * 0.42f))

    // 외곽선(작은 크기에서 형태가 뭉개지지 않게)
    drawCircle(dark.copy(alpha = 0.35f), headR, Offset(headCx, cy), style = Stroke(h * 0.012f))
}
