# DigitsCore 엔지니어링 기준

## 제품 목적

화면이 켜지고 잠금 해제된 동안 실제 전면 앱의 사용 흐름을 측정하고, 최근 24시간 코어 지수와 장기 통계를 통해 사용자가 스스로 디지털 균형을 조절하도록 돕습니다.

## 실제 기술 구성

- Kotlin 2.0.21, Android minSdk 26 / targetSdk 36
- Jetpack Compose + Material 3 + Navigation Compose
- `TrackerForegroundService`와 BroadcastReceiver
- Android `UsageStatsManager.queryEvents()`
- StateFlow 기반 현재 상태 전달
- Room 2.6.1 + SQLCipher 4.17.0 + Android Keystore
- Jetpack Glance AppWidget
- JUnit, Android instrumentation test, GitHub Actions

현재 프로젝트는 Hilt, DataStore, WorkManager를 사용하지 않습니다. 문서나 변경 제안에서 실제 의존성으로 오인하지 않습니다.

## 측정 규칙

1. 앱별 시간은 화면 ON·잠금 해제 상태에서 마지막으로 상호작용한 앱 하나에만 배정합니다. Samsung 분할 화면·PiP가 감지되면 시간을 중복하지 않고 표시 앱 중 가장 높은 부하 등급을 점수에 한 번만 적용합니다.
2. 집계의 근거는 주기적 폴링 횟수가 아니라 UsageEvents의 타임스탬프입니다. 프로세스 시작 때 한 번 상태를 복원하고 이후에는 마지막 커서 이후 이벤트만 읽습니다.
3. 화면 OFF, 잠금, 앱 전환, Activity 종료와 재부팅에서 열린 세션을 안전하게 닫습니다.
4. 화면 OFF 백그라운드 음악·영상 재생은 기록하지 않습니다.
5. 1분 미만 사용 구간도 삭제하지 않으며 초 단위로 표시합니다. 앱별 실행 횟수와 1분 미만 실행 횟수를 별도 일별 지표로 보관합니다.
6. 제조사 Digital Wellbeing의 비공개 내부 수치를 복사할 수 있다고 가정하지 않습니다.
7. 잠금 해제는 `ACTION_USER_PRESENT`와 `KEYGUARD_HIDDEN` 실제 이벤트만 세며 단순 화면 ON은 제외합니다. 관측되지 않은 값은 추정하지 않습니다.

## 데이터와 개인정보 규칙

1. 상세 전면 세션은 30일, 일별 앱·실행 집계는 365일, 잠금 해제·알림 최소 이벤트는 25시간 보관합니다.
2. Room 스키마 변경에는 명시적 migration과 기존 DB 업그레이드 검증을 추가합니다.
3. 기존 DB를 열지 못했다고 자동으로 삭제하거나 destructive migration하지 않습니다.
4. DB는 SQLCipher, 키는 Android Keystore로 보호하고 OS 자동 백업에서 제외합니다.
5. 수동 백업은 사용자 비밀번호 기반 AES-256-GCM만 사용합니다.
6. 새 수집 항목, SDK 또는 서버 전송을 추가하면 개인정보처리방침과 Play Data safety를 함께 갱신합니다.

## 점수 규칙

1. 메인 지수는 `RollingScoreCalculator`의 최근 24시간 방식입니다.
2. 좋은 앱은 가점을 만들지 않고 더 작은 비상쇄형 부하를 가집니다.
3. 30분 이후 모든 등급의 연속 사용, 몰입 관리 앱의 심야 사용과 언락 초과를 추가 부하로 처리합니다.
4. 쉬는 동안 급성 부하는 회복하지만 화면 OFF 자체에 무제한 가점을 주지 않습니다.
5. 50~90점 응답 곡선은 단조 증가해야 하며 50·70·90 고정점을 유지합니다.
6. 빈 기록 80점, 3시간 연속 몰입 관리 앱 약 40점, 이후 3시간 휴식 70점대 시나리오를 회귀 테스트로 보호합니다.

상세 수식은 [docs/SCORING.md](docs/SCORING.md)를 기준으로 합니다.

## UI와 표시 규칙

1. 앱에서 선택한 한국어·영어가 화면, 알림, 위젯에 모두 적용되어야 합니다.
2. 상태바 작은 아이콘은 제조사 슬롯 크기 제한을 고려해 점수 가독성을 우선합니다.
3. 런처 아이콘은 고정 B1 디자인이며 점수 애니메이션을 적용하지 않습니다.
4. 위젯은 실제 크기에 따라 1×1, 확장형, 2×2 이상 레이아웃으로 나누고 배경 스타일 변경을 즉시 반영합니다.
5. 통계는 핵심 비교가 한눈에 보이도록 그래프를 기본 표현으로 사용합니다.

## 출시 준비 우선순위

항목별 현재 상태와 증빙·완료 조건은 [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md)를 단일 체크리스트로 사용합니다.

### P0 · 코드 적용 완료

1. 최근 24시간 잠금 해제를 실제 이벤트로 통일
2. 하루 전체 반복 조회를 시작 1회 복원 + 증분 이벤트 조회로 전환
3. 전면 앱 포착률, 조회 범위·건수·시간과 프로세스 CPU 시간을 앱에서 확인
4. 지수 변화 원인, 예상 회복, 하루 요약, 자기 과거 비교와 한 가지 제안
5. 70·60·50 하향 통과 시 6시간 재알림 제한을 둔 안내
6. 1분 미만 전면 사용과 앱별 일일 실행·짧은 실행 횟수 보존 및 그래프
7. 개인정보 최소화 버그 리포트와 앱 내 출시 준비 상태 화면

### P1 · 출시 전 실기기 검증

1. Samsung에서 분할 화면·PiP를 포함해 2~4주 연속 측정하고 안정화 후 Pixel·Xiaomi로 확대
2. 제조사 디지털 웰빙 대비 일별·앱별 절대 오차, 전면 앱 포착률과 누락 구간 기록
3. Android 배터리 사용량의 24시간 평균 및 화면 ON 1시간당 DigitsCore CPU 시간 기록
4. 큰 글꼴·다크모드·TalkBack·가로화면·태블릿 회귀 테스트

### P2 · Play 제출 준비

1. 업로드 키 생성·오프라인 백업·GitHub Secrets 구성 후 서명 AAB 확인
2. 개발자명·지원 이메일·공개 개인정보처리방침 URL 및 Data safety 확정
3. Foreground Service 동작 설명과 권한 시연 영상 준비
4. 내부 테스트 배포 후 Android Vitals의 Crash·ANR·wake lock 확인

### P3 · 상품성 검증

1. 20~50명에게 30일 사용을 요청해 전체·프리셋별 점수 분포 확인
2. 평균 70, 일반적 양호 80, 과다 게임 약 40이라는 목표 구간과 실제 분포 비교
3. 안내 노출 후 휴식·짧은 실행 빈도 변화와 알림 해제율 확인

## 변경 검증

PR과 main 반영 전 다음을 통과해야 합니다.

```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
gradle :app:assembleRelease :app:bundleRelease --no-daemon
```

데이터베이스 또는 보안 변경은 Android 에뮬레이터의 v1.0.60 평문 DB 이전 테스트까지 확인합니다. 저장소에 Gradle wrapper 실행 파일/JAR가 없으므로 로컬에서는 Gradle 8.11.1을 사용하며 최종 검증은 GitHub Actions를 기준으로 합니다.
