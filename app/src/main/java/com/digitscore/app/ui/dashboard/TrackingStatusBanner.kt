package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.ui.onboarding.launchUsageAccessSettings
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize
import kotlinx.coroutines.delay

/** 대시보드 맨 위에 띄우는 측정 상태. 정상일 때는 아무것도 그리지 않습니다. */
internal enum class TrackingProblem { NONE, PERMISSION_OFF, SERVICE_STOPPED }

/**
 * 사용자가 추적을 켜 둔 상태에서만 문제를 판단합니다. 직접 끈 경우는 문제로 보지 않습니다.
 * 권한이 우선입니다. 권한이 없으면 서비스가 돌아도 기록할 수 없기 때문입니다.
 */
internal fun resolveTrackingProblem(
    trackingEnabled: Boolean,
    hasPermission: Boolean,
    serviceRunning: Boolean
): TrackingProblem = when {
    !trackingEnabled -> TrackingProblem.NONE
    !hasPermission -> TrackingProblem.PERMISSION_OFF
    !serviceRunning -> TrackingProblem.SERVICE_STOPPED
    else -> TrackingProblem.NONE
}

/** 측정 상태를 지켜보다가 문제가 생기면 돌려줍니다. 목록에 빈 칸이 생기지 않게 배너와 분리했습니다. */
@Composable
internal fun rememberTrackingProblem(): TrackingProblem {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val serviceRunning by ScoreRepository.isServiceRunning.collectAsState()
    val settings by remember {
        DigitsDatabase.getInstance(context).settingsDao().getSettingsFlow()
    }.collectAsState(initial = null)
    var hasPermission by remember { mutableStateOf(UsageStatsHelper.hasUsageStatsPermission(context)) }

    // 설정 화면에서 권한을 켜고 돌아오면 바로 사라지도록 화면 복귀 때 다시 확인합니다.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = UsageStatsHelper.hasUsageStatsPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 앱을 켜는 순간에는 서비스가 막 시작되는 중이라 잠깐 기다린 뒤에만 '멈춤'으로 봅니다.
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(5_000L)
        settled = true
    }

    val problem = resolveTrackingProblem(
        trackingEnabled = settings?.isTrackingEnabled == true,
        hasPermission = hasPermission,
        serviceRunning = serviceRunning || !settled
    )
    // 서비스가 멈춘 것은 사용자에게 묻지 않고 바로 다시 시작합니다. 앱이 화면에 떠 있으므로 OS도 허용합니다.
    LaunchedEffect(problem) {
        if (problem == TrackingProblem.SERVICE_STOPPED) {
            runCatching { TrackerForegroundService.start(context.applicationContext) }
        }
    }
    // 배너는 사용자만 고칠 수 있는 권한 꺼짐일 때만 보입니다.
    return if (problem == TrackingProblem.PERMISSION_OFF) problem else TrackingProblem.NONE
}

@Composable
internal fun TrackingStatusBanner(problem: TrackingProblem, modifier: Modifier = Modifier) {
    if (problem == TrackingProblem.NONE) return
    val context = LocalContext.current
    val isEn = AppLocale.isEnglish(context)
    val title = if (isEn) "Core Index is not being measured" else "코어 지수 측정이 멈췄습니다"
    val body = if (isEn) "Usage access is off. Tap to allow it again." else "사용 정보 접근이 꺼져 있습니다. 탭해서 다시 허용해 주세요."

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { launchUsageAccessSettings(context) },
        shape = DigitsShape.md,
        color = MaterialTheme.colorScheme.errorContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    fontSize = DigitsTextSize.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Text(
                    text = body,
                    fontSize = DigitsTextSize.label,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }
}
