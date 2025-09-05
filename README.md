# Divyam CLI

Divyam command line tool to manage and configure Divyam and run quick manual
tests.

This is a native application that requires not dependencies currently available

- Linux (debian and RHEL based) amd64
- macOS (Apple silicon M1/M2/M3)

## Installation

**TBD:** Github Releases / and or proper repositories.

For now please pick the installers from Github actions. Actions allow
building for macOS.

### macOS

**TBD:** Apple DMG packaging or homebrew repository.

#### Apple Silicon (M1/M2/M3)

Prerequisite

- [Homebrew](https://brew.sh/)

Select latest successful build
from [Github actions](https://github.com/Divyam-AI/divyam-cli/actions).

To the bottom of the page from the `Artifacts` section, download the
`macos-app` zip file.

Extract the zip contents after download.

```shell
unzip macos-app.zip # Change appropriately to match the downloaded file.
```

Install as a local homebrew tap

```shell
# Use the extracted directory. Change name to match the extracted directory
cd macos-app

# Create a local tap directory
mkdir -p $(brew --repository)/Library/Taps/local/homebrew-divyam/Formula

cp divyam-cli.local.rb $(brew --repository)/Library/Taps/local/homebrew-divyam/Formula/divyam-cli.rb

# Install from the tap
brew install local/divyam/divyam-cli
```

### Linux

Select latest successful build
from [Github actions](https://github.com/Divyam-AI/divyam-cli/actions).

To the bottom of the page from the `Artifacts` section, download the zip
for your architecture. E.g. `linux-packages-amd64`.

Extract the zip contents after download.

```shell
unzip linux-packages-amd64.zip # Change appropriately to match the downloaded file.
```

#### Debian based distro

```shell
# Use the extracted directory. Change name to match the extracted directory
cd linux-packages-amd64

sudo dpkg -i divyam*.deb 
```

#### RHEL based distro

```shell
# Use the extracted directory. Change name to match the extracted directory
cd linux-packages-amd64

sudo rpm -ivn divyam*.rpm 
```
