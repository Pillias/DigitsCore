package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.digitscore.app.model.DailyGoal
import com.digitscore.app.model.YesterdayBriefingSummary
import java.util.Locale

/**
 * 아침 기상 시 뜨는 데일리 코칭 팝업 다이얼로그
 * - 어제 3대 통계(점수, 화면 시간, 최대 부하 앱) 요약
 * - 오늘의 복합 목표(코어 지수 방어, 특정 앱 시간 제한, 잠금해제 횟수)를 직접 선택/설정
 */
@Composable
fun MorningBriefingDialog(
    yesterdaySummary: YesterdayBriefingSummary,
    dailyGoal: DailyGoal,
    onAcceptGoals: (scoreTarget: Int, targetPkg: String?, targetAppName: String, appLimitMins: Int, unlockLimit: Int) -> Unit,
    onSkipGoal: () -> Unit,
    onDismiss: () -> Unit
) {
    val english = Locale.getDefault().language == "en"
    val yHours = yesterdaySummary.totalScreenTimeMinutes / 60
    val yMins = yesterdaySummary.totalScreenTimeMinutes % 60
    val yTimeStr = if (yHours > 0) "${yHours}h ${yMins}m" else "${yMins}m"

    val topHours = yesterdaySummary.topAppUsageMinutes / 60
    val topMins = yesterdaySummary.topAppUsageMinutes % 60
    val topTimeStr = if (topHours > 0) "${topHours}h ${topMins}m" else "${topMins}m"

    var selectedScoreTarget by remember { mutableIntStateOf(dailyGoal.scoreTarget) }
    var selectedAppPkg by remember { mutableStateOf(dailyGoal.targetPackageName) }
    var selectedAppName by remember { mutableStateOf(dailyGoal.targetAppName) }
    var selectedAppLimit by remember { mutableIntStateOf(dailyGoal.appLimitMinutes) }
    var selectedUnlockTarget by remember { mutableIntStateOf(dailyGoal.unlockLimitTarget) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (english) "MORNING BRIEFING" else "모닝 브리핑",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (english) "Today's Focus Set" else "오늘의 3대 목표 설정",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = if (english) "DAY START" else "기상 감지",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // 2. 어제 핵심 통계 3칸 그리드
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 점수
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (english) "SCORE" else "어제 점수",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "${yesterdaySummary.score}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    yesterdaySummary.score >= 80 -> com.digitscore.app.ui.theme.ScoreGreen
                                    yesterdaySummary.score >= 60 -> com.digitscore.app.ui.theme.ScoreYellow
                                    else -> com.digitscore.app.ui.theme.ScoreRed
                                }
                            )
                        }
                    }

                    // 화면 시간
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (english) "SCREEN" else "화면 시간",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = yTimeStr,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 1위 앱
                    Surface(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (yesterdaySummary.topAppName.isNotBlank()) yesterdaySummary.topAppName
                                else (if (english) "TOP APP" else "최대 부하"),
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (yesterdaySummary.topAppUsageMinutes > 0) topTimeStr else "-",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.digitscore.app.ui.theme.ScoreOrange
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3. 오늘의 3대 복합 목표 설정 박스
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 목표 A: 코어 지수 방어선
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (english) "1. Core Index Goal" else "1. 코어 지수 목표",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${selectedScoreTarget} P",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = selectedScoreTarget.toFloat(),
                                onValueChange = { selectedScoreTarget = (Math.round(it / 5f) * 5).toInt() },
                                valueRange = 40f..80f,
                                steps = 7,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // 목표 B: 특정 앱 제한 (앱 선택 + 시간 조절)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (english) "2. Focus App Limit" else "2. 집중 관리 앱 제한",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (selectedAppPkg != null) "$selectedAppName ${selectedAppLimit}m"
                                    else (if (english) "None" else "선택 안 함"),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selectedAppPkg != null) com.digitscore.app.ui.theme.ScoreOrange
                                    else MaterialTheme.colorScheme.outline
                                )
                            }

                            // 관리 대상 앱 선택 칩 (어제 사용량 상위 앱 후보들)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilterChip(
                                    selected = selectedAppPkg == null,
                                    onClick = {
                                        selectedAppPkg = null
                                        selectedAppName = ""
                                    },
                                    label = { Text(if (english) "None" else "선택 안 함", fontSize = 11.sp) }
                                )

                                yesterdaySummary.candidateApps.forEach { candidate ->
                                    FilterChip(
                                        selected = selectedAppPkg == candidate.packageName,
                                        onClick = {
                                            selectedAppPkg = candidate.packageName
                                            selectedAppName = candidate.appName
                                            selectedAppLimit = ((candidate.yesterdayUsageMinutes * 0.7f).toInt().coerceAtLeast(15) / 5) * 5
                                        },
                                        label = {
                                            Text(
                                                "${candidate.appName} (${candidate.yesterdayUsageMinutes}m)",
                                                fontSize = 11.sp
                                            )
                                        }
                                    )
                                }
                            }

                            // 선택된 앱이 있을 때 드래그 슬라이더 표시
                            if (selectedAppPkg != null) {
                                val currentCandidate = yesterdaySummary.candidateApps.firstOrNull { it.packageName == selectedAppPkg }
                                val yMins = currentCandidate?.yesterdayUsageMinutes ?: 0L
                                if (yMins > 0) {
                                    val reduction = ((yMins - selectedAppLimit).toFloat() / yMins.toFloat() * 100).toInt().coerceAtLeast(0)
                                    Text(
                                        text = if (english) "💡 Yesterday ${yMins}m → Target ${selectedAppLimit}m today (-${reduction}%)"
                                        else "💡 어제 ${yMins}분 사용 → 오늘 목표 ${selectedAppLimit}분 (${reduction}% 절감 제안)",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Slider(
                                    value = selectedAppLimit.toFloat(),
                                    onValueChange = { selectedAppLimit = (Math.round(it / 5f) * 5).toInt() },
                                    valueRange = 15f..180f,
                                    steps = 32,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

                        // 목표 C: 잠금 해제 횟수 제한
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (english) "3. Daily Unlock Limit" else "3. 일일 잠금 해제 조절",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${selectedUnlockTarget}x",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val yUnlock = yesterdaySummary.unlockCount
                            val baseLowest = yesterdaySummary.past14DaysLowestUnlock
                            val unlockContextText = if (yUnlock > 0 && baseLowest > 0) {
                                if (english) "💡 Gradual reduction based on yesterday (${yUnlock}x) & 14-day low (${baseLowest}x)"
                                else "💡 어제 ${yUnlock}x · 14일 최저 ${baseLowest}x 기반 점진적 감축"
                            } else if (yUnlock > 0) {
                                if (english) "💡 Gradual reduction based on yesterday (${yUnlock}x)"
                                else "💡 어제 ${yUnlock}x 언락 기반 점진적 감축"
                            } else null
                            if (unlockContextText != null) {
                                Text(
                                    text = unlockContextText,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Slider(
                                value = selectedUnlockTarget.toFloat(),
                                onValueChange = { selectedUnlockTarget = (Math.round(it / 10f) * 10).toInt() },
                                valueRange = 40f..180f,
                                steps = 13,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 4. 하단 액션 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            onAcceptGoals(
                                selectedScoreTarget,
                                selectedAppPkg,
                                selectedAppName,
                                selectedAppLimit,
                                selectedUnlockTarget
                            )
                        },
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (english) "Start with Goals" else "오늘 3대 목표 설정",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = onSkipGoal,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (english) "Pass (Auto)" else "건너뛰기 (자동)",
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
