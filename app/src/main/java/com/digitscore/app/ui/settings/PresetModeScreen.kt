package com.digitscore.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.digitscore.app.data.DigitsDatabase
import com.digitscore.app.data.entity.UserSettingsEntity
import com.digitscore.app.model.PresetMode
import com.digitscore.app.ui.theme.ScoreGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetModeScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { DigitsDatabase.getInstance(context) }

    val userSettings by db.settingsDao().getSettingsFlow().collectAsState(initial = null)
    val settings = userSettings ?: UserSettingsEntity()
    val selectedModeId = settings.selectedPresetModeId

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("디톡스 모드 및 가중치 설정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = Color.White
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 프리셋 모드 선택
            item {
                Text(
                    text = "디톡스 프리셋 모드 선택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            items(PresetMode.entries) { mode ->
                val isSelected = (mode.id == selectedModeId)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch(Dispatchers.IO) {
                                val rule = mode.scoreRule
                                db.settingsDao().insertOrUpdateSettings(
                                    settings.copy(
                                        selectedPresetModeId = mode.id,
                                        distractingWeightPerMinute = rule.distractingWeightPerMinute,
                                        productiveBonusPerMinute = rule.productiveBonusPerMinute,
                                        idleBonusPer10Minutes = rule.idleBonusPer10Minutes,
                                        maxIdleBonus = rule.maxIdleBonus,
                                        maxProductiveBonus = rule.maxProductiveBonus,
                                        targetUnlockCount = rule.unlockPenaltyThreshold,
                                        unlockPenaltyPerCount = rule.unlockPenaltyPerCount,
                                        lateNightMultiplier = rule.lateNightMultiplier,
                                        isLogAccelerationEnabled = rule.isLogAccelerationEnabled
                                    )
                                )
                            }
                        },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mode.title,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                fontSize = 15.sp
                            )
                            Text(
                                text = mode.description,
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "선택됨",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // 2. 개인 목표 방어선
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "개인 목표 방어선",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "최저 점수 방어선: ${settings.minimumScoreDefenseLine}점",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "점수가 이 이하로 떨어지면 디톡스 경고를 강조합니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.minimumScoreDefenseLine.toFloat(),
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(minimumScoreDefenseLine = newValue.toInt())
                                    )
                                }
                            },
                            valueRange = 30f..90f,
                            steps = 11
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "일일 목표 언락 제한: ${settings.targetUnlockCount}회",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "이 횟수를 초과하여 스마트폰을 켤 때 페널티가 누적됩니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.targetUnlockCount.toFloat(),
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(targetUnlockCount = newValue.toInt())
                                    )
                                }
                            },
                            valueRange = 10f..80f,
                            steps = 13
                        )
                    }
                }
            }

            // 3. 세부 가중치 커스텀 설정
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "세부 가중치 커스텀 설정",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    androidx.compose.material3.TextButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                val currentPreset = PresetMode.fromId(settings.selectedPresetModeId)
                                val rule = currentPreset.scoreRule
                                db.settingsDao().insertOrUpdateSettings(
                                    settings.copy(
                                        distractingWeightPerMinute = rule.distractingWeightPerMinute,
                                        productiveBonusPerMinute = rule.productiveBonusPerMinute,
                                        idleBonusPer10Minutes = rule.idleBonusPer10Minutes,
                                        maxIdleBonus = rule.maxIdleBonus,
                                        maxProductiveBonus = rule.maxProductiveBonus,
                                        targetUnlockCount = rule.unlockPenaltyThreshold,
                                        unlockPenaltyPerCount = rule.unlockPenaltyPerCount,
                                        lateNightMultiplier = rule.lateNightMultiplier,
                                        isLogAccelerationEnabled = rule.isLogAccelerationEnabled
                                    )
                                )
                            }
                        }
                    ) {
                        Text(text = "기본값 초기화", fontSize = 12.sp)
                    }
                }
            }

            // 심야(24시~05시) 감점 가속 배수
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val lateNightMult = String.format(java.util.Locale.US, "%.1f", settings.lateNightMultiplier)
                        Text(
                            text = "🌙 심야(24시~05시) 감점 배수: ${lateNightMult}배",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "자정부터 새벽 5시까지 방해 앱 사용 시 감점을 배수로 가속합니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.lateNightMultiplier,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(lateNightMultiplier = (newValue * 10).toInt() / 10f)
                                    )
                                }
                            },
                            valueRange = 1.0f..3.0f,
                            steps = 19
                        )
                    }
                }
            }

            // 장시간 연속 사용 로그(Log) 가속 스위치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📈 장시간 사용 로그(Log) 가속",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "방해 앱을 15분 이상 오래 사용할수록 감점 속도가 비선형으로 빨라집니다.",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = settings.isLogAccelerationEnabled,
                            onCheckedChange = { isChecked ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(isLogAccelerationEnabled = isChecked)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 방해 앱 감점 가중치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val dWeight = String.format(java.util.Locale.US, "%.1f", settings.distractingWeightPerMinute)
                        Text(
                            text = "⚠️ 방해 앱 1분당 감점치: ${dWeight}점",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "SNS, 영상 등 방해 앱 사용 1분당 차감되는 기본 점수입니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.distractingWeightPerMinute,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(distractingWeightPerMinute = (newValue * 10).toInt() / 10f)
                                    )
                                }
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 18
                        )
                    }
                }
            }

            // 화면 미사용(Idle) 회복 가중치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val idleVal = String.format(java.util.Locale.US, "%.2f", settings.idleBonusPer10Minutes)
                        Text(
                            text = "🌿 화면 미사용 10분당 회복치: ${idleVal}점",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "스마트폰 화면을 끄고 휴식할 때 10분당 회복되는 점수입니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.idleBonusPer10Minutes,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(idleBonusPer10Minutes = (newValue * 20).toInt() / 20f)
                                    )
                                }
                            },
                            valueRange = 0.05f..1.0f,
                            steps = 18
                        )
                    }
                }
            }

            // 일일 보너스 최대 상한선(Cap)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "🛡️ 일일 회복 보너스 최대 상한: ${settings.maxIdleBonus.toInt()}점",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "수면 및 장시간 미사용으로 하루에 얻을 수 있는 보너스 최대 한도입니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.maxIdleBonus,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(
                                            maxIdleBonus = newValue.toInt().toFloat(),
                                            maxProductiveBonus = newValue.toInt().toFloat()
                                        )
                                    )
                                }
                            },
                            valueRange = 5f..30f,
                            steps = 4
                        )
                    }
                }
            }

            // 언락 초과당 감점치
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val unlVal = String.format(java.util.Locale.US, "%.1f", settings.unlockPenaltyPerCount)
                        Text(
                            text = "🔓 언락 기준 초과 1회당 감점치: ${unlVal}점",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "일일 목표 언락 횟수를 초과할 때마다 차감되는 페널티 점수입니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                        )
                        Slider(
                            value = settings.unlockPenaltyPerCount,
                            onValueChange = { newValue ->
                                scope.launch(Dispatchers.IO) {
                                    db.settingsDao().insertOrUpdateSettings(
                                        settings.copy(unlockPenaltyPerCount = (newValue * 10).toInt() / 10f)
                                    )
                                }
                            },
                            valueRange = 0.1f..2.0f,
                            steps = 18
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
