#!/usr/bin/env bash

set -euo pipefail

usage() {
    echo "usage: render-homebrew-cask.sh <version> <arm64-sha256> <x86_64-sha256> [release-download-base-url]" >&2
    exit 2
}

version=${1:-}
arm64_sha256=${2:-}
x86_64_sha256=${3:-}
release_download_base_url=${4:-https://github.com/Divyam-AI/divyam-cli/releases/download}

[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || usage
[[ "$arm64_sha256" =~ ^[[:xdigit:]]{64}$ ]] || usage
[[ "$x86_64_sha256" =~ ^[[:xdigit:]]{64}$ ]] || usage
[[ "$release_download_base_url" =~ ^https?://[A-Za-z0-9._:/-]+$ ]] || usage

cat <<EOF
cask "divyam-cli" do
  version "$version"
  arch arm: "arm64", intel: "x86_64"

  sha256 arm:   "$arm64_sha256",
         intel: "$x86_64_sha256"

EOF

if [[ "$release_download_base_url" == "https://github.com/Divyam-AI/divyam-cli/releases/download" ]]; then
    cat <<EOF
  url "$release_download_base_url/v#{version}/divyam-cli-#{version}-darwin-#{arch}.tar.gz",
      verified: "github.com/Divyam-AI/divyam-cli/"
EOF
else
    cat <<EOF
  url "$release_download_base_url/v#{version}/divyam-cli-#{version}-darwin-#{arch}.tar.gz"
EOF
fi

cat <<'EOF'
  name "Divyam CLI"
  desc "Command-line interface for Divyam"
  homepage "https://github.com/Divyam-AI/divyam-cli"

  livecheck do
    url :url
    strategy :github_latest
  end

  binary "divyam-cli-#{version}/bin/divyam"
end
EOF
