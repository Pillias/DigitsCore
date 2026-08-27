package com.digitscore.app

import android.app.Application
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.notification.ScoreNotificationManager

class DigitsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Room DB 인스턴스 초기화
        DigitsDatabase.getInstance(this)
        // 알림 채널 사전 등록
        ScoreNotificationManager.createNotificationChannel(this)
    }
}
