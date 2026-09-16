package com.digitscore.app.data

import com.digitscore.app.engine.RollingScoreCalculator
import com.digitscore.app.engine.RollingUsageSession
import com.digitscore.app.model.CoreIndexPreset
import java.time.LocalDate
import java.time.ZoneId

/**
 * v1.3 이전의 유효한 일별 행을 최근 30일 상세 세션으로 한 번만 코어 지수화합니다.
 * 세션 증거가 없는 날짜는 임의의 값으로 채우지 않습니다.
 */
object CoreIndexHistoryRepair {
    private const val CALIBRATED_USAGE_MILLIS = 60 * 60_000L

    suspend fun repairLegacyRows(database: DigitsDatabase): Int {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        var repairedCount = 0

        database.scoreDao().getLegacyScoreHistories().forEach { history ->
            val date = runCatching { LocalDate.parse(history.dateString) }.getOrNull()
                ?: return@forEach
            if (!date.isBefore(today)) return@forEach
            if (date.isBefore(today.minusDays(30))) return@forEach

            val dayEndMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            val windowStartMillis = dayEndMillis - 24 * 60 * 60_000L
            val sessions = database.foregroundUsageSessionDao()
                .getBetween(windowStartMillis, dayEndMillis)
                .map { session ->
                    RollingUsageSession(
                        packageName = session.packageName,
                        startTimeMillis = session.startTimeMillis,
                        endTimeMillis = session.endTimeMillis,
                        categoryLevel = session.effectiveCategoryLevel,
                        effectivePackageName = session.effectivePackageName,
                        sessionStartTimeMillis = session.sessionStartTimeMillis,
                        isLateNight = session.isLateNight
                    )
                }
            if (sessions.isEmpty()) return@forEach

            val repaired = RollingScoreCalculator.calculate(
                sessions = sessions,
                nowMillis = dayEndMillis,
                rollingUnlockCount = history.unlockCount,
                calibrationUsageMillis = CALIBRATED_USAGE_MILLIS,
                preset = CoreIndexPreset.BALANCED
            )
            database.scoreDao().insertOrUpdateScoreHistory(
                history.copy(
                    finalScore = repaired.finalScore,
                    scoreModelVersion = 3,
                    coreIndexPresetId = CoreIndexPreset.BALANCED.id,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
            repairedCount++
        }
        return repairedCount
    }
}
