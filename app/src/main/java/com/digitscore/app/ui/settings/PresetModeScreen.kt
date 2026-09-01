package com.digitscore.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import com.digitscore.app.BuildConfig
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.backup.DataBackupManager
import com.digitscore.app.data.privacy.PrivacyDataManager
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.entity.applyTo
import com.digitscore.app.engine.ScoringBenchmark
import com.digitscore.app.model.PresetMode
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.privacy.PrivacyPolicyDialog
import com.digitscore.app.ui.theme.ScoreGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024

private fun InputStream.readBytesWithLimit(maxBytes: Int = MAX_IMPORT_BYTES): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) break
        total += read
        require(total <= maxBytes) { "백업 파일은 20MB 이하여야 합니다." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetModeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DigitsDatabase.getInstance(context) }

    val userSettings by db.settingsDao().getSettingsFlow().collectAsState(initial = null)
    val settings = userSettings ?: UserSettingsEntity()
    val selectedModeId = settings.selectedPresetModeId
    val selectedPreset = PresetMode.fromId(selectedModeId)
    val selectedBenchmark = ScoringBenchmark.forPreset(selectedModeId)
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var isPresetMenuExpanded by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptedImport by remember { mutableStateOf<ByteArray?>(null) }
    var isRetentionMenuExpanded by remember { mutableStateOf(false) }
    var showDeleteHistoryConfirmation by remember { mutableStateOf(false) }

    fun selectPreset(mode: PresetMode) {
        isPresetMenuExpanded = false
        scope.launch(Dispatchers.IO) {
            val rule = mode.scoreRule
            db.settingsDao().insertOrUpdateSettings(
                settings.copy(
                    selectedPresetModeId = mode.id,
                    distractingWeightPerMinute = rule.distractingWeightPerMinute,
                    productiveBonusPerMinute = rule.productiveBonusPerMinute,
                    idleBonusPer10Minutes = rule.idleBonusPer10Minutes,
                    maxIdleBonus = rule.maxIdleBonus,
                    maxProductiveBonus = rule.maxProductiveBonus,
                    targetUnlockCount = rule.unlockPenaltyThreshold,
                    unlockPenaltyPerCount = rule.unlockPenaltyPerCount,
                    lateNightMultiplier = rule.lateNightMultiplier,
                    isLogAccelerationEnabled = rule.isLogAccelerationEnabled,
                    logAccelerationThresholdMinutes = rule.logAccelerationThresholdMinutes,
                    logAccelerationScaleMinutes = rule.logAccelerationScaleMinutes,
                    isYesterdayPenaltyEnabled = rule.isYesterdayPenaltyEnabled,
                    yesterdayPenaltyTriggerScore = rule.yesterdayPenaltyTriggerScore,
                    yesterdayPenaltyRate = rule.yesterdayPenaltyRate,
                    maxYesterdayPenalty = rule.maxYesterdayPenalty
                )
            )
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytesWithLimit()
                    }
                    if (content != null) {
                        if (com.digitscore.app.data.backup.BackupCrypto.isEncryptedBackup(content)) {
                            launch(Dispatchers.Main) { pendingEncryptedImport = content }
                        } else {
                            val success = DataBackupManager.importBackup(context, content, null)
                            launch(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    if (success) "이전 평문 백업을 복원했습니다. 새 백업은 암호화됩니다."
                                    else "백업 파일 형식이 올바르지 않습니다.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, "복원 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("디톡스 모드 및 가중치 설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 프리셋 모드 선택
            item {
                Text(
                    text = "디톡스 프리셋 모드 선택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = isPresetMenuExpanded,
                            onExpandedChange = { isPresetMenuExpanded = !isPresetMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedPreset.title,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("현재 프리셋") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = isPresetMenuExpanded
                                    )
                                },
                                modifier = Modifier
                                    .menuAnchor()
                                    .fillMaxWidth()
                            )

                            ExposedDropdownMenu(
                                expanded = isPresetMenuExpanded,
                                onDismissRequest = { isPresetMenuExpanded = false }
                            ) {
                                PresetMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(mode.title) },
                                        onClick = { selectPreset(mode) },
                                        trailingIcon = if (mode.id == selectedModeId) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "선택됨",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = selectedPreset.description,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (selectedBenchmark != null) {
                item {
                    BenchmarkPreviewCard(
                        benchmark = selectedBenchmark,
                        settings = settings,
                        preset = selectedPreset
                    )
                }
            }

            // 2. 개인 목표 방어선
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "백그라운드 추적",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (settings.isTrackingEnabled) "사용 기록 추적 중" else "사용 기록 추적 중지됨",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "끄면 백그라운드 서비스와 상태바 점수 알림이 즉시 종료됩니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.isTrackingEnabled,
                            onCheckedChange = { enabled ->
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(isTrackingEnabled = enabled)
                                        )
                                    }
                                    if (enabled) {
                                        TrackerForegroundService.start(context)
                                    } else {
                                        TrackerForegroundService.stop(context)
                                    }
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "개인 목표 방어선",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "최저 점수 방어선: ${settings.minimumScoreDefenseLine}점",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "점수가 이 이하로 떨어지면 디톡스 경고를 강조합니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.minimumScoreDefenseLine.toFloat(),
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(minimumScoreDefenseLine = newValue.toInt())
                                    )
                                }
                            },
                            valueRange = 30f..90f,
                            steps = 11
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "일일 목표 언락 제한: ${settings.targetUnlockCount}회",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "이 횟수를 초과하여 스마트폰을 켤 때 페널티가 누적됩니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.targetUnlockCount.toFloat(),
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(targetUnlockCount = newValue.toInt())
                                    )
                                }
                            },
                            valueRange = 10f..80f,
                            steps = 13
                        )
                    }
                }
            }

            // 3. 세부 가중치 커스텀 설정
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "세부 가중치 커스텀 설정",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val currentPreset = PresetMode.fromId(settings.selectedPresetModeId)
                                val rule = currentPreset.scoreRule
                                db.settingsDao().insertOrUpdateSettings(
                                    settings.copy(
                                        distractingWeightPerMinute = rule.distractingWeightPerMinute,
                                        productiveBonusPerMinute = rule.productiveBonusPerMinute,
                                        idleBonusPer10Minutes = rule.idleBonusPer10Minutes,
                                        maxIdleBonus = rule.maxIdleBonus,
                                        maxProductiveBonus = rule.maxProductiveBonus,
                                        targetUnlockCount = rule.unlockPenaltyThreshold,
                                        unlockPenaltyPerCount = rule.unlockPenaltyPerCount,
                                        lateNightMultiplier = rule.lateNightMultiplier,
                                        isLogAccelerationEnabled = rule.isLogAccelerationEnabled,
                                        logAccelerationThresholdMinutes = rule.logAccelerationThresholdMinutes,
                                        logAccelerationScaleMinutes = rule.logAccelerationScaleMinutes,
                                        isYesterdayPenaltyEnabled = rule.isYesterdayPenaltyEnabled,
                                        yesterdayPenaltyTriggerScore = rule.yesterdayPenaltyTriggerScore,
                                        yesterdayPenaltyRate = rule.yesterdayPenaltyRate,
                                        maxYesterdayPenalty = rule.maxYesterdayPenalty
                                    )
                                )
                            }
                        }
                    ) {
                        Text(text = "기본값 초기화", fontSize = 12.sp)
                    }
                }
            }

            // 심야(24시~05시) 감점 가속 배수
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val lateNightMult = String.format(java.util.Locale.US, "%.1f", settings.lateNightMultiplier)
                        Text(
                            text = "🌙 심야(24시~05시) 감점 배수: ${lateNightMult}배",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "자정부터 새벽 5시까지 방해 앱 사용 시 감점을 배수로 가속합니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.lateNightMultiplier,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(lateNightMultiplier = (newValue * 10).toInt() / 10f)
                                    )
                                }
                            },
                            valueRange = 1.0f..3.0f,
                            steps = 19
                        )
                    }
                }
            }

            // 장시간 연속 사용 로그(Log) 가속 스위치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📈 장시간 사용 로그(Log) 가속",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "설정한 앱별 누적시간 이후 감점 속도가 비선형으로 빨라집니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.isLogAccelerationEnabled,
                            onCheckedChange = { isChecked ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(isLogAccelerationEnabled = isChecked)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (settings.isLogAccelerationEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "가속 시작: ${settings.logAccelerationThresholdMinutes.toInt()}분",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "이 시간까지는 기본 감점만 적용합니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Slider(
                                value = settings.logAccelerationThresholdMinutes,
                                onValueChange = { value ->
                                    scope.launch(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(logAccelerationThresholdMinutes = value.toInt().toFloat())
                                        )
                                    }
                                },
                                valueRange = 30f..180f,
                                steps = 9
                            )

                            Text(
                                text = "가속 완만함: ${settings.logAccelerationScaleMinutes.toInt()}분",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "값이 클수록 장시간 사용 감점 증가가 완만합니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Slider(
                                value = settings.logAccelerationScaleMinutes,
                                onValueChange = { value ->
                                    scope.launch(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(logAccelerationScaleMinutes = value.toInt().toFloat())
                                        )
                                    }
                                },
                                valueRange = 60f..300f,
                                steps = 7
                            )
                        }
                    }
                }
            }

            // 전날 과사용 시작 페널티 (디톡스 부채) 스위치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "⏳ 전날 과사용 시작 페널티 (디톡스 부채)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "전날 점수가 설정 기준보다 낮을 때 다음 날 일부만 이월합니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.isYesterdayPenaltyEnabled,
                            onCheckedChange = { isChecked ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(isYesterdayPenaltyEnabled = isChecked)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (settings.isYesterdayPenaltyEnabled) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "부채 발동 점수: ${settings.yesterdayPenaltyTriggerScore}점 미만",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Slider(
                                value = settings.yesterdayPenaltyTriggerScore.toFloat(),
                                onValueChange = { value ->
                                    scope.launch(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(yesterdayPenaltyTriggerScore = value.toInt())
                                        )
                                    }
                                },
                                valueRange = 40f..90f,
                                steps = 9
                            )

                            Text(
                                text = "기준 미달 1점당 이월: ${String.format(java.util.Locale.US, "%.2f", settings.yesterdayPenaltyRate)}점",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Slider(
                                value = settings.yesterdayPenaltyRate,
                                onValueChange = { value ->
                                    scope.launch(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(yesterdayPenaltyRate = (value * 20).toInt() / 20f)
                                        )
                                    }
                                },
                                valueRange = 0.05f..1.0f,
                                steps = 18
                            )

                            Text(
                                text = "하루 최대 디톡스 부채: ${settings.maxYesterdayPenalty.toInt()}점",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Slider(
                                value = settings.maxYesterdayPenalty,
                                onValueChange = { value ->
                                    scope.launch(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(maxYesterdayPenalty = value.toInt().toFloat())
                                        )
                                    }
                                },
                                valueRange = 0f..30f,
                                steps = 5
                            )
                        }
                    }
                }
            }

            // 방해 앱 감점 가중치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val dWeight = String.format(java.util.Locale.US, "%.1f", settings.distractingWeightPerMinute)
                        Text(
                            text = "⚠️ 방해 앱 1분당 감점치: ${dWeight}점",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "SNS, 영상 등 방해 앱 사용 1분당 차감되는 기본 점수입니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.distractingWeightPerMinute,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(distractingWeightPerMinute = (newValue * 10).toInt() / 10f)
                                    )
                                }
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 18
                        )
                    }
                }
            }

            // 화면 미사용(Idle) 회복 가중치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val idleVal = String.format(java.util.Locale.US, "%.2f", settings.idleBonusPer10Minutes)
                        Text(
                            text = "🌿 화면 미사용 10분당 회복치: ${idleVal}점",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "스마트폰 화면을 끄고 휴식할 때 10분당 회복되는 점수입니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.idleBonusPer10Minutes,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(idleBonusPer10Minutes = (newValue * 20).toInt() / 20f)
                                    )
                                }
                            },
                            valueRange = 0.05f..1.0f,
                            steps = 18
                        )
                    }
                }
            }

            // 일일 보너스 최대 상한선(Cap)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🛡️ 일일 회복 보너스 최대 상한: ${settings.maxIdleBonus.toInt()}점",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "수면 및 장시간 미사용으로 하루에 얻을 수 있는 보너스 최대 한도입니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.maxIdleBonus,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(
                                            maxIdleBonus = newValue.toInt().toFloat(),
                                            maxProductiveBonus = newValue.toInt().toFloat()
                                        )
                                    )
                                }
                            },
                            valueRange = 5f..30f,
                            steps = 4
                        )
                    }
                }
            }

            // 언락 초과당 감점치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val unlVal = String.format(java.util.Locale.US, "%.1f", settings.unlockPenaltyPerCount)
                        Text(
                            text = "🔓 언락 기준 초과 1회당 감점치: ${unlVal}점",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "일일 목표 언락 횟수를 초과할 때마다 차감되는 페널티 점수입니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.unlockPenaltyPerCount,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(unlockPenaltyPerCount = (newValue * 10).toInt() / 10f)
                                    )
                                }
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 18
                        )
                    }
                }
            }

            // 4. 데이터 관리 및 백업
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "📦 데이터 관리 및 백업",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "앱별 기록은 기기 내부 암호화 DB에 저장되며, 백업 파일도 사용자 비밀번호로 암호화됩니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        ExposedDropdownMenuBox(
                            expanded = isRetentionMenuExpanded,
                            onExpandedChange = { isRetentionMenuExpanded = !isRetentionMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = when (settings.appHistoryRetentionDays) {
                                    30 -> "30일"
                                    90 -> "90일"
                                    180 -> "180일"
                                    else -> "365일"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("앱별 기록 보존 기간") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(isRetentionMenuExpanded)
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isRetentionMenuExpanded,
                                onDismissRequest = { isRetentionMenuExpanded = false }
                            ) {
                                listOf(30, 90, 180, 365).forEach { days ->
                                    DropdownMenuItem(
                                        text = { Text("${days}일") },
                                        onClick = {
                                            isRetentionMenuExpanded = false
                                            scope.launch(Dispatchers.IO) {
                                                db.settingsDao().insertOrUpdateSettings(
                                                    settings.copy(appHistoryRetentionDays = days)
                                                )
                                                PrivacyDataManager.applyAppHistoryRetention(context, days)
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("잠금 화면에서 상세 정보 숨기기", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "점수·화면시간·언락 횟수를 잠금 해제 전에는 표시하지 않습니다.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = settings.hideSensitiveNotificationOnLockScreen,
                                onCheckedChange = { hidden ->
                                    scope.launch(Dispatchers.IO) {
                                        db.settingsDao().insertOrUpdateSettings(
                                            settings.copy(hideSensitiveNotificationOnLockScreen = hidden)
                                        )
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 2) 데이터 백업
                            Button(
                                onClick = { showBackupPasswordDialog = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(imageVector = Icons.Default.Backup, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(text = "암호화 백업", fontSize = 13.sp)
                            }

                            // 3) 데이터 복원
                            Button(
                                onClick = {
                                    importLauncher.launch("*/*")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(text = "데이터 복원", fontSize = 13.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { showDeleteHistoryConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("모든 사용 기록 즉시 삭제")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "앱 정보",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "DigitsCore",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "디지털 사용 습관 점수 관리",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = { showPrivacyPolicy = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("개인정보 처리 안내")
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }

    if (showBackupPasswordDialog) {
        BackupPasswordDialog(
            title = "암호화 백업 만들기",
            confirmPassword = true,
            onDismiss = { showBackupPasswordDialog = false },
            onConfirm = { password ->
                showBackupPasswordDialog = false
                scope.launch(Dispatchers.IO) {
                    val chars = password.toCharArray()
                    try {
                        val encrypted = DataBackupManager.exportEncrypted(context, chars)
                        launch(Dispatchers.Main) {
                            DataBackupManager.shareEncryptedBackup(context, encrypted)
                        }
                    } catch (error: Exception) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, "백업 실패: ${error.message}", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        chars.fill('\u0000')
                    }
                }
            }
        )
    }

    pendingEncryptedImport?.let { encryptedBytes ->
        BackupPasswordDialog(
            title = "암호화 백업 복원",
            confirmPassword = false,
            onDismiss = { pendingEncryptedImport = null },
            onConfirm = { password ->
                pendingEncryptedImport = null
                scope.launch(Dispatchers.IO) {
                    val chars = password.toCharArray()
                    try {
                        val success = DataBackupManager.importBackup(context, encryptedBytes, chars)
                        launch(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (success) "암호화 백업을 복원했습니다."
                                else "백업 데이터 형식이 올바르지 않습니다.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (error: Exception) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, error.message ?: "복원에 실패했습니다.", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        chars.fill('\u0000')
                    }
                }
            }
        )
    }

    if (showDeleteHistoryConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteHistoryConfirmation = false },
            title = { Text("모든 사용 기록을 삭제할까요?", fontWeight = FontWeight.Bold) },
            text = {
                Text("앱별 365일 기록, 점수 기록과 언락 통계가 삭제되고 추적이 중지됩니다. 앱 등급과 점수 설정은 유지되며 이 작업은 되돌릴 수 없습니다.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteHistoryConfirmation = false
                        scope.launch(Dispatchers.IO) {
                            PrivacyDataManager.deleteAllUsageHistory(context)
                            launch(Dispatchers.Main) {
                                Toast.makeText(context, "사용 기록을 삭제하고 추적을 중지했습니다.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("삭제") }
            },
            dismissButton = { OutlinedButton(onClick = { showDeleteHistoryConfirmation = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun BackupPasswordDialog(
    title: String,
    confirmPassword: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val isValid = password.length >= 8 && (!confirmPassword || password == confirmation)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "비밀번호는 백업에 저장되지 않으며 분실하면 복원할 수 없습니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호 · 8자 이상") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmPassword) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text("비밀번호 확인") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = confirmation.isNotEmpty() && password != confirmation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = isValid) { Text("확인") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("취소") } }
    )
}

@Composable
private fun BenchmarkPreviewCard(
    benchmark: ScoringBenchmark,
    settings: UserSettingsEntity,
    preset: PresetMode
) {
    val previewRule = settings.applyTo(preset.scoreRule)
    val expectedScore = benchmark.evaluate(previewRule).finalScore
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "📊 ${benchmark.title} · 60점 보정",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "기준 사용 ${benchmark.totalScreenMinutes}분 · 방해 ${benchmark.distractingMinutes}분 · " +
                    "생산성 ${benchmark.productiveMinutes}분 · 언락 ${benchmark.unlockCount}회",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "현재 설정 예상 점수: ${expectedScore}점 (목표 ${benchmark.targetScore}점)",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (expectedScore in 55..65) ScoreGreen else MaterialTheme.colorScheme.error
            )
            Text(
                text = benchmark.sourceLabel,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "건강 진단 기준이 아닌 초기 보정용 시나리오입니다. 학업·업무·콘텐츠 품질에 맞게 세부값을 조정하세요.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
