package com.digitscore.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.digitscore.app.R

/**
 * 연속 기록 단계별 도룡뇽 (1~4). 색은 같고 모양으로 구분합니다.
 * 1 유생(큰 아가미·꼬리 지느러미) → 2 다리가 남 → 3 몸이 길어짐 → 4 다 자라 꼬리로 원을 감음.
 * 얼굴(웃는 실눈·볼·꽃잎 아가미)은 앱 아이콘 도룡뇽과 같습니다. 아가미는 우파루파처럼 다 자라도 남습니다.
 * 그림: res/drawable/ic_salamander_stage1~4.xml (scripts/art/gen_salamander_stages.py → svg_to_vector.py 로 생성)
 */
@Composable
fun SalamanderStageIcon(stage: Int, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val res = when (stage.coerceIn(1, 4)) {
        1 -> R.drawable.ic_salamander_stage1
        2 -> R.drawable.ic_salamander_stage2
        3 -> R.drawable.ic_salamander_stage3
        else -> R.drawable.ic_salamander_stage4
    }
    Image(painter = painterResource(res), contentDescription = null, modifier = modifier.size(size))
}
