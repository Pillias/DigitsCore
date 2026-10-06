package com.digitscore.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Share

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.BuildConfig
import com.digitscore.app.data.MeasurementDiagnostics
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.Text
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.support.BugReportBuilder
import com.digitscore.app.support.BugReportEnvironment
import com.digitscore.app.support.ReadinessStatus
import com.digitscore.app.support.ReleaseReadiness
import com.digitscore.app.support.ReleaseReadinessItem

@Composable
internal fun BugReportDialog(
    diagnostics: MeasurementDiagnostics,
    trackingEnabled: Boolean,
    notificationEnabled: Boolean,
    databaseMode: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var description by remember { mutableStateOf("") }
    val report = remember(
        description,
        diagnostics,
        trackingEnabled,
        notificationEnabled,
        databaseMode
    ) {
        BugReportBuilder.build(
            environment = BugReportEnvironment(
                versionName = "${BuildConfig.VERSION_NAME} · ${BuildConfig.BUILD_DATE}",
                versionCode = BuildConfig.VERSION_CODE,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                androidVersion = Build.VERSION.RELEASE,
                sdkInt = Build.VERSION.SDK_INT,
                languageCode = AppLocale.currentLanguage(context),
                trackingEnabled = trackingEnabled,
                notificationEnabled = notificationEnabled,
                usageAccessGranted = UsageStatsHelper.hasUsageStatsPermission(context),
                databaseMode = databaseMode,
                diagnostics = diagnostics,
                generatedAtMillis = System.currentTimeMillis()
            ),
            description = description
        )
    }

    var screenshotUris by remember { mutableStateOf<List<android.net.Uri>>(emptyList()) }
    val screenshotPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents()
    ) { uris: List<android.net.Uri> ->
        if (uris.isNotEmpty()) {
            val combined = (screenshotUris + uris).distinct().take(5)
            screenshotUris = combined
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("버그 리포트", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        "문제가 생긴 상황과 재현 순서를 적어주세요. 공식 지원 이메일(DigitsCore@gmail.com)로 진단 보고서와 함께 발송됩니다.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it.take(2_000) },
                        label = { Text("문제 상황과 재현 순서") },
                        supportingText = { Text("예: 위젯이 오전 10시 이후 갱신되지 않음") },
                        minLines = 4,
                        maxLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("스크린샷 첨부 (최대 5장)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(
                                    if (screenshotUris.isNotEmpty()) "${screenshotUris.size}/5장 첨부됨"
                                    else "증상을 보여주는 화면 캡처 이미지를 추가할 수 있습니다.",
                                    fontSize = 11.sp,
                                    color = if (screenshotUris.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (screenshotUris.isNotEmpty()) {
                                    OutlinedButton(onClick = { screenshotUris = emptyList() }) {
                                        Text("전체 삭제")
                                    }
                                }
                                if (screenshotUris.size < 5) {
                                    OutlinedButton(onClick = { screenshotPicker.launch("image/*") }) {
                                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                                        Text("추가", modifier = Modifier.padding(start = 4.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Text("첨부되는 진단 정보", fontWeight = FontWeight.SemiBold)
                    Text(
                        "앱 버전·기기 모델·Android 버전·권한 상태·측정 성능만 포함합니다. 앱 목록, 사용 이력, 점수 기록, 알림 내용, 계정 및 기기 식별자는 포함하지 않습니다.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    SelectionContainer {
                        Text(
                            report,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(12.dp),
                            fontSize = 10.sp,
                            lineHeight = 15.sp
                        )
                    }
                }
                item {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("DigitsCore bug report", report))
                            Toast.makeText(
                                context,
                                UiTranslator.translate("버그 리포트를 복사했습니다."),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                        Text("리포트 복사", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { sendBugReportEmail(context, report, screenshotUris) }) {
                Icon(Icons.Default.Email, contentDescription = null)
                Text("이메일 발송", modifier = Modifier.padding(start = 8.dp))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("닫기") } }
    )
}

private const val SUPPORT_EMAIL = "DigitsCore@gmail.com"

private fun sendBugReportEmail(context: Context, report: String, screenshotUris: List<android.net.Uri>) {
    val subject = "DigitsCore v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_DATE}) bug report"
    val intent = when {
        screenshotUris.size > 1 -> {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(screenshotUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        screenshotUris.size == 1 -> {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_STREAM, screenshotUris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        else -> {
            Intent(Intent.ACTION_SENDTO).apply {
                data = android.net.Uri.parse("mailto:$SUPPORT_EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
            }
        }
    }

    try {
        context.startActivity(
            Intent.createChooser(intent, UiTranslator.translate("이메일 앱 선택"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (e: Exception) {
        // 이메일 앱이 없는 경우 일반 공유로 폴백
        val shareIntent = if (screenshotUris.size > 1) {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(screenshotUris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else if (screenshotUris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
                putExtra(Intent.EXTRA_STREAM, screenshotUris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, report)
            }
        }
        context.startActivity(
            Intent.createChooser(shareIntent, UiTranslator.translate("버그 리포트 공유"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}



@Composable
internal fun MeasurementDiagnosticsDialog(
    diagnostics: MeasurementDiagnostics,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("측정 정확도와 처리 비용", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    diagnostics.foregroundCoveragePercent?.let { "전면 앱 포착률 ${it}%" } ?: "포착률 계산 중",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "최근 조회 ${diagnostics.lastQueryWindowMillis / 1_000}초 · 이벤트 ${diagnostics.queriedEventCount}개 · ${diagnostics.lastQueryDurationMillis}ms\n" +
                    "오늘 ${diagnostics.cyclesToday}회 측정 · 조회 ${diagnostics.totalQueryDurationTodayMillis}ms · CPU ${diagnostics.totalCpuTodayMillis}ms\n" +
                    "실제 이벤트 기준 최근 24시간 언락 ${diagnostics.rolling24HourUnlockCount}회",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "포착률은 화면 ON·잠금 해제 시간 중 전면 앱을 특정한 비율입니다. CPU 시간은 측정기의 처리 비용이며 배터리 비율과 같지 않습니다.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("확인") }
        }
    )
}
