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
                    body = "분석 결과와 설정은 사용자의 기기에만 저장됩니다. DigitsCore는 서버로 사용 기록을 전송하거나 개발자에게 수집·판매하지 않습니다. 사용자가 백업 기능을 실행한 경우에만 선택한 앱을 통해 백업 파일을 직접 내보냅니다."
                )
                PrivacySection(
                    title = "사용자 선택권",
                    body = "설정에서 추적을 끄거나 상태바 알림의 ‘추적 중지’를 누를 수 있습니다. Android 설정에서 사용 정보 접근 권한을 언제든 철회할 수 있으며, 앱을 삭제하면 기기에 저장된 앱 데이터도 삭제됩니다."
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
