package com.digitscore.app.data.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * 최근 24시간 언락을 자정 비율로 추정하지 않고 계산하기 위한 최소 이벤트입니다.
 * 원시 앱 이벤트는 저장하지 않으며 이 행은 최대 25시간만 보존합니다.
 */
@Entity(
    tableName = "device_interaction_events",
    primaryKeys = ["timestampMillis", "eventType"],
    indices = [Index("timestampMillis")]
)
data class DeviceInteractionEventEntity(
    val timestampMillis: Long,
    val eventType: Int
) {
    companion object {
        /** 화면이 켜졌다는 뜻일 뿐 잠금 해제를 의미하지는 않습니다. */
        const val SCREEN_INTERACTIVE = 1
        /** UsageEvents가 기록한 키가드 해제 상태 전환입니다. */
        const val KEYGUARD_HIDDEN = 2
        const val NOTIFICATION_INTERRUPTION = 3
        /** 추적 서비스가 ACTION_USER_PRESENT 브로드캐스트에서 직접 기록한 해제입니다. */
        const val USER_PRESENT = 4
    }
}
