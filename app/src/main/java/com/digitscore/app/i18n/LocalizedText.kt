package com.digitscore.app.i18n

import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/** 기존 Compose 문구도 앱 언어 전환에 즉시 참여시키는 공통 Text입니다. */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current
) {
    androidx.compose.material3.Text(
        text = UiTranslator.translate(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style
    )
}

object UiTranslator {
    private val dynamicEnglishReplacements = listOf(
        Regex("사용시간 상위 (\\d+)개 앱") to "Top \$1 Apps by Usage",
        Regex("상위 (\\d+)개 앱") to "Top \$1 Apps",
        Regex("전체 (\\d+)개 보기(.*)") to "View All \$1 Apps\$2",
        Regex("OS 감지 알림 (\\d+)건 · 언락 (\\d+)회") to "OS-detected notifications \$1 · \$2 unlocks",
        Regex("알림 (\\d+)건과 언락 (\\d+)회 비교 그래프") to "Chart comparing \$1 notifications and \$2 unlocks",
        Regex("최근 (\\d+)일 중 (\\d+)일 측정") to "\$2 of the last \$1 days measured",
        Regex("최근 (\\d+)일 사용량은 이전 기간과 비슷합니다\\.") to
            "Usage over the last \$1 days is similar to the previous period.",
        Regex("최근 (\\d+)일 평균이 이전 기간보다 (-?\\d+)% 늘었습니다\\.") to
            "The last \$1-day average increased \$2% from the previous period.",
        Regex("최근 (\\d+)일 평균이 이전 기간보다 (-?\\d+)% 줄었습니다\\.") to
            "The last \$1-day average decreased \$2% from the previous period.",
        Regex("최근 (\\d+)일에 새 사용 기록이 생겼습니다\\.") to
            "New usage was recorded within the last \$1 days.",
        Regex("🌙 심야 사용 (-?\\d+(?:\\.\\d+)?)분") to "🌙 Late-night use: \$1 min"
    )

    private val replacements = listOf(
        "사용 균형 통계 & 리포트" to "Usage Balance & Reports",
        "사용 균형 분석" to "Usage Balance Summary",
        "점수 프리셋 모드 선택" to "Choose a Score Preset",
        "점수가 이 이하로 떨어지면 사용 균형 안내를 강조합니다." to "Guidance is emphasized when the score falls below this value.",
        "이전 사용량 이월" to "Previous-use Carryover",
        "하루 최대 이전 사용량 이월" to "Maximum daily carryover",
        "기기에서 확인 가능한" to "Showing",
        "일의 기록을 표시하고 있어요." to "days available on this device.",
        "오래된 사용 기록은 기기 정책에 따라 제공되지 않을 수 있습니다." to "Older records may be unavailable because of device policy.",
        "기록이 쌓이면 선택한 기준과 실제 평균을 비교합니다." to "Once enough data is recorded, the selected benchmark is compared with your actual average.",
        "조사 평균은 건강 권고가 아니며, 앱 분류와 생활 맥락에 따라 직접 조정해야 합니다." to "Survey averages are not health guidance. Adjust ratings for your own context.",
        "실제 평균" to "Actual average",
        "보정 기준" to "Benchmark",
        "방어선" to "Target line",
        "방어 성공" to "days above target",
        "방해 앱" to "Managed apps",
        "생산성 앱" to "Growth apps",
        "중립/기타" to "Neutral / Other",
        "언락(회)" to "Unlocks",
        "전체 " to "All ",
        "개 보기" to " apps",
        "상위 " to "Top ",
        "개 앱" to " apps",
        "오늘 0시부터 계산한 기존 점수 내역입니다." to "This legacy score is calculated from midnight today.",
        "가장 잦은 시간은" to "Most frequent time:",
        "평균 약" to "About once every",
        "분마다 한 번 열었습니다." to "minutes",
        "이 Android 버전은 알림 이벤트 비교를 제공하지 않습니다." to "This Android version does not provide notification-event comparison.",
        "OS 감지 알림" to "OS-detected notifications",
        "건과 언락" to "vs unlocks",
        "알림 1건당 약" to "About",
        "회 언락했습니다." to "unlocks per notification.",
        "감지된 알림 없이도" to "Unlocks without detected notifications:",
        "아직 비교할 기록이 없습니다." to "There is not enough data to compare yet.",
        "OS 이벤트의 단순 비교이며 알림이 언락의 직접 원인이라는 뜻은 아닙니다." to "This is a simple event comparison and does not imply notifications caused the unlocks.",
        "스마트폰을 무의식적으로 켜는 습관을 줄이면 집중력을 대폭 향상시킬 수 있습니다." to "Reducing unconscious phone checks can help protect your focus.",
        "일일 기준치를 초과하여" to "Above the daily target:",
        "점 감점 적용 중입니다." to "points currently applied.",
        "현재 기준치 이내로 안전하게 유지하고 있습니다." to "Currently within the target.",
        "오늘 사용된 관리 대상 앱이 없습니다. 안정적인 사용 흐름입니다!" to "No managed apps used today. Your usage pattern is steady!",
        "아직 시간대 분석에 필요한 언락 기록이 없습니다." to "There is not enough unlock history for hourly analysis.",
        "아직 분석할 시간대 기록이 없습니다." to "There is no hourly history to analyze yet.",
        "가장 많이 사용한 시간은" to "Highest-usage time:",
        "한 번에 30분 이상 이어진 사용이 있습니다." to "At least one session lasted 30 minutes or more.",
        "한 번에 다소 길게 사용한 구간이 있습니다." to "Some sessions were moderately long.",
        "대체로 짧게 나누어 사용했습니다." to "Usage was mostly split into short sessions.",
        "아직 세션 기록이 없습니다." to "No session history yet.",
        "최근 사용 기록이 생겼습니다." to "New usage history was recorded recently.",
        "이전 기간보다" to "than the previous period",
        "늘었습니다." to "higher",
        "줄었습니다." to "lower",
        "이전 기간과 비슷합니다." to "similar to the previous period.",
        "일 중" to "days,",
        "일 측정" to "days measured",
        "평균 사용이 가장 많은 요일은" to "Highest average weekday:",
        "요일" to "",
        "기본값 초기화" to "Reset Defaults",
        "최저 점수 방어선" to "Minimum score target",
        "일일 목표 언락 제한" to "Daily unlock target",
        "이 횟수를 초과하여 스마트폰을 켤 때 페널티가 누적됩니다." to "Additional unlocks beyond this target add a deduction.",
        "감점 배수" to "deduction multiplier",
        "자정부터 새벽 5시까지 방해 앱 사용 시 감점을 배수로 가속합니다." to "Managed-app use from midnight to 5 AM receives an additional multiplier.",
        "설정한 앱별 누적시간 이후 감점 속도가 비선형으로 빨라집니다." to "Deductions accelerate nonlinearly after the configured usage time.",
        "가속 시작" to "Acceleration starts",
        "이 시간까지는 기본 감점만 적용합니다." to "Only the base deduction applies before this point.",
        "가속 완만함" to "Acceleration scale",
        "값이 클수록 장시간 사용 감점 증가가 완만합니다." to "A larger value makes long-session acceleration gentler.",
        "전날 점수가 설정 기준보다 낮을 때 다음 날 일부만 이월합니다." to "Part of a low previous-day score carries into the next day.",
        "부채 발동 점수" to "Carryover threshold",
        "기준 미달 1점당 이월" to "Carryover per point below target",
        "방해 앱 1분당 감점치" to "Managed-app deduction per minute",
        "SNS, 영상 등 방해 앱 사용 1분당 차감되는 기본 점수입니다." to "Base per-minute deduction for managed apps such as social and video apps.",
        "화면 미사용 10분당 회복치" to "Recovery per 10 screen-off minutes",
        "스마트폰 화면을 끄고 휴식할 때 10분당 회복되는 점수입니다." to "Legacy score recovery per 10 minutes with the screen off.",
        "일일 회복 보너스 최대 상한" to "Maximum daily recovery bonus",
        "수면 및 장시간 미사용으로 하루에 얻을 수 있는 보너스 최대 한도입니다." to "Legacy daily cap for long screen-off periods.",
        "언락 기준 초과 1회당 감점치" to "Deduction per unlock above target",
        "일일 목표 언락 횟수를 초과할 때마다 차감되는 페널티 점수입니다." to "Legacy deduction for each unlock above the daily target.",
        "앱별 기록은 기기 내부 암호화 DB에 저장되며, 백업 파일도 사용자 비밀번호로 암호화됩니다." to "App history is stored in an encrypted on-device database. Backup files are password-encrypted.",
        "기존 기록을 보호하기 위해 이번 실행에서는 호환 모드로 열었습니다. 백업 파일은 계속 사용자 비밀번호로 암호화됩니다." to "Compatibility mode is protecting existing records for this run. Backup files remain password-encrypted.",
        "DB 암호화 호환 모드" to "Database Encryption Compatibility Mode",
        "기록 손실과 실행 중단을 막기 위해 기존 DB를 그대로 사용하고 있습니다. 앱을 다시 시작하면 암호화를 재시도합니다." to "The existing database is preserved to prevent data loss. Encryption will be retried after restart.",
        "앱별 시작·종료 상세는 30일, 날짜별 집계는 365일 보관합니다." to "Detailed app sessions are kept for 30 days and daily aggregates for 365 days.",
        "30일을 넘겨 상세 기록을 보관하려면 만료 전에 암호화 백업을 저장하세요." to "Create an encrypted backup before expiry to retain detailed records longer.",
        "점수·화면시간·언락 횟수를 잠금 해제 전에는 표시하지 않습니다." to "Score, screen time and unlock counts stay hidden until the device is unlocked.",
        "암호화 백업을 복원했습니다." to "Encrypted backup restored.",
        "백업 데이터 형식이 올바르지 않습니다." to "The backup format is invalid.",
        "백업 파일 형식이 올바르지 않습니다." to "The backup file format is invalid.",
        "복원에 실패했습니다." to "Restore failed.",
        "백업 파일은 20MB 이하여야 합니다." to "Backup files must be 20 MB or smaller.",
        "백업 실패" to "Backup failed",
        "복원 중 오류가 발생했습니다" to "An error occurred while restoring",
        "이전 평문 백업을 복원했습니다. 새 백업은 암호화됩니다." to "Legacy plaintext backup restored. New backups are encrypted.",
        "앱별 365일 기록, 점수 기록과 언락 통계가 삭제되고 추적이 중지됩니다. 앱 등급과 점수 설정은 유지되며 이 작업은 되돌릴 수 없습니다." to "The 365-day app history, scores and unlock statistics will be deleted and tracking will stop. App ratings and score settings remain. This cannot be undone.",
        "사용 기록을 삭제하고 추적을 중지했습니다." to "Usage history deleted and tracking stopped.",
        "기준 사용" to "Reference usage",
        "현재 설정 예상 점수" to "Estimated score with current settings",
        "건강 진단 기준이 아닌 초기 보정용 시나리오입니다. 학업·업무·콘텐츠 품질에 맞게 세부값을 조정하세요." to "This is an initial calibration scenario, not health guidance. Adjust it for your study, work and content context.",
        "오늘의 앱 사용 현황" to "Today's App Usage",
        "아직 집계된 앱 사용 기록이 없습니다." to "No app usage has been measured yet.",
        "점수 산출 상세 내역" to "Score Breakdown",
        "방해 앱 사용 감점" to "Managed-app deduction",
        "생산성 앱 보너스" to "Growth-app allowance",
        "화면 휴식(Idle) 회복 보너스" to "Screen-break recovery",
        "심야(00~05시) 추가 가속 감점" to "Late-night acceleration (00–05)",
        "언락 목표 초과 감점" to "Unlock-target deduction",
        "기본 시작 점수" to "Starting score",
        "전체 통계 리포트 보기" to "View Full Report",
        "오늘의 화면 사용 시간" to "Today's Screen Time",
        "카테고리별 시간 분배" to "Time by Category",
        "앱별 사용 시간 (탭하여 설정 변경)" to "Usage by App (tap to edit)",
        "주간/월간 추세 보기" to "View Weekly / Monthly Trends",
        "오늘의 잠금 해제(언락) 통계" to "Today's Unlocks",
        "시간대별 언락" to "Unlocks by Hour",
        "알림과 언락 비교" to "Notifications vs Unlocks",
        "언락 관리 가이드" to "Unlock Guidance",
        "목표 언락 횟수 설정" to "Set Unlock Target",
        "방해 앱 집중 분석" to "Managed App Analysis",
        "지정된 방해 앱 목록입니다. 앱을 탭하여 카테고리를 변경할 수 있습니다." to "These apps are currently managed. Tap an app to change its rating.",
        "24시간 앱 사용량 그래프" to "24-hour app usage chart",
        "24시간 언락 횟수 그래프" to "24-hour unlock count chart",
        "관리 대상 앱별 사용시간 그래프" to "Usage chart for managed apps",
        "방해 생산성 중립 앱 사용시간 비교 그래프" to
            "Usage chart comparing managed, growth and neutral apps",
        "앱 분류 목록 관리" to "Manage App Ratings",
        "오늘의 전체 앱 사용 목록" to "All Apps Used Today",
        "균형 등급 변경" to "Change Balance Rating",
        "오늘 총 사용 시간" to "Total Today",
        "등급은 우측 상단 드롭다운에서 변경할 수 있습니다." to "Change the rating from the dropdown in the top-right corner.",
        "언제 많이 사용했나요?" to "When did you use it most?",
        "한 번에 너무 길게 사용했나요?" to "Were sessions too long?",
        "최근 앱 사용 세션 길이 그래프" to "Recent Session Length Chart",
        "최근 세션" to "Recent Sessions",
        "30분 이상은 주황색" to "30+ minutes shown in orange",
        "장기 사용 추세" to "Long-term Usage Trend",
        "요일별 평균 앱 사용량 그래프" to "Average Usage by Weekday",
        "기록이 더 쌓이면 같은 길이의 이전 기간과 비교할 수 있습니다." to "Once more data is recorded, it can be compared with the preceding period.",
        "최근 사용량 변화가 없습니다." to "There is no recent usage change.",
        "추세 기간 선택" to "Select Trend Period",
        "앱별 균형 등급" to "App Balance Ratings",
        "앱 이름 또는 패키지 검색" to "Search app name or package",
        "이 앱이 디지털 균형에 미치는 정도를 선택해 주세요." to "Choose how this app affects your digital balance.",
        "백그라운드 추적" to "Background Tracking",
        "백그라운드 추적 및 표시" to "Background Tracking & Display",
        "사용 기록 추적 중지됨" to "Usage tracking is off",
        "사용 기록 추적 중" to "Usage tracking is active",
        "끄면 백그라운드 서비스와 상태바 점수 알림이 즉시 종료됩니다." to "Turning this off stops background tracking and the status notification immediately.",
        "상태바 아이콘 스타일" to "Status Bar Icon Style",
        "점수 비율형" to "Score Proportion",
        "단계 단색형" to "Single-color Tier",
        "숫자 분리형" to "Separated Number",
        "빨간 원호 위를 현재 점수만큼 녹색이 채웁니다. 중앙 막대는 점수 구간색으로 바뀝니다." to
            "Green fills the red arc in proportion to the current score. The center stem follows the score tier color.",
        "전원 버튼 전체가 점수 구간에 따라 빨강·주황·노랑·초록으로 바뀝니다." to
            "The entire power symbol changes to red, orange, yellow, or green for the current score tier.",
        "작은 전원 버튼 옆에 외곽선을 넣은 큰 점수를 분리해 표시합니다." to
            "Shows a large outlined score beside a small power symbol.",
        "점수 구간: 0–39 빨강 · 40–59 주황 · 60–79 노랑 · 80–100 초록" to
            "Score tiers: 0–39 red · 40–59 orange · 60–79 yellow · 80–100 green",
        "개인 목표 방어선" to "Personal Score Target",
        "세부 가중치 커스텀 설정" to "Custom Score Weights",
        "장시간 사용 로그(Log) 가속" to "Long-session Acceleration",
        "데이터 관리 및 백업" to "Data & Backup",
        "기록 보존 방식" to "Retention Policy",
        "잠금 화면에서 상세 정보 숨기기" to "Hide Details on Lock Screen",
        "모든 사용 기록 즉시 삭제" to "Delete All Usage History",
        "디지털 사용 습관 점수 관리" to "Digital usage pattern scoring",
        "개인정보 처리 안내" to "Privacy Notice",
        "암호화 백업 만들기" to "Create Encrypted Backup",
        "암호화 백업 복원" to "Restore Encrypted Backup",
        "모든 사용 기록을 삭제할까요?" to "Delete all usage history?",
        "비밀번호는 백업에 저장되지 않으며 분실하면 복원할 수 없습니다." to "The password is not stored in the backup and cannot be recovered if lost.",
        "비밀번호 확인" to "Confirm Password",
        "비밀번호 · 8자 이상" to "Password · 8+ characters",
        "최근 7일" to "Last 7 Days",
        "최근 30일" to "Last 30 Days",
        "최근 30일 중 DigitsCore가 실제 저장한" to "DigitsCore recorded",
        "일을 표시합니다." to "days in the last 30 days.",
        "기록 범위:" to "Recorded range:",
        "장기 일별 저장 기능이 적용된 날부터 하루씩 누적됩니다." to
            "Daily history accumulates one day at a time from the date long-term storage became available.",
        "최근 4주" to "Last 4 Weeks",
        "최근 12주" to "Last 12 Weeks",
        "최근 6개월" to "Last 6 Months",
        "최근 1년" to "Last Year",
        "일별 점수 추세" to "Daily Score Trend",
        "사용 시간 & 언락 횟수" to "Screen Time & Unlocks",
        "총 화면시간" to "Screen Time",
        "평균 점수" to "Average Score",
        "목표 달성률" to "Target Success",
        "일평균 화면 시간" to "Daily Screen Average",
        "일평균 언락" to "Daily Unlock Average",
        "기간 내 평균" to "Period Average",
        "하루 평균 사용량" to "Average Daily Usage",
        "하루 폰 켠 횟수" to "Average Daily Unlocks",
        "기록된 사용량 데이터가 없습니다." to "No recorded usage data.",
        "기록된 이전 히스토리가 없습니다." to "No previous history has been recorded.",
        "오늘부터 점수가 기록됩니다." to "Scores will be recorded from today.",
        "뒤로가기" to "Back",
        "통계 리포트" to "Statistics",
        "앱 가중치 설정" to "App Ratings",
        "모드 설정" to "Settings",
        "현재 프리셋" to "Current Preset",
        "기본값 초기화" to "Reset Defaults",
        "선택됨" to "Selected",
        "화면" to "Screen",
        "언락" to "Unlocks",
        "방해" to "Managed",
        "관리" to "Managed",
        "성장" to "Growth",
        "균형" to "Balanced",
        "알림" to "Notifications",
        "검색" to "Search",
        "적용 및 저장" to "Apply & Save",
        "데이터 복원" to "Restore Data",
        "암호화 백업" to "Encrypted Backup",
        "앱 정보" to "App Information",
        "저장" to "Save",
        "취소" to "Cancel",
        "삭제" to "Delete",
        "닫기" to "Close",
        "확인" to "OK",
        "설정" to "Settings",
        "월" to "Mon", "화" to "Tue", "수" to "Wed", "목" to "Thu",
        "금" to "Fri", "토" to "Sat", "일" to "Sun"
    ).filter { it.first.length >= 4 }.sortedByDescending { it.first.length }

    private val exactReplacements = mapOf(
        "뒤로가기" to "Back", "통계 리포트" to "Statistics", "앱 가중치 설정" to "App Ratings",
        "모드 설정" to "Settings", "현재 프리셋" to "Current Preset", "기본값 초기화" to "Reset Defaults",
        "선택됨" to "Selected", "화면" to "Screen", "언락" to "Unlocks", "방해" to "Managed",
        "관리" to "Managed", "성장" to "Growth", "균형" to "Balanced", "알림" to "Notifications",
        "검색" to "Search", "적용 및 저장" to "Apply & Save", "데이터 복원" to "Restore Data",
        "암호화 백업" to "Encrypted Backup", "앱 정보" to "App Information", "저장" to "Save",
        "취소" to "Cancel", "삭제" to "Delete", "닫기" to "Close", "확인" to "OK",
        "설정" to "Settings", "월" to "Mon", "화" to "Tue", "수" to "Wed", "목" to "Thu",
        "금" to "Fri", "토" to "Sat", "일" to "Sun"
    )

    fun translate(source: String): String {
        if (Locale.getDefault().language != "en") return source
        exactReplacements[source]?.let { return it }
        var result = source
        dynamicEnglishReplacements.forEach { (pattern, replacement) ->
            result = result.replace(pattern, replacement)
        }
        replacements.forEach { (ko, en) -> result = result.replace(ko, en) }
        result = result
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)시간"), "$1 hr")
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)분"), "$1 min")
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)초"), "$1 sec")
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)회"), "$1 times")
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)점"), "$1 pts")
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)건"), "$1")
            .replace(Regex("(-?\\d+(?:\\.\\d+)?)배"), "$1×")
            .replace(Regex("(\\d+)일"), "$1 days")
            .replace(Regex("(\\d+)시"), "$1:00")
            .replace("오늘", "Today")
            .replace("심야", "Late night")
            .replace("최근", "Recent")
            .replace("화면", "Screen")
            .replace("방해", "Managed")
            .replace("생산성", "Growth")
            .replace("중립", "Neutral")
            .replace("언락", "Unlocks")
            .replace("잠금 해제", "unlocks")
            .replace("사용량", "usage")
            .replace("사용 시간", "Usage Time")
            .replace("사용시간", "Usage")
            .replace("평균", "Average")
            .replace("최장", "Longest")
            .replace("비교", "comparison")
            .replace("보정", "calibration")
            .replace("목표", "target")
            .replace("미만", "below")
            .replace("측정", "measured")
            .replace("입니다.", ".")
            .replace("총 ", "Total ")
            .replace("월요일", "Monday")
            .replace("화요일", "Tuesday")
            .replace("수요일", "Wednesday")
            .replace("목요일", "Thursday")
            .replace("금요일", "Friday")
            .replace("토요일", "Saturday")
            .replace("일요일", "Sunday")
            .replace("방어선:", "Target line:")
        return result
    }
}
