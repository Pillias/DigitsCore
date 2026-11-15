package com.digitscore.app.data

import com.digitscore.app.data.entity.ScoreImpactHourEntity

/**
 * 하루 그래프 아래 원인 표시: 가장 크게 내린 시간 최대 2곳(원인 앱)과 가장 크게 오른 시간 1곳(화면 꺼짐).
 * 합쳐서 최대 3개만 돌려줍니다.
 */
object DayMovementHighlights {
    const val MIN_POINTS = 1.0

    data class Highlight(val hour: Long, val points: Double, val packageName: String?) {
        val isRise: Boolean get() = points > 0
    }

    fun from(impacts: List<ScoreImpactHourEntity>): List<Highlight> {
        val totals = impacts.filter { it.packageName.isEmpty() }
        val appsByHour = impacts
            .filter { it.packageName.isNotEmpty() && it.packageName != StatisticsStore.UNLOCK_IMPACT_KEY }
            .groupBy { it.hour }
        val net = totals.groupBy { it.hour }.mapValues { (_, rows) -> rows.sumOf { it.recovery - it.loss } }
        val drops = net.filterValues { it <= -MIN_POINTS }.toList().sortedBy { it.second }.take(2).map { (hour, points) ->
            Highlight(hour, points, appsByHour[hour]?.maxByOrNull { it.loss }?.packageName)
        }
        val rise = net.filterValues { it >= MIN_POINTS }.maxByOrNull { it.value }?.let { Highlight(it.key, it.value, null) }
        return (drops + listOfNotNull(rise)).sortedBy { it.hour }
    }
}
