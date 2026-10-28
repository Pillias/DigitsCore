package com.digitscore.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.UsageHistoryImporter
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.ui.theme.DigitsShape
import com.digitscore.app.ui.theme.DigitsTextSize

/**
 * 지난 사용 기록을 불러온 뒤 한 번만: 가장 많이 쓴 앱 3개의 등급을 확인하도록 안내합니다 (V0_9_39 평가 A-1).
 * 등급이 맞아야 첫날부터 코어 지수를 믿을 수 있습니다. 확인하거나 닫으면 다시 나오지 않습니다.
 */
@Composable
fun TopAppsGradeCheckCard(onOpenAppSettings: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("dashboard_notices", android.content.Context.MODE_PRIVATE) }
    var dismissed by remember { mutableStateOf(prefs.getBoolean(KEY, false)) }
    var top by remember { mutableStateOf<List<Triple<String, Long, Int>>>(emptyList()) }
    val english = AppLocale.currentLanguage(context) == AppLocale.ENGLISH

    LaunchedEffect(dismissed) {
        if (dismissed) return@LaunchedEffect
        val imported = UsageHistoryImporter.getImportedDates(context)
        if (imported.isEmpty()) return@LaunchedEffect
        val rows = DigitsDatabase.getInstance(context).dailyAppUsageDao().getRange(imported.min(), "9999-12-31")
        top = rows.groupBy { it.packageName }
            .map { (_, r) ->
                val latest = r.maxBy { it.dateString }
                Triple(r.lastOrNull { it.appName.isNotBlank() }?.appName ?: latest.packageName,
                    r.sumOf { it.usageMillis } / 60_000L / r.map { it.dateString }.distinct().size.coerceAtLeast(1),
                    latest.categoryLevel)
            }
            .filter { it.third > 0 } // 권장·필수 앱은 확인할 필요가 적습니다
            .sortedByDescending { it.second }
            .take(3)
    }
    if (dismissed || top.isEmpty()) return

    fun close() { prefs.edit().putBoolean(KEY, true).apply(); dismissed = true }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = DigitsShape.lg,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (english) "Check grades of your top 3 apps" else "가장 많이 쓴 앱 3개의 등급을 확인해 주세요",
                fontWeight = FontWeight.Bold,
                fontSize = DigitsTextSize.body
            )
            Text(
                text = if (english) "Correct grades make the Core Index fit your day from the start."
                else "등급이 맞아야 첫날부터 코어 지수가 내 생활에 맞게 움직입니다.",
                fontSize = DigitsTextSize.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            top.forEach { (name, avgMin, level) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, modifier = Modifier.weight(1f), fontSize = DigitsTextSize.caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        text = (if (english) "${avgMin}min/day · " else "하루 ${avgMin}min · ") + gradeName(level, english),
                        fontSize = DigitsTextSize.caption,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { close() }) { Text(if (english) "Looks right" else "맞아요", fontSize = DigitsTextSize.label) }
                Button(onClick = { close(); onOpenAppSettings() }, shape = DigitsShape.sm) {
                    Text(if (english) "Change grades" else "등급 바꾸기", fontSize = DigitsTextSize.label)
                }
            }
        }
    }
}

private const val KEY = "top_apps_grade_check_done"

private fun gradeName(level: Int, english: Boolean): String = when {
    level <= 0 -> if (english) "Study·Tool" else "권장·필수"
    level >= 3 -> if (english) "Managed" else "관리"
    else -> if (english) "General" else "일반"
}
