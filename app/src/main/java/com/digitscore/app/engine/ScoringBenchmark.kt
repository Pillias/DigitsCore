package com.digitscore.app.engine

import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.model.AppUsage
import com.digitscore.app.model.ScoreRule

/**
 * 건강 진단 기준이 아닌, 공개 조사 수치를 점수 조정에 활용하기 위한 기준 시나리오입니다.
 * 전체 사용시간을 방해/생산성/중립 앱으로 나누는 비율과 언락·심야 시간은 제품 보정 가정입니다.
 */
enum class ScoringBenchmark(
    val presetModeId: String,
    private val koreanTitle: String,
    private val englishTitle: String,
    private val koreanSourceLabel: String,
    private val englishSourceLabel: String,
    val totalScreenMinutes: Long,
    val distractingMinutes: Long,
    val productiveMinutes: Long,
    val idleMinutes: Long,
    val unlockCount: Int,
    val lateNightMinutes: Long,
    val targetScore: Int = 60
) {
    WORKER(
        presetModeId = "worker",
        koreanTitle = "일반 성인·직장인 기준",
        englishTitle = "Adult / Office Worker Benchmark",
        koreanSourceLabel = "KISDI 2023 조사: 스마트폰 하루 평균 126분(음성통화 제외)",
        englishSourceLabel = "KISDI 2023: 126 average smartphone minutes/day, excluding calls",
        totalScreenMinutes = 126,
        distractingMinutes = 75,
        productiveMinutes = 30,
        idleMinutes = 600,
        unlockCount = 45,
        lateNightMinutes = 10
    ),
    STUDENT(
        presetModeId = "study",
        koreanTitle = "학생·청소년 기준",
        englishTitle = "Student / Teen Benchmark",
        koreanSourceLabel = "KISDI 2021 조사: 10대 스마트폰 하루 평균 약 142분",
        englishSourceLabel = "KISDI 2021: about 142 average smartphone minutes/day for teens",
        totalScreenMinutes = 142,
        distractingMinutes = 90,
        productiveMinutes = 35,
        idleMinutes = 600,
        unlockCount = 55,
        lateNightMinutes = 15
    ),
    KIDS(
        presetModeId = "kids",
        koreanTitle = "어린이 기준",
        englishTitle = "Kids Benchmark",
        koreanSourceLabel = "KISDI 2024 조사: 가정의 스마트기기 하루 허용시간 평균 106분",
        englishSourceLabel = "KISDI 2024: 106 average allowed smart-device minutes/day in households",
        totalScreenMinutes = 106,
        distractingMinutes = 80,
        productiveMinutes = 20,
        idleMinutes = 720,
        unlockCount = 35,
        lateNightMinutes = 0
    );

    val title: String get() = if (java.util.Locale.getDefault().language == "en") englishTitle else koreanTitle
    val sourceLabel: String get() = if (java.util.Locale.getDefault().language == "en") englishSourceLabel else koreanSourceLabel

    fun evaluate(rule: ScoreRule): ScoreDetail {
        val usages = buildList {
            if (distractingMinutes > 0) {
                add(
                    AppUsage(
                        packageName = "benchmark.distracting",
                        appName = "방해 앱",
                        usageTimeMillis = distractingMinutes * 60_000L,
                        categoryType = AppCategoryType.DISTRACTING,
                        lateNightUsageMillis = lateNightMinutes.coerceAtMost(distractingMinutes) * 60_000L
                    )
                )
            }
            if (productiveMinutes > 0) {
                add(
                    AppUsage(
                        packageName = "benchmark.productive",
                        appName = "생산성 앱",
                        usageTimeMillis = productiveMinutes * 60_000L,
                        categoryType = AppCategoryType.PRODUCTIVE
                    )
                )
            }
            val neutralMinutes = (totalScreenMinutes - distractingMinutes - productiveMinutes).coerceAtLeast(0L)
            if (neutralMinutes > 0) {
                add(
                    AppUsage(
                        packageName = "benchmark.neutral",
                        appName = "중립 앱",
                        usageTimeMillis = neutralMinutes * 60_000L,
                        categoryType = AppCategoryType.NEUTRAL
                    )
                )
            }
        }
        return ScoreCalculator.calculateScore(usages, idleMinutes, unlockCount, rule)
    }

    companion object {
        fun forPreset(presetModeId: String): ScoringBenchmark? =
            entries.firstOrNull { it.presetModeId == presetModeId }
    }
}
