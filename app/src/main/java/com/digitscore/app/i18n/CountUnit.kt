package com.digitscore.app.i18n

/**
 * 횟수 단위 (잠금 해제·앱 실행·짧은 확인 등).
 * 한국어는 '회', 영어는 단위 없이 숫자만 씁니다 (라벨이 이미 무엇의 횟수인지 말해 주므로).
 * 'x'는 '배'로 읽히기 쉬워 쓰지 않습니다 (2026-10-28 사용자 결정).
 */
fun countUnit(): String = if (AppLocale.isCurrentEnglish) "" else "회"

/** 숫자 + 횟수 단위. 예) 48회 / 48 */
fun countText(n: Number): String = "$n${countUnit()}"
