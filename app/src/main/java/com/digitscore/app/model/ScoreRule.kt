package com.digitscore.app.model

/**
 * 점수 계산 규칙 매개변수
 * @property initialScore 기본 시작 점수 (기본 100)
 * @property yesterdayPenalty 전날 과사용으로 인한 실제 시작 감점치
 * @property isYesterdayPenaltyEnabled 전날 과사용 시작 페널티(디톡스 부채) 활성화 여부 (기본 true)
 * @property yesterdayPenaltyTriggerScore 디톡스 부채가 발생하는 전날 점수 기준
 * @property yesterdayPenaltyRate 기준 미달 1점당 다음 날 감점 비율
 * @property maxYesterdayPenalty 디톡스 부채 최대 감점 상한
 * @property distractingWeightPerMinute 방해 앱 사용 1분당 감점치 (기본 0.8점 감점)
 * @property productiveBonusPerMinute 생산성 앱 사용 1분당 가산치 (기본 0.2점 가산)
 * @property idleBonusPer10Minutes 화면 꺼짐(미사용) 10분당 회복 점수 (기본 0.25점 가산, 1시간에 1.5점)
 * @property maxIdleBonus 하루 동안 획득 가능한 화면 미사용 회복 보너스 최대 상한선 (기본 15점)
 * @property maxProductiveBonus 하루 동안 획득 가능한 생산성 보너스 최대 상한선 (기본 15점)
 * @property unlockPenaltyThreshold 하루 기준 언락 횟수 초과 허용치 (기본 25회)
 * @property unlockPenaltyPerCount 기준 초과 언락 1회당 감점 (기본 0.5점 감점)
 * @property lateNightMultiplier 심야 시간(24시~05시) 방해 앱 감점 가속 배수 (기본 1.6배)
 * @property isLogAccelerationEnabled 장시간 사용 시 로그(Log) 가속 감점 적용 여부 (기본 true)
 * @property logAccelerationThresholdMinutes 로그 가속이 시작되는 앱별 누적 사용 기준 시간 (기본 60분)
 * @property logAccelerationScaleMinutes 로그 가속도 곡선 스케일 매개변수 (클수록 완만함)
 * @property minScoreBoundary 최저 점수 하한선 (0)
 * @property maxScoreBoundary 최고 점수 상한선 (100)
 */
data class ScoreRule(
    val initialScore: Float = 100f,
    val yesterdayPenalty: Float = 0f,
    val isYesterdayPenaltyEnabled: Boolean = true,
    val yesterdayPenaltyTriggerScore: Int = 60,
    val yesterdayPenaltyRate: Float = 0.2f,
    val maxYesterdayPenalty: Float = 10f,
    val distractingWeightPerMinute: Float = 0.6f,
    val productiveBonusPerMinute: Float = 0.2f,
    val idleBonusPer10Minutes: Float = 0.25f,
    val maxIdleBonus: Float = 15.0f,
    val maxProductiveBonus: Float = 15.0f,
    val unlockPenaltyThreshold: Int = 30,
    val unlockPenaltyPerCount: Float = 0.3f,
    val lateNightMultiplier: Float = 1.5f,
    val isLogAccelerationEnabled: Boolean = true,
    val logAccelerationThresholdMinutes: Float = 60.0f,
    val logAccelerationScaleMinutes: Float = 120.0f,
    val minScoreBoundary: Float = 0f,
    val maxScoreBoundary: Float = 100f
)
