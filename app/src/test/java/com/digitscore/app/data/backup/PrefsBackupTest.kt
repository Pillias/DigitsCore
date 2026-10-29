package com.digitscore.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrefsBackupTest {
    private val goalSpec = PrefsBackup.SPECS.first { it.prefsName == "daily_goal_state" }
    private val choiceSpec = PrefsBackup.SPECS.first { it.prefsName == "goal_limit_choices_prefs" }

    @Test
    fun `typed values round trip`() {
        val values = mapOf(
            "goal_separate_weekday_weekend" to true,
            "goal_weekday_score_target" to 75,
            "goal_weekend_app_limit" to 90L,
            "goal_day_records_json" to "[{\"date\":\"2026-10-28\"}]",
            "personal_best_json" to "{}"
        )
        val restored = PrefsBackup.decode(PrefsBackup.encode(values, goalSpec), goalSpec)
        assertEquals(values, restored)
        assertEquals(Long::class.javaObjectType, restored["goal_weekend_app_limit"]!!::class.javaObjectType)
    }

    @Test
    fun `transient and unrelated keys are excluded`() {
        val values = mapOf(
            "limit_choice_records_json" to "[]",
            "snooze_until_com.example" to 123L,
            "resend_count_com.example" to 2
        )
        val encoded = PrefsBackup.encode(values, choiceSpec)
        assertEquals(setOf("limit_choice_records_json"), encoded.keys().asSequence().toSet())

        val goalEncoded = PrefsBackup.encode(mapOf("goal_date" to "2026-10-29", "y_score" to 70), goalSpec)
        assertFalse(goalEncoded.keys().hasNext())
    }
}
