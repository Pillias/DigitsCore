package com.digitscore.app.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import com.digitscore.app.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.digitscore.app.R

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.privacy_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrivacySection(
                    title = stringResource(R.string.privacy_data_title),
                    body = stringResource(R.string.privacy_data_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_purpose_title),
                    body = stringResource(R.string.privacy_purpose_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_storage_title),
                    body = stringResource(R.string.privacy_storage_body)
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_choice_title),
                    body = stringResource(R.string.privacy_choice_body)
                )
                PrivacySection(
                    title = if (java.util.Locale.getDefault().language == "en") "Optional step evidence" else "선택적 걸음 보조 판단",
                    body = if (java.util.Locale.getDefault().language == "en")
                        "With your permission, the hardware step counter supports waking detection. Raw samples stay in memory only; location and step history are not stored or transmitted. Disable this in settings or revoke Physical activity permission. Cumulative score and waking confirmations are stored locally until you delete usage history."
                    else "선택 동의하면 하드웨어 걸음 센서를 기상 보조 판단에 사용합니다. 원시 값은 메모리에서만 비교하고 위치·걸음 이력은 저장하거나 전송하지 않습니다. 설정 또는 신체 활동 권한에서 끌 수 있습니다. 누적 점수와 기상 확인은 기기 내부에 보관하며 전체 기록 삭제 시 함께 삭제합니다."
                )
                PrivacySection(
                    title = stringResource(R.string.privacy_contact_title),
                    body = stringResource(R.string.privacy_contact_body)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        }
    )
}

@Composable
private fun PrivacySection(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
