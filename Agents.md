# Implementation Plan - PhoneScore / DigitsCore (Android MVP)

## Phase 1: Core Domain & Scoring Engine
- [x] Task 1.1: 도메인 모델 정의 (`AppUsage`, `AppCategoryType`, `ScoreRule`, `PresetMode`)
- [x] Task 1.2: `ScoreCalculator` 엔진 구현 (가중치 계산, Idle 보너스, Unlock 페널티)
- [x] Task 1.3: `ScoreCalculatorTest` 단위 테스트 작성 및 엣지 케이스(점수 0~100 경계값) 검증

## Phase 2: Room Database & Data Layer
- [x] Task 2.1: Room Entity 생성 (`AppWeightEntity`, `DailyScoreHistoryEntity`, `UserSettingsEntity`)
- [x] Task 2.2: `UsageStatsManager` 래퍼 구현 (최근 앱 전환 이벤트 파싱 로직)
- [x] Task 2.3: 기본 프리셋 모드(공부/눈보호/어린이/직장인) 기본값 DB 마이그레이션

## Phase 3: Background Tracking & Dynamic Notification
- [x] Task 3.1: `ScreenEventReceiver` (SCREEN_ON, SCREEN_OFF, USER_PRESENT) 구현
- [x] Task 3.2: `ScoreNotificationManager`에 Canvas 기반 Dynamic Number Bitmap 아이콘 생성기 구현
- [x] Task 3.3: `TrackerForegroundService` 작성 (1분 주기 집계 + 상태바 알림 업데이트)

## Phase 4: UI Development (Jetpack Compose)
- [x] Task 4.1: 권한 요청/온보딩 화면 (`PACKAGE_USAGE_STATS` 및 알림 권한 가이드)
- [x] Task 4.2: 대시보드 화면 (원형 점수 인디케이터, 언락 횟수, 실시간 앱 사용 TOP 5)
- [x] Task 4.3: 앱 분류/가중치 매핑 설정 UI
- [x] Task 4.4: 모드 변경 및 개인 목표 설정(최저 점수 방어선, 언락 목표치) UI

## Phase 5: Verification & Walkthrough
- [x] Task 5.1: 백그라운드 장기 실행 및 Doze 모드 동작 최적화 (Screen OFF 시 타이머 절전)
- [x] Task 5.2: 앱 실행/종료 시 점수 변동 및 상태바 아이콘 갱신 검증 로직 구현
- [x] Task 5.3: 최종 Walkthrough 문서 작성 및 프로젝트 아키텍처 완성