package com.digitscore.app.engine

/**
 * 잠금 해제 간격 정산에 필요한, 정산 호출 사이에 이어지는 값.
 * [CumulativeTimeline.advance]가 갱신하고 호출자(`CumulativeScoreStore`)가 체크포인트와 함께 저장합니다.
 */
data class UnlockPacingLedger(
    /** 마지막으로 끝난 앱 사용(화면 켜진 사용)의 시각. 다음 잠금 해제의 '화면을 꺼 둔 시간'의 시작점입니다. */
    val lastUseEndAt: Long = 0L,
    /** 마지막으로 수면으로 정산된 분의 끝 시각. 이 값이 lastUseEndAt보다 뒤면 다음 잠금 해제는 뉴트럴입니다. */
    val lastSleepAt: Long = 0L,
    /** 하루 상한을 세는 논리적 날짜(05:00 기준). */
    val dayKey: String = "",
    val dayLoss: Double = 0.0,
    val dayGain: Double = 0.0
)

/**
 * 잠금 해제 간격 점수 (DEBATE 안건 18, 계산 설정 v3).
 *
 * 잠금 해제 1회마다 직전에 화면을 꺼 둔 시간 g로 판정합니다. 기준 T0 = 12min(시간당 5회).
 * - g < 10s: 계산 안 함 (자동 꺼짐 직후 바로 켬)
 * - 10s ≤ g < T0: −maxLoss × (1 − g/T0) × speedDown
 * - T0 ≤ g < 2·T0: +maxGain × (g − T0)/T0 × speedUp
 * - g ≥ 2·T0: +maxGain × speedUp (상한. 오래 쉰 것은 휴식 회복이 이미 보상)
 * 화면이 켜진 시간에는 잠금 해제가 없으므로 영향이 없습니다.
 */
object UnlockPacingPolicy {
    const val MIN_GAP_MILLIS = 10_000L
    /** 잠금 해제 직후 이 시간 안에 처음 연 앱이 통화·내비이면 뉴트럴입니다. */
    const val FIRST_APP_WINDOW_MILLIS = 60_000L

    fun rawDelta(gapMillis: Long, signal: Double, config: CumulativeScoreConfig = CumulativeScoreConfig.CURRENT): Double {
        if (gapMillis < MIN_GAP_MILLIS) return 0.0
        val t0 = config.unlockReferenceMinutes * 60_000.0
        val g = gapMillis.toDouble()
        return when {
            g < t0 -> -config.unlockMaxLoss * (1.0 - g / t0) * CumulativeScoreEngine.speedDown(signal, config)
            g < 2 * t0 -> config.unlockMaxGain * ((g - t0) / t0) * CumulativeScoreEngine.speedUp(signal, config)
            else -> config.unlockMaxGain * CumulativeScoreEngine.speedUp(signal, config)
        }
    }

    /** 하루 상한을 적용한 Δ와 갱신된 장부. */
    fun capped(delta: Double, ledger: UnlockPacingLedger, dayKey: String, config: CumulativeScoreConfig): Pair<Double, UnlockPacingLedger> {
        val base = if (ledger.dayKey == dayKey) ledger else ledger.copy(dayKey = dayKey, dayLoss = 0.0, dayGain = 0.0)
        return if (delta < 0) {
            val applied = -minOf(-delta, (config.unlockDailyLossCap - base.dayLoss).coerceAtLeast(0.0))
            applied to base.copy(dayLoss = base.dayLoss - applied)
        } else {
            val applied = minOf(delta, (config.unlockDailyGainCap - base.dayGain).coerceAtLeast(0.0))
            applied to base.copy(dayGain = base.dayGain + applied)
        }
    }
}
