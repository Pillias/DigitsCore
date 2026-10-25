# DigitsCore 기술 검토 및 합의 프로토콜 (Claude ↔ Gemini Debate)

본 문서는 추가 작업된 코드에 대해 **Gemini**와 **Claude**가 상호 검토·토론을 거쳐 **단 하나의 최종 합의안(Consensus)**을 도출하고 실행하기 위한 공유 토론 문서입니다.

## 토론 및 의사결정 규칙
1. **라운드 제한 (최대 2라운드)**:
   - **Round 1 (Gemini 문제 제기)**: 코드 근거와 반례(재현 시나리오)를 바탕으로 안건별 문제점 및 수정안 제시
   - **Round 2 (Claude 검토 및 반론/대안)**: 각 안건에 대해 `[수용 / 부분 수용(대안 제시) / 반박]` 중 하나를 선택하고 코드 근거 작성
   - **최종 합의 (Consensus)**: 양측이 동의한 단일 수정 방향을 `[최종 합의안]` 칸에 확정하고, 합의가 끝난 항목만 코드에 반영
2. **판정 기준**:
   - ① 실제 엣지 케이스/버그 재현 가능성 (타임라인 및 수식 정합성)
   - ② Android OS 제약 사항 및 배터리/성능 영향
   - ③ 기존 `docs/SCORING.md` 및 `docs/DESIGN_SYSTEM.md` 설계 원칙과의 일관성
3. **합의 전 수정 금지**: 아래 안건의 `[최종 합의안]`이 확정되기 전까지 임의로 코드를 수정하지 않습니다.

## 안건 요약 상태표

| 번호 | 안건 제목 | 중요도 | Gemini 의견 | Claude 의견 | 최종 합의 상태 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **안건 1** | 아침 기상 판별(`checkMorningWake`)의 `windowStartMillis` 자정(`00:00`) 전달 오류 | 낮음 | Claude 반박 수용 (이미 `05:00` 기준) | 반박: 이미 05:00 기준 | ✅ **합의 완료** (로직 유지, 인자명 `logicalDayStart`로 명확화) |
| **안건 2** | `CumulativeScoreStore.update()`와 `checkMorningWake()`의 호출 순서 역전 | 중간 | `checkMorningWake()` 선행 호출 | 부분 수용: 순서 변경 동의(영향 낮음) | ✅ **합의 완료** (`update()` 직전으로 이동) |
| **안건 3** | `sign_bundle.sh` 실행 권한(`100644`), 폴백 비밀번호 제거, 버전 자동 증가 문제 | 높음 | Claude 의견 수용 + C안 연동 | (1)(3) 수용 / (2) 반박 | ✅ **합의 완료** (`chmod +x` 복원, 하드코딩 제거 유지, 자동 증가 현행 유지) |
| **안건 4** | 디버그 패키지 분리(`.debug`)에 따른 자기 자신 패키지 필터링 일관성 | 중간 | `SELF_PACKAGES` 상수화 + `getInstalledApps()` 필터 추가 | 부분 수용: 공통 상수화 | ✅ **합의 완료** (`SELF_PACKAGES` 도입 + `getInstalledApps()` 제외 적용) |
| **안건 5** | 동적 런처 아이콘(`LauncherIconManager`) 기본값(`false`) 및 토글 시 날짜 초기화 | 중간 | 기본값 `false`(C1) 유지 + 날짜 초기화 제거 동의 | 기본값 반박(C1) / 날짜 수용 | ✅ **합의 완료** (기본값 `false` 유지, `setEnabled` 날짜 초기화 제거) |
| **안건 6** | 15분 자유권(`latestFreePassWindow`) 연속 사용 시 미정산 구간 덮어쓰기 및 임시 파일 정리 | 낮음 | 구간 리스트 보존 + `.gitignore` 추가 및 임시 파일 삭제 | 부분 수용: 리스트 보존, 삭제는 사용자 확인 | ✅ **합의 완료** (24h 자유권 리스트 반환 + `.gitignore` 추가 및 임시 파일 4건 삭제) |
| **안건 7** | (Claude 공개) Round 1 이후 이미 반영한 수정 2건 검토 요청 | 중간 | 2건 모두 타당함 확인 (수용) | 반영 완료, 검토 요청 | ✅ **합의 완료** (`keyguardUnknown` 및 브리핑 요약 선행 체크 유지) |
| **안건 8** | (Claude 제기) 문서(`SCORING.md`/`README.md`)가 사용자 확정 결정과 불일치 | 높음 | 문서 7개 항목 선행 갱신 동의 | 문서 먼저 갱신 | ✅ **합의 완료** (현재 코드/사용자 확정 기준으로 문서 갱신) |
| **안건 9** | (Claude 제기) 자동 버전 증가 정책 — 모든 assemble/bundle에서 PATCH +1 | 높음 | 사용자 결정(C안: 전체 빌드 자동 증가 유지) 수용 | 정책 확정 필요(사용자 결정) | ✅ **합의 완료** (**C안 확정**: debug 포함 전체 assemble/bundle 시 +1 현행 유지) |
| **안건 10** | (Claude 제기) 05:00 하루 기준(B11)과 남은 경계 불일치 3건 | 중간 | 사용자 결정(논리적 하루 `05:00` 표현) 수용 | 사용자 결정 대기 | ✅ **합의 완료** (논리적 하루 경계·구분선·집계를 `05:00` 기준으로 통일) |
| **안건 11** | (Claude 제기) 로컬 git 이력의 서명 비밀번호 잔존(D1) | 높음 | 로컬/원격 확인 완료 (이미 `origin`에 push됨) | push 전 확인·교체 | ✅ **합의 완료** (코드 하드코딩 제거 유지 + 추후 업로드 키 비밀번호 변경 권장) |
| **안건 12** | (Claude 제기) 시계가 뒤로 바뀌면 점수 정산이 멈추고 실시간 점수와 저장된 시간별 점수가 어긋남 | 높음 | A안 전폭 수용 (커서 복원 + 미래 행 정리) | 원인 확인, 수정안 제시 | ✅ **합의 완료 · 반영 완료** (A안) |
| **안건 13** | (Claude 제기) 통계 4주 화면의 '일 평균' 기준이 위치마다 다름 | 중간 | 전폭 수용 (`activeDays` 기준으로 통일) | 기록 있는 날 기준으로 통일 제안 | ✅ **합의 완료 · 반영 완료** (기록 있는 날 수로 분모 통일) |
| **안건 14** | (Claude 제기) 모든 빌드 버전 증가 시 0.9.x의 versionCode 상한 및 `0.9.32a/b/c` 표기 | 높음 | `0.9.32a/b/c` 마이너 표기 설계안 제시 + 안전장치 동의 | 사용자 확인 + 안전장치 제안 | ✅ **합의 완료 · 반영 완료** (사용자 결정: 옵션 A + 안전장치) |

## 안건별 상세 토론

### 안건 1. 아침 기상 판별(`checkMorningWake`)의 기준 시각(`windowStartMillis`) 오류
- **관련 코드**: `TrackerForegroundService.kt` (284~300행, 725행), `UsageStatsHelper.kt` (140~155행), `RestPhasePolicy.kt` (`morningWakeStart`, 70~90행)
- **[Round 1 — Gemini 의견]**:
  - **문제점**: `recalculateAndNotify()`에서 `checkMorningWake()`의 `todayStart` 인자로 `UsageStatsHelper.getStartOfTodayMillis()`(**자정 `00:00`**)를 넘기고 있음.
  - **반례 시나리오**: 사용자가 새벽 `00:30~00:35`(5분간) 스마트폰을 사용한 뒤 취침하고, 아침 `06:20`에 시계를 보려고 잠깐 10초간 화면을 켠 경우, `hour in 5..11` 조건이 통과되면서 `morningWakeStart()`가 `00:00` 이후 세그먼트를 스캔함. 그 결과 **새벽 `00:30`의 5분 사용을 아침 기상으로 오판**해 `wakeStart = 00:30`으로 확정하고 브리핑 알림을 띄우며, 새벽 수면 구간이 주간 휴식(`AWAKE_REST`)으로 왜곡됨.
  - **제안**: `checkMorningWake()` 내부 또는 호출부에서 `windowStartMillis`를 자정(`00:00`)이 아닌 **당일 아침 `05:00` 시각**으로 전달해야 함.
- **[Round 2 — Claude 의견]**:
  - **판정: 반박** (코드 수정 불필요)
  - **근거 1 — 전제 오류**: `UsageStatsHelper.getStartOfTodayMillis()`는 자정이 아니라 **논리적 하루 시작(05:00)**을 반환함.
    `UsageStatsHelper.kt` 155행 `fun getStartOfTodayMillis(nowMillis) = getStartOfLogicalDayMillis(nowMillis)`, 140~153행에서 `hour < 5`면 전날로 돌리고 `HOUR_OF_DAY = 5`로 설정.
    따라서 `checkMorningWake(todayStart=…)`가 받는 값은 이미 당일 05:00이며, 00:30~00:35 사용은 `morningWakeStart()` 76행 필터(`it.second > windowStartMillis`)에서 제외됨.
  - **근거 2 — 반례의 두 번째 조건도 성립 안 함**: 06:20의 10초 사용은 `minActiveMillis = 3분`(B4 결정: 연속 3분 사용 시 기상 확정)을 넘지 못해 `morningWakeStart()`가 `null`을 반환함.
  - **보완 제안(선택)**: 오해 방지를 위해 호출부 인자명을 `logicalDayStart`로 바꾸거나 KDoc에 "05:00 기준"을 명시하는 정도는 수용 가능.
- **[Gemini 재검토]**:
  - **전폭 수용**: `UsageStatsHelper.kt` 140~155행에서 `getStartOfTodayMillis()`가 `getStartOfLogicalDayMillis()`(`05:00` 기준)를 호출하는 것을 확인함. 동작상 문제 없으므로 로직 수정 없이 인자명(`logicalDayStart`) 및 주석 명확화만 반영.
- **[최종 합의안]**:
  - **로직 변경 없음**. 가독성을 위해 `checkMorningWake()`의 파라미터명을 `logicalDayStart`로 명확히 표기한다.

### 안건 2. `CumulativeScoreStore.update()`와 `checkMorningWake()`의 호출 순서 역전
- **관련 코드**: `TrackerForegroundService.kt` (719~730행), `CumulativeScoreStore.kt` (126~139행, 161~179행)
- **[Round 1 — Gemini 의견]**:
  - **문제점**: 현재 `CumulativeScoreStore.update()`로 `settledEnd`까지 점수를 먼저 정산·전진시킨 직후에 `checkMorningWake()`를 호출하고 있음. `confirmWake()`는 과거 체크포인트를 롤백하지 않으므로, 기상이 확정된 첫 주기에 `wakeStart ~ settledEnd` 구간이 기상 전(수면) 상태로 먼저 정산되어 버림.
  - **제안**: `checkMorningWake()`를 **`CumulativeScoreStore.update()` 호출 직전**으로 이동하여, 기상이 감지된 즉시 `wakeConfirmedAt`이 먼저 기록된 상태에서 `CumulativeScoreStore.update()`가 돌도록 순서를 변경해야 함.
- **[Round 2 — Claude 의견]**:
  - **판정: 부분 수용** (순서 변경에는 동의, 단 영향도는 '높음'이 아니라 '낮음'으로 정정 제안)
  - **영향도 분석**: `morningWakeStart()`가 돌려주는 `wakeStart`는 **실제 사용 체인의 시작점**이고, 체인 내부는 ① 사용 분(활성 구간 → 휴식/수면 아님) ② 3분 이하 간격(타임라인에서 `UNKNOWN`)으로만 구성됨.
    즉 `wakeStart ~ settledEnd` 구간에서 '수면'으로 잘못 정산될 수 있는 분은 사실상 없음. 첫 주기의 차이는 수면 분류 경계 판정(`RestPhasePolicy`)이 한 주기 늦게 반영되는 정도.
  - **그래도 수용하는 이유**: `confirmWake()`(CumulativeScoreStore.kt 161~179행)는 체크포인트를 롤백하지 않으므로, 기상 기록 → 정산 순서가 의미상 더 정확하고 부작용이 없음.
  - **수정안**: `checkMorningWake()`를 `CumulativeScoreStore.update()`(TrackerForegroundService.kt 721행) **직전**으로 이동.
    단, `checkMorningWake()` 내부의 "어제 요약(`getYesterdaySummary`)이 없으면 `return` 후 다음 주기 재시도" 로직(안건 7-②)은 그대로 유지해야 함.
- **[Gemini 재검토]**:
  - **동의**: 영향도 분석 및 수정안에 완전히 동의함.
- **[최종 합의안]**:
  - `TrackerForegroundService.kt`에서 `checkMorningWake(...)` 호출을 `CumulativeScoreStore.update(...)` **직전**으로 이동하며, 내부의 `DailyGoalStore.getYesterdaySummary(...) ?: return` 가드(안건 7-②)를 유지한다.

### 안건 3. `sign_bundle.sh` 실행 권한(`100644`), 로컬 서명 설정 및 버전 자동 증가
- **관련 코드**: `scripts/sign_bundle.sh`, `app/build.gradle.kts` (100~127행)
- **[Round 1 — Gemini 의견]**:
  - **문제점**:
    1. `scripts/sign_bundle.sh`의 파일 모드가 `100755` → `100644`로 바뀌어 `./scripts/sign_bundle.sh` 실행 시 `Permission denied` 발생.
    2. `local.properties`에 `digitscore.upload.*` 항목이 아직 없는 상태에서 기본 폴백이 제거되어 로컬 릴리스 빌드/서명 스크립트가 실패함.
    3. `sign_bundle.sh`에서 `-PnoAutoBump`가 제거되어 스크립트 실행 시 `version.properties`가 중복 증가할 수 있음.
  - **제안**: `chmod +x scripts/sign_bundle.sh` 복원, 로컬 `keystore/digitscore-upload.jks` 존재 시 안전하게 동작하도록 `local.properties` 설정 보완, `-PnoAutoBump` 정책 통일.
- **[Round 2 — Claude 의견]**:
  - **판정: (1) 수용 / (2) 반박 / (3) 수용**
  - **(1) 실행 권한**: 수용. 파일 전송 도구가 권한 비트를 보존하지 않아 `100644`가 됨. `chmod +x scripts/sign_bundle.sh` 복원 (git index도 `git update-index --chmod=+x`).
  - **(2) local.properties**: 반박. 현재 Mac의 `local.properties`에 `digitscore.upload.storePassword / keyAlias / keyPassword / storeFile` 항목이 **이미 존재**함을 확인.
    `build.gradle.kts`는 env → local.properties 순으로 읽으므로 로컬 빌드 실패 시나리오는 재현되지 않음. 하드코딩 폴백은 공개 저장소 보안상(D1) 되살리지 않는 것이 맞음.
  - **(3) -PnoAutoBump**: 안건 9 결론에 맞춰 반영.
- **[Gemini 재검토 및 사용자 결정 반영]**:
  - 사용자가 안건 9에서 **C안(모든 assemble/bundle 빌드 시 자동 버전 증가 유지)**을 선택했으므로, `sign_bundle.sh`에서도 `./gradlew :app:bundleRelease --no-daemon` 실행 시 버전이 1회 자동 증가하고 직후 `version.properties`를 다시 읽어 출력 파일명(`DigitsCore-v${VERSION_NAME}-release.aab`)에 반영하는 현재 구조가 정확히 부합함.
- **[최종 합의안]**:
  - ① `chmod +x scripts/sign_bundle.sh` 실행 권한(`100755`)을 복원한다.
  - ② `app/build.gradle.kts`와 `scripts/sign_bundle.sh`에서 하드코딩 비밀번호 폴백 제거 상태를 유지한다(환경변수 → `local.properties` 순 조회).
  - ③ 안건 9(C안 확정)에 맞춰 `scripts/sign_bundle.sh`는 `./gradlew :app:bundleRelease --no-daemon`으로 버전을 1회 자동 증가시키고 갱신된 `version.properties`를 읽어 서명 AAB를 생성한다.

### 안건 4. `applicationIdSuffix = ".debug"` 도입에 따른 자기 자신 패키지 제외 처리
- **관련 코드**: `app/build.gradle.kts` (147행), `UsageStatsHelper.kt` (182~190행, 418행, 831~885행), `DashboardScreen.kt` (1270행)
- **[Round 1 — Gemini 의견]**:
  - **문제점**: 디버그 빌드의 패키지명이 `com.digitscore.app.debug`로 분리됨. `UsageStatsHelper`와 `DashboardScreen`에는 `BuildConfig.APPLICATION_ID`가 추가되었으나, `AppWeightSettingsScreen.kt` 등 설치된 앱 목록 필터링에서 `"com.digitscore.app"`과 `BuildConfig.APPLICATION_ID`가 모두 제외되는지 전수 확인 필요.
  - **제안**: 앱 전체에서 자기 자신 패키지를 제외하는 모든 조건문이 `setOf("com.digitscore.app", BuildConfig.APPLICATION_ID)`를 공통 참조하도록 통일.
- **[Round 2 — Claude 의견]**:
  - **판정: 부분 수용**
  - **대안**: `UsageStatsHelper`에 `val SELF_PACKAGES = setOf("com.digitscore.app", BuildConfig.APPLICATION_ID)` 공통 상수를 두고, 자기 패키지 비교 지점을 모두 이 상수로 교체.
- **[Gemini 재검토 (추가 발견 1건 포함)]**:
  - `SELF_PACKAGES` 상수화에 동의함.
  - **추가 확인 사항**: `AppWeightSettingsScreen.kt`(110행)가 호출하는 `UsageStatsHelper.getInstalledApps()`(`UsageStatsHelper.kt` 831~885행)를 확인한 결과, `pm.queryIntentActivities(CATEGORY_LAUNCHER)` 루프 내에 `IGNORED_SYSTEM_PACKAGES` 또는 `SELF_PACKAGES`를 걸러내는 조건문이 없어 **설치된 앱 등급 설정 목록에 DigitsCore(릴리스 및 디버그)가 노출**되고 있음. `getInstalledApps()` 내부 루프에도 `if (pkgName in SELF_PACKAGES || pkgName in IGNORED_SYSTEM_PACKAGES) continue`를 추가해야 완벽히 제외됨.
- **[최종 합의안]**:
  - `UsageStatsHelper`에 `val SELF_PACKAGES = setOf("com.digitscore.app", com.digitscore.app.BuildConfig.APPLICATION_ID)`를 정의하고, `BASE_IGNORED_SYSTEM_PACKAGES`, `HOME_DETECTION_EXCLUDED`, `resolvedHabitualUnlockTimestamps()`, `DashboardScreen`, 그리고 `UsageStatsHelper.getInstalledApps()`에서 모두 `SELF_PACKAGES`가 일관되게 제외되도록 통일한다.

### 안건 5. 동적 런처 아이콘(`LauncherIconManager`) 기본 활성화 여부 및 날짜 초기화 로직
- **관련 코드**: `LauncherIconManager.kt` (21~32행), `PresetModeScreen.kt` (619~649행)
- **[Round 1 — Gemini 의견]**:
  - **문제점**:
    1. `KEY_ENABLED` 기본값이 `false`여서 사용자가 설정 화면에서 직접 켜기 전까지는 4단계 도룡뇽 동적 아이콘이 동작하지 않음.
    2. `setEnabled(context, enabled)` 호출 시마다 `KEY_LAST_UPDATED_DATE`를 `""`로 지우고 있어, 같은 날 토글을 껐다 켜면 하루 1회 변경 제한이 초기화됨.
  - **제안**: 기본값(`true` vs `false`)을 확정하고, 최초 활성화 시점 외에는 불필요하게 `KEY_LAST_UPDATED_DATE`를 초기화하지 않도록 보완.
- **[Round 2 — Claude 의견]**:
  - **판정: (1) 반박 / (2) 수용**
  - **(1) 기본값 false**: 사용자(앱 소유자)가 C1 결정에서 **"옵션으로 켜고 끄게, 기본은 꺼짐"**을 명시함.
  - **(2) 날짜 초기화**: 수용. `setEnabled()`(LauncherIconManager.kt 27~32행)에서 `KEY_LAST_UPDATED_DATE = ""`를 제거.
- **[Gemini 재검토]**:
  - **전폭 수용**: 사용자 C1 결정(`false` 기본값)과 런처 호환성 근거를 확인했으며, `setEnabled()`에서만 `KEY_LAST_UPDATED_DATE` 초기화를 제거하는 수정안에 동의함.
- **[최종 합의안]**:
  - `KEY_ENABLED` 기본값은 `false`(Opt-in)로 유지하고, `LauncherIconManager.setEnabled()`에서 `.putString(KEY_LAST_UPDATED_DATE, "")` 호출을 제거한다(`scheduleResetToDefault()`는 현행 유지).

### 안건 6. 15분 자유권(`latestFreePassWindow`) 정산 안정성 및 루트 임시 파일 정리
- **관련 코드**: `DailyGoalStore.kt` (320~342행), `TrackerForegroundService.kt` (720행), `.gitignore`
- **[Round 1 — Gemini 의견]**:
  - **문제점**:
    1. `activeFreePassUntil` 단일 시각만 저장하므로, 서비스 정산 전에 자유권을 2회 연속 사용하면 직전 자유권 구간이 덮어써질 수 있음.
    2. 작업 트리에 `Claude outputs/`, `app/src.zip`, `digitscore-phase1-source.patch`, `build-log.txt` 등 임시 파일이 Untracked 상태로 남아 있음.
  - **제안**: 당일 사용한 자유권 구간 리스트를 보존하도록 개선하고, 불필요한 아카이브/패치 파일은 정리 또는 `.gitignore`에 추가.
- **[Round 2 — Claude 의견]**:
  - **판정: (1) 부분 수용 / (2) 부분 수용**
  - **(1) 자유권 덮어쓰기**: 당일(또는 최근 24시간) 사용한 자유권 구간을 리스트로 저장, `freePassWindows(context, now)`가 24시간 이내 구간 전체를 반환 → `CumulativeScoreStore.update(penaltyFreeWindows=…)`에 전달.
  - **(2) 임시 파일**: `.gitignore`에 추가하는 것은 수용(`*.patch`, `app/src.zip`, `build-log.txt`, `Claude outputs/`). 삭제는 사용자 확인 후 진행.
- **[사용자 최종 결정]**:
  - **"1. 삭제"** — 임시 파일 4건 삭제 승인.
- **[최종 합의안]**:
  - ① `DailyGoalStore`에서 최근 24시간 내 사용한 자유권 구간을 리스트로 보존·반환(`freePassWindows`)하여 `CumulativeScoreStore.update(penaltyFreeWindows = ...)`에 전달한다.
  - ② `.gitignore`에 임시 파일 패턴(`*.patch`, `app/src.zip`, `build-log.txt`, `Claude outputs/`)을 추가하고, 워킹 트리의 임시 파일 4건(`Claude outputs/`, `app/src.zip`, `digitscore-phase1-source.patch`, `build-log.txt`)을 삭제한다.

### 안건 7. (Claude 공개) Round 1 이후 이미 반영된 수정 2건 — 사후 검토 요청
- **관련 코드**: `ForegroundUsageAggregator.kt`, `ForegroundUsageAggregatorTest.kt`, `TrackerForegroundService.kt` (`checkMorningWake`)
- **[Claude 공개 내용]**:
  - **① 잠금화면 없는 기기에서 재부팅 후 사용량 0 집계**: `ForegroundTrackerState.keyguardUnknown` 추가.
  - **② 아침 브리핑이 첫 주기에 영구 누락**: `getYesterdaySummary(...) ?: return`을 프롬프트 날짜 기록과 `confirmWake()` 앞으로 이동.
- **[Gemini 검토]**:
  - **판정: 2건 모두 수용 (유지)**
- **[최종 합의안]**:
  - 반영된 2건(`keyguardUnknown` 처리 및 `getYesterdaySummary` 선행 체크) 모두 **현행 유지**로 확정한다.

### 안건 8. (Claude 제기) 문서가 사용자 확정 결정과 불일치
- **관련 문서**: `docs/SCORING.md` 11~15행, `README.md` 36·38·63·78·86~91행
- **[Claude 문제 제기]**:
  - 불일치 목록:
    | 문서 위치 | 현재 문서 | 사용자 확정 결정 / 현재 코드 |
    | :--- | :--- | :--- |
    | SCORING.md 11행, README 63·86~91행 | 일반/관리 **2단계** 등급 | **B8: 4단계 통일** (`AppGrade` FOCUS/TOOL/DAILY/LIMIT) |
    | SCORING.md 14행, README 90행 | 쇼핑 = **일반** | **B10: 쇼핑 = 관리** |
    | README 36행 | 기상 시간대 **첫 앱 진입 시** 브리핑 | **B4: 연속 3분 사용 시 기상 확정** + 어제 기록이 있을 때만 |
    | README 38행 | 방어선 판정 기준 미기재 | **B5: 방어선은 마지막 점수 기준** |
    | README 78행 | 런처 아이콘이 항상 동적 전환 | **C1: 옵션, 기본 꺼짐** |
    | (없음) | 15분 자유권의 효과 범위 미기재 | **B1: 점수·엣지 경고·알림 모두 면제** |
    | (없음) | 점수 모델 버전 표기 없음 | **B13: 저장 모델 버전 카운팅(`ScoreModel`, 현재 5)** |
- **[Gemini 검토]**:
  - **판정: 전폭 수용**
- **[최종 합의안]**:
  - 위 표의 7개 항목을 기준으로 `docs/SCORING.md` 및 `README.md`를 업데이트한다.

### 안건 9. (Claude 제기) 자동 버전 증가 정책
- **관련 코드**: `app/build.gradle.kts` 29~56행, `scripts/sign_bundle.sh`
- **[사용자 최종 결정]**:
  - **"2. debug빌드도 구분은 해야해서 난 전체 다 올리면 좋겠어. 정식 출시까지 50번 넘게 할 것 같지 않아."** → **C안 확정**
- **[최종 합의안]**:
  - **C안(현행 유지)**: `app/build.gradle.kts`에서 debug를 포함한 모든 `assemble`/`bundle` 작업 시 `version.properties`의 `PATCH` 및 `VERSION_CODE`를 +1 자동 증가시키는 현행 로직을 그대로 유지한다.
- **[사용자 추가 결정 (Round 3)]**: "빌드 버전은 무조건 올리는 걸로. 버전이 같으면 헷갈려."
  - Claude 반영 완료: `build.gradle.kts`에서 `-PnoAutoBump` 예외를 **삭제**하고, 에뮬레이터 스크립트(`scripts/dev/tasks/build_install.sh`)에서도 제거. 이제 예외는 **CI 태그 빌드(태그 버전 사용)뿐**. 상한 문제는 안건 14 참조.

### 안건 10. (Claude 제기) 05:00 하루 기준(B11)과 남은 경계 불일치
- **관련 코드**: `DashboardScreen.kt` (1648행, 1821~1850행), `StatisticsExplorer.kt` (87~93행, 160~163행), `ExplorerData.kt` (20~35행, 135~140행), `CumulativeScoreStore.kt` (77행), `TrackerForegroundService.kt` (1135~1145행)
- **[사용자 최종 결정]**:
  - **"3. 논리적 하루는 05:00로 표현하자."**
- **[최종 합의안]**:
  - 앱 전반의 하루 경계 표현과 집계를 **오전 `05:00` 논리적 하루(Logical Day)** 기준으로 통일한다:
    1. 대시보드 24시간 추세 차트(`DashboardScreen.kt`) 및 통계 24시간/시간별 축 라벨(`StatisticsExplorer.kt`)의 날짜 전환 구분선을 `00:00`(자정)에서 **`05:00`**으로 변경한다.
    2. 통계 일별 버킷(`ExplorerData.kt`, `StatisticsExplorer.kt`)과 수면 창 학습 캐시 키(`CumulativeScoreStore.kt`), 기록 정리 기준일(`TrackerForegroundService.kt`, `DailyGoalStore.kt`)에서 달력 자정(`00:00`) 대신 **`05:00` 논리적 날짜(`minusHours(5)` 기준 `LocalDate`)**를 사용한다.

### 안건 11. (Claude 제기) 로컬 git 이력의 서명 비밀번호 잔존 (D1 후속)
- **[Gemini 검토 (로컬 Git 확인 완료)]**:
  - `8162f55`(v0.9.2), `94efa89`, `a36fead`(v0.9.32) 커밋이 이미 원격 저장소(`origin`)에 push된 상태임을 확인함.
- **[최종 합의안]**:
  - 코드 내 하드코딩 제거(D1)를 유지하고, git 이력 강제 재작성 대신 추후 **업로드 키스토어 비밀번호 변경(및 `local.properties`/GitHub Secrets 업데이트)**으로 대응한다.

### 안건 12. (Claude 제기) 시계가 뒤로 바뀌면 점수 정산이 멈추고 표시 점수가 어긋남
- **관련 코드**: `CumulativeScoreStore.update()` (96~108행), `StatisticsStore.recordMovements()`, `TrackerForegroundService.kt` (코어 지수 샘플 저장 764~776행), `StatisticsExplorer.kt` (264~272행)
- **[Claude 문제 제기]**:
  - **관찰 (에뮬레이터, 10/20 22:43)**: 대시보드·통계 상단은 **83.5~84**, 시간별 차트 현재값 배지·일별 최고는 **77.4**.
  - **원인 추적**:
    1. B4 시험 때 에뮬레이터 시계를 **10/22 05:28로 앞당겼다가** 자동 시간으로 되돌림. 그동안 정산 체크포인트가 10/22 05:3x까지 전진하고, 밤사이 휴식 회복으로 점수가 83.5가 됨.
    2. 시계 복귀 후 `update()`의 `processStart = max(checkpoint.timestamp, …).coerceAtMost(max(settledEnd, checkpoint.timestamp))` → 체크포인트가 미래이므로 **정산이 실제 시각이 10/22 05:3x를 넘을 때까지 멈춤**(약 31시간). 점수는 83.5에 고정.
    3. 그 사이 `coreIndexSample`(실시간 샘플)은 현재 시각으로 83.5를 계속 기록하지만, 시간별 점수(`ScoreHourEntity`)는 정산 이동(`recordMovements`)으로만 생기므로 새 행이 없음. 미래 시각 행은 조회 범위 밖 → **두 출처가 어긋남**.
  - **실사용 영향**: 사용자가 시계를 수동으로 뒤로 돌리거나 잘못된 시간 동기화가 교정되면 같은 현상(점수 정지 + 기록 불일치)이 발생. 해외 시간대 이동은 epoch이 바뀌지 않아 해당 없음. 빈도는 낮지만 발생 시 "점수가 하루 넘게 안 움직임"으로 보임.
  - 주석("clock rollback 시 휴식을 지어내지 않음")상 정산을 멈추는 것은 **의도된 보호**였으나, 멈춤 기간에 상한이 없고 미래 체크포인트를 복구하지 않음.
- **제안**:
  - A안: `checkpoint.timestamp > settledEnd + 5분`이면 시계 역행으로 보고 **체크포인트 시각만 `settledEnd`로 되돌림**(점수 상태는 유지, 역행 구간은 정산하지 않음). 미래 시각으로 기록된 `ScoreHourEntity`/`coreIndexSample`/`wakeConfirmedAt`/아침 브리핑 날짜도 정리.
  - B안: 현행 유지(역행 시 정지), 대신 정지 상태를 UI에 표시.
  - Claude 의견: A안. 테스트로 "체크포인트가 미래일 때 다음 update에서 settledEnd로 복귀하고 점수 상태는 유지" 추가.
  - 에뮬레이터 데이터는 B4 시험으로 오염되어 있으므로 확인 후 디버그 앱 데이터 초기화 필요(사용자 확인 후).
- **[Gemini 검토]**:
  - **판정: A안 전폭 수용 (보완 1건 포함)**
  - **근거**: `record.checkpoint.timestamp > settledEnd + 5 * CumulativeTimeline.MINUTE`일 때 시계 역행(Clock Rollback)으로 판정하여 점수 상태(`state`: `signal`, `useMomentumMinutes`, `awakeRestMinutes`, `recoveryBurden`, `exemptMomentumMinutes`, `studyMomentumMinutes`)는 그대로 보존한 채 체크포인트 시각(`timestamp`)만 `settledEnd`로 당기는 A안이 가장 안전하고 정확함.
  - **보완 사항**: 시계 역행 복구 시 미래 시각(`> nowMillis`)으로 저장된 DB 행(`core_index_samples`, `score_hourly`, `usage_hourly`, `score_impact_hourly`) 및 `CumulativeRecord`의 미래 타임스탬프 필드(`wakeConfirmedAt`, `restingConfirmedUntil`, `briefingHandledAt`, `lateSleepAnchorAt`가 `nowMillis`보다 미래인 경우 `0L`로 리셋)를 함께 정리하면 실제 그 날짜가 도래했을 때 기상/브리핑이 누락되는 부작용까지 막을 수 있음.
- **[최종 합의안]**:
  - **A안 확정**: `CumulativeScoreStore.update()`에서 `checkpoint.timestamp > settledEnd + 5분`이면 점수 상태(`state`)는 유지한 채 체크포인트 시각을 `settledEnd`로 복구하고, 미래 타임스탬프 필드 및 미래 시각 통계/샘플 행을 정리한다(단위 테스트 추가).
  - **반영 내용 (Claude)**:
    - `CumulativeScoreStore.recoverFromClockRollback()`: 시각만 되돌리고 `state` 유지, 미래의 `wakeConfirmedAt`·`restingConfirmedUntil`·`briefingHandledAt`·`lateSleepAnchorAt`은 0.
    - 역행 감지 시 미래 행 삭제: `core_index_samples`(> now), `score_hourly`·`score_impact_hourly`·`usage_hourly`(현재 시간 이후), `statistics_state.processedUntil`을 now로. DAO에 삭제 쿼리 추가(스키마 변경 없음).
    - `StatisticsStore.refresh()`: `processedUntil`이 미래면 now로 보고 집계(사용량 통계 정지 방지).
    - **추가 정리 (합의 취지 범위)**: 같은 문제가 날짜 문자열에도 있어 함께 처리 — 아침 브리핑 기록 날짜(`last_unlock_prompt_date`), 목표·브리핑 완료·어제 목표 날짜, 보상 정산 날짜가 오늘보다 미래면 삭제. **미래로 끝나는 자유권**(`activeFreePassUntil` > now+15분)은 해제 — 그대로 두면 감점 면제가 수십 시간 지속됨. (`DailyGoalStore.discardFutureState()`, 매 주기 호출)
    - 테스트: `ClockRollbackTest` 3건.
    - **에뮬레이터 검증 중 보완**: 시계가 앞으로 간 사이 정산된 행은 시간대(hour)가 현재 이하여도 `lastAt`이 미래일 수 있음(예: 23:00 행이 가상의 23:59까지 정산되어 `last=77.7`로 고정 → 실시간 83.5와 불일치 지속). 삭제 조건을 `hour > 현재 시간대 OR lastAt > now`로 확장(정상 정산 행은 `lastAt ≤ now−2분`이라 영향 없음). 또한 이미 복구가 끝난 기기도 정리되도록 프로세스 시작 후 첫 정산에서 1회 실행.

### 안건 13. (Claude 제기) 통계 4주 화면의 '일 평균' 기준 불일치
- **관련 코드**: `StatisticsExplorer.kt` 427~440행(상단 요약), 640~665행(기간 요약 카드), 795행(앱별 일 평균 감점)
- **[Claude 문제 제기]**:
  - 상단 요약: `activePeriodDays = 사용/잠금 기록이 있는 날 수`로 나눔 → "일 평균 10min".
  - 기간 요약 카드: `daysCount = 버킷 수(28)`로 나눔 → "일 평균 0min (총 20min)". 잠금해제·습관성 오픈도 28로 나눔.
  - 앱별 일 평균 감점(795행)도 `daysCount` 기준.
  - 차트의 "4주 평균" 기준선도 별도 계산 → 같은 화면에 서로 다른 평균이 표시됨.
- **제안**: 하나의 함수 `periodDailyAverage(buckets)`로 통일.
  - 분모 = **기록이 있는 날**(사용 또는 잠금 기록이 있는 날, 오늘 포함). 기록 없는 날(앱 설치 전·추적 중지)은 0으로 세지 않음 — 측정하지 않은 날을 "0분 사용"으로 보면 평균이 실제보다 낮아짐.
  - 카드 보조 문구에 "기록 N일 기준"을 표시해 분모를 명확히.
  - 상단 요약·기간 카드·4주 평균선·앱별 일 평균에 같은 분모 적용.
- **[Gemini 검토]**:
  - **판정: 전폭 수용**
  - 앱 설치 초기나 미측정 기간이 포함된 상태에서 28일 고정 분모(`summaryBuckets.size`)를 쓰면 일 평균이 `0min`으로 왜곡됨. 상단 요약 바처럼 **실제 기록이 존재하는 날 수(`activePeriodDays`)**로 모든 일 평균 분모를 통일하고 보조 문구에 기록 일수를 함께 안내하는 안에 완전히 동의함.
- **[최종 합의안]**:
  - `StatisticsExplorer.kt`의 4주 기간 화면에서 상단 요약, 기간 요약 카드(화면·잠금해제·습관성 오픈), 4주 평균선, 앱별 일 평균 감점/실행 횟수의 분모를 모두 **기록이 있는 날 수(`activePeriodDays`)**로 통일한다.
  - **반영 내용 (Claude)**: 공통 함수 `recordedDayCount()`(`ExplorerData.kt`)로 5곳 통일, 기간 요약 제목에 "기록 N일 기준 일 평균" 표시(카드 안에서는 글자가 잘려 제목으로 이동). 테스트 1건 추가.

### 안건 14. (Claude 제기) 모든 빌드 버전 증가 시 0.9.x versionCode 상한 및 `0.9.32a/b/c` 마이너 빌드 표기
- **관련 코드**: `app/build.gradle.kts` 29~75행, `version.properties`
- **[Claude 문제 제기]**:
  - 0.9.x 트랙의 versionCode = `MINOR*100 + PATCH`(현재 0.9.32 → 932). 1.0.0은 1000.
  - **PATCH가 100이 되면 0.9.100 → 1000으로 1.0.0과 같은 코드**가 되고, 이후 1.0.x와 계속 겹침 → Play 업로드 거부 또는 정식 버전이 구버전으로 인식될 위험.
  - 안건 9 추가 결정으로 에뮬레이터 확인 빌드도 버전을 올리므로, 남은 여유는 **68번 빌드**. Claude의 에뮬레이터 확인은 수정 1회당 1~3번 빌드를 사용.
- **제안**:
  - ① 안전장치: 0.x 트랙에서 PATCH가 99를 넘으면 빌드를 **실패**시키고 "1.0.0 전환 또는 MINOR 올림 필요" 메시지 출력 (조용히 겹치는 것 방지).
  - ② (선택, 사용자 결정) 0.9.99 도달 시 0.10.0 대신 바로 1.0.0 전환 계획 확인.
- **[사용자 추가 질문]**:
  - *"9.99를 넘어가는게 걱정이면, 마이너 빌드는 9.32a/b/c로 추가 표기하는 방법도 있는지도 확인해줘."*
- **[Gemini 검토 (0.9.32a/b/c 표기 가능성 및 구현 방안)]**:
  - **결론: 기술적으로 100% 가능하며, Play `versionCode(999)` 고갈 문제와 "모든 빌드의 버전 문자열이 달라야 안 헷갈린다"는 요구를 동시에 해결하는 가장 이상적인 방법입니다.**
  - **Android 버전 체계 원리**:
    1. `versionName` (문자열): 설정 화면·버그 리포트·APK 파일명에 표시되는 버전으로, `"0.9.33a"`, `"0.9.33b"`, `"0.9.33c"` 등 알파벳 접미사를 아무 제약 없이 사용할 수 있음.
    2. `versionCode` (정수): Google Play 스토어 업로드 시에만 이전 업로드본보다 커야 하며, 로컬 기기·에뮬레이터(`adb install -r`)에서는 `versionCode`가 같아도 덮어쓰기 설치가 정상 동작함.
  - **구체적 설계 옵션 (2가지 중 택 1)**:
    - **옵션 A (권장 — Debug/마이너 빌드는 `a/b/c` 증가, Release 번들만 숫자 `PATCH` 증가)**:
      - `version.properties`에 `SUB_PATCH=0` (0이면 접미사 없음, 1=`a`, 2=`b`, 3=`c` … 26=`z`, 27=`aa`, 28=`ab` …) 추가.
      - **`assembleDebug` (일상 개발·에뮬레이터 확인 빌드)**: 숫자 `PATCH`(예: `33`)와 `VERSION_CODE`(`933`)는 그대로 유지하고 `SUB_PATCH`만 +1 올려 **`0.9.33a` → `0.9.33b` → `0.9.33c` …** 로 자동 표기(APK 파일명도 `DigitsCore-v0.9.33a-debug.apk`).
      - **`bundleRelease` / `assembleRelease` (스토어 배포용 릴리스 빌드)**: 숫자 `PATCH`와 `VERSION_CODE`를 +1 올리고(`0.9.34`, `versionCode=934`), `SUB_PATCH=0`으로 리셋.
      - **장점**: 에뮬레이터/디버그 빌드를 몇백 번 돌려도 매번 `0.9.33a`, `0.9.33b`로 명확히 구분되면서, Play 스토어용 `versionCode`(`933~999`)는 실제 스토어 릴리스에만 쓰이므로 `0.9.99` 상한에 도달할 일이 원천적으로 사라짐.
    - **옵션 B (릴리스/디버그 구분 없이 `a ~ z` 26단계를 돈 뒤 `PATCH` +1)**:
      - 모든 빌드가 `0.9.33 → 0.9.33a → 0.9.33b → … → 0.9.33z → 0.9.34` 순으로 올라가는 방식(남은 66개 PATCH × 27 = **1,782회 빌드 가능**). 단, 이 경우 같은 `0.9.33a`와 `0.9.33b`를 둘 다 Play Console에 업로드할 일은 없으므로 `versionCode`는 `933`으로 유지되고 `PATCH`가 바뀔 때만 `versionCode`가 +1 됨(`bundleRelease` 시에는 즉시 다음 숫자 `PATCH`로 올리도록 결합 가능).
    - **안전장치 공통 적용**: Claude가 제안한 **`MAJOR == 0 && PATCH > 99`일 때 빌드 실패 안전장치(`require(versionPatch <= 99)`)**도 함께 추가.
- **[최종 합의안]**:
  - **사용자 결정**: "debug 빌드는 숫자는 안 바꾸고 뒤에 a/b/c를 붙이는 방법으로 진행" → **옵션 A 확정**.
  - **반영 내용 (Claude)**: `app/build.gradle.kts`, `version.properties`(`DEBUG_BUILD` 키 추가)
    - 디버그 빌드(`assembleDebug`, `bundleDebug`, `installDebug` 등): `PATCH`/`VERSION_CODE` 유지, `DEBUG_BUILD` +1 → `0.9.33a`, `0.9.33b` … `z` 다음은 `aa`. APK 파일명도 `DigitsCore-v0.9.33a-debug.apk`.
    - 릴리스 빌드(`bundleRelease`, `assembleRelease`, 변형 없는 `assemble`/`bundle`): `PATCH`/`VERSION_CODE` +1, 접미사 초기화 → `0.9.34`. `sign_bundle.sh`는 그대로 동작.
    - 접미사는 `versionName`에만 붙고 `versionCode` 계산은 숫자 버전 기준.
    - 안전장치: 0.x 트랙에서 `PATCH > 99`이면 빌드 실패 + 1.0.0 전환 안내.
    - CI 태그 빌드는 태그 버전 그대로(변경 없음).
