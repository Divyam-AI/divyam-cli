# Install on macOS

The native `divyam` binary is currently unsigned. Install it using one of the supported paths below.

## Homebrew Cask

```bash
brew tap Divyam-AI/tap
brew install --cask divyam-cli
divyam version
```

If macOS blocks the first run because it cannot verify the developer, remove the quarantine attribute from the installed Cask, then run the command again.

```bash
xattr -r -d com.apple.quarantine "$(brew --prefix)/Caskroom/divyam-cli"
divyam version
```

## Standalone installer

```bash
curl --fail --location --output /tmp/divyam-install.sh \
  https://github.com/Divyam-AI/divyam-cli/releases/latest/download/install.sh
bash /tmp/divyam-install.sh --yes
"$HOME/.local/bin/divyam" version
```

The installer downloads the architecture-specific archive and verifies its SHA-256 checksum before installing it under `~/.local/share/divyam`. It also adds `~/.local/bin` to the shell profile when needed.

If macOS blocks the first run because it cannot verify the developer, remove the quarantine attribute from the installed releases, then run the command again.

```bash
xattr -r -d com.apple.quarantine "$HOME/.local/share/divyam/releases"
"$HOME/.local/bin/divyam" version
```
