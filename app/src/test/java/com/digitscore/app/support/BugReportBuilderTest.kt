package com.digitscore.app.support

import com.digitscore.app.data.MeasurementDiagnostics
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BugReportBuilderTest {
    @Test
    fun reportContainsActionableDiagnosticsButNoUsageHistory() {
        val report = BugReportBuilder.build(
            BugReportEnvironment(
                versionName = "1.7.0",
                versionCode = 301,
                manufacturer = "Example",
                model = "Phone",
                androidVersion = "16",
                sdkInt = 36,
                languageCode = "ko",
                trackingEnabled = true,
                notificationEnabled = true,
                usageAccessGranted = true,
                databaseMode = "encrypted",
                diagnostics = MeasurementDiagnostics(
                    isIncremental = true,
                    queriedEventCount = 12,
                    foregroundCoveragePercent = 98,
                    rolling24HourUnlockCount = 24
                ),
                generatedAtMillis = 1_700_000_000_000L
            ),
            "Widget stopped updating"
        )

        assertTrue(report.contains("Widget stopped updating"))
        assertTrue(report.contains("Foreground coverage: 98%"))
        assertTrue(report.contains("Recorded unlocks today/rolling 24h: 0 / 24"))
        assertFalse(report.contains("YouTube"))
        assertFalse(report.contains("com.example.private"))
    }
}
