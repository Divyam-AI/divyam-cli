#!/usr/bin/env bash

set -euo pipefail

usage() {
    cat <<'EOF'
Usage:
  test-macos-artifacts.sh verify-archive <archive.tar.gz> <version>
  test-macos-artifacts.sh direct-installer <install.sh> <archive.tar.gz> <version>
  test-macos-artifacts.sh installer-upgrade <install.sh>
  test-macos-artifacts.sh pkg <package.pkg> <version>
EOF
}

verify_archive() {
    local archive_path=${1:?usage: test-macos-artifacts.sh verify-archive <archive.tar.gz> <version>}
    local version=${2:?usage: test-macos-artifacts.sh verify-archive <archive.tar.gz> <version>}

    test -f "$archive_path"

    local archive_root="divyam-cli-$version"
    local repository_root
    repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
    local expected_entries
    expected_entries=$(
        {
            printf '%s\n' \
                "$archive_root/" \
                "$archive_root/LICENSE" \
                "$archive_root/bin/" \
                "$archive_root/bin/divyam"
            if [[ -f "$repository_root/NOTICE" ]]; then
                printf '%s\n' "$archive_root/NOTICE"
            fi
        } | LC_ALL=C sort
    )
    local actual_entries
    actual_entries=$(tar -tzf "$archive_path" | sed 's#^\./##' | LC_ALL=C sort)

    if [[ "$actual_entries" != "$expected_entries" ]]; then
        echo "Installer archive has unexpected contents" >&2
        printf '%s\n' "$actual_entries" >&2
        exit 1
    fi

    tmp_dir=$(mktemp -d)
    trap 'rm -rf "$tmp_dir"' EXIT

    tar -xzf "$archive_path" -C "$tmp_dir"
    local binary_path="$tmp_dir/$archive_root/bin/divyam"
    test -x "$binary_path"
    "$binary_path" --help >/dev/null
    "$binary_path" version | grep -F "Version: $version" >/dev/null
}

smoke_direct_installer() {
    local installer_path=${1:?usage: test-macos-artifacts.sh direct-installer <install.sh> <archive.tar.gz> <version>}
    local archive_path=${2:?usage: test-macos-artifacts.sh direct-installer <install.sh> <archive.tar.gz> <version>}
    local version=${3:?usage: test-macos-artifacts.sh direct-installer <install.sh> <archive.tar.gz> <version>}

    test -f "$installer_path"
    test -f "$archive_path"

    local archive_name
    archive_name=$(basename "$archive_path")
    local archive_sha
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

    local web_root="$tmp_dir/web"
    local release_root="$web_root/releases/download/v$version"
    local home_dir="$tmp_dir/home"
    local install_dir="$tmp_dir/bin"
    local data_dir="$tmp_dir/data"
    local installer_tmp_dir="$tmp_dir/installer-tmp"
    mkdir -p "$release_root" "$web_root/releases/latest/download" "$installer_tmp_dir"
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
        local help_output
        help_output=$(bash "$installer_path" "$help_option")
        printf '%s\n' "$help_output" | grep -F 'Usage: install.sh' >/dev/null
    done

    assert_failure 'unknown option: --unknown' run_installer --unknown
    assert_failure '--version requires X.Y.Z' run_installer --version
    assert_failure 'release version must be X.Y.Z; received: 1.2' run_installer --version 1.2
    assert_failure '--version cannot be used with --uninstall' run_installer --uninstall --version "$version"
    assert_failure '--uninstall cannot be used with --version' run_installer --version "$version" --uninstall
    assert_failure 'interactive confirmation requires a terminal; rerun with --yes' run_installer

    local install_output
    install_output=$(run_installer --yes)
    printf '%s\n' "$install_output" | grep -F "Installing Divyam CLI $version." >/dev/null

    local launcher="$install_dir/divyam"
    test -L "$launcher"
    test "$(readlink "$launcher")" = "$data_dir/releases/$version/bin/divyam"
    test -x "$data_dir/releases/$version/bin/divyam"
    "$launcher" --help >/dev/null
    "$launcher" version | grep -F "Version: $version" >/dev/null
    grep -Fqx '# Added by Divyam CLI installer' "$home_dir/.zshrc"
    test -z "$(find "$installer_tmp_dir" -mindepth 1 -maxdepth 1 -print -quit)"

    rm "$release_root/$archive_name" "$release_root/SHA256SUMS"
    local already_installed_output
    already_installed_output=$(run_installer --version "$version" -y)
    printf '%s\n' "$already_installed_output" | grep -F "Divyam CLI $version is already installed" >/dev/null

    assert_failure 'interactive confirmation requires a terminal; rerun with --yes' run_installer --uninstall
    run_installer --uninstall --yes
    test ! -e "$launcher"
    test ! -e "$data_dir/releases"
    test -f "$home_dir/.zshrc"
    test -z "$(find "$installer_tmp_dir" -mindepth 1 -maxdepth 1 -print -quit)"
}

test_installer_upgrade() {
    local installer_path=${1:?usage: test-macos-artifacts.sh installer-upgrade <install.sh>}
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

    local architecture
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

    local web_root="$tmp_dir/web"
    local home_dir="$tmp_dir/home"

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

    local initial_output
    initial_output=$(run_installer)
    printf '%s\n' "$initial_output" | grep -F 'Installing Divyam CLI 1.2.3.' >/dev/null
    local launcher="$home_dir/.local/bin/divyam"
    "$launcher" version | grep -F 'Version: 1.2.3' >/dev/null

    local archive_name="divyam-cli-1.2.4-darwin-$architecture.tar.gz"
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
    local update_output
    update_output=$(run_installer)
    printf '%s\n' "$update_output" | grep -F 'Updating Divyam CLI from 1.2.3 to 1.2.4.' >/dev/null
    "$launcher" version | grep -F 'Version: 1.2.4' >/dev/null

    rm "$web_root/releases/download/v1.2.4/$archive_name" "$web_root/releases/download/v1.2.4/SHA256SUMS"
    local already_installed_output
    already_installed_output=$(run_installer)
    printf '%s\n' "$already_installed_output" | grep -F 'Divyam CLI 1.2.4 is already installed' >/dev/null

    env HOME="$home_dir" bash "$installer_path" --uninstall --yes
    test ! -e "$launcher"
}

smoke_pkg() {
    local pkg_path=${1:?usage: test-macos-artifacts.sh pkg <package.pkg> <version>}
    local version=${2:?usage: test-macos-artifacts.sh pkg <package.pkg> <version>}
    local repository_root
    repository_root=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)

    test -f "$pkg_path"

    package_identifier="ai.divyam.divyam-cli"
    managed_paths=(
        /usr/local/bin/divyam
        /usr/local/etc/bash_completion.d/divyam
        /usr/local/share/zsh/site-functions/divyam
    )
    if [[ -f "$repository_root/NOTICE" ]]; then
        managed_paths+=(
            /usr/local/share/doc/divyam-cli/LICENSE
            /usr/local/share/doc/divyam-cli/NOTICE
        )
    fi

    local managed_path
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
}

mode=${1:-}
case "$mode" in
    verify-archive)
        shift
        verify_archive "$@"
        ;;
    direct-installer)
        shift
        smoke_direct_installer "$@"
        ;;
    installer-upgrade)
        shift
        test_installer_upgrade "$@"
        ;;
    pkg)
        shift
        smoke_pkg "$@"
        ;;
    *)
        usage >&2
        exit 1
        ;;
esac
