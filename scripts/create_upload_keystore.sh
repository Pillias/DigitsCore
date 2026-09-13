#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /absolute/offline/path/digitscore-upload.jks" >&2
  exit 2
fi

digitscore_keystore_path="$1"
if [[ "$digitscore_keystore_path" != /* ]]; then
  echo "Use an absolute path outside the repository." >&2
  exit 2
fi
if [[ -e "$digitscore_keystore_path" ]]; then
  echo "Refusing to overwrite existing file: $digitscore_keystore_path" >&2
  exit 1
fi

read -r -p "Certificate owner (for example, CN=Your Name,O=Your Studio,C=KR): " digitscore_dname
read -r -s -p "New keystore password (12+ characters): " DIGITSCORE_NEW_STORE_PASSWORD
echo
read -r -s -p "Repeat keystore password: " digitscore_store_confirmation
echo

if [[ ${#DIGITSCORE_NEW_STORE_PASSWORD} -lt 12 ]] || [[ "$DIGITSCORE_NEW_STORE_PASSWORD" != "$digitscore_store_confirmation" ]]; then
  echo "Passwords must match and contain at least 12 characters." >&2
  exit 1
fi
if [[ -z "$digitscore_dname" ]]; then
  echo "Certificate owner must not be empty." >&2
  exit 1
fi

export DIGITSCORE_NEW_STORE_PASSWORD
keytool -genkeypair \
  -keystore "$digitscore_keystore_path" \
  -storetype PKCS12 \
  -storepass:env DIGITSCORE_NEW_STORE_PASSWORD \
  -keypass:env DIGITSCORE_NEW_STORE_PASSWORD \
  -alias digitscore-upload \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "$digitscore_dname"

unset DIGITSCORE_NEW_STORE_PASSWORD digitscore_store_confirmation
echo "Created: $digitscore_keystore_path"
echo "Back it up offline before uploading the first Play release. Never commit it."
