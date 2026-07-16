#!/usr/bin/env bash

set -euo pipefail

binary_path=${1:?usage: sign-macos-binary.sh <path-to-divyam>}

: "${APPLE_DEVELOPER_ID_P12:?missing APPLE_DEVELOPER_ID_P12}"
: "${APPLE_DEVELOPER_ID_P12_PASSWORD:?missing APPLE_DEVELOPER_ID_P12_PASSWORD}"
: "${APPLE_DEVELOPER_ID_APPLICATION:?missing APPLE_DEVELOPER_ID_APPLICATION}"

decode_base64() {
    if base64 --help 2>&1 | grep -q -- '--decode'; then
        base64 --decode
    else
        base64 -D
    fi
}

work_dir=$(mktemp -d)
keychain_path="$work_dir/divyam-signing.keychain-db"
certificate_path="$work_dir/developer-id.p12"
keychain_password=$(uuidgen)

cleanup() {
    security delete-keychain "$keychain_path" >/dev/null 2>&1 || true
    rm -rf "$work_dir"
}
trap cleanup EXIT

printf '%s' "$APPLE_DEVELOPER_ID_P12" | decode_base64 > "$certificate_path"

security create-keychain -p "$keychain_password" "$keychain_path"
security set-keychain-settings -lut 21600 "$keychain_path"
security unlock-keychain -p "$keychain_password" "$keychain_path"
security import "$certificate_path" \
    -k "$keychain_path" \
    -P "$APPLE_DEVELOPER_ID_P12_PASSWORD" \
    -T /usr/bin/codesign \
    -T /usr/bin/security
security set-key-partition-list \
    -S apple-tool:,apple:,codesign: \
    -s \
    -k "$keychain_password" \
    "$keychain_path"

codesign \
    --force \
    --options runtime \
    --timestamp \
    --sign "$APPLE_DEVELOPER_ID_APPLICATION" \
    "$binary_path"
codesign --verify --deep --strict --verbose=2 "$binary_path"
