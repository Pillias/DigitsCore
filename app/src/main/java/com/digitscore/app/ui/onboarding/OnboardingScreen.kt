package com.digitscore.app.ui.onboarding

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.digitscore.app.R
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.ui.components.ResponsiveContent
import com.digitscore.app.ui.privacy.PrivacyPolicyDialog
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.ScoreGreenText

/**
 * 첫 실행 화면. 로고와 이름만 보여 주고, [시작하기]를 누르면 꼭 필요한 권한만 아래 시트로 묻습니다.
 * 화면 신호등(오버레이)은 설정에서 그 기능을 켤 때, 배터리 예외는 설정 > 일반에서 안내합니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    onNavigateToDashboard: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
    val asksNotification = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    var hasUsagePermission by remember { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }
    var hasNotificationPermission by remember { mutableStateOf(notificationGranted(context)) }
    // 설정 화면에 다녀온 뒤에도 시트가 다시 열려 있도록 저장합니다.
    var showPermissionSheet by rememberSaveable { mutableStateOf(false) }
    // 사용 정보 접근 화면에 다녀왔는데도 꺼져 있으면, 그때만 '제한된 설정' 안내를 보여 줍니다.
    var usageSettingsVisited by rememberSaveable { mutableStateOf(false) }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasNotificationPermission = granted }
    )

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsagePermission = UsageStatsHelper.hasUsageStatsPermission(context)
                hasNotificationPermission = notificationGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ResponsiveContent(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .background(Color(0xFF151F2E), DigitsShape.xl),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp)
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "DigitsCore",
                    fontSize = DigitsTextSize.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (isEn) "Your phone use and rest in one score" else "폰 사용과 휴식을 점수 하나로",
                    fontSize = DigitsTextSize.emphasis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Button(
                onClick = {
                    if (hasUsagePermission && (!asksNotification || hasNotificationPermission)) {
                        onNavigateToDashboard()
                    } else {
                        showPermissionSheet = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = DigitsShape.lg
            ) {
                Text(
                    text = if (isEn) "Get started" else "시작하기",
                    fontSize = DigitsTextSize.emphasis,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .clickable(role = Role.Button) { showPrivacyPolicy = true }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Text(
                    text = if (isEn) "Your history stays on this device" else "기록은 기기 안에만 저장됩니다",
                    fontSize = DigitsTextSize.label,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }

    if (showPermissionSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPermissionSheet = false },
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = when {
                        !asksNotification -> if (isEn) "Allow one thing" else "이것만 허용해 주세요"
                        isEn -> "Allow two things"
                        else -> "두 가지만 허용해 주세요"
                    },
                    fontSize = DigitsTextSize.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                PermissionRow(
                    icon = Icons.Rounded.QueryStats,
                    title = if (isEn) "Usage access" else "사용 정보 접근",
                    badge = if (isEn) "Required" else "필수",
                    body = if (isEn) "Counts which apps you use and for how long" else "어떤 앱을 얼마나 썼는지 셉니다",
                    hint = if (usageSettingsVisited && !hasUsagePermission) {
                        if (isEn) "Grayed out? App info ⋮ > Allow restricted settings"
                        else "회색으로 막혀 있으면 앱 정보 ⋮ > 제한된 설정 허용"
                    } else null,
                    granted = hasUsagePermission,
                    actionLabel = if (isEn) "Allow" else "허용",
                    onAction = {
                        usageSettingsVisited = true
                        launchUsageAccessSettings(context)
                    }
                )
                if (asksNotification) {
                    PermissionRow(
                        icon = Icons.Rounded.Notifications,
                        title = if (isEn) "Notifications" else "알림",
                        badge = if (isEn) "Recommended" else "권장",
                        body = if (isEn) "Shows your index in the status bar" else "상태바에 지수를 보여 줍니다",
                        hint = null,
                        granted = hasNotificationPermission,
                        actionLabel = if (isEn) "Allow" else "허용",
                        onAction = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        showPermissionSheet = false
                        onNavigateToDashboard()
                    },
                    enabled = hasUsagePermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = DigitsShape.lg
                ) {
                    Text(
                        text = if (isEn) "Continue" else "계속",
                        fontSize = DigitsTextSize.emphasis,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showPrivacyPolicy) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicy = false })
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    badge: String,
    body: String,
    hint: String?,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = title,
                    fontSize = DigitsTextSize.title,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = badge,
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                text = body,
                fontSize = DigitsTextSize.body,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (hint != null) {
                Text(
                    text = hint,
                    fontSize = DigitsTextSize.label,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = ScoreGreenText
            )
        } else {
            FilledTonalButton(onClick = onAction, shape = DigitsShape.md) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun notificationGranted(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true

internal fun launchUsageAccessSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            })
        }
    }
}

internal fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
    return powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true
}

/** 배터리 최적화 예외 목록 화면을 엽니다. (직접 예외 요청 권한은 사용하지 않습니다) */
internal fun launchBatteryOptimizationSettings(context: Context) {
    val intents = listOf(
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    )
    for (intent in intents) {
        val opened = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (opened) return
    }
}
