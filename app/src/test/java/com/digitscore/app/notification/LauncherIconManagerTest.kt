package com.digitscore.app.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherIconManagerTest {

    @Test
    fun `fromScore maps 4 core index tiers accurately`() {
        assertEquals(LauncherIconManager.IconTier.GREEN, LauncherIconManager.IconTier.fromScore(99))
        assertEquals(LauncherIconManager.IconTier.GREEN, LauncherIconManager.IconTier.fromScore(80))
        assertEquals(LauncherIconManager.IconTier.YELLOW, LauncherIconManager.IconTier.fromScore(79))
        assertEquals(LauncherIconManager.IconTier.YELLOW, LauncherIconManager.IconTier.fromScore(60))
        assertEquals(LauncherIconManager.IconTier.ORANGE, LauncherIconManager.IconTier.fromScore(59))
        assertEquals(LauncherIconManager.IconTier.ORANGE, LauncherIconManager.IconTier.fromScore(40))
        assertEquals(LauncherIconManager.IconTier.RED, LauncherIconManager.IconTier.fromScore(39))
        assertEquals(LauncherIconManager.IconTier.RED, LauncherIconManager.IconTier.fromScore(1))
    }
}
