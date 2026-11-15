#!/bin/bash
# DigitsCore 개발 작업 실행기
# Finder에서 더블클릭하면 scripts/dev/next.sh 를 실행하고 결과를 scripts/dev/out/last-run.log 에 남깁니다.
# (Claude가 next.sh 내용을 작업마다 바꿔 두고 이 파일을 실행합니다. 실행 전 next.sh를 열어 확인할 수 있습니다.)
cd "$(dirname "$0")/../.." || exit 1
mkdir -p scripts/dev/out
{
  echo "== $(date '+%Y-%m-%d %H:%M:%S') : $(head -n 3 scripts/dev/next.sh | grep '^# TASK' | sed 's/^# TASK: //') =="
  bash scripts/dev/next.sh
  echo "== exit code: $? =="
} 2>&1 | tee scripts/dev/out/last-run.log
