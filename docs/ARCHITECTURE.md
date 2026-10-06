# DigitsCore 구조

## 데이터 흐름

```text
Android UsageEvents
        ↓
UsageStatsHelper — 최초 상태 복원 + 커서 이후 증분 이벤트 재구성
        ↓
TrackerForegroundService — 화면 켜짐 중 주기 갱신 및 경계 처리
        ├─ Room + SQLCipher — 상세 30일 / 일별 집계 365일 / 상호작용 48시간
        ├─ ScoreRepository(StateFlow) — 현재 프로세스의 화면 상태
        ├─ CumulativeScoreStore → CumulativeTimeline → CumulativeScoreEngine — 누적 상태·증분 점수
        ├─ CoreIndexCoach — 변화 원인·회복 예상·한 가지 제안
        ├─ ScoreNotificationManager — 상태바 알림
        └─ ScoreWidget(Glance) — 반응형 홈 위젯
```

## 주요 구성요소

### 측정

- `UsageStatsHelper`: `UsageStatsManager.queryEvents()`를 해석해 전면 앱 세션, 화면시간, 실제 잠금 해제와 알림 interruption 수를 계산합니다. 전면 시간 조각과 실제 앱 진입 세션 ID를 분리해 PiP·Activity 전환이 실행 횟수를 늘리지 않게 합니다.
- `TrackerForegroundService`: 사용자가 추적을 켠 경우에만 동작합니다. 시작 때 오늘 상태를 한 번 복원하고 이후 마지막 처리 커서 이후 이벤트만 읽습니다. 화면 OFF에서는 반복 계산을 멈추고 화면 전환 이벤트의 시각으로 세션을 닫습니다.
- `ScreenEventReceiver`, `BootCompletedReceiver`: 화면·잠금 상태와 사용자가 활성화한 추적 복원을 연결합니다.
- 잠금 해제는 `ACTION_USER_PRESENT`와 `UsageEvents.KEYGUARD_HIDDEN`을 15초 안에서 한 건으로 합칩니다. 단순 화면 켜짐은 잠금 해제로 세지 않으며 누락 구간을 추정하지 않습니다.

### 점수

- `CumulativeScoreEngine`: 누적 점수의 순수 전이 함수. 일반/관리 사용 가속, 활동 중 회복, 수면 동결을 처리합니다.
- `CumulativeScoreStore`: 암호화 DB에 상태와 처리 커서를 함께 확정합니다. 설정 버전과 이전 점수를 보존하며, UI/예측 계산은 커서를 전진시키지 않습니다.
- `RestPhasePolicy`, `WakeStepEvidence`: 예상 야간 휴식과 선택적 하드웨어 걸음 근거. 실제 수면 측정이 아니며 확인 결과를 소급 적용하지 않습니다.
- `RollingScoreCalculator`: 이전 모델 기록의 호환·검증용이며 현재 서비스의 점수를 계산하지 않습니다.
- `ScoreCalculator`: 기존 자정 기준 계산. 저장 데이터와 기존 설정 호환을 위해 내부에만 남아 있으며 UI에는 노출하지 않습니다.
- `ScoreRepository`: 서비스가 계산한 누적 점수와 최근 24시간 앱별 사용·화면/관리 시간·언락을 Compose 화면·알림·위젯에 전달하는 프로세스 내 `StateFlow` 저장소입니다.
- `CoreIndexCoach`: 현재와 직전 표본, 최근 24시간 세션, 14일 기록을 비교해 변화 원인 한 문장, 3점 회복 예상, 24시간 요약과 한 가지 제안을 만듭니다.
- `MeasurementDiagnostics`: 이벤트 조회 범위·건수·시간, 측정 주기 CPU 시간, 화면 ON·잠금 해제 구간의 전면 앱 포착률을 노출해 실기기 정확도와 비용을 비교할 근거를 만듭니다.
- `DailyGoalStore`: 아침 브리핑 및 일일 3대 목표(코어 지수 방어선, 특정 앱 시간 한도, 잠금 해제 상한)를 05:00 AM 기준 활동일로 관리하며, 목표 진행률을 계산해 서비스 알림 코칭과 동기화합니다.
- `WidgetSnapshotStore`: 서비스가 확정한 점수·화면시간·관리시간·언락·흐름을 앱 전용 SharedPreferences에 먼저 저장합니다. Glance 작업이 지연되거나 프로세스가 재생성되어도 초기값으로 되돌아가지 않고 마지막 확정값을 그립니다.

수식은 [SCORING.md](SCORING.md)를 참고하세요.

### 저장

Room 데이터베이스 버전은 v19입니다.

- `cumulative_score_state`: 설정 버전, 내부 점수, 가속 잔여, 회복 부담, 커서, 기상 확인 및 제안 선택. 상세 기록 보관 기간과 독립적으로 유지되며 전체 기록 삭제 시 삭제

- `foreground_usage_sessions`: 앱별 상세 시작·종료 구간, 실제 앱 진입 세션 시작 시각과 분할 화면·PiP의 유효 점수 등급/동시 표시 앱 수, 30일
- `core_index_samples`: 화면 ON 상태에서 갱신한 5분 단위 코어 지수·부하·프리셋 표본, 30일
- `daily_app_usage`: 앱별 일일 사용시간·실행·1분 미만 실행·최장 세션 집계, 365일
- `daily_usage_coverage`: 날짜별 자체 측정 완료 여부
- `daily_score_history`: 일별 코어 지수·화면·언락과 당시 `coreIndexPresetId`, `scoreModelVersion` 집계. 새 누적 모델은 버전 5이며 이전 모델 기록도 보존
- `device_interaction_events`: 잠금 해제와 알림 interruption의 최소 타임스탬프, 48시간. 원본 앱 이벤트 전체는 저장하지 않음
- `usage_hourly`: 전체/앱 시간별 사용·관리 사용·오픈·짧은 실행 및 전체 언락/알림 수, 365일
- `score_hourly`: 모델별 시간 단위 점수 시작·마지막·최저·최고, 365일
- `score_impact_hourly`: 실제 전이의 앱별 감점과 전체 감점/회복·관측 범위, 365일
- `statistics_state`: 집계 처리 커서. 원본 삭제 전에 집계를 완료하며 최초에는 남아 있는 30일 원본만 이관

대시보드·앱 상세·상태 알림·위젯과 통계의 24시간 화면은 `core_index_samples`, `foreground_usage_sessions`, `device_interaction_events`를 현재 시점까지의 24시간으로 잘라 사용합니다. 자정 기준 일일 집계는 4주·요일별·장기 날짜 통계용으로만 사용하며, 4주 화면은 오늘을 포함한 28일 달력 축에 배치합니다.
통계는 24시간·1주·4주·3개월·1년을 탐색합니다. 하루는 선, 장기는 일별 캔들이며 1주·4주는 시간봉 전환을 제공합니다. 모델 경계를 넘어 선을 연결하거나 하나의 캔들로 합치지 않습니다. 사용/오픈/언락은 같은 시간축에 배치하고 미기록은 임의 값으로 채우지 않습니다. 차트 하단 앱 순위는 사용시간·실제 감점·오픈별로 전환합니다. 자세한 범위·보존·기여도 규칙은 [STATISTICS.md](STATISTICS.md)를 참고하세요.
- `app_weights`: 앱별 일반·관리 2단계 등급과 사용자 변경 여부. 이전 등급 식별자는 마이그레이션·백업 호환용으로만 읽음
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
- 백업 v11에는 현재 설정, 앱 등급, 최대 365일 시간별·일별 집계와 내보내는 시점에 남아 있는 상세 세션·코어 지수 표본이 포함됩니다. 시간별 데이터는 압축형 JSON 행으로 직렬화하며 파일 크기 제한은 64MB입니다. 기존 백업도 복원할 수 있습니다.
- 서버, 계정, 광고·분석 SDK는 없습니다.

## 빌드와 검증

GitHub Actions는 PR, main push, `v*` 태그에서 다음을 실행합니다.

1. JUnit 테스트와 debug APK 빌드
2. JUnit 선행 후 R8 release APK/AAB 빌드
3. Android 에뮬레이터에서 v1.0.60 평문 DB의 암호화 이전 테스트
4. 업로드 서명 secret이 모두 있을 때만 태그의 정식 서명 APK/AAB 게시

PR과 main push는 저장공간을 소비하는 APK 아티팩트를 남기지 않습니다. 수동 실행에서도 `upload_apk`를 선택한 경우에만 7일간 debug 아티팩트를 보관하며, `v*` 태그는 세 검증이 모두 통과한 뒤 debug APK를 Actions 저장소를 거치지 않고 GitHub Release에 직접 게시합니다. 업로드 서명 secret이 없는 현재 공개 GitHub Release는 실사용 검증용 debug APK입니다.
