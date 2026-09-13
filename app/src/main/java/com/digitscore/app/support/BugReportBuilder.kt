package com.digitscore.app.support

import com.digitscore.app.data.MeasurementDiagnostics
import java.time.Instant

/**
 * 사용자가 공유하기 전에 직접 확인할 수 있는 최소 진단 보고서입니다.
 * 앱 이름·패키지·사용 시각·점수 기록·식별자는 의도적으로 받지 않습니다.
 */
data class BugReportEnvironment(
    val versionName: String,
    val versionCode: Int,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val languageCode: String,
    val trackingEnabled: Boolean,
    val notificationEnabled: Boolean,
    val usageAccessGranted: Boolean,
    val databaseMode: String,
    val diagnostics: MeasurementDiagnostics,
    val generatedAtMillis: Long
)

object BugReportBuilder {
    fun build(
        environment: BugReportEnvironment,
        description: String
    ): String {
        val d = environment.diagnostics
        val coverage = d.foregroundCoveragePercent?.let { "$it%" } ?: "pending"
        val collectionMode = if (d.isIncremental) "incremental" else "state-restore"
        val userDescription = description.trim().ifBlank { "(not provided)" }

        return buildString {
            appendLine("DigitsCore bug report")
            appendLine("Generated: ${Instant.ofEpochMilli(environment.generatedAtMillis)}")
            appendLine()
            appendLine("What happened / reproduction steps:")
            appendLine(userDescription)
            appendLine()
            appendLine("App and device")
            appendLine("- App: ${environment.versionName} (${environment.versionCode})")
            appendLine("- Device: ${environment.manufacturer} ${environment.model}")
            appendLine("- Android: ${environment.androidVersion} (SDK ${environment.sdkInt})")
            appendLine("- App language: ${environment.languageCode}")
            appendLine("- Tracking: ${environment.trackingEnabled.asOnOff()}")
            appendLine("- Status notification: ${environment.notificationEnabled.asOnOff()}")
            appendLine("- Usage access: ${environment.usageAccessGranted.asGrantedDenied()}")
            appendLine("- Local database: ${environment.databaseMode}")
            appendLine()
            appendLine("Measurement diagnostics")
            appendLine("- Collection: $collectionMode")
            appendLine("- Foreground coverage: $coverage")
            appendLine("- Last query window: ${d.lastQueryWindowMillis} ms")
            appendLine("- Last query events/time: ${d.queriedEventCount} / ${d.lastQueryDurationMillis} ms")
            appendLine("- Last cycle CPU: ${d.lastCycleCpuMillis} ms")
            appendLine("- Today's cycles/query/CPU: ${d.cyclesToday} / ${d.totalQueryDurationTodayMillis} ms / ${d.totalCpuTodayMillis} ms")
            appendLine("- Recorded unlocks today/rolling 24h: ${d.todayUnlockCount} / ${d.rolling24HourUnlockCount}")
            appendLine("- Diagnostics updated: ${d.updatedAtMillis.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it) } ?: "pending"}")
            appendLine()
            append("Privacy: This report does not include app names, package names, usage history, score history, notification content, account data, or device identifiers.")
        }
    }

    private fun Boolean.asOnOff() = if (this) "on" else "off"
    private fun Boolean.asGrantedDenied() = if (this) "granted" else "denied"
}
