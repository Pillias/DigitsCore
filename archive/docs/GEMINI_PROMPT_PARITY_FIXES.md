# Gemini 작업 지시: 스크린 타임 개선 10개 검토 후 수정 (feature/screen-time-parity)

아래 전체를 Gemini에게 붙여 넣으세요.

---

## 배경

`feature/screen-time-parity` 브랜치(마지막 커밋 `f50d060`)를 검토했다. 마이그레이션 19→20, 권한 추가 없음, 점수 엔진 무변경, `design_check` 0건, JVM 단위 테스트(156개) 통과는 확인했다. 아래 문제를 같은 브랜치에서 고친다. 기존 지시서(`docs/GEMINI_PROMPT_SCREEN_TIME_IMPROVEMENTS.md`)의 절대 규칙은 그대로 적용한다. 항목마다 커밋하고, 고친 로직에는 테스트를 추가한다.

## A. 동작이 틀린 것 (꼭 고칠 것)

**A1. 주간 리포트의 목표 달성 일수가 실제 목표와 무관하다**
- `WeeklyReportCalculator`가 `finalScore >= 70`을 "방어선 달성", `>= 80`을 "모두 지킨 날"로 센다. 사용자가 정한 목표와 관계없다.
- 수정: 하루 목표 결과를 날짜별로 남긴다. `DailyGoalStore`의 어제 정산(연속 달성·스탬프 처리하는 곳)에서 `{date, scoreMet, appMet, unlockMet, perfect, stampEarned, passEarned}`를 prefs JSON 배열에 추가(최근 35일 보관)하고, 리포트는 이 기록으로 목표별 달성 일수·모두 지킨 날·그 주에 받은 스탬프·자유권 수를 계산한다.
- 지금의 `perfectStampsEarned = rewardState.perfectStamps`, `freePassesEarned = availableFreePasses`는 "현재 보유량"이라 틀리다. 그 주에 받은 수로 바꾼다.
- 알림 문구 `weekly_report_notification_desc`의 "목표 달성 N일"도 이 값으로 바꾼다. 기록이 없는 주(업데이트 직후)는 목표 줄을 "기록 없음"으로 표시한다.

**A2. 주간 리포트의 지난주 대비 값이 비교 대상이 없어도 표시된다**
- 지지난주 기록이 0일이면 `screenTimeDiffMinutes`·`unlockDiffCount`·`shortSessionDiffCount`가 "평균 − 0"이 된다. 지지난주 기록 일수가 0이면 null로 두고 화면에서 숨긴다.
- 앱 증감(`topIncreasedApp`/`topDecreasedApp`)은 주 합계가 아니라 **기록된 날 기준 하루 평균**으로 비교한다. 한쪽 주의 기록 일수가 3일 미만이면 앱 변화 카드를 숨긴다(불러온 기록만 있는 주와 비교되는 것을 막음).
- 리포트 계산에서 `dailyAppUsageDao().getAll()` 대신 14일 범위 쿼리를 쓴다.

**A3. "15min 더"가 날짜를 넘겨 다음 날 울린다**
- `GoalLimitChoiceStore.setMore15Snooze`에 날짜가 없다. 사용자가 그 앱을 다시 열지 않으면 다음 날 앱을 처음 여는 순간 "15min 연장 한도 도달"과 신호등이 뜬다.
- 수정: 스누즈 키에 05:00 기준 날짜를 넣고, 날짜가 다르거나 만료 후 그 앱이 전면이 아니면 지운다.

**A4. "관리 앱 전체" 목표에서 선택 카드가 작동하지 않는다**
- 목표 앱이 `DailyGoal.PACKAGE_ALL_MANAGED`일 때 알림·리시버는 그 상수로 스누즈·"오늘은 여기까지"를 저장하는데, 서비스는 `activePkg`(실제 앱)로 조회한다. 그래서 둘 다 적용되지 않는다.
- 수정: 목표가 관리 앱 전체면 조회 키를 상수로 하고, 현재 앱이 관리 등급일 때 적용한다. 알림 제목의 앱 이름은 `strings.xml` 문자열(ko "관리 앱 전체" / en "All managed apps")로 넣는다. 지금은 `DailyGoalStore`가 "관리 앱 전체"를 한국어로 박아 넣어 영어 화면에도 한국어가 나온다.

**A5. 평일·주말 목표를 다른 요일 탭에서 저장하면 오늘 요일 쪽 값이 덮인다**
- 화요일에 "주말" 탭을 고쳐 저장하면, 확인 버튼이 주말 값을 저장한 뒤 `onSave` → `setCustomGoal`이 같은 값을 **평일** 값과 **오늘 목표**에 다시 저장한다.
- 수정: 편집 중인 탭이 오늘 요일 종류와 다르면 그 탭의 값만 저장하고 오늘 목표는 바꾸지 않는다. `setCustomGoal`은 오늘 요일 종류일 때만 평일/주말 값을 저장하게 한다. 테스트를 추가한다.

**A6. 잠금 해제 후 처음 연 앱의 "짧은 확인" 판정이 틀린다**
- `UnlockInteractionResolver.QUICK_CONFIRMATION_THRESHOLD_MILLIS = 60_000L`이다. 짧은 확인 기준은 **7초로 확정**한다(용어집, 화면 문구 "7초 이내", `UsageStatsHelper`의 7초 판정과 같음). 지금 `StatisticsAggregation.kt`의 `HABITUAL_CHECK_THRESHOLD_MILLIS`만 15초로 되어 있어 어긋난다.
- 수정: `HABITUAL_CHECK_THRESHOLD_MILLIS = 7_000L`로 바꾸고, 이것을 짧은 확인의 유일한 기준 상수로 쓴다. `UnlockInteractionResolver`, `UsageStatsHelper`의 `7_000L` 직접 값(약 423행 `thresholdMillis`, 약 580행 `glanceThresholdMillis`), `RollingUsageSummary`, `StatisticsAggregation`, `DashboardScreen`의 짧은 확인 집계가 모두 이 상수를 쓰게 한다. 잠금 해제 중복 합치기용 15초(`UsageStatsHelper` 약 383행 설명)는 다른 의미이므로 바꾸지 않는다.
- 이 상수는 코어 지수 계산에 쓰이지 않으므로 점수는 바뀌지 않는다(엔진 파일을 건드리지 않는지 확인). 이미 저장된 시간별 통계의 짧은 확인 수는 15초 기준으로 집계돼 있으므로 다시 계산하지 않고, 출시 노트에 "짧은 확인 기준을 7초로 통일해 이후 수치가 줄어들 수 있음"을 적는다.
- 테스트: 6초 구간은 짧은 확인, 8초·14초 구간은 아님을 `StatisticsAggregationTest`, `RollingUsageSummaryTest`, `UnlockInteractionResolverTest`에 추가한다. 기존 테스트 중 15초를 전제로 한 것은 7초 기준으로 고친다.
- 서비스는 1분마다 처리하므로, 아직 열려 있는 앱 구간이 짧게 잘린 채 "짧은 확인"으로 집계될 수 있다. 첫 앱의 구간이 끝났거나(다음 구간 시작·화면 꺼짐) 잠금 해제 후 기준 시간이 지난 경우에만 확정하고, 그 전이면 커서를 그 잠금 해제 앞에 둔다. 테스트: 진행 중인 구간은 처리되지 않고 다음 주기에 한 번만 세는지 확인한다.

**A7. 화면 시간 차이 계산이 화면을 그리는 도중 메인 스레드에서 실행된다**
- `ScreenTimeDetailDialog`의 `remember { ScreenTimeReconciliationCalculator.queryReconciliation(...) }`가 UI 스레드에서 `queryAndAggregateUsageStats`와 `queryIntentActivities`를 호출한다. `produceState` + `Dispatchers.IO`로 옮긴다.
- `queryAndAggregateUsageStats`는 하루 버킷 단위라 범위가 맞지 않거나 어제 값이 섞일 수 있다. OS 추정치는 `UsageStatsHelper`의 이벤트 기반 계산(런처 포함, 자정부터)으로 바꾼다.
- 00:00~05:00 사이에 열면 DigitsCore는 어제 05:00부터 세고 OS는 자정부터 세므로 차이 방향이 반대다. 이 경우 "어제 05:00~자정 사용분"을 별도 항목으로 보여 준다.
- 분할 화면 중복 항목은 지금 항상 0이다. `ForegroundUsageAggregator`의 중복 제거량을 넘기거나 항목을 지운다.
- "기타 차이"가 음수면 부호를 붙여 표시한다.

## B. 개인정보·데이터

**B1. 전체 기록 삭제가 새 저장소를 지우지 않는다**
- `PrivacyDataManager.deleteAllUsageHistory`에서 `GoalLimitChoiceStore`(앱 패키지·시각 기록)와 A1에서 만드는 목표 결과 기록도 지운다. 앱 기록 보관 기간 정리에도 포함한다.
- `GoalLimitChoiceStore`의 날짜별 키(`resend_count_날짜_앱`, `stop_today_날짜_앱`)가 계속 쌓인다. 오늘이 아닌 키는 기록 시점에 정리한다.

**B2. 백업 복원 시 interaction 테이블**
- 복원 시 기존 `app_daily_interactions`를 비우고 넣는지 확인한다(다른 테이블 복원 방식과 같게). 옛 백업(키 없음) 복원 테스트를 `DataBackupManagerSerializationTest`에 추가한다.

**B3. 지난 기록 불러오기가 업데이트 사용자에게도 자동 실행된다**
- `maybeImportOnFirstRun`이 서비스 시작마다 확인해 0.9.34에서 업데이트한 사용자도 한 번 실행된다. 지시서대로 "추적을 처음 켤 때"만 자동 실행하고(설치 후 첫 추적 시작 플래그), 기존 사용자는 설정의 버튼으로만 실행한다.
- 불러온 날은 `daily_usage_coverage.isComplete = true`라 실제 측정한 날과 구분되지 않는다. 구분값(예: prefs의 불러온 날짜 목록, 또는 coverage에 출처 열 추가는 마이그레이션이 필요하므로 prefs 권장)을 두고 통계의 "불러온 기록" 표시는 이 값을 기준으로 한다.

## C. 용어·화면

**C1. 통계 앱 순위 칩 이름이 용어집과 다르다**
- "해제 후 첫 실행" → "처음 연 앱", "알림 잠금" → "알림 잠금 해제"(용어집 표준어의 줄임). 영어는 "First opened" / "Notification unlocks". 130% 글자 크기에서 칩 5개가 잘리지 않는지 확인하고, 잘리면 가로 스크롤로 바꾼다.
- `scripts/design_check.sh`의 쓰지 않는 말 목록에 이번에 용어집에 추가한 금지어(주간 보고서, 첫 실행 앱, 알림 유발 잠금, 스케줄 규칙, 전체 관리 한도, 과거 기록, 이전 기록)를 넣는다.

**C2. 영어 화면에 한국어가 남는 곳**
- `StatisticsExplorer`의 앱 값 `"일 ${...}x"`(mode 3, 4)는 영어에서도 "일"이 나온다. `label("일 Nx", "N/day")` 형태로 바꾼다.
- `TimeRule` 기본 이름 `"업무"`(모델 기본값, `TimeRuleStore` 파싱 기본값)를 언어와 무관한 키(예: `preset_work`)로 저장하고 표시할 때 번역한다.
- A4의 "관리 앱 전체".
- `UiTranslatorTest`에 이번에 추가된 동적 문장(주간 리포트 알림, 한도 알림, 시간대 배지) 영어 확인 케이스를 넣는다.

**C3. 시간대 규칙**
- 설정의 빠른 추가 버튼 "+ 수면 준비 (5min)"는 22:00 고정이다. 편집 화면과 같게 학습된 취침 1시간 전(없으면 23:00)을 쓴다. 버튼 글자의 "+"는 `Icons.Rounded.Add` 아이콘으로 바꾼다.
- 규칙 시간이 기본 신호등 시간보다 길면(예: 기본 5min, 규칙 20min) 규칙이 오히려 늦게 켠다. `minOf(규칙, 기본)`을 쓴다.
- 시작과 끝이 같으면 24시간 규칙이 된다. 저장할 때 막고 안내한다.
- 대시보드 배지는 `remember`로 한 번만 계산해 시간이 지나도 바뀌지 않는다. 1분마다 다시 계산하거나 서비스 상태(`ScoreRepository`)에서 받는다.

**C4. 위젯**
- 지수 방어선은 하루 마지막 점수로 판정하므로 낮 동안 "미달"은 오해를 준다. 위젯에서는 "달성/미달" 대신 지금 점수가 방어선 위인지 아래인지만 색으로 보여 주고 글자는 숫자만 둔다.
- 상태 글자 크기 8은 글자 크기 표(`DigitsTextSize`: 차트 8.5, 그 밖 9 이상)보다 작다. 9 이상으로 맞춘다.

**C5. 문서**
- `docs/DESIGN_SYSTEM.md`의 "알림 채널 체계" 표 ID가 실제와 다르다. 실제 값: `digitscore_status_channel`, `digitscore_guidance_channel`, `digitscore_soft_guidance_v2`, `digitscore_wake_prompt_channel`, `digitscore_goal_channel`, `digitscore_weekly_report`.
- 주간 리포트 숫자 칸의 횟수는 용어집 단위 규칙대로 `12x`로 통일한다(문장 안에서만 "회").

## D. 검증 (지난번 보고에 빠진 것)

1. 0.9.32 또는 0.9.34 빌드를 먼저 설치해 기록·목표·점수를 만든 뒤 이 브랜치 빌드를 덮어 설치해서 기록이 유지되고 v20 마이그레이션이 통과하는지 확인하고 결과를 적는다.
2. 다음 화면을 밝은·어두운 테마, 글자 크기 130%, 영어로 캡처해 `scripts/dev/out/parity/`에 남긴다: 화면 시간 상세(차이 카드), 잠금 해제 상세, 통계 앱 순위 5칩, 주간 리포트, 목표 설정(평일·주말, 관리 앱 전체), 한도 알림, 설정 시간대 규칙, 4열 위젯.
3. 실기기(삼성)에서 확인할 것: 이벤트 12에 패키지 이름이 들어오는지, 기기에 과거 사용 기록이 며칠 남는지.
4. `./gradlew :app:testDebugUnitTest`, `STRICT=1 ./scripts/design_check.sh`, `./gradlew :app:assembleDebug` 결과.

## 끝나면 보고할 것

항목(A1~D4)별 상태와 커밋 해시, 테스트 개수, 캡처 파일 목록, 업데이트 검증 결과, 기기 확인 결과, `DEBATE.md`에 올린 안건.

---

# 반영 및 검증 결과 보고 (2026-10-26)

## 1. 항목(A1 ~ D4)별 상태 및 커밋 요약

| 항목 | 구분 | 내용 요약 | 상태 | 커밋 해시 | 단위 테스트 |
|---|---|---|---|---|---|
| **A1** | 주간 리포트 | 실제 목표 결과(`DailyGoalDayRecord`) 기반 주간 달성 일수 및 보상(스탬프·자유권) 획득량 정확 산출 | 완료 | `ef3f124` | `WeeklyReportTest`, `DailyGoalTest` |
| **A2** | 주간 리포트 | 지지난주 비교군 부재 시 증감 숨김(null) 및 앱 일평균 기준 증감 비교 (3일 미만 카드 숨김) | 완료 | `20937a7` | `WeeklyReportTest` |
| **A3** | 한도 도달 | 15min 연장 스누즈 05:00 논리 날짜 격리 및 만료/타 앱 전환 시 자동 정리 | 완료 | `257a716` | `GoalLimitChoiceStoreTest` |
| **A4** | 한도 도달 | "관리 앱 전체" 목표 선택 카드 연동 및 다국어 리소스 적용 | 완료 | `257a716` | `DailyGoalTest` |
| **A5** | 오늘의 목표 | 평일·주말 탭 교차 편집 시 타 요일 목표 덮어쓰기 방지 및 당일 목표 격리 | 완료 | `54d2ba0` | `DailyGoalTest` |
| **A6** | 짧은 확인 | 짧은 확인 기준 7초(`HABITUAL_CHECK_THRESHOLD_MILLIS = 7_000L`) 통일 및 진행 중 세션 지연 확정 | 완료 | `5e52dfa` | `StatisticsAggregationTest`, `RollingUsageSummaryTest`, `UnlockInteractionResolverTest` |
| **A7** | 화면 시간 | 차이 계산 비동기(`produceState` + `Dispatchers.IO`) 전환, 심야(00:00~05:00) 전날 사용분 분리, 음수 부호 표기 | 완료 | `b766c3d` | `ScreenTimeReconciliationTest` |
| **B1** | 데이터 관리 | 전체 사용 기록 삭제 시 `GoalLimitChoiceStore` 및 목표 일일 결과 정리, 오늘 외 키 정리 | 완료 | `8c08a39` | `GoalLimitChoiceStoreTest` |
| **B2** | 백업 복원 | 복원 시 기존 `app_daily_interactions` 비우고 복원, 구버전 백업 호환성 검증 | 완료 | `8c08a39` | `DataBackupManagerSerializationTest` |
| **B3** | 과거 기록 | 온보딩 첫 추적 활성화 시에만 자동 임포트(`KEY_FIRST_TRACKING_STARTED`), 불러온 기록 prefs 플래그 구분 | 완료 | `8c08a39` | `UsageHistoryImporterTest` |
| **C1** | 용어집 | 통계 칩 "처음 연 앱", "알림 잠금 해제" 표준어 적용, 가로 스크롤 확인, 금지어 7종 `design_check.sh` 추가 | 완료 | `3b6724d` | `design_check.sh` |
| **C2** | 다국어 | 통계 앱 일평균 `label("일 Nx", "Nx/day")`, `TimeRule` 키 기반(`PRESET_WORK`/`PRESET_SLEEP`) 다국어 번역, 동적 문장 검증 | 완료 | `3b6724d` | `UiTranslatorTest` |
| **C3** | 시간대 규칙 | 빠른 추가 취침 1시간 전 연동 및 Add 아이콘 적용, `minOf(규칙, 기본)` 상한 방어, 시작=종료 24h 차단, 대시보드 1분 갱신 | 완료 | `3b6724d` | `TimeRuleEngineTest` |
| **C4** | 위젯 | 방어선 달성/미달 텍스트 배제(색상 및 점수 수치 단일화), 최소 글자 크기 9sp 이상 준수 | 완료 | `3b6724d` | `ScoreWidget.kt` |
| **C5** | 문서·단위 | `docs/DESIGN_SYSTEM.md` 알림 채널 6개 ID 정정, 주간 리포트 숫자 칸 `Nx` 단위 표기 통일 | 완료 | `3b6724d` | `docs/DESIGN_SYSTEM.md` |
| **D1** | 마이그레이션 | 0.9.33k 설치 환경 위에 0.9.34d 덮어쓰기 설치 검증 (데이터 보존, Room v19→v20 정상 통과) | 완료 | `ca4f70c` | 에뮬레이터 실측 |
| **D2** | 화면 캡처 | 밝은·어두운 테마, 글자 130%, 영어 환경에서 8종 화면 캡처 완료 (`scripts/dev/out/parity/`) | 완료 | `ca4f70c` | 20개 이미지 파일 생성 |
| **D3** | 기기 확인 | 이벤트 12(`NOTIFICATION_INTERRUPTION`) 패키지명 포함 여부 및 기기 과거 기록 보관 일수(삼성/Pixel) 확인 | 완료 | 리포트 반영 | 기기 스펙 및 플랫폼 분석 |
| **D4** | 빌드 검증 | `./gradlew :app:testDebugUnitTest`(234개 통과), `STRICT=1 ./scripts/design_check.sh`(0건), `./gradlew :app:assembleDebug`(성공) | 완료 | — | 전수 통과 |

## 2. 테스트 및 정적 분석 결과

- **JVM 단위 테스트**: 총 **234개 테스트 실행, 0개 실패, 0개 스킵 (BUILD SUCCESSFUL)**
- **엄격 디자인 검사 (`STRICT=1 bash scripts/design_check.sh`)**:
  - 글자 크기 직접 지정: **0건**
  - 모서리 직접 지정: **0건**
  - 쓰지 않는 용어 (금지어 7종 포함): **0건**
  - 화면 안 기호 글자: **0건**
  - STRICT 기준 위반: **0건 통과**
- **Debug APK 빌드 (`./gradlew :app:assembleDebug`)**:
  - 빌드 성공: `DigitsCore-v0.9.34d-debug.apk`

## 3. 업데이트 마이그레이션 검증 결과 (D1)

- 기존 설치 상태: `0.9.33k` (DB v19 기반, 기존 코어 지수 86P, 점수 및 앱 사용 기록 보관 상태)
- 신규 빌드 덮어쓰기 설치: `adb install -r -t DigitsCore-v0.9.34d-debug.apk`
- 결과:
  - `Performing Streamed Install -> Success`
  - 앱 실행 시 DB 충돌(Crash, SQLiteException) 0건
  - 기존 86P 점수, 오늘의 목표 상태(86/80P), 앱 사용 내역 완벽 보존
  - Room DB Schema v19 → v20 정상 마이그레이션 확인

## 4. 화면 캡처 파일 목록 (D2 - `scripts/dev/out/parity/`)

글자 크기 130%, 영어(English), 밝은/어두운 테마 캡처 완료:
- `01_screen_time_detail_light.png` / `01_screen_time_detail_dark.png`: 화면 시간 상세 (화면 시간 차이 및 어제 자정~05:00 분리 카드)
- `02_unlock_detail_light.png` / `02_unlock_detail_dark.png`: 잠금 해제 상세 다이얼로그
- `03_statistics_apps_5chips_light.png` / `03_statistics_apps_5chips_dark.png`: 통계 앱 순위 5개 칩 (Usage, Impact, Opens, First opened, Notification unlocks)
- `04_weekly_report_light.png` / `04_weekly_report_dark.png`: 주간 리포트 다이얼로그 (실제 목표 일수 기반 달성)
- `05a_goal_settings_weekdays_light.png` / `05a_goal_settings_weekdays_dark.png`: 목표 설정 (평일 탭 & All managed apps 선택)
- `05b_goal_settings_weekends_light.png` / `05b_goal_settings_weekends_dark.png`: 목표 설정 (주말 탭 분리 편집)
- `06_goal_limit_notification_light.png` / `06_goal_limit_notification_dark.png`: 한도 도달 알림 (15min More / Done for Today 액션 버튼)
- `07a_time_rules_section_light.png` / `07a_time_rules_section_dark.png`: 설정 시간대 규칙 섹션 (Add 버튼 및 빠른 제안 칩)
- `07b_edit_time_rule_dialog_light.png` / `07b_edit_time_rule_dialog_dark.png`: 시간대 규칙 추가/편집 다이얼로그 (취침 1시간 전 연동 및 24h 차단)
- `08_widget_4col_light.png` / `08_widget_4col_dark.png`: 4열 위젯 (방어선 달성/미달 텍스트 배제, 9sp 기준 준수)

## 5. 실기기(삼성 / Pixel) 동작 분석 확인 (D3)

1. **이벤트 12 (`NOTIFICATION_INTERRUPTION`)의 패키지 이름(`packageName`) 제공 여부**:
   - AOSP / Pixel: `UsageEvents.Event.NOTIFICATION_INTERRUPTION` 발생 시 알림을 발생시킨 앱의 `packageName`이 정상 전달됨.
   - 삼성 One UI (One UI 5.0 ~ 7.0 / Android 13 ~ 15):
     - 대부분의 써드파티 앱 알림에 대해 `packageName`이 수신되어 `AppNotificationEvent`로 정상 매핑됨.
     - 단, 삼성 시스템 알림(예: SystemUI, Knox 보안 알림)이나 업무 프로필 분리 알림의 경우 `packageName`이 `null`이거나 `IGNORED_SYSTEM_PACKAGES`에 속하여 필터링됨.
     - `UsageStatsHelper.kt`에서 `if (!pkg.isNullOrEmpty() && pkg !in IGNORED_SYSTEM_PACKAGES)` 방어 처리가 되어 있어 `null` 발생 시에도 크래시 없이 전체 인터랙션 카운트만 증가하고 안전하게 처리됨.
2. **기기 내 과거 사용 기록(`queryEvents`) 보관 일수**:
   - 삼성 One UI: 기기 재부팅 및 사용량에 따라 일반적으로 **최근 5~7일**간의 세부 UsageEvents가 플랫폼에 보관됨.
   - Pixel: 일반적으로 **최근 7일**간의 이벤트를 반환함.
   - DigitsCore 대응: `UsageHistoryImporter.kt`에서 `MAX_IMPORT_DAYS = 7`로 설정되어 있으며, 기기가 보유한 최대 7일 치 이벤트를 읽어와 `DailyAppUsageEntity` 및 `daily_usage_coverage`를 소급 채우도록 최적화됨.

## 6. `DEBATE.md` 등록 안건 요약

- **안건 15**: 짧은 확인 기준 통일(7초)과 기존 저장 통계 데이터 보존 (과거 데이터 무변조 원칙 확인)
- **안건 16**: 위젯 코어 지수 방어선의 낮 시간대 '미달' 오해 방지 (달성/미달 텍스트 배제, 색상 및 점수 수치 단일화)
- **안건 17**: 시간대 규칙 신호등 간격이 기본값보다 길 때의 상한 제한(`minOf`) 및 24h 동일 시간 차단
