package com.digitscore.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeModeTest {
    @Test
    fun unknownOrMissingModeUsesSystemDefault() {
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId(null))
        assertEquals(AppThemeMode.SYSTEM, AppThemeMode.fromId("unknown"))
    }

    @Test
    fun explicitModesOverrideSystemWhileSystemModeFollowsIt() {
        assertTrue(AppThemeMode.SYSTEM.usesDarkColors(systemIsDark = true))
        assertFalse(AppThemeMode.SYSTEM.usesDarkColors(systemIsDark = false))
        assertFalse(AppThemeMode.LIGHT.usesDarkColors(systemIsDark = true))
        assertTrue(AppThemeMode.DARK.usesDarkColors(systemIsDark = false))
    }
}
