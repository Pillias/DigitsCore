package com.digitscore.app.data

import com.digitscore.app.data.entity.CoreIndexSampleEntity

/**
 * "쉬는 동안 +nP": 화면을 끄고 쉰 뒤 다시 켰을 때 그동안 오른 지수.
 * 5분 표본은 화면이 켜진 동안만 쌓이므로, 표본 사이 10분 이상 빈 곳을 화면 꺼짐으로 봅니다.
 */
object RestGainNotice {
    const val MIN_REST_MILLIS = 10 * 60_000L
    const val MIN_GAIN = 2.0
    /** 화면을 다시 켠 지 이 시간 안에만 보여 줍니다. */
    const val SHOW_WITHIN_MILLIS = 15 * 60_000L

    data class Gain(val restStartMillis: Long, val restEndMillis: Long, val points: Int)

    fun latest(samples: List<CoreIndexSampleEntity>, currentExactScore: Double, nowMillis: Long): Gain? {
        val sorted = samples.filter { it.timestampMillis <= nowMillis }.sortedBy { it.timestampMillis }
        if (sorted.isEmpty()) return null
        // 마지막 표본 이후 지금까지 비어 있으면 지금 막 켠 것, 아니면 마지막 빈 곳을 찾습니다.
        val points = sorted.map { it.timestampMillis to it.exactScore } + (nowMillis to currentExactScore)
        for (i in points.lastIndex downTo 1) {
            val (endAt, _) = points[i]
            val (startAt, startScore) = points[i - 1]
            if (nowMillis - endAt > SHOW_WITHIN_MILLIS) return null
            if (endAt - startAt >= MIN_REST_MILLIS) {
                val gain = currentExactScore - startScore
                return if (gain >= MIN_GAIN) Gain(startAt, endAt, kotlin.math.floor(gain).toInt()) else null
            }
        }
        return null
    }
}
