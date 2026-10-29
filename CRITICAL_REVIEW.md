# DigitsCore 프로젝트 상세 코드 검토 및 비판적 평가 보고서

- **검토 일자**: 2026-10-29
- **검토 대상**: `/Users/jinwoo/Documents/Antigravity/DigitsCore` (`v0.9.41`, `versionCode 941`, `compileSdk`/`targetSdk 36`)
- **검토 범위**: 전체 아키텍처, 측정/점수 엔진, DB/암호화/백업, UI/UX(Compose·Glance·오버레이), 빌드/배포 구성, 문서 정합성 및 Google Play 정책 리스크

## 1. 종합 평가 요약 (Executive Summary)

**DigitsCore**는 기존의 '자정(00:00) 초기화형 스크린 타임 앱'이나 '강제 차단형 디지털 디톡스 앱'의 한계를 넘어, **Garmin Body Battery의 에너지 충전·소모 모델을 스마트폰 사용에 결합한 독창적인 디지털 웰빙 앱**입니다. 특히 `UsageStatsManager.queryEvents()`를 기반으로 분할 화면·PiP 중복 집계를 배제하고, 75점 기준 피벗 누적 모델(`CumulativeScoreEngine`)과 잠금 해제 간격(`UnlockPacingPolicy`), 아침 기상 확정 및 3대 목표·보호권·도룡뇽 성장 서사를 결합한 기획력은 상용 앱 수준 이상으로 정교합니다.

그러나 **실제 코드베이스와 엔지니어링 구현을 비판적으로 해부해 보면**, 빠른 기능 이터레이션 과정에서 누적된 **① 초거대 단일 파일(God Files) 구조, ② 문서와 실제 코드 간의 심각한 괴리(Documentation Drift), ③ SQLCipher 암호화 정책을 우회하는 평문 SharedPreferences 파편화 및 백업 누락, ④ Android 15/16(`targetSdk 36`)의 백그라운드 서비스 제한으로 인한 재부팅 시 추적 중단 버그, ⑤ 정규식(Regex) 기반 런타임 번역기의 성능·유지보수 취약점** 등 정식 출시(`v1.0`) 및 장기 운영 전에 반드시 해결해야 할 핵심 기술 부채가 존재합니다.

| 평가 영역 | 평점 (10점) | 핵심 강점 | 치명적 약점 및 리스크 |
| :--- | :---: | :--- | :--- |
| **제품 기획 & 도메인 로직** | **9.0 / 10** | • 75P 피벗 연속 누적 코어 지수 + 휴식 회복의 독보적 개념<br>• 분할 화면/PiP 시간 중복 방지 및 최고 부하 등급 적용<br>• '15min 자유권'의 모순을 '연속 기록 보호권 + 도룡뇽 성장'으로 해결 | • 수면/기상 추론 및 4단계 등급 규칙이 정교한 만큼 초기 사용자가 점수 변동을 직관적으로 이해하기까지 학습 곡선 존재 |
| **소프트웨어 아키텍처 & 코드 품질** | **5.5 / 10** | • 순수 함수로 분리된 엔진(`CumulativeScoreEngine`, `ForegroundUsageAggregator`)과 탄탄한 단위 테스트 | • `DashboardScreen.kt`(225KB), `PresetModeScreen.kt`(119KB), `StatisticsExplorer.kt`(96KB) 등 **초거대 단일 파일(God Object)**<br>• UI 컴포저블이 ViewModel을 건너뛰고 DB·SharedPreferences를 직접 호출 |
| **데이터 무결성, 보안 & 백업** | **6.5 / 10** | • SQLCipher + Android Keystore 래핑 + 평문 DB 무손실 마이그레이션<br>• PBKDF2(310,000회) + AES-256-GCM 수동 백업 암호화 | • **암호화 예외 사각지대**: 목표·상위 사용 앱·시간대 규칙 등 민감 데이터가 7개 이상의 **평문 SharedPreferences**에 분산 저장됨<br>• **백업 누락**: 시간대 규칙(`TimeRuleStore`), 내 기록(`PersonalBest`), 평일/주말 목표 설정이 백업(`DataBackupManager`)에서 누락됨 |
| **성능, 배터리 & OS 호환성** | **6.0 / 10** | • 화면 OFF 시 폴링 즉시 중단, 커서 기반 증분 `UsageEvents` 조회<br>• 측정 진단(`MeasurementDiagnostics`) 자체 내장 | • **Android 15/16 치명적 이슈**: `targetSdk 36`에서 `BOOT_COMPLETED` 수신 시 `specialUse` FGS 시작이 차단되어 **재부팅 후 추적이 조용히 죽음**<br>• 매 1분마다 오늘 전체 세션을 삭제 후 재삽입(`replaceDay`)하는 DB 쓰기 증폭 |
| **국제화(i18n) & UI 유지보수성** | **5.0 / 10** | • 앱 내 한/영 즉시 전환, 130% 큰 글꼴 및 다크/라이트 대비 고려 | • `UiTranslator`(`LocalizedText.kt`, 73KB)가 매 리컴포지션마다 **80여 개 정규식(Regex)과 200여 개 문자열 치환**을 수행하는 취약한 구조 |
| **문서 정합성 & 저장소 위생** | **5.5 / 10** | • 설계 배경(`DEBATE.md`), 채점 수식(`SCORING.md`), 릴리스 노트가 매우 상세함 | • `README.md`, `ARCHITECTURE.md`, `Agents.md`가 실제 `v0.9.41` 코드와 불일치(폐지된 15분 자유권, 미구현 걸음 센서, 구버전 통계 범위 등 기재)<br>• 로컬 폴더에 업로드 키스토어·백업본·스크래치 파일 혼재 |

## 2. 폴더 및 프로젝트 구조 상세 분석

### 2.1. 디렉터리 및 모듈 구성 현황

프로젝트는 단일 모듈(`:app`) Android 애플리케이션으로 구성되어 있으며, 의존성 주입(Hilt/Koin), DataStore, WorkManager를 사용하지 않고 순수 Android SDK + Room + Coroutines/StateFlow + Jetpack Compose/Glance로만 구축되어 있습니다.

| 경로 | 역할 및 현황 | 주요 점검 결과 |
| :--- | :--- | :--- |
| `app/src/main/java/.../engine/` | 코어 지수 계산, 타임라인 전이, 수면/휴식 정책, 코칭 문구 생성 | 순수 Kotlin 객체로 잘 격리되어 있으나, 현재 쓰이지 않는 구형 엔진(`ScoreCalculator.kt`, `RollingScoreCalculator.kt` 20KB)이 함께 남아 있음 |
| `app/src/main/java/.../data/` | Room DB(v20, 14개 엔티티), SQLCipher 보안, 백업, `UsageStatsHelper`, 각종 Store | Room DB와 7개 이상의 `SharedPreferences` 기반 Store가 혼재되어 단일 진실 공급원(SSOT) 원칙이 깨짐 |
| `app/src/main/java/.../service/` | `TrackerForegroundService.kt` (78KB) | 이벤트 수집, DB 저장, 일일 목표 판정, 알림 발송, 위젯 갱신, 엣지 라이팅 호출을 혼자 담당하는 비대한 서비스 |
| `app/src/main/java/.../ui/` | Compose 화면(`dashboard`, `statistics`, `settings`, `onboarding`) | 화면 파일 하나가 100KB~225KB에 달하며 UI 레이어 안에 비즈니스 쿼리와 상태 변이가 뒤섞여 있음 |
| `app/src/main/java/.../notification/` | 상태바 알림, 동적 아이콘(`LauncherIconManager`), 화면 신호등(`EdgeLightingOverlayManager`) | Canvas 기반 커스텀 드로잉 완성도는 높으나 OS 정책(오버레이, 런처 별칭 전환) 리스크 존재 |
| `app/src/main/java/.../widget/` | Jetpack Glance 위젯 4종(`1x1`, `2x1`, `2x2`, `4x2`) | `v0.9.41`에서 크기별 4개 리시버로 깔끔하게 재설계됨 |
| `app/src/test/` & `androidTest/` | 단위 테스트 22개 파일, 계측 테스트 5개 파일 | 핵심 수식·마이그레이션·암호화 회귀 테스트가 잘 갖춰져 있음 |
| `docs/` & 루트 `.md` 문서 | `README.md`, `ARCHITECTURE.md`, `SCORING.md`, `DEBATE.md` 등 30여 개 문서 | 최근 `v0.9.40~0.9.41` 변경사항이 일부 핵심 문서(`README.md` 등)에 반영되지 않아 모순 발생 |
| 루트 작업 파일 (`.gitignore` 대상) | `keystore/digitscore-upload.jks`, `*.apk`, `*.aab`, `*.dcorebackup`, `Claude outputs/` | Git 커밋에서는 제외되어 있으나 작업 디렉터리 루트에 상용 키스토어와 대용량 바이너리·임시 스크립트가 방치됨 |

## 3. 핵심 강점 (What Was Built Well)

비판적 분석에 앞서, DigitsCore가 다른 디지털 웰빙 앱들과 비교해 탁월하게 구현해낸 엔지니어링 성과를 먼저 짚어야 합니다.

1. **정밀한 전면 앱 시간 재구성 (`UsageStatsHelper.kt` & `ForegroundUsageAggregator.kt`)**:
   - 제조사마다 부풀려지거나 중복 계산되는 `queryAndAggregateUsageStats()`에 의존하지 않고, `ACTIVITY_RESUMED`, `ACTIVITY_PAUSED`, `ACTIVITY_STOPPED`, `USER_INTERACTION`, `SCREEN_INTERACTIVE`, `KEYGUARD_HIDDEN` 등 원시 타임라인 상태 기계를 직접 구현했습니다.
   - 특히 Samsung 기기의 분할 화면(Multi-Resume)과 PiP(Picture-in-Picture) 환경에서 **사용 시간은 마지막으로 상호작용한 단일 앱에만 귀속시키고, 점수 감점 등급만 화면에 동시 노출된 앱 중 최고 부하 등급으로 적용**한 설계는 매우 영리하고 정확합니다.
   - 보안 잠금 화면을 쓰지 않는 기기('잠금 없음')에서 부팅 후 `KEYGUARD_HIDDEN` 이벤트가 오지 않아 사용 시간이 0으로 남는 엣지 케이스를 `keyguardUnknown` 플래그로 해결한 점도 실전적입니다.
2. **수학적으로 일관된 75점 피벗 누적 엔진 (`CumulativeScoreEngine.kt` & `CumulativeTimeline.kt`)**:
   - 상방 가동폭 `(100 - S) * 40%`와 하방 가동폭 `S * 13.3%` 스케일러(`speedUp`, `speedDown`)를 통해 점수가 극단(1점이나 99점)에 고착되지 않고 75점 균형점을 중심으로 탄력 있게 움직이도록 설계했습니다.
   - 폴링 주기에 영향을 받지 않는 결정론적 1분 클럭(`CumulativeTimeline.advance`)과 2분 정착 지연(`safeEnd = nowMillis - 2 * MINUTE`)을 도입해 재폴링이나 서비스 재시작 시에도 점수가 이중 차감되지 않습니다.
   - 시계 역행(Clock Rollback) 발생 시 미래 타임스탬프 체크포인트와 통계를 안전하게 정리하는 방어 로직(`recoverFromClockRollback`, `discardFutureRows`)까지 구현되어 있습니다.
3. **데이터 손실을 막는 보수적 암호화 마이그레이션 (`DatabaseEncryptionManager.kt`)**:
   - 기존 평문 SQLite DB를 SQLCipher로 이전할 때 임시 파일(`encrypted.tmp`) 생성 → `sqlcipher_export` → `PRAGMA integrity_check` 검증 → 원본 백업(`plaintext.migration-backup`) 교체 → Room 스키마 오픈 성공 확인 후 백업 삭제라는 5단계 원자적 절차를 밟으며, 실패 시 평문 호환 모드로 자동 복구해 사용자 데이터 증발을 원천 차단했습니다.

## 4. 비판적 기술·설계 결함 분석 (Critical Technical & Architectural Flaws)

### 4.1. [심각도: 상] Android 15/16 (`targetSdk 36`) 부팅 시 Foreground Service 시작 차단 버그

* **위치**: `app/src/main/java/com/digitscore/app/receiver/BootCompletedReceiver.kt` (26~33행), `AndroidManifest.xml` (97~105행)
* **현상 및 원인**:
  - 현재 앱은 `targetSdk = 36`으로 설정되어 있으며, `TrackerForegroundService`는 `android:foregroundServiceType="specialUse"`를 사용합니다.
  - Android 15(API 35) 이상을 타깃팅하는 앱부터는 **`BOOT_COMPLETED` 브로드캐스트 리시버가 `specialUse` 타입의 포그라운드 서비스를 직접 시작하는 것이 OS 차원에서 금지**(`ForegroundServiceStartNotAllowedException` 발생)되었습니다.
  - `BootCompletedReceiver.kt`에서는 `runCatching { context.startForegroundService(serviceIntent) }`으로 예외를 잡고 로그만 출력하고 넘어갑니다.
* **실사용 영향**:
  - Android 15/16 기기 사용자가 휴대폰을 재부팅하거나 야간 자동 재시작이 실행되면, **사용자가 앱을 직접 열어 `MainActivity`를 띄우기 전까지 백그라운드 추적이 완전히 멈춥니다.**
  - 실시간 코어 지수와 상태바 알림을 핵심 가치로 내세우는 앱에서 재부팅 후 추적이 조용히 죽는 것은 치명적인 신뢰도 하락 요인입니다.
* **해결 방안**:
  - Google Play 정책 및 Android 15+ 가이드라인에 맞춰 `BOOT_COMPLETED`에서 직접 FGS를 띄우지 말고, 재부팅 직후 알림("기기가 재시작되어 코어 지수 추적을 재개하려면 탭하세요")을 띄우거나, `WorkManager` 주기 작업으로 백그라운드 집계를 수행하다가 앱 진입 시 FGS를 연결하는 하이브리드 구조로 전환해야 합니다.

### 4.2. [심각도: 상] 저장소 파편화(SQLCipher vs 평문 SharedPreferences) 및 백업 데이터 누락

* **위치**: `DataBackupManager.kt`, `DailyGoalStore.kt`, `TimeRuleStore.kt`, `GoalLimitChoiceStore.kt`, `PRIVACY_POLICY.md`
* **현상 및 원인**:
  1. **개인정보보호방침과의 모순 (평문 노출)**:
     - `PRIVACY_POLICY.md`와 `README.md`는 *"사용 기록과 설정은 SQLCipher로 암호화된 기기 내부 데이터베이스에 저장됩니다"*라고 명시합니다.
     - 그러나 실제 코드를 보면 `DailyGoalStore.kt`(`daily_goal_state.xml`), `GoalLimitChoiceStore.kt`(`goal_limit_choice_store.xml`), `TimeRuleStore.kt`(`time_rules_prefs.xml`) 등 **최소 7개 이상의 일반(평문) `SharedPreferences` 파일**에 사용자가 제한하려는 특정 앱 패키지명(`goal_target_pkg`), 어제 가장 많이 쓴 상위 앱 후보 목록과 사용 시간(`y_candidates` JSON), 날짜별 목표 달성 이력(`goal_day_records_json`), 한도 도달 시 선택 기록 등이 암호화 없이 저장되고 있습니다.
  2. **수동 백업(`DataBackupManager.kt`)의 데이터 유실**:
     - `DataBackupManager.exportToJson()`은 Room DB 테이블들과 `edgeLightingPrefs`, `customStudyPrefs`, 그리고 `goalRewardState`의 일부 필드만 백업합니다.
     - **백업에서 완전히 누락된 항목**:
       - 사용자가 설정한 **시간대 규칙 전체** (`TimeRuleStore`)
       - 사용자의 **내 기록(Personal Best: 최소 관리 앱 시간·최소 잠금 해제 및 달성일)** (`DailyGoalStore.getPersonalBest`)
       - 사용자의 **평일/주말 분리 목표 설정값** (`goal_weekday_*`, `goal_weekend_*`) 및 **365일 목표 달성 상세 기록** (`goal_day_records_json`)
       - 한도 도달 선택 통계 (`GoalLimitChoiceStore`)
* **실사용 영향**:
  - 기기 변경이나 앱 재설치 시 암호화 백업(`.dcorebackup`)을 복원해도 시간대 규칙, 내 최고 기록(Personal Best), 평일/주말 목표 설정, 주간 리포트용 한도 선택 통계가 모두 증발합니다.
* **해결 방안**:
  - 평문 `SharedPreferences`에 흩어진 상태들을 암호화된 Room DB 테이블(또는 `user_settings` / `cumulative_score_state`)로 통합하거나, 최소한 `DataBackupManager`의 백업/복원 스키마(v12)에 위 누락 항목들을 전수 포함해야 합니다.

### 4.3. [심각도: 상] 문서(README·아키텍처·정책)와 실제 코드(`v0.9.41`) 간의 심각한 불일치

코드 변경 속도를 문서가 따라가지 못해, 외부 공개 문서(`README.md`, `PRIVACY_POLICY.md`)와 내부 기준 문서(`Agents.md`, `ARCHITECTURE.md`) 곳곳에 실제 코드와 정반대되는 설명이 남아 있습니다.

| 문서 파일 | 문서에 적힌 내용 (구버전/오류) | 실제 코드 구현 (`v0.9.41` 기준) |
| :--- | :--- | :--- |
| **`README.md`** (47행) | "스탬프 3개를 모으면 **15분 자유권**이 지급되며, 자유권 사용 중(15분)에는 점수 감점·가속·화면 신호등 경고·사용 알림이 모두 면제됩니다." | `v0.9.40`(`StreakRewards.kt`)에서 15분 자유권은 폐지되고 **연속 기록 보호권(Streak Shield, 최대 2장) + 도룡뇽 4단계 성장**으로 대체됨 |
| **`README.md`** (54~63행) | "전체 통계는 `24시간·1주·4주·3개월·1년`을 선택하는 탐색형 차트… `최근 7일·4주·12주·6개월·1년` 기간 선택" | `StatisticsExplorer.kt`는 **실시간(24시간), 4주(28일), 요일별 평균** 3가지 고정 뷰만 제공함 (`3개월·6개월·1년` UI 없음) |
| **`README.md`** (95~98행) | 홈 위젯 구성: `2x2(목표 배지 3종, 미니 추세 차트)`, `4x1 / 4x2(추세 바)` | `v0.9.41`(`ScoreWidget.kt`)에서 미니 차트/추세 바를 제거하고 **`1x1`, `2x1`, `2x2(할 일 한 줄)`, `4x2(목표 3줄 + 할 일)` 4종**으로 개편됨 |
| **`README.md`** (138행) | 권한 표에 **`신체 활동 (선택): 하드웨어 걸음 센서로 기상 판단 보조`** 명시 | `AndroidManifest.xml`에 `ACTIVITY_RECOGNITION` 권한이나 걸음 센서 코드가 전혀 없음 |
| **`ARCHITECTURE.md`** (44, 67~69행) | "Room 데이터베이스 버전은 **v19**… `app_weights`: 앱별 **일반·관리 2단계 등급**… 런처: **고정 B1 전원 버튼 아이콘**" | Room DB는 **v20**, 앱 등급은 **권장·필수·일반·관리 4단계**, 런처는 **도룡뇽 4단계 동적 아이콘** |
| **`Agents.md`** (40~46행) | "메인 지수는 `RollingScoreCalculator`… 빈 기록 80점… 잠금 해제 최소 이벤트는 25시간 보관" | 메인 지수는 `CumulativeScoreEngine`(75점 시작), 잠금 해제 이벤트는 48시간 보관(`INTERACTION_RETENTION_MILLIS`) |

### 4.4. [심각도: 중상] 초거대 단일 파일(God Files)과 단방향 데이터 흐름(UDF) 위반

* **파일 크기 현황**:
  - `DashboardScreen.kt`: **225 KB (약 4,800줄 이상)**
  - `PresetModeScreen.kt`: **119 KB (약 2,500줄 이상)**
  - `StatisticsExplorer.kt`: **96 KB (약 2,000줄 이상)**
  - `TrackerForegroundService.kt`: **78 KB (약 1,600줄 이상)**
  - `LocalizedText.kt`: **73 KB (약 1,500줄 이상)**
  - `DailyGoalStore.kt`: **51 KB (약 1,100줄 이상)**
* **아키텍처 문제점**:
  1. **무의미한 ViewModel 계층**: `DashboardViewModel.kt`(1KB)는 `ScoreRepository`의 `StateFlow` 프로퍼티를 그대로 노출하는 단순 래퍼일 뿐이며, `StatisticsScreen`, `PresetModeScreen`, `AppWeightSettingsScreen`은 아예 ViewModel조차 없습니다.
  2. **UI 내부의 직접 DB/SharedPreferences 접근**: `DashboardScreen`과 `StatisticsScreen` 컴포저블 함수 본문에서 `val db = remember { DigitsDatabase.getInstance(context) }`를 호출하고, `LaunchedEffect`와 클릭 핸들러 안에서 직접 DAO 쿼리·트랜잭션·`SharedPreferences.edit()`를 실행합니다.
  3. **프로세스 재생성(Process Death) 시 빈 화면 노출 가능성**: `ScoreRepository`는 인메모리 싱글톤입니다. 안드로이드 시스템이 백그라운드에서 프로세스를 정리한 뒤 사용자가 최근 앱 목록에서 `MainActivity`를 복원하면, `TrackerForegroundService`가 첫 `recalculateAndNotify()` 루프를 끝내기 전까지 `ScoreRepository`의 `appsUsage`, `rollingUsageSummary`, `coreIndexGuidance`가 모두 초기 빈 값(`emptyList()`, `null`) 상태로 렌더링됩니다.
  4. **네이밍 혼란**: `GoalRewardState`의 필드명이 여전히 `availableFreePasses`로 되어 있는데 실제로는 '연속 기록 보호권(Streak Shield)' 개수를 저장하고 있어, 코드를 읽는 개발자나 AI 에이전트가 자유권과 보호권을 혼동하기 쉽습니다.

### 4.5. [심각도: 중상] 매 1분마다 발생하는 DB 쓰기 증폭(Write Amplification)과 백업 OOM 위험

* **위치**: `TrackerForegroundService.kt` (405~456행), `DataBackupManager.kt` (96~285행)
* **문제점 1 — 매 분 전체 세션 삭제 후 재삽입 (`replaceDay`)**:
  - `TrackerForegroundService`는 화면이 켜져 있는 동안 60초마다 `recalculateAndNotify()`를 실행합니다.
  - 이때 증분 조회로 추가된 마지막 1분치 세그먼트만 `INSERT/UPDATE`하는 것이 아니라, `db.dailyAppUsageDao().replaceDay(...)`와 `db.foregroundUsageSessionDao().replaceDay(currentDateString, sessionRecords)`를 호출해 **오늘 아침 05:00부터 쌓인 수백 개의 세션 행 전체를 매 분마다 `DELETE` 후 `INSERT`**합니다.
  - SQLCipher 암호화 DB 위에서 매 분마다 수백 건의 행을 지우고 다시 쓰는 것은 불필요한 Flash I/O와 CPU/배터리 소모를 유발합니다.
* **문제점 2 — 인메모리 전체 JSON 직렬화로 인한 백업/복원 시 OOM(Out-Of-Memory) 위험**:
  - `DataBackupManager.exportEncrypted()`는 최대 30일치 상세 세션(최대 20만 건), 30일치 5분 표본, 365일치 시간별/일별 통계를 모두 메모리에 올려 거대한 `JSONObject` 트리를 만든 뒤, `.toString()` → `.toByteArray(Charsets.UTF_8)` → `BackupCrypto.encrypt()`를 거칩니다.
  - 파일 상한을 `64MB`로 잡아두었지만, 실제로 15~20MB만 되어도 `JSONObject` 객체 그래프 + `String`(UTF-16) + 평문 `ByteArray` + 암호문 `ByteArray`가 동시에 힙(Heap)에 상주하면서 **150MB 이상의 피크 메모리**를 소모해 보급형 기기에서 `OutOfMemoryError`로 크래시가 날 수 있습니다. (`JsonWriter` + `CipherOutputStream` 스트리밍 방식으로 개편 필요)

### 4.6. [심각도: 중] 정규식(Regex) 80개에 의존하는 취약한 런타임 번역기 (`UiTranslator`)

* **위치**: `app/src/main/java/com/digitscore/app/i18n/LocalizedText.kt`
* **현상 및 문제점**:
  - 커스텀 `@Composable fun Text(...)`가 `UiTranslator.translate(text)`를 호출하며, 영어 모드일 때 **200여 개의 고정 문자열 치환과 80여 개의 `Regex` 매칭(`dynamicEnglishReplacements`)을 매번 순회**합니다.
  - **성능 문제**: `LazyColumn` 스크롤이나 게이지 애니메이션으로 리컴포지션이 일어날 때마다 화면의 모든 텍스트에 대해 수십 개의 정규식 매칭이 메인 스레드에서 실행됩니다.
  - **유지보수 취약성**: 한국어 UI 문구에서 조사 하나나 띄어쓰기, 단위(`회` ↔ `x`, `분` ↔ `min`)만 수정해도 정규식 매칭이 깨져 영어 화면에 한국어 문장이 그대로 노출되는 버그가 반복적으로 발생할 수밖에 없습니다. 실제로 `LocalizedText.kt` 안에는 이미 폐기된 구버전 문구(예: `"생산성 앱 보너스"`, `"부채 발동 점수"`, `"이전 사용량 이월"`) 번역 규칙 수십 개가 죽은 코드로 방치되어 있습니다.
  - 또한 `EdgeLightingOverlayManager.kt`(152행)는 `AppLocale.isEnglish(appContext)` 대신 `Locale.getDefault().language == "en"`을 사용하여, 시스템 언어가 한국어인 상태에서 앱 내 언어를 영어로 바꾼 경우 화면 신호등 배지가 한국어로 나오는 버그가 있습니다.

### 4.7. [심각도: 중] 빌드 스크립트의 부작용(Side Effect) 및 로컬 작업 폴더 보안 위생

* **위치**: `app/build.gradle.kts` (54~77행), 프로젝트 루트 디렉터리
* **문제점 1 — Gradle Configuration 단계의 파일 변조**:
  - `app/build.gradle.kts`는 태스크 실행 단계가 아닌 **구성(Configuration) 단계**에서 `assemble`/`bundle`/`install` 태스크 이름을 감지해 `version.properties` 파일의 `PATCH`나 `DEBUG_BUILD` 값을 자동으로 1 올리고 디스크에 덮어씁니다.
  - 개발자가 단순 빌드 확인이나 Android Studio 실행만 해도 Git 추적 파일인 `version.properties`가 변경되어 워킹 트리가 오염됩니다.
* **문제점 2 — 로컬 폴더 내 민감 파일 및 임시 파일 혼재**:
  - `.gitignore`로 커밋은 막혀 있으나, 현재 로컬 프로젝트 폴더 안에 **실제 Play 업로드 키스토어(`keystore/digitscore-upload.jks`)와 평문 비밀번호가 적힌 `local.properties`**가 함께 놓여 있습니다. AI 에이전트나 외부 스크립트가 폴더 전체에 접근하는 환경에서 업로드 키스토어와 비밀번호를 프로젝트 폴더 내부에 두는 것은 위험합니다.
  - 또한 루트 폴더에 릴리스 빌드 산출물 6개(`DigitsCore-v0.9.39~41-release.apk/.aab`, 약 60MB), 실제 백업 파일 2개(`*.dcorebackup`, 약 6MB), `stock_data.json`, 그리고 `Claude outputs/` 폴더(구버전 `.kt` 복사본 6개와 쉘 스크립트 23개)가 정리되지 않은 채 남아 있습니다.

## 5. UX 및 Google Play 스토어 심사·운영 리스크 평가

### 5.1. Google Play 심사 거절 가능성 (Policy Risks)

1. **`FOREGROUND_SERVICE_SPECIAL_USE` 심사 허들**:
   - Google Play는 Android 14+부터 `specialUse` FGS 권한에 대해 가장 엄격한 심사를 적용하며, 기존 FGS 타입(`health`, `dataSync` 등)으로 대체할 수 없음을 증명하는 시연 영상과 사유서를 요구합니다.
   - 심사관이 "왜 `UsageStatsManager`를 주기적으로 읽는 데 상시 FGS가 필요한가?"라고 이의를 제기할 수 있으므로, **"사용자가 직접 켠 동안에만 상단 상태바에 실시간 코어 지수 아이콘을 즉시 갱신하고 연속 사용 30분/50분 경계에서 즉시 개입(화면 신호등)하기 위해 상시 프로세스가 필수적임"**을 시연 영상에서 명확히 입증해야 합니다.
2. **동적 런처 아이콘(`<activity-alias>`) 전환의 런처 호환성 문제**:
   - `LauncherIconManager.kt`는 화면이 꺼졌을 때 하루 최대 1회 `setComponentEnabledSetting`으로 `MainActivityAliasYellow/Green/Orange/Red`를 교체합니다.
   - 기본값이 OFF로 되어 있는 것은 다행이나, 사용자가 이 옵션을 켰을 때 **Samsung One UI, Xiaomi MIUI, 일부 서드파티 런처에서는 기존 홈 화면 폴더나 독(Dock)에 꺼내둔 앱 아이콘이 사라지거나 첫 페이지 밖으로 튕겨 나가는 현상**이 발생합니다. 설정 토글 설명에 이 부작용을 더 명확히 경고해야 합니다.

### 5.2. 제품 UX 및 행동 유도 측면의 비판적 고찰

1. **초기 앱 등급 분류의 한국 시장 편향성 (`AppCategoryPolicy.kt`)**:
   - 기본 분류 목록(`NAVIGATION_PACKAGES`, `EDUCATION_PACKAGES`, `FINANCE_AND_WORK_PACKAGES`, `SHOPPING_PACKAGES`, `WEBTOON_PACKAGES`)이 티맵, 카카오내비, 토스, 배민/쿠팡, 무신사, 에이블리, 밀리의서재, 메가스터디 등 **한국 앱 위주로 하드코딩**되어 있습니다.
   - 영어권 사용자가 설치했을 때는 영미권 주요 은행(Chase, BofA, Revolut), 독서(Kindle, Audible, Libby), 업무(Asana, Linear, Jira), 쇼핑(Target, Walmart, Etsy) 앱 등이 `NEUTRAL(일반)`로 빠져 첫날 점수 정확도가 한국 사용자보다 크게 떨어집니다.
2. **'오늘의 목표'와 '코어 지수'의 이중 기준 혼선 가능성**:
   - 코어 지수 자체는 **75점**을 균형점으로 설계했는데, 일일 목표의 기본 지수 방어선은 **70점**(또는 사용자 설정)이고, 안정(Stable) 등급은 **80점 이상**입니다.
   - 사용자 입장에서는 `70점(방어선) · 75점(균형점) · 80점(안정 등급 시작)` 세 가지 기준점이 동시에 존재하여, *"오늘 몇 점 이상이어야 잘한 것인가?"*에 대해 미세한 인지 부하가 발생합니다.

## 6. 단계별 개선 권고안 (Actionable Roadmap)

### Phase A. 정식 출시(`v1.0`) 전 즉시 수정 권장 (Critical Fixes)

1. **`README.md`, `ARCHITECTURE.md`, `Agents.md` 문서 현행화**:
   - '15분 자유권' → '연속 기록 보호권 + 도룡뇽 4단계 성장'으로 수정.
   - `AndroidManifest.xml`에 없는 '신체 활동(걸음 센서)' 권한 표기 삭제.
   - 통계 화면 기간 설명(`24시간·4주·요일별`) 및 위젯 4종(`1x1·2x1·2x2·4x2`) 설명을 `v0.9.41` 실제 구현과 일치하도록 수정.
2. **`DataBackupManager` 백업 누락 항목 보완**:
   - `TimeRuleStore`(시간대 규칙), `PersonalBest`(내 기록), `DailyGoalStore`의 평일/주말 목표 설정 및 `goal_day_records_json`을 JSON 백업/복원 대상에 추가.
3. **`EdgeLightingOverlayManager` 언어 판별 버그 수정**:
   - `Locale.getDefault().language == "en"`을 `AppLocale.isEnglish(appContext)`로 교체해 앱 내 언어 설정과 일치시킴.
4. **로컬 작업 폴더 정리 및 키스토어 분리**:
   - `keystore/digitscore-upload.jks`를 프로젝트 폴더 외부의 안전한 경로(예: `~/.keystore/`)로 이동하고, 루트의 구버전 `.apk`/`.aab`, `Claude outputs/`, `stock_data.json` 삭제.

### Phase B. 단기 안정화 버전(`v1.0.x` ~ `v1.1`) 아키텍처 개선

1. **Android 15/16 부팅 복원 대응 (`BootCompletedReceiver`)**:
   - `startForegroundService()`가 `ForegroundServiceStartNotAllowedException`으로 실패할 경우, 즉시 `WorkManager` 1회성 작업이나 부팅 알림("탭하여 코어 지수 추적 재개")을 발송해 추적이 몰래 중단된 채 방치되지 않도록 보완.
2. **매 분 `replaceDay` 전체 덮어쓰기 최적화**:
   - `TrackerForegroundService`에서 매 분마다 오늘 전체 세션을 지우고 다시 쓰는 대신, 마지막 커서 이후 새로 닫힌 세그먼트와 현재 진행 중인 세그먼트만 `UPSERT`하도록 변경하여 DB I/O를 90% 이상 절감.
3. **평문 `SharedPreferences`의 암호화 DB 통합 및 레거시 정리**:
   - 민감한 앱 패키지명과 사용 시간이 담긴 `daily_goal_state` 등을 SQLCipher Room DB 내부로 이전하여 개인정보처리방침의 '전수 암호화' 약속을 기술적으로 완결.
   - `GoalRewardState.availableFreePasses` 등 구버전 변수명을 `availableShields`로 리팩터링하고 미사용 구형 계산기(`ScoreCalculator`, `RollingScoreCalculator`) 제거.

### Phase C. 중장기 리팩터링 (`v1.2+`)

1. **God File 분할 및 ViewModel 표준화**:
   - `DashboardScreen.kt`(225KB)를 카드·모달 단위의 하위 파일(`HeroScoreCard.kt`, `ScoreStatsRow.kt`, `DashboardModals.kt` 등)로 분리하고, UI 내 직접 DB 호출을 ViewModel + Repository 계층으로 이관.
2. **`UiTranslator` 정규식 번역기 제거 → 표준 Android String Resources(`strings.xml`) 전환**:
   - 런타임 정규식 치환을 점진적으로 제거하고 `stringResource(R.string.*, ...)` 포맷 문자열로 일원화하여 리컴포지션 성능과 번역 안정성 확보.
3. **백업/복원 스트리밍 처리**:
   - `DataBackupManager`와 `BackupCrypto`에 `JsonWriter`/`JsonReader` 및 `CipherOutputStream`/`CipherInputStream`을 적용해 대용량 백업 시 메모리 피크를 제거.

---

## 7. Claude 답변 (2026-10-29)

표기: **반영** = 이번에 고침 · **확인 필요** = 사용자 결정 후 진행 · **이후** = v1.0.x 이후 일정 · **이견** = 지적과 다르게 판단

### 7.1. 항목별 답변

| 항목 | 판단 | 답변 |
| :--- | :---: | :--- |
| 4.1 부팅 시 FGS 차단 | **이견 + 반영** | Android 15에서 `BOOT_COMPLETED`로 시작이 막히는 FGS 타입은 `dataSync`·`camera`·`mediaPlayback`·`phoneCall`·`mediaProjection`·`microphone`입니다. `specialUse`는 이 목록에 없어 "재부팅 후 조용히 죽는다"는 진단은 현재 OS 기준으로는 맞지 않습니다. API 35 에뮬레이터 재부팅 시험으로 확인합니다(결과는 7.3). 다만 향후 정책 변경이나 제조사 제한으로 막힐 수 있으므로, 시작 실패 시 "탭하여 측정 재개" 알림을 띄우는 대비책을 넣었습니다(`ScoreNotificationManager.showTrackingResumeNeeded`). WorkManager 하이브리드 전환은 필요 없다고 봅니다. |
| 4.2 백업 누락 | **반영** | 시간대 규칙, 평일/주말 목표, 지난 목표 결과(`goal_day_records_json`), 내 기록(`PersonalBest`), 목표 초과 선택 기록을 백업에 넣었습니다(`PrefsBackup.kt`, 루트 키 `userPrefs`, 값 타입 보존). 스누즈·재알림 횟수 같은 당일 임시 상태는 일부러 뺐습니다. 선택 필드라 백업 스키마 버전(11)은 올리지 않았고, 이전 백업도 그대로 복원됩니다. 테스트 `PrefsBackupTest` 추가. |
| 4.2 평문 SharedPreferences | **반영(문구) · 이후(DB 이전)** | 앱 데이터 영역은 다른 앱이 읽을 수 없고 OS 백업에서도 제외되어 실제 노출 위험은 낮지만, 방침의 "모든 데이터 암호화"는 사실과 달랐습니다. 사용자 결정에 따라 개인정보처리방침(md·html)·앱 내 안내·README를 "사용 기록 DB는 암호화, 목표·시간대 규칙·화면 설정 등 일부 값은 앱 전용 저장소"로 고치고 시행일을 2026-10-29로 갱신했습니다. 목표·내 기록 저장소의 DB 이전은 v1.0.x에 합니다. |
| 4.3 문서 불일치 | **반영** | README(보상, 통계 범위, 위젯 4종, 단위 표기, 걸음 센서 행 삭제), ARCHITECTURE(DB v20, 4단계 등급, 런처 아이콘, 위젯), Agents.md(48시간 보관, 위젯, 단위, 통계), SCORING(자유권 면제 문단), GLOSSARY(보호권)를 코드에 맞췄습니다. |
| 4.4 God File · ViewModel | **이후** | 지적에 동의합니다. 출시 3일 전 대규모 분할은 회귀 위험이 커서 v1.1에 화면 단위로 나눕니다. `availableFreePasses` 이름은 이번에 `streakShields`로 바꿨습니다(저장 키·백업 JSON 키는 호환을 위해 유지). |
| 미사용 구형 계산기 | **이견** | `RollingScoreCalculator`는 `CoreIndexCoach`(심야 배율·회복 예측)와 `CoreIndexHistoryRepair`(과거 기록 복원)에서 아직 쓰고 있어 지울 수 없습니다. `ScoreCalculator`는 테스트·벤치마크 전용인지 v1.0.x에 정리합니다. |
| 4.5 매 분 `replaceDay` | **이후** | 동의합니다. 화면이 켜진 동안 오늘 세션 전체를 다시 쓰는 구조라 v1.0.x에서 변경분 UPSERT로 바꿉니다. 실측 배터리 영향부터 기록합니다. |
| 4.5 백업 OOM | **이후** | 30일 상세 세션 기준 실제 백업 크기를 먼저 재고, 10MB를 넘으면 스트리밍(JsonWriter + CipherOutputStream)으로 바꿉니다. |
| 4.6 Locale 버그 | **반영** | 화면 신호등 배지가 앱 언어(`AppLocale.isEnglish`)를 따르도록 고쳤습니다. 아침 팝업의 "Shield x2"도 단위 규칙에 맞게 "Shields 2"로 바꿨습니다. |
| 4.6 정규식 번역기 | **이후** | 동의합니다. 새 문구는 `strings.xml`로만 추가하고, 기존 규칙은 화면 단위로 옮기면서 죽은 규칙을 지웁니다(v1.1~). |
| 4.7 Gradle 버전 자동 증가 | **확인 필요** | 의도된 동작입니다(릴리스마다 PATCH·VERSION_CODE 누락 방지). 작업 트리 오염은 커밋 전에 되돌리는 방식으로 운영 중입니다. 릴리스 스크립트에서만 올리도록 바꿀 수 있습니다. |
| 4.7 키스토어 위치 | **반영** | 업로드 키스토어를 `~/.keystore/digitscore-upload.jks`로, 서명 비밀번호를 `~/.keystore/digitscore-upload.properties`로 옮겼습니다. `local.properties`에는 `sdk.dir`만 남습니다(원본은 `~/.keystore/local.properties.bak`). Gradle은 환경변수 → `~/.keystore` → 이전 경로 순으로 읽고, 이동 후 release 서명 지문이 같은지 확인합니다. |
| 4.7 루트 잡동사니 | **반영(이전 작업)** | 구버전 빌드·백업·`Claude outputs`·`stock_data.json`은 이미 `archive/`(개인 파일은 git 제외 `archive/local/`)로 옮겼습니다. 루트에는 현재 릴리스(v0.9.41) 산출물만 남아 있습니다. |
| 5.1 specialUse 심사 | **이후(제출 준비)** | 동의합니다. 시연 영상에 "사용자가 켠 동안만 상태바 실시간 지수 + 30분 경계 즉시 개입"을 담고, `PLAY_CONSOLE_SUBMISSION.md`에 사유서 문안을 둡니다. |
| 5.1 동적 아이콘 부작용 | **이미 반영** | 설정 토글 설명에 "일부 런처는 홈 화면 바로가기를 지울 수 있습니다"가 이미 있고 기본값은 꺼짐입니다. |
| 5.2 한국 앱 편향 | **반영(확인 대기)** | 영어권 독서·학습(Kindle, Audible, Libby, Google Play 북 등 9개), 금융·업무(Chase, BofA, Wells Fargo, Revolut, PayPal, Asana, Jira, Trello 등 20개), 쇼핑(Walmart, Target, Etsy, Best Buy, SHEIN) 기본 분류를 추가했습니다. 함께 발견한 버그: 패키지 비교가 대소문자를 구분해 `com.locnall.KimGiSa`(카카오내비)·`GoogleCamera`가 기본 분류되지 않았고, Coursera 패키지명이 `com.coursera`로 잘못돼 있었습니다(실제 `org.coursera`). 목록 확인 후 커밋합니다. |
| 5.2 70·75·80 기준 혼선 | **반영** | 화면에는 방어선(기본 70)과 등급(80 안정)만 남겼습니다. 온보딩의 "75P 균형 지수", 지수 도움말의 "75에서 시작", 통계 차트 기준선의 75 기본값(→ 기본 방어선 70)을 정리했습니다. 계산은 바뀌지 않습니다. |

### 7.2. 사용자 결정 (2026-10-29)

1. 개인정보 문구: 지금 사실대로 수정, DB 이전은 v1.0.x → 반영
2. 키스토어: 프로젝트 밖으로 이동 → 반영
3. 영어권 앱 기본 분류: 추가 → 반영(목록 확인 후 커밋)
4. 화면 기준: 방어선·등급만 표시 → 반영
5. Gradle 버전 자동 증가: 미정(현재 유지)

### 7.3. 검증

- 단위 테스트 282개 통과, 디자인 점검 통과(이번 수정분 포함 재확인 예정)
- 재부팅 시험(API 35): 진행 중
