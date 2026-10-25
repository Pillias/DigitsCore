package com.digitscore.app.engine

import com.digitscore.app.model.TimeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class TimeRuleEngineTest {

    @Test
    fun testDaytimeRule() {
        val workRule = TimeRule(
            id = "work",
            name = "업무",
            enabled = true,
            daysOfWeek = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            ),
            startHour = 9,
            startMinute = 0,
            endHour = 18,
            endMinute = 0,
            lightingMinutes = 10
        )

        assertFalse(workRule.isOvernight)

        // 월요일 10:00 -> 일치
        val mondayWork = LocalDateTime.of(2026, 10, 26, 10, 0) // 2026-10-26 is Monday
        assertEquals(DayOfWeek.MONDAY, mondayWork.dayOfWeek)
        assertTrue(TimeRuleEngine.matches(workRule, mondayWork))

        // 월요일 08:59 -> 불일치
        assertFalse(TimeRuleEngine.matches(workRule, LocalDateTime.of(2026, 10, 26, 8, 59)))

        // 월요일 18:00 -> 불일치 (종료 시각 경계는 미포함)
        assertFalse(TimeRuleEngine.matches(workRule, LocalDateTime.of(2026, 10, 26, 18, 0)))

        // 토요일 14:00 -> 주말이므로 불일치
        val saturday = LocalDateTime.of(2026, 10, 31, 14, 0)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertFalse(TimeRuleEngine.matches(workRule, saturday))
    }

    @Test
    fun testOvernightRuleEveryday() {
        val sleepPrepRule = TimeRule(
            id = "sleep",
            name = "수면 준비",
            enabled = true,
            daysOfWeek = DayOfWeek.values().toSet(),
            startHour = 22,
            startMinute = 30,
            endHour = 6,
            endMinute = 30,
            lightingMinutes = 5
        )

        assertTrue(sleepPrepRule.isOvernight)

        // 밤 23:00 -> 일치
        assertTrue(TimeRuleEngine.matches(sleepPrepRule, LocalDateTime.of(2026, 10, 26, 23, 0)))

        // 새벽 04:00 -> 일치
        assertTrue(TimeRuleEngine.matches(sleepPrepRule, LocalDateTime.of(2026, 10, 27, 4, 0)))

        // 아침 06:29 -> 일치
        assertTrue(TimeRuleEngine.matches(sleepPrepRule, LocalDateTime.of(2026, 10, 27, 6, 29)))

        // 아침 06:30 -> 종료 시각이므로 불일치
        assertFalse(TimeRuleEngine.matches(sleepPrepRule, LocalDateTime.of(2026, 10, 27, 6, 30)))

        // 오후 14:00 -> 불일치
        assertFalse(TimeRuleEngine.matches(sleepPrepRule, LocalDateTime.of(2026, 10, 27, 14, 0)))
    }

    @Test
    fun testOvernightRuleSpecificDays() {
        // 금요일 밤샘 규칙: 금요일 22:00 ~ 토요일 04:00
        val fridayNightRule = TimeRule(
            id = "friday_night",
            name = "불금",
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.FRIDAY),
            startHour = 22,
            startMinute = 0,
            endHour = 4,
            endMinute = 0,
            lightingMinutes = 5
        )

        assertTrue(fridayNightRule.isOvernight)

        // 2026-10-30은 금요일
        val fridayNight = LocalDateTime.of(2026, 10, 30, 23, 30)
        assertEquals(DayOfWeek.FRIDAY, fridayNight.dayOfWeek)
        assertTrue(TimeRuleEngine.matches(fridayNightRule, fridayNight))

        // 2026-10-31(토요일) 새벽 02:00 -> 금요일 밤에서 이어진 새벽이므로 일치!
        val saturdayMorning = LocalDateTime.of(2026, 10, 31, 2, 0)
        assertEquals(DayOfWeek.SATURDAY, saturdayMorning.dayOfWeek)
        assertTrue(TimeRuleEngine.matches(fridayNightRule, saturdayMorning))

        // 2026-10-31(토요일) 밤 23:00 -> 토요일 밤은 규칙 대상이 아니므로 불일치
        assertFalse(TimeRuleEngine.matches(fridayNightRule, LocalDateTime.of(2026, 10, 31, 23, 0)))

        // 2026-10-30(금요일) 새벽 02:00 -> 목요일 밤에서 이어진 새벽이므로 불일치
        assertFalse(TimeRuleEngine.matches(fridayNightRule, LocalDateTime.of(2026, 10, 30, 2, 0)))
    }

    @Test
    fun testDisabledRuleNeverMatches() {
        val disabledRule = TimeRule(
            id = "disabled",
            name = "비활성",
            enabled = false,
            daysOfWeek = DayOfWeek.values().toSet(),
            startHour = 0,
            startMinute = 0,
            endHour = 23,
            endMinute = 59,
            lightingMinutes = 5
        )
        assertFalse(TimeRuleEngine.matches(disabledRule, LocalDateTime.of(2026, 10, 26, 12, 0)))
    }

    @Test
    fun testOverlapPriorityShorterLightingMinutes() {
        val lenientRule = TimeRule(
            id = "lenient",
            name = "업무 일반",
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            startHour = 9,
            startMinute = 0,
            endHour = 18,
            endMinute = 0,
            lightingMinutes = 15
        )

        val strictRule = TimeRule(
            id = "strict",
            name = "집중 회의",
            enabled = true,
            daysOfWeek = setOf(DayOfWeek.MONDAY),
            startHour = 10,
            startMinute = 0,
            endHour = 12,
            endMinute = 0,
            lightingMinutes = 5
        )

        val monday11am = LocalDateTime.of(2026, 10, 26, 11, 0)
        val activeRule = TimeRuleEngine.getActiveRule(listOf(lenientRule, strictRule), monday11am)

        // 두 규칙 모두 일치하지만 5min인 strictRule이 우선됨
        assertEquals("strict", activeRule?.id)
        assertEquals(5, activeRule?.lightingMinutes)

        // 14:00에는 strictRule이 해당하지 않으므로 lenientRule이 선택됨
        val monday2pm = LocalDateTime.of(2026, 10, 26, 14, 0)
        val afternoonRule = TimeRuleEngine.getActiveRule(listOf(lenientRule, strictRule), monday2pm)
        assertEquals("lenient", afternoonRule?.id)
        assertEquals(15, afternoonRule?.lightingMinutes)

        // 밤 20:00에는 둘 다 해당하지 않음
        val monday8pm = LocalDateTime.of(2026, 10, 26, 20, 0)
        assertNull(TimeRuleEngine.getActiveRule(listOf(lenientRule, strictRule), monday8pm))
    }
}
