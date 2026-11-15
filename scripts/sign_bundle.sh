#!/usr/bin/env bash
set -euo pipefail

# DigitsCore Release AAB Signer
# 사용법: ./scripts/sign_bundle.sh [키스토어_경로] [키_별칭] [출력_AAB_경로]

KEYSTORE_PATH="${1:-$HOME/.keystore/digitscore-upload.jks}"
ALIAS="${2:-digitscore-upload}"

if [[ ! -f "$KEYSTORE_PATH" ]]; then
  echo "오류: 키스토어 파일을 찾을 수 없습니다: $KEYSTORE_PATH" >&2
  exit 1
fi

JAVA_BIN="/opt/homebrew/opt/openjdk@17/bin"
if [[ -d "$JAVA_BIN" ]]; then
  export PATH="$JAVA_BIN:$PATH"
fi

# 비밀번호는 스크립트에 두지 않습니다: 환경변수 → local.properties(digitscore.upload.storePassword) 순서로 읽습니다.
STOREPASS="${DIGITSCORE_UPLOAD_STORE_PASSWORD:-}"
if [[ -z "$STOREPASS" && -f local.properties ]]; then
  STOREPASS="$(grep '^digitscore.upload.storePassword=' local.properties | head -n 1 | cut -d'=' -f2-)"
fi
if [[ -z "$STOREPASS" ]]; then
  echo "오류: DIGITSCORE_UPLOAD_STORE_PASSWORD 환경변수나 local.properties의 digitscore.upload.storePassword가 필요합니다." >&2
  exit 1
fi

echo "안내: 릴리스 번들을 새로 빌드합니다..."
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew :app:bundleRelease --no-daemon

# version.properties에서 최신 버전 다시 읽기 (Auto-Versioning 반영)
MAJOR=$(grep "^MAJOR=" version.properties | cut -d'=' -f2)
MINOR=$(grep "^MINOR=" version.properties | cut -d'=' -f2)
PATCH=$(grep "^PATCH=" version.properties | cut -d'=' -f2)
VERSION_NAME="${MAJOR}.${MINOR}.${PATCH}"

SOURCE_AAB="app/build/outputs/bundle/release/app-release.aab"
OUTPUT_AAB="${3:-DigitsCore-v${VERSION_NAME}-release.aab}"

echo ">> AAB 복사: $OUTPUT_AAB"
cp "$SOURCE_AAB" "$OUTPUT_AAB"

echo ">> AAB 서명 진행 (jarsigner)..."
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 \
  -keystore "$KEYSTORE_PATH" \
  -storepass "$STOREPASS" \
  "$OUTPUT_AAB" \
  "$ALIAS"

echo ">> 서명 검증 중..."
jarsigner -verify -verbose -certs "$OUTPUT_AAB" | grep "jar verified" || true

echo ""
echo "=========================================================="
echo " 성공: Google Play 업로드용 서명 AAB가 준비되었습니다!"
echo " 파일 경로: $(pwd)/$OUTPUT_AAB"
echo "=========================================================="
