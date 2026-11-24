package com.digitscore.app.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.digitscore.app.data.backup.DataBackupManager
import com.digitscore.app.data.backup.RestoreMode
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.engine.CumulativeScoreConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 추적을 꺼 둔 기기에서 만든 백업을 추적 중인 새 기기에 복원해도
 * 새 기기 추적이 꺼지지 않고, 화면 점수가 복원한 지수로 바로 바뀌어야 합니다.
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTrackingTest {
    @Test fun restoreKeepsDeviceTrackingAndPublishesRestoredScore() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DigitsDatabase::class.java).build()
        val previousDetail = ScoreRepository.rollingScoreDetail.value
        try {
            db.settingsDao().insertOrUpdateSettings(UserSettingsEntity(id = 1, isTrackingEnabled = true))
            val checkpointAt = System.currentTimeMillis() - 10 * 60_000L
            val signal = CumulativeScoreConfig.CURRENT.signalForScore(78.24)
            val backup = JSONObject()
                .put("version", 11)
                .put("settings", JSONObject().put("isTrackingEnabled", false).put("minimumScoreDefenseLine", 70))
                .put("cumulativeScoreState", JSONObject()
                    .put("version", 1).put("timestamp", checkpointAt).put("startedAt", checkpointAt - 86_400_000L)
                    .put("signal", signal).put("momentum", 0.0).put("rest", 0.0).put("burden", 0.0))

            // 덮어쓰기는 실제 추적 서비스를 멈추고 다시 켜므로, 시험은 설정·점수 반영이 같은 합치기로 확인합니다.
            assertTrue(DataBackupManager.importFromJson(context, backup.toString(), RestoreMode.MERGE, db))

            val settings = db.settingsDao().getSettings()!!
            assertTrue("이 기기의 추적 켜짐을 유지해야 합니다", settings.isTrackingEnabled)
            assertEquals("다른 설정은 백업 값을 씁니다", 70, settings.minimumScoreDefenseLine)
            assertEquals(
                kotlin.math.round(CumulativeScoreConfig.CURRENT.displayed(signal)).toInt(),
                ScoreRepository.rollingScoreDetail.value?.finalScore
            )
        } finally {
            ScoreRepository.updateRollingScoreDetail(previousDetail)
            db.close()
        }
    }
}
