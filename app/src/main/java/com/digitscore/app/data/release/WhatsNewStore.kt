package com.digitscore.app.data.release

import android.content.Context

/**
 * 업데이트 후 안내 팝업을 한 번만 띄우기 위한 기록.
 * - 새로 설치한 경우: 띄우지 않고 지금 버전을 '본 것'으로 저장
 * - 업데이트한 경우: 지금 버전 노트가 있고 quiet가 아니면 한 번 띄움
 */
object WhatsNewStore {
    private const val PREFS = "whats_new"
    private const val KEY_SEEN = "last_seen_version"

    fun shouldShow(context: Context, currentVersion: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getString(KEY_SEEN, null)
        if (seen == null && isFreshInstall(context)) {
            markSeen(context, currentVersion)
            return false
        }
        return seen == null || ReleaseNotes.compare(currentVersion, seen) > 0
    }

    fun markSeen(context: Context, version: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_SEEN, version).apply()
    }

    private fun isFreshInstall(context: Context): Boolean = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.firstInstallTime == info.lastUpdateTime
    }.getOrDefault(false)
}
