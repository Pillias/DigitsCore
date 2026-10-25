package com.digitscore.app.model

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 오늘의 목표 설정 슬라이더의 범위와 추천값.
 * 고정 범위(40~80P, 15~180min) 대신 지금 값과 최근 기록 주변만 보여 줘서
 * 의미 없는 값(지금보다 한참 낮은 점수, 이미 넘긴 사용 시간)을 고르지 않게 합니다.
 */
data class GoalSliderSpec(
    val min: Int,
    val max: Int,
    val step: Int,
    /** 처음 열었을 때 고를 값 */
    val suggested: Int,
    /** 슬라이더 아래 표시할 기준점: 오늘 값 */
    val today: Int?,
    /** 슬라이더 아래 표시할 기준점: 최근 기록 평균 (기록 없으면 null) */
    val recentAverage: Int?
) {
    /** Compose Slider의 steps (양 끝 사이의 칸 수) */
    val sliderSteps: Int get() = ((max - min) / step - 1).coerceAtLeast(0)

    fun snap(value: Float): Int =
        ((((value - min) / step).roundToInt()) * step + min).coerceIn(min, max)

    /** 지금 목표값이 범위 안이면 그대로, 밖이면 추천값을 씁니다. */
    fun initial(current: Int): Int = if (current in min..max) snap(current.toFloat()) else suggested
}

object GoalRanges {
    private fun roundUp(value: Double, step: Int): Int = (ceil(value / step) * step).toInt()
    private fun round(value: Double, step: Int): Int = ((value / step).roundToInt() * step)

    /**
     * 지수 방어선: 1P 단위. 지금 점수와 최근 마감 점수 평균에서 아래로 15P, 위로 10P까지.
     * 추천값은 최근 평균(없으면 지금 점수 - 5P)과 지금 점수 중 낮은 쪽입니다.
     */
    fun score(currentScore: Int, recentFinalScores: List<Int>): GoalSliderSpec {
        val avg = recentFinalScores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
        val low = minOf(currentScore, avg ?: currentScore)
        val high = maxOf(currentScore, avg ?: currentScore)
        val min = (low - 15).coerceIn(40, 85)
        val max = (high + 10).coerceIn(min + 10, 95)
        val suggested = minOf(currentScore, avg ?: (currentScore - 5)).coerceIn(min, max)
        return GoalSliderSpec(min, max, 1, suggested, currentScore, avg)
    }

    /**
     * 관리할 앱 한도: 5min 단위. 오늘 이미 쓴 시간보다 낮게는 고를 수 없고,
     * 위로는 최근 평균·오늘 사용 중 큰 값의 1.5배(최소 +30min)까지.
     * 추천값은 최근 평균의 80%, 기록이 없으면 오늘 사용 + 30min입니다.
     */
    fun appLimit(todayMinutes: Int, recentDailyMinutes: List<Int>): GoalSliderSpec {
        val avg = recentDailyMinutes.takeIf { it.isNotEmpty() }?.average()
        val min = maxOf(5, roundUp(todayMinutes.toDouble(), 5))
        val max = maxOf(min + 30, roundUp(maxOf(avg ?: 0.0, todayMinutes.toDouble()) * 1.5, 5)).coerceAtMost(720)
        val suggested = (if (avg != null) round(avg * 0.8, 5) else min + 30).coerceIn(min, max)
        return GoalSliderSpec(min, max, 5, suggested, todayMinutes, avg?.roundToInt())
    }

    /**
     * 잠금 해제 한도: 5회 단위. 오늘 이미 푼 횟수보다 낮게는 고를 수 없고,
     * 위로는 최근 평균·오늘 횟수 중 큰 값의 1.5배(최소 +30회)까지.
     * 추천값은 최근 평균의 90%, 기록이 없으면 오늘 + 20회입니다.
     */
    fun unlockLimit(todayUnlocks: Int, recentDailyUnlocks: List<Int>): GoalSliderSpec {
        val avg = recentDailyUnlocks.takeIf { it.isNotEmpty() }?.average()
        val min = maxOf(10, roundUp(todayUnlocks.toDouble(), 5))
        val max = maxOf(min + 30, roundUp(maxOf(avg ?: 0.0, todayUnlocks.toDouble()) * 1.5, 5)).coerceAtMost(500)
        val suggested = (if (avg != null) round(avg * 0.9, 5) else min + 20).coerceIn(min, max)
        return GoalSliderSpec(min, max, 5, suggested, todayUnlocks, avg?.roundToInt())
    }
}
