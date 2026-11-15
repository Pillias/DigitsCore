# 에뮬레이터를 켜고 부팅 완료까지 기다립니다. (이미 켜져 있으면 그대로 사용)
source scripts/dev/env.sh
if ! adb devices | grep -q "^emulator-"; then
  # 에뮬레이터는 ANDROID_HOME을 우선하므로 시스템 이미지가 있는 SDK로 함께 지정합니다.
  ANDROID_HOME="$EMU_SDK" ANDROID_SDK_ROOT="$EMU_SDK" nohup "$EMULATOR_BIN" -avd "$AVD_NAME" -no-boot-anim -no-snapshot-save > scripts/dev/out/emulator.log 2>&1 &
  EMU_PID=$!
  echo ">> 에뮬레이터 시작 중... (pid $EMU_PID)"
fi
for i in $(seq 1 150); do
  [ "$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  if [ -n "$EMU_PID" ] && ! kill -0 "$EMU_PID" 2>/dev/null; then
    echo "에뮬레이터가 종료되었습니다:"; tail -n 20 scripts/dev/out/emulator.log; exit 1
  fi
  sleep 3
done
[ "$(adb -e shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] || { echo "부팅 시간 초과"; tail -n 30 scripts/dev/out/emulator.log; exit 1; }
echo ">> 에뮬레이터 준비 완료: Android $(adb -e shell getprop ro.build.version.release | tr -d '\r')"
