package com.digitscore.app.ui.settings

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.digitscore.app.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.ScoreRepository
import com.digitscore.app.data.UsageStatsHelper
import com.digitscore.app.data.entity.AppWeightEntity
import com.digitscore.app.i18n.AppLocale
import com.digitscore.app.i18n.UiTranslator
import com.digitscore.app.i18n.formatMinutesSmallUnit
import com.digitscore.app.i18n.formatReadableMinutes
import com.digitscore.app.model.AppCategoryPolicy
import com.digitscore.app.model.AppCategoryType
import com.digitscore.app.service.TrackerForegroundService
import com.digitscore.app.model.AppUsage
import com.digitscore.app.ui.components.DetailChevron
import com.digitscore.app.ui.components.ResponsiveContent
import com.digitscore.app.ui.dashboard.AppDetailDialog
import com.digitscore.app.ui.theme.ScoreGreen
import com.digitscore.app.ui.theme.ScoreRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppWeightSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH
    val scope = rememberCoroutineScope()
    val db = remember { DigitsDatabase.getInstance(context) }

    val savedAppWeights by db.appDao().getAllAppWeights().collectAsState(initial = emptyList())
    val todayUsageSummary by ScoreRepository.rollingUsageSummary.collectAsState()
    var installedApps by remember { mutableStateOf<List<AppUsage>>(emptyList()) }
    var appCategoryMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableIntStateOf(0) } // 0: 사용시간순, 1: 카테고리별, 2: 가나다순, 3: 앱등급별
    var studyToggleVersion by remember { mutableIntStateOf(0) }
    var localGradeOverrides by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }

    var selectedAppForDetail by remember { mutableStateOf<AppUsage?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = UsageStatsHelper.getInstalledApps(context, emptyMap())
            val pm = context.packageManager
            val catMap = HashMap<String, Int>()
            apps.forEach { app ->
                val appInfo = runCatching { pm.getApplicationInfo(app.packageName, 0) }.getOrNull()
                val isLegacyGame = appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_IS_GAME) != 0
                val cat = when {
                    appInfo == null -> ApplicationInfo.CATEGORY_UNDEFINED
                    appInfo.category != ApplicationInfo.CATEGORY_UNDEFINED -> appInfo.category
                    isLegacyGame -> ApplicationInfo.CATEGORY_GAME
                    else -> ApplicationInfo.CATEGORY_UNDEFINED
                }
                catMap[app.packageName] = cat
            }
            installedApps = apps
            appCategoryMap = catMap
        }
    }

    val appWeightMap = remember(savedAppWeights) {
        savedAppWeights.associateBy { it.packageName }
    }

    val allResolvedApps = remember(installedApps, appWeightMap, localGradeOverrides, studyToggleVersion) {
        installedApps.map { app ->
            val localIdx = localGradeOverrides[app.packageName]
            val effectiveCat = if (localIdx != null) {
                when (localIdx) {
                    0, 1 -> AppCategoryType.EXEMPT
                    3 -> AppCategoryType.DISTRACTING
                    else -> AppCategoryType.NEUTRAL
                }
            } else {
                val saved = appWeightMap[app.packageName]?.takeIf { it.isUserModified }
                (saved?.categoryType ?: app.categoryType).canonical
            }
            app.copy(categoryType = effectiveCat)
        }
    }

    val sortedApps = remember(allResolvedApps, sortOption, searchQuery, appCategoryMap) {
        val searched = if (searchQuery.isBlank()) allResolvedApps
        else allResolvedApps.filter {
            it.appName.contains(searchQuery, ignoreCase = true) ||
            it.packageName.contains(searchQuery, ignoreCase = true)
        }
        when (sortOption) {
            0 -> searched.sortedWith(compareByDescending<AppUsage> { it.usageTimeMillis }.thenBy { it.appName.lowercase() })
            1 -> searched.sortedWith(
                compareBy<AppUsage> { app ->
                    val cat = appCategoryMap[app.packageName] ?: ApplicationInfo.CATEGORY_UNDEFINED
                    resolveAppTypeOrderAndLabel(app.packageName, cat, isEn).first
                }.thenByDescending { it.usageTimeMillis }
                .thenBy { it.appName.lowercase() }
            )
            2 -> searched.sortedBy { it.appName.lowercase() }
            3 -> searched.sortedWith(compareBy<AppUsage> {
                val isStudy = AppCategoryPolicy.isStudyOrReadingPackage(it.packageName, context)
                when (it.categoryType.canonical) {
                    AppCategoryType.DISTRACTING -> 0
                    AppCategoryType.EXEMPT -> if (isStudy) 1 else 2
                    AppCategoryType.NEUTRAL -> 3
                    else -> 4
                }
            }.thenByDescending { it.usageTimeMillis })
            else -> searched
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEn) "App Usage & Grades" else "앱별 사용량 및 등급",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = UiTranslator.translate("뒤로가기"),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        ResponsiveContent(modifier = Modifier.padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // 오늘 사용 시간 3종 요약 서브헤더 바
                val totalResolvedUsage = allResolvedApps.sumOf { it.usageTimeMillis }
                val screenMins = todayUsageSummary.totalScreenTimeMillis / 60_000L
                val managedMins = if (totalResolvedUsage > 0L) {
                    allResolvedApps.filter { it.categoryType.canonical == AppCategoryType.DISTRACTING }.sumOf { it.usageTimeMillis } / 60_000L
                } else {
                    todayUsageSummary.managedTimeMillis / 60_000L
                }
                val exemptMins = if (totalResolvedUsage > 0L) {
                    allResolvedApps.filter { it.categoryType.canonical == AppCategoryType.EXEMPT }.sumOf { it.usageTimeMillis } / 60_000L
                } else {
                    (todayUsageSummary.growthTimeMillis / 60_000L).coerceAtLeast(0L)
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isEn) "Screen" else "화면", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = formatMinutesSmallUnit(screenMins, unitSize = 10.sp),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isEn) "Managed" else "관리", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = formatMinutesSmallUnit(managedMins, unitSize = 10.sp),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = ScoreRed
                            )
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(24.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(if (isEn) "Exempt" else "면제", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                            Text(
                                text = formatMinutesSmallUnit(exemptMins, unitSize = 10.sp),
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF29B6F6)
                            )
                        }
                    }
                }

                // 검색창
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    placeholder = {
                        Text(
                            if (isEn) "Search app name or package" else "앱 이름 또는 패키지 검색",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = if (isEn) "Search" else UiTranslator.translate("검색"),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedTextColor = MaterialTheme.colorScheme.onBackground,
                        unfocusedTextColor = MaterialTheme.colorScheme.onBackground
                    )
                )

                // 4가지 정렬 칩
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sortLabels = listOf(
                        if (isEn) "Usage Time" else "사용시간순",
                        if (isEn) "Category" else "카테고리별",
                        if (isEn) "Name A-Z" else "가나다순",
                        if (isEn) "Grade" else "앱등급별"
                    )
                    sortLabels.forEachIndexed { idx, label ->
                        FilterChip(
                            selected = sortOption == idx,
                            onClick = { sortOption = idx },
                            label = { Text(label, fontSize = 12.sp) }
                        )
                    }
                }

                // 앱 목록
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(sortedApps, key = { it.packageName }) { app ->
                        val cat = appCategoryMap[app.packageName] ?: ApplicationInfo.CATEGORY_UNDEFINED
                        val typeLabel = resolveAppTypeOrderAndLabel(app.packageName, cat, isEn).second

                        AppWeightRowCard(
                            app = app,
                            typeLabel = typeLabel,
                            onClick = { selectedAppForDetail = app },
                            studyToggleVersion = studyToggleVersion,
                            overrideGradeIndex = localGradeOverrides[app.packageName],
                            onGradeSelect = { newGradeIndex ->
                                val (catType, isStudy) = when (newGradeIndex) {
                                    0 -> AppCategoryType.EXEMPT to true
                                    1 -> AppCategoryType.EXEMPT to false
                                    3 -> AppCategoryType.DISTRACTING to false
                                    else -> AppCategoryType.NEUTRAL to false
                                }
                                localGradeOverrides = localGradeOverrides + (app.packageName to newGradeIndex)
                                AppCategoryPolicy.setCustomStudyPackage(context, app.packageName, isStudy)
                                studyToggleVersion++
                                scope.launch(Dispatchers.IO) {
                                    db.appDao().insertOrUpdateAppWeight(
                                        AppWeightEntity(
                                            packageName = app.packageName,
                                            appName = app.appName,
                                            categoryType = catType,
                                            isUserModified = true
                                        )
                                    )
                                    TrackerForegroundService.refreshNotification(context)
                                }
                            }
                        )
                    }

                    if (sortedApps.isEmpty()) {
                        item {
                            Text(
                                text = if (isEn) "No apps found." else "검색 결과가 없습니다.",
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // 1. 앱 상세 분석 다이얼로그 (차트, 세션 정보)
    selectedAppForDetail?.let { targetApp ->
        AppDetailDialog(
            appUsage = targetApp,
            onDismiss = { selectedAppForDetail = null },
            onCategoryChanged = { newCategory ->
                scope.launch(Dispatchers.IO) {
                    db.appDao().insertOrUpdateAppWeight(
                        AppWeightEntity(
                            packageName = targetApp.packageName,
                            appName = targetApp.appName,
                            categoryType = newCategory,
                            isUserModified = true
                        )
                    )
                }
                selectedAppForDetail = null
            }
        )
    }
}

private fun resolveAppTypeOrderAndLabel(
    packageName: String,
    appCategory: Int,
    isEn: Boolean
): Pair<Int, String> {
    val pkg = packageName.lowercase()
    return when {
        appCategory == ApplicationInfo.CATEGORY_GAME ||
        pkg.contains("game") || pkg.contains("chess") || pkg.contains("sudoku") ||
        pkg.contains("puzzle") || pkg.contains("poker") || pkg.contains("rpg") ->
            0 to (if (isEn) "Game" else "게임")

        appCategory == ApplicationInfo.CATEGORY_SOCIAL ||
        (android.os.Build.VERSION.SDK_INT >= 31 && appCategory == 8 /* CATEGORY_MESSAGE */) ||
        AppCategoryPolicy.MESSENGER_PACKAGES.any { pkg.startsWith(it) } ||
        AppCategoryPolicy.SOCIAL_MEDIA_PACKAGES.any { pkg.startsWith(it) } ||
        pkg.contains("kakao.talk") || pkg.contains("instagram") || pkg.contains("tiktok") ||
        pkg.contains("line") || pkg.contains("facebook") || pkg.contains("twitter") ||
        pkg.contains("telegram") || pkg.contains("discord") || pkg.contains("youtube") ->
            1 to (if (isEn) "Social" else "소셜·메신저")

        appCategory == ApplicationInfo.CATEGORY_VIDEO ||
        pkg.contains("netflix") || pkg.contains("tving") || pkg.contains("coupangplay") ||
        pkg.contains("wavve") || pkg.contains("disney") || pkg.contains("watcha") ->
            2 to (if (isEn) "Media" else "동영상·미디어")

        appCategory == ApplicationInfo.CATEGORY_NEWS ||
        pkg.contains("chrome") || pkg.contains("sec.android.app.sbrowser") ||
        pkg.contains("naver.searchapp") || pkg.contains("browser") || pkg.contains("daum") ->
            3 to (if (isEn) "Web & News" else "뉴스·웹")

        appCategory == ApplicationInfo.CATEGORY_PRODUCTIVITY ||
        AppCategoryPolicy.EDUCATION_PACKAGES.any { pkg.startsWith(it) } ||
        pkg.contains("calendar") || pkg.contains("notes") || pkg.contains("office") ||
        pkg.contains("notion") || pkg.contains("slack") || pkg.contains("teams") ||
        pkg.contains("docs") || pkg.contains("drive") || pkg.contains("evernote") ->
            4 to (if (isEn) "Work" else "생산성·업무")

        appCategory == ApplicationInfo.CATEGORY_MAPS ||
        AppCategoryPolicy.NAVIGATION_PACKAGES.any { pkg.startsWith(it) } ||
        pkg.contains("map") || pkg.contains("tmap") || pkg.contains("kakaot") ||
        pkg.contains("subway") || pkg.contains("transit") || pkg.contains("waze") ->
            5 to (if (isEn) "Navigation" else "지도·교통")

        AppCategoryPolicy.FINANCE_AND_WORK_PACKAGES.any { pkg.startsWith(it) && (pkg.contains("bank") || pkg.contains("pay") || pkg.contains("toss")) } ||
        pkg.contains("bank") || pkg.contains("pay") || pkg.contains("toss") ||
        pkg.contains("card") || pkg.contains("finance") || pkg.contains("wallet") ->
            6 to (if (isEn) "Finance" else "금융·결제")

        AppCategoryPolicy.SHOPPING_PACKAGES.any { pkg.startsWith(it) } ||
        AppCategoryPolicy.WEBTOON_PACKAGES.any { pkg.startsWith(it) } ||
        pkg.contains("coupang") || pkg.contains("11st") || pkg.contains("shopping") ||
        pkg.contains("market") || pkg.contains("danawa") || pkg.contains("aliexpress") ||
        pkg.contains("temu") || pkg.contains("musinsa") || pkg.contains("zigzag") ||
        pkg.contains("towneers") ->
            7 to (if (isEn) "Shopping" else "쇼핑")

        appCategory == ApplicationInfo.CATEGORY_IMAGE ||
        appCategory == ApplicationInfo.CATEGORY_AUDIO ||
        AppCategoryPolicy.UTILITY_PACKAGES.any { pkg.startsWith(it) } ||
        AppCategoryPolicy.CALL_PACKAGES.any { pkg.startsWith(it) } ||
        pkg.contains("camera") || pkg.contains("clock") || pkg.contains("calculator") ||
        pkg.contains("gallery") || pkg.contains("settings") ->
            8 to (if (isEn) "Utility" else "도구·유틸")

        else ->
            9 to (if (isEn) "General" else "일반")
    }
}

@Composable
private fun AppWeightRowCard(
    app: AppUsage,
    typeLabel: String,
    onClick: () -> Unit,
    onGradeSelect: (Int) -> Unit,
    studyToggleVersion: Int = 0,
    overrideGradeIndex: Int? = null
) {
    val context = LocalContext.current
    val isEn = AppLocale.currentLanguage(context) == AppLocale.ENGLISH

    val isStudy = remember(app.packageName, studyToggleVersion) {
        AppCategoryPolicy.isStudyOrReadingPackage(app.packageName, context)
    }
    val currentGradeIndex = overrideGradeIndex ?: when {
        app.categoryType.canonical == AppCategoryType.EXEMPT && isStudy -> 0 // 집중 (Focus)
        app.categoryType.canonical == AppCategoryType.EXEMPT -> 1 // 필수 (Tool)
        app.categoryType.canonical == AppCategoryType.DISTRACTING -> 3 // 관리 (Limit)
        else -> 2 // 일반 (Daily)
    }

    val icon by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName).toBitmap(56, 56).asImageBitmap()
            }.getOrNull()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 앱 아이콘 (28.dp)
            if (icon != null) {
                Image(
                    bitmap = icon!!,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 좌측: 2줄 레이아웃 (1줄: 앱이름, 2줄: [56.dp 카테고리명] + [오늘 사용 시간])
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 6.dp)
            ) {
                Text(
                    text = app.appName,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(56.dp)
                    ) {
                        Text(
                            text = typeLabel,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatMinutesSmallUnit(app.usageTimeMinutes, unitSize = 8.5.sp),
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            // 우측: 가로 4칸의 1-tap 세그먼트 버튼 [집중 | 필수 | 일반 | 관리] / [Focus | Tool | Daily | Limit]
            val gradeLabels = if (isEn) listOf("Focus", "Tool", "Daily", "Limit") else listOf("집중", "필수", "일반", "관리")
            val gradeColors = listOf(ScoreGreen, Color(0xFF29B6F6), MaterialTheme.colorScheme.onSurfaceVariant, ScoreRed)

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.height(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    gradeLabels.forEachIndexed { idx, label ->
                        val isSelected = currentGradeIndex == idx
                        val activeColor = gradeColors[idx]
                        Box(
                            modifier = Modifier
                                .height(28.dp)
                                .background(
                                    color = if (isSelected) activeColor else Color.Transparent,
                                    shape = RoundedCornerShape(5.dp)
                                )
                                .clickable { onGradeSelect(idx) }
                                .padding(horizontal = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                        if (idx < 3) {
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(14.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            )
                        }
                    }
                }
            }
        }
    }
}
