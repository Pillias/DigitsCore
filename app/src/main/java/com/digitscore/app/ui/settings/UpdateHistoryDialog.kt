package com.digitscore.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.BuildConfig
import com.digitscore.app.data.release.ReleaseNote
import com.digitscore.app.data.release.ReleaseNotes
import com.digitscore.app.i18n.AppLocale
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.ui.draw.rotate
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.tile

/**
 * 설정 > 앱 정보 > 업데이트 기록. 최신 버전이 위, 지금 버전은 펼친 상태로 시작합니다.
 * 내용은 docs/release-notes 폴더의 버전별 md 파일 (빌드 때 assets로 담김).
 */
@Composable
fun UpdateHistoryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val english = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    val notes = remember(english) { ReleaseNotes.load(context, english, BuildConfig.VERSION_NAME) }
    var expanded by remember { mutableStateOf(notes.firstOrNull()?.version) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            androidx.compose.material3.Text(
                if (english) "Update history" else "업데이트 기록",
                fontWeight = FontWeight.Bold, fontSize = DigitsTextSize.title
            )
        },
        text = {
            if (notes.isEmpty()) {
                androidx.compose.material3.Text(
                    if (english) "No update notes yet." else "아직 업데이트 기록이 없습니다.",
                    fontSize = DigitsTextSize.body
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notes, key = { it.version }) { note ->
                        NoteCard(
                            note = note,
                            isCurrent = ReleaseNotes.compare(note.version, BuildConfig.VERSION_NAME) == 0,
                            open = expanded == note.version,
                            english = english,
                            onToggle = { expanded = if (expanded == note.version) null else note.version }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { androidx.compose.material3.Text(if (english) "Close" else "닫기") }
        }
    )
}

@Composable
private fun NoteCard(note: ReleaseNote, isCurrent: Boolean, open: Boolean, english: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tile, DigitsShape.md)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                "v${note.version}",
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold,
                fontSize = DigitsTextSize.bodyLarge
            )
            if (isCurrent) {
                androidx.compose.material3.Text(
                    if (english) "Current" else "지금 버전",
                    fontSize = DigitsTextSize.caption,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }
            val turn by androidx.compose.animation.core.animateFloatAsState(if (open) 90f else 0f, label = "noteChevron")
            androidx.compose.material3.Icon(
                imageVector = androidx.compose.material.icons.Icons.Rounded.ChevronRight,
                contentDescription = if (english) (if (open) "Collapse" else "Expand") else (if (open) "접기" else "펼치기"),
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.rotate(turn)
            )
        }
        if (note.summary.isNotBlank()) {
            androidx.compose.material3.Text(
                bold(note.summary), fontSize = DigitsTextSize.label,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (open) Int.MAX_VALUE else 2
            )
        }
        if (open) {
            note.items.forEach { item ->
                Row {
                    androidx.compose.material3.Text("•", fontSize = DigitsTextSize.label, modifier = Modifier.padding(end = 6.dp))
                    androidx.compose.material3.Text(bold(item), fontSize = DigitsTextSize.label, lineHeight = 19.sp)
                }
            }
        }
    }
}

/** '**굵게**' 표시만 살립니다. */
internal fun bold(text: String): AnnotatedString = buildAnnotatedString {
    val parts = text.split("**")
    parts.forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}
