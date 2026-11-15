package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digitscore.app.model.GoalRewardState
import com.digitscore.app.model.PersonalBest
import com.digitscore.app.model.StreakRewards
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize
import com.digitscore.app.ui.theme.tile

/**
 * 도룡뇽 단계 · 보호권 · 내 기록 (자유권 대체, 2026-10-28).
 * 도룡뇽 그림은 단계마다 모양이 바뀝니다(SalamanderStageIcon).
 */
@Composable
fun StreakRewardRow(reward: GoalRewardState, best: PersonalBest, isEn: Boolean) {
    val stage = reward.salamanderStage.coerceIn(1, StreakRewards.MAX_STAGE)
    val toNext = StreakRewards.daysToNextStage(reward.currentStreak, stage)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tile, DigitsShape.sm)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                com.digitscore.app.ui.components.SalamanderStageIcon(stage = stage, size = 30.dp)
            }
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEn) "Salamander stage $stage" else "도룡뇽 ${stage}단계",
                    fontSize = DigitsTextSize.caption,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = when {
                        toNext == null -> if (isEn) "Fully grown" else "다 자랐어요"
                        isEn -> "$toNext more streak days to grow"
                        else -> "연속 ${toNext}일 더 지키면 자라요"
                    },
                    fontSize = DigitsTextSize.tiny,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(StreakRewards.MAX_STAGE) { i ->
                    Box(
                        Modifier
                            .size(6.dp)
                            .background(
                                if (i < stage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            )
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isEn) "Shield ${reward.streakShields}/${StreakRewards.MAX_SHIELDS}" else "보호권 ${reward.streakShields}/${StreakRewards.MAX_SHIELDS}",
                fontSize = DigitsTextSize.caption,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
        }
        val parts = buildList {
            best.minManagedMinutes?.let { add(if (isEn) "Least managed-app use ${it}min (${short(best.minManagedDate)})" else "관리 앱 최소 ${it}min (${short(best.minManagedDate)})") }
            best.minUnlocks?.let { add(if (isEn) "Fewest unlocks $it (${short(best.minUnlocksDate)})" else "잠금 해제 최소 ${it}회 (${short(best.minUnlocksDate)})") }
            if (reward.bestStreak > 0) add(if (isEn) "Longest streak ${reward.bestStreak}d" else "최장 연속 ${reward.bestStreak}일")
        }
        if (parts.isNotEmpty()) {
            Text(
                text = (if (isEn) "My records · " else "내 기록 · ") + parts.joinToString(" · "),
                fontSize = DigitsTextSize.tiny,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun short(date: String?): String = date?.takeIf { it.length >= 10 }?.let { "${it.substring(5, 7).trimStart('0')}/${it.substring(8, 10).trimStart('0')}" } ?: "-"
