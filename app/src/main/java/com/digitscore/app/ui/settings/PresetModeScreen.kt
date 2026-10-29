package com.digitscore.app.ui.settings

import com.digitscore.app.ui.theme.tile
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.DigitsShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Speed
import android.os.Build
import android.provider.Settings
import android.content.Intent
import android.view.View
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.digitscore.app.notification.EdgeLightingOverlayManager
import com.digitscore.app.notification.ScoreNotificationManager
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import com.digitscore.app.i18n.Text
import com.digitscore.app.data.entity.effectiveScoringConfig
import com.digitscore.app.model.defaultScoringConfig
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.stringResource
import com.digitscore.app.R
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.BuildConfig
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.backup.DataBackupManager
import com.digitscore.app.data.privacy.PrivacyDataManager
import com.digitscore.app.data.security.DatabaseEncryptionManager
import com.digitscore.app.data.security.DatabaseSecurityMode
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.entity.applyTo
import com.digitscore.app.data.entity.effectiveRapidUsageAlertConfig
import com.digitscore.app.model.CoreIndexPreset
import com.digitscore.app.model.PresetMode
import com.digitscore.app.model.defaultRapidUsageAlertConfig
import com.digitscore.app.ui.components.SectionHeading
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.InformationDetailDialog
import com.digitscore.app.ui.components.ResponsiveContent
import com.digitscore.app.ui.theme.AppTheme
import com.digitscore.app.ui.theme.AppThemeMode
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

private const val MAX_IMPORT_BYTES = 64 * 1024 * 1024
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
        require(total <= maxBytes) { "백업 파일은 64MB 이하여야 합니다." }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetModeScreen(
    onNavigateBack: () -> Unit,
    onThemeModeChanged: (AppThemeMode) -> Unit
) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    val scope = rememberCoroutineScope()
    val db = remember { DigitsDatabase.getInstance(context) }
    val databaseSecurityStatus = remember { DatabaseEncryptionManager.currentStatus() }

    val userSettings by db.settingsDao().getSettingsFlow().collectAsState(initial = null)
    val measurementDiagnostics by ScoreRepository.measurementDiagnostics.collectAsState()
    val settings = userSettings ?: UserSettingsEntity()
    val selectedModeId = settings.selectedPresetModeId
    val selectedPreset = PresetMode.fromId(selectedModeId)
    val selectedCoreIndexPreset = CoreIndexPreset.fromId(settings.selectedCoreIndexPresetId)
    val rapidAlertConfig = settings.effectiveRapidUsageAlertConfig(selectedCoreIndexPreset)
    val scoringConfig = settings.effectiveScoringConfig(selectedCoreIndexPreset)

    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var showUpdateHistory by remember { mutableStateOf(false) }
    var isPresetMenuExpanded by remember { mutableStateOf(false) }
    var isCoreIndexPresetMenuExpanded by remember { mutableStateOf(false) }
    var isLanguageMenuExpanded by remember { mutableStateOf(false) }
    var isThemeMenuExpanded by remember { mutableStateOf(false) }
    var isStatusIconMenuExpanded by remember { mutableStateOf(false) }
    var isWidgetBackgroundMenuExpanded by remember { mutableStateOf(false) }
    var showBackupPasswordDialog by remember { mutableStateOf(false) }
    var pendingEncryptedImport by remember { mutableStateOf<ByteArray?>(null) }
    var showDeleteHistoryConfirmation by remember { mutableStateOf(false) }
    var showBugReport by remember { mutableStateOf(false) }
    var showDiagnosticsModal by remember { mutableStateOf(false) }
    var selectedDetail by remember { mutableStateOf<SettingsDetail?>(null) }
    var selectedCategoryTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    var edgeLightingEnabled by remember { mutableStateOf(EdgeLightingOverlayManager.isEnabled(context)) }
    var managedTriggerMins by remember { mutableStateOf(EdgeLightingOverlayManager.getManagedTriggerMinutes(context)) }
    var studyTriggerMins by remember { mutableStateOf(EdgeLightingOverlayManager.getStudyTriggerMinutes(context)) }
    var dynamicLauncherIconEnabled by remember {
        mutableStateOf(com.digitscore.app.notification.LauncherIconManager.isEnabled(context))
    }

    var isEdgeLightingDetailExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var isSilentAlertDetailExpanded by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasOverlayPermission = Settings.canDrawOverlays(context)
                edgeLightingEnabled = EdgeLightingOverlayManager.isEnabled(context)
                managedTriggerMins = EdgeLightingOverlayManager.getManagedTriggerMinutes(context)
                studyTriggerMins = EdgeLightingOverlayManager.getStudyTriggerMinutes(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    fun saveRapidAlertSettings(updated: UserSettingsEntity) {
        scope.launch(Dispatchers.IO) {
            db.settingsDao().insertOrUpdateSettings(updated)
            if (updated.isTrackingEnabled) {
                TrackerForegroundService.refreshNotification(context)
            }
        }
    }

    fun customizeRapidAlert(
        windowMinutes: Int = rapidAlertConfig.windowMinutes,
        scoreDrop: Int = rapidAlertConfig.scoreDrop,
        usageMinutes: Int = rapidAlertConfig.usageMinutes,
        continuousMinutes: Int = rapidAlertConfig.continuousMinutes,
        cooldownMinutes: Int = rapidAlertConfig.cooldownMinutes
    ) {
        saveRapidAlertSettings(
            settings.copy(
                usePresetRapidAlertDefaults = false,
                rapidAlertWindowMinutes = windowMinutes,
                rapidAlertScoreDrop = scoreDrop,
                rapidAlertUsageMinutes = usageMinutes.coerceAtMost(windowMinutes),
                rapidAlertContinuousMinutes = continuousMinutes,
                rapidAlertCooldownMinutes = cooldownMinutes
            )
        )
    }

    fun saveScoringSettings(updated: UserSettingsEntity) {
        scope.launch(Dispatchers.IO) {
            db.settingsDao().insertOrUpdateSettings(updated)
            if (updated.isTrackingEnabled) {
                TrackerForegroundService.refreshNotification(context)
            }
        }
    }

    fun customizeScoring(
        useDefaults: Boolean = false,
        continuousStartMinutes: Int = scoringConfig.continuousLoadStartMinutes.toInt(),
        lateNightTier1Multiplier: Float = scoringConfig.lateNightTier1Multiplier.toFloat(),
        lateNightTier2Multiplier: Float = scoringConfig.lateNightTier2Multiplier.toFloat(),
        isSleepFreezeEnabled: Boolean = scoringConfig.isSleepFreezeEnabled,
        sleepThresholdMinutes: Int = scoringConfig.sleepDetectionThresholdMinutes.toInt(),
        targetUnlockCount: Int = scoringConfig.unlockThreshold
    ) {
        saveScoringSettings(
            settings.copy(
                usePresetScoringDefaults = useDefaults,
                customContinuousStartMinutes = continuousStartMinutes,
                customLateNightTier1Multiplier = lateNightTier1Multiplier,
                customLateNightTier2Multiplier = lateNightTier2Multiplier,
                customIsSleepFreezeEnabled = isSleepFreezeEnabled,
                customSleepThresholdMinutes = sleepThresholdMinutes,
                customTargetUnlockCount = targetUnlockCount
            )
        )
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
                title = { Text(if (isEn) "Settings" else "환경설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
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
        ResponsiveContent(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                // 4개 폴더 탭 바 (가로 스크롤 없이 1/4씩 균등 분할)
                val tabs = listOf(
                    if (isEn) "General" else "일반",
                    if (isEn) "Scoring" else "점수",
                    if (isEn) "Alerts" else "알림",
                    if (isEn) "Data" else "데이터"
                )
                Surface(
                    shape = DigitsShape.md,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEachIndexed { index, label ->
                            val isSelected = selectedCategoryTab == index
                            Surface(
                                shape = DigitsShape.sm,
                                color = if (isSelected) MaterialTheme.colorScheme.tile else Color.Transparent,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedCategoryTab = index }
                            ) {
                                Box(
                                    modifier = Modifier.padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = DigitsTextSize.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
            if (selectedCategoryTab == 0) item {
                // 다른 탭과 첫 줄을 맞추기 위한 제목
                SectionHeading(
                    title = if (isEn) "Display & tracking" else "화면과 추적",
                    subtitle = if (isEn) "Language, theme, widget and app icon." else "언어, 화면 모드, 위젯, 앱 아이콘을 정합니다."
                )
            }

            if (selectedCategoryTab == 0) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            // 좌측: 언어 설정 (weight 1f)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(stringResource(R.string.language), fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
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
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall
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
                                                text = { Text(label, fontSize = DigitsTextSize.body) },
                                                onClick = {
                                                    isLanguageMenuExpanded = false
                                                    scope.launch {
                                                        AppLocale.saveLanguage(context, code)
                                                        com.digitscore.app.widget.DigitsWidgets.updateAll(context)
                                                        AppLocale.applyLanguage(context as Activity, code)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // 우측: 테마 설정 (weight 1f)
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(stringResource(R.string.theme_mode), fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                                ExposedDropdownMenuBox(
                                    expanded = isThemeMenuExpanded,
                                    onExpandedChange = { isThemeMenuExpanded = !isThemeMenuExpanded }
                                ) {
                                    val themeMode = AppTheme.currentMode(context)
                                    OutlinedTextField(
                                        value = when (themeMode) {
                                            AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                            AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                            AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                        },
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            ExposedDropdownMenuDefaults.TrailingIcon(isThemeMenuExpanded)
                                        },
                                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    ExposedDropdownMenu(
                                        expanded = isThemeMenuExpanded,
                                        onDismissRequest = { isThemeMenuExpanded = false }
                                    ) {
                                        AppThemeMode.entries.forEach { mode ->
                                            val label = when (mode) {
                                                AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                                AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
                                                AppThemeMode.DARK -> stringResource(R.string.theme_dark)
                                            }
                                            DropdownMenuItem(
                                                text = { Text(label, fontSize = DigitsTextSize.body) },
                                                onClick = {
                                                    isThemeMenuExpanded = false
                                                    onThemeModeChanged(mode)
                                                },
                                                trailingIcon = if (themeMode == mode) {
                                                    {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Check,
                                                            contentDescription = UiTranslator.translate("선택됨"),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }
                                                } else null
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 하단: 위젯 배경 설정
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(if (isEn) "Widget Background" else "위젯 배경", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
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
                                    value = UiTranslator.translate(when (selectedWidgetBackground) {
                                        WidgetBackgroundStyle.DARK -> "어두운 배경"
                                        WidgetBackgroundStyle.WHITE -> "흰색 배경"
                                        WidgetBackgroundStyle.TRANSPARENT -> "투명 배경"
                                    }),
                                    onValueChange = {},
                                    readOnly = true,
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(isWidgetBackgroundMenuExpanded)
                                    },
                                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall
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
                                            text = { Text(UiTranslator.translate(label), fontSize = DigitsTextSize.body) },
                                            onClick = {
                                                isWidgetBackgroundMenuExpanded = false
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        db.settingsDao().insertOrUpdateSettings(
                                                            settings.copy(widgetBackgroundStyleId = style.id)
                                                        )
                                                    }
                                                    com.digitscore.app.widget.DigitsWidgets.updateAll(context)
                                                }
                                            },
                                            trailingIcon = if (selectedWidgetBackground == style) {
                                                {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Check,
                                                        contentDescription = UiTranslator.translate("선택됨"),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else null
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        // 점수에 따라 바뀌는 런처 아이콘 (기본 꺼짐)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp)
                                .padding(top = 12.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text(
                                    if (isEn) "Score-colored app icon" else "점수 색상 앱 아이콘",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = DigitsTextSize.body
                                )
                                Text(
                                    if (isEn) "Changes the launcher icon color (green/yellow/orange/red) at most once a day while the screen is off. Some launchers may remove the home-screen shortcut when the icon changes."
                                    else "화면이 꺼져 있을 때 하루 최대 1회 런처 아이콘 색(녹/노/주/빨)을 바꿉니다. 일부 런처는 아이콘이 바뀔 때 홈 화면 바로가기를 지울 수 있습니다.",
                                    fontSize = DigitsTextSize.label,
                                    color = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.padding(top = 3.dp)
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = dynamicLauncherIconEnabled,
                                onCheckedChange = { enabled ->
                                    dynamicLauncherIconEnabled = enabled
                                    com.digitscore.app.notification.LauncherIconManager.setEnabled(context, enabled)
                                }
                            )
                        }
                    }
                }
            }

            if (selectedCategoryTab == 1) item {
                SectionHeading(
                    title = if (isEn) "Core Index Preset" else "점수 모드",
                    subtitle = if (isEn) "Select which signals matter most in your 24h usage pattern." else "최근 24시간 사용 흐름에서 중요하게 볼 항목을 선택합니다."
                )
            }

            if (selectedCategoryTab == 1) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ExposedDropdownMenuBox(
                            expanded = isCoreIndexPresetMenuExpanded,
                            onExpandedChange = {
                                isCoreIndexPresetMenuExpanded = !isCoreIndexPresetMenuExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = UiTranslator.translate(selectedCoreIndexPreset.title(isEn)),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(if (isEn) "Current Preset" else "현재 점수 모드") },
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
                                    val isAvailable = preset == CoreIndexPreset.BALANCED
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = UiTranslator.translate(preset.title(isEn)),
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (!isAvailable) {
                                                        Surface(
                                                            shape = DigitsShape.xs,
                                                            color = MaterialTheme.colorScheme.surfaceVariant
                                                        ) {
                                                            Text(
                                                                text = "Later",
                                                                fontSize = DigitsTextSize.micro,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.outline,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    text = if (isAvailable) preset.sensitivitySummary(isEn) else UiTranslator.translate("충분한 데이터 축적 후 추후 제공 예정"),
                                                    fontSize = DigitsTextSize.caption,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        },
                                        onClick = {
                                            if (isAvailable) {
                                                selectCoreIndexPreset(preset)
                                            }
                                        },
                                        enabled = isAvailable,
                                        trailingIcon = if (preset == selectedCoreIndexPreset) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Rounded.Check,
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
                            selectedCoreIndexPreset.description(isEn),
                            fontSize = DigitsTextSize.body,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            selectedCoreIndexPreset.sensitivitySummary(isEn),
                            fontSize = DigitsTextSize.label,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (selectedCategoryTab == 2) item {
                SectionHeading(
                    title = if (isEn) "Traffic Light & Rapid Alerts" else "화면 신호등 및 사용 흐름 알림",
                    subtitle = if (isEn) "Sets threshold conditions for screen glow and unobtrusive alerts." else "게임이나 화면을 방해하지 않는 신호등 조명과 알림 조건을 정합니다."
                )
            }

            if (selectedCategoryTab == 2) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = if (isEn) "Traffic Light Edge Alert" else "화면 신호등 알림",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = DigitsTextSize.bodyLarge
                                    )
                                    Surface(
                                        shape = DigitsShape.xs,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = if (isEn) "Beta" else "베타",
                                            fontSize = DigitsTextSize.micro,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Text(
                                    text = if (isEn) "Gently pulses the phone bezel in green or red when continuous use limits are reached."
                                    else "스마트폰 최외곽 베젤에서 초록불·빨간불 간접 조명으로 연속 사용 한도를 알려줍니다.",
                                    fontSize = DigitsTextSize.label,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.Switch(
                                checked = edgeLightingEnabled && hasOverlayPermission,
                                onCheckedChange = { newChecked ->
                                    if (newChecked) {
                                        EdgeLightingOverlayManager.setEnabled(context, true)
                                        edgeLightingEnabled = true
                                        if (!hasOverlayPermission) {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            try {
                                                context.startActivity(intent)
                                            } catch (_: Exception) {
                                                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                            }
                                        }
                                    } else {
                                        EdgeLightingOverlayManager.setEnabled(context, false)
                                        edgeLightingEnabled = false
                                    }
                                }
                            )
                        }

                        if (!hasOverlayPermission) {
                            Text(
                                text = if (isEn) "Requires 'Display over other apps' permission" else "'다른 앱 위에 표시' 권한 허용 필요",
                                fontSize = DigitsTextSize.caption,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val intent = Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        try {
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                                        }
                                    }
                            )
                        }

                        if (edgeLightingEnabled && hasOverlayPermission) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )

                            // 한 줄 요약 + 접기/펴기 토글 행
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isEdgeLightingDetailExpanded = !isEdgeLightingDetailExpanded }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isEn) "Managed ${managedTriggerMins}min · Study ${studyTriggerMins}min"
                                    else "관리 ${managedTriggerMins}min · 공부 ${studyTriggerMins}min",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = if (isEdgeLightingDetailExpanded) {
                                        if (isEn) "Hide" else "접기"
                                    } else {
                                        if (isEn) "Customize" else "시간·미리보기"
                                    },
                                    fontSize = DigitsTextSize.label,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            if (isEdgeLightingDetailExpanded) {
                                // 1) 빨간불 (관리 앱)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (isEn) "Managed apps (red light) limit" else "관리 앱 (빨간불) 연속 한도",
                                        fontSize = DigitsTextSize.label,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val managedOptions = listOf(1, 5, 15, 30, 45, 60)
                                    // 6개를 한 줄에 두면 칩 안 여백 때문에 "15min"이 "15n"처럼 잘립니다. 3개씩 두 줄로 둡니다.
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    managedOptions.chunked(3).forEach { rowOptions ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowOptions.forEach { m ->
                                            val labelText = if (m == 1 || m == 5) "${m}min*" else "${m}min"
                                            FilterChip(
                                                selected = managedTriggerMins == m,
                                                onClick = {
                                                    managedTriggerMins = m
                                                    EdgeLightingOverlayManager.setManagedTriggerMinutes(context, m)
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = {
                                                    Text(
                                                        text = labelText,
                                                        fontSize = DigitsTextSize.label,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    }
                                    }
                                }

                                // 2) 초록불 (공부·독서 앱)
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = if (isEn) "Study & reading (green light) limit" else "공부·독서 앱 (초록불) 연속 한도",
                                        fontSize = DigitsTextSize.label,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    val studyOptions = listOf(1, 5, 30, 50, 60, 90)
                                    // 6개를 한 줄에 두면 칩 안 여백 때문에 "15min"이 "15n"처럼 잘립니다. 3개씩 두 줄로 둡니다.
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    studyOptions.chunked(3).forEach { rowOptions ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowOptions.forEach { m ->
                                            val labelText = if (m == 1 || m == 5) "${m}min*" else "${m}min"
                                            FilterChip(
                                                selected = studyTriggerMins == m,
                                                onClick = {
                                                    studyTriggerMins = m
                                                    EdgeLightingOverlayManager.setStudyTriggerMinutes(context, m)
                                                },
                                                modifier = Modifier.weight(1f),
                                                label = {
                                                    Text(
                                                        text = labelText,
                                                        fontSize = DigitsTextSize.label,
                                                        maxLines = 1,
                                                        softWrap = false,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                                    )
                                                }
                                            )
                                        }
                                    }
                                    }
                                    }
                                    Text(
                                        text = if (isEn) "* 1min and 5min options are for testing.\n* Repeats every half-interval (${(managedTriggerMins / 2).coerceAtLeast(1)}min / ${(studyTriggerMins / 2).coerceAtLeast(1)}min) after the first alert."
                                               else "* 1min·5min은 동작 테스트용 옵션입니다.\n* 첫 점등 이후에도 계속 사용 시 설정 시간의 절반 간격(${(managedTriggerMins / 2).coerceAtLeast(1)}min / ${(studyTriggerMins / 2).coerceAtLeast(1)}min)마다 다시 알려줍니다.",
                                        fontSize = DigitsTextSize.caption,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                // 3) 미리보기 버튼
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            EdgeLightingOverlayManager.showEdgeWarning(
                                                context,
                                                isStudyGreen = true,
                                                isPreview = true,
                                                labelText = if (isEn) "Preview · 50min focus · Rest eyes" else "미리보기 · 50min 집중 · 휴식 권장"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 4.dp,
                                            vertical = 6.dp
                                        )
                                    ) {
                                        Text(
                                            text = if (isEn) "Green Light Preview" else "초록불 미리보기",
                                            fontSize = DigitsTextSize.caption,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            EdgeLightingOverlayManager.showEdgeWarning(
                                                context,
                                                isStudyGreen = false,
                                                isPreview = true,
                                                labelText = if (isEn) "Preview · 30min continuous · Overuse" else "미리보기 · 30min 연속 · 과몰입 주의"
                                            )
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                            horizontal = 4.dp,
                                            vertical = 6.dp
                                        )
                                    ) {
                                        Text(
                                            text = if (isEn) "Red Light Preview" else "빨간불 미리보기",
                                            fontSize = DigitsTextSize.caption,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedCategoryTab == 2) item {
                TimeRuleSection(isEn = isEn)
            }

            if (selectedCategoryTab == 2) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        if (isEn) "Silent Status Alert" else "무음 상태바 경고",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = DigitsTextSize.bodyLarge
                                    )
                                    Surface(
                                        shape = DigitsShape.xs,
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = if (isEn) "Beta" else "베타",
                                            fontSize = DigitsTextSize.micro,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                                Text(
                                    if (isEn) "Silently posts a status notification without sound, vibration, or pop-ups."
                                    else "소리·진동·화면 가림 없이 상단 알림창에만 조용히 경고를 남깁니다.",
                                    fontSize = DigitsTextSize.label,
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            androidx.compose.material3.Switch(
                                checked = settings.isRapidUsageAlertEnabled,
                                onCheckedChange = { enabled ->
                                    saveRapidAlertSettings(
                                        settings.copy(isRapidUsageAlertEnabled = enabled)
                                    )
                                }
                            )
                        }

                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )

                        // 요약 행 (클릭 시 접기/펴기 토글)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSilentAlertDetailExpanded = !isSilentAlertDetailExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isEn) "${selectedCoreIndexPreset.title(true)} criteria · ${rapidAlertConfig.scoreDrop}P drop in ${rapidAlertConfig.windowMinutes}min"
                                else "${selectedCoreIndexPreset.title(isEn)} 기준 · ${rapidAlertConfig.windowMinutes}min 동안 ${rapidAlertConfig.scoreDrop}P 하락",
                                fontSize = DigitsTextSize.label,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSilentAlertDetailExpanded) {
                                    if (isEn) "Hide" else "접기"
                                } else {
                                    if (isEn) "Details" else "세부 설정"
                                },
                                fontSize = DigitsTextSize.label,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (isSilentAlertDetailExpanded) {
                            Text(
                                text = if (isEn) "Or triggers if screen time reaches ${rapidAlertConfig.usageMinutes}min or continuous use reaches ${rapidAlertConfig.continuousMinutes}min in the same window."
                                else "또는 같은 구간에서 화면 ${rapidAlertConfig.usageMinutes}min, 연속 사용 ${rapidAlertConfig.continuousMinutes}min에 도달하면 알려줍니다.",
                                fontSize = DigitsTextSize.label,
                                lineHeight = 17.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (settings.usePresetRapidAlertDefaults) {
                                Text(
                                    text = if (isEn) "Preset defaults applied" else "점수 모드 기본값 적용 중",
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isEn) "Custom values applied" else "사용자 조정값 적용 중",
                                        fontSize = DigitsTextSize.caption,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            val defaults = selectedCoreIndexPreset.defaultRapidUsageAlertConfig
                                            saveRapidAlertSettings(
                                                settings.copy(
                                                    usePresetRapidAlertDefaults = true,
                                                    rapidAlertWindowMinutes = defaults.windowMinutes,
                                                    rapidAlertScoreDrop = defaults.scoreDrop,
                                                    rapidAlertUsageMinutes = defaults.usageMinutes,
                                                    rapidAlertContinuousMinutes = defaults.continuousMinutes,
                                                    rapidAlertCooldownMinutes = defaults.cooldownMinutes
                                                )
                                            )
                                        }
                                    ) {
                                        Text(if (isEn) "Restore Preset Defaults" else "점수 모드 기본값으로 되돌리기")
                                    }
                                }
                            }

                            if (settings.isRapidUsageAlertEnabled) {
                                RapidAlertSlider(
                                    title = if (isEn) "Window Duration" else "판단 구간",
                                    value = rapidAlertConfig.windowMinutes,
                                    valueLabel = "${rapidAlertConfig.windowMinutes}min",
                                    range = 15f..60f,
                                    steps = 8,
                                    onValueCommitted = { customizeRapidAlert(windowMinutes = it) }
                                )
                                RapidAlertSlider(
                                    title = if (isEn) "Score Drop" else "점수 하락 폭",
                                    value = rapidAlertConfig.scoreDrop,
                                    valueLabel = "${rapidAlertConfig.scoreDrop}P",
                                    range = 2f..12f,
                                    steps = 9,
                                    onValueCommitted = { customizeRapidAlert(scoreDrop = it) }
                                )
                                RapidAlertSlider(
                                    title = if (isEn) "Window Screen Time" else "구간 내 사용 시간",
                                    value = rapidAlertConfig.usageMinutes,
                                    valueLabel = "${rapidAlertConfig.usageMinutes}min",
                                    range = 10f..rapidAlertConfig.windowMinutes.toFloat(),
                                    steps = (rapidAlertConfig.windowMinutes - 11).coerceAtLeast(0),
                                    onValueCommitted = { customizeRapidAlert(usageMinutes = it) }
                                )
                                RapidAlertSlider(
                                    title = if (isEn) "Continuous Use" else "연속 사용 시간",
                                    value = rapidAlertConfig.continuousMinutes,
                                    valueLabel = "${rapidAlertConfig.continuousMinutes}min",
                                    range = 15f..90f,
                                    steps = 14,
                                    onValueCommitted = { customizeRapidAlert(continuousMinutes = it) }
                                )
                                RapidAlertSlider(
                                    title = if (isEn) "Cooldown" else "다시 알림 간격",
                                    value = rapidAlertConfig.cooldownMinutes,
                                    valueLabel = "${rapidAlertConfig.cooldownMinutes}min",
                                    range = 30f..360f,
                                    steps = 10,
                                    onValueCommitted = { customizeRapidAlert(cooldownMinutes = it) }
                                )
                            }
                        }
                    }
                }
            }

            if (selectedCategoryTab == 1) item {
                var weeklyReportEnabled by remember {
                    mutableStateOf(ScoreNotificationManager.isWeeklyReportNotificationEnabled(context))
                }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
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
                                text = if (isEn) "Weekly Report Alert" else "주간 리포트 알림",
                                fontWeight = FontWeight.Bold,
                                fontSize = DigitsTextSize.bodyLarge
                            )
                            Text(
                                text = if (isEn) "Sends a summary of past week's Core Index and goal achievements every Monday morning."
                                else "매주 월요일 아침 지난주 코어 지수 및 목표 달성 요약을 알림으로 받아봅니다.",
                                fontSize = DigitsTextSize.label,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        androidx.compose.material3.Switch(
                            checked = weeklyReportEnabled,
                            onCheckedChange = { checked ->
                                weeklyReportEnabled = checked
                                ScoreNotificationManager.setWeeklyReportNotificationEnabled(context, checked)
                            }
                        )
                    }
                }
            }

            if (selectedCategoryTab == 2) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(if (isEn) "Status Bar Icon Style" else "상태바 아이콘 스타일", fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.bodyLarge)
                        val selectedIconStyle = StatusIconStyle.fromId(settings.statusIconStyleId)
                        ExposedDropdownMenuBox(
                            expanded = isStatusIconMenuExpanded,
                            onExpandedChange = {
                                isStatusIconMenuExpanded = !isStatusIconMenuExpanded
                            }
                        ) {
                            OutlinedTextField(
                                value = UiTranslator.translate(when (selectedIconStyle) {
                                    StatusIconStyle.SCORE_PROPORTION -> "점수 비율형 (기본 권장)"
                                    StatusIconStyle.BIG_NUMBER -> "코어 숫자형"
                                    StatusIconStyle.SCORE_TIER -> "전원 단계형"
                                    StatusIconStyle.NUMBER_FOCUS -> "숫자 분리형"
                                }),
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
                                    StatusIconStyle.SCORE_PROPORTION to "점수 비율형 (기본 권장)",
                                    StatusIconStyle.BIG_NUMBER to "코어 숫자형",
                                    StatusIconStyle.SCORE_TIER to "전원 단계형",
                                    StatusIconStyle.NUMBER_FOCUS to "숫자 분리형"
                                ).forEach { (style, label) ->
                                    DropdownMenuItem(
                                        text = { Text(UiTranslator.translate(label)) },
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
                                                    imageVector = Icons.Rounded.Check,
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
                                    if (isEn) "Shows large score digits inside the power ring."
                                    else "전원 링 안에 현재 점수를 크게 표시합니다."
                                StatusIconStyle.SCORE_PROPORTION ->
                                    if (isEn) "Fills the power ring proportionally with your score."
                                    else "전원 링의 채움 비율로 점수를 표시합니다."
                                StatusIconStyle.SCORE_TIER ->
                                    if (isEn) "Shows the entire power ring in your score tier color."
                                    else "전원 링 전체를 점수 구간 색상으로 표시합니다."
                                StatusIconStyle.NUMBER_FOCUS ->
                                    if (isEn) "Shows score digits right next to the power icon."
                                    else "전원 아이콘 옆에 점수 숫자를 나란히 표시합니다."
                            },
                            fontSize = DigitsTextSize.label,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 1. 점수 모드 선택
            if (selectedCategoryTab == 1 && SHOW_LEGACY_SCORE_SETTINGS) item {
                SectionHeading(
                    title = "점수 모드 선택",
                    subtitle = "생활 패턴에 맞는 기본값을 선택하고 아래에서 세부 조정합니다."
                )
            }

            if (selectedCategoryTab == 1 && SHOW_LEGACY_SCORE_SETTINGS) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
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
                                value = UiTranslator.translate(selectedPreset.title),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("현재 점수 모드") },
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
                                                    imageVector = Icons.Rounded.Check,
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
                            fontSize = DigitsTextSize.body,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }


            if (selectedCategoryTab == 0) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
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
                                text = if (isEn) (if (settings.isTrackingEnabled) "Usage tracking is active" else "Usage tracking is off")
                                else (if (settings.isTrackingEnabled) "사용 기록 추적 중" else "사용 기록 추적 중지됨"),
                                fontWeight = FontWeight.Bold,
                                fontSize = DigitsTextSize.bodyLarge
                            )
                            Text(
                                text = if (isEn) "Turning this off stops background tracking and the status notification immediately."
                                else "끄면 백그라운드 서비스와 상태바 점수 알림이 즉시 종료됩니다.",
                                fontSize = DigitsTextSize.label,
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

            // 2. 개인 목표 지수 방어선

            if (selectedCategoryTab == 1 && SHOW_LEGACY_SCORE_SETTINGS) item {
                SectionHeading(
                    title = "개인 목표 기준선",
                    subtitle = "점수 안내와 잠금 해제 기준을 본인의 생활 패턴에 맞춥니다."
                )
            }

            if (selectedCategoryTab == 1 && SHOW_LEGACY_SCORE_SETTINGS) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEn) "Minimum Score Defense: ${settings.minimumScoreDefenseLine}P" else "최저 점수 지수 방어선: ${settings.minimumScoreDefenseLine}P",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = DigitsTextSize.bodyLarge
                        )
                        Text(
                            text = "점수가 이 이하로 떨어지면 사용 균형 안내를 강조합니다.",
                            fontSize = DigitsTextSize.label,
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

            if (selectedCategoryTab == 1 && SHOW_LEGACY_SCORE_SETTINGS) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isEn) "Daily Target Unlock Limit: ${settings.targetUnlockCount}" else "일일 목표 잠금 해제 제한: ${settings.targetUnlockCount}회",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontSize = DigitsTextSize.bodyLarge
                        )
                        Text(
                            text = "이 횟수를 초과하여 스마트폰을 켤 때 페널티가 누적됩니다.",
                            fontSize = DigitsTextSize.label,
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

            // 3. 코어 지수 세부 규칙 (추후 지원 안내 카드)
            if (selectedCategoryTab == 1) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = DigitsShape.xs,
                            color = MaterialTheme.colorScheme.secondaryContainer
                        ) {
                            Text(
                                text = if (isEn) "Later" else "추후 지원",
                                fontSize = DigitsTextSize.tiny,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        Text(
                            text = if (isEn) "Custom weight fine-tuning will be available in a future update."
                            else "개인별 가중치 미세조정 기능은 데이터 축적 후 추후 업데이트에서 지원됩니다.",
                            fontSize = DigitsTextSize.label,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 4. 데이터 관리 및 백업
            if (selectedCategoryTab == 3) item {
                SectionHeading(
                    title = "데이터 관리 및 백업",
                    subtitle = "기록 보존, 암호화 백업, 복원과 삭제를 관리합니다."
                )
            }

            if (selectedCategoryTab == 3) item {
                var isImportingHistory by remember { mutableStateOf(false) }
                var importResultDialogMessage by remember { mutableStateOf<String?>(null) }
                val importScope = rememberCoroutineScope()

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEn) "Import Past History" else "지난 기록 불러오기",
                            fontWeight = FontWeight.Bold,
                            fontSize = DigitsTextSize.bodyLarge
                        )
                        Text(
                            text = if (isEn) "Imports up to 7 days of recent app usage stored on your device. Past scores are not created; Core Index begins from today."
                            else "기기에 남은 최근 최대 7일간의 앱 사용 기록을 가져옵니다. 과거 점수는 생성하지 않으며 코어 지수는 오늘부터 시작합니다.",
                            fontSize = DigitsTextSize.label,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Button(
                            onClick = {
                                if (!isImportingHistory) {
                                    isImportingHistory = true
                                    importScope.launch {
                                        val result = com.digitscore.app.data.UsageHistoryImporter.importPastHistory(context, db)
                                        importResultDialogMessage = result.message(isEn)
                                        isImportingHistory = false
                                    }
                                }
                            },
                            enabled = !isImportingHistory,
                            shape = DigitsShape.sm,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (isImportingHistory) (if (isEn) "Importing..." else "불러오는 중…") else (if (isEn) "Import Past Usage History" else "지난 사용 기록 불러오기"),
                                fontSize = DigitsTextSize.body,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                importResultDialogMessage?.let { msg ->
                    AlertDialog(
                        onDismissRequest = { importResultDialogMessage = null },
                        title = {
                            Text(
                                text = if (isEn) "Import Result" else "기록 불러오기 결과",
                                fontWeight = FontWeight.Bold,
                                fontSize = DigitsTextSize.headline
                            )
                        },
                        text = {
                            Text(
                                text = msg,
                                fontSize = DigitsTextSize.body
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = { importResultDialogMessage = null },
                                shape = DigitsShape.sm
                            ) {
                                Text(if (isEn) "OK" else "확인", fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }
            }

            if (selectedCategoryTab == 3) item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.lg,
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
                            fontSize = DigitsTextSize.label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (databaseSecurityStatus.mode == DatabaseSecurityMode.PLAINTEXT_FALLBACK) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = DigitsShape.md
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "DB 암호화 호환 모드",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = DigitsTextSize.body,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Text(
                                        "기록 손실과 실행 중단을 막기 위해 기존 DB를 그대로 사용하고 있습니다. 앱을 다시 시작하면 암호화를 재시도합니다.",
                                        fontSize = DigitsTextSize.caption,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = DigitsShape.md
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("기록 보존 방식", fontWeight = FontWeight.Medium, fontSize = DigitsTextSize.body)
                                Text(
                                    "앱별 시작·종료 상세와 5분 단위 코어 지수 표본은 30일, 시간별·날짜별 집계는 365일 보관합니다.",
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    "30일을 넘겨 상세 기록을 보관하려면 만료 전에 암호화 백업을 저장하세요.",
                                    fontSize = DigitsTextSize.caption,
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
                                Text("잠금 화면에서 상세 정보 숨기기", fontWeight = FontWeight.Medium, fontSize = DigitsTextSize.body)
                                Text(
                                    "점수·화면 시간·잠금 해제 횟수를 잠금 해제 전에는 표시하지 않습니다.",
                                    fontSize = DigitsTextSize.caption,
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
                                shape = DigitsShape.md,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                if (LocalDensity.current.fontScale <= 1.15f) Icon(imageVector = Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(text = "암호화 백업", fontSize = DigitsTextSize.body, maxLines = 1)
                            }

                            // 3) 데이터 복원
                            Button(
                                onClick = {
                                    importLauncher.launch("*/*")
                                },
                                modifier = Modifier.weight(1f),
                                shape = DigitsShape.md,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                            ) {
                                if (LocalDensity.current.fontScale <= 1.15f) Icon(imageVector = Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                                Text(text = "데이터 복원", fontSize = DigitsTextSize.body, maxLines = 1)
                            }
                        }

                        OutlinedButton(
                            onClick = { showDeleteHistoryConfirmation = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                            Text("모든 사용 기록 즉시 삭제")
                        }
                    }
                }
            }

            if (selectedCategoryTab == 3) item {
                SectionHeading(
                    title = "앱 정보",
                    subtitle = "버전, 업데이트 기록, 개인정보 처리 정책을 확인합니다."
                )
            }

            if (selectedCategoryTab == 3) item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedDetail = SettingsDetail(
                                title = "DigitsCore",
                                value = "v${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_DATE}",
                                description = "디지털 사용 습관을 전면 앱 사용 시간과 잠금 해제 기록으로 분석합니다.",
                                supportingText = "개인정보 처리 방식은 아래 개인정보 처리 안내에서 확인할 수 있습니다."
                            )
                        },
                    shape = DigitsShape.lg,
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
                                text = "DigitsCore",
                                fontWeight = FontWeight.Bold,
                                fontSize = DigitsTextSize.bodyLarge
                            )
                            Text(
                                text = "디지털 사용 습관 점수 관리",
                                fontSize = DigitsTextSize.label,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 버전과 날짜를 두 줄로: 큰 글씨에서도 잘리지 않게
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "v${BuildConfig.VERSION_NAME}",
                                    fontSize = DigitsTextSize.label,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1
                                )
                                Text(
                                    text = BuildConfig.BUILD_DATE,
                                    fontSize = DigitsTextSize.caption,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                            DetailChevron()
                        }
                    }
                }
            }

            if (selectedCategoryTab == 3) item {
                OutlinedButton(
                    onClick = { showUpdateHistory = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.md
                ) {
                    Text("업데이트 기록")
                }
            }

            if (selectedCategoryTab == 3) item {
                OutlinedButton(
                    onClick = { showPrivacyPolicy = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.md
                ) {
                    Text("개인정보 처리 안내")
                }
            }

            if (selectedCategoryTab == 3) item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showBugReport = true },
                        modifier = Modifier.weight(1f),
                        shape = DigitsShape.md
                    ) {
                        Icon(Icons.Rounded.BugReport, contentDescription = null)
                        Text("버그 리포트", modifier = Modifier.padding(start = 6.dp), fontSize = DigitsTextSize.label)
                    }
                    OutlinedButton(
                        onClick = { showDiagnosticsModal = true },
                        modifier = Modifier.weight(1f),
                        shape = DigitsShape.md
                    ) {
                        Icon(Icons.Rounded.Speed, contentDescription = null)
                        Text("측정 상태 진단", modifier = Modifier.padding(start = 6.dp), fontSize = DigitsTextSize.label)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
            }
        }
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }

    if (showUpdateHistory) {
        UpdateHistoryDialog(onDismiss = { showUpdateHistory = false })
    }

    if (showBugReport) {
        BugReportDialog(
            diagnostics = measurementDiagnostics,
            trackingEnabled = settings.isTrackingEnabled,
            notificationEnabled = settings.isNotificationEnabled,
            databaseMode = databaseSecurityStatus.mode.name.lowercase(),
            onDismiss = { showBugReport = false }
        )
    }

    if (showDiagnosticsModal) {
        MeasurementDiagnosticsDialog(
            diagnostics = measurementDiagnostics,
            onDismiss = { showDiagnosticsModal = false }
        )
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
                Text("앱별 365일 기록, 점수 기록과 잠금 해제 통계가 삭제되고 추적이 중지됩니다. 앱 등급과 점수 설정은 유지되며 이 작업은 되돌릴 수 없습니다.")
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
private fun RapidAlertSlider(
    title: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueCommitted: (Int) -> Unit
) {
    var draftValue by remember(value, range.start, range.endInclusive) {
        mutableFloatStateOf(value.toFloat().coerceIn(range.start, range.endInclusive))
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, fontSize = DigitsTextSize.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                valueLabel,
                fontSize = DigitsTextSize.label,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = draftValue,
            onValueChange = { draftValue = it },
            onValueChangeFinished = { onValueCommitted(draftValue.toInt()) },
            valueRange = range,
            steps = steps
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
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    val isValid = password.length >= 4 && (!confirmPassword || password == confirmation)

    // 안드로이드 OS 자동완성(Samsung Pass, Google Password Manager)이 백업 암호화 키를 가로채지 못하도록 명시적 차단
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val view = LocalView.current
        DisposableEffect(view) {
            val original = view.importantForAutofill
            view.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            onDispose {
                view.importantForAutofill = original
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (isEn) "Passwords are not stored in the app or server and are only used for backup encryption (PBKDF2 + AES-256-GCM). Data cannot be recovered if lost."
                    else "비밀번호는 앱이나 서버에 저장되지 않고 백업 파일 암호화(PBKDF2 + AES-256-GCM)에만 사용됩니다. 분실하면 복원할 수 없습니다.",
                    fontSize = DigitsTextSize.label,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isEn) "Password · 4+ characters (e.g. 4-digit PIN)" else "암호화 비밀번호 · 4자 이상 (숫자 4자리 등)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = if (confirmPassword) ImeAction.Next else ImeAction.Done,
                        autoCorrect = false
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                if (confirmPassword) {
                    OutlinedTextField(
                        value = confirmation,
                        onValueChange = { confirmation = it },
                        label = { Text(if (isEn) "Confirm Password" else "비밀번호 확인") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                            autoCorrect = false
                        ),
                        isError = confirmation.isNotEmpty() && password != confirmation,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(password) }, enabled = isValid) { Text(if (isEn) "Confirm" else "확인") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(if (isEn) "Cancel" else "취소") } }
    )
}
