#!/usr/bin/env bash
set -euo pipefail

# DigitsCore Release AAB Signer
# 사용법: ./scripts/sign_bundle.sh /오프라인/경로/digitscore-upload.jks [키_별칭]

if [[ $# -lt 1 ]]; then
  echo "사용법: $0 /절대/경로/digitscore-upload.jks [alias]" >&2
  exit 1
fi

KEYSTORE_PATH="$1"
ALIAS="${2:-digitscore-upload}"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "오류: 키스토어 파일을 찾을 수 없습니다: $KEYSTORE_PATH" >&2
  exit 1
fi

export PATH="/opt/homebrew/opt/openjdk@17/bin:$PATH"

SOURCE_AAB="app/build/outputs/bundle/release/app-release.aab"
OUTPUT_AAB="${3:-DigitsCore-v3.3.7-release.aab}"

if [[ ! -f "$SOURCE_AAB" ]]; then
  echo "안내: 릴리스 번들이 없습니다. 새로 빌드합니다..."
  JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk gradle :app:bundleRelease
fi

echo ">> AAB 복사: $OUTPUT_AAB"
cp "$SOURCE_AAB" "$OUTPUT_AAB"

echo ">> AAB 서명 진행 (jarsigner)..."
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore "$KEYSTORE_PATH" \
  "$OUTPUT_AAB" \
  "$ALIAS"

echo ">> 서명 검증 중..."
jarsigner -verify -verbose -certs "$OUTPUT_AAB" | grep "jar verified" || true

echo ""
echo "=========================================================="
echo " 성공: Google Play 업로드용 서명 AAB가 준비되었습니다!"
echo " 파일 경로: $(pwd)/$OUTPUT_AAB"
echo "=========================================================="
