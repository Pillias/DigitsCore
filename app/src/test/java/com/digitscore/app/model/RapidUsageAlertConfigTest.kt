package com.digitscore.app.model

import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.entity.effectiveRapidUsageAlertConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class RapidUsageAlertConfigTest {
    @Test
    fun `every preset starts with a thirty minute observation window`() {
        CoreIndexPreset.entries.forEach { preset ->
            assertEquals(30, preset.defaultRapidUsageAlertConfig.windowMinutes)
        }
        assertNotEquals(
            CoreIndexPreset.BALANCED.defaultRapidUsageAlertConfig,
            CoreIndexPreset.FAMILY.defaultRapidUsageAlertConfig
        )
    }

    @Test
    fun `custom settings override preset defaults and are bounded`() {
        val settings = UserSettingsEntity(
            usePresetRapidAlertDefaults = false,
            rapidAlertWindowMinutes = 20,
            rapidAlertScoreDrop = 1,
            rapidAlertUsageMinutes = 50,
            rapidAlertContinuousMinutes = 200,
            rapidAlertCooldownMinutes = 5
        )

        assertEquals(
            RapidUsageAlertConfig(
                windowMinutes = 20,
                scoreDrop = 2,
                usageMinutes = 20,
                continuousMinutes = 90,
                cooldownMinutes = 30
            ),
            settings.effectiveRapidUsageAlertConfig(CoreIndexPreset.BALANCED)
        )
    }
}
