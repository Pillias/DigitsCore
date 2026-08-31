# DigitsCore

Android의 전면 앱 사용, 잠금 해제, 화면 미사용 시간을 기기 안에서 분석해 0~100점으로 보여주는 디지털 웰빙 앱입니다.

[최신 Release v1.0.60](https://github.com/Pillias/DigitsCore/releases/tag/v1.0.60) · [APK 다운로드](https://github.com/Pillias/DigitsCore/releases/download/v1.0.60/DigitsCore-v1.0.60-debug.apk) · [개인정보처리방침](PRIVACY_POLICY.md)

> 현재 GitHub Release의 APK는 실사용 검증용 debug 서명 빌드입니다. Google Play 배포용 서명 AAB와는 구분됩니다.

## 현재 구현 상태

### 전면 앱 사용시간 측정

- `UsageStatsManager.queryEvents()`의 Activity·화면·잠금 이벤트로 앱 사용 타임라인을 재구성합니다.
- 화면이 켜지고 잠금이 해제된 동안 전면에 있는 앱 하나만 시간을 소유합니다.
- 다음 앱의 `ACTIVITY_RESUMED`, 화면 OFF, 잠금, 앱 종료 및 재부팅 이벤트에서 사용 구간을 닫습니다.
- 제조사별 중복 집계가 발생할 수 있는 `queryAndAggregateUsageStats().totalTimeInForeground` 값은 앱별 시간 계산에 사용하지 않습니다.
- 추적 서비스는 화면이 켜진 동안 1분 주기로 갱신하지만, 사용시간은 이벤트 타임스탬프를 기준으로 계산합니다.

Samsung Digital Wellbeing 등 제조사 시스템 앱의 내부 집계값을 직접 읽는 공개 API는 없습니다. DigitsCore는 동일 수치를 복사하지 않고 공개 Android 이벤트로 독립 계산하므로 기기·OS의 이벤트 보존 및 전달 방식에 따라 차이가 날 수 있습니다.

### 앱별 분석

- 오늘 많이 사용한 시간대의 24시간 막대 그래프
- 사용 세션 횟수, 평균 및 최장 세션
- 30분 이상 이어진 세션을 그래프에서 주황색으로 강조
- 최근 7일 앱별 사용 추세 그래프
- 심야(00:00~05:00) 사용시간 분리
- 앱 상세 우측 상단에서 5단계 균형 등급 변경

최근 7일 분석은 상세 화면을 열 때만 조회하므로 상시 백그라운드 부하를 추가하지 않습니다. Android가 보존한 이벤트가 없는 기간은 복구하거나 추정하지 않습니다.

### 언락 및 알림 분석

- 시간대별 언락 분포 그래프
- 언락 사이의 평균 간격
- OS가 기록한 notification interruption 이벤트 수와 언락 횟수 비교

알림과 언락 비교는 상관관계를 살펴보기 위한 단순 비교이며, 특정 알림이 언락의 직접 원인임을 뜻하지 않습니다. 별도의 알림 접근 권한이나 `NotificationListenerService`는 사용하지 않습니다.

### 점수와 리포트

- 앱 등급별 가산·감점, 언락 초과, 심야 사용, 화면 미사용 회복을 반영하는 0~100점 점수
- 상태바 알림과 홈 화면 위젯
- 점수 계산 내역, 7일·30일 통계와 그래프
- 공부, 눈 건강, 직장인, 어린이, 밸런스 프리셋
- 주요 점수 계수 사용자 설정 및 로컬 백업·복원

현재 점수는 하루 단위이며 현지 시각 자정에 새 날짜 계산을 시작합니다. 자정에도 점수를 유지하고 미사용 구간에서 연속 회복하는 방식은 다음 점수 엔진 개선 후보입니다.

## 5단계 앱 균형 등급

| 단계 | 이름 | 점수 강도 | 기본 추천 |
|---:|---|---:|---|
| 1 | 성장 | +50% | 교육·학습 |
| 2 | 집중 지원 | +25% | 생산성·목표 관리 |
| 3 | 균형 | 0% | 일반·도구·지도 |
| 4 | 절제 | -50% | 쇼핑·SNS·뉴스 |
| 5 | 몰입 관리 | -100% | 게임·동영상·음악 |

Android가 쇼핑·교육을 독립된 `ApplicationInfo` 카테고리로 제공하지 않으므로 일부 대표 앱은 패키지 기반 추천을 사용합니다. 사용자가 직접 지정한 등급은 자동 추천보다 항상 우선합니다.

## 권한과 개인정보

| 권한/기능 | 용도 |
|---|---|
| 사용 정보 접근 | 전면 앱·화면·잠금·알림 이벤트 분석 |
| 알림 | 추적 상태와 현재 점수 표시 |
| Foreground Service | 사용자가 켠 실시간 추적 유지 |
| 부팅 완료 | 추적 활성 상태 복원 |

- 최초 설치 시 추적은 꺼져 있으며 사용자가 직접 시작해야 합니다.
- 사용 기록, 앱 등급과 설정은 기기 내부 Room 데이터베이스에서 처리합니다.
- 계정, 광고 SDK, 분석 SDK 및 개발자 서버 전송 기능이 없습니다.
- 백업은 사용자가 직접 실행하고 Android 공유 화면에서 대상을 선택할 때만 외부로 전달됩니다.

자세한 내용은 [개인정보처리방침](PRIVACY_POLICY.md)을 참고하세요.

## 설치

### GitHub Release APK

1. [최신 Release](https://github.com/Pillias/DigitsCore/releases/latest)에서 APK를 내려받습니다.
2. Android에서 출처를 확인하고 설치합니다.
3. 온보딩 안내에 따라 사용 정보 접근 권한을 허용합니다.
4. 앱에서 추적을 직접 시작합니다.

기존 설치본과 서명이 다르면 덮어쓰기가 거부될 수 있습니다. 이 경우 기존 앱 데이터를 백업한 뒤 제거하고 새 APK를 설치해야 할 수 있습니다.

### 소스 빌드

필요 환경:

- JDK 17
- Android SDK 36
- Gradle 8.11.1

```bash
gradle :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Google Play용 release AAB 준비 절차는 [PLAY_RELEASE_CHECKLIST.md](PLAY_RELEASE_CHECKLIST.md)를 참고하세요.

## 기술 구성

- Kotlin 2.0.21
- Jetpack Compose + Material 3
- StateFlow 기반 UI 상태
- Room Database
- Android UsageStats, Foreground Service, Notification, AppWidget API
- JUnit 및 GitHub Actions 기반 APK/AAB 검증

## 점수 보정 기준

직장인·학생·어린이 프리셋의 약 60점 기준은 건강 진단이나 의학적 판정이 아닙니다. 공개 조사 평균에 제품 내부 가정인 앱 등급 비율, 언락 횟수와 심야 사용을 적용한 초기 보정 시나리오입니다.

- 일반 성인: [KISDI 스마트폰과 TV의 시간 점유율 경쟁](https://m.kisdi.re.kr/report/view.do?arrMasterId=4333447&artId=1170836&key=m2101113025790&masterId=4333447)
- 학생: [KISDI 연령별 하루 평균 스마트폰 이용시간](https://www.kisdi.re.kr/report/fileView.do?arrMasterId=4333447&id=662716&key=m2101113025790)
- 어린이: [KISDI 스마트 기기 시대의 가정 내 미디어 규범](https://m.kisdi.re.kr/report/view.do?arrMasterId=4333447&artId=1909656&key=m2101113025790&masterId=4333447)
- 참고: [AAP Screen Time Guidelines Q&A](https://www.aap.org/en/patient-care/media-and-children/center-of-excellence-on-social-media-and-youth-mental-health/qa-portal/qa-portal-library/qa-portal-library-questions/screen-time-guidelines)

## 로드맵

- 자정 초기화 대신 연속 점수와 화면 미사용 회복 모델 적용 검토
- 삼성·샤오미 등 여러 제조사 실기기 비교 검증
- 사용자 지원 정보와 공개 개인정보처리방침 확정
- Google Play 내부 테스트용 서명 AAB 배포

## 릴리스

- 최신 공개 버전: [v1.0.60](https://github.com/Pillias/DigitsCore/releases/tag/v1.0.60)
- CI: [GitHub Actions](https://github.com/Pillias/DigitsCore/actions)
- 현재 릴리스 노트와 APK는 각 GitHub Release 페이지에서 확인할 수 있습니다.
