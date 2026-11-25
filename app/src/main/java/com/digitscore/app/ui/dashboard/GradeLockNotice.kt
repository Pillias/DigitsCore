package com.digitscore.app.ui.dashboard

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.model.AppCategoryPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * 등급 고정 첫 실행에 한 번만: 등급을 직접 바꿔 둔 적이 있는 사용자에게 바뀐 점과 요청 방법을 알립니다.
 * 바꿔 둔 등급이 없으면 아무것도 보이지 않고 확인한 것으로 저장합니다.
 * [enabled]가 false(다른 안내가 떠 있는 동안)면 기다렸다가 다음 기회에 띄웁니다.
 */
@Composable
fun GradeLockNotice(enabled: Boolean, onOpenAppSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var changedCount by remember { mutableIntStateOf(-1) }

    LaunchedEffect(Unit) {
        if (!AppCategoryPolicy.userGradesLocked || prefs.getBoolean(KEY_SEEN, false)) return@LaunchedEffect
        // 업데이트 안내(1.5초 뒤 결정)가 먼저 정해지도록 조금 기다립니다.
        kotlinx.coroutines.delay(2_500L)
        val count = withContext(Dispatchers.IO) {
            // 변경 표시만 있고 기본값과 같은 행(백업 복원·예전 온보딩)은 세지 않습니다.
            val changed = runCatching {
                DigitsDatabase.getInstance(context).appDao().getAllAppWeights().first()
                    .filter { it.isUserModified }
                    // 설치되어 있지 않은 앱(백업에만 남은 앱)은 지금 계산에 영향이 없으므로 뺍니다.
                    .filter { runCatching { context.packageManager.getApplicationInfo(it.packageName, 0) }.isSuccess }
                    .filter { it.categoryType.canonical != com.digitscore.app.data.UsageStatsHelper.defaultCategoryFor(context, it.packageName) }
                    .map { it.packageName.lowercase() }
                    .toSet()
            }.getOrDefault(emptySet())
            val study = runCatching {
                AppCategoryPolicy.getCustomStudyPackages(context).filterNot { pkg ->
                    AppCategoryPolicy.EDUCATION_PACKAGES.any { pkg.startsWith(it.lowercase()) }
                }.toSet()
            }.getOrDefault(emptySet())
            (changed + study).size
        }
        if (count <= 0) prefs.edit().putBoolean(KEY_SEEN, true).apply() else changedCount = count
    }

    if (changedCount <= 0 || !enabled) return
    val english = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    fun close() { prefs.edit().putBoolean(KEY_SEEN, true).apply(); changedCount = 0 }

    AlertDialog(
        onDismissRequest = { close() },
        title = {
            Text(
                if (english) "App grades are now the same for everyone" else "앱 등급이 모두 같은 기준이 됐습니다",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                if (english) "Grades you changed now use the default grade. Past records stay as they are."
                else "직접 바꾼 등급은 오늘부터 기본 등급으로 계산됩니다. 지난 기록은 그대로입니다."
            )
        },
        confirmButton = {
            TextButton(onClick = { close(); onOpenAppSettings() }) {
                Text(if (english) "View app grades" else "앱 등급 보기", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { close() }) { Text(if (english) "OK" else "확인") }
        }
    )
}

private const val PREFS = "dashboard_notices"
private const val KEY_SEEN = "grade_lock_notice_seen"
