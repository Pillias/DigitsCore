# 📱 DigitsCore

> 스마트폰 사용 습관을 0~100점의 실시간 점수로 게이미피케이션하여 자율적인 디지털 디톡스를 유도하는 Android 앱입니다. 사용 시간과 언락 횟수는 Android가 제공하는 UsageStats 이벤트를 기반으로 한 근사치이며 기기별로 차이가 날 수 있습니다.

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

---

## 📐 점수 보정 기준

직장인·학생·어린이 프리셋의 60점 기준은 건강 진단 기준이 아니라 공개 조사 평균을 활용한 초기 보정 시나리오입니다.
조사상의 전체 사용시간에 제품 내부 가정(방해/생산성 앱 비율, 언락 횟수, 심야 사용)을 적용해 약 60점이
나오도록 기본 가중치를 맞추며, 사용 목적과 생활 패턴에 따라 설정 화면에서 모든 주요 계수를 조정할 수 있습니다.

- 일반 성인: [KISDI 스마트폰과 TV의 시간 점유율 경쟁](https://m.kisdi.re.kr/report/view.do?arrMasterId=4333447&artId=1170836&key=m2101113025790&masterId=4333447) — 2023년 스마트폰 하루 평균 126.4분(음성통화 제외)
- 학생: [KISDI 연령별 하루 평균 스마트폰 이용시간](https://www.kisdi.re.kr/report/fileView.do?arrMasterId=4333447&id=662716&key=m2101113025790) — 2021년 10대 약 142분
- 어린이: [KISDI 스마트 기기 시대의 가정 내 미디어 규범](https://m.kisdi.re.kr/report/view.do?arrMasterId=4333447&artId=1909656&key=m2101113025790&masterId=4333447) — 2024년 가정 내 하루 허용시간 평균 1시간 46분
- 연령과 맥락을 무시한 단일 제한시간은 근거가 부족하다는 [AAP 최신 안내](https://www.aap.org/en/patient-care/media-and-children/center-of-excellence-on-social-media-and-youth-mental-health/qa-portal/qa-portal-library/qa-portal-library-questions/screen-time-guidelines)도 함께 반영했습니다.
