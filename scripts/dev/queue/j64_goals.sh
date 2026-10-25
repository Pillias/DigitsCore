export JAVA_HOME=/opt/homebrew/opt/openjdk@17
P=com.digitscore.app.debug
./gradlew :app:testDebugUnitTest --no-daemon -q 2>&1 | grep -v "SDK XML\|daemon\|Kotlin compile" | tail -20
echo "test_exit=${PIPESTATUS[0]}"
source scripts/dev/tasks/build_install.sh
grep -E "PATCH|DEBUG_BUILD" version.properties
adb -e shell settings put system font_scale 1.0
adb -e shell cmd uimode night no
adb -e shell cmd window user-rotation lock 0 >/dev/null 2>&1
adb -e shell am force-stop $P; app $P; sleep 7
tap 731 158; sleep 3; shot g01_goal_edit
texts | grep -E "P|min|x|평균|지금|오늘" | head -20
tapc "YouTube"; sleep 2; shot g02_goal_edit_yt
key 4; sleep 1
adb -e shell cmd uimode night yes; sleep 2
adb -e shell am force-stop $P; app $P; sleep 7; tap 731 158; sleep 3; shot g03_goal_edit_dark; key 4
adb -e shell cmd uimode night no
adb -e logcat -d -t 3000 | grep -E "FATAL EXCEPTION|E AndroidRuntime" -A6 | tail -20
echo done
