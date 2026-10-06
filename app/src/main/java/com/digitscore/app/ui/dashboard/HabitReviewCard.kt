package com.digitscore.app.ui.dashboard

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.service.TrackerForegroundService
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

@Composable
fun WakeEvidenceSettings() {
    val context = LocalContext.current
    val english = Locale.getDefault().language == "en"
    val prefs = remember { context.getSharedPreferences("habit_sensor_settings", Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean("steps_enabled", false)) }
    val available = remember { (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
        .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null }
    val threshold = remember(enabled) { com.digitscore.app.service.WakeStepAdaptiveManager.getThreshold(context) }
    val samples = remember(enabled) { com.digitscore.app.service.WakeStepAdaptiveManager.getSampleCount(context) }
    fun save(value: Boolean) {
        enabled = value
        prefs.edit().putBoolean("steps_enabled", value).apply()
        TrackerForegroundService.refreshNotification(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { save(it) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (english) "Wake detection · Adaptive steps" else "기상 판단 · 적응형 걸음 보조",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Switch(enabled = available, checked = enabled, onCheckedChange = { value ->
                    if (value && Build.VERSION.SDK_INT >= 29) permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    else save(value)
                })
            }
            Text(
                text = if (!available) {
                    if (english) "No step counter available. Usage-pattern detection still works." else "걸음 센서가 없습니다. 사용 패턴 기준 판단은 계속 동작합니다."
                } else if (english) {
                    "Waking is detected when reaching $threshold steps in 15 minutes (auto-calibrated to 2/3 of your pattern; $samples day(s) recorded). No location or raw step history is stored."
                } else {
                    "아침 15분 내 ${threshold}걸음 감지 시 기상 보조 근거로 사용합니다. (3일 이상 측정 시 평소 기상 걸음의 2/3으로 자동 최적화, 현재 ${samples}일 학습됨) 위치나 이동 궤적은 일절 저장하지 않습니다."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                lineHeight = 16.sp
            )
        }
    }
}

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
    val english = Locale.getDefault().language == "en"
    var morning by remember { mutableStateOf(false) }
    var suggestion by remember { mutableStateOf<AppUsage?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val prefs = remember { context.getSharedPreferences("habit_sensor_settings", Context.MODE_PRIVATE) }
    var steps by remember { mutableStateOf(prefs.getBoolean("steps_enabled", false)) }
    val hasStepSensor = remember {
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager)
            .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }
    fun setSteps(enabled: Boolean) {
        prefs.edit().putBoolean("steps_enabled", enabled).apply()
        steps = enabled
        TrackerForegroundService.refreshNotification(context)
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { setSteps(it) }

    LaunchedEffect(score, apps.map { it.packageName }, revision) {
        val record = db.cumulativeScoreStateDao().get()?.let { CumulativeRecord.decode(it.payload) }
            ?: return@LaunchedEffect
        val zone = ZoneId.systemDefault()
        val now = java.time.ZonedDateTime.now(zone)
        val handledDate = Instant.ofEpochMilli(record.briefingHandledAt).atZone(zone).toLocalDate()
        // 오전 5시~11시59분 사이, 아직 확인하지 않았거나 오늘의 목표 카드가 dismiss되지 않은 경우 (새벽 0~4시 심야 미표시)
        morning = (now.hour in 5..11 && handledDate != now.toLocalDate()) ||
                (dailyGoal != null && !dailyGoal.isDismissed && dailyGoal.isAutoAssigned && now.hour in 5..11)

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

    if (morning && dailyGoal != null && !dailyGoal.isDismissed) {
        val summary = yesterdaySummary ?: com.digitscore.app.model.YesterdayBriefingSummary()
        val yHours = summary.totalScreenTimeMinutes / 60
        val yMins = summary.totalScreenTimeMinutes % 60
        val yTimeStr = if (yHours > 0) "${yHours}h ${yMins}m" else "${yMins}m"

        val topHours = summary.topAppUsageMinutes / 60
        val topMins = summary.topAppUsageMinutes % 60
        val topTimeStr = if (topHours > 0) "${topHours}h ${topMins}m" else "${topMins}m"

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 상단 라벨
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (english) "MORNING BRIEFING" else "모닝 브리핑",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (english) "Yesterday Overview" else "어제 분석",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                // 어제 핵심 통계 3칸 그리드 (숫자 강조)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 어제 점수
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (english) "SCORE" else "점수",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${summary.score}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    summary.score >= 80 -> com.digitscore.app.ui.theme.ScoreGreen
                                    summary.score >= 60 -> com.digitscore.app.ui.theme.ScoreYellow
                                    else -> com.digitscore.app.ui.theme.ScoreRed
                                }
                            )
                        }
                    }

                    // 2. 어제 화면 시간
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (english) "SCREEN" else "화면 시간",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = yTimeStr,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 3. 어제 최대 사용 앱
                    Surface(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (summary.topAppName.isNotBlank()) summary.topAppName else (if (english) "MAX LOAD" else "최대 부하"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (summary.topAppUsageMinutes > 0) topTimeStr else "-",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.digitscore.app.ui.theme.ScoreOrange
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 오늘의 3대 복합 목표 (미니 게이지 & 숫자)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (english) "TODAY'S 3-IN-1 GOALS" else "오늘의 3대 맞춤 목표",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        if (dailyGoal.isAutoAssigned) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = if (english) "AUTOPILOT" else "자동 배정",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 1. 코어 지수 방어선
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (english) "1. Core Index Defense" else "1. 코어 지수 방어선",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${dailyGoal.currentScore}점 / 목표 ${dailyGoal.scoreTarget}점",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dailyGoal.isScoreDefenseAchieved) com.digitscore.app.ui.theme.ScoreGreen
                                else com.digitscore.app.ui.theme.ScoreRed
                            )
                        }
                        LinearProgressIndicator(
                            progress = { (dailyGoal.currentScore.toFloat() / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = if (dailyGoal.isScoreDefenseAchieved) com.digitscore.app.ui.theme.ScoreGreen
                            else com.digitscore.app.ui.theme.ScoreRed,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    // 2. 특정 앱 사용량 제한 (선택된 경우)
                    if (dailyGoal.targetPackageName != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "2. ${dailyGoal.targetAppName} 제한",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "${dailyGoal.currentAppUsageMinutes}분 / 목표 ${dailyGoal.appLimitMinutes}분",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (dailyGoal.currentAppUsageMinutes > dailyGoal.appLimitMinutes) com.digitscore.app.ui.theme.ScoreRed
                                    else if (dailyGoal.appProgressRatio >= 0.8f) com.digitscore.app.ui.theme.ScoreOrange
                                    else MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { dailyGoal.appProgressRatio.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = if (dailyGoal.currentAppUsageMinutes > dailyGoal.appLimitMinutes) com.digitscore.app.ui.theme.ScoreRed
                                else if (dailyGoal.appProgressRatio >= 0.8f) com.digitscore.app.ui.theme.ScoreOrange
                                else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surface
                            )
                        }
                    }

                    // 3. 잠금 해제 조절
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (english) "3. Unlock Limit" else "3. 잠금 해제 조절",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${dailyGoal.currentUnlockCount}회 / 목표 ${dailyGoal.unlockLimitTarget}회",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (dailyGoal.currentUnlockCount > dailyGoal.unlockLimitTarget) com.digitscore.app.ui.theme.ScoreRed
                                else if (dailyGoal.unlockProgressRatio >= 0.8f) com.digitscore.app.ui.theme.ScoreOrange
                                else MaterialTheme.colorScheme.secondary
                            )
                        }
                        LinearProgressIndicator(
                            progress = { dailyGoal.unlockProgressRatio.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = if (dailyGoal.currentUnlockCount > dailyGoal.unlockLimitTarget) com.digitscore.app.ui.theme.ScoreRed
                            else if (dailyGoal.unlockProgressRatio >= 0.8f) com.digitscore.app.ui.theme.ScoreOrange
                            else MaterialTheme.colorScheme.secondary,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                    }
                }

                // 시작 / 건너뛰기 액션 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                com.digitscore.app.data.DailyGoalStore.setUserAccepted(context)
                                com.digitscore.app.data.DailyGoalStore.markBriefingCompleted(context)
                                CumulativeScoreStore.confirmActivity(db, System.currentTimeMillis(), true)
                                revision++
                                TrackerForegroundService.refreshNotification(context)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (english) "Accept Goal" else "목표 시작", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                // 스킵하더라도 앱이 알아서 오토파일럿으로 유지하거나 카드만 닫음
                                com.digitscore.app.data.DailyGoalStore.setDismissed(context, true)
                                com.digitscore.app.data.DailyGoalStore.markBriefingCompleted(context)
                                CumulativeScoreStore.confirmActivity(db, System.currentTimeMillis(), true)
                                revision++
                                TrackerForegroundService.refreshNotification(context)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (english) "Pass (Auto)" else "건너뛰기 (자동)", fontSize = 13.sp)
                    }
                }
            }
        }
    }
    suggestion?.let { app ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (english) "Manage ${app.appName}?" else "${app.appName}을 관리 앱으로 바꿀까요?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (english) "Used for at least 90 minutes on 3 days this week. Changes apply to future scoring only."
                        else "최근 7일 중 3일 이상 90분 넘게 사용했습니다. 변경 후 사용부터 점수에 반영합니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (english) "Manage" else "관리로 변경", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { scope.launch {
                            val old = db.appDao().getAppWeight(app.packageName)
                            db.appDao().insertOrUpdateAppWeight((old ?: AppWeightEntity(app.packageName, app.appName,
                                AppCategoryType.NEUTRAL)).copy(categoryType = AppCategoryType.NEUTRAL, isUserModified = true))
                            CumulativeScoreStore.snoozeSuggestion(db, app.packageName, Long.MAX_VALUE); revision++
                            TrackerForegroundService.refreshNotification(context)
                        } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (english) "Keep general" else "일반 유지", fontSize = 12.sp) }
                    TextButton(
                        onClick = { scope.launch {
                            CumulativeScoreStore.snoozeSuggestion(db, app.packageName, System.currentTimeMillis() + 7 * 86_400_000L); revision++
                        } }
                    ) { Text(if (english) "Later" else "나중에", fontSize = 12.sp) }
                }
            }
        }
    }
}
