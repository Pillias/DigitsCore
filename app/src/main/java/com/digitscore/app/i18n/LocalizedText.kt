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
        Regex("(\\d+)분부터") to "From \$1 min",
        Regex("(\\d+)회 초과 시 감점") to "Load above \$1 unlocks",
        Regex("(.+) 기본값으로 복원") to "Restore \$1 defaults",
        Regex("(.+) · 24시간 (\\d+)회") to "\$1 · \$2 opens in 24h",
        Regex("24시간 (\\d+)회 · 1분 미만 (\\d+)회") to
            "\$1 opens in 24h · \$2 under one minute",
        Regex("최근 24시간 (\\d+)회 열었고, 그중 1분 미만은 (\\d+)회입니다\\.") to
            "Opened \$1 times in the last 24 hours; \$2 were under one minute.",
        Regex("최근 24시간 (\\d+)회 · 1분 미만 (\\d+)회") to
            "Last 24 hours: \$1 opens · \$2 under one minute",
        Regex("가장 많이 사용한 구간은 (.+) · (.+)입니다\\.") to
            "Highest-usage interval: \$1 · \$2.",
        Regex("가장 잦은 구간은 (.+) · (\\d+)회입니다\\.") to
            "Most frequent interval: \$1 · \$2 unlocks.",
        Regex("(.+) 기준 · (\\d+)분 동안 (\\d+)점 하락") to
            "\$1 baseline · down \$3 pts within \$2 min",
        Regex("또는 같은 구간에서 화면 (\\d+)분, 연속 사용 (\\d+)분에 도달하면 알려줍니다\\.") to
            "Also alerts at \$1 min of screen use in the window or \$2 min of continuous use.",
        Regex("완료 (\\d+)/16 · 검증 준비 (\\d+) · 관리자 입력 (\\d+) · 외부 검증 (\\d+)") to
            "Complete \$1/16 · Ready to validate \$2 · Owner action \$3 · External validation \$4",
        Regex("오늘 (\\d+)회 · 1분 미만 (\\d+)회") to
            "\$1 opens today · \$2 under one minute",
        Regex("짧은 확인이 전체 실행의 (\\d+)%입니다\\. 습관적으로 여는 흐름인지 살펴보세요\\.") to
            "Brief checks are \$1% of opens. See whether this is a habitual pattern.",
        Regex("(.+) · 오늘 (\\d+)회") to "\$1 · \$2 opens today",
        Regex("오늘 (\\d+)회 열었고, 그중 1분 미만은 (\\d+)회입니다\\.") to
            "Opened \$1 times today; \$2 were under one minute.",
        Regex("(.+) 사용이 최근 부하의 가장 큰 원인입니다\\.") to
            "\$1 usage is the largest recent load.",
        Regex("직전 기록보다 (\\d+)점 올랐습니다\\.") to "Up \$1 points from the previous reading.",
        Regex("직전 기록보다 (\\d+)점 낮아졌습니다\\.") to "Down \$1 points from the previous reading.",
        Regex("지금 화면을 쉬면 약 (\\d+)분 뒤 (\\d+)점에 도달할 것으로 예상됩니다\\.") to
            "If you rest from the screen now, the index is estimated to reach \$2 in about \$1 min.",
        Regex("화면 (.+) · 앱 (\\d+)회 실행 · 1분 미만 (\\d+)회") to
            "Screen \$1 · \$2 app opens · \$3 under one minute",
        Regex("최근 기록 평균은 (\\d+)점입니다\\. 이전 비교 기간을 준비하고 있습니다\\.") to
            "Your recent average is \$1. Preparing the previous comparison period.",
        Regex("최근 7일 평균 (\\d+)점 · 이전 7일 대비 (-?\\d+)점") to
            "Recent 7-day average \$1 · \$2 vs previous 7 days",
        Regex("(증분 수집|상태 복원) · 전면 앱 포착률 (\\d+%|—)") to
            "\$1 · Foreground coverage \$2",
        Regex("전면 앱 포착률 (\\d+)%") to "Foreground coverage \$1%",
        Regex("최근 조회 (\\d+)초 · 이벤트 (\\d+)개 · (\\d+)ms") to
            "Last query \$1 sec · \$2 events · \$3 ms",
        Regex("오늘 (\\d+)회 측정 · 조회 (\\d+)ms · CPU (\\d+)ms") to
            "\$1 cycles today · queries \$2 ms · CPU \$3 ms",
        Regex("실제 이벤트 기준 최근 24시간 언락 (\\d+)회") to
            "\$1 unlocks in the last 24 hours from recorded events",
        Regex("(\\d+)개 앱 · 항목을 누르면 등급 설명과 변경 옵션을 볼 수 있습니다\\.") to
            "\$1 apps · Tap an item to view its rating details and options.",
        Regex("(\\d+)일 기록") to "\$1 days recorded",
        Regex("평균 (\\d+)점") to "\$1 pts average",
        Regex("기간 중 최고 (\\d+)점, 최저 (\\d+)점입니다\\.") to
            "Highest \$1 pts and lowest \$2 pts in this period.",
        Regex("현재 (\\d+)점 · 최저 (\\d+) · 최고 (\\d+)") to
            "Current \$1 pts · Low \$2 · High \$3",
        Regex("최저 (\\d+|—) · 평균 (\\d+|—) · 최고 (\\d+|—)") to
            "Low \$1 · Average \$2 · High \$3",
        Regex("24H 범위 최저 (\\d+|—) · 최고 (\\d+|—)") to
            "24H range: low \$1 · high \$2",
        Regex("4주 범위 최저 (\\d+|—) · 최고 (\\d+|—)") to
            "4-week range: low \$1 · high \$2",
        Regex("(\\d{4}-\\d{2}-\\d{2}) · 시작 (\\d+) · 마지막 (\\d+) · 최저 (\\d+) · 최고 (\\d+)") to
            "\$1 · Start \$2 · Last \$3 · Low \$4 · High \$5",
        Regex("화면 (.+) · 관리 (.+) · 언락 (\\d+)회( · .+)?") to
            "Screen \$1 · Managed \$2 · \$3 unlocks\$4",
        Regex("최저 (\\d+)점, 최고 (\\d+)점이며 (\\d+)개 구간을 표시합니다\\.") to
            "Low \$1 pts, high \$2 pts, across \$3 recorded intervals.",
        Regex("화면 (.+) · 언락 (\\d+)회") to "Screen \$1 · \$2 unlocks",
        Regex("(\\d+)회 열음") to "\$1 unlocks",
        Regex("같은 기간 OS 감지 알림은 (\\d+)건입니다\\.") to
            "The OS detected \$1 notifications in the same period.",
        Regex("최근 24시간 (.+) 사용했습니다\\.") to "Used for \$1 during the last 24 hours.",
        Regex("(.+) 앱의 가장 긴 전면 사용 세션입니다\\.") to
            "The longest foreground session was in \$1.",
        Regex("월요일 평균 (.+)") to "Monday average: \$1",
        Regex("화요일 평균 (.+)") to "Tuesday average: \$1",
        Regex("수요일 평균 (.+)") to "Wednesday average: \$1",
        Regex("목요일 평균 (.+)") to "Thursday average: \$1",
        Regex("금요일 평균 (.+)") to "Friday average: \$1",
        Regex("토요일 평균 (.+)") to "Saturday average: \$1",
        Regex("일요일 평균 (.+)") to "Sunday average: \$1",
        Regex("이전 7일 (.+) · 최근 7일 (.+)") to
            "Previous 7 days \$1 · Recent 7 days \$2",
        Regex("최근 7일 하루 평균은 이전 7일보다 (\\d+)분 늘었고, 요일별 평균도 함께 비교합니다\\.") to
            "The recent 7-day daily average is \$1 min higher than the previous 7 days; weekday averages are also compared.",
        Regex("최근 7일 하루 평균은 이전 7일보다 (\\d+)분 줄었고, 요일별 평균도 함께 비교합니다\\.") to
            "The recent 7-day daily average is \$1 min lower than the previous 7 days; weekday averages are also compared.",
        Regex("최근 7일 하루 평균은 이전 7일보다 0분 같고, 요일별 평균도 함께 비교합니다\\.") to
            "The recent and previous 7-day daily averages are equal; weekday averages are also compared.",
        Regex("최근 7일의 하루 평균 화면시간이 이전 7일보다 (\\d+)분 늘었습니다\\.") to
            "Recent daily screen time is \$1 min higher than the previous seven days.",
        Regex("최근 7일의 하루 평균 화면시간이 이전 7일보다 (\\d+)분 줄었습니다\\.") to
            "Recent daily screen time is \$1 min lower than the previous seven days.",
        Regex("최근 7일의 하루 평균 화면시간이 이전 7일보다 0분 같습니다\\.") to
            "Recent and previous seven-day daily screen time are equal.",
        Regex("이전 7일보다 하루 평균 (.+) 증가") to
            "Daily average increased by \$1 vs the previous seven days",
        Regex("이전 7일보다 하루 평균 (.+) 감소") to
            "Daily average decreased by \$1 vs the previous seven days",
        Regex("최다 월 · (.+)") to "Highest Mon · \$1",
        Regex("최다 화 · (.+)") to "Highest Tue · \$1",
        Regex("최다 수 · (.+)") to "Highest Wed · \$1",
        Regex("최다 목 · (.+)") to "Highest Thu · \$1",
        Regex("최다 금 · (.+)") to "Highest Fri · \$1",
        Regex("최다 토 · (.+)") to "Highest Sat · \$1",
        Regex("최다 일 · (.+)") to "Highest Sun · \$1",
        Regex("최저 (\\d+)점 · 최고 (\\d+)점 · 마지막 (\\d+)점") to
            "Low \$1 pts · High \$2 pts · Last \$3 pts",
        Regex("점선은 설정한 기준선 (\\d+)점을 나타냅니다\\.") to
            "The dotted line marks your \$1-point target.",
        Regex("하루 평균 화면 (\\d+)분, 관리 앱 (\\d+)분, 언락 (\\d+)회입니다\\.") to
            "Daily average: \$1 min screen time, \$2 min managed apps, and \$3 unlocks.",
        Regex("화면 평균 (.+) · 언락 평균 (\\d+)회") to
            "Screen average \$1 · Unlock average \$2",
        Regex("같은 기간 코어 지수 범위는 (\\d+)~(\\d+)점입니다\\.") to
            "The Core Index ranged from \$1 to \$2 in the same period.",
        Regex("전체 화면시간 (.+) 중 관리 앱을 (.+) 사용했습니다\\.") to
            "Managed apps accounted for \$2 of \$1 total screen time.",
        Regex("기록된 (\\d+)일 중 (\\d+)일이 기준선 (\\d+)점 이상이었습니다\\.") to
            "\$2 of \$1 recorded days were at or above the \$3-point target.",
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
        Regex("🌙 심야 사용 (-?\\d+(?:\\.\\d+)?)분") to "🌙 Late-night use: \$1 min",
        Regex("심야 사용 (-?\\d+(?:\\.\\d+)?)분") to "Late-night use: \$1 min"
    )

    private val replacements = listOf(
        "일상 균형" to "Everyday Balance",
        "집중 유지" to "Sustained Focus",
        "화면 휴식" to "Screen Rest",
        "심야 균형" to "Night Balance",
        "가족 보호" to "Family Guard",
        "지금의 한 가지 제안" to "One suggestion now",
        "오늘의 사용 흐름" to "Today's Usage Pattern",
        "지수가 움직인 이유" to "Why the index moved",
        "회복 예상" to "Recovery Estimate",
        "오늘 요약" to "Today Summary",
        "나의 최근 기준" to "My Recent Baseline",
        "사용 흐름을 학습하고 있습니다." to "Learning your usage pattern.",
        "연속 사용이 현재 지수 변화의 가장 큰 원인입니다." to "Continuous use is the biggest driver of the current change.",
        "잦은 화면 확인이 최근 부하에 반영됐습니다." to "Frequent screen checks are reflected in the recent load.",
        "화면을 내려놓은 뒤 회복이 진행 중입니다." to "The index is recovering while the screen is down.",
        "최근 사용 흐름은 안정적입니다." to "Your recent usage pattern is steady.",
        "직전 기록과 같은 점수지만 사용 흐름은 계속 갱신됩니다." to "The score is unchanged, but the usage pattern keeps updating.",
        "3시간 안의 뚜렷한 회복보다 최근 24시간 누적 사용을 먼저 줄이는 편이 좋습니다." to
            "Reducing the rolling 24-hour load will help more than expecting a clear recovery within three hours.",
        "기록이 쌓이면 자신의 지난 사용 흐름과 비교합니다." to "Once enough history is recorded, this compares against your own past pattern.",
        "지금의 흐름을 유지하고 다음 확인을 의식적으로 선택해보세요." to "Keep this pattern and choose the next check intentionally.",
        "지금 한 번, 10분 동안 화면을 내려놓아 보세요." to "Put the screen down for ten minutes now.",
        "알림을 잠시 두고 화면 없는 휴식을 시작해보세요." to "Leave notifications for a moment and take a screen-free break.",
        "다음 확인 두 번을 한 번으로 묶어보세요." to "Combine the next two checks into one.",
        "심야 사용을 마치고 화면 밝기를 내려놓을 시간입니다." to "Wind down late-night use and put the screen away.",
        "측정 상태" to "Measurement Status",
        "증분 수집" to "Incremental collection",
        "상태 복원" to "State restore",
        "측정 정확도와 처리 비용" to "Measurement Accuracy & Cost",
        "포착률 계산 중" to "Calculating coverage",
        "통계에서 확인" to "View in Statistics",
        "포착률은 화면 ON·잠금 해제 시간 중 전면 앱을 특정한 비율입니다. CPU 시간은 측정기의 처리 비용이며 배터리 비율과 같지 않습니다. 실제 배터리 영향은 Android 배터리 사용량과 장기 실기기 시험에서 함께 확인해야 합니다." to
            "Coverage is the share of screen-on, unlocked time assigned to a foreground app. CPU time is processing cost, not battery percentage. Verify real battery impact with Android battery usage and long-running device tests.",
        "사용 균형 통계 & 리포트" to "Usage Balance & Reports",
        "4주 사용 요약" to "4-Week Usage Summary",
        "코어 지수" to "Core Index",
        "코어 지수 세부 계산 조정" to "Core Index calculation settings",
        "연속 사용 가속, 심야 차등 가중치, 수면 중 회복 여부를 직접 조정합니다." to "Adjust continuous-use acceleration, night weights and overnight recovery.",
        "프리셋 기본값 사용" to "Use preset defaults",
        "연속 사용 가속 시작 시간" to "Continuous-use acceleration starts",
        "심야 1단계 (23~01시) 가중치" to "Night weight (23:00–01:00)",
        "심야 2단계 (01~05시) 가중치" to "Late-night weight (01:00–05:00)",
        "수면 중 회복 제한" to "Limit overnight recovery",
        "야간 휴식의 회복을 제한합니다. 아침의 반복 잠금 해제나 지속 사용으로 기상을 추정합니다." to "Limits overnight recovery. Repeated morning unlocks or sustained use provide evidence of waking.",
        "수면 판정 화면 미사용 시간" to "Minimum rest before estimating wake-up",
        "최근 24시간 언락 기준" to "Unlock threshold in the last 24 hours",
        "첫 기록 대비 상승" to "Up from first reading",
        "첫 기록 대비 하락" to "Down from first reading",
        "일별 마지막 지수 7일 평균" to "7-day average of daily last readings",
        "점선은 계산 방식 또는 프리셋 변경일입니다. 평균선은 같은 기준의 기록만 사용하며, 누락일은 제외합니다. 아래 빨간 막대는 관리 앱 사용시간입니다." to "Dashed lines mark model or preset changes. Averages use matching settings and exclude missing days. Red lower bars show managed-app time.",
        "7일 평균" to "7-Day Average",
        "하락/관리" to "Decline / Managed",
        "차트를 누르거나 드래그해 시점별 기록을 확인하세요. 화면을 끄고 쉰 구간은 다음 회복값까지 선으로 이어지며 사용량은 0으로 표시됩니다." to
            "Tap or drag the chart to inspect each point. Screen-off breaks connect to the next recovered value while usage remains zero.",
        "화면을 끄고 쉬는 동안 연속 사용 부하가 줄어 코어 지수가 회복됩니다. 차트는 다음 사용 시 계산된 회복값까지 흐름을 이어 표시합니다." to
            "Putting the screen down reduces continuous-use load and lets the Core Index recover. The chart connects the trend to the recovered value calculated at the next use.",
        "범위봉은 하루의 시작·마지막·최저·최고 코어 지수를, 아래 막대는 기록된 모든 날짜의 화면 사용을 표시합니다." to
            "Range bars show each day's opening, last, low, and high Core Index; the lower bars show screen use for every recorded day.",
        "날짜를 누르면 하루 중 5분 단위 변화를 확인할 수 있습니다." to
            "Tap a date to inspect its five-minute changes.",
        "최근 24시간 코어 지수" to "Core Index · Last 24 Hours",
        "현재 코어 지수 · 24H" to "Current Core Index · 24H",
        "현재 코어 지수 · 4W" to "Current Core Index · 4W",
        "24시간 내 첫 기록 대비" to "vs first reading in 24H",
        "표본 준비 중" to "Preparing Samples",
        "최근 24시간 코어 지수 표본을 준비하고 있습니다." to
            "Preparing Core Index samples for the last 24 hours.",
        "이 기기에서 저장된 최근 24시간 코어 지수 표본이 아직 없습니다." to
            "No Core Index samples from the last 24 hours are stored on this device yet.",
        "24시간 사용과 언락" to "24-Hour Usage & Unlocks",
        "현재 시각 직전 24시간의 전면 앱 사용과 잠금 해제 흐름입니다." to
            "Foreground app usage and unlock activity during the 24 hours immediately before viewing.",
        "청록색은 전체 전면 사용, 빨간색은 몰입 관리 앱의 전면·병렬 표시, 노란 점은 언락 횟수입니다." to
            "Teal is total foreground use, red is foreground or concurrent exposure to Immersion Management apps, and yellow dots are unlocks.",
        "24시간 사용 흐름" to "24-Hour Usage Flow",
        "24시간 핵심 정보" to "24-Hour Highlights",
        "가장 많이 사용" to "Most Used",
        "가장 많이 사용한 앱" to "Most Used App",
        "화면 ON·잠금 해제 상태에서 최상단이었던 시간만 포함합니다." to
            "Includes only time when the screen was on, unlocked, and the app was foremost.",
        "최장 연속 사용" to "Longest Session",
        "기록 없음" to "No records",
        "최근 24시간에 저장된 사용 세션이 없습니다." to
            "No usage sessions were stored in the last 24 hours.",
        "화면을 끄거나 다른 앱으로 전환하면 세션이 종료됩니다." to
            "A session ends when the screen turns off or another app takes the foreground.",
        "관리 앱 사용" to "Managed App Usage",
        "몰입 관리 앱" to "Immersion Management Apps",
        "최근 24시간 중 몰입 관리 앱이 전면 또는 병렬 화면에 표시된 시간입니다." to
            "Time an Immersion Management app was foreground or concurrently visible during the last 24 hours.",
        "앱 등급을 변경하면 이후 세션부터 새 등급으로 기록됩니다." to
            "A changed app rating applies to sessions recorded afterward.",
        "언락 간격" to "Unlock Interval",
        "24시간 언락 흐름" to "24-Hour Unlock Flow",
        "연속 언락 사이 평균 간격은" to "The average interval between unlocks is",
        "계산 전" to "not available yet",
        "같은 기간 OS 감지 알림은" to "OS-detected notifications in the same period:",
        "이 기기에서는 알림 이벤트 수를 제공하지 않을 수 있습니다." to
            "This device might not provide notification-event counts.",
        "4주 사용 패턴" to "4-Week Usage Patterns",
        "화면을 얼마나 오래 쓰는지 주간·요일별로 비교합니다." to
            "Compare how long you use the screen by week and weekday.",
        "주간 하루 평균" to "Daily Average by Week",
        "요일별 하루 평균" to "Daily Average by Weekday",
        "이전 7일과 하루 평균 사용시간이 같습니다." to
            "Daily average usage is unchanged from the previous seven days.",
        "비교할 주간 사용 기록을 준비하고 있습니다." to
            "Preparing weekly usage for comparison.",
        "막대는 사용 기록이 있는 날의 화면시간 평균입니다." to
            "Bars show average screen time for days with usage records.",
        "두 개의 7일 구간에 사용 기록이 있으면 주간 변화를 비교합니다." to
            "Weekly change appears when both seven-day periods contain usage records.",
        "주간 막대와 요일 막대는 사용 기록이 있는 날의 화면시간 평균입니다. 기록이 없는 날을 0분으로 채우지 않습니다." to
            "Weekly and weekday bars average days with usage records. Missing days are not filled with zero minutes.",
        "최근 7일의 변화는 4주 흐름 안에서 함께 비교합니다." to
            "Recent seven-day change is compared within the four-week trend.",
        "일별 코어 지수 추세" to "Daily Core Index Trend",
        "코어 지수 프리셋" to "Core Index Preset",
        "최근 24시간 사용 흐름에서 중요하게 볼 항목을 선택합니다." to
            "Choose what the rolling 24-hour Core Index should emphasize.",
        "프리셋 변경 즉시 최근 24시간 기록을 새 기준으로 다시 계산합니다." to
            "Changing the preset immediately recalculates the last 24 hours with the new profile.",
        "큰 테두리는 프리셋이 바뀐 날입니다." to
            "A large ring marks the day when the preset changed.",
        "프리셋 변경:" to "Preset changes:",
        "하루 코어 지수" to "Core Index During the Day",
        "이 날짜에는 하루 중 변화 기록이 없습니다. 세부 변화는 화면을 사용하는 동안 5분 단위로 저장됩니다." to
            "No intraday changes are available for this date. Detailed changes are saved every five minutes while the screen is in use.",
        "화면을 사용하는 동안 5분 단위의 최신 값을 저장합니다. 화면을 끄고 쉬면 지수가 회복되고, 다음 사용 시 계산된 값까지 선으로 이어집니다." to
            "The latest value is saved every five minutes during use. Putting the screen down lets the index recover, and the chart connects to the value calculated at the next use.",
        "그래프의 날짜를 누르면 하루 중 변화를 볼 수 있습니다." to
            "Tap a date on the chart to view changes during that day.",
        "프리셋:" to "Preset:",
        "앱별 시작·종료 상세와 5분 단위 코어 지수 표본은 30일, 날짜별 집계는 365일 보관합니다." to
            "App start/end details and five-minute Core Index samples are retained for 30 days; daily summaries are retained for 365 days.",
        "코어 지수 기록을 준비하고 있습니다." to "Core Index history is being prepared.",
        "사용 흐름을 측정하면 날짜별 기록이 쌓입니다." to
            "Daily records accumulate as your usage pattern is measured.",
        "이 기간에 계산된 코어 지수가 아직 없습니다." to
            "No Core Index has been calculated in this period yet.",
        "기록 준비 중" to "Preparing History",
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
        "최근 24시간 언락 횟수는 코어 지수의 사용 부하에 완만하게 반영됩니다." to
            "Unlocks over the last 24 hours contribute gently to the Core Index load.",
        "일일 기준치를 초과하여" to "Above the daily target:",
        "점 감점 적용 중입니다." to "points currently applied.",
        "현재 기준치 이내로 안전하게 유지하고 있습니다." to "Currently within the target.",
        "현재 기준치 이내로 유지하고 있습니다." to "Currently within the target.",
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
        "관리 앱 1분당 감점치" to "Managed-app deduction per minute",
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
        "최근 24시간 앱 사용 현황" to "App Usage · Last 24 Hours",
        "현재 시각 직전 24시간의 시간대·세션·최근 추세입니다." to
            "Hourly use, sessions, and trends for the 24 hours immediately before now.",
        "앱을 누르면 시간대·세션·최근 추세를 볼 수 있습니다." to
            "Tap an app to view hourly use, sessions, and recent trends.",
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
        "최근 24시간 화면 사용" to "Screen Use · Last 24 Hours",
        "카테고리별 시간 분배" to "Time by Category",
        "앱별 사용 시간 (탭하여 설정 변경)" to "Usage by App (tap to edit)",
        "주간/월간 추세 보기" to "View Weekly / Monthly Trends",
        "오늘의 잠금 해제(언락) 통계" to "Today's Unlocks",
        "최근 24시간 잠금 해제" to "Unlocks · Last 24 Hours",
        "시간대별 언락" to "Unlocks by Hour",
        "알림과 언락 비교" to "Notifications vs Unlocks",
        "언락 관리 가이드" to "Unlock Guidance",
        "목표 언락 횟수 설정" to "Set Unlock Target",
        "방해 앱 집중 분석" to "Managed App Analysis",
        "관리 앱 상세 분석" to "Managed App Details",
        "관리 대상 앱 목록입니다. 앱을 눌러 등급을 변경할 수 있습니다." to
            "These apps are managed. Tap one to change its rating.",
        "지정된 방해 앱 목록입니다. 앱을 탭하여 카테고리를 변경할 수 있습니다." to "These apps are currently managed. Tap an app to change its rating.",
        "24시간 앱 사용량 그래프" to "24-hour app usage chart",
        "24시간 언락 횟수 그래프" to "24-hour unlock count chart",
        "관리 대상 앱별 사용시간 그래프" to "Usage chart for managed apps",
        "방해 생산성 중립 앱 사용시간 비교 그래프" to
            "Usage chart comparing managed, growth and neutral apps",
        "앱 분류 목록 관리" to "Manage App Ratings",
        "앱 등급 목록 관리" to "Manage App Ratings",
        "성장 앱" to "Growth Apps",
        "균형/기타" to "Balanced / Other",
        "오늘의 전체 앱 사용 목록" to "All Apps Used Today",
        "최근 24시간 전체 앱 목록" to "All Apps · Last 24 Hours",
        "균형 등급 변경" to "Change Balance Rating",
        "오늘 총 사용 시간" to "Total Today",
        "최근 24시간 사용" to "Last 24 Hours",
        "등급은 우측 상단 드롭다운에서 변경할 수 있습니다." to "Change the rating from the dropdown in the top-right corner.",
        "언제 많이 사용했나요?" to "When did you use it most?",
        "최근 24시간 언제 많이 사용했나요?" to "When did you use it most in the last 24 hours?",
        "한 번에 너무 길게 사용했나요?" to "Were sessions too long?",
        "하루에 몇 번 열었나요?" to "How often did you open it?",
        "날짜별로 몇 번 열었나요?" to "How often did you open it each day?",
        "최근 24시간 사용 흐름" to "Usage Flow · Last 24 Hours",
        "최근 24시간 요약" to "Last 24 Hours Summary",
        "최근 24시간 사용된 관리 대상 앱이 없습니다. 안정적인 사용 흐름입니다." to
            "No managed apps were used in the last 24 hours. Your usage pattern is steady.",
        "아직 앱 실행 기록이 없습니다." to "No app-open records yet.",
        "최근 14일 앱 실행 횟수와 1분 미만 실행 그래프" to
            "Chart of app opens and under-one-minute opens over the last 14 days",
        "전체 실행" to "All opens",
        "주황색 · 1분 미만" to "Orange · under one minute",
        "최근 앱 사용 세션 길이 그래프" to "Recent Session Length Chart",
        "최근 세션" to "Recent Sessions",
        "30분 이상은 주황색" to "30+ minutes shown in orange",
        "장기 사용 추세" to "Long-term Usage Trend",
        "이 장기 그래프만 요일 비교를 위해 달력 날짜 단위로 집계합니다." to
            "Only this long-term chart uses calendar days for weekday comparisons.",
        "요일별 평균 앱 사용량 그래프" to "Average Usage by Weekday",
        "기록이 더 쌓이면 같은 길이의 이전 기간과 비교할 수 있습니다." to "Once more data is recorded, it can be compared with the preceding period.",
        "최근 사용량 변화가 없습니다." to "There is no recent usage change.",
        "추세 기간 선택" to "Select Trend Period",
        "앱별 균형 등급" to "App Balance Ratings",
        "앱 이름 또는 패키지 검색" to "Search app name or package",
        "검색 결과가 없습니다." to "No matching apps.",
        "이 앱이 디지털 균형에 미치는 정도를 선택해 주세요." to "Choose how this app affects your digital balance.",
        "백그라운드 추적" to "Background Tracking",
        "백그라운드 추적 및 표시" to "Background Tracking & Display",
        "생활 패턴에 맞는 기본값을 선택하고 아래에서 세부 조정합니다." to
            "Choose a baseline for your routine, then fine-tune it below.",
        "상태바와 위젯의 표시 방식, 실시간 추적 여부를 관리합니다." to
            "Manage status-bar and widget styles and real-time tracking.",
        "위젯 배경" to "Widget Background",
        "어두운 배경" to "Dark Background",
        "흰색 배경" to "White Background",
        "투명 배경" to "Transparent Background",
        "어두운 카드와 밝은 글자를 사용합니다." to "Uses a dark card with light text.",
        "흰색 카드와 어두운 글자를 사용합니다." to "Uses a white card with dark text.",
        "전체 배경은 투명하게 하고 정보 영역만 읽기 쉽게 표시합니다." to
            "Keeps the overall background transparent and adds contrast only behind details.",
        "사용 기록 추적 중지됨" to "Usage tracking is off",
        "사용 기록 추적 중" to "Usage tracking is active",
        "끄면 백그라운드 서비스와 상태바 점수 알림이 즉시 종료됩니다." to "Turning this off stops background tracking and the status notification immediately.",
        "상태바 아이콘 스타일" to "Status Bar Icon Style",
        "코어 숫자형 (권장)" to "Core Number (Recommended)",
        "점수 비율형" to "Score Proportion",
        "전원 단계형" to "Power Tier",
        "단계 단색형" to "Single-color Tier",
        "숫자 분리형" to "Separated Number",
        "큰 점수 크기는 유지하고, 점수 구간에 맞는 고대비 단색으로 숫자와 전원 원호를 표시합니다." to
            "Keeps the large score and uses one high-contrast tier color for both the number and power arc.",
        "빨간 원호 위를 현재 점수만큼 녹색이 채웁니다. 중앙 막대는 점수 구간색으로 바뀝니다." to
            "Green fills the red arc in proportion to the current score. The center stem follows the score tier color.",
        "전원 버튼 전체가 점수 구간에 따라 빨강·주황·노랑·초록으로 바뀝니다." to
            "The entire power symbol changes to red, orange, yellow, or green for the current score tier.",
        "작은 전원 버튼 옆에 외곽선을 넣은 큰 점수를 분리해 표시합니다." to
            "Shows a large outlined score beside a small power symbol.",
        "점수 구간: 0–39 빨강 · 40–59 주황 · 60–79 노랑 · 80–100 초록" to
            "Score tiers: 0–39 red · 40–59 orange · 60–79 yellow · 80–100 green",
        "개인 목표 방어선" to "Personal Score Target",
        "개인 목표 기준선" to "Personal Targets",
        "점수 안내와 언락 기준을 본인의 생활 패턴에 맞춥니다." to
            "Adapt score guidance and unlock targets to your routine.",
        "세부 가중치 커스텀 설정" to "Custom Score Weights",
        "세부 가중치 설정" to "Score Weights",
        "심야 사용, 장시간 사용, 회복과 언락의 반영 강도를 조정합니다." to
            "Adjust how late-night use, long sessions, recovery, and unlocks affect the score.",
        "장시간 사용 로그(Log) 가속" to "Long-session Acceleration",
        "데이터 관리 및 백업" to "Data & Backup",
        "기록 보존, 암호화 백업, 복원과 삭제를 관리합니다." to
            "Manage retention, encrypted backups, restoration, and deletion.",
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
        "이전 7일" to "Previous 7 Days",
        "기록 범위:" to "Recorded range:",
        "최근 4주" to "Last 4 Weeks",
        "최근 12주" to "Last 12 Weeks",
        "최근 6개월" to "Last 6 Months",
        "최근 1년" to "Last Year",
        "일별 점수 추세" to "Daily Score Trend",
        "선택한 생활 유형의 참고 기준과 실제 기간 평균을 비교합니다." to
            "Compares the selected lifestyle benchmark with your period average.",
        "조사 평균은 건강 진단 기준이 아닙니다." to "Survey averages are not medical guidance.",
        "사용 시간과 언락" to "Screen Time & Unlocks",
        "청록색은 전체 화면시간, 빨간색은 관리 앱 시간, 노란 점은 언락 횟수입니다." to
            "Teal shows screen time, red shows managed-app time, and yellow dots show unlocks.",
        "사용 시간 & 언락 횟수" to "Screen Time & Unlocks",
        "총 화면시간" to "Screen Time",
        "평균 점수" to "Average Score",
        "목표 달성률" to "Target Success",
        "일평균 화면 시간" to "Daily Screen Average",
        "일평균 언락" to "Daily Unlock Average",
        "가장 많이 쓴 날" to "Highest-Usage Day",
        "하루 사용 최고" to "Highest Daily Usage",
        "관리 앱 비중" to "Managed App Share",
        "전체 화면시간 중" to "Of Total Screen Time",
        "최근 4주 기록에서 화면을 켜고 전면 앱을 가장 오래 사용한 날입니다." to
            "The day with the most screen-on foreground app use in the last four weeks.",
        "몰입 관리 앱이 전면 또는 병렬 화면에 표시된 사용시간 비율입니다." to
            "Share of usage time with an Immersion Management app in the foreground or concurrently visible.",
        "4주 차트에서 날짜별 화면시간, 관리 앱 시간과 언락 횟수를 함께 비교합니다." to
            "The four-week chart compares daily screen time, managed-app time, and unlocks.",
        "현재 지수는 최근 24시간 사용 흐름으로 계산하며, 4주 차트는 날짜별 변화를 보여줍니다." to
            "The current Index reflects the last 24 hours; the four-week chart shows its daily trend.",
        "기록이 없는 날짜는 평균에 포함하지 않습니다." to
            "Days without a record are excluded from the average.",
        "기준선은 설정에서 변경할 수 있습니다." to "You can change the target in Settings.",
        "기록된 날짜의 전체 전면 앱 사용시간 평균입니다." to
            "Average foreground app time across recorded days.",
        "화면 OFF 백그라운드 재생은 포함하지 않습니다." to
            "Screen-off background playback is excluded.",
        "기록된 날짜의 잠금 해제 횟수 평균입니다." to
            "Average unlock count across recorded days.",
        "짧은 앱 사용은 시간과 별도로 앱별 실행 횟수에 반영됩니다." to
            "Brief app use is represented separately in each app's open count.",
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
        "버전과 개인정보 처리 정책을 확인합니다." to "Review the version and privacy policy.",
        "디지털 사용 습관을 전면 앱 사용시간과 언락 기록으로 분석합니다." to
            "Analyzes digital habits using foreground app time and unlock history.",
        "개인정보 처리 방식은 아래 개인정보 처리 안내에서 확인할 수 있습니다." to
            "See the privacy notice below for details about data handling.",
        "저장" to "Save",
        "취소" to "Cancel",
        "삭제" to "Delete",
        "닫기" to "Close",
        "확인" to "OK",
        "설정" to "Settings",
        "상세 보기" to "View details",
        "전체 보기" to "View all",
        "기본값" to "Defaults",
        "관리 앱" to "Managed Apps",
        "월" to "Mon", "화" to "Tue", "수" to "Wed", "목" to "Thu",
        "금" to "Fri", "토" to "Sat", "일" to "Sun"
    ).filter { it.first.length >= 4 }.sortedByDescending { it.first.length }

    private val exactReplacements = mapOf(
        "뒤로가기" to "Back", "통계 리포트" to "Statistics", "앱 가중치 설정" to "App Ratings",
        "최근 24시간" to "Last 24 Hours", "최근 4주" to "Last 4 Weeks",
        "모드 설정" to "Settings", "현재 프리셋" to "Current Preset", "기본값 초기화" to "Reset Defaults",
        "선택됨" to "Selected", "화면" to "Screen", "언락" to "Unlocks", "방해" to "Managed",
        "관리" to "Managed", "성장" to "Growth", "균형" to "Balanced", "회복" to "Recovery", "알림" to "Notifications",
        "검색" to "Search", "적용 및 저장" to "Apply & Save", "데이터 복원" to "Restore Data",
        "암호화 백업" to "Encrypted Backup", "앱 정보" to "App Information", "저장" to "Save",
        "취소" to "Cancel", "삭제" to "Delete", "닫기" to "Close", "확인" to "OK",
        "설정" to "Settings", "상세 보기" to "View details", "전체 보기" to "View all",
        "기본값" to "Defaults", "관리 앱" to "Managed Apps",
        "월" to "Mon", "화" to "Tue", "수" to "Wed", "목" to "Thu",
        "금" to "Fri", "토" to "Sat", "일" to "Sun",
        "버그 리포트" to "Bug Report", "출시 체크리스트" to "Release Checklist",
        "출시 준비 체크리스트" to "Release Readiness Checklist",
        "문제가 생긴 상황과 재현 순서를 적어주세요. 아래 보고서를 확인한 뒤 직접 공유합니다." to
            "Describe what happened and how to reproduce it. Review the report below before sharing.",
        "문제 상황과 재현 순서" to "What happened and reproduction steps",
        "예: 위젯이 오전 10시 이후 갱신되지 않음" to "Example: The widget stopped updating after 10 AM",
        "첨부되는 진단 정보" to "Included diagnostics",
        "앱 버전·기기 모델·Android 버전·권한 상태·측정 성능만 포함합니다. 앱 목록, 사용 이력, 점수 기록, 알림 내용, 계정 및 기기 식별자는 포함하지 않습니다." to
            "Includes only the app version, device model, Android version, permission state, and measurement performance. It excludes app lists, usage and score history, notification content, accounts, and device identifiers.",
        "리포트 복사" to "Copy Report", "공유" to "Share", "버그 리포트 공유" to "Share Bug Report",
        "버그 리포트를 복사했습니다." to "Bug report copied.",
        "급격한 사용 증가 알림" to "Rapid Usage Alerts",
        "게임이나 화면을 방해하지 않는 1단계 알림의 조건을 정합니다." to
            "Set the conditions for gentle alerts that do not interrupt games or the current screen.",
        "부드러운 사용 경고" to "Gentle Usage Alert",
        "소리·진동·팝업 없이 알림창에만 표시합니다." to
            "Appears only in notifications, without sound, vibration, or a pop-up.",
        "프리셋 기본값 적용 중" to "Using preset defaults",
        "사용자 조정값 적용 중" to "Using custom values",
        "프리셋 기본값 복원" to "Restore Preset Defaults",
        "관찰 시간" to "Observation Window",
        "코어 지수 하락" to "Core Index Drop",
        "관찰 구간 내 화면 사용" to "Screen Use in Window",
        "한 번에 이어서 사용" to "Continuous Use",
        "알림 후 쉬는 시간" to "Alert Cooldown",
        "반드시 필요한 것" to "Required for Release", "상품성을 위해 필요한 것" to "Product Readiness",
        "완료" to "Complete", "검증 준비" to "Ready to Validate", "관리자 입력" to "Owner Action", "외부 검증" to "External Validation",
        "기기·사용자·Play Console이 필요한 항목은 코드만으로 완료 처리하지 않습니다. 저장소 docs/RELEASE_READINESS.md에 실행 절차와 증빙 위치를 정리했습니다." to
            "Items requiring devices, users, or Play Console are not marked complete from code alone. Execution steps and evidence locations are in docs/RELEASE_READINESS.md.",
        "최근 24시간 실제 언락 이벤트" to "Actual Unlock Events in the Last 24 Hours",
        "ACTION_USER_PRESENT와 KEYGUARD_HIDDEN 관측 이벤트만 집계하고 추정치는 사용하지 않습니다." to
            "Counts only observed ACTION_USER_PRESENT and KEYGUARD_HIDDEN events; no estimates are used.",
        "UsageEvents 증분 처리" to "Incremental UsageEvents Processing",
        "프로세스 시작 시 상태를 한 번 복원한 뒤 마지막 커서 이후 이벤트만 조회합니다." to
            "Restores state once at process start, then queries only events after the last cursor.",
        "Samsung·Pixel·Xiaomi 장기 측정" to "Long-term Samsung, Pixel, and Xiaomi Measurement",
        "기기별 2~4주 실측은 실제 기기와 측정 담당자가 필요합니다." to
            "Two to four weeks of measurement per device requires physical devices and testers.",
        "정확도와 배터리 수치화" to "Quantified Accuracy and Battery Use",
        "포착률·조회시간·CPU 진단은 준비됐습니다. 장기 기기 시험에서 시간 오차와 배터리 값을 채워야 합니다." to
            "Coverage, query time, and CPU diagnostics are ready. Long-running device tests must supply time-error and battery measurements.",
        "업로드 키와 서명 AAB" to "Upload Key and Signed AAB",
        "서명 자동화는 준비됐지만 업로드 키 생성·보관 및 GitHub Secrets 입력이 필요합니다." to
            "Signing automation is ready, but the upload key must be created, backed up, and added to GitHub Secrets.",
        "개인정보·지원·Data Safety" to "Privacy, Support, and Data Safety",
        "개인정보처리방침 초안과 Data Safety 답안은 준비됐습니다. 개발자명·지원 이메일·공개 URL 확정이 필요합니다." to
            "The privacy-policy draft and Data Safety answers are ready. The developer name, support email, and public URL must be finalized.",
        "FGS 설명과 시연 영상" to "FGS Description and Demo Video",
        "제출 설명과 촬영 순서는 준비됐습니다. 실기기 화면 녹화와 Play Console 제출이 남았습니다." to
            "The declaration text and shot list are ready. Device recording and Play Console submission remain.",
        "Android Vitals 감시" to "Android Vitals Monitoring",
        "내부 테스트 업로드 뒤 Play Console에서 Crash·ANR·wake lock을 실제 감시해야 합니다." to
            "Crash, ANR, and wake-lock metrics must be monitored in Play Console after an internal-test upload.",
        "접근성·화면 크기 시험" to "Accessibility and Screen-size Testing",
        "테마·반응형 기반은 준비됐습니다. 큰 글꼴·TalkBack·가로·태블릿 수동 매트릭스 시험이 남았습니다." to
            "Theme and responsive foundations are ready. Manual large-text, TalkBack, landscape, and tablet matrix testing remains.",
        "20~50명 30일 점수 검증" to "30-day Score Validation with 20–50 Users",
        "동의한 실제 사용자를 모집해 개인 식별 없는 집계 결과를 검증해야 합니다." to
            "Consenting users must be recruited to validate aggregated results without personal identifiers.",
        "점수 변화 이유 한 문장" to "One-sentence Reason for Score Changes",
        "현재 지수 변화의 가장 큰 원인을 대시보드에 표시합니다." to
            "Shows the largest driver of the current index change on the dashboard.",
        "예상 회복 시간" to "Estimated Recovery Time",
        "화면을 쉬었을 때 목표 지수까지의 예상 시간을 표시합니다." to
            "Shows the estimated time to reach the target index while resting from the screen.",
        "부드러운 임계치 알림" to "Gentle Threshold Alerts",
        "프리셋별 관찰 시간의 점수 하락·화면 사용·연속 사용을 감지하며 사용자가 기준과 재알림 간격을 조정할 수 있습니다." to
            "Detects score drops, screen use, and continuous use within a preset-specific window; thresholds and cooldown can be customized.",
        "하루·주간 핵심 요약" to "Daily and Weekly Highlights",
        "최근 24시간 사용과 최근 7일/이전 7일 비교를 제공합니다." to
            "Provides last-24-hour usage and a recent-versus-previous seven-day comparison.",
        "한 번에 한 가지 제안" to "One Action at a Time",
        "현재 흐름에서 실행할 한 가지 행동만 추천합니다." to
            "Recommends only one actionable step for the current pattern.",
        "사용자 자신의 과거와 비교" to "Comparison with Personal History",
        "고정 타인 평균 대신 사용자의 최근 기록을 기준선으로 사용합니다." to
            "Uses the user's recent history as the baseline instead of a fixed population average."
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
