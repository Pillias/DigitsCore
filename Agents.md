# DigitsCore 구현 현황 및 계획

## 완료

### 측정과 데이터

- [x] 화면 ON·잠금 해제 상태의 최상단 앱 단독 집계
- [x] Activity 전환, 화면 OFF, 잠금, 종료, 재부팅 세션 경계 처리
- [x] 1분 미만 구간을 버리지 않는 이벤트 타임스탬프 기반 계산
- [x] 프로세스 시작 1회 상태 복원 후 UsageEvents 증분 처리
- [x] 앱별 실행·1분 미만 실행 일별 집계와 14일 그래프
- [x] ACTION_USER_PRESENT/KEYGUARD_HIDDEN 기반 실제 24시간 언락과 25시간 최소 이벤트 보관
- [x] 전면 앱 포착률·이벤트 조회시간·CPU 처리시간 측정 진단
- [x] 상세 세션 30일, 일별 집계 365일 보관
- [x] SQLCipher + Android Keystore 기기 내 암호화
- [x] 기존 평문 DB 무손실 이전과 에뮬레이터 회귀 테스트
- [x] AES-256-GCM 비밀번호 기반 수동 백업·복원

### 점수

- [x] 자정에 초기화되지 않는 최근 24시간 코어 지수
- [x] 80점 시작 및 전면 사용 60분 초기 보정
- [x] 5단계 비상쇄형 앱 부하
- [x] 30분 이후 연속 사용 가속과 휴식 중 급성 부하 회복
- [x] 4·5단계 심야 사용 및 언락 초과 반영
- [x] 50~90점 중앙 반응 확대와 50·70·90 고정점
- [x] 사용자 화면의 점수를 최근 24시간 코어 지수로 단일화
- [x] 기존 일일 점수 기록을 코어 지수 통계에서 분리하고 관련 설정 UI 숨김
- [x] 목적형 코어 지수 프리셋 v2와 변경 이력 표시
- [x] 기존 상세 세션 기반 일별 코어 지수 복원과 5분 단위 하루 변화 그래프

### 분석과 UI

- [x] 앱별 시간대, 세션 길이, 최장 세션, 최근 추세 그래프
- [x] 7일·4주·12주·6개월·1년 및 요일별 장기 통계
- [x] 언락 시간대와 notification interruption 비교 그래프
- [x] 앱별 5단계 균형 등급 변경
- [x] 한국어·영어 전환: 앱 화면, 알림, 위젯
- [x] 코어 숫자형을 포함한 네 가지 상태바 아이콘 스타일
- [x] 고정 B1 런처 아이콘
- [x] 크기별 반응형 위젯과 dark/white/transparent 배경
- [x] 공통 밝은·어두운 테마, 타이포, 모서리와 섹션 제목 체계
- [x] 대시보드·통계·앱 등급의 모든 주요 정보 항목 상세 진입
- [x] 밝은 테마 그래프 라벨 대비와 상세 진입 접근성 표시
- [x] 변화 원인·회복 예상·자기 과거 비교·하루 요약·한 가지 제안
- [x] 70/60/50 하향 통과 안내와 6시간 재알림 제한

### 배포와 개인정보

- [x] 앱 내 개인정보 안내, 잠금 화면 상세 숨김, 즉시 삭제
- [x] OS 자동 백업·기기 이전 제외
- [x] PR/main/tag CI에서 단위 테스트, debug, R8 release, DB 이전 검증
- [x] GitHub Release debug APK 배포
- [x] 앱·기기·측정 진단만 사용자가 검토 후 공유하는 버그 리포트
- [x] 앱 내 16항목 출시 준비 체크리스트 및 외부 검증 프로토콜

## 다음 검증

- [ ] Samsung·Pixel·Xiaomi에서 2~4주 시간 정확도·배터리 실측
- [ ] 20~50명 실제 사용자의 30일 점수 분포 검증
- [ ] 50~90점 반응 확대 적용 후 실제 사용자 분포와 변화 빈도 검토
- [ ] 코어 지수 프리셋별 실제 사용자 분포와 점수 이동 폭 검증
- [ ] 30일 이상 축적된 요일별·기간별 통계 정확성 검토
- [ ] 업로드 keystore와 GitHub Secrets 구성
- [ ] 공개 개인정보처리방침 URL 및 Google Play Data safety 확정
- [ ] Play 내부 테스트용 정식 서명 AAB 배포

최신 상태·완료 조건은 [docs/RELEASE_READINESS.md](docs/RELEASE_READINESS.md), 기기 시험은 [docs/DEVICE_VALIDATION_PROTOCOL.md](docs/DEVICE_VALIDATION_PROTOCOL.md), Play 입력은 [docs/PLAY_CONSOLE_SUBMISSION.md](docs/PLAY_CONSOLE_SUBMISSION.md), 사용자 시험은 [docs/USER_VALIDATION_PROTOCOL.md](docs/USER_VALIDATION_PROTOCOL.md)를 기준으로 합니다.

점수 상세는 [docs/SCORING.md](docs/SCORING.md), 데이터 흐름은 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)를 참고합니다.
