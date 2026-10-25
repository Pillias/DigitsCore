package com.digitscore.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.DigitsShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.i18n.Text
import com.digitscore.app.i18n.UiTranslator

@Composable
fun DetailChevron(
    tint: Color = MaterialTheme.colorScheme.outline,
    description: String = "상세 보기"
) {
    Icon(
        imageVector = Icons.Rounded.ChevronRight,
        contentDescription = UiTranslator.translate(description),
        tint = tint
    )
}

/** 상단 바의 이름 붙은 동작 버튼: 아이콘 아래에 짧은 이름을 붙여 처음 쓰는 사람도 바로 알 수 있게 합니다. */
@Composable
fun LabeledTopAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    contentDescription: String = label
) {
    Column(
        modifier = Modifier
            .clip(DigitsShape.md)
            .clickable(onClick = onClick, role = androidx.compose.ui.semantics.Role.Button, onClickLabel = contentDescription)
            .defaultMinSize(minWidth = 52.dp, minHeight = 48.dp)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            fontSize = DigitsTextSize.caption,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1
        )
    }
}

/** "더 보기"류 링크: 글자 + 오른쪽 꺾쇠 아이콘 하나로 통일합니다. */
@Composable
fun MoreLink(text: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = if (onClick != null) Modifier.clip(DigitsShape.sm).clickable(onClick = onClick) else Modifier
    ) {
        Text(
            text = text,
            fontSize = DigitsTextSize.label,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 목표 칸의 달성(체크)·초과(느낌표) 표시. 글자 기호 대신 아이콘을 씁니다. */
@Composable
fun GoalStatusMark(achieved: Boolean, exceeded: Boolean, tint: Color) {
    val icon = when {
        exceeded -> Icons.Rounded.PriorityHigh
        achieved -> Icons.Rounded.Check
        else -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.padding(start = 2.dp).size(12.dp)
    )
}

@Composable
fun SectionHeading(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = DigitsTextSize.label,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, fontWeight = FontWeight.Medium)
                DetailChevron(tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun InformationDetailDialog(
    title: String,
    value: String? = null,
    description: String,
    supportingText: String? = null,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = MaterialTheme.shapes.extraLarge,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (value != null) {
                    Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        fontSize = DigitsTextSize.label,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        },
        confirmButton = {
            val context = androidx.compose.ui.platform.LocalContext.current
            val isEn = com.digitscore.app.i18n.AppLocale.currentLanguage(context) == com.digitscore.app.i18n.AppLocale.ENGLISH
            TextButton(onClick = onDismiss) {
                Text(if (isEn) "Close" else "닫기", fontWeight = FontWeight.Medium)
            }
        }
    )
}
