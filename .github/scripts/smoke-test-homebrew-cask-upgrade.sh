#!/usr/bin/env bash

set -euo pipefail

previous_cask=${1:?usage: smoke-test-homebrew-cask-upgrade.sh <previous-cask.rb> <new-cask.rb> <new-version>}
new_cask=${2:?usage: smoke-test-homebrew-cask-upgrade.sh <previous-cask.rb> <new-cask.rb> <new-version>}
new_version=${3:?usage: smoke-test-homebrew-cask-upgrade.sh <previous-cask.rb> <new-cask.rb> <new-version>}

cleanup() {
    HOMEBREW_NO_AUTO_UPDATE=1 brew uninstall --cask divyam-cli >/dev/null 2>&1 || true
}
trap cleanup EXIT

HOMEBREW_NO_AUTO_UPDATE=1 brew install --cask "$previous_cask"
HOMEBREW_NO_AUTO_UPDATE=1 brew upgrade --cask "$new_cask"
divyam version | grep -F "Version: $new_version" >/dev/null
