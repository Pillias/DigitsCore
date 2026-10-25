# Gemini 작업 지시: 스크린 타임 비교 개선안 10개 전체 적용

아래 전체를 Gemini에게 그대로 붙여 넣으세요.

---

## 역할과 목표

너는 Android 앱 DigitsCore(패키지 `com.digitscore.app`, Kotlin·Jetpack Compose·Room·Glance)의 개발자다. 프로젝트 루트는 `/Users/jinwoo/Documents/antigravity/mysterious-brahmagupta`이다.

삼성 디지털 웰빙과 애플 스크린 타임을 비교해 만든 개선안 10개를 **모두** 구현한다. 원문 제안서: https://claude.ai/artifact/SjYcdVuAuBpARo1MAV2NiS

DigitsCore의 정체성은 "얼마나 썼나"가 아니라 "지금 사용과 휴식의 균형이 어떤가"를 코어 지수(1~99, 75에서 시작, 05:00 기준 하루, 자정에 초기화하지 않음)로 보여 주고, **막지 않고 알려 주는** 앱이다. 이번 작업은 그 정체성을 유지하면서 돌아보기와 시간대 규칙을 보강한다.

## 절대 규칙

1. **코어 지수 계산을 바꾸지 않는다.** `engine/CumulativeScoreEngine.kt`, `engine/CumulativeTimeline.kt`, `engine/RestPhasePolicy.kt`, `data/CumulativeScoreStore.kt`의 점수 계산·체크포인트 형식은 건드리지 않는다. 점수에 영향을 주는 변경이 필요하다고 판단되면 구현하지 말고 `DEBATE.md`에 안건으로 적고 멈춘다.
2. **앱을 강제로 막지 않는다.** 강제 종료, 화면 가림, 접근성 서비스, PIN 잠금, 웹사이트 추적, 카메라·블루투스 권한은 추가하지 않는다. 새 `uses-permission`을 추가하지 않는다. (매니페스트에 receiver 추가는 허용, `exported="false"`)
3. **인터넷으로 보내지 않는다.** 모든 데이터는 기기 안(Room/SQLCipher, SharedPreferences)에만 둔다.
4. **기존 사용자 데이터를 잃지 않는다.** Room 스키마를 바꾸면 반드시 `MIGRATION_19_20`을 추가하고 `version = 20`, `app/schemas/.../20.json`이 생성되게 한다. `fallbackToDestructiveMigration`은 쓰지 않는다. 0.9.32 → 새 버전 업데이트 테스트를 한다.
5. **용어·디자인 기준을 지킨다.** `docs/GLOSSARY.md`, `docs/DESIGN_SYSTEM.md`를 먼저 읽는다.
   - 표준어: 코어 지수, 지수 단계, 잠금 해제, 짧은 확인, 관리 앱, 관리할 앱 한도, 지수 방어선, 오늘의 목표, 화면 신호등, 받은 알림, 15min 자유권, 회복 부담.
   - 시간 단위는 `min`(숫자 칸은 `formatMinutesSmallUnit`/`formatGoalMinutesSmallUnit`로 0.7배), 문장 속 횟수는 한국어 "회", 영어 "times". "~합니다"체, 느낌표 금지, 이모지·기호 글자(✓▲▼▸● 등) 금지.
   - 글자 크기는 `ui/theme/Tokens.kt`의 `DigitsTextSize`, 모서리는 `DigitsShape`만 쓴다. `fontSize = 13.sp`, `RoundedCornerShape(12.dp)`, `Color(0x…)`를 화면 코드에 직접 쓰지 않는다. 점수 색 글자는 `ScoreGreenText` 등 `*Text` 변형, 채우기는 `ScoreGreen` 등.
   - 아이콘은 `Icons.Rounded.*`/`Icons.AutoMirrored.Rounded.*`만. 굵기는 Normal·Medium·Bold만.
   - 새 문구는 `res/values/strings.xml`과 `res/values-en/strings.xml`에 함께 넣는다. 동적 문장을 `UiTranslator`에 의존하지 말고, 만들었다면 `UiTranslatorTest`에 영어 회귀 케이스를 추가한다.
6. **버전 규칙.** 디버그 빌드는 `version.properties`의 숫자를 유지하고 a/b/c 접미사가 붙는다(빌드 스크립트가 처리). 릴리스용 AAB를 만들 때만 PATCH를 올린다. 이번 작업 중에는 릴리스 빌드를 만들지 않는다.
7. **테스트 가능한 순수 로직은 분리한다.** 계산(집계, 범위, 시간대 판정, 리포트 문장 재료)은 Android 의존성 없는 `object`/함수로 `model/` 또는 `data/`에 두고 `app/src/test`에 JUnit 테스트를 붙인다. 예: 이미 있는 `model/GoalRanges.kt` + `GoalRangesTest.kt` 방식.
8. **비밀번호·키를 출력하거나 커밋하지 않는다.** `keystore/`, `local.properties`를 건드리지 않는다.

## 꼭 알아야 할 현재 구조

| 영역 | 위치 | 메모 |
|---|---|---|
| 추적 서비스 | `service/TrackerForegroundService.kt` | 1분 주기 재계산, 80% 목표 알림(약 795~830행), 화면 신호등 발동(`EdgeLightingOverlayManager.showEdgeWarning` 호출부), `onConfigurationChanged` |
| 사용 이벤트 | `data/UsageStatsHelper.kt` | `HOME_LAUNCHER_PACKAGES`, NOTIFICATION_INTERRUPTION(이벤트 12) 수집, KEYGUARD_HIDDEN |
| 잠금 해제·알림 이벤트 | `data/entity/DeviceInteractionEventEntity.kt` | **패키지 이름 없음, 25시간만 보관**. 앱별 알림·30일 통계에는 쓸 수 없음 |
| 앱 사용 구간 | `ForegroundUsageSessionEntity` (30일) | `effectivePackageName`, `sessionStartTimeMillis`, `dateString`(05:00 기준) |
| 앱별 하루 합계 | `DailyAppUsageEntity` | `usageMillis`, `sessionCount`, `shortSessionCount` |
| 하루 점수 기록 | `DailyScoreHistoryEntity` | `finalScore`, `unlockCount` |
| 오늘의 목표 | `data/DailyGoalStore.kt`, `model/DailyGoal.kt` | SharedPreferences, `getLogicalDateString()`(05:00 기준), `setCustomGoal` |
| 목표 범위 | `model/GoalRanges.kt` | 슬라이더 범위·추천값 (재사용) |
| 알림 | `notification/ScoreNotificationManager.kt` | 채널: status, guidance, soft_guidance_v2, wake_prompt, goal |
| 화면 신호등 | `notification/EdgeLightingOverlayManager.kt` | 관리 앱 한도 `getManagedTriggerMinutes` |
| 대시보드 | `ui/dashboard/DashboardScreen.kt`, `GoalDetails.kt` | `ScreenTimeDetailDialog`(약 2650행), `EditThreeGoalsDialog`, `GoalItemDetailDialog` |
| 통계 | `ui/statistics/StatisticsExplorer.kt`, `ExplorerData.kt` | 시간별·일별·요일별·앱 순위 |
| 위젯 | `widget/ScoreWidget.kt` (Glance), `WidgetSnapshotStore.kt` | |
| DB | `data/DigitsDatabase.kt` | version 19, 마이그레이션 1→19 |
| 백업·삭제 | `data/backup/DataBackupManager.kt`, `data/privacy/PrivacyDataManager.kt` | 새 테이블은 백업·복원·전체 삭제·보관 기간 정리에 포함 |

## 구현할 10개 (순서대로)

### 1단계 (0.9.35 목표)

**1. OS 화면 시간과 다른 이유 보여 주기**
- `ScreenTimeDetailDialog`에 "디지털 웰빙과 숫자가 다른 이유" 영역을 추가한다.
- 오늘 기준으로 계산해 보여 줄 값: (a) DigitsCore 화면 시간(05:00부터, 현재 값), (b) 자정부터 기준 OS 방식 추정치 = `UsageStatsManager.queryAndAggregateUsageStats` 또는 이벤트 합으로 자정~지금 전체 전면 사용 합계(런처 포함), (c) 차이 항목: 00:00~05:00 사용분, 홈 화면(런처) 머문 시간, 분할 화면·PiP 중복 제거분, 기타.
- 각 항목은 min 단위, 합이 차이와 맞지 않으면 "기타"로 나머지를 표시한다. 계산은 순수 함수(`data/ScreenTimeReconciliation.kt` 등)로 분리하고 테스트한다.
- 문장 예: "디지털 웰빙은 자정부터 세고 홈 화면 시간도 포함합니다. DigitsCore는 05:00부터 세고 홈 화면은 빼서 숫자가 다를 수 있습니다."

**3. 잠금 해제 후 처음 연 앱**
- 잠금 해제(USER_PRESENT/KEYGUARD_HIDDEN) 직후 60초 안에 처음 전면에 온 앱(런처·DigitsCore 자신 제외)을 그날 집계한다.
- 30일 통계가 필요하므로 새 테이블을 만든다(아래 "새 테이블" 참고). 집계는 서비스 재계산 주기에서 증분으로, 같은 잠금 해제를 두 번 세지 않게 커서(마지막 처리 시각)를 둔다.
- 화면: 잠금 해제 상세(대시보드 잠금 해제 칸 → 상세)와 통계 앱 순위에 "잠금 해제 후 처음 연 앱" 상위 5개(횟수, 그중 짧은 확인 횟수).

**7. 요일별 목표**
- 오늘의 목표 설정에 "평일·주말 따로" 스위치. 켜면 평일 값·주말 값을 따로 저장(SharedPreferences, `DailyGoalStore`에 키 추가).
- 아침 브리핑·자동 배정은 그날이 평일인지 주말인지(05:00 기준 날짜의 요일)에 맞는 값을 쓴다. 범위는 `GoalRanges`를 그대로 쓴다.

**2. 주간 리포트**
- 매주 월요일 05:00 이후 첫 화면 켜짐(서비스가 이미 받는 `ACTION_USER_PRESENT`/재계산)에서 한 번만 알림을 보낸다. 새 채널 `digitscore_weekly_report`(중요도 기본), 마지막 발송 주를 prefs에 저장해 중복 방지.
- 알림을 누르면 "주간 리포트" 화면(다이얼로그 또는 통계 탭)을 연다. 내용(지난주 월~일, 05:00 기준):
  - 코어 지수: 하루 마감 점수 평균과 지난주 대비 변화, 가장 높았던 날·낮았던 날
  - 오늘의 목표: 3가지 각각 달성 일수, 모두 지킨 날, 받은 스탬프·자유권
  - 화면 시간·잠금 해제·짧은 확인: 하루 평균과 지난주 대비
  - 가장 많이 늘어난 앱·줄어든 앱 1개씩
  - 5번 기능의 선택 기록(“15min 더”/“오늘은 여기까지” 횟수)
- 리포트 재료 계산은 순수 함수(`data/WeeklyReport.kt`)로 만들고 테스트한다. 기록 없는 날은 평균에서 뺀다(기존 일 평균 규칙과 같게).
- 설정 → 알림 탭에 "주간 리포트 알림" 스위치(기본 켬).

### 2단계 (0.9.36~37 목표)

**5. 한도 도달 시 선택 카드**
- 관리할 앱 한도(목표 2)에 도달하면(100%, 하루 1회) 목표 알림 채널로 알림을 보내고 액션 두 개를 단다: "15min 더", "오늘은 여기까지". 앱을 막지 않는다.
- 액션은 새 `BroadcastReceiver`(`receiver/GoalLimitActionReceiver.kt`, `exported=false`)로 받는다.
  - "15min 더": 15min 뒤 같은 앱을 계속 쓰고 있으면 화면 신호등을 한 번 켜고 같은 알림을 다시 보낸다(재발송은 하루 최대 3회).
  - "오늘은 여기까지": 그날 남은 시간 동안 그 앱의 화면 신호등 재알림 간격을 5min으로 줄인다.
- 선택은 날짜·앱·선택·시각으로 prefs(JSON 배열, 최근 35일만 보관) 또는 새 테이블에 저장하고 주간 리포트에 보여 준다.
- 목표 상세(`GoalItemDetailDialog` APP)에 오늘 선택 기록을 한 줄로 표시.

**4. 알림이 부른 잠금 해제**
- `UsageStatsHelper`에서 NOTIFICATION_INTERRUPTION 이벤트의 `packageName`을 함께 받아, 새 테이블에 날짜·앱별 받은 알림 수를 집계한다(기존 25시간 이벤트 테이블은 그대로 둔다).
- 알림 뒤 60초 안에 잠금 해제가 있고, 그 직후 처음 연 앱이 알림을 보낸 앱과 같으면 "알림이 부른 잠금 해제"로 1회 센다.
- 화면: 통계(또는 잠금 해제 상세)에 "알림을 많이 보낸 앱"과 "알림이 부른 잠금 해제" 상위 5개, 안내 문장 "이 앱 알림을 끄면 하루 약 N회 잠금 해제를 줄일 수 있습니다."와 해당 앱 알림 설정으로 가는 버튼(`Settings.ACTION_APP_NOTIFICATION_SETTINGS`).
- 일부 기기는 이벤트 12에 패키지가 비어 있을 수 있다. 비어 있으면 앱별 표시를 숨기고 전체 수만 보여 준다.

**8. 관리 앱 묶음 한도**
- 목표 2에 범위 선택 추가: "앱 1개"(기존) / "관리 앱 전체". 저장은 `DailyGoalStore`에 scope 키(기본 앱 1개, 기존 사용자 동작 유지).
- "관리 앱 전체"면 관리 등급(`AppGrade`/`AppCategoryType` 관리) 앱의 오늘 사용 합계로 진행률·80% 알림·한도 알림·주간 달성을 계산한다. 슬라이더 범위는 `GoalRanges.appLimit`에 관리 앱 합계 기록을 넣어 계산한다.
- 대시보드 목표 칸 라벨: "관리 앱 전체 · 45/60min" 형태.

### 3단계 (정식 출시 전)

**6. 시간대 규칙 (업무·수면 준비)**
- 설정 → 알림 탭에 "시간대 규칙" 섹션. 규칙 최대 3개, 각 규칙: 이름(업무/수면 준비/직접 입력), 요일, 시작·끝 시각(자정 넘김 허용), 관리 앱 신호등 시간(5/10/15/20min).
- 규칙 시간 안에서는 `EdgeLightingOverlayManager`의 관리 앱 발동 시간 대신 규칙 값을 쓴다. **점수 계산은 바꾸지 않는다.**
- "수면 준비" 기본 시각은 `RestPhasePolicy`가 학습한 휴식 시작 시각 1시간 전으로 제안만 한다(학습값이 없으면 23:00). 읽기 전용으로 학습값을 가져온다.
- 시간대 판정(요일, 자정 넘김, 겹치는 규칙은 더 짧은 시간 우선)은 순수 함수 + 테스트.
- 대시보드 상단 상태 줄에 지금 적용 중인 규칙 이름을 작게 표시("업무 시간 · 신호등 10min").

**10. 설치 첫날 지난 기록 불러오기**
- 추적을 처음 켤 때(또는 설정 → 데이터의 "지난 기록 불러오기" 버튼) `UsageStatsManager.queryEvents`로 기기에 남은 과거 기록(최대 7일)을 읽어 `DailyAppUsageEntity`와 `daily_usage_coverage`를 채운다. 이미 기록이 있는 날은 덮어쓰지 않는다.
- **코어 지수 체크포인트와 점수 기록은 만들지 않는다**(점수는 75에서 시작하는 기존 규칙 유지). 불러온 날은 통계에서 "불러온 기록"으로 표시하고 점수 그래프에서는 비운다.
- 불러온 평균은 목표 슬라이더(`GoalRanges`)와 주간 리포트 비교에 쓴다.
- 기기별 보관 일수가 다르므로 실제 불러온 날짜 범위를 결과 문장으로 보여 준다("10월 18일~24일 기록 7일을 불러왔습니다.").

**9. 위젯에 목표 진행**
- `ScoreWidget`에 크기별 배치: 작은 크기는 코어 지수만(기존), 가로 4칸 이상이면 아래에 오늘의 목표 3칸(방어선 P, 관리할 앱 min, 잠금 해제 x와 달성 표시).
- 위젯 데이터는 `WidgetSnapshotStore`에 목표 값을 추가해 서비스가 갱신할 때 같이 쓴다. 위젯 색은 `ScorePalette`만 쓴다.

## 새 테이블 (DB version 20)

하나로 묶는다. 이름 예시 `app_daily_interactions`:

| 열 | 타입 | 설명 |
|---|---|---|
| dateString | TEXT | 05:00 기준 날짜 (PK 일부) |
| packageName | TEXT | (PK 일부) |
| firstAfterUnlockCount | INTEGER | 잠금 해제 후 처음 연 횟수 |
| firstAfterUnlockQuickCount | INTEGER | 그중 짧은 확인 |
| notificationCount | INTEGER | 받은 알림 수 |
| notificationUnlockCount | INTEGER | 알림이 부른 잠금 해제 |
| lastUpdatedTimestamp | INTEGER | |

- `MIGRATION_19_20`은 `CREATE TABLE IF NOT EXISTS` + 인덱스만. 기존 테이블은 건드리지 않는다.
- 보관: 앱 기록 보관 기간 설정(`PrivacyDataManager.applyAppHistoryRetention`)과 전체 삭제(`deleteAllUsageHistory`)에 포함.
- 백업·복원(`DataBackupManager`)에 포함. 옛 백업(이 테이블 없음) 복원도 되게 `optJSONArray`로 읽는다.
- 마이그레이션 테스트: 19 → 20 후 기존 데이터 수 유지 확인(가능하면 androidTest, 최소한 SQL 수동 검증 기록).

## 작업 방식

1. 시작 전에 `git status`로 깨끗한지 확인하고 브랜치 `feature/screen-time-parity`를 만든다.
2. 1단계 → 2단계 → 3단계 순서로, **제안 하나마다 커밋**한다. 커밋 메시지 예: `feat(weekly-report): 주간 리포트 알림과 화면`.
3. 각 제안이 끝날 때마다 실행:
   - `./gradlew :app:testDebugUnitTest`
   - `STRICT=1 ./scripts/design_check.sh` (글자 크기·모서리 직접 지정 0, 쓰지 않는 용어 0, 기호 글자 0 유지)
   - `./gradlew :app:assembleDebug`
4. 에뮬레이터가 있으면 `scripts/dev/tasks/build_install.sh`로 설치하고, 바뀐 화면을 밝은·어두운 테마, 글자 크기 130%, 영어에서 캡처해 `scripts/dev/out/`에 남긴다.
5. 업데이트 검증: 0.9.32 릴리스 AAB(`DigitsCore-v0.9.32-release.aab`)나 그 시점 debug 빌드를 먼저 설치해 기록을 만든 뒤 새 빌드를 덮어 설치하고, 기록·목표·점수가 그대로인지 확인한다.
6. `docs/DESIGN_SYSTEM.md`(새 화면·알림 채널), `docs/GLOSSARY.md`(새 용어: 주간 리포트, 잠금 해제 후 처음 연 앱, 알림이 부른 잠금 해제, 시간대 규칙, 관리 앱 전체 한도, 불러온 기록), `README.md` 기능 목록을 갱신한다.
7. `-1`이 붙은 중복 파일(`*-1.kt` 등)이 생기면 빌드가 깨지므로 매 단계 확인한다.

## 끝나면 보고할 것

- 제안별 상태(완료/부분/보류)와 보류 이유
- 바뀐 파일 목록과 커밋 해시
- 새 테이블·마이그레이션 요약, 업데이트 검증 결과
- 테스트 결과(개수, 실패 0), `design_check` 결과
- 캡처 파일 이름 목록
- 기기 확인이 필요한 항목: 이벤트 12의 패키지 이름 유무, 과거 기록 보관 일수(삼성·Pixel)
- 코어 지수 계산에 영향을 줄 수 있어 `DEBATE.md`로 넘긴 안건이 있으면 그 내용
