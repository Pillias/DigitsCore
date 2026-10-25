package com.digitscore.app.engine

import com.digitscore.app.model.TimeRule
import java.time.LocalDateTime

/**
 * 시간대 규칙 평가 엔진 (순수 함수)
 *
 * 요일 매칭, 자정 넘김(심야 구간), 규칙 간 우선순위(중복 시 더 짧은 간격 우선)를 판정합니다.
 */
object TimeRuleEngine {

    /**
     * 특정 일시([dateTime])에 [rule]이 활성화되는지 여부를 반환합니다.
     *
     * 1. 당일 구간 (start < end):
     *    - [dateTime.dayOfWeek]이 rule.daysOfWeek에 포함되어 있고,
     *      시각이 [startMinuteOfDay, endMinuteOfDay) 사이인 경우.
     * 2. 자정 넘김 구간 (start >= end):
     *    - 저녁 시작 부분 (시각 >= startMinuteOfDay):
     *      당일 요일이 rule.daysOfWeek에 포함된 경우.
     *    - 익일 새벽 부분 (시각 < endMinuteOfDay):
     *      전날 요일(rule이 시작된 요일)이 rule.daysOfWeek에 포함된 경우.
     */
    fun matches(rule: TimeRule, dateTime: LocalDateTime): Boolean {
        if (!rule.enabled) return false
        if (rule.daysOfWeek.isEmpty()) return false

        val currentMinute = dateTime.hour * 60 + dateTime.minute
        val start = rule.startMinuteOfDay
        val end = rule.endMinuteOfDay

        return if (!rule.isOvernight) {
            dateTime.dayOfWeek in rule.daysOfWeek && currentMinute >= start && currentMinute < end
        } else {
            if (currentMinute >= start) {
                dateTime.dayOfWeek in rule.daysOfWeek
            } else if (currentMinute < end) {
                val previousDay = dateTime.dayOfWeek.minus(1)
                previousDay in rule.daysOfWeek
            } else {
                false
            }
        }
    }

    /**
     * 등록된 규칙 목록 중 현재 시각에 일치하는 활성 규칙을 반환합니다.
     * 여러 규칙이 동시에 일치할 경우 더 짧은 [TimeRule.lightingMinutes]를 가진 규칙이 우선합니다.
     */
    fun getActiveRule(rules: List<TimeRule>, dateTime: LocalDateTime = LocalDateTime.now()): TimeRule? {
        val matching = rules.filter { matches(it, dateTime) }
        return matching.minWithOrNull(
            compareBy<TimeRule> { it.lightingMinutes }
                .thenBy { it.id }
        )
    }
}
