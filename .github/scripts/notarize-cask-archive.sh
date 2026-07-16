#!/usr/bin/env bash

set -euo pipefail

archive_path=${1:?usage: notarize-cask-archive.sh <archive.zip> <path-to-divyam>}
binary_path=${2:?usage: notarize-cask-archive.sh <archive.zip> <path-to-divyam>}

: "${APPLE_NOTARY_API_KEY:?missing APPLE_NOTARY_API_KEY}"
: "${APPLE_NOTARY_KEY_ID:?missing APPLE_NOTARY_KEY_ID}"
: "${APPLE_NOTARY_ISSUER_ID:?missing APPLE_NOTARY_ISSUER_ID}"

decode_base64() {
    if base64 --help 2>&1 | grep -q -- '--decode'; then
        base64 --decode
    else
        base64 -D
    fi
}

work_dir=$(mktemp -d)
api_key_path="$work_dir/AuthKey_${APPLE_NOTARY_KEY_ID}.p8"
trap 'rm -rf "$work_dir"' EXIT

printf '%s' "$APPLE_NOTARY_API_KEY" | decode_base64 > "$api_key_path"

xcrun notarytool submit "$archive_path" \
    --key "$api_key_path" \
    --key-id "$APPLE_NOTARY_KEY_ID" \
    --issuer "$APPLE_NOTARY_ISSUER_ID" \
    --wait
spctl --assess --type execute --verbose=4 "$binary_path"
