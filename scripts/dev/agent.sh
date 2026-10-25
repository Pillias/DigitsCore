# 작업 대기열 실행기: scripts/dev/queue/*.sh 를 순서대로 실행하고 결과를 scripts/dev/out/jobs/ 에 남깁니다.
# 이 터미널 창을 닫거나 Ctrl+C 하면 멈춥니다. 프로젝트 폴더 안에서만 동작합니다.
source scripts/dev/env.sh
mkdir -p scripts/dev/queue scripts/dev/out/jobs
echo ">> 대기열 실행기 시작 ($(date '+%H:%M:%S')). 중지: Ctrl+C"
echo "$$" > scripts/dev/out/agent.pid
while true; do
  for JOB in $(ls scripts/dev/queue/*.sh 2>/dev/null | sort); do
    NAME="$(basename "$JOB" .sh)"
    echo ">> 실행: $NAME"
    ( source scripts/dev/lib.sh; bash -c "source scripts/dev/env.sh >/dev/null; source scripts/dev/lib.sh; source '$JOB'" ) > "scripts/dev/out/jobs/$NAME.log" 2>&1
    echo "exit=$?" >> "scripts/dev/out/jobs/$NAME.log"
    mv "$JOB" "scripts/dev/out/jobs/$NAME.sh.done"
    touch "scripts/dev/out/jobs/$NAME.done"
  done
  date '+%s' > scripts/dev/out/agent.heartbeat
  sleep 1
done
