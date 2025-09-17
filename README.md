# Divyam CLI

Divyam command line tool to manage and configure Divyam and verify Divyam
installations.

This is a native application that requires not dependencies currently available

- Linux (debian and RHEL based) amd64
- Linux (debian and RHEL based) arm64
- macOS (Apple silicon M1/M2/M3/M4)
- macOS (Intel/amd64)

## Installation

Download the appropriate artifact for
the [latest release](https://github.com/Divyam-AI/divyam-cli/releases/latest)
based on

- your OS
- the CPU architecture (arm64, amd64). The Apple Silicon mac books use arm64
  architecture.

### macOS

* Apple Silicon arm64 (M1/M2/M3/M4)- Download the `divyam-cli-<version>.arm64.
pkg` file.
* Apple amd64 - Download the `divyam-cli-<version>.x86_64.pkg` file.

#### Finder

Locate the downloaded in the finder launch the installer and follow the
installation wizard to complete installation.

#### Terminal

Install the package

```shell
# Install the package
sudo installer -pkg divyam-cli-*.pkg  -target /
```

#### Autocomplete

##### Bash

To enable autocompletion for divyam, add the following line to your ~/.bashrc (
or ~/.bash_profile on macOS):

```shell
source /usr/local/etc/bash_completion.d/divyam
```

Then reload your shell configuration:

```shell
source ~/.bashrc    # or source ~/.bash_profile
```

##### Zsh

To enable autocompletion for divyam, ensure the completion script is available
and then add the following to your ~/.zshrc:

```shell
source /usr/local/etc/bash_completion.d/divyam
```

Then reload your shell configuration:

```shell
source ~/.zshrc
```

### Linux

#### Debian based distro

Download the `divyam-cli_<version>_amd64.deb` or
`divyam-cli_<version>_arm64.deb` based on your architecture from the GitHub
releases page.

Install the deb package.

```shell
sudo dpkg -i divyam*.deb 
```

#### RHEL based distro

Download the `divyam-cli-<version>.x86_64.rpm` or
`divyam-cli-<version>.aarch64.rpm` based on your architecture from the GitHub
releases page.

Install the RPM package.

```shell
sudo rpm -ivh divyam*.rpm 
```

#### Autocomplete

##### Bash

To enable autocompletion for divyam, add the following line to your ~/.bashrc (
or ~/.bash_profile on macOS):

```shell
source /etc/bash_completion.d/divyam
```

Then reload your shell configuration:

```shell
source ~/.bashrc    # or source ~/.bash_profile
```

##### Zsh

To enable autocompletion for divyam, ensure the completion script is available
and then add the following to your ~/.zshrc:

```shell
source /etc/bash_completion.d/divyam
```

Then reload your shell configuration:

```shell
source ~/.zshrc
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

### Autocomplete

Use TAB to see commands and subcommand based on current context, and
autocomplete them.

Type '--' + TAB to see what options are available.

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
