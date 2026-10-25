package com.digitscore.app.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.digitscore.app.data.CumulativeScoreStore
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.TimeRuleStore
import com.digitscore.app.model.TimeRule
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek

@Composable
fun TimeRuleSection(
    isEn: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var rules by remember { mutableStateOf(TimeRuleStore.getRules(context)) }
    var editingRule by remember { mutableStateOf<TimeRule?>(null) }
    var isNewRule by remember { mutableStateOf(false) }

    fun refreshRules() {
        rules = TimeRuleStore.getRules(context)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DigitsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isEn) "Time-of-day Rules" else "시간대 규칙",
                        fontWeight = FontWeight.Bold,
                        fontSize = DigitsTextSize.bodyLarge
                    )
                    Text(
                        text = if (isEn) "Stricter screen lighting intervals during specific hours (up to 3)"
                        else "특정 시간대에 화면 신호등 간격을 강화합니다 (최대 3개)",
                        fontSize = DigitsTextSize.label,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                if (rules.size < TimeRuleStore.MAX_RULES) {
                    TextButton(
                        onClick = {
                            editingRule = TimeRule(
                                id = java.util.UUID.randomUUID().toString(),
                                name = if (isEn) "Work" else "업무",
                                enabled = true
                            )
                            isNewRule = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isEn) "Add" else "추가",
                            fontSize = DigitsTextSize.label,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (rules.isEmpty()) {
                // 추천 설정 제안
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val preset = TimeRuleStore.getDefaultPresetRules(isEn)[0].copy(enabled = true)
                            TimeRuleStore.addRule(context, preset)
                            refreshRules()
                        },
                        modifier = Modifier.weight(1f),
                        shape = DigitsShape.sm
                    ) {
                        Text(
                            text = if (isEn) "+ Work (10min)" else "+ 업무 (10min)",
                            fontSize = DigitsTextSize.caption
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val preset = TimeRuleStore.getDefaultPresetRules(isEn)[1].copy(enabled = true)
                            TimeRuleStore.addRule(context, preset)
                            refreshRules()
                        },
                        modifier = Modifier.weight(1f),
                        shape = DigitsShape.sm
                    ) {
                        Text(
                            text = if (isEn) "+ Sleep prep (5min)" else "+ 수면 준비 (5min)",
                            fontSize = DigitsTextSize.caption
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    rules.forEachIndexed { index, rule ->
                        if (index > 0) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        TimeRuleItemRow(
                            rule = rule,
                            isEn = isEn,
                            onToggleEnabled = { enabled ->
                                TimeRuleStore.updateRule(context, rule.copy(enabled = enabled))
                                refreshRules()
                            },
                            onClick = {
                                editingRule = rule
                                isNewRule = false
                            }
                        )
                    }
                }
            }
        }
    }

    editingRule?.let { rule ->
        EditTimeRuleDialog(
            initialRule = rule,
            isNew = isNewRule,
            isEn = isEn,
            onDismiss = { editingRule = null },
            onSave = { updated ->
                if (isNewRule) {
                    TimeRuleStore.addRule(context, updated)
                } else {
                    TimeRuleStore.updateRule(context, updated)
                }
                refreshRules()
                editingRule = null
            },
            onDelete = if (!isNewRule) {
                {
                    TimeRuleStore.deleteRule(context, rule.id)
                    refreshRules()
                    editingRule = null
                }
            } else null
        )
    }
}

@Composable
private fun TimeRuleItemRow(
    rule: TimeRule,
    isEn: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = rule.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = DigitsTextSize.body,
                    color = if (rule.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                )
                Surface(
                    shape = DigitsShape.xs,
                    color = if (rule.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isEn) "Lighting ${rule.lightingMinutes}min" else "신호등 ${rule.lightingMinutes}min",
                        fontSize = DigitsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        color = if (rule.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            val daysText = formatDaysSummary(rule.daysOfWeek, isEn)
            val timeText = String.format("%02d:%02d - %02d:%02d", rule.startHour, rule.startMinute, rule.endHour, rule.endMinute)
            val overnightHint = if (rule.isOvernight) (if (isEn) " (Overnight)" else " (자정 넘김)") else ""
            Text(
                text = "$daysText · $timeText$overnightHint",
                fontSize = DigitsTextSize.caption,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Switch(
            checked = rule.enabled,
            onCheckedChange = onToggleEnabled
        )
    }
}

@Composable
private fun EditTimeRuleDialog(
    initialRule: TimeRule,
    isNew: Boolean,
    isEn: Boolean,
    onDismiss: () -> Unit,
    onSave: (TimeRule) -> Unit,
    onDelete: (() -> Unit)?
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initialRule.name) }
    var daysOfWeek by remember { mutableStateOf(initialRule.daysOfWeek) }
    var startHour by remember { mutableIntStateOf(initialRule.startHour) }
    var startMinute by remember { mutableIntStateOf(initialRule.startMinute) }
    var endHour by remember { mutableIntStateOf(initialRule.endHour) }
    var endMinute by remember { mutableIntStateOf(initialRule.endMinute) }
    var lightingMinutes by remember { mutableIntStateOf(initialRule.lightingMinutes) }
    var sleepPrepAppliedHint by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) (if (isEn) "Add Time Rule" else "시간대 규칙 추가") else (if (isEn) "Edit Time Rule" else "시간대 규칙 수정"),
                fontWeight = FontWeight.Bold,
                fontSize = DigitsTextSize.headline
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 추천 칩
                Text(
                    text = if (isEn) "Presets" else "빠른 제안",
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = name == (if (isEn) "Work" else "업무"),
                        onClick = {
                            name = if (isEn) "Work" else "업무"
                            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                            startHour = 9
                            startMinute = 0
                            endHour = 18
                            endMinute = 0
                            lightingMinutes = 10
                            sleepPrepAppliedHint = null
                        },
                        label = { Text(if (isEn) "Work" else "업무", fontSize = DigitsTextSize.caption) }
                    )
                    FilterChip(
                        selected = name == (if (isEn) "Sleep prep" else "수면 준비"),
                        onClick = {
                            name = if (isEn) "Sleep prep" else "수면 준비"
                            daysOfWeek = DayOfWeek.values().toSet()
                            lightingMinutes = 5
                            scope.launch {
                                val db = DigitsDatabase.getInstance(context)
                                val learned = withContext(Dispatchers.IO) {
                                    CumulativeScoreStore.getLearnedBedtimeMinute(db)
                                }
                                if (learned != null) {
                                    val prepMinute = (learned - 60 + 1440) % 1440
                                    startHour = prepMinute / 60
                                    startMinute = prepMinute % 60
                                    endHour = 7
                                    endMinute = 0
                                    sleepPrepAppliedHint = if (isEn) "Learned bedtime -1h applied" else "학습된 취침 1시간 전 제안"
                                } else {
                                    startHour = 23
                                    startMinute = 0
                                    endHour = 7
                                    endMinute = 0
                                    sleepPrepAppliedHint = if (isEn) "Default 23:00 applied" else "기본값 23:00 제안"
                                }
                            }
                        },
                        label = { Text(if (isEn) "Sleep prep" else "수면 준비", fontSize = DigitsTextSize.caption) }
                    )
                }

                // 규칙 이름
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isEn) "Rule name" else "규칙 이름", fontSize = DigitsTextSize.caption) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                sleepPrepAppliedHint?.let { hint ->
                    Text(
                        text = hint,
                        fontSize = DigitsTextSize.caption,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 요일 선택
                Text(
                    text = if (isEn) "Days of week" else "적용 요일",
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = daysOfWeek.size == 5 && !daysOfWeek.contains(DayOfWeek.SATURDAY) && !daysOfWeek.contains(DayOfWeek.SUNDAY),
                        onClick = {
                            daysOfWeek = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
                        },
                        label = { Text(if (isEn) "Weekdays" else "평일", fontSize = DigitsTextSize.caption) }
                    )
                    FilterChip(
                        selected = daysOfWeek.size == 7,
                        onClick = { daysOfWeek = DayOfWeek.values().toSet() },
                        label = { Text(if (isEn) "Everyday" else "매일", fontSize = DigitsTextSize.caption) }
                    )
                    FilterChip(
                        selected = daysOfWeek.size == 2 && daysOfWeek.contains(DayOfWeek.SATURDAY) && daysOfWeek.contains(DayOfWeek.SUNDAY),
                        onClick = { daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) },
                        label = { Text(if (isEn) "Weekends" else "주말", fontSize = DigitsTextSize.caption) }
                    )
                }

                // 7개 요일 개별 칩
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dayLabels = if (isEn) {
                        listOf("M" to DayOfWeek.MONDAY, "T" to DayOfWeek.TUESDAY, "W" to DayOfWeek.WEDNESDAY,
                            "T" to DayOfWeek.THURSDAY, "F" to DayOfWeek.FRIDAY, "S" to DayOfWeek.SATURDAY, "S" to DayOfWeek.SUNDAY)
                    } else {
                        listOf("월" to DayOfWeek.MONDAY, "화" to DayOfWeek.TUESDAY, "수" to DayOfWeek.WEDNESDAY,
                            "목" to DayOfWeek.THURSDAY, "금" to DayOfWeek.FRIDAY, "토" to DayOfWeek.SATURDAY, "일" to DayOfWeek.SUNDAY)
                    }
                    dayLabels.forEach { (label, day) ->
                        val isSelected = daysOfWeek.contains(day)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                daysOfWeek = if (isSelected) {
                                    if (daysOfWeek.size > 1) daysOfWeek - day else daysOfWeek
                                } else {
                                    daysOfWeek + day
                                }
                            },
                            label = { Text(label, fontSize = DigitsTextSize.caption) }
                        )
                    }
                }

                // 시간 범위 (시작 / 종료)
                Text(
                    text = if (isEn) "Time window" else "시간 범위",
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                startHour = h
                                startMinute = m
                            }, startHour, startMinute, true).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = DigitsShape.sm
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isEn) "Start" else "시작", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                            Text(String.format("%02d:%02d", startHour, startMinute), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text("~", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.outline)
                    OutlinedButton(
                        onClick = {
                            TimePickerDialog(context, { _, h, m ->
                                endHour = h
                                endMinute = m
                            }, endHour, endMinute, true).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = DigitsShape.sm
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isEn) "End" else "종료", fontSize = DigitsTextSize.caption, color = MaterialTheme.colorScheme.outline)
                            Text(String.format("%02d:%02d", endHour, endMinute), fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (endHour * 60 + endMinute <= startHour * 60 + startMinute) {
                    Text(
                        text = if (isEn) "Overnight window (ends next day)" else "자정 넘김 (다음 날 아침 종료)",
                        fontSize = DigitsTextSize.caption,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // 화면 신호등 간격 (5, 10, 15, 20min)
                Text(
                    text = if (isEn) "Lighting interval" else "화면 신호등 간격",
                    fontSize = DigitsTextSize.caption,
                    color = MaterialTheme.colorScheme.outline
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(5, 10, 15, 20).forEach { mins ->
                        FilterChip(
                            selected = lightingMinutes == mins,
                            onClick = { lightingMinutes = mins },
                            label = { Text("${mins}min", fontSize = DigitsTextSize.caption) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initialRule.copy(
                            name = name.ifBlank { if (isEn) "Custom Rule" else "시간대 규칙" },
                            daysOfWeek = daysOfWeek,
                            startHour = startHour,
                            startMinute = startMinute,
                            endHour = endHour,
                            endMinute = endMinute,
                            lightingMinutes = lightingMinutes
                        )
                    )
                },
                shape = DigitsShape.sm
            ) {
                Text(if (isEn) "Save" else "저장", fontSize = DigitsTextSize.body, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(if (isEn) "Delete" else "삭제", fontSize = DigitsTextSize.body)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(if (isEn) "Cancel" else "취소", fontSize = DigitsTextSize.body)
                }
            }
        }
    )
}

private fun formatDaysSummary(days: Set<DayOfWeek>, isEn: Boolean): String {
    return when {
        days.size == 7 -> if (isEn) "Every day" else "매일"
        days.size == 5 && !days.contains(DayOfWeek.SATURDAY) && !days.contains(DayOfWeek.SUNDAY) -> if (isEn) "Weekdays" else "평일"
        days.size == 2 && days.contains(DayOfWeek.SATURDAY) && days.contains(DayOfWeek.SUNDAY) -> if (isEn) "Weekends" else "주말"
        else -> {
            val names = if (isEn) {
                listOf(DayOfWeek.MONDAY to "M", DayOfWeek.TUESDAY to "T", DayOfWeek.WEDNESDAY to "W",
                    DayOfWeek.THURSDAY to "T", DayOfWeek.FRIDAY to "F", DayOfWeek.SATURDAY to "S", DayOfWeek.SUNDAY to "S")
            } else {
                listOf(DayOfWeek.MONDAY to "월", DayOfWeek.TUESDAY to "화", DayOfWeek.WEDNESDAY to "수",
                    DayOfWeek.THURSDAY to "목", DayOfWeek.FRIDAY to "금", DayOfWeek.SATURDAY to "토", DayOfWeek.SUNDAY to "일")
            }
            names.filter { it.first in days }.joinToString(" ") { it.second }
        }
    }
}
