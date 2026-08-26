---
name: divyam-cli
description: >-
  Operate the Divyam router through its `divyam` CLI in plain language — models, selectors, and
  service accounts. Use whenever the user mentions Divyam or the `divyam` command, or asks to:
  add/register a model or provider (Gemini, Google, OpenAI, Bedrock, Anthropic, Vertex), set model
  pricing, create/update/train a selector, promote a selector to prod, retire it, set the
  cost-efficient or high-quality optimization goal, allocate or shift traffic ("give the selector
  N% traffic", traffic buckets, control group), manage a service account or its API keys, rotate a
  key, or check the current config / active service account. Trigger even when they don't say
  "divyam" but clearly describe these router operations.
---

# Divyam CLI

Turn plain-language requests into correct `divyam` commands: run safe ones, confirm risky ones.
The config is already set up (`~/.divyam/config.json`) — use whatever it points at.

**`references/commands.md` is the full flag reference — read it before building any command you're
unsure of, rather than guessing.** Deep dives: `references/traffic-allocation.md`,
`references/dashboards.md`.

## On load (once per session)

Run `divyam config current`, show its output, and name the active service account in one line
(resolve the name via `sa get --id <id>` if only the id shows). Don't repeat it later in the session.

## Rules

- Do exactly what's asked, then stop — no "shall I also…" proposals. Keep replies short: the
  command(s) and a one-line result.
- Omit `--org-id` / `--sa-id` unless the user names a different one (they resolve from env/config).
- Run one command at a time when the next depends on the previous output.
- On failure, show the CLI error verbatim and diagnose from it — don't retry blindly.
- **Confirm before risky changes — the only things you ask about:** promoting a selector
  (`--to-prod`), reallocating traffic (`--traffic-allocation-config`), and deleting / retiring /
  revoking. Print the exact command, say what it does, wait for an explicit yes.

## Provider keys (inbound)

Never place a spoken key on the command line or echo it back. Have the user export it once per
session as `<PROVIDER>_API_KEY` (provider name uppercased, non-alphanumerics → `_`; `google` →
`GOOGLE_API_KEY`, `aws_bedrock` → `AWS_BEDROCK_API_KEY`), then reference the variable:

```
export GOOGLE_API_KEY='<paste key>'
divyam model-info create --provider-name google --api-type GEMINI \
  --model-names gemini-2.5-flash --provider-base-url "" --provider-api-key "$GOOGLE_API_KEY"
```

One key per provider, reused across that provider's creates. If it's unset, ask for the export
first (never inline the literal). Vertex SA JSON: use `--provider-api-key-file <path>` instead.

## Generated keys (outbound)

`sa create` / `sa key create` print `api_key` **once and store it nowhere**. Show it verbatim with
"shown once, can't be retrieved later," and give the persist command (run only if asked):

```
divyam config set -c <name> -e <endpoint> -o <org> -s <sa-id> --api-token   # bare flag prompts for paste
divyam config use -c <name>
```

Never write a key to disk or logs. Read commands (`sa get`, `model-info get`, `selector get
--details`) redact secrets, so their output is safe to show.

## Intent → command (flag details in `references/commands.md`)

| User says | Do |
|---|---|
| add / register a model | `model-info create`. "Gemini endpoint" → `--api-type GEMINI`; `--provider-base-url ""` for Vertex-native Gemini, else the provider's `/v1` URL. Quoted prices → `--input-price`/`--output-price` (built-in card covers Gemini/OpenAI). Key via `$<PROVIDER>_API_KEY`. |
| add another, same details | reuse the last create's args, change only what's overridden; same provider reuses its key var. |
| create / train a selector | `selector create --name <n>` + `-x <extractor>` **or** `-c <file>`; optional `-m provider:model,…`, window `--start-timestamp`/`--end-timestamp`, `--eval-id`. Then give the **Selector Training** dashboard link (SA-filtered). Stop — don't offer to promote/tune. |
| move selector to prod / retire | `selector update --id <id> --to-prod` (or `--retire`) — risky, confirm first; include the **A/B Traffic** link after promoting. |
| make SA cost-efficient / quality-first | `sa update --optimization-goal COST_EFFICIENT` (or `HIGH_QUALITY`) — the SA goal, not a selector lambda. |
| allocate N% traffic to a selector | per `traffic-allocation.md`: `sa get` → `scripts/rebalance_traffic.py` (sum 100, control ≥ ~2%) → show before→after → confirm → `sa update --traffic-allocation-config`; then the A/B link. |
| rotate / create / revoke a key | `sa key create --name <n>` (handle the key above) → repoint → `sa key revoke --key-id <old>` (risky, confirm). Always keep one active key. |
| show config / traffic split | `config current`; `sa get` → read back `traffic_allocation_config`. |
