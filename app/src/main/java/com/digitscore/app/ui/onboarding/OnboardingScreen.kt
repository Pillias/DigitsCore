package com.digitscore.app.ui.onboarding

import com.digitscore.app.ui.theme.ScoreGreenText
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.DigitsShape
import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Highlight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.digitscore.app.R
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.ResponsiveContent
import com.digitscore.app.ui.privacy.PrivacyPolicyDialog
import com.digitscore.app.ui.theme.ScoreGreen
import java.util.Locale

@Composable
fun OnboardingScreen(
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH

    var hasUsagePermission by remember {
        mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context))
    }
    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else true
        )
    }
    var hasOverlayPermission by remember {
        mutableStateOf(Settings.canDrawOverlays(context))
    }
    var isBatteryOptimizationIgnored by remember {
        mutableStateOf(isIgnoringBatteryOptimizations(context))
    }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    var pendingDashboardNavigation by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasNotificationPermission = isGranted
            if (pendingDashboardNavigation) {
                pendingDashboardNavigation = false
                onNavigateToDashboard()
            }
        }
    )

    // 화면 포커스 복귀 시 권한 상태 재확인
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = UsageStatsHelper.hasUsageStatsPermission(context)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                hasOverlayPermission = Settings.canDrawOverlays(context)
                isBatteryOptimizationIgnored = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ResponsiveContent(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 로고 & 타이틀 헤더 (표준 머티리얼3 스타일)
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(Color(0xFF151F2E), DigitsShape.lg),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "DigitsCore",
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.onboarding_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(R.string.onboarding_permissions_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
                )

                // 권한 안내 카드 1: 사용 정보 접근 권한 (필수)
                StandardPermissionCard(
                    icon = Icons.Rounded.QueryStats,
                    title = stringResource(R.string.usage_access_title),
                    badgeText = if (isEn) "Required" else "필수",
                    isRequired = true,
                    description = stringResource(R.string.usage_access_body),
                    hint = if (!hasUsagePermission) stringResource(R.string.usage_access_guide) else null,
                    isGranted = hasUsagePermission,
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { launchUsageAccessSettings(context) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 권한 안내 카드 2: 알림 권한 (권장)
                StandardPermissionCard(
                    icon = Icons.Rounded.Notifications,
                    title = stringResource(R.string.notification_permission_title),
                    badgeText = if (isEn) "Recommended" else "권장",
                    isRequired = false,
                    description = stringResource(R.string.notification_permission_body),
                    hint = null,
                    isGranted = hasNotificationPermission,
                    actionLabel = stringResource(R.string.action_allow),
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 권한 안내 카드 3: 화면 테두리 알림 (선택)
                StandardPermissionCard(
                    icon = Icons.Rounded.Highlight,
                    title = stringResource(R.string.overlay_permission_title),
                    badgeText = if (isEn) "Optional" else "선택",
                    isRequired = false,
                    description = stringResource(R.string.overlay_permission_body),
                    hint = null,
                    isGranted = hasOverlayPermission,
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { launchOverlaySettings(context) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 권한 안내 카드 4: 배터리 최적화 예외 (권장)
                // Samsung 등은 절전 정책으로 백그라운드 추적을 종료할 수 있어, 예외 등록을 안내합니다.
                StandardPermissionCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = if (isEn) "Keep tracking running" else "백그라운드 추적 유지",
                    badgeText = if (isEn) "Recommended" else "권장",
                    isRequired = false,
                    description = if (isEn) "Some phones stop background apps to save battery, which breaks the Core Index flow. Allow DigitsCore to run without battery optimization."
                        else "일부 기기는 배터리 절약을 위해 백그라운드 앱을 종료해 코어 지수 흐름이 끊길 수 있습니다. DigitsCore를 배터리 최적화에서 제외해 주세요.",
                    hint = if (!isBatteryOptimizationIgnored && Build.MANUFACTURER.equals("samsung", ignoreCase = true)) {
                        if (isEn) "Samsung: Settings › Battery › Background usage limits › Never sleeping apps › add DigitsCore"
                        else "Samsung: 설정 › 배터리 › 백그라운드 사용 제한 › 절전 예외 앱 › DigitsCore 추가"
                    } else null,
                    isGranted = isBatteryOptimizationIgnored,
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { launchBatteryOptimizationSettings(context) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 개인정보 및 암호화 안심 배너 (컴팩트 카드)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyPolicy = true },
                    shape = DigitsShape.md,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.privacy_secure_badge),
                            fontSize = DigitsTextSize.label,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.privacy_more),
                            fontSize = DigitsTextSize.caption,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        DetailChevron(tint = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // DigitsCore 핵심 방식 안내 (컴팩트 2포인트 카드)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = DigitsShape.md,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = if (isEn) "How DigitsCore Works" else "DigitsCore의 핵심 방식",
                            fontWeight = FontWeight.Bold,
                            fontSize = DigitsTextSize.label,
                            color = MaterialTheme.colorScheme.outline
                        )
                        FeatureItem(
                            icon = Icons.Rounded.Autorenew,
                            title = if (isEn) "Continuous 75P Balance Index" else "자정 리셋 없는 75P 균형 지수",
                            desc = if (isEn) "Tracks your ongoing usage flow without midnight resets." else "자정에 초기화되지 않고 연속된 사용 흐름을 반영합니다."
                        )
                        FeatureItem(
                            icon = Icons.Rounded.Restore,
                            title = if (isEn) "30min Acceleration & Break Recovery" else "30min 연속 가속과 휴식 회복",
                            desc = if (isEn) "30+ min managed use accelerates loss; screen-off breaks recover score." else "관리 앱 30min 초과 시 가속 감점되며, 화면을 끄고 쉬면 빠르게 회복됩니다."
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // 하단 스마트 액션 버튼 (항상 활성화 & 상황별 원터치 진행)
            Button(
                onClick = {
                    if (!hasUsagePermission) {
                        launchUsageAccessSettings(context)
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission && !pendingDashboardNavigation) {
                        pendingDashboardNavigation = true
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        onNavigateToDashboard()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = DigitsShape.lg,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (hasUsagePermission) {
                        stringResource(R.string.start_digitscore)
                    } else {
                        stringResource(R.string.action_grant_required)
                    },
                    fontSize = DigitsTextSize.emphasis,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }
}

@Composable
private fun StandardPermissionCard(
    icon: ImageVector,
    title: String,
    badgeText: String,
    isRequired: Boolean,
    description: String,
    hint: String?,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isGranted, onClick = onAction),
        shape = DigitsShape.lg,
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (isGranted) ScoreGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isGranted) ScoreGreenText else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = DigitsTextSize.bodyLarge,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isRequired) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                shape = DigitsShape.xs
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = DigitsTextSize.tiny,
                            fontWeight = FontWeight.Bold,
                            color = if (isRequired) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
                Text(
                    text = description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = DigitsTextSize.label,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (!hint.isNullOrBlank()) {
                    Text(
                        text = hint,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = DigitsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = stringResource(R.string.granted),
                        tint = ScoreGreenText,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.granted),
                        color = ScoreGreenText,
                        fontSize = DigitsTextSize.label,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            } else {
                FilledTonalButton(
                    onClick = onAction,
                    shape = DigitsShape.md,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = DigitsTextSize.label,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = DigitsTextSize.label,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = DigitsTextSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

private fun launchUsageAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            val appDetailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(appDetailsIntent)
        }
    }
}

private fun launchOverlaySettings(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallback = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            context.startActivity(fallback)
        } catch (_: Exception) {}
    }
}

private fun isIgnoringBatteryOptimizations(context: android.content.Context): Boolean {
    val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

/** 배터리 최적화 예외 목록 화면을 엽니다. (직접 예외 요청 권한은 사용하지 않습니다) */
private fun launchBatteryOptimizationSettings(context: android.content.Context) {
    val intents = listOf(
        android.content.Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        android.content.Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.parse("package:${context.packageName}"))
    )
    for (intent in intents) {
        val opened = runCatching {
            context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (opened) return
    }
}
