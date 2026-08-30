package com.digitscore.app.data.entity

import org.junit.Assert.assertFalse
import org.junit.Test

class UserSettingsEntityTest {

    @Test
    fun `tracking is opt in for a new installation`() {
        assertFalse(UserSettingsEntity().isTrackingEnabled)
    }
}
