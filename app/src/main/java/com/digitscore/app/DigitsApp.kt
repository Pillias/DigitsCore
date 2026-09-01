package com.digitscore.app

import android.app.Application
import com.digitscore.app.notification.ScoreNotificationManager
import com.digitscore.app.data.backup.DataBackupManager

class DigitsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // DB 암호화/마이그레이션은 MainActivity의 복구 가능한 시작 화면에서 수행합니다.
        DataBackupManager.cleanupStaleBackups(this)
        // 알림 채널 사전 등록
        ScoreNotificationManager.createNotificationChannel(this)
    }
}
