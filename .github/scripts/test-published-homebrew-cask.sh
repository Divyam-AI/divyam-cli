#!/usr/bin/env bash

set -euo pipefail

version=${1:?usage: test-published-homebrew-cask.sh <version>}
cask_reference=${DIVYAM_HOMEBREW_CASK_REFERENCE:-Divyam-AI/tap/divyam-cli}
tap_name=${DIVYAM_HOMEBREW_TAP_NAME:-Divyam-AI/tap}
tap_name_lower=$(printf '%s' "$tap_name" | tr '[:upper:]' '[:lower:]')
cask_name=divyam-cli

brew_bin=$(command -v brew || true)
test -n "$brew_bin"

brew_prefix=$("$brew_bin" --prefix)
launcher="$brew_prefix/bin/divyam"
tap_was_present=false

if "$brew_bin" tap | grep -Fx "$tap_name_lower" >/dev/null; then
    tap_was_present=true
fi
if "$brew_bin" list --cask "$cask_name" >/dev/null 2>&1; then
    echo "Refusing to replace an installed $cask_name Cask" >&2
    exit 1
fi
if [[ -e "$launcher" || -L "$launcher" ]]; then
    echo "Refusing to replace existing launcher: $launcher" >&2
    exit 1
fi

cleanup() {
    HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" uninstall --cask --force "$cask_reference" >/dev/null 2>&1 || true
    if [[ "$tap_was_present" == "false" ]]; then
        HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" untap "$tap_name" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" install --cask --yes "$cask_reference"

test -L "$launcher"
"$launcher" --help >/dev/null
"$launcher" version | grep -F "Version: $version" >/dev/null
