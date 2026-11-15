package com.digitscore.app.ui.statistics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TextButton
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.Text
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.ui.components.coreIndexTierColor
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.CoreIndexHistoryRepair
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.UnlockInsights
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.CoreIndexSampleEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.InformationDetailDialog
import com.digitscore.app.ui.components.ResponsiveContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

// 통계 화면에서 쓰는 계산 함수들입니다. (이전 통계 화면 UI는 StatisticsExplorer로 대체되어 제거했습니다.)

private const val CURRENT_SCORE_MODEL_VERSION = com.digitscore.app.engine.ScoreModel.CURRENT_STORED

internal fun currentModelWindowSamples(
    samples: List<CoreIndexSampleEntity>, startMillis: Long, endMillis: Long
): List<CoreIndexSampleEntity> = samples.filter {
    it.scoreModelVersion == CURRENT_SCORE_MODEL_VERSION && it.timestampMillis in startMillis..endMillis
}.sortedBy { it.timestampMillis }

internal fun currentModelScoreHistories(
    histories: List<DailyScoreHistoryEntity>
): List<DailyScoreHistoryEntity> = histories.filter {
    it.scoreModelVersion == CURRENT_SCORE_MODEL_VERSION
}

internal data class RollingUsageSummary(
    val totalMillis: Long,
    val managedMillis: Long,
    val hourlyTotalMillis: List<Long>,
    val hourlyManagedMillis: List<Long>,
    val longestSessionMillis: Long,
    val longestSessionAppName: String?,
    val topAppMillis: Long,
    val topAppName: String?
)

private data class PrimarySessionKey(
    val packageName: String,
    val sessionStartTimeMillis: Long
)

internal fun summarizeRollingUsage(
    sessions: List<ForegroundUsageSessionEntity>,
    windowStartMillis: Long,
    windowEndMillis: Long
): RollingUsageSummary {
    val duration = (windowEndMillis - windowStartMillis).coerceAtLeast(1L)
    val bucketMillis = (duration / 24).coerceAtLeast(1L)
    val hourlyTotal = LongArray(24)
    val hourlyManaged = LongArray(24)
    val packageTotals = mutableMapOf<String, Long>()
    val appNames = mutableMapOf<String, String>()
    val primarySessionDurations = mutableMapOf<PrimarySessionKey, Long>()

    sessions.sortedBy { it.startTimeMillis }.forEach { session ->
        val start = maxOf(session.startTimeMillis, windowStartMillis)
        val end = minOf(session.endTimeMillis, windowEndMillis)
        if (end <= start) return@forEach

        val clippedDuration = end - start
        packageTotals[session.packageName] =
            (packageTotals[session.packageName] ?: 0L) + clippedDuration
        appNames[session.packageName] = session.appName
        val sessionKey = PrimarySessionKey(session.packageName, session.sessionStartTimeMillis)
        primarySessionDurations[sessionKey] =
            (primarySessionDurations[sessionKey] ?: 0L) + clippedDuration

        var cursor = start
        while (cursor < end) {
            val bucket = ((cursor - windowStartMillis) / bucketMillis)
                .toInt()
                .coerceIn(0, 23)
            val bucketEnd = minOf(
                end,
                windowStartMillis + (bucket + 1L) * bucketMillis
            )
            val interval = (bucketEnd - cursor).coerceAtLeast(0L)
            hourlyTotal[bucket] += interval
            if (session.effectiveCategoryLevel >= 3) hourlyManaged[bucket] += interval
            cursor = bucketEnd
        }
    }

    val topPackage = packageTotals.maxByOrNull { it.value }
    val longestSession = primarySessionDurations.maxByOrNull { it.value }
    return RollingUsageSummary(
        totalMillis = hourlyTotal.sum(),
        managedMillis = hourlyManaged.sum(),
        hourlyTotalMillis = hourlyTotal.toList(),
        hourlyManagedMillis = hourlyManaged.toList(),
        longestSessionMillis = longestSession?.value ?: 0L,
        longestSessionAppName = longestSession?.key?.packageName?.let(appNames::get),
        topAppMillis = topPackage?.value ?: 0L,
        topAppName = topPackage?.key?.let(appNames::get)
    )
}

private const val CORE_INDEX_SCORE_MODEL_VERSION = 2

internal fun coreIndexHistories(
    histories: List<DailyScoreHistoryEntity>
): List<DailyScoreHistoryEntity> = histories.filter {
    it.scoreModelVersion >= CORE_INDEX_SCORE_MODEL_VERSION
}

internal data class CoreIndexPresetChange(
    val dateString: String,
    val presetId: String
)

internal fun coreIndexPresetChanges(
    histories: List<DailyScoreHistoryEntity>
): List<CoreIndexPresetChange> = histories.zipWithNext().mapNotNull { (previous, current) ->
    if (previous.coreIndexPresetId == current.coreIndexPresetId) null else {
        CoreIndexPresetChange(current.dateString, current.coreIndexPresetId)
    }
}

internal fun historiesInCalendarRange(
    histories: List<DailyScoreHistoryEntity>,
    days: Int,
    today: LocalDate = LocalDate.now()
): List<DailyScoreHistoryEntity> {
    val safeDays = days.coerceAtLeast(1)
    val firstDate = today.minusDays((safeDays - 1).toLong())
    return histories.asSequence()
        .mapNotNull { history ->
            val date = runCatching { LocalDate.parse(history.dateString) }.getOrNull()
                ?: return@mapNotNull null
            if (date.isBefore(firstDate) || date.isAfter(today)) null else date to history
        }
        .sortedBy { it.first }
        .map { it.second }
        .toList()
}

internal fun calendarDayOffset(
    dateString: String,
    days: Int,
    today: LocalDate = LocalDate.now()
): Int {
    val safeDays = days.coerceAtLeast(2)
    val firstDate = today.minusDays((safeDays - 1).toLong())
    val date = runCatching { LocalDate.parse(dateString) }.getOrDefault(firstDate)
    return java.time.temporal.ChronoUnit.DAYS.between(firstDate, date)
        .toInt()
        .coerceIn(0, safeDays - 1)
}

internal data class DailyCoreRange(
    val history: DailyScoreHistoryEntity,
    val startScore: Int,
    val lastScore: Int,
    val low: Int,
    val high: Int,
    val hasIntradaySamples: Boolean
)

internal fun buildDailyCoreRanges(
    histories: List<DailyScoreHistoryEntity>,
    samples: List<CoreIndexSampleEntity>
): List<DailyCoreRange> {
    val samplesByDate = samples.groupBy { it.dateString }
    return histories.sortedBy { it.dateString }.map { history ->
        val daySamples = samplesByDate[history.dateString].orEmpty()
            .filter { it.scoreModelVersion == history.scoreModelVersion }
            .sortedBy { it.timestampMillis }
        if (daySamples.isEmpty()) {
            DailyCoreRange(
                history = history,
                startScore = history.finalScore,
                lastScore = history.finalScore,
                low = history.finalScore,
                high = history.finalScore,
                hasIntradaySamples = false
            )
        } else {
            DailyCoreRange(
                history = history,
                startScore = daySamples.first().score,
                lastScore = daySamples.last().score,
                low = daySamples.minOf { it.score },
                high = daySamples.maxOf { it.score },
                hasIntradaySamples = true
            )
        }
    }
}

internal fun sevenDayMovingAverages(ranges: List<DailyCoreRange>): List<Float?> = ranges.map { current ->
    val currentDate = runCatching { LocalDate.parse(current.history.dateString) }.getOrNull()
        ?: return@map null
    val startDate = currentDate.minusDays(6)
    ranges.mapNotNull { candidate ->
        val date = runCatching { LocalDate.parse(candidate.history.dateString) }.getOrNull()
            ?: return@mapNotNull null
        candidate.lastScore.takeIf { !date.isBefore(startDate) && !date.isAfter(currentDate) &&
            candidate.history.scoreModelVersion == current.history.scoreModelVersion &&
            candidate.history.coreIndexPresetId == current.history.coreIndexPresetId }
    }.takeIf { it.isNotEmpty() }?.average()?.toFloat()
}
