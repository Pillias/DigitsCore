package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.digitscore.app.model.DailyGoal
import com.digitscore.app.model.DailyGoalType
import com.digitscore.app.model.YesterdayBriefingSummary
import java.util.Locale

/**
 * 아침 기상 시 뜨는 풀스크린 모달 팝업 다이얼로그
 * 어제 통계 요약 및 오늘 목표(수락/스킵)를 다이얼로그 형태로 선택하게 함.
 */
@Composable
fun MorningBriefingDialog(
    yesterdaySummary: YesterdayBriefingSummary,
    dailyGoal: DailyGoal,
    onAcceptGoal: () -> Unit,
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
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (english) "MORNING BRIEFING" else "모닝 브리핑",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = if (english) "Set your daily focus" else "오늘의 시작과 목표 설정",
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

                // 어제 핵심 요약 (3칸 대형 숫자 카드)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. 점수
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (english) "SCORE" else "어제 점수",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "${yesterdaySummary.score}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = when {
                                    yesterdaySummary.score >= 80 -> com.digitscore.app.ui.theme.ScoreGreen
                                    yesterdaySummary.score >= 60 -> com.digitscore.app.ui.theme.ScoreYellow
                                    else -> com.digitscore.app.ui.theme.ScoreRed
                                }
                            )
                        }
                    }

                    // 2. 화면 시간
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (english) "SCREEN" else "화면 시간",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = yTimeStr,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 3. 1위 앱
                    Surface(
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (yesterdaySummary.topAppName.isNotBlank()) yesterdaySummary.topAppName
                                else (if (english) "TOP APP" else "최대 부하"),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (yesterdaySummary.topAppUsageMinutes > 0) topTimeStr else "-",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = com.digitscore.app.ui.theme.ScoreOrange
                            )
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 오늘의 추천 목표 카드
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (english) "TODAY'S GOAL" else "오늘의 맞춤 추천 목표",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = if (english) "RECOMMENDED" else "추천",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Text(
                            text = when (dailyGoal.type) {
                                DailyGoalType.APP_USAGE_LIMIT ->
                                    if (english) "${dailyGoal.targetAppName} under ${dailyGoal.targetValue} min"
                                    else "${dailyGoal.targetAppName} ${dailyGoal.targetValue}분 이내 사용"
                                DailyGoalType.SCORE_DEFENSE ->
                                    if (english) "Defend Core Index ${dailyGoal.targetValue}+"
                                    else "코어 지수 ${dailyGoal.targetValue}점 이상 방어"
                                DailyGoalType.UNLOCK_LIMIT ->
                                    if (english) "Unlocks under ${dailyGoal.targetValue}"
                                    else "잠금 해제 ${dailyGoal.targetValue}회 이내"
                            },
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = if (english) "Milestone alerts will coach you when reaching 80%."
                            else "80% 도달 시 중간 코칭 알림을 제공합니다.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // 액션 버튼 (확정 / 건너뛰기)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onAcceptGoal,
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (english) "Accept Goal" else "목표 설정하기",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
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
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
