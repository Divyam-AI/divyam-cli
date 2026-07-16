#!/usr/bin/env bash

set -euo pipefail

version=${1:?usage: render-homebrew-cask.sh <version> <arm64-sha256> <x86_64-sha256> <output-path>}
arm64_sha=${2:?usage: render-homebrew-cask.sh <version> <arm64-sha256> <x86_64-sha256> <output-path>}
x86_64_sha=${3:?usage: render-homebrew-cask.sh <version> <arm64-sha256> <x86_64-sha256> <output-path>}
output_path=${4:?usage: render-homebrew-cask.sh <version> <arm64-sha256> <x86_64-sha256> <output-path>}

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Version must be X.Y.Z" >&2
    exit 1
fi

for checksum in "$arm64_sha" "$x86_64_sha"; do
    if [[ ! "$checksum" =~ ^[0-9a-f]{64}$ ]]; then
        echo "Checksums must be lowercase SHA-256 values" >&2
        exit 1
    fi
done

mkdir -p "$(dirname "$output_path")"
printf '%s\n' \
    'cask "divyam-cli" do' \
    '  arch arm: "arm64", intel: "x86_64"' \
    '' \
    "  version \"$version\"" \
    "  sha256 arm:   \"$arm64_sha\"," \
    "         intel: \"$x86_64_sha\"" \
    '' \
    '  url "https://github.com/Divyam-AI/divyam-cli/releases/download/v#{version}/divyam-cli-#{version}-macos-#{arch}.zip",' \
    '      verified: "github.com/Divyam-AI/divyam-cli/"' \
    '  name "Divyam CLI"' \
    '  desc "Command-line tool to manage and configure Divyam"' \
    '  homepage "https://github.com/Divyam-AI/divyam-cli"' \
    '' \
    '  livecheck do' \
    '    url :url' \
    '    strategy :github_latest' \
    '  end' \
    '' \
    '  binary "divyam"' \
    'end' \
    > "$output_path"
