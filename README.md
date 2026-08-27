# 📱 DigitsCore

> 스마트폰 사용 습관을 0~100점의 실시간 점수로 게이미피케이션하여 자율적인 디지털 디톡스를 유도하는 Android 앱

---

## ✨ 핵심 기능

- 🎯 **실시간 디톡스 스코어링 엔진:** 0~100점 점수 시스템, 생산성 앱 가산(+), 방해 앱 페널티(-), 화면 미사용(Idle) 회복 보너스, 언락 초과 페널티
- 🔔 **동적 상태바 숫자 알림:** Canvas 기반으로 0~100점 숫자를 원형 뱃지 비트맵으로 렌더링하여 상태바에 실시간 표시
- ⚡ **배터리 최적화 백그라운드 트래킹:** 화면 OFF 시 절전 모드, 화면 ON/언락 시 일괄 정산
- 📊 **주간/월간 통계 & 리포트:** Canvas 점수 추세 차트 및 디톡스 목표 달성률 분석
- ⚙️ **맞춤 모드 설정:** 공부 모드, 눈 건강 모드, 직장인 모드, 어린이 모드, 밸런스 모드

---

## 🛠️ 기술 스택

- **Language:** Kotlin 1.9+
- **UI:** Jetpack Compose (Material 3)
- **Architecture:** Clean Architecture + MVVM/MVI
- **Database:** Room Database
- **System APIs:** `UsageStatsManager`, `NotificationManager`, `ForegroundService`

---

## 🚀 시작하기

1. Android Studio에서 프로젝트 열기 (`DigitsCore`)
2. 디바이스 또는 에뮬레이터 연결
3. Run (`Shift + F10`)
