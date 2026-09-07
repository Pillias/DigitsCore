package com.digitscore.app.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyAppUsageEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.DailyUsageCoverageEntity
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.data.entity.ForegroundUsageSessionEntity
import com.digitscore.app.model.AppCategoryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DataBackupManager {

    private const val BACKUP_SCHEMA_VERSION = 3
    private const val MAX_BACKUP_BYTES = 20 * 1024 * 1024
    private val DATE_PATTERN = Regex("\\d{4}-\\d{2}-\\d{2}")
    private const val BACKUP_CACHE_MAX_AGE_MILLIS = 24 * 60 * 60 * 1_000L

    /**
     * DB의 모든 데이터(점수 히스토리, 설정, 앱 분류 가중치)를 JSON 문자열로 직렬화합니다.
     */
    suspend fun exportToJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = DigitsDatabase.getInstance(context)
        val histories = db.scoreDao().getAllScoreHistories().firstOrNull() ?: emptyList()
        val settings = db.settingsDao().getSettings()
        val appWeights = db.appDao().getAllAppWeights().firstOrNull() ?: emptyList()
        val dailyAppUsage = db.dailyAppUsageDao().getAll()
        val dailyCoverage = db.dailyAppUsageDao().getAllCoverage()
        val detailedSessions = db.foregroundUsageSessionDao().getAll()

        val rootJson = JSONObject()
        rootJson.put("version", BACKUP_SCHEMA_VERSION)
        rootJson.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        // 1. 점수 히스토리
        val historiesArray = JSONArray()
        for (h in histories) {
            val hObj = JSONObject().apply {
                put("dateString", h.dateString)
                put("finalScore", h.finalScore)
                put("totalScreenTimeMinutes", h.totalScreenTimeMinutes)
                put("distractingTimeMinutes", h.distractingTimeMinutes)
                put("productiveTimeMinutes", h.productiveTimeMinutes)
                put("idleMinutes", h.idleMinutes)
                put("unlockCount", h.unlockCount)
                put("lastUpdatedTimestamp", h.lastUpdatedTimestamp)
            }
            historiesArray.put(hObj)
        }
        rootJson.put("scoreHistories", historiesArray)

        // 2. 사용자 설정
        if (settings != null) {
            val sObj = JSONObject().apply {
                put("selectedPresetModeId", settings.selectedPresetModeId)
                put("minimumScoreDefenseLine", settings.minimumScoreDefenseLine)
                put("targetUnlockCount", settings.targetUnlockCount)
                put("distractingWeightPerMinute", settings.distractingWeightPerMinute.toDouble())
                put("productiveBonusPerMinute", settings.productiveBonusPerMinute.toDouble())
                put("idleBonusPer10Minutes", settings.idleBonusPer10Minutes.toDouble())
                put("maxIdleBonus", settings.maxIdleBonus.toDouble())
                put("maxProductiveBonus", settings.maxProductiveBonus.toDouble())
                put("unlockPenaltyPerCount", settings.unlockPenaltyPerCount.toDouble())
                put("lateNightMultiplier", settings.lateNightMultiplier.toDouble())
                put("isLogAccelerationEnabled", settings.isLogAccelerationEnabled)
                put("logAccelerationThresholdMinutes", settings.logAccelerationThresholdMinutes.toDouble())
                put("logAccelerationScaleMinutes", settings.logAccelerationScaleMinutes.toDouble())
                put("isYesterdayPenaltyEnabled", settings.isYesterdayPenaltyEnabled)
                put("yesterdayPenaltyTriggerScore", settings.yesterdayPenaltyTriggerScore)
                put("yesterdayPenaltyRate", settings.yesterdayPenaltyRate.toDouble())
                put("maxYesterdayPenalty", settings.maxYesterdayPenalty.toDouble())
                put("isTrackingEnabled", settings.isTrackingEnabled)
                put("isNotificationEnabled", settings.isNotificationEnabled)
                put("appHistoryRetentionDays", settings.appHistoryRetentionDays)
                put("hideSensitiveNotificationOnLockScreen", settings.hideSensitiveNotificationOnLockScreen)
                put("statusIconStyleId", settings.statusIconStyleId)
            }
            rootJson.put("settings", sObj)
        }

        // 3. 앱 가중치 분류
        val appsArray = JSONArray()
        for (app in appWeights) {
            val aObj = JSONObject().apply {
                put("packageName", app.packageName)
                put("appName", app.appName)
                put("categoryType", app.categoryType.name)
            }
            appsArray.put(aObj)
        }
        rootJson.put("appWeights", appsArray)

        // 4. 최대 365일 앱별 자체 집계와 측정일 표식
        val dailyAppsArray = JSONArray()
        for (record in dailyAppUsage) {
            dailyAppsArray.put(JSONObject().apply {
                put("dateString", record.dateString)
                put("packageName", record.packageName)
                put("appName", record.appName)
                put("usageMillis", record.usageMillis)
                put("sessionCount", record.sessionCount)
                put("longestSessionMillis", record.longestSessionMillis)
                put("lateNightUsageMillis", record.lateNightUsageMillis)
                put("categoryLevel", record.categoryLevel)
                put("lastUpdatedTimestamp", record.lastUpdatedTimestamp)
            })
        }
        rootJson.put("dailyAppUsage", dailyAppsArray)

        val coverageArray = JSONArray()
        for (coverage in dailyCoverage) {
            coverageArray.put(JSONObject().apply {
                put("dateString", coverage.dateString)
                put("isComplete", coverage.isComplete)
                put("lastUpdatedTimestamp", coverage.lastUpdatedTimestamp)
            })
        }
        rootJson.put("dailyUsageCoverage", coverageArray)

        // 5. 최근 30일 상세 세션. 암호화 백업을 만들면 내보낸 시점의 상세 기록도 보존됩니다.
        val sessionsArray = JSONArray()
        for (session in detailedSessions) {
            sessionsArray.put(JSONObject().apply {
                put("packageName", session.packageName)
                put("startTimeMillis", session.startTimeMillis)
                put("endTimeMillis", session.endTimeMillis)
                put("dateString", session.dateString)
                put("appName", session.appName)
                put("categoryLevel", session.categoryLevel)
                put("isLateNight", session.isLateNight)
                put("lastUpdatedTimestamp", session.lastUpdatedTimestamp)
            })
        }
        rootJson.put("foregroundUsageSessions", sessionsArray)

        rootJson.toString(2)
    }

    suspend fun exportEncrypted(context: Context, password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val json = exportToJson(context)
        BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)
    }

    suspend fun importBackup(
        context: Context,
        bytes: ByteArray,
        password: CharArray?
    ): Boolean = withContext(Dispatchers.IO) {
        val jsonBytes = if (BackupCrypto.isEncryptedBackup(bytes)) {
            BackupCrypto.decrypt(bytes, password ?: charArrayOf())
        } else {
            bytes
        }
        require(jsonBytes.size <= MAX_BACKUP_BYTES) { "Backup file is too large" }
        importFromJson(context, jsonBytes.toString(Charsets.UTF_8))
    }

    /**
     * JSON 문자열을 파싱하여 DB에 복원합니다.
     */
    suspend fun importFromJson(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            require(jsonString.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) {
                "Backup file is too large"
            }
            val db = DigitsDatabase.getInstance(context)
            val rootJson = JSONObject(jsonString)
            require(rootJson.optInt("version", -1) in 1..BACKUP_SCHEMA_VERSION) {
                "Unsupported backup schema version"
            }

            db.withTransaction {

            // 1. 점수 히스토리 복원
            if (rootJson.has("scoreHistories")) {
                val historiesArray = rootJson.getJSONArray("scoreHistories")
                require(historiesArray.length() <= 3660) { "Too many history records" }
                for (i in 0 until historiesArray.length()) {
                    val hObj = historiesArray.getJSONObject(i)
                    val dateString = hObj.getString("dateString")
                    require(DATE_PATTERN.matches(dateString)) { "Invalid history date" }
                    val history = DailyScoreHistoryEntity(
                        dateString = dateString,
                        finalScore = hObj.getInt("finalScore").coerceIn(0, 100),
                        totalScreenTimeMinutes = hObj.getLong("totalScreenTimeMinutes").coerceIn(0L, 1_440L),
                        distractingTimeMinutes = hObj.getLong("distractingTimeMinutes").coerceIn(0L, 1_440L),
                        productiveTimeMinutes = hObj.getLong("productiveTimeMinutes").coerceIn(0L, 1_440L),
                        idleMinutes = hObj.getLong("idleMinutes").coerceIn(0L, 1_440L),
                        unlockCount = hObj.getInt("unlockCount").coerceIn(0, 10_000),
                        lastUpdatedTimestamp = hObj.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
                    )
                    db.scoreDao().insertOrUpdateScoreHistory(history)
                }
            }

            // 2. 사용자 설정 복원
            if (rootJson.has("settings")) {
                val sObj = rootJson.getJSONObject("settings")
                val settings = UserSettingsEntity(
                    id = 1,
                    selectedPresetModeId = sObj.optString("selectedPresetModeId", "balanced"),
                    minimumScoreDefenseLine = sObj.optInt("minimumScoreDefenseLine", 60).coerceIn(0, 100),
                    targetUnlockCount = sObj.optInt("targetUnlockCount", 30).coerceIn(0, 1_000),
                    distractingWeightPerMinute = sObj.optDouble("distractingWeightPerMinute", 0.6).toFloat().coerceIn(0f, 10f),
                    productiveBonusPerMinute = sObj.optDouble("productiveBonusPerMinute", 0.2).toFloat().coerceIn(0f, 10f),
                    idleBonusPer10Minutes = sObj.optDouble("idleBonusPer10Minutes", 0.25).toFloat().coerceIn(0f, 10f),
                    maxIdleBonus = sObj.optDouble("maxIdleBonus", 15.0).toFloat().coerceIn(0f, 100f),
                    maxProductiveBonus = sObj.optDouble("maxProductiveBonus", 15.0).toFloat().coerceIn(0f, 100f),
                    unlockPenaltyPerCount = sObj.optDouble("unlockPenaltyPerCount", 0.3).toFloat().coerceIn(0f, 10f),
                    lateNightMultiplier = sObj.optDouble("lateNightMultiplier", 1.5).toFloat().coerceIn(1f, 10f),
                    isLogAccelerationEnabled = sObj.optBoolean("isLogAccelerationEnabled", true),
                    logAccelerationThresholdMinutes = sObj.optDouble("logAccelerationThresholdMinutes", 60.0).toFloat().coerceIn(30f, 180f),
                    logAccelerationScaleMinutes = sObj.optDouble("logAccelerationScaleMinutes", 120.0).toFloat().coerceIn(60f, 300f),
                    isYesterdayPenaltyEnabled = sObj.optBoolean("isYesterdayPenaltyEnabled", true),
                    yesterdayPenaltyTriggerScore = sObj.optInt("yesterdayPenaltyTriggerScore", 60).coerceIn(40, 90),
                    yesterdayPenaltyRate = sObj.optDouble("yesterdayPenaltyRate", 0.2).toFloat().coerceIn(0.05f, 1f),
                    maxYesterdayPenalty = sObj.optDouble("maxYesterdayPenalty", 10.0).toFloat().coerceIn(0f, 30f),
                    isTrackingEnabled = sObj.optBoolean("isTrackingEnabled", false),
                    isNotificationEnabled = sObj.optBoolean("isNotificationEnabled", true),
                    appHistoryRetentionDays = 365,
                    hideSensitiveNotificationOnLockScreen = sObj.optBoolean(
                        "hideSensitiveNotificationOnLockScreen",
                        true
                    ),
                    statusIconStyleId = sObj.optString("statusIconStyleId", "score_tier")
                        .takeIf {
                            it == "score_proportion" || it == "score_tier" || it == "number_focus"
                        }
                        ?: "score_tier"
                )
                db.settingsDao().insertOrUpdateSettings(settings)
            }

            // 3. 앱 가중치 복원
            if (rootJson.has("appWeights")) {
                val appsArray = rootJson.getJSONArray("appWeights")
                require(appsArray.length() <= 10_000) { "Too many app records" }
                for (i in 0 until appsArray.length()) {
                    val aObj = appsArray.getJSONObject(i)
                    val catName = aObj.optString("categoryType", "NEUTRAL")
                    val cat = try {
                        AppCategoryType.valueOf(catName)
                    } catch (e: Exception) {
                        AppCategoryType.NEUTRAL
                    }
                    val packageName = aObj.getString("packageName").trim()
                    val appName = aObj.getString("appName").trim()
                    require(packageName.length in 1..255 && appName.length in 1..255) { "Invalid app record" }
                    val entity = AppWeightEntity(
                        packageName = packageName,
                        appName = appName,
                        categoryType = cat
                    )
                    db.appDao().insertOrUpdateAppWeight(entity)
                }
            }

            // 4. v2부터 포함되는 앱별 장기 집계 복원
            if (rootJson.has("dailyAppUsage")) {
                val recordsArray = rootJson.getJSONArray("dailyAppUsage")
                require(recordsArray.length() <= 100_000) { "Too many daily app usage records" }
                val records = buildList {
                    for (i in 0 until recordsArray.length()) {
                        val obj = recordsArray.getJSONObject(i)
                        val dateString = obj.getString("dateString")
                        val packageName = obj.getString("packageName").trim()
                        val appName = obj.getString("appName").trim()
                        require(DATE_PATTERN.matches(dateString)) { "Invalid daily app usage date" }
                        require(packageName.length in 1..255 && appName.length in 1..255) { "Invalid daily app record" }
                        add(
                            DailyAppUsageEntity(
                                dateString = dateString,
                                packageName = packageName,
                                appName = appName,
                                usageMillis = obj.getLong("usageMillis").coerceIn(0L, 86_400_000L),
                                sessionCount = obj.optInt("sessionCount", 0).coerceIn(0, 10_000),
                                longestSessionMillis = obj.optLong("longestSessionMillis", 0L).coerceIn(0L, 86_400_000L),
                                lateNightUsageMillis = obj.optLong("lateNightUsageMillis", 0L).coerceIn(0L, 18_000_000L),
                                categoryLevel = obj.optInt("categoryLevel", 3).coerceIn(1, 5),
                                lastUpdatedTimestamp = obj.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
                db.dailyAppUsageDao().insertAll(records)
            }

            if (rootJson.has("dailyUsageCoverage")) {
                val coverageArray = rootJson.getJSONArray("dailyUsageCoverage")
                require(coverageArray.length() <= 365) { "Too many daily coverage records" }
                for (i in 0 until coverageArray.length()) {
                    val obj = coverageArray.getJSONObject(i)
                    val dateString = obj.getString("dateString")
                    require(DATE_PATTERN.matches(dateString)) { "Invalid coverage date" }
                    db.dailyAppUsageDao().insertCoverage(
                        DailyUsageCoverageEntity(
                            dateString = dateString,
                            isComplete = obj.optBoolean("isComplete", true),
                            lastUpdatedTimestamp = obj.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
                        )
                    )
                }
            }

            if (rootJson.has("foregroundUsageSessions")) {
                val sessionsArray = rootJson.getJSONArray("foregroundUsageSessions")
                require(sessionsArray.length() <= 200_000) { "Too many detailed usage sessions" }
                val sessions = buildList {
                    for (i in 0 until sessionsArray.length()) {
                        val obj = sessionsArray.getJSONObject(i)
                        val dateString = obj.getString("dateString")
                        val packageName = obj.getString("packageName").trim()
                        val appName = obj.getString("appName").trim()
                        val start = obj.getLong("startTimeMillis")
                        val end = obj.getLong("endTimeMillis")
                        require(DATE_PATTERN.matches(dateString)) { "Invalid detailed session date" }
                        require(packageName.length in 1..255 && appName.length in 1..255) { "Invalid detailed session" }
                        require(start >= 0L && end > start && end - start <= 86_400_000L) { "Invalid session duration" }
                        add(
                            ForegroundUsageSessionEntity(
                                packageName = packageName,
                                startTimeMillis = start,
                                endTimeMillis = end,
                                dateString = dateString,
                                appName = appName,
                                categoryLevel = obj.optInt("categoryLevel", 3).coerceIn(1, 5),
                                isLateNight = obj.optBoolean("isLateNight", false),
                                lastUpdatedTimestamp = obj.optLong("lastUpdatedTimestamp", System.currentTimeMillis())
                            )
                        )
                    }
                }
                db.foregroundUsageSessionDao().insertAll(sessions)
            }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * 백업 JSON을 임시 파일로 저장하고 공유 Intent를 실행합니다.
     */
    fun shareEncryptedBackup(context: Context, encryptedContent: ByteArray) {
        cleanupStaleBackups(context, maxAgeMillis = 0L)
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "DigitsCore_Backup_$dateStr.dcorebackup")
        backupFile.writeBytes(encryptedContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "DigitsCore Backup ($dateStr)")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "DigitsCore 데이터 백업 공유/저장").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun cleanupStaleBackups(
        context: Context,
        maxAgeMillis: Long = BACKUP_CACHE_MAX_AGE_MILLIS
    ) {
        val now = System.currentTimeMillis()
        File(context.cacheDir, "backups").listFiles()?.forEach { file ->
            if (maxAgeMillis == 0L || now - file.lastModified() >= maxAgeMillis) {
                file.delete()
            }
        }
    }
}
