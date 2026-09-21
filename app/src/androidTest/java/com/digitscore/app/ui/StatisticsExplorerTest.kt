package com.digitscore.app.ui

import android.graphics.Bitmap
import android.content.ContentValues
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.digitscore.app.data.*
import com.digitscore.app.data.entity.*
import com.digitscore.app.ui.statistics.StatisticsScreen
import com.digitscore.app.ui.theme.DigitsCoreTheme
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class StatisticsExplorerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chartPeriodsRankingsAndDarkLargeTextRender() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val db = Room.inMemoryDatabaseBuilder(context, DigitsDatabase::class.java).build()
        val locale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
        try {
            val now = System.currentTimeMillis()
            val hour = statisticHour(now)
            val usage = mutableListOf<UsageHourEntity>()
            val scores = mutableListOf<ScoreHourEntity>()
            val impacts = mutableListOf<ScoreImpactHourEntity>()
            for (i in 0 until 14*24) {
                val h = hour - (i+1)*STAT_HOUR
                val score = 72.0 + kotlin.math.sin(i/8.0)*5
                scores.add(ScoreHourEntity(h,5,h,h+STAT_HOUR-1,score,score+1,score-2,score+2))
                usage.add(UsageHourEntity(h,"","",900_000,600_000,5,2,3,2))
                usage.add(UsageHourEntity(h,"youtube.test","YouTube",600_000,600_000,3,1))
                usage.add(UsageHourEntity(h,"study.test","Duolingo",300_000,0,2,1))
                impacts.add(ScoreImpactHourEntity(h,"",1.2,0.3,STAT_HOUR,h,h+STAT_HOUR-1))
                impacts.add(ScoreImpactHourEntity(h,"youtube.test",1.2,0.0,0,h,h+STAT_HOUR-1))
                if(i<24) {
                    val date=Instant.ofEpochMilli(h).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    db.foregroundUsageSessionDao().insertAll(listOf(ForegroundUsageSessionEntity(
                        "youtube.test",h,h+600_000,date,"YouTube",3,isLateNight=false)))
                    db.coreIndexSampleDao().insertOrUpdate(CoreIndexSampleEntity(h,h,date,score.toInt(),score,0.0,0.0,"balanced",5))
                }
            }
            db.statisticsDao().putUsage(usage); db.statisticsDao().putScores(scores); db.statisticsDao().putImpacts(impacts)
            val dark = mutableStateOf(false)
            compose.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, if(dark.value) 1.3f else 1f)) {
                    DigitsCoreTheme(darkTheme=dark.value) { StatisticsScreen({}, db) }
                }
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Core Index").fetchSemanticsNodes().isNotEmpty() }
            compose.waitForIdle()
            fun screenshot(name: String) {
                instrumentation.uiAutomation.takeScreenshot().also { image ->
                    // AGP removes the test app after the suite; keep synthetic screenshots
                    // in the disposable emulator's media collection for visual QA.
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, "statistics-$name.png")
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DigitsCoreTests")
                    }
                    val uri = checkNotNull(context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
                    checkNotNull(context.contentResolver.openOutputStream(uri)).use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                    image.recycle()
                }
            }
            screenshot("light")
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("1w"))
            compose.onNodeWithText("1w").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
            compose.onNodeWithText("Daily ▾").performClick()
            compose.onNodeWithText("Hourly ▾").assertExists()
            compose.onNodeWithText("Hourly ▾").performClick()
            compose.waitForIdle(); screenshot("candles")
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("Score impact"))
            compose.onNodeWithText("Score impact").performClick()
            compose.onNodeWithTag("statistics-list").performScrollToNode(hasText("YouTube"))
            compose.onNodeWithText("YouTube").performClick()
            compose.onNode(hasText("Recorded score loss") and hasAnyAncestor(hasTestTag("app-detail"))).assertExists()
            compose.onNode(hasContentDescription("Back") and hasAnyAncestor(hasTestTag("app-detail"))).performClick()
            compose.runOnIdle { dark.value=true }
            compose.onNodeWithTag("statistics-list").performScrollToIndex(0)
            compose.waitForIdle(); screenshot("dark-large")
        } finally { Locale.setDefault(locale); db.close() }
    }
}
