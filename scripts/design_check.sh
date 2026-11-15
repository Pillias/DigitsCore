#!/usr/bin/env bash
# DigitsCore 디자인 기준 점검 (docs/DESIGN_SYSTEM.md, docs/GLOSSARY.md)
# 사용법: ./scripts/design_check.sh          → 개수 보고
#         STRICT=1 ./scripts/design_check.sh → 쓰지 않는 용어·기호가 있으면 실패
set -uo pipefail
SRC="app/src/main"
UI="$SRC/java/com/digitscore/app"
THEME="$UI/ui/theme"
fail=0

count() { grep -rhoE "$1" --include=*.kt "$UI" | grep -v "^\s*//" | wc -l | tr -d ' '; }

echo "== 테마 밖 직접 지정 값 (줄여 갈 대상)"
raw_sp=$(grep -rhoE '(fontSize|textSize) = [0-9.]+\.sp' --include=*.kt "$UI" | wc -l | tr -d ' ')
echo "글자 크기 직접 지정(fontSize·textSize): $raw_sp"; [ "$raw_sp" != "0" ] && fail=1
echo "Color(0x…) 직접 지정: $(grep -rlE 'Color\(0x' --include=*.kt "$UI" | grep -v "$THEME" | xargs -r grep -hoE 'Color\(0x[0-9A-Fa-f]{8}\)' | wc -l | tr -d ' ')"
raw_shape=$(grep -rlE 'RoundedCornerShape\([0-9.]+\.dp\)' --include=*.kt "$UI" | grep -v "$THEME" | xargs -r grep -hoE 'RoundedCornerShape\([0-9.]+\.dp\)' | wc -l | tr -d ' ')
echo "모서리 직접 지정(RoundedCornerShape): $raw_shape"; [ "$raw_shape" != "0" ] && fail=1
echo "FontWeight SemiBold/ExtraBold/Black: $(grep -rhoE 'FontWeight\.(SemiBold|ExtraBold|Black)' --include=*.kt "$UI" | wc -l | tr -d ' ')"
echo "Filled/Outlined 아이콘: $(grep -rhoE 'Icons\.(Default|Filled|Outlined)\.' --include=*.kt "$UI" | wc -l | tr -d ' ')"

echo "== 쓰지 않는 용어 (docs/GLOSSARY.md)"
for w in "누적 피로도" "연속 사용 가속 (min)" "언락" "잠금해제" "코어지수" "화면시간" "사용시간" "몰입 관리" "몰입관리" "방해 앱" "습관성 오픈" "습관적 열람" "순간 확인" "짧은 실행" "3대 실천 목표" "실천 목표" "집중 관리할 앱" "프리셋" "스트릭" "퍼펙트 데이" "올 클리어" "OS 감지 알림" "병렬 화면" "Immersion Management" "주간 보고서" "첫 실행 앱" "알림 유발 잠금" "스케줄 규칙" "전체 관리 한도" "과거 기록" "이전 기록"; do
  n=$(grep -rhoF "$w" --include=*.kt --include=*.xml "$SRC" | wc -l | tr -d ' ')
  if [ "$n" != "0" ]; then echo "  $w: $n"; fail=1; fi
done

echo "== 화면 안 기호 글자 (ui/)"
n=$(find "$UI/ui" -name '*.kt' -exec perl -CSD -ne 'next if /^\s*\/\//; $c += () = /[\x{2713}\x{25B2}\x{25BC}\x{25B8}\x{2191}\x{2193}\x{21BB}\x{25BE}\x{2715}]/g; END { print $c+0, "\n" }' {} + | awk '{s+=$1} END {print s+0}')
echo "  기호: $n"; [ "$n" != "0" ] && fail=1

if [ "${STRICT:-0}" = "1" ] && [ "$fail" = "1" ]; then echo "STRICT: 기준 위반"; exit 1; fi
exit 0
