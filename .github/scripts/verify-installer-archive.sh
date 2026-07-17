#!/usr/bin/env bash

set -euo pipefail

archive_path=${1:?usage: verify-installer-archive.sh <archive.tar.gz> <version>}
version=${2:?usage: verify-installer-archive.sh <archive.tar.gz> <version>}

test -f "$archive_path"

archive_root="divyam-cli-$version"
expected_entries=$(printf '%s\n' \
    "$archive_root/" \
    "$archive_root/LICENSE" \
    "$archive_root/bin/" \
    "$archive_root/bin/divyam")
actual_entries=$(tar -tzf "$archive_path" | sed 's#^\./##' | LC_ALL=C sort)

if [[ "$actual_entries" != "$expected_entries" ]]; then
    echo "Installer archive must contain only LICENSE and bin/divyam" >&2
    printf '%s\n' "$actual_entries" >&2
    exit 1
fi

tmp_dir=$(mktemp -d)
trap 'rm -rf "$tmp_dir"' EXIT

tar -xzf "$archive_path" -C "$tmp_dir"
binary_path="$tmp_dir/$archive_root/bin/divyam"
test -x "$binary_path"
"$binary_path" --help >/dev/null
"$binary_path" version | grep -F "Version: $version" >/dev/null
