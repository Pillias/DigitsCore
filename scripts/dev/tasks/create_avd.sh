# AVD를 avdmanager 없이 직접 만듭니다 (Homebrew avdmanager의 devices.xml 오류 회피)
source scripts/dev/env.sh
[ -n "$EMU_SDK" ] || { echo "에뮬레이터 또는 시스템 이미지를 찾을 수 없습니다"; exit 1; }
mkdir -p "$AVD_HOME/$AVD_NAME.avd"
cat > "$AVD_HOME/$AVD_NAME.ini" <<INI
avd.ini.encoding=UTF-8
path=$AVD_HOME/$AVD_NAME.avd
path.rel=avd/$AVD_NAME.avd
target=android-35
INI
cat > "$AVD_HOME/$AVD_NAME.avd/config.ini" <<CFG
AvdId=$AVD_NAME
avd.ini.displayname=DigitsCore API35
avd.ini.encoding=UTF-8
abi.type=arm64-v8a
hw.cpu.arch=arm64
hw.cpu.ncore=4
image.sysdir.1=$SYS_IMAGE_DIR/
tag.id=google_apis
tag.display=Google APIs
target=android-35
hw.lcd.width=1080
hw.lcd.height=2340
hw.lcd.density=450
hw.ramSize=2048
vm.heapSize=256
hw.keyboard=yes
hw.gpu.enabled=yes
hw.gpu.mode=auto
disk.dataPartition.size=4G
PlayStore.enabled=false
showDeviceFrame=no
skin.dynamic=yes
CFG
echo ">> AVD 준비: $AVD_HOME/$AVD_NAME.avd"
ANDROID_SDK_ROOT="$EMU_SDK" "$EMULATOR_BIN" -list-avds
