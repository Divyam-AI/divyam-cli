#!/usr/bin/env bash

set -euo pipefail

renderer_path=${1:?usage: verify-homebrew-cask.sh <renderer> <version> <arm64-archive> <x86_64-archive> <output-cask>}
version=${2:?usage: verify-homebrew-cask.sh <renderer> <version> <arm64-archive> <x86_64-archive> <output-cask>}
arm64_archive=${3:?usage: verify-homebrew-cask.sh <renderer> <version> <arm64-archive> <x86_64-archive> <output-cask>}
x86_64_archive=${4:?usage: verify-homebrew-cask.sh <renderer> <version> <arm64-archive> <x86_64-archive> <output-cask>}
output_cask=${5:?usage: verify-homebrew-cask.sh <renderer> <version> <arm64-archive> <x86_64-archive> <output-cask>}

test -x "$renderer_path"
test -f "$arm64_archive"
test -f "$x86_64_archive"

mkdir -p "$(dirname "$output_cask")"

arm64_sha256=$(shasum -a 256 "$arm64_archive" | awk '{print $1}')
x86_64_sha256=$(shasum -a 256 "$x86_64_archive" | awk '{print $1}')

"$renderer_path" "$version" "$arm64_sha256" "$x86_64_sha256" > "$output_cask"
ruby -c "$output_cask" >/dev/null

grep -Fqx "  version \"$version\"" "$output_cask"
grep -Fq "  sha256 arm:   \"$arm64_sha256\"," "$output_cask"
grep -Fq "         intel: \"$x86_64_sha256\"" "$output_cask"
grep -Fq 'binary "divyam-cli-#{version}/bin/divyam"' "$output_cask"
if grep -Fq 'sha256 :no_check' "$output_cask"; then
    echo "Homebrew Cask must pin immutable archive checksums" >&2
    exit 1
fi
