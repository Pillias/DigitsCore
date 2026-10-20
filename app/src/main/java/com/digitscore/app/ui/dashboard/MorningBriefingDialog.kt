package com.digitscore.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.digitscore.app.data.DailyGoalStore
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.model.DailyGoal
import com.digitscore.app.model.GoalDifficulty
import com.digitscore.app.model.GoalRewardState
import com.digitscore.app.model.YesterdayBriefingSummary
import com.digitscore.app.model.YesterdayGoalResult
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreOrange
import com.digitscore.app.ui.theme.ScoreRed
import com.digitscore.app.ui.theme.ScoreYellow
import java.util.Locale

/**
 * 아침 기상 시 뜨는 데일리 코칭 팝업 다이얼로그
 * - [상단] 어제의 3대 실천 목표 결과 피드백 & 스트릭/자유권 보상 확인
 * - [하단] 오늘의 시스템 자동 지정 미션 (난이도 선택 / 원터치 수락 / 필요 시 세부 조정)
 */
@Composable
fun MorningBriefingDialog(
    yesterdaySummary: YesterdayBriefingSummary,
    yesterdayGoalResult: YesterdayGoalResult?,
    dailyGoal: DailyGoal,
    rewardState: GoalRewardState,
    onAcceptGoals: (scoreTarget: Int, targetPkg: String?, targetAppName: String, appLimitMins: Int, unlockLimit: Int) -> Unit,
    onSkipGoal: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val english = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    val yTimeStr = "${yesterdaySummary.totalScreenTimeMinutes}min"
    val topTimeStr = "${yesterdaySummary.topAppUsageMinutes}min"

    var userInteracted by remember { mutableStateOf(false) }
    var autoCloseSeconds by remember { mutableIntStateOf(5) }

    var selectedDifficulty by remember { mutableStateOf(GoalDifficulty.NORMAL) }
    var showCustomAdjust by remember { mutableStateOf(false) }

    // 기본 추천 목표 (난이도 적용)
    val difficultyGoal = remember(selectedDifficulty, dailyGoal) {
        DailyGoalStore.applyDifficulty(dailyGoal, selectedDifficulty)
    }

    var selectedScoreTarget by remember(difficultyGoal) { mutableIntStateOf(difficultyGoal.scoreTarget) }
    var selectedAppPkg by remember(difficultyGoal) { mutableStateOf(difficultyGoal.targetPackageName) }
    var selectedAppName by remember(difficultyGoal) { mutableStateOf(difficultyGoal.targetAppName) }
    var selectedAppLimit by remember(difficultyGoal) { mutableIntStateOf(difficultyGoal.appLimitMinutes) }
    var selectedUnlockTarget by remember(difficultyGoal) { mutableIntStateOf(difficultyGoal.unlockLimitTarget) }

    LaunchedEffect(userInteracted) {
        if (!userInteracted) {
            while (autoCloseSeconds > 0 && !userInteracted) {
                kotlinx.coroutines.delay(1_000L)
                if (!userInteracted) {
                    autoCloseSeconds -= 1
                }
            }
            if (!userInteracted && autoCloseSeconds <= 0) {
                onAcceptGoals(
                    selectedScoreTarget,
                    selectedAppPkg,
                    selectedAppName,
                    selectedAppLimit,
                    selectedUnlockTarget
                )
            }
        }
    }

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
                .fillMaxWidth(0.95f)
                .heightIn(max = 640.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. 헤더: 타이틀 + 스트릭 배지
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
                            text = if (english) "Yesterday Feedback & Today's Mission" else "어제 피드백 & 오늘의 미션",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (rewardState.currentStreak > 0) ScoreOrange.copy(alpha = 0.15f) else MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (rewardState.currentStreak > 0) {
                                Icon(
                                    imageVector = Icons.Default.Whatshot,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = ScoreOrange
                                )
                            }
                            Text(
                                text = if (rewardState.currentStreak > 0) (if (english) "${rewardState.currentStreak}-Day Streak" else "${rewardState.currentStreak}일 스트릭")
                                else (if (english) "Today's Start" else "오늘의 시작"),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rewardState.currentStreak > 0) ScoreOrange else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                // 2. [상단] 어제의 목표 결과 피드백 카드
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 스트릭 및 보상 획득 현황 배너
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (rewardState.currentStreak > 0) {
                                    Icon(
                                        imageVector = Icons.Default.Whatshot,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = ScoreOrange
                                    )
                                }
                                Text(
                                    text = if (rewardState.currentStreak > 0) (if (english) "${rewardState.currentStreak}-Day Streak Active" else "${rewardState.currentStreak}일 연속 방어 중")
                                    else (if (english) "New Streak Starting" else "새로운 스트릭 도전"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rewardState.currentStreak > 0) ScoreOrange else MaterialTheme.colorScheme.outline
                                )
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.StarOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (english) "Stamps ${rewardState.perfectStamps}/3" else "스탬프 ${rewardState.perfectStamps}/3",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                if (rewardState.availableFreePasses > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ConfirmationNumber,
                                                contentDescription = null,
                                                modifier = Modifier.size(11.dp),
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = if (english) "Free Pass x${rewardState.availableFreePasses}" else "자유권 ${rewardState.availableFreePasses}장",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 어제 결과 판정 텍스트
                        if (yesterdayGoalResult != null && yesterdayGoalResult.hasGoal) {
                            val bannerMessage = when {
                                yesterdayGoalResult.isPerfectDay -> if (english) "All 3 Goals Cleared! Perfect Day!" else "3개 올 클리어! 퍼펙트 데이 달성!"
                                yesterdayGoalResult.isStreakMaintained -> if (english) "${yesterdayGoalResult.achievedCount} Goals Cleared! Shield streak maintained." else "${yesterdayGoalResult.achievedCount}개 달성! 훌륭하게 방어했습니다 (스트릭 유지)"
                                else -> if (english) "Good effort yesterday! Ready to recover today?" else "아쉽지만 좋은 시도였습니다. 오늘 다시 회복해볼까요?"
                            }

                            Text(
                                text = bannerMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (yesterdayGoalResult.isStreakMaintained) ScoreGreen else MaterialTheme.colorScheme.onSurface
                            )

                            // 3대 항목별 실적 그리드
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // 1) 코어 지수
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (english) "CORE" else "코어 지수",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${yesterdayGoalResult.finalScore}P",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (yesterdayGoalResult.isScoreAchieved) ScoreGreen else ScoreOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                         Text(
                                            text = if (yesterdayGoalResult.isScoreAchieved) (if (english) "Cleared ✓" else "방어 성공 ✓")
                                            else (if (english) "Below !" else "목표 미달 !"),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (yesterdayGoalResult.isScoreAchieved) ScoreGreen else ScoreOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // 2) 집중 앱
                                Surface(
                                    modifier = Modifier.weight(1.1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (yesterdayGoalResult.targetPackageName != null) yesterdayGoalResult.targetAppName
                                            else (if (english) "NO APP" else "관리 앱 없음"),
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${yesterdayGoalResult.finalAppUsageMinutes}min",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (yesterdayGoalResult.isAppLimitAchieved) ScoreGreen else ScoreOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val appDiff = yesterdayGoalResult.appLimitMinutes - yesterdayGoalResult.finalAppUsageMinutes
                                        val appStatusText = if (yesterdayGoalResult.targetPackageName == null) {
                                            if (english) "Free Pass" else "자유 사용"
                                        } else if (appDiff >= 0) {
                                            if (english) "Saved -${appDiff}min" else "-${appDiff}min 절감"
                                        } else {
                                            if (english) "+${-appDiff}min !" else "+${-appDiff}min 초과 !"
                                        }
                                        Text(
                                            text = appStatusText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (yesterdayGoalResult.isAppLimitAchieved) ScoreGreen else ScoreOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // 3) 잠금 해제
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = if (english) "UNLOCK" else "잠금 해제",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.outline,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${yesterdayGoalResult.finalUnlockCount}x",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (yesterdayGoalResult.isUnlockLimitAchieved) ScoreGreen else ScoreOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val unlockDiff = yesterdayGoalResult.unlockLimitTarget - yesterdayGoalResult.finalUnlockCount
                                        val unlockStatusText = if (unlockDiff >= 0) {
                                            if (english) "Cleared ✓" else "절제 성공 ✓"
                                        } else {
                                            if (english) "+${-unlockDiff}x !" else "+${-unlockDiff}x 초과 !"
                                        }
                                        Text(
                                            text = unlockStatusText,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (yesterdayGoalResult.isUnlockLimitAchieved) ScoreGreen else ScoreOrange,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        } else {
                            // 어제 목표가 없었던 날 (첫날 등)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = if (english) "Yesterday's Baseline Usage" else "어제의 기본 사용 기록",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (english) "Score: ${yesterdaySummary.score}P  |  Screen: $yTimeStr  |  Most Used: ${yesterdaySummary.topAppName.ifBlank { "None" }} $topTimeStr"
                                    else "점수: ${yesterdaySummary.score}P  |  화면: $yTimeStr  |  최다 사용: ${yesterdaySummary.topAppName.ifBlank { "없음" }} $topTimeStr",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = if (english) "Ready to challenge your first daily mission today!" else "오늘 첫 시스템 미션을 수락하고 스트릭을 시작해보세요!",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // 3. [하단] 오늘의 시스템 미션 (Daily Mission)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 미션 헤더
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (english) "TODAY'S GOALS" else "오늘의 추천 목표",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            TextButton(
                                onClick = {
                                    userInteracted = true
                                    showCustomAdjust = !showCustomAdjust
                                },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (showCustomAdjust) (if (english) "Fold ▲" else "접기 ▲") else (if (english) "Tune ▼" else "세부 조정 ▼"),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // 난이도 선택 칩 (여유 / 표준 / 도전)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedDifficulty == GoalDifficulty.EASY,
                                onClick = {
                                    userInteracted = true
                                    selectedDifficulty = GoalDifficulty.EASY
                                },
                                label = { Text(if (english) "Easy" else "여유", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedDifficulty == GoalDifficulty.NORMAL,
                                onClick = {
                                    userInteracted = true
                                    selectedDifficulty = GoalDifficulty.NORMAL
                                },
                                label = { Text(if (english) "Normal" else "표준", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1.2f)
                            )
                            FilterChip(
                                selected = selectedDifficulty == GoalDifficulty.HARD,
                                onClick = {
                                    userInteracted = true
                                    selectedDifficulty = GoalDifficulty.HARD
                                },
                                label = { Text(if (english) "Hard" else "도전", fontSize = 11.sp, maxLines = 1, softWrap = false) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 3대 미션 목표 요약 표시 (가로 1열 카드 형태)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(text = if (english) "Core Index Defense" else "코어 지수 방어", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Text(text = "${selectedScoreTarget} P", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (selectedAppPkg != null) (if (english) "Limit $selectedAppName" else "${selectedAppName} 절제") else (if (english) "Focus App" else "집중 관리 앱"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = if (selectedAppPkg != null) "${selectedAppLimit}min" else (if (english) "None" else "선택 안 함"),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (selectedAppPkg != null) ScoreOrange else MaterialTheme.colorScheme.outline
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.LockOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(13.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(text = if (english) "Daily Unlock Limit" else "일일 잠금 해제 조절", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                                Text(text = "${selectedUnlockTarget}x", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        // 세부 조정 확장 영역 (필요 시 슬라이더와 앱 선택 칩 노출)
                        AnimatedVisibility(visible = showCustomAdjust) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 코어 지수 슬라이더
                                Column {
                                    Text(text = if (english) "Adjust Core Index Target" else "코어 지수 목표 미세조정", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Slider(
                                        value = selectedScoreTarget.toFloat(),
                                        onValueChange = {
                                            userInteracted = true
                                            selectedScoreTarget = (Math.round(it / 5f) * 5).toInt()
                                        },
                                        valueRange = 40f..85f,
                                        steps = 8,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                // 앱 선택 칩 & 슬라이더
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(text = if (english) "Change Focus App" else "집중 관리 앱 변경", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = selectedAppPkg == null,
                                            onClick = {
                                                userInteracted = true
                                                selectedAppPkg = null
                                                selectedAppName = ""
                                            },
                                            label = { Text(if (english) "None" else "선택 안 함", fontSize = 11.sp) }
                                        )

                                        yesterdaySummary.candidateApps.forEach { candidate ->
                                            FilterChip(
                                                selected = selectedAppPkg == candidate.packageName,
                                                onClick = {
                                                    userInteracted = true
                                                    selectedAppPkg = candidate.packageName
                                                    selectedAppName = candidate.appName
                                                    val baseMins = if (candidate.yesterdayUsageMinutes > 0) candidate.yesterdayUsageMinutes else candidate.recentAvgMinutes
                                                    selectedAppLimit = ((baseMins * 0.75f).toInt().coerceAtLeast(15) / 5) * 5
                                                },
                                                label = { Text("${candidate.appName} (${candidate.yesterdayUsageMinutes}min)", fontSize = 11.sp) }
                                            )
                                        }
                                    }

                                    if (selectedAppPkg != null) {
                                        Slider(
                                            value = selectedAppLimit.toFloat(),
                                            onValueChange = {
                                                userInteracted = true
                                                selectedAppLimit = (Math.round(it / 5f) * 5).toInt()
                                            },
                                            valueRange = 15f..180f,
                                            steps = 32,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }

                                // 잠금해제 슬라이더
                                Column {
                                    Text(text = if (english) "Adjust Unlock Limit" else "잠금 해제 한도 미세조정", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                                    Slider(
                                        value = selectedUnlockTarget.toFloat(),
                                        onValueChange = {
                                            userInteracted = true
                                            selectedUnlockTarget = (Math.round(it / 10f) * 10).toInt()
                                        },
                                        valueRange = 40f..180f,
                                        steps = 13,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
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
                            userInteracted = true
                            onAcceptGoals(
                                selectedScoreTarget,
                                selectedAppPkg,
                                selectedAppName,
                                selectedAppLimit,
                                selectedUnlockTarget
                            )
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = if (english) {
                                if (!userInteracted && autoCloseSeconds > 0) "Start Mission (Auto in ${autoCloseSeconds}s)" else "Start Today's Mission"
                            } else {
                                if (!userInteracted && autoCloseSeconds > 0) "오늘 미션 시작 (${autoCloseSeconds}초 후 자동 닫힘)" else "오늘 미션 시작"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            userInteracted = true
                            onSkipGoal()
                        },
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
