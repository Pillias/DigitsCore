# debug APK를 빌드해 에뮬레이터에 설치하고 테스트용 권한을 부여합니다.
set -e
source scripts/dev/env.sh
# 모든 빌드에서 버전이 1 올라갑니다 (같은 버전의 다른 빌드 방지).
./gradlew :app:assembleDebug --no-daemon -q
APK="$(ls -t "$APK_DIR"/*.apk | head -n 1)"
echo ">> 설치: $APK"
adb -e install -r -t "$APK"
# 온보딩 수동 설정을 줄이기 위해 테스트 기기에만 권한을 직접 부여합니다.
adb -e shell appops set "$APP_ID" GET_USAGE_STATS allow
adb -e shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow
adb -e shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb -e shell dumpsys deviceidle whitelist +"$APP_ID" >/dev/null 2>&1 || true
pkill -f "adb -e logcat" 2>/dev/null || true
adb -e logcat -c
nohup adb -e logcat -v time > scripts/dev/out/logcat.txt 2>&1 &
adb -e shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
echo ">> 설치 및 실행 완료 (logcat → scripts/dev/out/logcat.txt)"
