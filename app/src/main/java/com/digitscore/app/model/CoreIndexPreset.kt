package com.digitscore.app.model

import java.util.Locale

enum class PresetSensitivity(
    private val koreanLabel: String,
    private val englishLabel: String
) {
    RELAXED("완화", "Relaxed"),
    STANDARD("표준", "Standard"),
    SENSITIVE("강화", "Sensitive");

    val label: String
        get() = if (Locale.getDefault().language == "en") englishLabel else koreanLabel
}

/** 최근 24시간 코어 지수에 직접 적용되는 프리셋입니다. */
enum class CoreIndexPreset(
    val id: String,
    private val koreanTitle: String,
    private val englishTitle: String,
    private val koreanDescription: String,
    private val englishDescription: String,
    val baseLoadMultiplier: Double,
    val categoryLoadMultiplier: Double,
    val acuteLoadMultiplier: Double,
    val lateNightMultiplier: Double,
    val lateNightCarryoverRatio: Double,
    val unlockThreshold: Int,
    val unlockLoadPerExcess: Double,
    val continuousLoadStartMinutes: Double,
    val recoveryHalfLifeMinutes: Double,
    val overallSensitivity: PresetSensitivity,
    val continuousSensitivity: PresetSensitivity,
    val lateNightSensitivity: PresetSensitivity,
    val unlockSensitivity: PresetSensitivity,
    val lateNightTier1Multiplier: Double = lateNightMultiplier,
    val lateNightTier2Multiplier: Double = lateNightMultiplier * 1.5,
    val sleepRecoveryHalfLifeMinutes: Double = 3600.0
) {
    BALANCED(
        id = "balanced",
        koreanTitle = "일상 균형",
        englishTitle = "Everyday Balance",
        koreanDescription = "일반적인 사용 흐름을 약 70점 중심으로 살펴보는 기본 모드",
        englishDescription = "The default profile centered around a Core Index near 70 for typical use",
        baseLoadMultiplier = 1.0,
        categoryLoadMultiplier = 1.0,
        acuteLoadMultiplier = 1.0,
        lateNightMultiplier = 1.25,
        lateNightCarryoverRatio = 0.20,
        unlockThreshold = 20,
        unlockLoadPerExcess = 0.12,
        continuousLoadStartMinutes = 30.0,
        recoveryHalfLifeMinutes = 90.0,
        overallSensitivity = PresetSensitivity.STANDARD,
        continuousSensitivity = PresetSensitivity.STANDARD,
        lateNightSensitivity = PresetSensitivity.STANDARD,
        unlockSensitivity = PresetSensitivity.STANDARD,
        lateNightTier1Multiplier = 1.4,
        lateNightTier2Multiplier = 2.0,
        sleepRecoveryHalfLifeMinutes = 3600.0
    ),
    FOCUS(
        id = "focus",
        koreanTitle = "집중 유지",
        englishTitle = "Sustained Focus",
        koreanDescription = "성장 앱의 기본 부하는 낮추고 산만한 앱·연속 사용·잦은 확인은 더 민감하게 보는 모드",
        englishDescription = "Lighter base load for growth apps, with stronger response to managed apps, long sessions, and frequent checks",
        baseLoadMultiplier = 0.85,
        categoryLoadMultiplier = 1.20,
        acuteLoadMultiplier = 1.15,
        lateNightMultiplier = 1.25,
        lateNightCarryoverRatio = 0.20,
        unlockThreshold = 20,
        unlockLoadPerExcess = 0.14,
        continuousLoadStartMinutes = 25.0,
        recoveryHalfLifeMinutes = 105.0,
        overallSensitivity = PresetSensitivity.RELAXED,
        continuousSensitivity = PresetSensitivity.SENSITIVE,
        lateNightSensitivity = PresetSensitivity.STANDARD,
        unlockSensitivity = PresetSensitivity.SENSITIVE,
        lateNightTier1Multiplier = 1.5,
        lateNightTier2Multiplier = 2.2,
        sleepRecoveryHalfLifeMinutes = 3600.0
    ),
    SCREEN_REST(
        id = "screen_rest",
        koreanTitle = "화면 휴식",
        englishTitle = "Screen Rest",
        koreanDescription = "앱 종류와 관계없이 전체 화면시간과 긴 연속 사용을 더 민감하게 보는 모드",
        englishDescription = "More sensitive to total screen time and long continuous sessions regardless of app type",
        baseLoadMultiplier = 1.25,
        categoryLoadMultiplier = 1.0,
        acuteLoadMultiplier = 1.30,
        lateNightMultiplier = 1.35,
        lateNightCarryoverRatio = 0.25,
        unlockThreshold = 25,
        unlockLoadPerExcess = 0.10,
        continuousLoadStartMinutes = 20.0,
        recoveryHalfLifeMinutes = 120.0,
        overallSensitivity = PresetSensitivity.SENSITIVE,
        continuousSensitivity = PresetSensitivity.SENSITIVE,
        lateNightSensitivity = PresetSensitivity.SENSITIVE,
        unlockSensitivity = PresetSensitivity.RELAXED,
        lateNightTier1Multiplier = 1.6,
        lateNightTier2Multiplier = 2.4,
        sleepRecoveryHalfLifeMinutes = 3600.0
    ),
    NIGHT_BALANCE(
        id = "night_balance",
        koreanTitle = "심야 균형",
        englishTitle = "Night Balance",
        koreanDescription = "자정부터 새벽 5시까지 몰입 관리 앱 사용을 특히 민감하게 보는 모드",
        englishDescription = "Especially sensitive to Immersion Management app use between midnight and 5 a.m.",
        baseLoadMultiplier = 1.0,
        categoryLoadMultiplier = 1.0,
        acuteLoadMultiplier = 1.0,
        lateNightMultiplier = 1.75,
        lateNightCarryoverRatio = 0.35,
        unlockThreshold = 20,
        unlockLoadPerExcess = 0.12,
        continuousLoadStartMinutes = 30.0,
        recoveryHalfLifeMinutes = 90.0,
        overallSensitivity = PresetSensitivity.STANDARD,
        continuousSensitivity = PresetSensitivity.STANDARD,
        lateNightSensitivity = PresetSensitivity.SENSITIVE,
        unlockSensitivity = PresetSensitivity.STANDARD,
        lateNightTier1Multiplier = 1.75,
        lateNightTier2Multiplier = 2.6,
        sleepRecoveryHalfLifeMinutes = 4800.0
    ),
    FAMILY(
        id = "family",
        koreanTitle = "가족 보호",
        englishTitle = "Family Guard",
        koreanDescription = "전체 사용·긴 세션·심야 사용·잦은 확인을 모두 엄격하게 살펴보는 모드",
        englishDescription = "A stricter profile for total use, long sessions, late-night use, and frequent checks",
        baseLoadMultiplier = 1.15,
        categoryLoadMultiplier = 1.25,
        acuteLoadMultiplier = 1.30,
        lateNightMultiplier = 1.75,
        lateNightCarryoverRatio = 0.35,
        unlockThreshold = 15,
        unlockLoadPerExcess = 0.15,
        continuousLoadStartMinutes = 20.0,
        recoveryHalfLifeMinutes = 120.0,
        overallSensitivity = PresetSensitivity.SENSITIVE,
        continuousSensitivity = PresetSensitivity.SENSITIVE,
        lateNightSensitivity = PresetSensitivity.SENSITIVE,
        unlockSensitivity = PresetSensitivity.SENSITIVE,
        lateNightTier1Multiplier = 1.8,
        lateNightTier2Multiplier = 2.8,
        sleepRecoveryHalfLifeMinutes = 4800.0
    );

    val title: String
        get() = if (Locale.getDefault().language == "en") englishTitle else koreanTitle

    val description: String
        get() = if (Locale.getDefault().language == "en") englishDescription else koreanDescription

    val sensitivitySummary: String
        get() = if (Locale.getDefault().language == "en") {
            "Overall ${overallSensitivity.label} · Continuous ${continuousSensitivity.label} · " +
                "Night ${lateNightSensitivity.label} · Unlocks ${unlockSensitivity.label}"
        } else {
            "전체 사용 ${overallSensitivity.label} · 연속 사용 ${continuousSensitivity.label} · " +
                "심야 ${lateNightSensitivity.label} · 언락 ${unlockSensitivity.label}"
        }

    companion object {
        fun fromId(id: String): CoreIndexPreset = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

val CoreIndexPreset.defaultScoringConfig: CoreIndexScoringConfig
    get() = CoreIndexScoringConfig(
        continuousLoadStartMinutes = continuousLoadStartMinutes,
        sessionJoinGapMillis = 90_000L,
        lateNightTier1StartHour = 23,
        lateNightTier1EndHour = 1,
        lateNightTier1Multiplier = lateNightTier1Multiplier,
        lateNightTier2StartHour = 1,
        lateNightTier2EndHour = 5,
        lateNightTier2Multiplier = lateNightTier2Multiplier,
        isSleepFreezeEnabled = true,
        sleepDetectionThresholdMinutes = 150L,
        sleepRecoveryHalfLifeMinutes = sleepRecoveryHalfLifeMinutes,
        unlockThreshold = unlockThreshold,
        recoveryHalfLifeMinutes = recoveryHalfLifeMinutes,
        chronicCarryoverThresholdMinutes = 60.0,
        chronicCarryoverRatio = lateNightCarryoverRatio
    )

