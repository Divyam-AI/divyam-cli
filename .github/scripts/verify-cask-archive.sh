#!/usr/bin/env bash

set -euo pipefail

archive_path=${1:?usage: verify-cask-archive.sh <archive.zip> <version>}
version=${2:?usage: verify-cask-archive.sh <archive.zip> <version>}

test -f "$archive_path"

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

actual_entries=$(unzip -Z1 "$archive_path" | sed '/\/$/d' | LC_ALL=C sort)
expected_entries=$(printf 'LICENSE\ndivyam')

if [[ "$actual_entries" != "$expected_entries" ]]; then
    echo "Cask archive must contain only LICENSE and divyam" >&2
    printf '%s\n' "$actual_entries" >&2
    exit 1
fi

unzip -q "$archive_path" -d "$tmp_dir"
test -x "$tmp_dir/divyam"
"$tmp_dir/divyam" --help >/dev/null
"$tmp_dir/divyam" version | grep -F "Version: $version" >/dev/null
