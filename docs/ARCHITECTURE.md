# DigitsCore 구조

## 데이터 흐름

```text
Android UsageEvents
        ↓
UsageStatsHelper — 최초 상태 복원 + 커서 이후 증분 이벤트 재구성
        ↓
TrackerForegroundService — 화면 켜짐 중 주기 갱신 및 경계 처리
        ├─ Room + SQLCipher — 상세 30일 / 일별 집계 365일 / 상호작용 25시간
        ├─ ScoreRepository(StateFlow) — 현재 프로세스의 화면 상태
        ├─ RollingScoreCalculator — 최근 24시간 코어 지수
        ├─ CoreIndexCoach — 변화 원인·회복 예상·한 가지 제안
        ├─ ScoreNotificationManager — 상태바 알림
        └─ ScoreWidget(Glance) — 반응형 홈 위젯
```

## 주요 구성요소

### 측정

- `UsageStatsHelper`: `UsageStatsManager.queryEvents()`를 해석해 전면 앱 세션, 화면시간, 실제 잠금 해제와 알림 interruption 수를 계산합니다. 0ms보다 긴 모든 전면 세션을 유지합니다.
- `TrackerForegroundService`: 사용자가 추적을 켠 경우에만 동작합니다. 시작 때 오늘 상태를 한 번 복원하고 이후 마지막 처리 커서 이후 이벤트만 읽습니다. 화면 OFF에서는 반복 계산을 멈추고 화면 전환 이벤트의 시각으로 세션을 닫습니다.
- `ScreenEventReceiver`, `BootCompletedReceiver`: 화면·잠금 상태와 사용자가 활성화한 추적 복원을 연결합니다.
- 잠금 해제는 `ACTION_USER_PRESENT`와 `UsageEvents.KEYGUARD_HIDDEN`을 15초 안에서 한 건으로 합칩니다. 단순 화면 켜짐은 잠금 해제로 세지 않으며 누락 구간을 추정하지 않습니다.

### 점수

- `RollingScoreCalculator`: 메인 코어 지수. 최근 24시간 누적 부하, 연속 사용 급성 부하, 휴식 회복, 언락과 심야 가중치를 처리합니다.
- `ScoreCalculator`: 기존 자정 기준 계산. 저장 데이터와 기존 설정 호환을 위해 내부에만 남아 있으며 UI에는 노출하지 않습니다.
- `ScoreRepository`: 서비스가 계산한 현재 점수와 사용량을 Compose 화면·알림·위젯에 전달하는 프로세스 내 `StateFlow` 저장소입니다.
- `CoreIndexCoach`: 현재와 직전 표본, 최근 세션, 14일 기록을 비교해 변화 원인 한 문장, 3점 회복 예상, 하루 요약과 한 가지 제안을 만듭니다.
- `MeasurementDiagnostics`: 이벤트 조회 범위·건수·시간, 측정 주기 CPU 시간, 화면 ON·잠금 해제 구간의 전면 앱 포착률을 노출해 실기기 정확도와 비용을 비교할 근거를 만듭니다.
- `WidgetSnapshotStore`: 서비스가 확정한 점수·화면시간·관리시간·언락·흐름을 앱 전용 SharedPreferences에 먼저 저장합니다. Glance 작업이 지연되거나 프로세스가 재생성되어도 메모리 기본값 80/0으로 되돌아가지 않고 마지막 확정값을 그립니다.

수식은 [SCORING.md](SCORING.md)를 참고하세요.

### 저장

Room 데이터베이스 버전은 v13입니다.

- `foreground_usage_sessions`: 앱별 상세 시작·종료 구간, 30일
- `core_index_samples`: 화면 ON 상태에서 갱신한 5분 단위 코어 지수·부하·프리셋 표본, 30일
- `daily_app_usage`: 앱별 일일 사용시간·실행·1분 미만 실행·최장 세션 집계, 365일
- `daily_usage_coverage`: 날짜별 자체 측정 완료 여부
- `daily_score_history`: 일별 코어 지수·화면·언락과 당시 `coreIndexPresetId` 집계. `scoreModelVersion=2`인 행만 코어 지수 통계에 사용하고, 이전 행의 사용량·언락 집계는 계속 보존
- `device_interaction_events`: 잠금 해제와 알림 interruption의 최소 타임스탬프, 25시간. 원본 앱 이벤트 전체는 저장하지 않음

통계의 24시간 화면은 `core_index_samples`, `foreground_usage_sessions`, `device_interaction_events`를 현재 시점까지의 24시간으로 잘라 사용합니다. 4주 화면은 오늘을 포함한 28일 달력 축에 날짜별 집계를 배치합니다.
24시간 복합 차트는 5분 코어 지수 표본을 하나의 연속 추세선으로 표시합니다. 화면 OFF로 표본이 없는 구간은 다음 사용 시 계산된 회복값까지 직선으로 연결하되, 하단 사용량 막대에는 추정값을 채우지 않고 0으로 유지합니다. 전면 사용량과 언락은 같은 24개 버킷에 정렬합니다. 4주 차트의 사용량 막대는 모든 유효 일별 집계를, 코어 지수 범위봉은 현재 지수를 계산할 수 있는 날짜만 사용합니다. 7일 이동평균은 누락일을 0으로 채우지 않습니다.
- `app_weights`: 앱별 5단계 등급과 사용자 변경 여부
- `user_settings`: 코어 지수 프리셋, 호환용 구식 점수 계수, 추적·알림·잠금화면·상태 아이콘·위젯 배경 설정

SQLCipher DB 암호는 Android Keystore로 보호합니다. 이전 평문 DB를 암호화 DB로 안전하게 옮기는 경로와 실패 시 기존 기록을 보존하는 호환 모드가 있습니다.

### UI와 외부 표시

- Jetpack Compose + Material 3: 온보딩, 대시보드, 통계, 앱 등급, 점수 설정. 공통 760dp 본문 프레임과 전원 버튼형 `CoreIndexGauge`로 가로 화면 및 외부 표시와 시각 언어를 통일
- 상태바: Canvas로 점수 또는 전원 버튼을 그려 큰 점수와 얇은 전원 실루엣을 시스템 밝기 모드 대비 단색으로 표시하는 코어 숫자형을 포함한 네 가지 스타일 제공. foreground-service immediate 표시와 주기적 `startForeground` 재확인으로 아이콘 복원력을 높임
- 런처: 고정 B1 전원 버튼 아이콘
- Jetpack Glance 위젯: 1×1부터 2×2 이상까지 반응형 레이아웃, dark/white/transparent 배경
- 언어: 한국어·영어 문자열 리소스와 기존 Compose 문구 호환 번역 계층

### 백업과 개인정보

- 기기 내부 DB와 설정은 OS 자동 백업·기기 이전에서 제외합니다.
- 수동 내보내기는 사용자 비밀번호로 AES-256-GCM 암호화합니다.
- 백업에는 현재 설정, 앱 등급, 최대 365일 일별 집계와 내보내는 시점에 남아 있는 상세 세션·코어 지수 표본이 포함됩니다.
- 서버, 계정, 광고·분석 SDK는 없습니다.

## 빌드와 검증

GitHub Actions는 PR, main push, `v*` 태그에서 다음을 실행합니다.

1. JUnit 테스트와 debug APK 빌드
2. JUnit 선행 후 R8 release APK/AAB 빌드
3. Android 에뮬레이터에서 v1.0.60 평문 DB의 암호화 이전 테스트
4. 업로드 서명 secret이 모두 있을 때만 태그의 정식 서명 APK/AAB 게시

PR과 main push는 저장공간을 소비하는 APK 아티팩트를 남기지 않습니다. 수동 실행만 7일간 debug 아티팩트를 보관하며, `v*` 태그는 세 검증이 모두 통과한 뒤 debug APK를 Actions 저장소를 거치지 않고 GitHub Release에 직접 게시합니다. 업로드 서명 secret이 없는 현재 공개 GitHub Release는 실사용 검증용 debug APK입니다.
