package com.digitscore.app.model

/**
 * 점수 계산 규칙 매개변수
 * @property initialScore 기본 시작 점수 (기본 100)
 * @property distractingWeightPerMinute 방해 앱 사용 1분당 감점치 (기본 0.5점 감점)
 * @property productiveBonusPerMinute 생산성 앱 사용 1분당 가산치 (기본 0.3점 가산)
 * @property idleBonusPer10Minutes 화면 꺼짐(미사용) 10분당 회복 점수 (기본 1.0점 가산)
 * @property unlockPenaltyThreshold 하루 기준 언락 횟수 초과 허용치 (기본 30회)
 * @property unlockPenaltyPerCount 기준 초과 언락 1회당 감점 (기본 0.2점 감점)
 * @property minScoreBoundary 최저 점수 하한선 (0)
 * @property maxScoreBoundary 최고 점수 상한선 (100)
 */
data class ScoreRule(
    val initialScore: Float = 100f,
    val distractingWeightPerMinute: Float = 0.5f,
    val productiveBonusPerMinute: Float = 0.3f,
    val idleBonusPer10Minutes: Float = 1.0f,
    val unlockPenaltyThreshold: Int = 30,
    val unlockPenaltyPerCount: Float = 0.2f,
    val minScoreBoundary: Float = 0f,
    val maxScoreBoundary: Float = 100f
)
