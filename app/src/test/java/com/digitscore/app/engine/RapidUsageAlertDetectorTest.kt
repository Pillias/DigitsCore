package com.digitscore.app.engine

import com.digitscore.app.model.RapidUsageAlertConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RapidUsageAlertDetectorTest {
    private val config = RapidUsageAlertConfig(
        windowMinutes = 30,
        scoreDrop = 5,
        usageMinutes = 24,
        continuousMinutes = 35,
        cooldownMinutes = 90
    )

    @Test
    fun `score drop is the primary alert condition`() {
        val alert = RapidUsageAlertDetector.evaluate(
            observation = RapidUsageObservation(
                currentScore = 73,
                baselineScore = 80,
                windowUsageMinutes = 25,
                continuousUsageMinutes = 40
            ),
            config = config
        )

        assertEquals(RapidUsageAlertReason.SCORE_DROP, alert?.reason)
        assertEquals(7, alert?.scoreDrop)
    }

    @Test
    fun `usage condition works without an earlier score sample`() {
        val alert = RapidUsageAlertDetector.evaluate(
            observation = RapidUsageObservation(
                currentScore = 80,
                baselineScore = null,
                windowUsageMinutes = 24,
                continuousUsageMinutes = 10
            ),
            config = config
        )

        assertEquals(RapidUsageAlertReason.HIGH_USAGE, alert?.reason)
    }

    @Test
    fun `below all configured thresholds does not alert`() {
        val alert = RapidUsageAlertDetector.evaluate(
            observation = RapidUsageObservation(
                currentScore = 78,
                baselineScore = 80,
                windowUsageMinutes = 20,
                continuousUsageMinutes = 20
            ),
            config = config
        )

        assertNull(alert)
    }
}
