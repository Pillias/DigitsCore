package com.digitscore.app.data

import androidx.room.withTransaction
import com.digitscore.app.data.entity.CumulativeScoreStateEntity
import com.digitscore.app.engine.*
import org.json.JSONObject
import java.time.ZoneId
import kotlin.math.roundToInt

data class CumulativeRecord(
    val checkpoint: CumulativeCheckpoint,
    val configVersion: Int = CumulativeScoreConfig.CURRENT.version,
    val startedAt: Long = checkpoint.timestamp,
    val wakeConfirmedAt: Long = 0L,
    val restingConfirmedUntil: Long = 0L,
    val briefingHandledAt: Long = 0L,
    val suggestionSnoozes: Map<String, Long> = emptyMap(),
    val lateSleepAnchorAt: Long = 0L
) {
    fun encode(): String = JSONObject().apply {
        put("version", configVersion)
        put("timestamp", checkpoint.timestamp)
        put("startedAt", startedAt)
        put("signal", checkpoint.state.signal)
        put("momentum", checkpoint.state.useMomentumMinutes)
        put("rest", checkpoint.state.awakeRestMinutes)
        put("burden", checkpoint.state.recoveryBurden)
        put("exemptMomentum", checkpoint.state.exemptMomentumMinutes)
        put("studyMomentum", checkpoint.state.studyMomentumMinutes)
        put("wake", wakeConfirmedAt)
        put("restingUntil", restingConfirmedUntil)
        put("briefing", briefingHandledAt)
        put("suggestions", JSONObject(suggestionSnoozes))
        put("lateSleepAnchor", lateSleepAnchorAt)
    }.toString()

    companion object {
        fun decode(payload: String): CumulativeRecord {
            val obj = JSONObject(payload)
            val ver = obj.optInt("version", 1)
            require(ver in 1..CumulativeScoreConfig.CURRENT.version) { "Unsupported score configuration" }
            val timestamp = obj.getLong("timestamp")
            require(timestamp >= 0L)
            val s = obj.getDouble("signal")
            val q = obj.getDouble("momentum")
            val r = obj.getDouble("rest")
            val b = obj.getDouble("burden")
            val em = obj.optDouble("exemptMomentum", 0.0)
            val sm = obj.optDouble("studyMomentum", 0.0)
            require(s.isFinite() && s in 0.0..100.0)
            require(listOf(q, r, b, em, sm).all { it.isFinite() && it >= 0.0 })
            // v1로 저장된 기록 중 상당수는 이미 v2 기기에서 v2 점수로 계속 갱신된 상태라
            // 저장 번호만으로 v1 signal인지 구분할 수 없습니다. 값은 그대로 이어 쓰고, 다음 저장부터
            // 현재 버전을 기록해 이후 모델 변경 때는 정확히 변환할 수 있게 합니다.
            return CumulativeRecord(CumulativeCheckpoint(timestamp, CumulativeScoreState(s, q, r, b, em, sm)),
                CumulativeScoreConfig.CURRENT.version, obj.getLong("startedAt"), obj.optLong("wake"),
                obj.optLong("restingUntil"), obj.optLong("briefing"),
                obj.optJSONObject("suggestions")?.let { map ->
                    map.keys().asSequence().associateWith { map.getLong(it) }
                } ?: emptyMap(), obj.optLong("lateSleepAnchor"))
        }
    }
}

/** The service is the sole advancing writer. UI/notification/forecast never advances this cursor. */
object CumulativeScoreStore {
    /** 수면 시간대는 최근 14일 상세 세션으로 하루 1회만 학습합니다. (db, 날짜+시간대, 학습 결과) */
    @Volatile
    private var windowCache: Triple<DigitsDatabase, String, RestWindow>? = null
    private const val REST_WINDOW_LEARNING_DAYS = 14L

    fun clearWindowCache() {
        windowCache = null
    }

    private suspend fun learnedRestWindow(db: DigitsDatabase, nowMillis: Long, zone: ZoneId): RestWindow {
        val key = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).minusHours(5).toLocalDate().toString() + "@" + zone.id
        windowCache?.let { (cachedDb, cachedKey, window) ->
            if (cachedDb === db && cachedKey == key) return window
        }
        val since = nowMillis - REST_WINDOW_LEARNING_DAYS * 24 * 3_600_000L
        val history = db.foregroundUsageSessionDao().getSince(since).map {
            CumulativeUse(it.startTimeMillis, it.endTimeMillis, managed = false,
                openingId = "${it.packageName}:${it.sessionStartTimeMillis}", openedAt = it.sessionStartTimeMillis)
        }
        return RestPhasePolicy.learn(history, zone).also { windowCache = Triple(db, key, it) }
    }

    suspend fun getLearnedBedtimeMinute(
        db: DigitsDatabase,
        nowMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Int? {
        val window = learnedRestWindow(db, nowMillis, zone)
        return if (window.learnedNights > 0) window.bedtimeMinute else null
    }

    suspend fun update(
        db: DigitsDatabase,
        nowMillis: Long,
        coverageStartMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        penaltyFreeWindows: List<LongRange> = emptyList()
    ): Pair<CumulativeRecord, RollingScoreDetail> = db.withTransaction {
        val dao = db.cumulativeScoreStateDao()
        val safeEnd = nowMillis - 2 * CumulativeTimeline.MINUTE
        val settledEnd = safeEnd - Math.floorMod(safeEnd, CumulativeTimeline.MINUTE)
        val stored = dao.get()?.let { CumulativeRecord.decode(it.payload) }
        // 시계가 뒤로 바뀌어 체크포인트가 미래에 있으면 정산이 그 시각까지 멈춥니다.
        // 점수 상태는 그대로 두고 시각만 되돌린 뒤, 미래 시각으로 남은 기록을 정리합니다.
        val saved = stored?.let { recoverFromClockRollback(it, settledEnd, nowMillis) }
        // 역행을 감지했을 때, 그리고 프로세스 시작 후 첫 정산 때 한 번 미래 시각 기록을 정리합니다.
        if ((stored != null && saved !== stored) || !futureRowsChecked) {
            discardFutureRows(db, nowMillis)
            futureRowsChecked = true
        }
        val record = saved ?: run {
            val previousScore = db.coreIndexSampleDao().getLatestBefore(nowMillis)?.exactScore ?: 75.0
            CumulativeRecord(CumulativeCheckpoint(settledEnd, CumulativeScoreState(
                signal = CumulativeScoreConfig.CURRENT.signalForScore(previousScore))))
        }
        // Do not fabricate rest during tracking/permission gaps, clock rollback or missing source history.
        val processStart = maxOf(record.checkpoint.timestamp, coverageStartMillis.let {
            it - Math.floorMod(it, CumulativeTimeline.MINUTE) + CumulativeTimeline.MINUTE
        }).coerceAtMost(maxOf(settledEnd, record.checkpoint.timestamp))
        val start = record.checkpoint.copy(timestamp = processStart)
        fun asUse(it: com.digitscore.app.data.entity.ForegroundUsageSessionEntity) =
            CumulativeUse(
                start = it.startTimeMillis,
                end = it.endTimeMillis,
                managed = it.effectiveCategoryLevel >= 3,
                openingId = "${it.packageName}:${it.sessionStartTimeMillis}",
                openedAt = it.sessionStartTimeMillis,
                packageName = it.packageName,
                effectivePackageName = it.effectivePackageName,
                exempt = (it.effectiveCategoryLevel <= 0),
                studyExempt = com.digitscore.app.model.AppCategoryPolicy.isStudyOrReadingPackage(it.effectivePackageName.ifBlank { it.packageName }),
                unlimitedExempt = com.digitscore.app.model.AppCategoryPolicy.isNavigationOrCallPackage(it.effectivePackageName.ifBlank { it.packageName })
            )
        // Incremental scoring query: only cursor look-behind, not a fresh full-day/full-week score replay.
        val uses = db.foregroundUsageSessionDao().getSince(processStart - 5 * 60_000L).map(::asUse)
        val movements = mutableListOf<ScoreMovement>()
        val window = learnedRestWindow(db, nowMillis, zone)
        val lateAnchor = RestPhasePolicy.lateSleepAnchor(uses, zone) ?: record.lateSleepAnchorAt
        val next = CumulativeTimeline.advance(start, settledEnd, uses, { ts ->
            RestPhasePolicy.activityAt(
                timestamp = ts,
                window = window,
                zone = zone,
                wakeConfirmedAt = record.wakeConfirmedAt,
                restingConfirmedUntil = record.restingConfirmedUntil,
                lateSleepAnchorAt = lateAnchor
            )
        }, onMovement = { movements.add(it) }, penaltyFreeWindows = penaltyFreeWindows)
        // Attribution and cursor commit together: re-polling/restarting cannot charge twice.
        StatisticsStore.recordMovements(db, movements)
        val updated = record.copy(checkpoint = next, lateSleepAnchorAt = lateAnchor)
        dao.put(CumulativeScoreStateEntity(payload = updated.encode()))
        updated to detail(updated, sleeping = false)
    }

    fun detail(record: CumulativeRecord, sleeping: Boolean = false): RollingScoreDetail {
        val state = record.checkpoint.state
        val score = CumulativeScoreConfig.CURRENT.displayed(state.signal)
        val flow = when {
            sleeping -> ScoreFlow.STEADY
            state.awakeRestMinutes > 0 -> ScoreFlow.RECOVERING
            state.useMomentumMinutes > 0 -> ScoreFlow.USING
            else -> ScoreFlow.STEADY
        }
        return RollingScoreDetail(score.roundToInt(), score, state.recoveryBurden,
            state.useMomentumMinutes, 0.0, 1f, flow, "", 0L,
            state.useMomentumMinutes.toLong(), state.awakeRestMinutes.toLong(),
            effectiveRecoveryMinutes = state.awakeRestMinutes.toLong())
    }

    /** 체크포인트가 이 값보다 미래에 있으면 시계 역행으로 봅니다. */
    internal const val CLOCK_ROLLBACK_TOLERANCE_MILLIS = 5 * CumulativeTimeline.MINUTE

    /**
     * 시계 역행 복구: 체크포인트가 settledEnd보다 5분 넘게 미래이면 점수 상태(state)는 유지하고
     * 시각만 settledEnd로 되돌립니다. 미래 시각으로 기록된 기상/휴식/브리핑/늦은 취침 기준은 0으로 비웁니다.
     * 역행이 아니면 같은 객체를 그대로 반환합니다.
     */
    internal fun recoverFromClockRollback(record: CumulativeRecord, settledEnd: Long, nowMillis: Long): CumulativeRecord {
        if (record.checkpoint.timestamp <= settledEnd + CLOCK_ROLLBACK_TOLERANCE_MILLIS) return record
        fun pastOrZero(ts: Long) = if (ts > nowMillis) 0L else ts
        return record.copy(
            checkpoint = record.checkpoint.copy(timestamp = settledEnd),
            startedAt = minOf(record.startedAt, settledEnd),
            wakeConfirmedAt = pastOrZero(record.wakeConfirmedAt),
            restingConfirmedUntil = pastOrZero(record.restingConfirmedUntil),
            briefingHandledAt = pastOrZero(record.briefingHandledAt),
            lateSleepAnchorAt = pastOrZero(record.lateSleepAnchorAt)
        )
    }

    @Volatile
    private var futureRowsChecked = false

    private suspend fun discardFutureRows(db: DigitsDatabase, nowMillis: Long) {
        val currentHour = statisticHour(nowMillis)
        db.coreIndexSampleDao().deleteAfter(nowMillis)
        db.statisticsDao().deleteScoresFrom(currentHour, nowMillis)
        db.statisticsDao().deleteImpactsFrom(currentHour, nowMillis)
        db.statisticsDao().deleteUsageAfter(currentHour)
        db.statisticsDao().state()?.takeIf { it.processedUntil > nowMillis }?.let {
            db.statisticsDao().putState(it.copy(processedUntil = nowMillis))
        }
        clearWindowCache()
    }

    /** 기상을 확정합니다. 같은 날 더 이른 확정 시각이 있으면 그 값을 유지합니다. */
    suspend fun confirmWake(
        db: DigitsDatabase,
        wakeAtMillis: Long,
        acknowledge: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault()
    ) = db.withTransaction {
        val record = db.cumulativeScoreStateDao().get()?.let { CumulativeRecord.decode(it.payload) }
            ?: return@withTransaction
        fun localDate(ts: Long) = java.time.Instant.ofEpochMilli(ts).atZone(zone).minusHours(5).toLocalDate()
        val sameDayEarlier = record.wakeConfirmedAt in 1..wakeAtMillis &&
            localDate(record.wakeConfirmedAt) == localDate(wakeAtMillis)
        val next = record.copy(
            wakeConfirmedAt = if (sameDayEarlier) record.wakeConfirmedAt else wakeAtMillis,
            restingConfirmedUntil = 0,
            briefingHandledAt = if (acknowledge) wakeAtMillis else record.briefingHandledAt
        )
        // No historical score rollback for an incorrect sleep/wake guess.
        db.cumulativeScoreStateDao().put(CumulativeScoreStateEntity(payload = next.encode()))
    }

    suspend fun snoozeSuggestion(db: DigitsDatabase, packageName: String, until: Long) = db.withTransaction {
        val record = db.cumulativeScoreStateDao().get()?.let { CumulativeRecord.decode(it.payload) }
            ?: return@withTransaction
        db.cumulativeScoreStateDao().put(CumulativeScoreStateEntity(payload = record.copy(
            suggestionSnoozes = record.suggestionSnoozes + (packageName to until)).encode()))
    }

    fun awakeRecoveryMinutes(state: CumulativeScoreState, targetScore: Int): Int? {
        var copy = state
        for (minute in 1..720) {
            copy = CumulativeScoreEngine.advance(copy, CumulativeActivity.AWAKE_REST, 1.0)
            if (CumulativeScoreConfig.CURRENT.displayed(copy.signal) >= targetScore) return minute
        }
        return null
    }
}
