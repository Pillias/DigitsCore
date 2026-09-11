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
import com.digitscore.app.i18n.Text
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
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.stringResource
import com.digitscore.app.R
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.BuildConfig
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.backup.DataBackupManager
import com.digitscore.app.data.privacy.PrivacyDataManager
import com.digitscore.app.data.security.DatabaseEncryptionManager
import com.digitscore.app.data.security.DatabaseSecurityMode
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.entity.applyTo
import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.PresetMode
import com.digitscore.app.ui.components.SectionHeading
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.InformationDetailDialog
import com.digitscore.app.notification.StatusIconStyle
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.widget.ScoreWidget
import com.digitscore.app.widget.WidgetBackgroundStyle
import com.digitscore.app.ui.privacy.PrivacyPolicyDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.glance.appwidget.updateAll
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val MAX_IMPORT_BYTES = 20 * 1024 * 1024
// 기존 일일 점수 설정은 DB/백업 호환을 위해 유지하되 새 코어 지수 UI에서는 숨깁니다.
private const val SHOW_LEGACY_SCORE_SETTINGS = false

private data class SettingsDetail(
    val title: String,
    val value: String? = null,
    val description: String,
    val supportingText: String? = null
)

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
    val databaseSecurityStatus = remember { DatabaseEncryptionManager.currentStatus() }

    val userSettings by db.settingsDao().getSettingsFlow().collectAsState(initial = null)
    val settings = userSettings ?: UserSettingsEntity()
    val selectedModeId = settings.selectedPresetModeId
    val selectedPreset = PresetMode.fromId(selectedModeId)
    val selectedCoreIndexPreset = CoreIndexPreset.fromId(settings.selectedCoreIndexPresetId)
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var isPresetMenuExpanded by remember { mutableStateOf(false) }
    var isCoreIndexPresetMenuExpanded by remember { mutableStateOf(false) }
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }
    var isStatusIconMenuExpanded by remember { mutableStateOf(false) }
    var isWidgetBackgroundMenuExpanded by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptedImport by remember { mutableStateOf<ByteArray?>(null) }
    var showDeleteHistoryConfirmation by remember { mutableStateOf(false) }
    var selectedDetail by remember { mutableStateOf<SettingsDetail?>(null) }

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

    fun selectCoreIndexPreset(preset: CoreIndexPreset) {
        isCoreIndexPresetMenuExpanded = false
        scope.launch(Dispatchers.IO) {
            db.settingsDao().insertOrUpdateSettings(
                settings.copy(selectedCoreIndexPresetId = preset.id)
            )
            if (settings.isTrackingEnabled) {
                TrackerForegroundService.refreshNotification(context)
            }
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
                                    UiTranslator.translate(
                                        if (success) "이전 평문 백업을 복원했습니다. 새 백업은 암호화됩니다."
                                        else "백업 파일 형식이 올바르지 않습니다."
                                    ),
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, UiTranslator.translate("복원 중 오류가 발생했습니다: ${e.message}"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = UiTranslator.translate("뒤로가기"),
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
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.language), fontWeight = FontWeight.Bold)
                        Text(
                            stringResource(R.string.language_description),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        ExposedDropdownMenuBox(
                            expanded = isLanguageMenuExpanded,
                            onExpandedChange = { isLanguageMenuExpanded = !isLanguageMenuExpanded }
                        ) {
                            val language = AppLocale.currentLanguage(context)
                            OutlinedTextField(
                                value = if (language == AppLocale.ENGLISH) {
                                    stringResource(R.string.language_english)
                                } else {
                                    stringResource(R.string.language_korean)
                                },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(isLanguageMenuExpanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isLanguageMenuExpanded,
                                onDismissRequest = { isLanguageMenuExpanded = false }
                            ) {
                                listOf(
                                    AppLocale.KOREAN to stringResource(R.string.language_korean),
                                    AppLocale.ENGLISH to stringResource(R.string.language_english)
                                ).forEach { (code, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            isLanguageMenuExpanded = false
                                            scope.launch {
                                                AppLocale.saveLanguage(context, code)
                                                ScoreWidget().updateAll(context)
                                                AppLocale.applyLanguage(context as Activity, code)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                SectionHeading(
                    title = "코어 지수 프리셋",
                    subtitle = "최근 24시간 사용 흐름에서 중요하게 볼 항목을 선택합니다."
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = isCoreIndexPresetMenuExpanded,
                            onExpandedChange = {
                                isCoreIndexPresetMenuExpanded = !isCoreIndexPresetMenuExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = selectedCoreIndexPreset.title,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("현재 프리셋") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = isCoreIndexPresetMenuExpanded
                                    )
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isCoreIndexPresetMenuExpanded,
                                onDismissRequest = { isCoreIndexPresetMenuExpanded = false }
                            ) {
                                CoreIndexPreset.entries.forEach { preset ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(preset.title, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    preset.sensitivitySummary,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        },
                                        onClick = { selectCoreIndexPreset(preset) },
                                        trailingIcon = if (preset == selectedCoreIndexPreset) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = UiTranslator.translate("선택됨"),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }

                        Text(
                            selectedCoreIndexPreset.description,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            selectedCoreIndexPreset.sensitivitySummary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "프리셋 변경 즉시 최근 24시간 기록을 새 기준으로 다시 계산합니다.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 1. 프리셋 모드 선택
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
                SectionHeading(
                    title = "점수 프리셋 모드 선택",
                    subtitle = "생활 패턴에 맞는 기본값을 선택하고 아래에서 세부 조정합니다."
                )
            }

            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
                                                    contentDescription = UiTranslator.translate("선택됨"),
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

            item {
                SectionHeading(
                    title = "백그라운드 추적 및 표시",
                    subtitle = "상태바와 위젯의 표시 방식, 실시간 추적 여부를 관리합니다."
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("상태바 아이콘 스타일", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val selectedIconStyle = StatusIconStyle.fromId(settings.statusIconStyleId)
                        ExposedDropdownMenuBox(
                            expanded = isStatusIconMenuExpanded,
                            onExpandedChange = {
                                isStatusIconMenuExpanded = !isStatusIconMenuExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = when (selectedIconStyle) {
                                    StatusIconStyle.BIG_NUMBER -> "큰 숫자형 (권장)"
                                    StatusIconStyle.SCORE_PROPORTION -> "점수 비율형"
                                    StatusIconStyle.SCORE_TIER -> "전원 단계형"
                                    StatusIconStyle.NUMBER_FOCUS -> "숫자 분리형"
                                },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(isStatusIconMenuExpanded)
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isStatusIconMenuExpanded,
                                onDismissRequest = { isStatusIconMenuExpanded = false }
                            ) {
                                listOf(
                                    StatusIconStyle.BIG_NUMBER to "큰 숫자형 (권장)",
                                    StatusIconStyle.SCORE_TIER to "전원 단계형",
                                    StatusIconStyle.SCORE_PROPORTION to "점수 비율형",
                                    StatusIconStyle.NUMBER_FOCUS to "숫자 분리형"
                                ).forEach { (style, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            isStatusIconMenuExpanded = false
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    db.settingsDao().insertOrUpdateSettings(
                                                        settings.copy(statusIconStyleId = style.id)
                                                    )
                                                }
                                                if (settings.isTrackingEnabled) {
                                                    TrackerForegroundService.refreshNotification(context)
                                                }
                                            }
                                        },
                                        trailingIcon = if (selectedIconStyle == style) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = UiTranslator.translate("선택됨"),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (selectedIconStyle) {
                                StatusIconStyle.BIG_NUMBER ->
                                    "전원 모양 없이 상태바 영역 전체에 점수를 가장 크게 표시합니다."
                                StatusIconStyle.SCORE_PROPORTION ->
                                    "빨간 원호 위를 현재 점수만큼 녹색이 채웁니다. 중앙 막대는 점수 구간색으로 바뀝니다."
                                StatusIconStyle.SCORE_TIER ->
                                    "전원 버튼 전체가 점수 구간에 따라 빨강·주황·노랑·초록으로 바뀝니다."
                                StatusIconStyle.NUMBER_FOCUS ->
                                    "작은 전원 버튼 옆에 외곽선을 넣은 큰 점수를 분리해 표시합니다."
                            },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            "점수 구간: 0–39 빨강 · 40–59 주황 · 60–79 노랑 · 80–100 초록",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("위젯 배경", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        val selectedWidgetBackground = WidgetBackgroundStyle.fromId(
                            settings.widgetBackgroundStyleId
                        )
                        ExposedDropdownMenuBox(
                            expanded = isWidgetBackgroundMenuExpanded,
                            onExpandedChange = {
                                isWidgetBackgroundMenuExpanded = !isWidgetBackgroundMenuExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = when (selectedWidgetBackground) {
                                    WidgetBackgroundStyle.DARK -> "어두운 배경"
                                    WidgetBackgroundStyle.WHITE -> "흰색 배경"
                                    WidgetBackgroundStyle.TRANSPARENT -> "투명 배경"
                                },
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(isWidgetBackgroundMenuExpanded)
                                },
                                modifier = Modifier.menuAnchor().fillMaxWidth()
                            )
                            ExposedDropdownMenu(
                                expanded = isWidgetBackgroundMenuExpanded,
                                onDismissRequest = { isWidgetBackgroundMenuExpanded = false }
                            ) {
                                listOf(
                                    WidgetBackgroundStyle.DARK to "어두운 배경",
                                    WidgetBackgroundStyle.WHITE to "흰색 배경",
                                    WidgetBackgroundStyle.TRANSPARENT to "투명 배경"
                                ).forEach { (style, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            isWidgetBackgroundMenuExpanded = false
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    db.settingsDao().insertOrUpdateSettings(
                                                        settings.copy(widgetBackgroundStyleId = style.id)
                                                    )
                                                }
                                                ScoreWidget().updateAll(context)
                                            }
                                        },
                                        trailingIcon = if (selectedWidgetBackground == style) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = UiTranslator.translate("선택됨"),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                        Text(
                            text = when (selectedWidgetBackground) {
                                WidgetBackgroundStyle.DARK -> "어두운 카드와 밝은 글자를 사용합니다."
                                WidgetBackgroundStyle.WHITE -> "흰색 카드와 어두운 글자를 사용합니다."
                                WidgetBackgroundStyle.TRANSPARENT ->
                                    "전체 배경은 투명하게 하고 정보 영역만 읽기 쉽게 표시합니다."
                            },
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.outline
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

            // 2. 개인 목표 방어선

            if (SHOW_LEGACY_SCORE_SETTINGS) item {
                SectionHeading(
                    title = "개인 목표 기준선",
                    subtitle = "점수 안내와 언락 기준을 본인의 생활 패턴에 맞춥니다."
                )
            }

            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
                            text = "점수가 이 이하로 떨어지면 사용 균형 안내를 강조합니다.",
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

            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
                Spacer(modifier = Modifier.height(8.dp))
                SectionHeading(
                    title = "세부 가중치 설정",
                    subtitle = "심야 사용, 장시간 사용, 회복과 언락의 반영 강도를 조정합니다.",
                    actionLabel = "기본값",
                    onAction = {
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
                )
            }

            // 심야(24시~05시) 감점 가속 배수
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val lateNightMult = String.format(java.util.Locale.US, "%.1f", settings.lateNightMultiplier)
                        Text(
                            text = "심야(24시~05시) 감점 배수: ${lateNightMult}배",
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
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
                                text = "장시간 사용 로그(Log) 가속",
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
                if (SHOW_LEGACY_SCORE_SETTINGS) item {
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

            // 이전 사용량 이월 스위치
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
                                text = "⏳ 이전 사용량 이월",
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
                if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
                                text = "하루 최대 이전 사용량 이월: ${settings.maxYesterdayPenalty.toInt()}점",
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
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val dWeight = String.format(java.util.Locale.US, "%.1f", settings.distractingWeightPerMinute)
                        Text(
                            text = "관리 앱 1분당 감점치: ${dWeight}점",
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
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
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
            if (SHOW_LEGACY_SCORE_SETTINGS) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val unlVal = String.format(java.util.Locale.US, "%.1f", settings.unlockPenaltyPerCount)
                        Text(
                            text = "언락 기준 초과 1회당 감점치: ${unlVal}점",
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
                SectionHeading(
                    title = "데이터 관리 및 백업",
                    subtitle = "기록 보존, 암호화 백업, 복원과 삭제를 관리합니다."
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
                            text = if (databaseSecurityStatus.mode == DatabaseSecurityMode.ENCRYPTED) {
                                "앱별 기록은 기기 내부 암호화 DB에 저장되며, 백업 파일도 사용자 비밀번호로 암호화됩니다."
                            } else {
                                "기존 기록을 보호하기 위해 이번 실행에서는 호환 모드로 열었습니다. 백업 파일은 계속 사용자 비밀번호로 암호화됩니다."
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (databaseSecurityStatus.mode == DatabaseSecurityMode.PLAINTEXT_FALLBACK) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "DB 암호화 호환 모드",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "기록 손실과 실행 중단을 막기 위해 기존 DB를 그대로 사용하고 있습니다. 앱을 다시 시작하면 암호화를 재시도합니다.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("기록 보존 방식", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    "앱별 시작·종료 상세와 5분 단위 코어 지수 표본은 30일, 날짜별 집계는 365일 보관합니다.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    "30일을 넘겨 상세 기록을 보관하려면 만료 전에 암호화 백업을 저장하세요.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
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
                SectionHeading(
                    title = "앱 정보",
                    subtitle = "버전과 개인정보 처리 정책을 확인합니다."
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDetail = SettingsDetail(
                                title = "DigitsCore",
                                value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                description = "디지털 사용 습관을 전면 앱 사용시간과 언락 기록으로 분석합니다.",
                                supportingText = "개인정보 처리 방식은 아래 개인정보 처리 안내에서 확인할 수 있습니다."
                            )
                        },
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DetailChevron()
                        }
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

    selectedDetail?.let { detail ->
        InformationDetailDialog(
            title = detail.title,
            value = detail.value,
            description = detail.description,
            supportingText = detail.supportingText,
            onDismiss = { selectedDetail = null }
        )
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
                            Toast.makeText(context, UiTranslator.translate("백업 실패: ${error.message}"), Toast.LENGTH_LONG).show()
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
                                UiTranslator.translate(
                                    if (success) "암호화 백업을 복원했습니다."
                                    else "백업 데이터 형식이 올바르지 않습니다."
                                ),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } catch (error: Exception) {
                        launch(Dispatchers.Main) {
                            Toast.makeText(context, UiTranslator.translate(error.message ?: "복원에 실패했습니다."), Toast.LENGTH_LONG).show()
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
                                Toast.makeText(context, UiTranslator.translate("사용 기록을 삭제하고 추적을 중지했습니다."), Toast.LENGTH_LONG).show()
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
