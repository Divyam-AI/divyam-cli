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
home_dir="$tmp_dir/home"
install_dir="$tmp_dir/bin"
data_dir="$tmp_dir/data"
installer_tmp_dir="$tmp_dir/installer-tmp"
mkdir -p "$release_root" "$web_root/releases/latest/download"
mkdir -p "$installer_tmp_dir"
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

run_installer() {
    env \
        HOME="$home_dir" \
        SHELL=/bin/zsh \
        TMPDIR="$installer_tmp_dir" \
        DIVYAM_INSTALL_DIR="$install_dir" \
        DIVYAM_DATA_DIR="$data_dir" \
        DIVYAM_RELEASE_BASE_URL="http://127.0.0.1:$port/releases" \
        bash "$installer_path" "$@"
}

assert_failure() {
    local expected_message=$1
    shift

    if "$@" >"$tmp_dir/installer-error.log" 2>&1; then
        echo "installer unexpectedly succeeded: $*" >&2
        exit 1
    fi

    grep -F -- "$expected_message" "$tmp_dir/installer-error.log" >/dev/null
}

for help_option in --help -h; do
    help_output=$(bash "$installer_path" "$help_option")
    printf '%s\n' "$help_output" | grep -F 'Usage: install.sh' >/dev/null
done

assert_failure 'unknown option: --unknown' run_installer --unknown
assert_failure '--version requires X.Y.Z' run_installer --version
assert_failure 'release version must be X.Y.Z; received: 1.2' run_installer --version 1.2
assert_failure '--version cannot be used with --uninstall' run_installer --uninstall --version "$version"
assert_failure '--uninstall cannot be used with --version' run_installer --version "$version" --uninstall
assert_failure 'interactive confirmation requires a terminal; rerun with --yes' run_installer

run_installer --yes

launcher="$install_dir/divyam"
test -L "$launcher"
test "$(readlink "$launcher")" = "$data_dir/releases/$version/bin/divyam"
test -x "$data_dir/releases/$version/bin/divyam"
"$launcher" --help >/dev/null
"$launcher" version | grep -F "Version: $version" >/dev/null
grep -Fqx '# Added by Divyam CLI installer' "$home_dir/.zshrc"
test -z "$(find "$installer_tmp_dir" -mindepth 1 -maxdepth 1 -print -quit)"

run_installer --version "$version" -y

assert_failure 'interactive confirmation requires a terminal; rerun with --yes' run_installer --uninstall
run_installer --uninstall --yes
test ! -e "$launcher"
test ! -e "$data_dir/releases"
test -f "$home_dir/.zshrc"
test -z "$(find "$installer_tmp_dir" -mindepth 1 -maxdepth 1 -print -quit)"
