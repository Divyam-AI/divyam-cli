#!/usr/bin/env bash

set -euo pipefail

renderer_path=${1:?usage: smoke-test-homebrew-cask.sh <renderer> <archive.tar.gz> <version>}
archive_path=${2:?usage: smoke-test-homebrew-cask.sh <renderer> <archive.tar.gz> <version>}
version=${3:?usage: smoke-test-homebrew-cask.sh <renderer> <archive.tar.gz> <version>}

brew_bin=$(command -v brew || true)
test -n "$brew_bin"
test -x "$renderer_path"
test -f "$archive_path"

tap_name="Divyam-AI/divyam-cli-cask-smoke-test"
cask_name="divyam-cli"
initial_version="0.0.0"
brew_prefix=$("$brew_bin" --prefix)
launcher="$brew_prefix/bin/divyam"

if [[ "$version" == "$initial_version" ]]; then
    echo "Smoke-test version must differ from $initial_version" >&2
    exit 1
fi

if "$brew_bin" list --cask "$cask_name" >/dev/null 2>&1; then
    echo "Refusing to replace an installed $cask_name Cask" >&2
    exit 1
fi
if [[ -e "$launcher" || -L "$launcher" ]]; then
    echo "Refusing to replace existing launcher: $launcher" >&2
    exit 1
fi

tmp_dir=$(mktemp -d)
port=$(python3 - <<'PY'
import socket

with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
)
tapped=false

cleanup() {
    HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" uninstall --cask --force "$tap_name/$cask_name" >/dev/null 2>&1 || true
    if [[ "$tapped" == "true" ]]; then
        HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" untap "$tap_name" >/dev/null 2>&1 || true
    fi
    if [[ -n "${server_pid:-}" ]]; then
        kill "$server_pid" >/dev/null 2>&1 || true
        wait "$server_pid" 2>/dev/null || true
    fi
    rm -rf "$tmp_dir"
}
trap cleanup EXIT

web_root="$tmp_dir/web"
tap_root="$tmp_dir/homebrew-divyam-cli-cask-smoke-test"
initial_payload_root="$tmp_dir/initial-payload"
mkdir -p "$tap_root/Casks" "$initial_payload_root"

tar -xzf "$archive_path" -C "$initial_payload_root"
mv "$initial_payload_root/divyam-cli-$version" "$initial_payload_root/divyam-cli-$initial_version"

for release_version in "$initial_version" "$version"; do
    release_root="$web_root/releases/download/v$release_version"
    mkdir -p "$release_root"
    for arch in arm64 x86_64; do
        archive_name="divyam-cli-$release_version-darwin-$arch.tar.gz"
        if [[ "$release_version" == "$initial_version" ]]; then
            tar -C "$initial_payload_root" -czf "$release_root/$archive_name" "divyam-cli-$initial_version"
        else
            cp "$archive_path" "$release_root/$archive_name"
        fi
    done
done

initial_arm64_sha256=$(shasum -a 256 "$web_root/releases/download/v$initial_version/divyam-cli-$initial_version-darwin-arm64.tar.gz" | awk '{print $1}')
initial_x86_64_sha256=$(shasum -a 256 "$web_root/releases/download/v$initial_version/divyam-cli-$initial_version-darwin-x86_64.tar.gz" | awk '{print $1}')
"$renderer_path" "$initial_version" "$initial_arm64_sha256" "$initial_x86_64_sha256" "http://127.0.0.1:$port/releases/download" \
    > "$tap_root/Casks/$cask_name.rb"
ruby -c "$tap_root/Casks/$cask_name.rb" >/dev/null

git -C "$tap_root" init --quiet
git -C "$tap_root" config user.name "Divyam CLI Cask smoke test"
git -C "$tap_root" config user.email "cask-smoke-test@example.invalid"
git -C "$tap_root" add Casks/$cask_name.rb
git -C "$tap_root" commit --quiet -m "Add test Cask"

python3 -m http.server "$port" --bind 127.0.0.1 --directory "$web_root" \
    >"$tmp_dir/http-server.log" 2>&1 &
server_pid=$!

for _ in $(seq 1 20); do
    if curl --fail --silent "http://127.0.0.1:$port/releases/download/v$initial_version/divyam-cli-$initial_version-darwin-arm64.tar.gz" >/dev/null; then
        break
    fi
    sleep 1
done

if ! curl --fail --silent "http://127.0.0.1:$port/releases/download/v$initial_version/divyam-cli-$initial_version-darwin-arm64.tar.gz" >/dev/null; then
    echo "Local Cask archive server did not start" >&2
    exit 1
fi

HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" tap "$tap_name" "$tap_root" >/dev/null
tapped=true
HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" install --cask --yes "$tap_name/$cask_name"

test -L "$launcher"
"$launcher" --help >/dev/null

arm64_sha256=$(shasum -a 256 "$web_root/releases/download/v$version/divyam-cli-$version-darwin-arm64.tar.gz" | awk '{print $1}')
x86_64_sha256=$(shasum -a 256 "$web_root/releases/download/v$version/divyam-cli-$version-darwin-x86_64.tar.gz" | awk '{print $1}')
installed_tap_root=$("$brew_bin" --repository "$tap_name")
"$renderer_path" "$version" "$arm64_sha256" "$x86_64_sha256" "http://127.0.0.1:$port/releases/download" \
    > "$installed_tap_root/Casks/$cask_name.rb"
git -C "$installed_tap_root" add "Casks/$cask_name.rb"
git -C "$installed_tap_root" commit --quiet -m "Update test Cask"

HOMEBREW_NO_AUTO_UPDATE=1 "$brew_bin" upgrade --cask --yes "$tap_name/$cask_name"

test -d "$brew_prefix/Caskroom/$cask_name/$version"
test -L "$launcher"
"$launcher" version | grep -F "Version: $version" >/dev/null
