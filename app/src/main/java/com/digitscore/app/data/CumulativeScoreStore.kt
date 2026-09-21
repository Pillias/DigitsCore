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
        put("wake", wakeConfirmedAt)
        put("restingUntil", restingConfirmedUntil)
        put("briefing", briefingHandledAt)
        put("suggestions", JSONObject(suggestionSnoozes))
        put("lateSleepAnchor", lateSleepAnchorAt)
    }.toString()

    companion object {
        fun decode(payload: String): CumulativeRecord {
            val obj = JSONObject(payload)
            require(obj.getInt("version") == CumulativeScoreConfig.CURRENT.version) { "Unsupported score configuration" }
            val timestamp = obj.getLong("timestamp")
            require(timestamp >= 0L)
            val s = obj.getDouble("signal")
            val q = obj.getDouble("momentum")
            val r = obj.getDouble("rest")
            val b = obj.getDouble("burden")
            require(s.isFinite() && s in 0.0..100.0)
            require(listOf(q, r, b).all { it.isFinite() && it >= 0.0 })
            return CumulativeRecord(CumulativeCheckpoint(timestamp, CumulativeScoreState(s, q, r, b)),
                obj.getInt("version"), obj.getLong("startedAt"), obj.optLong("wake"),
                obj.optLong("restingUntil"), obj.optLong("briefing"),
                obj.optJSONObject("suggestions")?.let { map ->
                    map.keys().asSequence().associateWith { map.getLong(it) }
                } ?: emptyMap(), obj.optLong("lateSleepAnchor"))
        }
    }
}

/** The service is the sole advancing writer. UI/notification/forecast never advances this cursor. */
object CumulativeScoreStore {
    private var windowCache: Triple<DigitsDatabase, String, RestWindow>? = null
    suspend fun update(
        db: DigitsDatabase,
        nowMillis: Long,
        coverageStartMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Pair<CumulativeRecord, RollingScoreDetail> = db.withTransaction {
        val dao = db.cumulativeScoreStateDao()
        val safeEnd = nowMillis - 2 * CumulativeTimeline.MINUTE
        val settledEnd = safeEnd - Math.floorMod(safeEnd, CumulativeTimeline.MINUTE)
        val saved = dao.get()?.let { CumulativeRecord.decode(it.payload) }
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
            CumulativeUse(it.startTimeMillis, it.endTimeMillis, it.effectiveCategoryLevel >= 3,
                "${it.packageName}:${it.sessionStartTimeMillis}", it.sessionStartTimeMillis,
                it.packageName, it.effectivePackageName)
        val dayKey = "${java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()}:$zone"
        val cached = windowCache
        val window = if (cached?.first === db && cached.second == dayKey) cached.third else {
            RestPhasePolicy.learn(db.foregroundUsageSessionDao().getSince(nowMillis - 8 * 24 * 3_600_000L)
                .map(::asUse), zone).also { windowCache = Triple(db, dayKey, it) }
        }
        // Incremental scoring query: only cursor look-behind, not a fresh full-day/full-week score replay.
        val uses = db.foregroundUsageSessionDao().getSince(processStart - 5 * 60_000L).map(::asUse)
        val lateSleepAnchor = maxOf(record.lateSleepAnchorAt, RestPhasePolicy.lateSleepAnchor(uses, zone) ?: 0L)
        val movements = mutableListOf<ScoreMovement>()
        val next = CumulativeTimeline.advance(start, settledEnd, uses, { timestamp ->
            RestPhasePolicy.activityAt(timestamp, window, zone, record.wakeConfirmedAt, record.restingConfirmedUntil, lateSleepAnchor)
        }, onMovement = { movements.add(it) })
        // Attribution and cursor commit together: re-polling/restarting cannot charge twice.
        StatisticsStore.recordMovements(db, movements)
        val updated = record.copy(checkpoint = next, lateSleepAnchorAt = lateSleepAnchor)
        dao.put(CumulativeScoreStateEntity(payload = updated.encode()))
        updated to detail(updated, RestPhasePolicy.activityAt(next.timestamp, window, zone,
            record.wakeConfirmedAt, record.restingConfirmedUntil, lateSleepAnchor) == CumulativeActivity.SLEEP)
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

    suspend fun confirmActivity(db: DigitsDatabase, nowMillis: Long, awake: Boolean, acknowledge: Boolean = true) = db.withTransaction {
        val record = db.cumulativeScoreStateDao().get()?.let { CumulativeRecord.decode(it.payload) }
            ?: return@withTransaction
        val next = if (awake) record.copy(wakeConfirmedAt = nowMillis, restingConfirmedUntil = 0,
            briefingHandledAt = if (acknowledge) nowMillis else record.briefingHandledAt)
        else record.copy(restingConfirmedUntil = nowMillis + 2 * 3_600_000L, briefingHandledAt = nowMillis)
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
