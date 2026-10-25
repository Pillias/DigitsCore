package com.digitscore.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalLimitChoiceStoreTest {

    @Test
    fun testSerializationAndDeserialization() {
        val records = listOf(
            GoalLimitChoiceRecord("2026-10-25", "com.google.android.youtube", GoalLimitChoiceStore.CHOICE_MORE_15, 1729850000000L),
            GoalLimitChoiceRecord("2026-10-25", "com.google.android.youtube", GoalLimitChoiceStore.CHOICE_STOP_TODAY, 1729851000000L)
        )

        val json = GoalLimitChoiceStore.serializeRecords(records)
        assertTrue(json.contains("com.google.android.youtube"))
        assertTrue(json.contains("MORE_15"))
        assertTrue(json.contains("STOP_TODAY"))

        val parsed = GoalLimitChoiceStore.parseRecords(json)
        assertEquals(2, parsed.size)
        assertEquals("2026-10-25", parsed[0].dateString)
        assertEquals(GoalLimitChoiceStore.CHOICE_MORE_15, parsed[0].choice)
        assertEquals("2026-10-25", parsed[1].dateString)
        assertEquals(GoalLimitChoiceStore.CHOICE_STOP_TODAY, parsed[1].choice)
    }

    @Test
    fun testRangeFiltering() {
        val records = listOf(
            GoalLimitChoiceRecord("2026-10-18", "com.pkg1", GoalLimitChoiceStore.CHOICE_MORE_15, 1000L),
            GoalLimitChoiceRecord("2026-10-19", "com.pkg1", GoalLimitChoiceStore.CHOICE_MORE_15, 2000L),
            GoalLimitChoiceRecord("2026-10-20", "com.pkg1", GoalLimitChoiceStore.CHOICE_STOP_TODAY, 3000L),
            GoalLimitChoiceRecord("2026-10-25", "com.pkg2", GoalLimitChoiceStore.CHOICE_MORE_15, 4000L),
            GoalLimitChoiceRecord("2026-10-26", "com.pkg2", GoalLimitChoiceStore.CHOICE_STOP_TODAY, 5000L)
        )

        val start = LocalDate.parse("2026-10-19")
        val end = LocalDate.parse("2026-10-25")

        val inRange = records.filter { it.dateString in start.toString()..end.toString() }
        val more15 = inRange.count { it.choice == GoalLimitChoiceStore.CHOICE_MORE_15 }
        val stopToday = inRange.count { it.choice == GoalLimitChoiceStore.CHOICE_STOP_TODAY }

        assertEquals(2, more15) // 10-19, 10-25
        assertEquals(1, stopToday) // 10-20
    }
}
