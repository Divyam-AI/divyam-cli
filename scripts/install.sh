#!/usr/bin/env bash

set -euo pipefail

repository="Divyam-AI/divyam-cli"
release_base_url="${DIVYAM_RELEASE_BASE_URL:-https://github.com/$repository/releases}"
install_dir="${DIVYAM_INSTALL_DIR:-$HOME/.local/bin}"
data_dir="${DIVYAM_DATA_DIR:-${XDG_DATA_HOME:-$HOME/.local/share}/divyam}"
requested_version="${DIVYAM_VERSION:-}"
modify_path="${DIVYAM_NO_MODIFY_PATH:-0}"
mode="install"
version_option_used=0

fatal() {
    echo "divyam installer: $*" >&2
    exit 1
}

usage() {
    cat <<'EOF'
Usage: install.sh [--version X.Y.Z] [--uninstall] [--help|-h]

Environment:
  DIVYAM_INSTALL_DIR       Directory containing the divyam launcher.
  DIVYAM_DATA_DIR          Directory containing installed Divyam releases.
  DIVYAM_NO_MODIFY_PATH=1  Do not add the install directory to a shell profile.
EOF
}

download_file() {
    local url=$1
    local destination=$2

    if command -v curl >/dev/null 2>&1; then
        curl --fail --location --silent --show-error --retry 3 --output "$destination" "$url"
    elif command -v wget >/dev/null 2>&1; then
        wget --quiet --tries=3 --output-document="$destination" "$url"
    else
        fatal "curl or wget is required"
    fi
}

detect_architecture() {
    case "$(uname -s)" in
        Darwin) ;;
        *) fatal "this installer currently supports macOS only" ;;
    esac

    case "$(uname -m)" in
        arm64|aarch64) echo "arm64" ;;
        x86_64|amd64)
            if [[ "$(sysctl -n sysctl.proc_translated 2>/dev/null || true)" == "1" ]]; then
                echo "arm64"
            else
                echo "x86_64"
            fi
            ;;
        *) fatal "unsupported macOS architecture: $(uname -m)" ;;
    esac
}

is_version() {
    [[ $1 =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]
}

is_on_path() {
    case ":${PATH:-}:" in
        *":$install_dir:"*) return 0 ;;
        *) return 1 ;;
    esac
}

configure_path() {
    if [[ "$modify_path" == "1" ]] || is_on_path; then
        return
    fi

    local shell_name profile marker path_line
    shell_name=$(basename "${SHELL:-zsh}")
    marker="# Added by Divyam CLI installer"
    path_line="export PATH=\"$install_dir:\$PATH\""

    case "$shell_name" in
        zsh) profile="${ZDOTDIR:-$HOME}/.zshrc" ;;
        bash) profile="$HOME/.bashrc" ;;
        *)
            echo "Add $install_dir to PATH to run divyam from future shells."
            return
            ;;
    esac

    mkdir -p "$(dirname "$profile")"

    if [[ -f "$profile" ]] && grep -Fqx "$marker" "$profile"; then
        return
    fi

    {
        printf '\n%s\n' "$marker"
        printf '%s\n' "$path_line"
    } >> "$profile"

    echo "Added $install_dir to PATH in $profile. Open a new terminal or run:"
    echo "  $path_line"
}

uninstall() {
    local launcher="$install_dir/divyam"

    if [[ -L "$launcher" ]] && [[ "$(readlink "$launcher")" == "$data_dir/releases/"* ]]; then
        rm "$launcher"
    elif [[ -e "$launcher" ]]; then
        fatal "refusing to remove $launcher because it is not managed by this installer"
    fi

    rm -rf "$data_dir/releases"
    echo "Divyam CLI has been removed. Shell profile entries and configuration were preserved."
}

while (($#)); do
    case "$1" in
        --version)
            (($# >= 2)) || fatal "--version requires X.Y.Z"
            [[ "$mode" != "uninstall" ]] || fatal "--version cannot be used with --uninstall"
            requested_version=$2
            version_option_used=1
            shift 2
            ;;
        --uninstall)
            ((version_option_used == 0)) || fatal "--uninstall cannot be used with --version"
            mode="uninstall"
            shift
            ;;
        --help|-h)
            usage
            exit 0
            ;;
        *) fatal "unknown option: $1" ;;
    esac
done

if [[ "$mode" == "uninstall" ]]; then
    uninstall
    exit 0
fi

mkdir -p "$install_dir" "$data_dir/releases"

if [[ -z "$requested_version" ]]; then
    version_file=$(mktemp)
    if ! download_file "${DIVYAM_VERSION_URL:-$release_base_url/latest/download/latest-version}" "$version_file"; then
        rm -f "$version_file"
        fatal "failed to resolve the latest release version"
    fi
    requested_version=$(tr -d '[:space:]' < "$version_file")
    rm -f "$version_file"
fi

is_version "$requested_version" || fatal "release version must be X.Y.Z; received: $requested_version"

architecture=$(detect_architecture)
archive_name="divyam-cli-$requested_version-darwin-$architecture.tar.gz"
archive_url="${DIVYAM_ARCHIVE_URL:-$release_base_url/download/v$requested_version/$archive_name}"
checksums_url="${DIVYAM_CHECKSUMS_URL:-$release_base_url/download/v$requested_version/SHA256SUMS}"
release_dir="$data_dir/releases/$requested_version"
launcher="$install_dir/divyam"

if [[ -e "$launcher" || -L "$launcher" ]]; then
    if [[ ! -L "$launcher" ]] || [[ "$(readlink "$launcher")" != "$data_dir/releases/"* ]]; then
        fatal "refusing to replace unmanaged executable: $launcher"
    fi
fi

work_dir=$(mktemp -d)
cleanup() {
    rm -rf "$work_dir"
}
trap cleanup EXIT

archive_path="$work_dir/$archive_name"
checksums_path="$work_dir/SHA256SUMS"

echo "Downloading Divyam CLI $requested_version for macOS $architecture..."
download_file "$archive_url" "$archive_path"
download_file "$checksums_url" "$checksums_path"

expected_checksum=$(awk -v archive="$archive_name" '$2 == archive { print $1; exit }' "$checksums_path")
[[ "$expected_checksum" =~ ^[a-f0-9]{64}$ ]] || fatal "no valid SHA-256 checksum for $archive_name"

actual_checksum=$(shasum -a 256 "$archive_path" | awk '{ print $1 }')
[[ "$actual_checksum" == "$expected_checksum" ]] || fatal "SHA-256 verification failed for $archive_name"

tar -xzf "$archive_path" -C "$work_dir"
archive_root="$work_dir/divyam-cli-$requested_version"
binary_path="$archive_root/bin/divyam"
[[ -x "$binary_path" ]] || fatal "release archive does not contain an executable divyam binary"
"$binary_path" --help >/dev/null
"$binary_path" version | grep -F "Version: $requested_version" >/dev/null

if [[ -d "$release_dir" ]]; then
    existing_binary="$release_dir/bin/divyam"
    [[ -x "$existing_binary" ]] || fatal "existing release directory is incomplete: $release_dir"
    "$existing_binary" --help >/dev/null
    "$existing_binary" version | grep -F "Version: $requested_version" >/dev/null
else
    mv "$archive_root" "$release_dir"
fi

temporary_launcher="$install_dir/.divyam-install-$$"
ln -s "$release_dir/bin/divyam" "$temporary_launcher"
mv -f "$temporary_launcher" "$launcher"

configure_path

echo "Divyam CLI $requested_version installed at $launcher"
