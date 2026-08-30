package com.digitscore.app.data.backup

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.UserSettingsEntity
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

    /**
     * DB의 모든 데이터(점수 히스토리, 설정, 앱 분류 가중치)를 JSON 문자열로 직렬화합니다.
     */
    suspend fun exportToJson(context: Context): String = withContext(Dispatchers.IO) {
        val db = DigitsDatabase.getInstance(context)
        val histories = db.scoreDao().getAllScoreHistories().firstOrNull() ?: emptyList()
        val settings = db.settingsDao().getSettings()
        val appWeights = db.appDao().getAllAppWeights().firstOrNull() ?: emptyList()

        val rootJson = JSONObject()
        rootJson.put("version", 1)
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
                put("isYesterdayPenaltyEnabled", settings.isYesterdayPenaltyEnabled)
                put("isTrackingEnabled", settings.isTrackingEnabled)
                put("isNotificationEnabled", settings.isNotificationEnabled)
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

        rootJson.toString(2)
    }

    /**
     * JSON 문자열을 파싱하여 DB에 복원합니다.
     */
    suspend fun importFromJson(context: Context, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val db = DigitsDatabase.getInstance(context)
            val rootJson = JSONObject(jsonString)

            // 1. 점수 히스토리 복원
            if (rootJson.has("scoreHistories")) {
                val historiesArray = rootJson.getJSONArray("scoreHistories")
                for (i in 0 until historiesArray.length()) {
                    val hObj = historiesArray.getJSONObject(i)
                    val history = DailyScoreHistoryEntity(
                        dateString = hObj.getString("dateString"),
                        finalScore = hObj.getInt("finalScore"),
                        totalScreenTimeMinutes = hObj.getLong("totalScreenTimeMinutes"),
                        distractingTimeMinutes = hObj.getLong("distractingTimeMinutes"),
                        productiveTimeMinutes = hObj.getLong("productiveTimeMinutes"),
                        idleMinutes = hObj.getLong("idleMinutes"),
                        unlockCount = hObj.getInt("unlockCount"),
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
                    minimumScoreDefenseLine = sObj.optInt("minimumScoreDefenseLine", 60),
                    targetUnlockCount = sObj.optInt("targetUnlockCount", 25),
                    distractingWeightPerMinute = sObj.optDouble("distractingWeightPerMinute", 0.8).toFloat(),
                    productiveBonusPerMinute = sObj.optDouble("productiveBonusPerMinute", 0.2).toFloat(),
                    idleBonusPer10Minutes = sObj.optDouble("idleBonusPer10Minutes", 0.25).toFloat(),
                    maxIdleBonus = sObj.optDouble("maxIdleBonus", 15.0).toFloat(),
                    maxProductiveBonus = sObj.optDouble("maxProductiveBonus", 15.0).toFloat(),
                    unlockPenaltyPerCount = sObj.optDouble("unlockPenaltyPerCount", 0.5).toFloat(),
                    lateNightMultiplier = sObj.optDouble("lateNightMultiplier", 1.6).toFloat(),
                    isLogAccelerationEnabled = sObj.optBoolean("isLogAccelerationEnabled", true),
                    isYesterdayPenaltyEnabled = sObj.optBoolean("isYesterdayPenaltyEnabled", true),
                    isTrackingEnabled = sObj.optBoolean("isTrackingEnabled", true),
                    isNotificationEnabled = sObj.optBoolean("isNotificationEnabled", true)
                )
                db.settingsDao().insertOrUpdateSettings(settings)
            }

            // 3. 앱 가중치 복원
            if (rootJson.has("appWeights")) {
                val appsArray = rootJson.getJSONArray("appWeights")
                for (i in 0 until appsArray.length()) {
                    val aObj = appsArray.getJSONObject(i)
                    val catName = aObj.optString("categoryType", "NEUTRAL")
                    val cat = try {
                        AppCategoryType.valueOf(catName)
                    } catch (e: Exception) {
                        AppCategoryType.NEUTRAL
                    }
                    val entity = AppWeightEntity(
                        packageName = aObj.getString("packageName"),
                        appName = aObj.getString("appName"),
                        categoryType = cat
                    )
                    db.appDao().insertOrUpdateAppWeight(entity)
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
    fun shareBackup(context: Context, jsonContent: String) {
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val backupFile = File(backupDir, "DigitsCore_Backup_$dateStr.json")
        backupFile.writeText(jsonContent)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            backupFile
        )

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
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
}
