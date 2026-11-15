#!/usr/bin/env bash
set -e
source scripts/dev/env.sh
source scripts/dev/lib.sh

mkdir -p scripts/dev/out/parity

# Ensure font scale 1.30
adb -e shell settings put system font_scale 1.30

# Ensure English locale
adb -e shell "run-as com.digitscore.app.debug sh -c 'echo \"<?xml version=\\\"1.0\\\" encoding=\\\"utf-8\\\" standalone=\\\"yes\\\"?><map><string name=\\\"language\\\">en</string></map>\" > shared_prefs/app_locale.xml'"

capture_suite() {
  local THEME="$1" # "light" or "dark"
  echo "=== Capturing $THEME theme suite ==="
  
  if [ "$THEME" = "dark" ]; then
    adb -e shell "cmd uimode night yes"
  else
    adb -e shell "cmd uimode night no"
  fi
  sleep 1

  # Restart app cleanly on Dashboard
  adb -e shell am force-stop com.digitscore.app.debug
  adb -e shell monkey -p com.digitscore.app.debug -c android.intent.category.LAUNCHER 1
  sleep 4

  # 1. Screen Time Detail (difference card)
  echo ">> 1. Screen time detail ($THEME)"
  tap 300 980
  sleep 2
  adb -e exec-out screencap -p > "scripts/dev/out/parity/01_screen_time_detail_${THEME}.png"
  key 4
  sleep 1

  # 2. Unlock Detail
  echo ">> 2. Unlock detail ($THEME)"
  tap 645 980
  sleep 2
  adb -e exec-out screencap -p > "scripts/dev/out/parity/02_unlock_detail_${THEME}.png"
  key 4
  sleep 1

  # 3. Goal Settings (Weekdays / Weekends / All managed apps)
  echo ">> 3. Goal settings ($THEME)"
  tap 885 1238 # Details on today's goals
  sleep 1
  tap 757 1528 # Edit goals button
  sleep 1
  # Ensure separate weekday / weekend is turned on (check switch bounds)
  tap 849 486
  sleep 1
  # Tap Weekday tab
  tap 424 694
  sleep 1
  adb -e exec-out screencap -p > "scripts/dev/out/parity/05a_goal_settings_weekdays_${THEME}.png"
  # Tap Weekend tab
  tap 817 694
  sleep 1
  adb -e exec-out screencap -p > "scripts/dev/out/parity/05b_goal_settings_weekends_${THEME}.png"
  key 4
  sleep 1
  key 4
  sleep 1

  # 4. Statistics 5 chips
  echo ">> 4. Statistics 5 chips ($THEME)"
  tap 535 189 # Stats top icon
  sleep 3
  # Scroll down to app ranking section
  swipe 500 1800 500 500 400
  sleep 2
  adb -e exec-out screencap -p > "scripts/dev/out/parity/03_statistics_apps_5chips_${THEME}.png"
  key 4
  sleep 1

  # 5. Weekly Report
  echo ">> 5. Weekly report ($THEME)"
  adb -e shell am start -a com.digitscore.app.action.OPEN_WEEKLY_REPORT -n com.digitscore.app.debug/com.digitscore.app.ui.MainActivity
  sleep 2
  adb -e exec-out screencap -p > "scripts/dev/out/parity/04_weekly_report_${THEME}.png"
  key 4
  sleep 1

  # 6. Limit Notification
  echo ">> 6. Limit notification ($THEME)"
  adb -e shell am start -n com.digitscore.app.debug/com.digitscore.app.ui.MainActivity --ez show_goal_limit_notification true
  sleep 1
  adb -e shell cmd statusbar expand-notifications
  sleep 2
  adb -e exec-out screencap -p > "scripts/dev/out/parity/06_goal_limit_notification_${THEME}.png"
  adb -e shell cmd statusbar collapse
  sleep 1

  # 7. Time Rules Section & Edit Dialog
  echo ">> 7. Settings Time Rules ($THEME)"
  adb -e shell am start -n com.digitscore.app.debug/com.digitscore.app.ui.MainActivity
  sleep 2
  tap 984 189 # Settings icon
  sleep 2
  tap 660 346 # Alerts tab
  sleep 1
  swipe 500 1800 500 800 300 # Scroll down to Time-of-day Rules
  sleep 1
  adb -e exec-out screencap -p > "scripts/dev/out/parity/07a_time_rules_section_${THEME}.png"
  tap 912 583 # Add rule
  sleep 1
  adb -e exec-out screencap -p > "scripts/dev/out/parity/07b_edit_time_rule_dialog_${THEME}.png"
  key 4
  sleep 1
  key 4
  sleep 1

  # 8. Widget
  echo ">> 8. 4-column widget ($THEME)"
  adb -e shell input keyevent KEYCODE_HOME
  sleep 2
  adb -e exec-out screencap -p > "scripts/dev/out/parity/08_widget_4col_${THEME}.png"
}

capture_suite "light"
capture_suite "dark"

echo "=== All parity screenshots captured successfully ==="
ls -lh scripts/dev/out/parity/
