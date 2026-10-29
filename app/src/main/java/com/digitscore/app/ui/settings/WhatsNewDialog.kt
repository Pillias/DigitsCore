package com.digitscore.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.release.ReleaseNote
import com.digitscore.app.ui.theme.DigitsTextSize

/** 업데이트 후 첫 실행에 한 번: 핵심 3줄과 '자세히'(업데이트 기록). */
@Composable
fun WhatsNewDialog(note: ReleaseNote, english: Boolean, onMore: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (english) "What's new in v${note.version}" else "v${note.version} 업데이트",
                fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.title
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (note.summary.isNotBlank()) {
                    Text(bold(note.summary), fontSize = DigitsTextSize.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                note.items.take(3).forEach { item ->
                    Row {
                        Text("•", fontSize = DigitsTextSize.body, modifier = Modifier.padding(end = 6.dp))
                        Text(bold(item), fontSize = DigitsTextSize.body, lineHeight = 20.sp)
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onMore) { Text(if (english) "More" else "자세히") } },
        confirmButton = { TextButton(onClick = onDismiss) { Text(if (english) "OK" else "확인") } }
    )
}
