# DigitsCore 구조

## 데이터 흐름

```text
Android UsageEvents
        ↓
UsageStatsHelper — 화면 ON·잠금 해제·전면 앱 구간 재구성
        ↓
TrackerForegroundService — 화면 켜짐 중 주기 갱신 및 경계 처리
        ├─ Room + SQLCipher — 상세 30일 / 일별 집계 365일
        ├─ ScoreRepository(StateFlow) — 현재 프로세스의 화면 상태
        ├─ RollingScoreCalculator — 최근 24시간 코어 지수
        ├─ ScoreNotificationManager — 상태바 알림
        └─ ScoreWidget(Glance) — 반응형 홈 위젯
```

## 주요 구성요소

### 측정

- `UsageStatsHelper`: `UsageStatsManager.queryEvents()`를 해석해 전면 앱 세션, 화면시간, 언락과 알림 interruption 수를 계산합니다.
- `TrackerForegroundService`: 사용자가 추적을 켠 경우에만 동작합니다. 화면 OFF에서는 반복 계산을 멈추고 화면 전환 이벤트의 시각으로 세션을 닫습니다.
- `ScreenEventReceiver`, `BootCompletedReceiver`: 화면·잠금 상태와 사용자가 활성화한 추적 복원을 연결합니다.

### 점수

- `RollingScoreCalculator`: 메인 코어 지수. 최근 24시간 누적 부하, 연속 사용 급성 부하, 휴식 회복, 언락과 심야 가중치를 처리합니다.
- `ScoreCalculator`: 기존 자정 기준 점수. 전환기 비교와 기존 설정 호환을 위해 남아 있습니다.
- `ScoreRepository`: 서비스가 계산한 현재 점수와 사용량을 Compose 화면·알림·위젯에 전달하는 프로세스 내 `StateFlow` 저장소입니다.

수식은 [SCORING.md](SCORING.md)를 참고하세요.

### 저장

Room 데이터베이스 버전은 v8입니다.

- `foreground_usage_sessions`: 앱별 상세 시작·종료 구간, 30일
- `daily_app_usage`: 앱별 일일 사용 집계, 365일
- `daily_usage_coverage`: 날짜별 자체 측정 완료 여부
- `daily_score_history`: 일별 점수·화면·언락 집계
- `app_weights`: 앱별 5단계 등급과 사용자 변경 여부
- `user_settings`: 프리셋, 점수 계수, 추적·알림·잠금화면·상태 아이콘·위젯 배경 설정

SQLCipher DB 암호는 Android Keystore로 보호합니다. 이전 평문 DB를 암호화 DB로 안전하게 옮기는 경로와 실패 시 기존 기록을 보존하는 호환 모드가 있습니다.

### UI와 외부 표시

- Jetpack Compose + Material 3: 온보딩, 대시보드, 통계, 앱 등급, 점수 설정
- 상태바: Canvas로 전원 버튼과 점수를 그려 세 가지 표시 스타일 제공
- 런처: 고정 B1 전원 버튼 아이콘
- Jetpack Glance 위젯: 1×1부터 2×2 이상까지 반응형 레이아웃, dark/white/transparent 배경
- 언어: 한국어·영어 문자열 리소스와 기존 Compose 문구 호환 번역 계층

### 백업과 개인정보

- 기기 내부 DB와 설정은 OS 자동 백업·기기 이전에서 제외합니다.
- 수동 내보내기는 사용자 비밀번호로 AES-256-GCM 암호화합니다.
- 백업에는 현재 설정, 앱 등급, 최대 365일 일별 집계와 내보내는 시점에 남아 있는 상세 세션이 포함됩니다.
- 서버, 계정, 광고·분석 SDK는 없습니다.

## 빌드와 검증

GitHub Actions는 PR, main push, `v*` 태그에서 다음을 실행합니다.

1. JUnit 테스트와 debug APK 빌드
2. JUnit 선행 후 R8 release APK/AAB 빌드
3. Android 에뮬레이터에서 v1.0.60 평문 DB의 암호화 이전 테스트
4. 업로드 서명 secret이 모두 있을 때만 태그의 정식 서명 APK/AAB 게시

서명 secret이 없는 현재 공개 GitHub Release는 별도로 올린 실사용 검증용 debug APK입니다.
