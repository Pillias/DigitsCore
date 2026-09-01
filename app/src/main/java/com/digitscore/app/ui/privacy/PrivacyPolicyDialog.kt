package com.digitscore.app.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("개인정보 처리 안내", fontWeight = FontWeight.Bold)
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
                    title = "처리하는 정보",
                    body = "DigitsCore는 사용자가 사용 정보 접근 권한을 허용한 경우 앱 이름, 패키지 이름, 화면에 표시된 앱의 사용 시간과 기기 잠금 해제 횟수의 근사치를 읽습니다."
                )
                PrivacySection(
                    title = "이용 목적",
                    body = "해당 정보는 디지털 사용 습관을 계산하고 점수·통계를 기기 화면과 알림에 표시하는 데만 사용합니다."
                )
                PrivacySection(
                    title = "저장 및 전송",
                    body = "분석 결과와 설정은 SQLCipher로 암호화된 기기 내부 DB에 저장되고 DB 암호는 Android Keystore로 보호됩니다. OS 자동 백업과 기기 이전에서는 제외됩니다. 서버 전송은 없으며, 수동 백업은 사용자 비밀번호로 AES-256-GCM 암호화됩니다."
                )
                PrivacySection(
                    title = "사용자 선택권",
                    body = "앱별 기록은 기본 365일 보관되며 30·90·180·365일 중 선택할 수 있습니다. 잠금 화면 상세 숨김, 추적 중지와 모든 사용 기록 즉시 삭제를 지원하며 Android 설정에서 권한을 언제든 철회할 수 있습니다."
                )
                PrivacySection(
                    title = "문의",
                    body = "개인정보 관련 문의는 Google Play 스토어에 표시된 개발자 연락처를 이용해 주세요."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("확인")
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
