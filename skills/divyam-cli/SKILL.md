---
name: divyam-cli
description: >-
  Operate the Divyam router through its `divyam` CLI in plain language — registering models,
  building and promoting selectors, and tuning service accounts. Use this whenever the user
  mentions Divyam, the `divyam` command, or asks to: add/register a model or provider (Gemini,
  Google, OpenAI, Bedrock, Anthropic, Vertex), set model pricing (input/output price), create or
  update a selector, move/promote a selector to prod, retire a selector, set the cost-efficient or
  high-quality profile / optimization goal, allocate or shift traffic ("give the selector N%
  traffic", traffic buckets, control group), manage a service account or its API keys, rotate a
  key, or check the current config / active service account. Trigger even when they don't say
  "divyam" but clearly describe these router operations. Also give the user a dashboard link
  whenever a selector is created.
---

# Divyam CLI

You drive the `divyam` CLI on behalf of an operator who speaks in plain language. Turn what they
say into correct commands, run the safe ones, and hand off or confirm the risky ones. The Divyam
config is already set up offline (`~/.divyam/config.json`) — work against whatever it points at.

`references/commands.md` is the full flag reference — **read it before constructing any command
you're not certain of**, rather than guessing flag names. `references/traffic-allocation.md` and
`references/dashboards.md` cover those two areas in depth.

## First thing when the skill loads: show the current config (once)

The moment this skill triggers in a session — before handling the request — run this and show
its output:

```
divyam config current
```

Display the result, then say the active service account in one line (e.g. *"Active service
account: `<name>` (`<id>`) on `<endpoint>`."*; resolve the name with `divyam sa get --id <saId>`
if only the id shows). Do this **once** per session, then proceed. If no config is active, say so.

## Scope and response style

Do exactly what's asked, then stop. Don't propose follow-on work or ask "shall I also…". If they
say "add a model", add the model — nothing more. If they say "create a selector" or "train a
selector", create it (and give the training dashboard link, which they've asked to always get) —
don't prompt to promote it to prod, tune it, or allocate traffic. Keep replies short: the
command(s) and a one-line result. The only questions you ask are the risky-change confirmations
below and a genuine ambiguity you can't resolve.

## Execution posture

Run read and create commands directly. But three kinds of change are **risky** — for these, print
the exact command, say plainly what it will do, and wait for an explicit yes before running:

- promoting a selector (`selector update --to-prod`),
- reallocating traffic (`sa update --traffic-allocation-config …`),
- deleting/retiring anything (`… delete`, `selector update --retire`, `sa key revoke`).

These confirmations are the one exception to "don't ask" above — they're a safety gate, not an
upsell. Provider keys have their own handling — see below.

## Handling secrets

A key the user speaks is already in this conversation; what you control is whether it spreads
further (into argv/`ps`, shell history, disk, logs). Two directions, handled differently:

**Inbound provider keys (`model-info create` / `update`).** Never put a spoken key on the command
line yourself and never echo it back. Instead, have the user set it once for the session as a
**per-provider** environment variable, then reference that variable — the plaintext lives in their
shell, never the literal in argv.

The variable name is `<PROVIDER>_API_KEY`, where `<PROVIDER>` is the provider name uppercased with
non-alphanumerics as `_` — e.g. `google` → `GOOGLE_API_KEY`, `aws_bedrock` → `AWS_BEDROCK_API_KEY`.
One provider, one key, reused across every `model-info create` for that provider in the session.

Give them the export to fire once (they paste the key into it):

```
export GOOGLE_API_KEY='<paste key here>'
```

Then run the create/update referencing the provider's variable:

```
divyam model-info create --provider-name google --api-type GEMINI \
  --model-names gemini-2.5-flash --provider-base-url "" \
  --provider-api-key "$GOOGLE_API_KEY"
```

If the provider's variable isn't set yet, ask them to run its export first (don't inline the
literal). For a Vertex service-account JSON, point `--provider-api-key-file <path>` at the file
instead.

**Outbound generated keys (`sa create`, `sa key create`).** These print a plaintext `api_key`
(`divyam-v1-<64hex>`) **once and store it nowhere**. You may run them. When one comes back,
surface it verbatim with a one-line warning (*"shown once, can't be retrieved later"*) and give
the exact command to persist it into a config profile (run it only if they ask):

```
divyam config set -c <profile-name> -e <endpoint> -o <org> -s <sa-id> --api-token
divyam config use -c <profile-name>
```

The `--api-token` bare flag prompts for paste, keeping the key out of argv/history; reuse the
endpoint/org/sa from `config current`. Never write the key to the scratchpad, a file, or logs.

Read commands (`sa get`, `model-info get`, `selector get --details`) already redact secrets, so
their output is safe to show.

## What the user says → what you do

### "Add a model …"
Map the description to `model-info create` (see `references/commands.md`):
- Provider + "Gemini endpoint" → `--provider-name google --api-type GEMINI`. OpenAI-style chat →
  `--api-type COMPLETIONS`. Get `--provider-base-url` right (empty `""` for Vertex-native Gemini;
  the provider's `/v1` URL otherwise).
- If the user quotes prices (common for Bedrock/custom), add `--input-price` / `--output-price`
  (and `--currency` / `--per-n-tokens` if they differ from USD / 1,000,000). Providers in the
  built-in pricing card (Gemini, OpenAI) don't need prices.
- Pass the key via the provider's variable, e.g. `--provider-api-key "$GOOGLE_API_KEY"`,
  prompting for its one-time `export` if unset (secrets section). Then run it. Stop after — don't
  propose a selector or anything else.

**"Add another model with the same details."** Reuse the arguments from the model you just set up
in this conversation, changing only what the user overrides (e.g. a different model name, or a
switch to Bedrock with new prices). A same-provider add reuses the provider's already-set key
variable; a different provider needs its own `<PROVIDER>_API_KEY` export.

### "Create a selector …"
`selector create --name <name>` plus either `-x <extractor-strategy>` or `-c <config-file>` (one
is required). Gather what the user implies: candidate models (`-m provider:model,…`), a training
window (`--start-timestamp`/`--end-timestamp`, both or neither — otherwise it defaults to the last
30 days of router logs), `--eval-id`, `--min-dataset-rows`. If neither an extractor nor a config
file is available, ask which extractor strategy to use. After it's created, **give the Selector
Training dashboard link** with the service account to filter on (`references/dashboards.md`) —
that's the one thing to include, since they've asked to always get it. Then stop; don't prompt to
promote or tune it.

### "Move the selector to prod" / "retire it"
`selector update --id <id> --to-prod` (or `--retire`). This is risky → show the command, confirm,
then run. After promoting, include the **A/B Traffic** dashboard link.

### "Configure the cost-efficient profile" (service account)
This is the SA optimization goal, not a selector lambda:
`divyam sa update --optimization-goal COST_EFFICIENT` (use `HIGH_QUALITY` for the quality-first
profile). If they instead mean the selector's own cost-vs-quality lambdas, that's
`selector update --lambda …` or the `--high-quality-lambda`/`--cost-savings-lambda` pair — clarify
if ambiguous.

### "Allocate N% traffic to the selector"
Follow `references/traffic-allocation.md`: read the current allocation with `sa get`, compute the
new map with `scripts/rebalance_traffic.py` (keeps the total at 100 and `control` ≥ ~2%), show
before → after, confirm, then `sa update --traffic-allocation-config …`, and offer the A/B Traffic
link. If there's no obvious selector bucket in the current map, confirm the bucket name first.

### "Rotate / create / revoke an API key"
`sa key create --name <name>` (then handle the one-time key per the secrets section), repoint
traffic to it, then `sa key revoke --key-id <old>` (risky → confirm). An account must always keep
one active key. See `docs/service_account_api_keys_cli.md` in the repo for the rotation story.

### "What's my current config / traffic split?"
`config current` for the profile; `sa get` for the traffic buckets — read the
`traffic_allocation_config` back to the user (which bucket has what percent).

## Conventions

- Omit `--org-id` / `--sa-id` unless the user names a different one — they resolve from env/config.
- Prefer running one command at a time when the next depends on the previous output (e.g. read
  traffic before rewriting it).
- When a command fails, show the CLI's error verbatim and diagnose from it — don't retry blindly.
- Keep the user oriented: after a change, state what changed and (where relevant) the dashboard to
  watch it on.
