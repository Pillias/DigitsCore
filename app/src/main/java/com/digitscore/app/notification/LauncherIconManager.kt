package com.digitscore.app.notification

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * 코어 지수 4단계(녹색 80~99P / 노랑 60~79P / 주황 40~59P / 빨강 1~39P)에 따라
 * 홈 화면 런처 아이콘(<activity-alias>)을 하루 최대 1회, 화면이 꺼진 안전한 시점에 전환합니다.
 *
 * 일부 런처는 별칭이 바뀌면 홈 화면 바로가기를 지우므로, 기본은 꺼져 있고 설정에서 켭니다.
 * 꺼져 있으면 기본 아이콘(노랑 별칭)으로 되돌린 뒤 더 이상 바꾸지 않습니다.
 */
object LauncherIconManager {

    private const val PREFS_NAME = "launcher_icon_state"
    private const val KEY_ACTIVE_TIER = "active_tier"
    private const val KEY_LAST_UPDATED_DATE = "last_updated_date"
    private const val KEY_ENABLED = "dynamic_icon_enabled"
    private const val KEY_PENDING_RESET = "pending_reset"
    private val DEFAULT_TIER = IconTier.YELLOW

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** 설정에서 켜고 끕니다. 아이콘 전환 자체는 다음 화면 꺼짐 시점에 적용됩니다. */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    /** 전체 기록 삭제 후 다음 화면 꺼짐 시점에 기본 아이콘으로 되돌리도록 예약합니다. */
    fun scheduleResetToDefault(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_PENDING_RESET, true)
            .putString(KEY_LAST_UPDATED_DATE, "")
            .apply()
    }

    enum class IconTier(val id: String, val aliasClassName: String) {
        GREEN("green", "com.digitscore.app.ui.MainActivityAliasGreen"),
        YELLOW("yellow", "com.digitscore.app.ui.MainActivityAliasYellow"),
        ORANGE("orange", "com.digitscore.app.ui.MainActivityAliasOrange"),
        RED("red", "com.digitscore.app.ui.MainActivityAliasRed");

        companion object {
            fun fromScore(score: Int): IconTier = when {
                score >= 80 -> GREEN
                score >= 60 -> YELLOW
                score >= 40 -> ORANGE
                else -> RED
            }

            fun fromId(id: String?): IconTier =
                entries.firstOrNull { it.id == id } ?: YELLOW
        }
    }

    /**
     * 화면이 꺼져 있는 상태에서만 하루 최대 1회 런처 아이콘 단계를 전환합니다.
     * 사용자가 앱을 사용 중일 때 런처 새로고침으로 인해 화면이 닫히는 현상을 방지합니다.
     */
    fun maybeUpdateOnScreenOff(
        context: Context,
        score: Int,
        logicalDateString: String
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTier = IconTier.fromId(prefs.getString(KEY_ACTIVE_TIER, DEFAULT_TIER.id))
        val pendingReset = prefs.getBoolean(KEY_PENDING_RESET, false)
        if (pendingReset || !isEnabled(context)) {
            if (pendingReset) prefs.edit().putBoolean(KEY_PENDING_RESET, false).apply()
            return if (currentTier != DEFAULT_TIER) applyTierIcon(context, DEFAULT_TIER) else false
        }
        val targetTier = IconTier.fromScore(score)
        if (targetTier == currentTier) {
            return false
        }

        val lastUpdatedDate = prefs.getString(KEY_LAST_UPDATED_DATE, "") ?: ""
        if (lastUpdatedDate == logicalDateString) {
            // 하루 최대 1회만 아이콘을 변경하여 잦은 런처 갱신을 방지합니다.
            return false
        }

        return applyTierIcon(context, targetTier, logicalDateString)
    }

    fun applyTierIcon(
        context: Context,
        targetTier: IconTier,
        logicalDateString: String? = null
    ): Boolean {
        return try {
            val pm = context.packageManager
            val pkg = context.packageName

            // 새 아이콘 별칭을 먼저 활성화한 뒤 나머지 별칭을 비활성화합니다.
            val targetComponent = ComponentName(pkg, targetTier.aliasClassName)
            pm.setComponentEnabledSetting(
                targetComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            for (tier in IconTier.entries) {
                if (tier != targetTier) {
                    val comp = ComponentName(pkg, tier.aliasClassName)
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ACTIVE_TIER, targetTier.id)
            if (!logicalDateString.isNullOrBlank()) {
                editor.putString(KEY_LAST_UPDATED_DATE, logicalDateString)
            }
            editor.apply()
            true
        } catch (e: Exception) {
            android.util.Log.w("DigitsCoreIcon", "Failed to switch launcher icon alias", e)
            false
        }
    }
}
