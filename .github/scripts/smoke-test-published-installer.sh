#!/usr/bin/env bash

set -euo pipefail

version=${1:?usage: smoke-test-published-installer.sh <version>}
repository=${GITHUB_REPOSITORY:-Divyam-AI/divyam-cli}
tmp_dir=$(mktemp -d)

cleanup() {
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

installer_path="$tmp_dir/install.sh"
curl --fail --location --silent --show-error \
    "https://github.com/$repository/releases/download/v$version/install.sh" \
    -o "$installer_path"

home_dir="$tmp_dir/home"
env HOME="$home_dir" SHELL=/bin/zsh bash "$installer_path" --version "$version"

launcher="$home_dir/.local/bin/divyam"
test -L "$launcher"
"$launcher" --help >/dev/null
"$launcher" version | grep -F "Version: $version" >/dev/null

env HOME="$home_dir" bash "$installer_path" --uninstall
test ! -e "$launcher"
