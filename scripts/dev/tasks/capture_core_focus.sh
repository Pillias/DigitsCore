#!/usr/bin/env bash
# 코어 지수 집중 UX 캡처: 대시보드·통계·주간 리포트 (밝음/어두움 × 100/130% × 한/영)
source scripts/dev/env.sh
source scripts/dev/lib.sh
O=scripts/dev/out/core_index_focus; mkdir -p $O
P=com.digitscore.app.debug
setlang() { adb -e shell "run-as $P sh -c 'echo \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\"?><map><string name=\\\"language\\\">$1</string></map>\" > shared_prefs/app_locale.xml'"; }
suite() { # $1 tag $2 lang $3 night(yes/no) $4 font
  adb -e shell settings put system font_scale $4
  adb -e shell "cmd uimode night $3"
  setlang $2
  adb -e shell am force-stop $P; sleep 1; app $P; sleep 6
  adb -e exec-out screencap -p > $O/${1}_01_dashboard.png
  swipe 540 1700 540 700 400; sleep 2
  adb -e exec-out screencap -p > $O/${1}_02_dashboard_scrolled.png
  swipe 540 700 540 1900 300; sleep 1
  adb -e shell am start -a com.digitscore.app.action.OPEN_WEEKLY_REPORT -n $P/com.digitscore.app.ui.MainActivity >/dev/null; sleep 3
  adb -e exec-out screencap -p > $O/${1}_04_weekly_report.png
  key 4; sleep 1
  if [ "$2" = ko ]; then tapt "통계"; else tapt "Stats"; fi; sleep 4
  adb -e exec-out screencap -p > $O/${1}_03_statistics.png
  key 4; sleep 1
}
suite ko_light_100 ko no 1.0
suite ko_dark_130 ko yes 1.30
suite en_light_130 en no 1.30
adb -e shell settings put system font_scale 1.0; adb -e shell "cmd uimode night no"; setlang ko
ls $O
