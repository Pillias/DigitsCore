package com.digitscore.app.data

import android.content.Context
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 설치 첫날 또는 사용자 요청 시 기기에 남은 과거 최대 7일간의 사용 기록을 가져오는 임포터입니다.
 *
 * 코어 지수 체크포인트와 점수 기록은 일절 생성하지 않으며(점수는 기존 규칙에 따라 75점에서 시작),
 * 오직 앱별 일일 사용 기록(daily_app_usage)과 측정 완료 표식(daily_usage_coverage)만 채웁니다.
 * 이미 기록이 존재하는 날짜는 덮어쓰지 않습니다.
 */
object UsageHistoryImporter {

    private const val PREFS_NAME = "com.digitscore.app.HISTORY_IMPORTER"
    private const val KEY_AUTO_IMPORTED = "auto_imported"

    data class ImportResult(
        val importedDaysCount: Int,
        val startDateString: String?,
        val endDateString: String?,
        val totalDaysChecked: Int
    ) {
        fun message(isEn: Boolean): String {
            if (importedDaysCount == 0) {
                return if (isEn) "No past records found to import." else "불러올 수 있는 지난 기록이 없습니다."
            }
            return if (isEn) {
                "Imported $importedDaysCount days ($startDateString to $endDateString)."
            } else {
                "${startDateString}~${endDateString} 기록 ${importedDaysCount}일을 불러왔습니다."
            }
        }
    }

    suspend fun importPastHistory(
        context: Context,
        db: DigitsDatabase,
        zone: ZoneId = ZoneId.systemDefault(),
        maxDays: Int = 7
    ): ImportResult = withContext(Dispatchers.IO) {
        val today = LocalDate.now(zone).let {
            if (LocalTime.now(zone).hour < 5) it.minusDays(1) else it
        }

        val existingCoverages = db.dailyAppUsageDao().getAllCoverage().map { it.dateString }.toSet()
        val appWeights = db.appDao().getAllAppWeights().firstOrNull()?.associateBy { it.packageName } ?: emptyMap()

        val importedDates = mutableListOf<String>()

        for (dayOffset in 1..maxDays) {
            val date = today.minusDays(dayOffset.toLong())
            val dateString = date.toString()
            if (dateString in existingCoverages) {
                // 이미 기록이 있는 날은 덮어쓰지 않습니다.
                continue
            }

            val dayStart = date.atTime(5, 0).atZone(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atTime(5, 0).atZone(zone).toInstant().toEpochMilli()

            val snapshot = UsageStatsHelper.getUsageSnapshotForRange(
                context = context,
                appWeightMap = appWeights,
                startTime = dayStart,
                endTime = dayEnd
            )

            if (snapshot.appsUsage.isNotEmpty()) {
                val dailyRecords = snapshot.appsUsage.map { app ->
                    val sessions = snapshot.sessionSummariesByPackage[app.packageName]
                    DailyAppUsageEntity(
                        dateString = dateString,
                        packageName = app.packageName,
                        appName = app.appName,
                        usageMillis = app.usageTimeMillis,
                        sessionCount = sessions?.sessionCount ?: 0,
                        shortSessionCount = app.shortSessionCount,
                        longestSessionMillis = sessions?.longestSessionMillis ?: 0L,
                        lateNightUsageMillis = app.lateNightUsageMillis,
                        categoryLevel = app.categoryType.level,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                }

                db.dailyAppUsageDao().insertAll(dailyRecords)
                db.dailyAppUsageDao().insertCoverage(
                    DailyUsageCoverageEntity(
                        dateString = dateString,
                        isComplete = true,
                        lastUpdatedTimestamp = System.currentTimeMillis()
                    )
                )
                importedDates.add(dateString)
            }
        }

        importedDates.sort()
        val result = ImportResult(
            importedDaysCount = importedDates.size,
            startDateString = importedDates.firstOrNull(),
            endDateString = importedDates.lastOrNull(),
            totalDaysChecked = maxDays
        )

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_IMPORTED, true).apply()

        result
    }

    suspend fun maybeImportOnFirstRun(
        context: Context,
        db: DigitsDatabase,
        zone: ZoneId = ZoneId.systemDefault()
    ): ImportResult? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUTO_IMPORTED, false)) return null
        return importPastHistory(context, db, zone)
    }
}
