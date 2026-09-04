# macOS installation notes

Divyam CLI currently supports three macOS installation paths. [Certain]

## Preferred paths

### Direct installer script

```bash
curl -fsSL https://github.com/Divyam-AI/divyam-cli/releases/latest/download/install.sh | bash
```

This path downloads the published archive, verifies `SHA256SUMS`, installs the launcher to `~/.local/bin/divyam`, and preserves the previous installed version if a checksum check fails. [Certain]

### Homebrew Cask

```bash
brew tap Divyam-AI/tap
brew trust Divyam-AI/tap
brew install --cask divyam-cli
```

`brew trust` only trusts the Homebrew tap for Homebrew's tap-trust enforcement. It does not mark the installed artifact as trusted by macOS Gatekeeper. [Certain]

## Finder `.pkg` path

The published `.pkg` installs correctly when run through `installer`, but Finder can still block it because the package is not signed with an Apple Developer ID and has not been notarized by Apple. [Certain]

If Finder shows a message that the package or app "could not be identified" or "Apple could not verify it is free of malware", use one of these workarounds: [Certain]

1. Prefer the direct installer script or the Homebrew Cask route above. [Certain]
2. If you must use the `.pkg`, open it once, let Gatekeeper block it, then go to **System Settings > Privacy & Security** and choose **Open Anyway** for the blocked installer. [Likely]
3. On some macOS versions, right-clicking the `.pkg` in Finder and selecting **Open** also exposes the approval flow. [Likely]

This Gatekeeper behavior is separate from Homebrew tap trust. [Certain]
