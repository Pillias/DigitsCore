package com.digitscore.app.data

import com.digitscore.app.data.entity.ScoreImpactHourEntity

/**
 * 최근 24시간 지수 변화를 원인별 P로 나눈 값 (대시보드 요약 3칸·앱 행).
 * 엔진이 1분 단위로 남긴 감점·회복(`score_impact_hourly`)을 범주별로 더하므로 비례 배분 없이 합이 맞습니다.
 */
data class CoreIndexBreakdown(
    /** 관리 앱 사용으로 깎인 P (0 이하) */
    val managedApps: Double,
    /** 그 외 앱(일반 + 면제 한도를 넘은 필수·공부 앱) 사용으로 깎인 P (0 이하) */
    val otherApps: Double,
    /** 잠금 해제 간격 점수의 순합 (감점이면 음수, 가점이면 양수) */
    val unlocks: Double,
    /** 화면 꺼짐 휴식·수면으로 오른 P (0 이상) */
    val rest: Double,
    /** 앱별 감점 (0 이하), 영향 큰 순서 */
    val perApp: List<Pair<String, Double>>
) {
    val appUse: Double get() = managedApps + otherApps
    val total: Double get() = appUse + unlocks + rest

    companion object {
        val EMPTY = CoreIndexBreakdown(0.0, 0.0, 0.0, 0.0, emptyList())

        /** [impacts]는 같은 기간의 시간별 행. [isManaged]는 패키지가 관리 등급인지. */
        fun from(impacts: List<ScoreImpactHourEntity>, isManaged: (String) -> Boolean): CoreIndexBreakdown {
            if (impacts.isEmpty()) return EMPTY
            val totals = impacts.filter { it.packageName.isEmpty() }
            val unlockRows = impacts.filter { it.packageName == StatisticsStore.UNLOCK_IMPACT_KEY }
            val appRows = impacts.filter { it.packageName.isNotEmpty() && it.packageName != StatisticsStore.UNLOCK_IMPACT_KEY }
            val totalLoss = totals.sumOf { it.loss }
            val totalRecovery = totals.sumOf { it.recovery }
            val unlockLoss = unlockRows.sumOf { it.loss }
            val unlockGain = unlockRows.sumOf { it.recovery }
            val perApp = appRows.groupBy { it.packageName }
                .mapValues { (_, rows) -> -rows.sumOf { it.loss } }
                .filterValues { it < 0.0 }
                .toList()
                .sortedBy { it.second }
            val appLoss = (totalLoss - unlockLoss).coerceAtLeast(0.0)
            val managed = perApp.filter { isManaged(it.first) }.sumOf { -it.second }.coerceAtMost(appLoss)
            return CoreIndexBreakdown(
                managedApps = -managed,
                otherApps = -(appLoss - managed),
                unlocks = unlockGain - unlockLoss,
                rest = (totalRecovery - unlockGain).coerceAtLeast(0.0),
                perApp = perApp
            )
        }
    }
}
