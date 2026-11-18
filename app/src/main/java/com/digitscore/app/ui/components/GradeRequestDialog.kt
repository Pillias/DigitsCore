package com.digitscore.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitscore.app.BuildConfig
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.model.AppGrade
import com.digitscore.app.model.GradeChangeRequest
import com.digitscore.app.ui.theme.DigitsTextSize

/**
 * 등급 변경 요청 창. 바라는 등급을 고르면 메일 앱이 열리고, 사용자가 내용을 확인한 뒤 직접 보냅니다.
 * 앱 안에서 등급이 바뀌는 것은 없습니다.
 */
@Composable
fun GradeRequestDialog(
    appName: String,
    packageName: String,
    current: AppGrade,
    initialDesired: AppGrade? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val english = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    var desired by remember { mutableStateOf(initialDesired?.takeIf { it != current } ?: AppGrade.entries.first { it != current }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(GradeChangeRequest.buttonLabel(english), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(appName, fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.body)
                Text(
                    (if (english) "Current grade: " else "지금 등급: ") + current.label(english),
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    if (english) "Requested grade" else "바라는 등급",
                    fontSize = DigitsTextSize.label,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )
                AppGrade.entries.filter { it != current }.forEach { grade ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { desired = grade },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = desired == grade, onClick = { desired = grade })
                        Column {
                            Text(grade.label(english), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Medium)
                            Text(grade.description, fontSize = DigitsTextSize.tiny, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
                Text(
                    GradeChangeRequest.explanation(english),
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                openGradeRequestMail(context, appName, packageName, current, desired, english)
                onDismiss()
            }) { Text(if (english) "Write email" else "메일 쓰기", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (english) "Cancel" else "취소") }
        }
    )
}

fun openGradeRequestMail(
    context: Context,
    appName: String,
    packageName: String,
    current: AppGrade,
    desired: AppGrade,
    english: Boolean
) {
    val message = GradeChangeRequest.build(appName, packageName, current, desired, BuildConfig.VERSION_NAME, english)
    val uri = Uri.parse(
        "mailto:${GradeChangeRequest.EMAIL}?subject=${Uri.encode(message.subject)}&body=${Uri.encode(message.body)}"
    )
    val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
        putExtra(Intent.EXTRA_EMAIL, arrayOf(GradeChangeRequest.EMAIL))
        putExtra(Intent.EXTRA_SUBJECT, message.subject)
        putExtra(Intent.EXTRA_TEXT, message.body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        // 메일 앱이 없으면 내용을 복사하고 주소를 알려 줍니다.
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(message.subject, "${message.subject}\n\n${message.body}"))
        Toast.makeText(
            context,
            if (english) "No mail app found. The request was copied. Please send it to ${GradeChangeRequest.EMAIL}."
            else "메일 앱이 없어 요청 내용을 복사했습니다. ${GradeChangeRequest.EMAIL}로 보내 주세요.",
            Toast.LENGTH_LONG
        ).show()
    }
}
