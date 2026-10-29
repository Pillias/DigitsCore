# DigitsCore 마스코트 캐릭터 & 게이지 디자인 명세: 도룡뇽 (Salamander)

- **최종 업데이트**: 2026-10-29 (`v0.9.40` 이후 브랜치 `feature/core-index-focus` 커밋 7be87d5·be02e69·2a84f26 기준, 아직 출시 전)
- **한글 공식 명칭**: **도룡뇽** *(별도 조어 없이 직관적이고 담백한 보통명사 유지)*
- **영문 공식 명칭**: **Salamander**
- **핵심 컨셉**: 우파루파 계열 도룡뇽. 두 가지를 따로 보여 줍니다.
  - **지금 지수** → 앱 아이콘·지수 게이지에서 도룡뇽이 빨간 링을 먹은 양 (쉬면 더 많이 먹음)
  - **연속 기록** → 성장 1~4단계 그림에서 도룡뇽이 자람 (빨간 링을 먹어서 자라는 것이 아님)

---

## 1. 캐릭터 명칭 및 톤앤매너 확정 기록

### 1.1. '도룡뇽(Salamander)' 기본 명칭 유지 배경
- `GLOSSARY.md`의 원칙(*"게임식 외래어나 인위적인 조어를 쓰지 않고 담백하게 표현한다"*)에 맞춰, 별도의 캐릭터 조어 대신 **'도룡뇽(Salamander)'** 그대로 부르기로 최종 확정했습니다.
- 화면 표기 표준:
  - 한국어: `도룡뇽 1단계` ~ `도룡뇽 4단계` (`StreakRewardRow.kt`)
  - 영어: `Salamander stage 1` ~ `Salamander stage 4`

### 1.2. 이원화 시각 체계 (역할 분리)
1. **앱 아이콘 및 앱 내 코어 지수 게이지 (`CoreIndexGauge.kt`, `LauncherIconManager.kt`)**:
   - 도룡뇽 몸통이 코어 지수 비율만큼 빨간 링을 덮고, 그 끝에서 도룡뇽 머리가 남은 빨간 링을 베어 먹는 구조로 **앱 아이콘과 대시보드 메인 게이지의 조형을 100% 일치**시켰습니다.
   - 중앙 전원 막대와 도룡뇽의 **색상·표정**은 현재 코어 지수 4단계(`안정 80+ · 보통 60~79 · 주의 40~59 · 휴식 필요 40 미만`)를 실시간으로 반영합니다.
2. **연속 달성 성장 아이콘 (`SalamanderStageIcon.kt`, `res/drawable/ic_salamander_stage1~4.xml`)**:
   - 오늘의 목표 연속 달성(기본 · 7일 · 14일 · 30일)에 따른 성장은 **색상을 고정(초록)하고 오직 '모양(실루엣)'으로만 1~4단계를 구분**합니다.
   - 우파루파(Axolotl)처럼 다 자라도 귀엽게 웃는 실눈·볼·꽃잎 아가미는 그대로 유지됩니다:
     - **1단계 (기본 · 0~6일)**: 유생 단계 — 큰 꽃잎 아가미와 꼬리 지느러미 중심의 작고 둥근 형태
     - **2단계 (연속 7일)**: 어린 도룡뇽 — 네 다리가 나고, 아가미·꼬리 지느러미가 작아진 형태
     - **3단계 (연속 14일)**: 도룡뇽 — 몸통과 꼬리가 길어지고 꼬리 지느러미가 사라진 형태 (아가미는 작게 남음)
     - **4단계 (연속 30일)**: 다 자란 완전체 — 긴 몸통과 꼬리로 원을 둥글게 감싸 안는 형태 (**앱 아이콘/코어 게이지의 원형 링 형태와 시각적으로 연결됨**)

### 1.3. 하지 않는 것 (근거 보관)
- **아이콘 4종 ↔ 성장 4단계 1:1 매칭 안 함**: 지수는 하루에도 여러 번 바뀌므로 맞추면 점수가 잠깐 떨어질 때 도룡뇽이 유생으로 돌아간 것처럼 보여 '쌓은 성장은 사라지지 않는다' 원칙과 부딪힘. 모양×색을 나누면 아이콘이 4×4=16종이 되어 런처 부담도 큼. 대신 **아이콘 도룡뇽 = 다 자란 모습**, 성장 그림은 그 모습까지 자라 가는 과정.
- **성장 그림에 지수 단계별 색·표정 넣지 않음**: 지수 단계는 아이콘·게이지가 이미 보여 줌. (아이콘·게이지의 단계별 색·표정은 기존 아이콘 그대로 유지)
- **점무늬, 금색 보석·뿔·광채 장식, 단계 이름(아기·새싹·수호·마스터) 쓰지 않음**: 작은 크기에서 뭉개지고 모양 구분 원칙과 맞지 않음.
- 문서의 기호(💧 ⌒ ⊙ 등)는 화면에 쓰지 않음 (디자인 점검 기준).

---

## 2. `v0.9.40` 이후 수정분(아이콘 · 앱 내 게이지 · 캐릭터 · 위젯) 코드 검토 결과

### 2.1. 잘된 점 (긍정적 평가)
1. **앱 아이콘 ↔ 앱 내 메인 게이지(`CoreIndexGauge.kt`)의 완전한 시각 통일**:
   - 이전에는 앱 아이콘에만 도룡뇽이 있고 앱 안의 `CoreIndexGauge`는 단순 기하학적 원호여서 안팎의 인상이 달랐으나, 이제 `drawSalamanderGauge`와 `rememberGaugeHeads`를 통해 대시보드 게이지에서도 도룡뇽이 빨간 링을 먹는 모습이 그대로 살아났습니다.
   - (참고) 점수 숫자와 단계 글씨가 게이지 아래(`Column`)에 있는 배치는 이전 버전부터 그대로이며 이번 변경 사항은 아닙니다. 머리 그림은 아이콘 4종의 머리를 그대로 잘라 씁니다(`res/drawable-nodpi/gauge_head_*.png`).
2. **성장 단계 아이콘의 빌드 파이프라인화 (`SalamanderStageIcon.kt`)**:
   - `scripts/art/gen_salamander_stages.py → svg_to_vector.py`를 통해 `res/drawable/ic_salamander_stage1~4.xml` 벡터 드로어블로 생성·관리하도록 구조화하여, 런타임 드로잉 부하 없이 모든 해상도에서 일관된 품질을 보장합니다.
   - 특히 4단계("다 자라 꼬리로 원을 감음")가 대시보드 메인 코어 게이지의 원형 도룡뇽과 자연스럽게 프리퀄→완전체 서사로 이어지는 점이 매우 설득력 있습니다.
3. **(참고, 이전 버전 반영분) `LauncherIconManager.kt`의 하루 1회 제한 우회 버그 수정** — v0.9.34에서 반영된 내용으로, 이번 변경에는 포함되지 않습니다:
   - 앞서 지적했던 `setEnabled()` 호출 시 `KEY_LAST_UPDATED_DATE`가 초기화되던 문제가 해결되어, 사용자가 설정 토글을 껐다 켜도 하루 1회 변경 제한이 안전하게 유지됩니다.
   - 전체 데이터 삭제 시 `scheduleResetToDefault()`로 다음 화면 OFF 시점에 기본(노랑) 아이콘으로 복귀시키는 안전장치도 깔끔하게 반영되었습니다.

### 2.2. 확인 및 주의가 필요한 점검 포인트 3가지

| 구분 | 대상 파일 및 위치 | 점검 내용 및 권장 확인 사항 |
| :--- | :--- | :--- |
| **점검 1**<br>*(레이아웃 높이)* | `CoreIndexGauge.kt` (53~85행) 및 호출부(`DashboardScreen.kt`) | • `CoreIndexGauge`가 기존에는 원형 Canvas 하나였으나, 이제 `Column` 안에 `Canvas(iconSize)` + `Spacer(4.dp)` + `점수 텍스트` + `단계 텍스트`가 세로로 배치됩니다.<br>• 만약 호출부에서 `CoreIndexGauge(modifier = Modifier.size(...))`처럼 고정 높이를 주면 아래로 내려간 점수·단계 글씨가 잘리거나 Canvas가 찌그러질 수 있으므로, 호출부에서는 `iconSize` 파라미터로만 크기를 조절하고 `modifier`에는 고정 `height`/`size`를 주지 않는지 확인이 필요합니다. |
| **점검 2**<br>*(0점 시작 애니메이션)* | `CoreIndexGauge.kt` (47~58행) | • `celebrate = true`(단계 상승 축하)일 때 `Animatable`이 `0f`에서 출발하여 `drawSalamanderGauge(animatedScore = 0f, ...)`가 호출됩니다.<br>• `animatedScore`가 `0f ~ 5f` 사이일 때 도룡뇽 몸통 원호 길이가 매우 짧아지는데, 이때 머리 그림(`heads`)이 12시 전원 막대와 겹치거나 각도가 급격히 꺾이지 않는지 첫 프레임을 눈으로 확인해 두면 안전합니다. |
| **점검 3**<br>*(영어 내 기록 단위)* | `StreakRewardRow.kt` (85~86행) | • 86행 `best.minUnlocks?.let { add(if (isEn) "Fewest unlocks $it (${short(...)})" else "잠금 해제 최소 ${it}회 (${short(...)})") }`에서 영어 문구에는 숫자 뒤에 단위(`x` 또는 `times`)가 생략되어 있습니다(예: `Fewest unlocks 12 (10/28)`). 그대로도 의미는 통하지만 `Fewest unlocks ${it}x` 또는 `${it} times`로 맞추면 앞의 `${it}min`, 뒤의 `${bestStreak}d`와 단위 리듬이 더 잘 맞습니다. |

### 2.3. 점검 포인트 확인 결과 (Claude, 2026-10-29)
| 점검 | 결과 |
| :--- | :--- |
| 1. 호출부 높이 제약 | 문제없음. `DashboardScreen.kt`의 세 호출부는 모두 `iconSize`로만 크기를 정하고, `modifier`는 너비만 지정(96dp·80dp)하며 높이를 고정하지 않음. |
| 2. `celebrate` 0f 시작 | 문제없음. `drawSalamanderGauge`가 점수를 최소 6P로 보정해 머리가 링 시작점(막대 오른쪽)에 놓이고 막대와 겹치지 않음. 애니메이션 중 머리 그림은 최종 점수의 단계 그림을 씀. |
| 3. 영어 내 기록 단위 | 문제없음. 새 횟수 기준(영어는 라벨 뒤 숫자만)에 맞음: `Fewest unlocks 12 (10/28)`. |

---

## 3. 파일 · 생성 스크립트
| 대상 | 파일 | 만드는 방법 |
| :--- | :--- | :--- |
| 앱 아이콘 4종 | `res/drawable/ic_launcher_foreground*.png` | 원본 + 등줄기 선: `scripts/art/icon_stripe_overlay.py` |
| 원래 아이콘 백업 | `store_assets/icon_original_v0940/` | 되돌리는 방법은 같은 폴더 README |
| 게이지 머리 4종 | `res/drawable-nodpi/gauge_head_*.png` | 원래 아이콘에서 잘라 냄: `scripts/art/gauge_heads.py` |
| 지수 게이지 | `ui/components/SalamanderGauge.kt`, `CoreIndexGauge.kt`, 색 `ui/theme/SalamanderColors.kt` | 코드 (위젯용 그림은 `SalamanderGaugeBitmap`) |
| 성장 1~4단계 | `res/drawable/ic_salamander_stage1~4.xml` | `scripts/art/gen_salamander_stages.py` → `svg_to_vector.py` (원본 SVG `store_assets/coryong/v3/`) |
| 위젯 4종 | `widget/ScoreWidget.kt`, `res/xml/score_widget*_info.xml` | 1x1 점수 · 2x1 게이지·점수·단계 · 2x2 + 할 일 · 4x2 + 오늘의 목표 |

## 4. 남은 일
- 실기기 확인: 게이지(90P 이상·15P 이하 머리 위치), 위젯 4종(특히 1x1·2x1 최종 화면), 큰 글꼴 130%.
- 스토어 512px 아이콘(`store_assets/icon_512.png`)을 등줄기 선 아이콘에 맞춰 다시 만들기.
