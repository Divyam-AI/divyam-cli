#!/usr/bin/env bash

set -euo pipefail

cask_path=${1:?usage: smoke-test-published-homebrew-cask.sh <cask.rb> <version>}
version=${2:?usage: smoke-test-published-homebrew-cask.sh <cask.rb> <version>}

cleanup() {
    HOMEBREW_NO_AUTO_UPDATE=1 brew uninstall --cask divyam-cli >/dev/null 2>&1 || true
}
trap cleanup EXIT

HOMEBREW_NO_AUTO_UPDATE=1 brew install --cask "$cask_path"
divyam --help >/dev/null
divyam version | grep -F "Version: $version" >/dev/null
