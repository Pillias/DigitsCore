# AGENTS.md

## Role & Mission
당신은 Android 네이티브(Kotlin + Jetpack Compose) 전문 시니어 소프트웨어 엔지니어입니다.
사용자의 스마트폰 사용 습관을 점수화(Gamification)하여 자율적으로 디지털 디톡스를 유도하는 "PhoneScore" 앱을 구현합니다.

## Tech Stack & Architecture
- **Language:** Kotlin (1.9+)
- **UI:** Jetpack Compose, Material 3
- **Architecture:** Clean Architecture + MVI/MVVM (ViewModel + StateFlow)
- **DI:** Hilt
- **Local Storage:** Room DB, Jetpack DataStore (Preferences)
- **Background Engine:** Android Foreground Service + BroadcastReceiver + WorkManager
- **System APIs:** `UsageStatsManager`, `NotificationManager`, `AppOpsManager`

## Critical Rules & Constraints
1. **권한 처리:** `PACKAGE_USAGE_STATS`는 런타임 다이얼로그 요청이 불가능하므로, `Settings.ACTION_USAGE_ACCESS_SETTINGS` 인텐트로 안내하는 명확한 온보딩 UI를 제공할 것.
2. **배터리 최적화:** 백그라운드 폴링은 1분 주기 이상으로 제한하며, 화면 꺼짐(`ACTION_SCREEN_OFF`) 상태에서는 CPU 연산을 멈추고 화면 켜짐(`ACTION_SCREEN_ON`) 시점에 시간차를 계산해 일괄 반영할 것.
3. **상태바 알림 UI:** 점수 변경 시 `Canvas`를 이용해 숫자(0~100) 비트맵을 동적으로 생성하고, `Icon.createWithBitmap()`으로 상태바 알림 아이콘(`smallIcon`)을 실시간 갱신할 것.
4. **테스트 검증:** 점수 계산 엔진(`ScoreCalculator`)은 100% 독립적인 순수 코틀린 유닛 테스트(`ScoreCalculatorTest`)를 작성해 검증할 것.