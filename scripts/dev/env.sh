# 공통 환경: Java, Android SDK 경로
for J in /opt/homebrew/opt/openjdk@17 "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$(/usr/libexec/java_home -v 17 2>/dev/null)"; do
  if [ -n "$J" ] && [ -x "$J/bin/java" ]; then export JAVA_HOME="$J"; break; fi
done
# 빌드용 SDK (local.properties의 sdk.dir과 동일)
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
SYS_IMAGE="system-images;android-35;google_apis;arm64-v8a"
SYS_IMAGE_DIR="system-images/android-35/google_apis/arm64-v8a"
# 에뮬레이터용 SDK: 시스템 이미지가 설치된 쪽을 사용합니다 (Homebrew sdkmanager는 자체 경로에 설치함)
EMU_SDK=""
for R in "$ANDROID_HOME" /opt/homebrew/share/android-commandlinetools; do
  if [ -x "$R/emulator/emulator" ] && [ -d "$R/$SYS_IMAGE_DIR" ]; then EMU_SDK="$R"; break; fi
done
export PATH="$ANDROID_HOME/platform-tools:$JAVA_HOME/bin:$PATH"
AVD_NAME="DigitsCore_API35"
AVD_HOME="$HOME/.android/avd"
APP_ID="com.digitscore.app.debug"
APK_DIR="app/build/outputs/apk/debug"
EMULATOR_BIN="$EMU_SDK/emulator/emulator"
echo "JAVA_HOME=$JAVA_HOME"
echo "ANDROID_HOME=$ANDROID_HOME"
echo "EMU_SDK=${EMU_SDK:-(없음)}"
