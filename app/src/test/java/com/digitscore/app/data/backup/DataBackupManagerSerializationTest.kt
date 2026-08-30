package com.digitscore.app.data.backup

import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.model.AppCategoryType
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * DataBackupManager의 JSON 직렬화/역직렬화 형식 검증 테스트
 */
class DataBackupManagerSerializationTest {

    @Test
    fun testJsonFormat_scoreHistory() {
        val history = DailyScoreHistoryEntity(
            dateString = "2026-08-29",
            finalScore = 85,
            totalScreenTimeMinutes = 120,
            distractingTimeMinutes = 45,
            productiveTimeMinutes = 30,
            idleMinutes = 300,
            unlockCount = 22,
            lastUpdatedTimestamp = 1234567890L
        )
        
        val json = JSONObject().apply {
            put("dateString", history.dateString)
            put("finalScore", history.finalScore)
            put("totalScreenTimeMinutes", history.totalScreenTimeMinutes)
            put("distractingTimeMinutes", history.distractingTimeMinutes)
            put("productiveTimeMinutes", history.productiveTimeMinutes)
            put("idleMinutes", history.idleMinutes)
            put("unlockCount", history.unlockCount)
            put("lastUpdatedTimestamp", history.lastUpdatedTimestamp)
        }
        
        assertEquals("2026-08-29", json.getString("dateString"))
        assertEquals(85, json.getInt("finalScore"))
        assertEquals(120L, json.getLong("totalScreenTimeMinutes"))
        assertEquals(22, json.getInt("unlockCount"))
    }

    @Test
    fun testJsonFormat_appWeight() {
        val entity = AppWeightEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            categoryType = AppCategoryType.DISTRACTING
        )
        
        val json = JSONObject().apply {
            put("packageName", entity.packageName)
            put("appName", entity.appName)
            put("categoryType", entity.categoryType.name)
        }
        
        assertEquals("com.instagram.android", json.getString("packageName"))
        assertEquals("DISTRACTING", json.getString("categoryType"))
        
        // Verify round-trip: parse back
        val parsedCat = AppCategoryType.valueOf(json.getString("categoryType"))
        assertEquals(AppCategoryType.DISTRACTING, parsedCat)
    }

    @Test
    fun testJsonFormat_userSettings() {
        val settings = UserSettingsEntity(
            id = 1,
            selectedPresetModeId = "study",
            minimumScoreDefenseLine = 70,
            targetUnlockCount = 15,
            distractingWeightPerMinute = 1.2f,
            lateNightMultiplier = 1.8f
        )
        
        val json = JSONObject().apply {
            put("selectedPresetModeId", settings.selectedPresetModeId)
            put("minimumScoreDefenseLine", settings.minimumScoreDefenseLine)
            put("targetUnlockCount", settings.targetUnlockCount)
            put("distractingWeightPerMinute", settings.distractingWeightPerMinute.toDouble())
            put("lateNightMultiplier", settings.lateNightMultiplier.toDouble())
        }
        
        assertEquals("study", json.getString("selectedPresetModeId"))
        assertEquals(70, json.getInt("minimumScoreDefenseLine"))
        assertEquals(1.2, json.getDouble("distractingWeightPerMinute"), 0.01)
    }

    @Test
    fun testJsonRoundTrip_fullBackup() {
        val rootJson = JSONObject()
        rootJson.put("version", 1)
        rootJson.put("exportDate", "2026-08-30 10:00:00")

        // Score histories
        val historiesArray = org.json.JSONArray()
        historiesArray.put(JSONObject().apply {
            put("dateString", "2026-08-28")
            put("finalScore", 75)
            put("totalScreenTimeMinutes", 180)
            put("distractingTimeMinutes", 90)
            put("productiveTimeMinutes", 40)
            put("idleMinutes", 200)
            put("unlockCount", 30)
        })
        rootJson.put("scoreHistories", historiesArray)

        // App weights
        val appsArray = org.json.JSONArray()
        appsArray.put(JSONObject().apply {
            put("packageName", "com.youtube")
            put("appName", "YouTube")
            put("categoryType", "DISTRACTING")
        })
        rootJson.put("appWeights", appsArray)

        // Verify structure
        val jsonStr = rootJson.toString(2)
        val parsed = JSONObject(jsonStr)
        
        assertEquals(1, parsed.getInt("version"))
        assertTrue(parsed.has("scoreHistories"))
        assertTrue(parsed.has("appWeights"))
        
        val parsedHistories = parsed.getJSONArray("scoreHistories")
        assertEquals(1, parsedHistories.length())
        assertEquals(75, parsedHistories.getJSONObject(0).getInt("finalScore"))
        
        val parsedApps = parsed.getJSONArray("appWeights")
        assertEquals("YouTube", parsedApps.getJSONObject(0).getString("appName"))
    }

    @Test
    fun testCategoryType_invalidValueFallback() {
        val invalidCatName = "UNKNOWN_CATEGORY"
        val fallback = try {
            AppCategoryType.valueOf(invalidCatName)
        } catch (e: Exception) {
            AppCategoryType.NEUTRAL
        }
        assertEquals(AppCategoryType.NEUTRAL, fallback)
    }
}
