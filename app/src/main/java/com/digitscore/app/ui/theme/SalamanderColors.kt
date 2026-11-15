package com.digitscore.app.ui.theme

import androidx.compose.ui.graphics.Color

/** 지수 게이지 도룡뇽 색 (앱 아이콘 4종에서 뽑은 색). 지수 단계마다 몸·막대 색이 바뀝니다. */
object SalamanderColors {
    val Ring = Color(0xFFEA4E3E)        // 아직 먹지 않은 빨간 링

    data class Tone(val rim: Color, val body: Color, val stripe: Color, val stem: Color)

    val Stable = Tone(Color(0xFF26B566), Color(0xFF31D379), Color(0xFF98E9BC), Color(0xFF4DDE8D))
    val Fair = Tone(Color(0xFF24B063), Color(0xFF2ECC72), Color(0xFF96E5B8), Color(0xFFF8D33E))
    val Caution = Tone(Color(0xFF6A9E36), Color(0xFF80C044), Color(0xFFC0E0A2), Color(0xFFF39E35))
    val Rest = Tone(Color(0xFF8D5437), Color(0xFF88B24A), Color(0xFFC4D9A5), Color(0xFFF36252))
}
