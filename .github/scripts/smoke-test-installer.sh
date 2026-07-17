#!/usr/bin/env bash

set -euo pipefail

installer_path=${1:?usage: smoke-test-installer.sh <install.sh> <archive.tar.gz> <version>}
archive_path=${2:?usage: smoke-test-installer.sh <install.sh> <archive.tar.gz> <version>}
version=${3:?usage: smoke-test-installer.sh <install.sh> <archive.tar.gz> <version>}

test -f "$installer_path"
test -f "$archive_path"

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
    if [[ -n "${server_pid:-}" ]]; then
        kill "$server_pid" >/dev/null 2>&1 || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

web_root="$tmp_dir/web"
release_root="$web_root/releases/download/v$version"
mkdir -p "$release_root" "$web_root/releases/latest/download"
cp "$archive_path" "$release_root/$archive_name"
printf '%s  %s\n' "$archive_sha" "$archive_name" > "$release_root/SHA256SUMS"
printf '%s\n' "$version" > "$web_root/releases/latest/download/latest-version"

python3 -m http.server "$port" --bind 127.0.0.1 --directory "$web_root" \
    >"$tmp_dir/http-server.log" 2>&1 &
server_pid=$!

for _ in $(seq 1 20); do
    if curl --fail --silent "http://127.0.0.1:$port/releases/latest/download/latest-version" >/dev/null; then
        break
    fi
    sleep 1
done

home_dir="$tmp_dir/home"
env \
    HOME="$home_dir" \
    SHELL=/bin/zsh \
    DIVYAM_RELEASE_BASE_URL="http://127.0.0.1:$port/releases" \
    bash "$installer_path"

launcher="$home_dir/.local/bin/divyam"
test -L "$launcher"
"$launcher" --help >/dev/null
"$launcher" version | grep -F "Version: $version" >/dev/null
grep -Fqx '# Added by Divyam CLI installer' "$home_dir/.zshrc"

env \
    HOME="$home_dir" \
    SHELL=/bin/zsh \
    DIVYAM_RELEASE_BASE_URL="http://127.0.0.1:$port/releases" \
    bash "$installer_path" --version "$version"

env HOME="$home_dir" bash "$installer_path" --uninstall
test ! -e "$launcher"
