# 에뮬레이터 조작 도우미 (adb)
shot() { adb -e exec-out screencap -p > "scripts/dev/out/${1:-screen}.png"; }
ui()   { adb -e shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb -e shell cat /sdcard/ui.xml > "scripts/dev/out/${1:-ui}.xml"; }
tap()  { adb -e shell input tap "$1" "$2"; }
swipe(){ adb -e shell input swipe "$1" "$2" "$3" "$4" "${5:-300}"; }
key()  { adb -e shell input keyevent "$1"; }
txt()  { adb -e shell input text "$1"; }
app()  { adb -e shell monkey -p "${1:-$APP_ID}" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; }
# 화면에서 text 또는 content-desc가 정확히 일치(부분 일치는 tapc)하는 요소의 중앙을 누릅니다.
_center() { ui _find; perl -ne 'while (/(?:text|content-desc)="([^"]+)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/g) { if ($ENV{MODE} eq "c" ? index($1,$ENV{Q})>=0 : $1 eq $ENV{Q}) { printf "%d %d\n", ($2+$4)/2, ($3+$5)/2; exit } }' scripts/dev/out/_find.xml; }
tapt() { local c; c="$(Q="$1" MODE=e _center)"; [ -n "$c" ] && { tap $c; echo "tap '$1' @ $c"; } || echo "not found: $1"; }
tapc() { local c; c="$(Q="$1" MODE=c _center)"; [ -n "$c" ] && { tap $c; echo "tap ~'$1' @ $c"; } || echo "not found: ~$1"; }
texts() { ui _find; perl -ne 'while (/(?:text|content-desc)="([^"]+)"[^>]*?bounds="([^"]+)"/g) { print "$1  $2\n" }' scripts/dev/out/_find.xml; }
