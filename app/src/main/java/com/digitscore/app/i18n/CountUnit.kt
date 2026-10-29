package com.digitscore.app.i18n

/**
 * 횟수 단위 (잠금 해제·앱 실행·짧은 확인 등).
 * 한국어는 '회', 영어는 단위 없이 숫자만 씁니다 (라벨이 이미 무엇의 횟수인지 말해 주므로).
 * 'x'는 '배'로 읽히기 쉬워 쓰지 않습니다 (2026-10-28 사용자 결정).
 */
fun countUnit(): String = if (AppLocale.isCurrentEnglish) "" else "회"

/** 숫자는 그대로, '회'만 작은 글씨 (min과 같은 0.7배). 영어는 숫자만. */
fun formatCountSmallUnit(n: Int, unitSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit(0.7f, androidx.compose.ui.unit.TextUnitType.Em)):
    androidx.compose.ui.text.AnnotatedString = androidx.compose.ui.text.buildAnnotatedString {
    append("$n")
    val unit = countUnit()
    if (unit.isNotEmpty()) {
        pushStyle(androidx.compose.ui.text.SpanStyle(fontSize = unitSize, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        append(unit); pop()
    }
}

/** 숫자 + 횟수 단위. 예) 48회 / 48 */
fun countText(n: Number): String = "$n${countUnit()}"
