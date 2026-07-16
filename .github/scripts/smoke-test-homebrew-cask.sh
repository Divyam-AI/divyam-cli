#!/usr/bin/env bash

set -euo pipefail

archive_path=${1:?usage: smoke-test-homebrew-cask.sh <archive.zip> <version>}
version=${2:?usage: smoke-test-homebrew-cask.sh <archive.zip> <version>}

archive_dir=$(cd "$(dirname "$archive_path")" && pwd)
archive_name=$(basename "$archive_path")
archive_sha=$(shasum -a 256 "$archive_path" | awk '{print $1}')
tmp_dir=$(mktemp -d)
port=$(python3 - <<'PY'
import socket

with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
)

cleanup() {
    HOMEBREW_NO_AUTO_UPDATE=1 brew uninstall --cask divyam-cli >/dev/null 2>&1 || true
    if [[ -n "${server_pid:-}" ]]; then
        kill "$server_pid" >/dev/null 2>&1 || true
    fi
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

python3 -m http.server "$port" --bind 127.0.0.1 --directory "$archive_dir" \
    >"$tmp_dir/http-server.log" 2>&1 &
server_pid=$!

for _ in $(seq 1 20); do
    if curl --fail --silent "http://127.0.0.1:$port/$archive_name" >/dev/null; then
        break
    fi
    sleep 1
done

printf '%s\n' \
  'cask "divyam-cli" do' \
  "  version \"$version\"" \
  "  sha256 \"$archive_sha\"" \
  '' \
  "  url \"http://127.0.0.1:$port/$archive_name\"" \
  '  name "Divyam CLI"' \
  '  desc "Command-line tool to manage and configure Divyam"' \
  '  homepage "https://github.com/Divyam-AI/divyam-cli"' \
  '' \
  '  binary "divyam"' \
  'end' > "$tmp_dir/divyam-cli.rb"

HOMEBREW_NO_AUTO_UPDATE=1 brew install --cask "$tmp_dir/divyam-cli.rb"
divyam --help >/dev/null
divyam version | grep -F "Version: $version" >/dev/null
