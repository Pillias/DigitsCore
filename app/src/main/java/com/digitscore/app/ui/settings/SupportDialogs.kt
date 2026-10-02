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
import androidx.compose.material.icons.filled.ContentCopy
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
                        "문제가 생긴 상황과 재현 순서를 적어주세요. 아래 보고서를 확인한 뒤 직접 공유합니다.",
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
            Button(onClick = { shareBugReport(context, report) }) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("공유", modifier = Modifier.padding(start = 8.dp))
            }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("닫기") } }
    )
}

private fun shareBugReport(context: Context, report: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "DigitsCore v${BuildConfig.VERSION_NAME} (${BuildConfig.BUILD_DATE}) bug report")
        putExtra(Intent.EXTRA_TEXT, report)
    }
    context.startActivity(
        Intent.createChooser(intent, UiTranslator.translate("버그 리포트 공유"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

@Composable
internal fun ReleaseReadinessDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("출시 준비 체크리스트", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        "완료 ${ReleaseReadiness.count(ReadinessStatus.COMPLETE)}/16 · 검증 준비 ${ReleaseReadiness.count(ReadinessStatus.READY_TO_VALIDATE)} · 관리자 입력 ${ReleaseReadiness.count(ReadinessStatus.OWNER_ACTION)} · 외부 검증 ${ReleaseReadiness.count(ReadinessStatus.EXTERNAL_VALIDATION)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item { Text("반드시 필요한 것", fontWeight = FontWeight.Bold) }
                items(ReleaseReadiness.required, key = { it.number }) { item ->
                    ReadinessRow(item)
                }
                item { Text("상품성을 위해 필요한 것", fontWeight = FontWeight.Bold) }
                items(ReleaseReadiness.product, key = { it.number }) { item ->
                    ReadinessRow(item)
                }
                item {
                    Text(
                        "기기·사용자·Play Console이 필요한 항목은 코드만으로 완료 처리하지 않습니다. 저장소 docs/RELEASE_READINESS.md에 실행 절차와 증빙 위치를 정리했습니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } }
    )
}

@Composable
private fun ReadinessRow(item: ReleaseReadinessItem) {
    val statusLabel = when (item.status) {
        ReadinessStatus.COMPLETE -> "완료"
        ReadinessStatus.READY_TO_VALIDATE -> "검증 준비"
        ReadinessStatus.OWNER_ACTION -> "관리자 입력"
        ReadinessStatus.EXTERNAL_VALIDATION -> "외부 검증"
    }
    val statusColor = when (item.status) {
        ReadinessStatus.COMPLETE -> MaterialTheme.colorScheme.primary
        ReadinessStatus.READY_TO_VALIDATE -> MaterialTheme.colorScheme.tertiary
        ReadinessStatus.OWNER_ACTION -> MaterialTheme.colorScheme.error
        ReadinessStatus.EXTERNAL_VALIDATION -> MaterialTheme.colorScheme.secondary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${item.number}. ${item.title}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(statusLabel, color = statusColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Text(item.detail, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
