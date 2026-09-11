package com.digitscore.app.data.entity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsEntityTest {

    @Test
    fun `tracking is opt in for a new installation`() {
        assertFalse(UserSettingsEntity().isTrackingEnabled)
        assertEquals(365, UserSettingsEntity().appHistoryRetentionDays)
        assertTrue(UserSettingsEntity().hideSensitiveNotificationOnLockScreen)
        assertEquals("big_number", UserSettingsEntity().statusIconStyleId)
        assertEquals("dark", UserSettingsEntity().widgetBackgroundStyleId)
    }
}
