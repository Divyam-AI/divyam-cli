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

#### Apple Silicon (M1/M2/M3/M4)

Prerequisite

- [Homebrew](https://brew.sh/)

Select latest successful build
from [Github actions](https://github.com/Divyam-AI/divyam-cli/actions).

To the bottom of the page from the `Artifacts` section, download the
`macos-apple-silicon` zip file.

Extract the zip contents after download.

```shell
# Change appropriately to match the downloaded file.
unzip macos-app.zip 
unzip divyam-cli*.zip
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

#### Apple Intel (amd64)

Prerequisite

- [Homebrew](https://brew.sh/)

Select latest successful build
from [Github actions](https://github.com/Divyam-AI/divyam-cli/actions).

To the bottom of the page from the `Artifacts` section, download the
`macos-amd64` zip file.

Extract the zip contents after download.

```shell
# Change appropriately to match the downloaded file.
unzip macos-app.zip 
unzip divyam-cli*.zip
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
# Change appropriately to match the downloaded file.
unzip linux-packages-amd64.zip 
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

sudo rpm -ivh divyam*.rpm 
```

## Usage

Once installed `divyam` command should be available for running on a terminal.

Verify installation

```shell
divyam --help
```

This should show something like this

```shell
Usage: divyam [--help] [COMMAND]
Divyam CLI
      --help   display help message
Commands:
  org         Manage orgs
  sa          Manage service accounts
  eval        Manage evals
  selector    Manage selectors
  model-info  Manage model info.
  user        Manage users
  chat        Command line chatbot
```

The cli is organized to work with commands and subcommands to manage various
divyam entities, like users, service accounts, evals. It also provides a
simple command based chatbot to interact with divyam.

To get help form any command use the `--help` option.

For e.g.

```shell
divyam user --help

Usage: divyam user [--help] [COMMAND]
Manage users
      --help   display help message
Commands:
  ls      List users
  create  Create a user
  update  Update a user
  get     Get a specific use
```

This lists the subcommands available for user management.

To get help form the ls command run

```shell
divyam user ls --help

Usage: divyam user ls [--disable-tls-verification] [--help] [-p[=<password>]]
                      [-t[=<apiToken>]] [-e=<endpoint>]
                      [--format=<outputFormat>] -o=<orgId> [-u=<user>]
List users
      --disable-tls-verification
                         Disable TLS verification for non-production cases only
  -e, --endpoint=<endpoint>
                         The API endpoint base URL (e.g. https://api.divyam.ai)
      --format=<outputFormat>
                         output format. Valid values: TEXT, JSON, YAML
      --help             display help message
  -o, --org-id=<orgId>   The org id
  -p, --password[=<password>]
                         User password. Required if service account api token
                           is not provided.
  -t, --api-token[=<apiToken>]
                         Service account api token
  -u, --user=<user>      User email-id. Required if service account api token
                           is not provided.
```

To run a command provide the appropriate arguments.

### Authentication

The cli supports authenticating as

- Users - with user email and password. The `-u` and `-p` arguments provide
  the user email and password
- Service accounts - with Divyam API token. The `-t` options provides the
  service account token.

#### Secure credentials

The credentials can be provided with a prompt from the input stream

```shell
# Lists the selectors for org id 10
divyam selector ls -e https://api.preprod.divyam.ai -u admin@dashboard.divyam.ai -p -o 10 
Enter value for --password (User password. Required if service account api token is not provided.)
```

```shell
# Lists the selectors for org id 10 using a service account
divyam selector ls -e https://api.preprod.divyam.ai -t -o 10
Enter value for --api-token (Service account api token): 
```

#### As command line arguments

```shell
# Lists the selectors for org id 10
divyam selector ls -e https://api.preprod.divyam.ai -u admin@dashboard.divyam.ai -p "********" -o 10
```

```shell
# Lists the selectors for org id 10 using a service account
divyam selector ls -e https://api.preprod.divyam.ai -t "*****" -o 10
```

### Divyam endpoint

The `-e` argument selects the Divyam endpoint the CLI interacts with.
Supports

- `http` for test or private installations
- `https` for secure public installations

For example

```shell
divyam user ls -e https://api.preprod.divyam.ai -u admin@dashboard.divyam.ai -p -o 10 
```

## Examples

### List all users

```shell
divyam user ls -e https://api.divyam.ai -u admin@dashboard.divyam.ai -p "*******" -o 10 
```

### Debug chat completions with custom payload

```shell
cat my-payload | divyam debug chat -e http://localhost:8888 -t "service-account-api-token"

```

### Run chatbot with debug mode

```shell
divyam chat -e http://localhost:8888 -t "service-account-api-token" --model-name gpt-4.1-mini --debug
```
