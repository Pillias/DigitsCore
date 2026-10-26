#!/bin/bash
# DigitsCore v0.9.35 릴리스: 테스트 → 서명된 AAB/APK 빌드 → 커밋·푸시 → GitHub 릴리스
set -euo pipefail
cd "$(dirname "$0")/../../.."
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="$JAVA_HOME/bin:$PATH"
STEP="${1:-build}"

if [ "$STEP" = "build" ]; then
  echo "== 1. 작업 트리 확인"; git status --short
  test "$(git branch --show-current)" = "feature/screen-time-parity"
  if find . -path ./.git -prune -o -path ./scripts/dev/out -prune -o -path '*/build' -prune -o -name '*-1.*' -print | grep -q .; then echo "중복 -1 파일 있음"; exit 1; fi
  echo "== 2. 변경 커밋"
  git add app/src DEBATE.md docs scripts/dev/tasks
  git commit -m "fix(parity): 검토 반영 - 짧은 확인 확정 시점, 자기 패키지 제외, 관리 앱 전체 알림 이름, 시간대 빠른 추가 버튼, 불러온 날 판정 통일; DEBATE 15~17 합의" || true
  echo "== 3. 단위 테스트"; ./gradlew :app:testDebugUnitTest --no-daemon -q
  echo "== 4. 디자인 점검"; STRICT=1 bash scripts/design_check.sh
  echo "== 5. 릴리스 빌드 (PATCH 자동 +1)"; ./gradlew :app:bundleRelease :app:assembleRelease --no-daemon -q
  V="$(grep '^MAJOR=' version.properties | cut -d= -f2).$(grep '^MINOR=' version.properties | cut -d= -f2).$(grep '^PATCH=' version.properties | cut -d= -f2)"
  echo "버전: $V (code $(grep '^VERSION_CODE=' version.properties | cut -d= -f2))"
  APK=$(ls app/build/outputs/apk/release/*.apk | head -1)
  cp app/build/outputs/bundle/release/app-release.aab "DigitsCore-v${V}-release.aab"
  cp "$APK" "DigitsCore-v${V}-release.apk"
  echo "== 6. 서명 확인 (업로드 키 SHA1 A6:CD:47:42:...:6C:8A 여야 함)"
  keytool -printcert -jarfile "DigitsCore-v${V}-release.aab" | grep -E "SHA1|Owner"
  BT=$(ls -d "$(grep '^sdk.dir=' local.properties | cut -d= -f2)"/build-tools/* | sort -V | tail -1)
  "$BT/apksigner" verify --print-certs "DigitsCore-v${V}-release.apk" | grep -E "SHA-1|Signer #1 certificate DN"
  ls -la "DigitsCore-v${V}-release.aab" "DigitsCore-v${V}-release.apk"
  echo "== 7. 버전 커밋"
  git add version.properties
  git commit -m "release: v${V} (versionCode $(grep '^VERSION_CODE=' version.properties | cut -d= -f2)) - screen time parity, weekly report, time rules"
  git branch -f "release/v${V}"
  echo "BUILD_DONE v${V}"
fi

if [ "$STEP" = "publish" ]; then
  V="$(grep '^MAJOR=' version.properties | cut -d= -f2).$(grep '^MINOR=' version.properties | cut -d= -f2).$(grep '^PATCH=' version.properties | cut -d= -f2)"
  git push origin feature/screen-time-parity "release/v${V}"
  gh release create "v${V}" "DigitsCore-v${V}-release.apk" "DigitsCore-v${V}-release.aab" \
    --target "release/v${V}" --title "DigitsCore v${V}" --notes-file "docs/release-notes/v${V}.md"
  gh release view "v${V}" --json url,assets --jq '.url, (.assets[].name)'
  echo "PUBLISH_DONE"
fi
