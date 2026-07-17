#!/usr/bin/env bash

set -euo pipefail

installer_path=${1:?usage: test-installer-upgrade.sh <install.sh>}
test -f "$installer_path"

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

case "$(uname -m)" in
    arm64|aarch64) architecture=arm64 ;;
    x86_64|amd64)
        if [[ "$(sysctl -n sysctl.proc_translated 2>/dev/null || true)" == "1" ]]; then
            architecture=arm64
        else
            architecture=x86_64
        fi
        ;;
    *) echo "unsupported architecture" >&2; exit 1 ;;
esac

web_root="$tmp_dir/web"
home_dir="$tmp_dir/home"

make_release() {
    local version=$1
    local release_root="$web_root/releases/download/v$version"
    local archive_name="divyam-cli-$version-darwin-$architecture.tar.gz"
    local staging_root="$tmp_dir/divyam-cli-$version"

    mkdir -p "$release_root" "$staging_root/bin"
    touch "$staging_root/LICENSE"
    printf '%s\n' \
        '#!/usr/bin/env bash' \
        'case "$1" in' \
        '  --help) exit 0 ;;' \
        "  version) echo \"Version: $version\" ;;" \
        '  *) exit 1 ;;' \
        'esac' \
        > "$staging_root/bin/divyam"
    chmod +x "$staging_root/bin/divyam"
    tar -czf "$release_root/$archive_name" -C "$tmp_dir" "divyam-cli-$version"
    shasum -a 256 "$release_root/$archive_name" \
        | sed "s#  $release_root/#  #" \
        > "$release_root/SHA256SUMS"
    rm -rf "$staging_root"
}

make_release 1.2.3
make_release 1.2.4
mkdir -p "$web_root/releases/latest/download"
printf '%s\n' '1.2.3' > "$web_root/releases/latest/download/latest-version"

python3 -m http.server "$port" --bind 127.0.0.1 --directory "$web_root" \
    >"$tmp_dir/http-server.log" 2>&1 &
server_pid=$!

for _ in $(seq 1 20); do
    if curl --fail --silent "http://127.0.0.1:$port/releases/latest/download/latest-version" >/dev/null; then
        break
    fi
    sleep 1
done

run_installer() {
    env \
        HOME="$home_dir" \
        SHELL=/bin/zsh \
        DIVYAM_RELEASE_BASE_URL="http://127.0.0.1:$port/releases" \
        bash "$installer_path" --yes
}

initial_output=$(run_installer)
printf '%s\n' "$initial_output" | grep -F 'Installing Divyam CLI 1.2.3.' >/dev/null
launcher="$home_dir/.local/bin/divyam"
"$launcher" version | grep -F 'Version: 1.2.3' >/dev/null

archive_name="divyam-cli-1.2.4-darwin-$architecture.tar.gz"
printf '%064d  %s\n' 0 "$archive_name" > "$web_root/releases/download/v1.2.4/SHA256SUMS"
printf '%s\n' '1.2.4' > "$web_root/releases/latest/download/latest-version"

if run_installer; then
    echo "installer unexpectedly accepted an invalid checksum" >&2
    exit 1
fi
"$launcher" version | grep -F 'Version: 1.2.3' >/dev/null

shasum -a 256 "$web_root/releases/download/v1.2.4/$archive_name" \
    | sed "s#  $web_root/releases/download/v1.2.4/#  #" \
    > "$web_root/releases/download/v1.2.4/SHA256SUMS"
update_output=$(run_installer)
printf '%s\n' "$update_output" | grep -F 'Updating Divyam CLI from 1.2.3 to 1.2.4.' >/dev/null
"$launcher" version | grep -F 'Version: 1.2.4' >/dev/null

rm "$web_root/releases/download/v1.2.4/$archive_name" "$web_root/releases/download/v1.2.4/SHA256SUMS"
already_installed_output=$(run_installer)
printf '%s\n' "$already_installed_output" | grep -F 'Divyam CLI 1.2.4 is already installed' >/dev/null

env HOME="$home_dir" bash "$installer_path" --uninstall --yes
test ! -e "$launcher"
