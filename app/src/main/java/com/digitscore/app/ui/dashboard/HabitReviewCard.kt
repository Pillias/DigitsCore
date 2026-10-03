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

/** Optional in-app briefing; no overlay, notification sound, or mandatory time entry. */
@Composable
fun HabitReviewCard(score: Int, apps: List<AppUsage>) {
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
        morning = now.hour in 4..12 && handledDate != now.toLocalDate()
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
    if (morning) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (english) "Morning check-in · Core Index $score" else "아침 브리핑 · 코어 지수 $score",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = if (english) "Ready to start your day? Awake breaks can restore your index; estimated sleep holds recovery. No time entry needed."
                        else "이제 활동을 시작하나요? 활동 중 휴식은 회복에 반영하고, 수면으로 추정한 휴식은 회복을 보류합니다. 시간을 입력할 필요는 없습니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { scope.launch {
                            CumulativeScoreStore.confirmActivity(db, System.currentTimeMillis(), true)
                            revision++; TrackerForegroundService.refreshNotification(context)
                        } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (english) "Start my day" else "활동 시작", fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = { scope.launch {
                            CumulativeScoreStore.confirmActivity(db, System.currentTimeMillis(), false)
                            revision++; TrackerForegroundService.refreshNotification(context)
                        } },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) { Text(if (english) "Still resting" else "아직 쉬는 중", fontSize = 13.sp) }
                }
                if (hasStepSensor) {
                    val threshold = remember(steps, revision) { com.digitscore.app.service.WakeStepAdaptiveManager.getThreshold(context) }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (english) "Use step evidence" else "걸음 보조 판단",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (english) "Waking is assisted when reaching $threshold steps in 15 min."
                                    else "15분 안에 ${threshold}걸음 감지 시 기상 보조로 사용",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Switch(
                            checked = steps,
                            onCheckedChange = { enabled ->
                                if (enabled && Build.VERSION.SDK_INT >= 29) permission.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                else setSteps(enabled)
                            }
                        )
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
