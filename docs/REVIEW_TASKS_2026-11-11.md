# DigitsCore 검토 후속 작업 지시서 (2026-11-11)

이 문서는 2026-11-11 전체 검토에서 나온 작업을 우선순위대로 정리한 것입니다.
작업자(Claude, Gemini)는 이 문서만 읽고 시작할 수 있어야 합니다. 두 작업자가 같은 브랜치에서 **서로 다른 파일**을 고칩니다.

## 0. 공통 배경과 규칙

- 저장소: `/Users/jinwoo/Documents/Antigravity/DigitsCore`, 브랜치 `feature/core-index-focus` (`main`은 건드리지 않음)
- 현재 배포: v0.9.45 (versionCode 945). Play 공개 테스트(178개국)에 올려 검토 중이고, GitHub Release에도 게시됨
- 앱: Android(Kotlin, Compose, Room + SQLCipher). 화면이 켜진 동안 전면 앱을 측정해 1~99점 '코어 지수'로 보여 줌. 인터넷 권한 없음
- 하루의 기준은 **05:00** (논리적 날짜). 00:00~04:59는 전날로 침

**빌드·테스트**
- `export JAVA_HOME=/opt/homebrew/opt/openjdk@17`, Gradle 실행 전 `./gradlew --stop; sleep 3`
- 단위 테스트: `./gradlew :app:testDebugUnitTest --no-daemon -q` (현재 287개 통과)
- 디자인 점검: `STRICT=1 bash scripts/design_check.sh` (통과해야 함)
- `assemble*`/`bundle*`/`install*` 작업은 `version.properties`를 자동으로 올림. 디버그 빌드 뒤에는 `git checkout -- version.properties`
- 에뮬레이터: `bash scripts/dev/tasks/start_emulator.sh` 후 `bash scripts/dev/tasks/build_install.sh`. 조작 도우미는 `scripts/dev/lib.sh`

**작업 규칙**
- 자기 트랙의 파일만 고침. 다른 트랙 파일을 고쳐야 하면 멈추고 사용자에게 알림
- 버전을 올리거나 릴리스 빌드·푸시·Play 업로드를 하지 않음. 릴리스는 두 트랙이 끝난 뒤 사용자가 지시
- 커밋은 작업 ID 단위로. 작성자 `박진우 <jinwoo@bagjin-uui-MacBookAir.local>`, 메시지는 한국어, 끝에 Co-Authored-By 줄
- 삭제는 허락받고 함(아니면 `archive/`로 이동). 서명 키·비밀번호는 출력·커밋 금지
- 화면 글자: 컬러 이모지 금지, 단위는 시간 `min`·점수 `P`·횟수는 한국어 `회`/영어 숫자만. 새 문구는 `strings.xml`에 추가하고 용어는 `docs/GLOSSARY.md`를 따름
- 고친 것마다 "무엇을 어떻게 확인했는지"를 보고. 확인 못 한 것은 못 했다고 적음
- 아래 '확인 상태'가 **코드 확인**인 항목은 기기에서 재현한 것이 아님. 고치기 전에 해당 코드를 직접 읽고 진단이 맞는지 먼저 확인할 것

## 1. 사용자 결정 (2026-11-14 확정)

| ID | 결정 | 걸린 작업 |
|---|---|---|
| D1 | P0 수정이 끝나는 대로 0.9.46을 바로 올림(진행 중인 Play 검토가 다시 시작되는 것은 감수) | 릴리스 |
| D2 | 백업 비밀번호는 **4자 이상 그대로**. 길이는 사용자가 고름. 대신 비밀번호 입력 칸의 안내 글이 두 줄로 넘어가는 것을 고침 | B5 |
| D3 | Play 대상 연령: **미정**(유지할 때의 영향을 확인한 뒤 사용자가 결정) | B8 |
| D4 | 잠금 화면이 없는 기기: 먼저 0회가 맞는지 확인하고, 맞으면 보안 잠금이 없을 때만 '화면 켜짐'을 잠금 해제로 셈 | B3 |
| D5 | 영어 용어: 지수 방어선 = "Index floor", 등급 = "Study / Tool / General / Managed", 설정 탭 "Scoring" = "Index" | C1 |
| D6 | 서비스가 죽었을 때 되살리는 15분 점검(WorkManager)을 넣음 | B2 |
| D7 | `ACCESS_NETWORK_STATE`는 빼지 않고 개인정보처리방침에 설명만 추가 | B7 |
| D8 | CI는 `feature/**`도 봄. `main` 병합은 0.9.46 뒤 | A3, D-3 |
| D9 | 측정이 멈추면 홈 배너 + 권한이 꺼졌을 때만 알림 1회 | B1 |
| D10 | 죽은 코드 삭제는 1.0 뒤로 | C7 |
| D11 | 의존성 올리기는 1.0 뒤로 | — |
| D12 | 트랙 1(서비스·빌드) Claude, 트랙 2(화면·문서) Gemini | 전체 |

## 2. 트랙 1 — 서비스·측정·빌드·CI

**이 트랙만 고치는 파일:** `service/TrackerForegroundService.kt`, `data/UsageStatsHelper.kt`, `data/ForegroundUsageAggregator.kt`, `receiver/*`, `widget/WidgetSnapshotStore.kt`, `i18n/AppLocale.kt`, `app/build.gradle.kts`, `.github/workflows/build-apk.yml`, `app/src/test/**`, `app/src/main/res/values*/themes.xml`
(경로 앞부분 `app/src/main/java/com/digitscore/app/` 생략)

### P0 — 0.9.46에 들어가야 함

**A1. 05:00 직후 어제 기록이 덮어써짐** · 확인 상태: 코드 확인
- 문제: `recalculateAndNotify()`가 날짜 문자열은 필드 `currentDateString`에서, 하루 시작 시각은 `UsageStatsHelper.getStartOfTodayMillis()`에서 따로 얻음. `currentDateString`은 `checkDateRollover()`가 불릴 때만 바뀌는데, `onScreenTurnedOff`, `onConfigurationChanged`, `onStartCommand`, `restoreTodayHistory`는 이를 부르지 않고 재계산함
- 결과: 화면을 켠 채 05:00을 넘기고 다음 1분 루프 전에 위 경로로 재계산되면, 어제 날짜로 `dailyAppUsageDao().replaceDay`, `foregroundUsageSessionDao().replaceDay`, `insertOrUpdateScoreHistory`, 표본 저장이 실행되어 어제 기록이 오늘 몇 초치로 바뀜. 어제 목표 판정·연속 기록·내 기록(잠금 해제 최소)까지 틀어짐
- 고칠 방향: 주기 시작에서 `now`로 논리적 날짜와 하루 시작을 **한 번에** 계산해 주기 끝까지 그 값만 씀. 필드와 다르면 주기 안에서 롤오버 처리(캐시 초기화)를 함. 같은 주기 안의 `DailyGoalStore.getLogicalDateString()` 직접 호출(624행, 901행 근처)도 같은 값으로 통일
- 완료 기준: 날짜·시작 시각 계산을 순수 함수로 빼고, "04:59:50에 시작한 화면 사용 중 05:00:20에 화면 끄기" 경우 어제 행이 바뀌지 않는다는 단위 테스트가 있음. 기존 테스트 전부 통과

**A2. Play 설치본에서 영어 화면에 한국어가 섞임** · 확인 상태: lint 경고(`AppBundleLocaleChanges`) + AAB BundleConfig 확인, 실기기 재현 안 함
- 문제: 기본 리소스 `res/values/`가 한국어, 영어는 `values-en/`. 앱은 자체 언어 설정으로 로캘을 바꾸는데 AAB 언어 분할이 켜져 있어, 기기 언어 목록에 영어가 없으면 `values-en`이 설치되지 않음
- 고칠 방향: `app/build.gradle.kts`의 `android { }` 안에 `bundle { language { enableSplit = false } }`
- 완료 기준: `:app:bundleRelease`로 만든 AAB의 `BundleConfig.pb`에 언어 분할 해제가 들어감(가능하면 bundletool로 한국어 전용 기기 스펙의 APK 세트에 영어 문자열이 포함되는지 확인). 릴리스 빌드는 버전을 올리므로 확인 뒤 `git checkout -- version.properties`

**A3. CI가 계속 실패** · 확인 상태: 최신 실행(v0.9.45) 로그 확인
- 문제 1: `UiTranslatorTest.koreanModeKeepsOriginalText`가 영어 로캘 JVM에서 실패. `AppLocale.isCurrentEnglish`가 클래스 로딩 때 `Locale.getDefault()`로 한 번 정해져, 테스트가 `Locale.setDefault(KOREAN)`을 해도 반영되지 않음
- 문제 2: 워크플로 `on.push.branches`가 `feat/**`라 `feature/**` 푸시에는 돌지 않음
- 고칠 방향: 테스트에서 `AppLocale.isCurrentEnglish`를 직접 설정·복원(같은 파일의 다른 테스트도 같은 전제인지 확인). 브랜치 필터에 `feature/**` 추가(D8). `-Duser.language=en`으로 로컬에서 재현 후 수정 확인
- 완료 기준: `./gradlew :app:testDebugUnitTest -Duser.language=en -Duser.country=US`와 기본 로캘 둘 다 통과. 푸시 뒤 Actions가 초록인지는 사용자가 푸시를 허락한 뒤 확인

**A4. 잠금 해제 상세의 시간대 그래프 구간이 틀림** · 확인 상태: 코드 확인
- 문제: `UsageStatsHelper.getUnlockInsights()`가 `(timestamp - start) * 24 / (end - start)`로 24칸을 만듦. `start`는 오늘 05:00, `end`는 지금이라 한 칸이 1시간이 아님. 화면(`DashboardScreen.kt`의 `rollingHourLabel`, `RollingHourlyAxisLabels`)은 한 칸을 1시간으로 보고 라벨을 붙임
- 고칠 방향: 칸을 `start`부터 1시간 단위로 고정(`(timestamp - start) / 3_600_000`, 0~23). 화면 코드는 건드리지 않음(트랙 2 파일). `getAppUsageInsights()`는 이미 이 방식
- 완료 기준: "start=05:00, now=11:00, 잠금 해제 10:50 → 5번 칸" 단위 테스트

**A5. 공유 시트가 앱 사용으로 집계됨** · 확인 상태: 에뮬레이터(Android 15) 화면에서 "IntentResolver 1min" 확인
- 고칠 방향: `TRANSIENT_SYSTEM_OVERLAY_PACKAGES`에 `com.android.intentresolver` 추가
- 완료 기준: `UsageStatsHelperTest`에 제외 확인 추가. 에뮬레이터에서 홈의 공유 버튼을 누른 뒤 앱 목록에 IntentResolver가 생기지 않음

### P1 — 0.9.46 또는 0.9.47

**B2. 서비스가 죽으면 되살릴 장치가 없음** (D6) · 코드 확인
- 방향: 15분 주기 WorkManager 작업으로 "추적 켜짐 + 권한 있음 + 서비스 꺼짐"이면 재시작 시도, 막히면 `ScoreNotificationManager.showTrackingResumeNeeded()` 호출. WorkManager는 Glance가 이미 끌어옴(의존성 추가 여부 확인)

**B1-알림. 권한이 꺼지면 알림 1회** (D9) · 코드 확인
- 방향: 재계산 주기에서 사용 정보 접근 권한이 없으면 하루 1회 "측정이 멈췄습니다. 권한을 다시 켜 주세요" 알림. 권한이 돌아오면 알림을 지움

**B4. 월요일에 주간 리포트 자료가 없으면 매 분 다시 조회** · 코드 확인
- 방향: `checkAndSendWeeklyReport()`에서 자료 없음도 그 주 처리 완료로 기록하거나, 재시도를 시간당 1회로 제한

**B9. 서비스 상태 변수의 스레드 경합** · 코드 확인
- 문제: `cachedTodaySnapshot`, `lastUsageQueryEndMillis`, `currentDateString`, `recalcJob` 등을 메인 스레드(리시버)와 계산 코루틴이 동기화 없이 바꿈. `recalculateAndNotify(immediate=false)`가 진행 중인 주기를 취소해 '잠금 해제 후 처음 연 앱' 수가 두 번 세어질 수 있음
- 방향: 상태 변경을 전부 `recalcMutex` 안으로 옮기거나 단일 스레드 디스패처로 한정. A1을 고칠 때 함께 정리하면 좋음

**B10. DB를 못 열 때 서비스·부팅 리시버가 죽음** · 코드 확인
- 방향: `DigitsDatabase.getInstance()` 실패를 잡아 서비스는 조용히 멈추고 '앱을 열어 복구' 알림을 띄움. `BootCompletedReceiver`도 같음

**B3. 잠금 화면이 없는 기기의 잠금 해제 0회** (D4) · 추정
- 먼저 잠금 없음으로 설정한 에뮬레이터에서 실제로 0회인지 확인하고 결과를 보고

### P2 — 여유 있을 때

- **B12.** 일별 기록의 '심야 사용'이 05~10시를 담음. `getUsageSnapshotForRange()`의 `lateNightEndTime = startTime + 5h`가 하루 시작이 자정이던 때의 계산. 실제 00~05시(논리적 하루의 마지막 5시간)로 고치거나 필드를 쓰지 않는다고 명시. 지금 점수에는 영향 없음
- **C5.** 위젯 기본 방어선 75 → 70 (`TrackerForegroundService.kt` 1019행 근처, `WidgetSnapshotStore.kt` 기본값)
- **C6.** `buildSnapshot`/`resolveCategory`가 매 분 앱마다 `PackageManager`를 조회. 패키지별 캐시 추가
- **B11a.** lint 오류 4건: `themes.xml`의 `windowLightNavigationBar`(API 27), `forceDarkAllowed`(API 29)를 `values-v27`/`values-v29`로 옮김
- **C8.** 버전 코드 규칙: 1.x에서 PATCH 99·MINOR 9 초과 시 겹침. 0.x처럼 빌드를 막는 검사 추가

## 3. 트랙 2 — 화면·문구·문서

**이 트랙만 고치는 파일:** `ui/**`, `share/IndexShareCard.kt`, `res/values/strings.xml`, `res/values-en/strings.xml`, `i18n/LocalizedText.kt`, `README.md`, `PRIVACY_POLICY.md`, `docs/**`(이 문서 제외), `Agents.md`, `HANDOFF.md`

### P0 — 0.9.46에 들어가야 함

**B6. 남아 있는 "전부 암호화" 문구** · 확인 상태: 파일 확인
- `strings.xml`(ko/en)의 `privacy_secure_badge`: "100% 기기 내 암호화 · 외부 전송 없음" → 사실대로("사용 기록은 기기 안에 암호화 · 외부 전송 없음" 수준). 영어 문자열의 `%` 뒤 공백도 lint `StringFormatCount` 경고 원인이니 함께 정리
- `docs/index.html` 168행: "모든 데이터는 … 암호화" → `docs/privacy.html` 168행과 같은 문장으로
- 완료 기준: 저장소 전체에서 "모든 데이터.*암호화", "100%.*암호화" 검색 결과 없음

### P1

**B1. 측정이 멈춰도 홈에 표시가 없음** (D9) · 코드 확인
- 문제: 사용 정보 접근 권한이 꺼지거나 추적이 꺼져도 `DashboardScreen`에 경고가 없음. 표본이 2개 미만이면 "실시간 추적 진행 중"이 나옴. `DashboardViewModel.isServiceRunning`은 있지만 화면에서 쓰지 않음
- 방향: 홈 맨 위에 상태 줄 하나. 경우는 셋 — 권한 없음(설정으로 이동), 추적 꺼짐(켜기), 서비스 멈춤(다시 시작). 화면에 돌아올 때(`ON_RESUME`) 권한을 다시 확인. "실시간 추적 진행 중"은 실제로 서비스가 돌 때만
- 권한이 꺼졌을 때의 알림 1회는 서비스 쪽 작업이라 트랙 1이 맡음(트랙 1의 B1-알림)

**B5. 백업 비밀번호 입력 칸의 안내 글 줄바꿈** (D2) · 에뮬레이터 화면에서 확인
- `ui/settings/PresetModeScreen.kt` 2172행 근처. 최소 길이 `password.length >= 4`는 **그대로 둠**(길이는 사용자 선택)
- 문제: 입력 칸 라벨 "암호화 비밀번호 · 4자 이상 (숫자 4자리 등)"(영어 "Password · 4+ characters (e.g. 4-digit PIN)")이 칸 안에서 두 줄로 넘어감
- 방향: 라벨은 짧게("비밀번호" / "Password") 두고, 조건은 칸 아래 보조 글(`supportingText`)로 한 줄("4자 이상" / "4+ characters"). 글꼴 130%·영어에서도 한 줄인지 확인
- 완료 기준: 한국어·영어 × 글꼴 100%·130%에서 라벨과 보조 글이 각각 한 줄

**B13. 앱 목록 화면과 홈의 숫자가 다름** · 에뮬레이터에서 증상 확인, 원인은 코드 확인
- 문제: `ui/settings/AppWeightSettingsScreen.kt`가 `UsageStatsHelper.getInstalledApps()`(기기 실시간 조회, 설치된 앱만)를 씀. 홈은 DB. 복원한 날·삭제한 앱이 있으면 사용 시간이 0min으로 나옴
- 방향: 오늘 사용 시간은 DB(`dailyAppUsageDao`의 오늘 행)에서 읽어 합침. `UsageStatsHelper.kt`는 트랙 1 파일이므로 고치지 말고 화면 쪽에서 합칠 것

**C1. 영어 용어 통일** (D5) · 화면 캡처로 확인
- 지수 방어선이 목표 화면 "Score floor", 통계 범례 "Defense line"으로 다름
- 앱 등급이 앱 목록 "Study / Tool / Daily / Limit", 다른 화면 "General / Managed"로 다름
- 결정된 용어를 `docs/GLOSSARY.md`에 영어 열로 추가하고 화면·알림·위젯 전체에 적용. 스토어 문구(`docs/store-listing/play_listing.md`)도 같은 용어인지 확인

### P2

- **C2.** 홈 추세 그래프에서 최고·최저가 같은 값이면 라벨 하나만 표시
- **C3.** 홈의 도움말·공유 아이콘 터치 영역 18dp → 눌리는 영역 48dp(아이콘 크기는 그대로)
- **C4.** `IndexShareCard.share()`가 메인 스레드에서 1080×1080 이미지를 만들고 PNG로 저장. `Dispatchers.Default/IO`로 옮기고 버튼 중복 누름 방지
- **C9.** 영어 130% 글꼴에서 요약 칸 "Longest rest"가 잘림. 꺾쇠는 세 칸이 같은 규칙으로 보이게(한 칸이라도 잘리면 모두 숨김)
- **B7.** `PRIVACY_POLICY.md`·`docs/privacy.html`의 권한 설명에 "위젯 갱신 라이브러리가 '네트워크 연결 상태 보기'와 '절전 모드 해제' 권한을 포함하지만 앱은 인터넷 권한이 없어 전송하지 않음" 한 줄 추가(D7)
- **D-1. README 갱신:** 142·171·186행의 "debug APK", "정식 서명 파일 미공개" 삭제, 31행 "걸음 보조 판단" 삭제(해당 코드 없음), "GitHub APK(업로드 키 서명)와 Play 설치본(Play 서명)은 서로 업데이트되지 않음" 추가
- **D-2. 현황 문서 갱신:** `docs/RELEASE_READINESS.md`(기준일 9월 14일, 5·6번 항목 완료 처리), `Agents.md`의 CI 설명(DB 이전 검증은 CI에 없음), `HANDOFF.md`를 현재 상태로 다시 씀
- **C7.** 죽은 코드 정리는 1.0 뒤(D10). 지금은 하지 않음

## 4. 사용자가 직접 할 일

- **B8.** Play Console → 앱 콘텐츠 → 대상 연령 확인·변경(D3)
- 검토가 끝나면 앱 이름 변경(`docs/store-listing/play_listing.md`의 새 이름) 적용
- Play Console '판매자 상태 선언'(기한 2027년 2월)
- 소스 폴더의 `.fuse_hidden*` 파일 8개를 지울지 `archive/`로 옮길지 결정
- **D-3.** `main` 병합(D8)

## 5. 검토하지 못한 영역 (작업 중 이상하면 보고)

- 화면 코드 대부분: `DashboardScreen.kt`(4,468줄), `PresetModeScreen.kt`, `StatisticsExplorer.kt`, `MorningBriefingDialog.kt`는 줄 단위로 읽지 않음
- 점수 수식의 타당성, DB 마이그레이션, 계측 테스트 5개(실행하지 않음)
- 실기기(Samsung) 동작

## 6. 끝난 뒤 보고 형식

작업 ID별로: 바꾼 파일, 진단이 맞았는지(틀렸으면 실제 원인), 확인 방법과 결과, 남은 위험. 두 트랙이 끝나면 사용자가 0.9.46 릴리스를 지시합니다.
