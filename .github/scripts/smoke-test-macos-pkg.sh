#!/usr/bin/env bash

set -euo pipefail

pkg_path=${1:?usage: smoke-test-macos-pkg.sh <package.pkg> <version>}
version=${2:?usage: smoke-test-macos-pkg.sh <package.pkg> <version>}

test -f "$pkg_path"

package_identifier="ai.divyam.divyam-cli"
managed_paths=(
    /usr/local/bin/divyam
    /usr/local/etc/bash_completion.d/divyam
    /usr/local/share/zsh/site-functions/divyam
)

for managed_path in "${managed_paths[@]}"; do
    if [[ -e "$managed_path" || -L "$managed_path" ]]; then
        echo "Refusing to replace an existing file: $managed_path" >&2
        exit 1
    fi
done

if pkgutil --pkg-info "$package_identifier" >/dev/null 2>&1; then
    echo "Refusing to replace existing package receipt: $package_identifier" >&2
    exit 1
fi

cleanup() {
    sudo rm -f "${managed_paths[@]}"
    sudo pkgutil --forget "$package_identifier" >/dev/null 2>&1 || true
}
trap cleanup EXIT

sudo installer -pkg "$pkg_path" -target /
pkgutil --pkg-info "$package_identifier" >/dev/null

/usr/local/bin/divyam --help >/dev/null
/usr/local/bin/divyam version | grep -F "Version: $version" >/dev/null

for managed_path in "${managed_paths[@]}"; do
    test -f "$managed_path"
done
