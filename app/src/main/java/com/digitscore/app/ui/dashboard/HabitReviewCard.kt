package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.*
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.service.TrackerForegroundService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun HabitReviewCard(
    score: Int,
    apps: List<AppUsage>,
    dailyGoal: com.digitscore.app.model.DailyGoal? = null,
    yesterdaySummary: com.digitscore.app.model.YesterdayBriefingSummary? = null
) {
    val context = LocalContext.current
    val db = remember { DigitsDatabase.getInstance(context) }
    val scope = rememberCoroutineScope()
    val english = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    var suggestion by remember { mutableStateOf<AppUsage?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    LaunchedEffect(score, apps.map { it.packageName }, revision) {
        val record = db.cumulativeScoreStateDao().get()?.let { CumulativeRecord.decode(it.payload) }
            ?: return@LaunchedEffect
        val zone = ZoneId.systemDefault()
        val since = LocalDate.now(zone).minusDays(6).toString()
        suggestion = null
        for (app in apps.filter { it.categoryType.canonical == AppCategoryType.NEUTRAL }) {
            if ((record.suggestionSnoozes[app.packageName] ?: 0) > System.currentTimeMillis()) continue
            val days = db.dailyAppUsageDao().getForPackageSince(app.packageName, since)
            if (days.count { it.usageMillis >= 90 * 60_000L } >= 3) {
                suggestion = app
                break
            }
        }
    }

    suggestion?.let { app ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = if (english) "Manage ${app.appName}?" else "${app.appName}을 관리 앱으로 바꿀까요?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
                Text(
                    text = if (english) "Used 90+ min on 3+ days. Applies to future scoring."
                        else "최근 7일 중 3일 이상 90min 넘게 사용했습니다. 변경 후 사용부터 반영합니다.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { scope.launch {
                            val old = db.appDao().getAppWeight(app.packageName)
                            db.appDao().insertOrUpdateAppWeight((old ?: AppWeightEntity(app.packageName, app.appName,
                                AppCategoryType.NEUTRAL)).copy(categoryType = AppCategoryType.DISTRACTING, isUserModified = true))
                            CumulativeScoreStore.snoozeSuggestion(db, app.packageName, Long.MAX_VALUE)
                            revision++; TrackerForegroundService.refreshNotification(context)
                        } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (english) "Manage" else "관리로 변경", fontSize = 11.5.sp) }
                    OutlinedButton(
                        onClick = { scope.launch {
                            val old = db.appDao().getAppWeight(app.packageName)
                            db.appDao().insertOrUpdateAppWeight((old ?: AppWeightEntity(app.packageName, app.appName,
                                AppCategoryType.NEUTRAL)).copy(categoryType = AppCategoryType.NEUTRAL, isUserModified = true))
                            CumulativeScoreStore.snoozeSuggestion(db, app.packageName, Long.MAX_VALUE); revision++
                            TrackerForegroundService.refreshNotification(context)
                        } },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (english) "Keep" else "일반 유지", fontSize = 11.5.sp) }
                    TextButton(
                        onClick = { scope.launch {
                            CumulativeScoreStore.snoozeSuggestion(db, app.packageName, System.currentTimeMillis() + 7 * 86_400_000L); revision++
                        } },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
                    ) { Text(if (english) "Later" else "나중에", fontSize = 11.5.sp) }
                }
            }
        }
    }
}
