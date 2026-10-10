package com.digitscore.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 화면 켜짐/꺼짐, 사용자 잠금 해제(언락) 및 시간대/시각 변경 이벤트를 수신하는 리시버
 */
class ScreenEventReceiver(
    private val onScreenOn: () -> Unit,
    private val onScreenOff: () -> Unit,
    private val onUserPresent: () -> Unit,
    private val onTimeOrTimezoneChanged: (() -> Unit)? = null
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> onScreenOn()
            Intent.ACTION_SCREEN_OFF -> onScreenOff()
            Intent.ACTION_USER_PRESENT -> onUserPresent()
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> onTimeOrTimezoneChanged?.invoke()
        }
    }
}

