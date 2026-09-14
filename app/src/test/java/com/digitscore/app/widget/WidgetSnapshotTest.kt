package com.digitscore.app.widget

import com.digitscore.app.engine.ScoreFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetSnapshotTest {
    @Test
    fun snapshotSanitizesValuesBeforePersistence() {
        val sanitized = WidgetSnapshot(
            score = 120,
            screenMinutes = -5,
            managedMinutes = -1,
            unlockCount = -3,
            flow = ScoreFlow.USING,
            updatedAtMillis = -1
        ).sanitized()

        assertEquals(100, sanitized.score)
        assertEquals(0L, sanitized.screenMinutes)
        assertEquals(0L, sanitized.managedMinutes)
        assertEquals(0, sanitized.unlockCount)
        assertEquals(0L, sanitized.updatedAtMillis)
    }

    @Test
    fun displayComparisonIgnoresTimestampButIncludesEveryVisibleValue() {
        val first = WidgetSnapshot(79, 61, 20, 14, ScoreFlow.USING, 100)
        assertTrue(first.copy(updatedAtMillis = 200).hasSameDisplayedValues(first))
        assertFalse(first.copy(screenMinutes = 62).hasSameDisplayedValues(first))
        assertFalse(first.copy(unlockCount = 15).hasSameDisplayedValues(first))
        assertFalse(first.copy(flow = ScoreFlow.RECOVERING).hasSameDisplayedValues(first))
    }
}
