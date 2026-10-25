package com.digitscore.app.model

import java.time.DayOfWeek

/**
 * 시간대 규칙 모델
 *
 * 업무, 수면 준비 등 특정 시간대에 화면 신호등 발동 간격을 일시적으로 강화하거나 조정합니다.
 * 코어 지수 점수 계산에는 일절 영향을 주지 않습니다.
 */
data class TimeRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = PRESET_WORK,
    val enabled: Boolean = true,
    val daysOfWeek: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 18,
    val endMinute: Int = 0,
    val lightingMinutes: Int = 10 // 5, 10, 15, 20
) {
    val startMinuteOfDay: Int get() = startHour * 60 + startMinute
    val endMinuteOfDay: Int get() = endHour * 60 + endMinute
    val isOvernight: Boolean get() = endMinuteOfDay <= startMinuteOfDay

    fun displayName(isEn: Boolean): String = when (name) {
        PRESET_WORK, "업무", "Work" -> if (isEn) "Work" else "업무"
        PRESET_SLEEP, "수면 준비", "Sleep prep" -> if (isEn) "Sleep prep" else "수면 준비"
        else -> name
    }

    companion object {
        const val PRESET_WORK = "preset_work"
        const val PRESET_SLEEP = "preset_sleep"
    }
}
