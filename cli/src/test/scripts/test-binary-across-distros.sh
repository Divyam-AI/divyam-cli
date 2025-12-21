#!/bin/bash

# Cross-Distro Binary Testing Script
# Tests a binary across multiple Linux distributions

set -e

set -euo pipefail

# Ensure all children die when this script exits
trap 'kill $(jobs -p) 2>/dev/null || true' EXIT

# Configuration
PORT=8484
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BINARY_PATH="${BINARY_PATH:-$SCRIPT_DIR/../../../build/native/nativeCompile/divyam}"
ENDPOINT="http://localhost:$PORT"
USER="admin@dashboard.divyam.ai"
PASSWORD="divyam123"
SA_TOKEN="token"
COMMON_ARGS="--disable-tls-verification"
RESULTS_DIR="$SCRIPT_DIR/../../../build/distro-test-results"

DOCKER="docker"
if ! $DOCKER ls >> /dev/null 2>&1; then
  DOCKER="sudo docker"
fi

TEST_COMMANDS=()
TEST_COMMANDS+=("org ls -e $ENDPOINT -u $USER -p $PASSWORD $COMMON_ARGS --stacktrace")
TEST_COMMANDS+=("sa create -e $ENDPOINT -u $USER -p $PASSWORD $COMMON_ARGS --name test_sa --org-id 1")
TEST_COMMANDS+=("sa ls -e $ENDPOINT -u $USER -p $PASSWORD $COMMON_ARGS --org-id 1")
TEST_COMMANDS+=("chat -e $ENDPOINT -u $USER -p $PASSWORD $COMMON_ARGS -t $SA_TOKEN --model-name gpt-4.1-mini")

# Color codes
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Distro configurations: "name:image:setup_command"
DISTROS=(
        "Ubuntu 20.04:ubuntu:20.04:"
        "Ubuntu 22.04:ubuntu:22.04:"
        "Ubuntu 24.04:ubuntu:24.04:"
        "Debian 11:debian:11:"
        "Debian 12:debian:12:"
        "Alpine 3.18:alpine:3.18:"
        "Alpine 3.19:alpine:3.19:"
        "CentOS Stream 9:quay.io/centos/centos:stream9:"
        "AlmaLinux 8:almalinux:8:"
        "AlmaLinux 9:almalinux:9:"
        "Fedora 38:fedora:38:"
        "Fedora 39:fedora:39:"
        #"Arch Linux:archlinux:latest:"
)

# Function to print colored output
print_status() {
    local color=$1
    local message=$2
    echo -e "${color}${message}${NC}"
}

# Function to test binary on a distro
test_distro() {
    local name=$1
    local image=$2
    local setup_cmd=$3
    local container_name="test-$(echo "$name" | tr ' ' '-' | tr '[:upper:]' '[:lower:]')"

    print_status $BLUE "Testing on $name ($image)..."

    # Create results directory for this distro
    local result_dir="$RESULTS_DIR/$(echo "$name" | tr ' ' '-' | tr '[:upper:]' '[:lower:]')"
    mkdir -p "$result_dir"

    # Start container
    print_status $YELLOW "  Starting container..."
    if ! $DOCKER run --net=host -d --name "$container_name" "$image" tail -f /dev/null > "$result_dir/debug.txt" 2>&1; then
        print_status $RED "  ❌ Failed to start container"
        echo "FAILED: Could not start container" > "$result_dir/result.txt"
        return 1
    fi

    # Copy binary to container
    print_status $YELLOW "  Copying binary..."
    if ! $DOCKER cp "$BINARY_PATH" "$container_name:/tmp/divyam" >> "$result_dir/debug.txt" 2>&1; then
        print_status $RED "  ❌ Failed to copy binary"
        echo "FAILED: Could not copy binary" > "$result_dir/result.txt"
        $DOCKER rm -f "$container_name" > /dev/null 2>&1
        return 1
    fi

    # Make binary executable
    if ! $DOCKER exec "$container_name" chmod +x /tmp/divyam >> "$result_dir/debug.txt" 2>&1; then
        print_status $RED "  ❌ Failed to make binary executable"
        echo "FAILED: Could not make binary executable" > "$result_dir/result.txt"
        $DOCKER rm -f "$container_name" > /dev/null 2>&1
        return 1
    fi

    # Run setup command if provided
    if [ -n "$setup_cmd" ]; then
        print_status $YELLOW "  Running setup..."
        $DOCKER exec "$container_name" sh -c "$setup_cmd" > /dev/null 2>&1 || true
    fi

    # Test the binary
    print_status $YELLOW "  Testing binary..."
    local start_time=$(date +%s)

    # Remove all files
    rm -f $result_dir/* || true

    for TEST_COMMAND in "${TEST_COMMANDS[@]}"; do
      echo "Command: $TEST_COMMAND" >> "$result_dir/result.txt"

      if $DOCKER exec "$container_name" timeout 30 /tmp/divyam $TEST_COMMAND < "$SCRIPT_DIR/../data/prompts.txt" >> "$result_dir/stdout.txt" 2>> "$result_dir/stderr.txt"; then
          local end_time=$(date +%s)
          local duration=$((end_time - start_time))
          print_status $GREEN "  ✅ SUCCESS (${duration}s)"
          echo "SUCCESS: Completed in ${duration}s" >> "$result_dir/result.txt"
      else
          local exit_code=$?
          local end_time=$(date +%s)
          local duration=$((end_time - start_time))

          if [ $exit_code -eq 124 ]; then
              print_status $RED "  ❌ TIMEOUT (30s)"
              echo "FAILED: Timeout after 30s" >> "$result_dir/result.txt"
          else
              print_status $RED "  ❌ FAILED (exit code: $exit_code, ${duration}s)"
              echo "FAILED: Exit code $exit_code after ${duration}s" >> "$result_dir/result.txt"
          fi

          # Cleanup
          $DOCKER rm -f "$container_name" > /dev/null 2>&1

          return 1
      fi
    done

    # Get system info
    $DOCKER exec "$container_name" sh -c "cat /etc/os-release 2>/dev/null || echo 'OS info not available'" > "$result_dir/os-release.txt"
    $DOCKER exec "$container_name" uname -a > "$result_dir/uname.txt" 2>/dev/null || echo "uname not available" > "$result_dir/uname.txt"
    $DOCKER exec "$container_name" ldd --version > "$result_dir/libc-version.txt" 2>/dev/null || echo "glibc not available (might be musl)" > "$result_dir/libc-version.txt"

    # Cleanup
    $DOCKER rm -f "$container_name" > /dev/null 2>&1
    return 0
}

# Main execution
main() {
    print_status $BLUE "Cross-Distro Binary Testing Script"
    print_status $BLUE "=================================="

    # Check if binary exists
    if [ ! -f "$BINARY_PATH" ]; then
        print_status $RED "Error: Binary not found at $BINARY_PATH"
        exit 1
    fi

    # Create results directory
    rm -rf "$RESULTS_DIR"
    mkdir -p "$RESULTS_DIR"
    mkdir -p "$RESULTS_DIR/server/"

    # Start mock server
    print_status $YELLOW "Starting mock server"
    print_status $YELLOW "=================================="
    pushd $SCRIPT_DIR/../../../../
    ./gradlew :divyam-mock:run --args="--port $PORT --password $PASSWORD" 2>&1 > $RESULTS_DIR/server/logs &
    mkdir -p "$RESULTS_DIR/server"
    TIMEOUT=300   # seconds
    INTERVAL=1   # seconds
    HOST="localhost"
    end=$((SECONDS+TIMEOUT))
    success=0

    while (( SECONDS < end )); do
        if nc -z "$HOST" "$PORT" 2>/dev/null; then
            echo "✅ Port $PORT is open on $HOST"
            success=1
            break
        fi
        sleep $INTERVAL
    done
    if (( success == 0 )); then
        echo "❌ Timeout after $TIMEOUT seconds: $HOST:$PORT not open"
        exit 1
    fi
    popd

    # Test each distro
    local total_distros=${#DISTROS[@]}
    local successful=0
    local failed=0

    for distro_config in "${DISTROS[@]}"; do
        IFS=':' read -r name image tag setup_cmd <<< "$distro_config"

        image="$image:$tag"

        if test_distro "$name" "$image" "$setup_cmd"; then
            ((successful++))||true
        else
            ((failed++))||true
        fi
        echo
    done

    # Summary
    print_status $BLUE "Test Summary"
    print_status $BLUE "============"
    print_status $GREEN "✅ Successful: $successful/$total_distros"

    if [ $failed -gt 0 ]; then
        print_status $RED "❌ Failed: $failed/$total_distros"
    fi

    # Generate detailed report
    echo "# Cross-Distro Test Results" > "$RESULTS_DIR/summary.md"
    echo "Generated: $(date)" >> "$RESULTS_DIR/summary.md"
    echo >> "$RESULTS_DIR/summary.md"

    for distro_config in "${DISTROS[@]}"; do
        IFS=':' read -r name image setup_cmd <<< "$distro_config"
        local result_dir="$RESULTS_DIR/$(echo "$name" | tr ' ' '-' | tr '[:upper:]' '[:lower:]')"

        echo "## $name ($image)" >> "$RESULTS_DIR/summary.md"
        if [ -f "$result_dir/result.txt" ]; then
            echo "**Result:** $(cat "$result_dir/result.txt")" >> "$RESULTS_DIR/summary.md"
        fi

        if [ -f "$result_dir/stderr.txt" ] && [ -s "$result_dir/stderr.txt" ]; then
            echo "**Errors:**" >> "$RESULTS_DIR/summary.md"
            echo '```' >> "$RESULTS_DIR/summary.md"
            head -20 "$result_dir/stderr.txt" >> "$RESULTS_DIR/summary.md"
            echo '```' >> "$RESULTS_DIR/summary.md"
        fi
        echo >> "$RESULTS_DIR/summary.md"
    done

    print_status $BLUE "Detailed results saved to: $RESULTS_DIR/"
    print_status $BLUE "View summary: cat $RESULTS_DIR/summary.md"

    if [ $failed -gt 0 ]; then
        exit 1
    fi

    exit 0
}

# Usage information
usage() {
    echo "Usage: $0 [options]"
    echo "Options:"
    echo "  -b, --binary PATH     Path to binary (default: ./divyam)"
    echo "  -c, --command CMD     Test command (default: org ls -e https://...)"
    echo "  -h, --help           Show this help"
    echo
    echo "Example:"
    echo "  $0 -b ./my-binary -c \"--version\""
}

# Parse command line arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        -b|--binary)
            BINARY_PATH="$2"
            shift 2
            ;;
        -c|--command)
            TEST_COMMAND="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            usage
            exit 1
            ;;
    esac
done

# Run main function
main
