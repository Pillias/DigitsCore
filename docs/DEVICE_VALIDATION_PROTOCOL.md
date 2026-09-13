# 실기기 정확도·배터리 검증 프로토콜

## 목적과 범위

Samsung, Pixel, Xiaomi에서 DigitsCore가 화면 ON·잠금 해제 중 최상단 앱만 집계하는지, 증분 수집이 누락 없이 유지되는지, 지속 추적의 비용이 허용 가능한지 14~28일 동안 확인합니다.

## 최소 시험 구성

| 구분 | 최소 조건 |
|---|---|
| 기기 | Samsung 1대, Pixel 1대, Xiaomi 1대 |
| Android | 지원 범위 안에서 가능한 서로 다른 최신 OS 2개 이상 |
| 기간 | 기기당 14일, 권장 28일 |
| 앱 빌드 | 같은 서명 release 후보 AAB에서 설치한 동일 버전 |
| 전원 정책 | 기본 정책 7일 + 제조사 절전 예외 허용 7일을 구분 기록 |

## 매일 기록할 값

1. DigitsCore의 전체 전면 사용시간, 상위 앱별 시간, 실제 이벤트 언락 수
2. 기기 Digital Wellbeing의 같은 시간 범위 전체/앱별 시간과 잠금 해제 수
3. 앱의 `측정 정확도와 처리 비용` 또는 버그 리포트에 표시된 포착률, 이벤트 조회 시간, CPU 시간, 측정 횟수
4. Android 설정의 DigitsCore 일일 배터리 사용률과 백그라운드 시간
5. 재부팅·권한 변경·강제 종료·절전 진입과 누락 의심 시각

분 단위 표시는 반올림 차이가 있으므로 가능하면 원시 초 단위를 사용하고, 그렇지 않으면 비교 오차 허용치에 ±59초를 명시합니다.

## 계산식

- 앱별 절대 오차(분): `|DigitsCore - 기준값|`
- 전체 MAE: 모든 앱·날짜의 절대 오차 평균
- MAPE: `|DigitsCore - 기준값| / max(기준값, 1분) × 100`
- 전면 포착률: `전면 앱에 배정한 시간 / 화면 ON·잠금 해제 관측 가능 시간 × 100`
- 언락 오차: `DigitsCore 실제 이벤트 수 - 기준 언락 수`
- 일일 배터리 비용: Android 설정에서 보고한 DigitsCore 배터리 비율. 기기별 중앙값과 95백분위를 함께 기록

Digital Wellbeing도 제조사별 집계 방식이 달라 절대적인 원본은 아닙니다. 불일치가 생기면 5~10분의 통제된 시나리오(잠금 해제 → 앱 A → 앱 B → 홈 → 잠금)를 화면 녹화하고 이벤트 시각과 대조합니다.

## 통과 기준 초안

- 통제 시나리오에서 잘못된 전면 앱 배정 0건
- 일일 전체 사용시간 MAE 5분 이하 또는 MAPE 5% 이하 중 더 관대한 값
- 5분 이상 사용한 개별 앱 MAPE 중앙값 10% 이하
- 실제 언락 이벤트 누락/중복 일일 2회 이하. 제조사 API가 이벤트를 제공하지 않은 경우 별도 표기
- 전면 포착률 중앙값 95% 이상
- 앱 일일 배터리 중앙값 2% 이하, 95백분위 4% 이하를 초기 목표로 사용하되 기기 용량/사용량과 함께 해석
- Crash 0, ANR 0, 추적 서비스의 설명 없는 장기 중단 0

기준을 넘기면 평균으로 숨기지 않고 제조사·OS·절전 설정별 이슈로 분리합니다.

## 결과 템플릿

```text
Device / OS / build:
Test dates:
Power policy:
Days observed:
Total-time MAE / MAPE:
App-time median MAPE / P95:
Foreground coverage median / P5:
Unlock daily MAE:
Battery daily median / P95:
Crashes / ANRs / service gaps:
Issue links and conclusion:
```
