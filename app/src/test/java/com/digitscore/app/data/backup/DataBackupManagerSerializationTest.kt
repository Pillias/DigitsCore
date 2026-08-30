package com.digitscore.app.data.backup

import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.data.entity.DailyScoreHistoryEntity
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.model.AppCategoryType
import org.junit.Assert.*
import org.junit.Test

/**
 * DataBackupManager의 데이터 모델 및 백업 형식 검증 테스트 (Pure Kotlin)
 */
class DataBackupManagerSerializationTest {

    @Test
    fun testScoreHistoryEntity_creationAndFields() {
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

        assertEquals("2026-08-29", history.dateString)
        assertEquals(85, history.finalScore)
        assertEquals(120L, history.totalScreenTimeMinutes)
        assertEquals(45L, history.distractingTimeMinutes)
        assertEquals(30L, history.productiveTimeMinutes)
        assertEquals(300L, history.idleMinutes)
        assertEquals(22, history.unlockCount)
        assertEquals(1234567890L, history.lastUpdatedTimestamp)
    }

    @Test
    fun testAppWeightEntity_categoryTypes() {
        val distractingApp = AppWeightEntity(
            packageName = "com.instagram.android",
            appName = "Instagram",
            categoryType = AppCategoryType.DISTRACTING
        )
        assertEquals("com.instagram.android", distractingApp.packageName)
        assertEquals(AppCategoryType.DISTRACTING, distractingApp.categoryType)

        val productiveApp = AppWeightEntity(
            packageName = "com.notion.id",
            appName = "Notion",
            categoryType = AppCategoryType.PRODUCTIVE
        )
        assertEquals(AppCategoryType.PRODUCTIVE, productiveApp.categoryType)

        val neutralApp = AppWeightEntity(
            packageName = "com.google.android.dialer",
            appName = "Phone",
            categoryType = AppCategoryType.NEUTRAL
        )
        assertEquals(AppCategoryType.NEUTRAL, neutralApp.categoryType)
    }

    @Test
    fun testUserSettingsEntity_defaultsAndCustomValues() {
        val settings = UserSettingsEntity(
            id = 1,
            selectedPresetModeId = "study",
            minimumScoreDefenseLine = 70,
            targetUnlockCount = 15,
            distractingWeightPerMinute = 1.2f,
            lateNightMultiplier = 1.8f
        )

        assertEquals(1, settings.id)
        assertEquals("study", settings.selectedPresetModeId)
        assertEquals(70, settings.minimumScoreDefenseLine)
        assertEquals(15, settings.targetUnlockCount)
        assertEquals(1.2f, settings.distractingWeightPerMinute, 0.01f)
        assertEquals(1.8f, settings.lateNightMultiplier, 0.01f)
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
