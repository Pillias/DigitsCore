package com.digitscore.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.service.TrackerForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = DigitsDatabase.getInstance(context)
                val settings = db.settingsDao().getSettings()
                if (settings == null || settings.isTrackingEnabled) {
                    val serviceIntent = Intent(context, TrackerForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
