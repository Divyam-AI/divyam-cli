# Divyam CLI command reference

Flag names below are verbatim from the CLI source (`cli/src/main/kotlin/ai/divyam/cli/**`)
and `client/specs/openapi.json`. Only the flags you actually need per task are listed; run
`divyam <group> <cmd> --help` to see the rest.

## Context resolution (org / SA / endpoint)

Almost every command takes `--org-id` and `--sa-id`, but you rarely pass them. They resolve
in this order: **explicit flag → `DIVYAM_ORG_ID` / `DIVYAM_SA_ID` env var → current config
file** (`~/.divyam/config.json`). Omit them unless the user names a different org/SA than the
active config. The endpoint and auth (user/password or api token) come from the config too.

- `divyam config current` — print the active config (endpoint, orgId, serviceAccountId). Add
  `--format text` for a table. This is the "show me the current config" command.
- `divyam config list` / `config get -c <name>` / `config use -c <name>` — manage profiles.
- `divyam config set -c <name> [-e <endpoint>] [-u <user>] [-p] [-t] [-o <org>] [-s <sa>]` —
  create/merge a profile. `-p`/`--password` and `-t`/`--api-token` are **interactive** (pass
  the bare flag and the CLI prompts, no echo). Merges onto an existing profile of the same name.

## Models — `divyam model-info`

### create (register a model)
```
divyam model-info create \
  --provider-name <name> \
  --model-names <m1,m2,...> \
  --api-type <COMPLETIONS|RESPONSES|GEMINI> \
  --provider-base-url <url-or-empty> \
  --provider-api-key            # bare interactive flag — see secrets note below
  [--input-price N --output-price N] [--currency USD] [--per-n-tokens 1000000] \
  [--model-configs-json '{...}'] [--supported-modalities text] [--is-selection-enabled true]
```
- `--provider-name`: e.g. `google`, `openai`, `bedrock`, `anthropic` (free string — the value
  the router expects for that provider).
- `--api-type`: **"Gemini endpoint" → `GEMINI`.** OpenAI-compatible/chat → `COMPLETIONS`.
  Responses API → `RESPONSES`.
- `--model-names`: comma-separated. `model_name:base_model_name` to alias a base model.
- `--provider-base-url` is **required**. OpenAI: `https://api.openai.com/v1`. Gemini
  OpenAI-compat: `https://generativelanguage.googleapis.com/v1beta/openai`. **Vertex-native
  GEMINI: pass an empty string `""`.** AI-Studio Gemini key: `""` is fine.
- Pricing: providers in the built-in pricing card (Gemini, OpenAI, …) don't need prices. When
  the user quotes prices (typically Bedrock/custom), pass `--input-price`/`--output-price`
  (per `--per-n-tokens`, default 1,000,000; `--currency` default USD). Both price flags together.
- Vertex (no API key): omit the key and put `{"project_id":"...","location":"...",
  ["target_principal":"..."]}` in `--model-configs-json`. See `docs/gemini-model-info.md`
  in the repo for AI-Studio / Vertex-SA-key / ADC / impersonation variants.
- **Secrets:** `--provider-api-key` is declared `interactive`. Never put a spoken key on the
  command line. Build the command with `--provider-api-key` as the last, value-less flag and
  hand it to the user to run in their terminal, where the CLI prompts with no echo. For Vertex
  SA JSON, `--provider-api-key-file <path>` is the alternative. See the SKILL's secrets section.

### update
```
divyam model-info update --id <model-info-id> [--model-name ...] [--base-model-name ...] \
  [--provider-name ...] [--provider-base-url ...] [--api-type ...] \
  [--input-price N --output-price N | --skip-pricing-update] \
  [--model-configs-json '{...}'] [--is-selection-enabled true|false] [--is-active true|false]
```
`--skip-pricing-update` when you're only changing credentials/config. `--provider-api-key` is
interactive here too — hand off if a new key is involved.

### others
`divyam model-info list` · `model-info get --id <id>` (redacts secrets) · `model-info delete --id <id>`.

## Selectors — `divyam selector`

### create
```
divyam selector create --name <name> \
  ( -x <extractor-strategy> | -c <config.yaml|json> ) \
  [-m <provider:model,provider:model,...>] [--eval-id <id>] \
  [--min-dataset-rows N] [--start-timestamp <ts> --end-timestamp <ts>]
```
- Requires `--name` **and** either `-x/--extractor-strategy` or `-c/--config-file`.
- With no config file and no timestamps, the CLI defaults to a 30-day router-logs training
  window. `--start-timestamp`/`--end-timestamp` must be given together; accept `YYYY-MM-DD`
  or full ISO-8601 (`2026-07-01T09:00:00`).
- `-m/--candidate-models`: comma list of `provider:model` (or bare `model`).
- A new selector starts in a non-prod state; you promote it later with `update --to-prod`.

### update
```
divyam selector update --id <id> [--to-prod] [--retire] [--name ...] [--selector-endpoint ...] \
  [--lambda D | (--high-quality-lambda D --cost-savings-lambda D)]
```
- `--to-prod` → PROD. `--retire` → INACTIVE. (Mutually exclusive.)
- Lambdas tune the selector's willingness-to-pay (WTP) tradeoff: `--lambda` sets both
  high-quality and cost-efficient to the same value; or set the two independently with
  `--high-quality-lambda` + `--cost-savings-lambda` (both required together).
- **"Cost-efficient profile" for a service account is a different knob** — that's the SA
  optimization goal, not a selector lambda. See `sa update --optimization-goal` below.

### others
`divyam selector list` · `selector get --id <id> [--details]` (redacts secrets under `--details`)
· `selector clone` · `selector delete --id <id>`.

## Service accounts — `divyam sa`

### update (config knobs)
```
divyam sa update [--id <sa>] \
  [--traffic-allocation-config '{"control":10,"selector_disabled":90}'] \
  [--optimization-goal <COST_EFFICIENT|HIGH_QUALITY>] \
  [--auth-mode <API_KEYS_SAVED|API_KEYS_IN_HEADER>] \
  [--is-admin true|false] [--is-org-admin true|false] \
  [--retry-fallback-policy '{...}' | --max-retries N --retry-delay-s N ...] \
  [--allowed-ip-networks ...] [--blocked-ip-networks ...] [--ip-verifications ...]
```
- **`--optimization-goal COST_EFFICIENT`** is "make this service account cost-efficient";
  `HIGH_QUALITY` is the default / "prioritize quality".
- `--traffic-allocation-config` takes a JSON `{bucket: percent}` map (percents may be integers
  or decimals). See `references/traffic-allocation.md` for the read-adjust-confirm flow.
- `--id` falls back to the current config's SA if omitted.

### create
```
divyam sa create --name <name> [--org-id N] [--traffic-allocation-config '{...}'] \
  [--optimization-goal ...] [--auth-mode ...] [--is-admin ...] [--is-org-admin ...] [retry flags]
```
Returns the account **and its one-time `default_key` plaintext** — treat like `sa key create`
output (secrets section).

### keys — `divyam sa key`
```
divyam sa key ls     [-s <sa>]
divyam sa key create [-s <sa>] --name <name>      # prints api_key ONCE, stored nowhere
divyam sa key revoke [-s <sa>] --key-id <id>
```
Rotation = create replacement → repoint traffic → revoke old. An account must always keep one
active key (revoking the last one is refused). See `docs/service_account_api_keys_cli.md`.

### others
`divyam sa list` · `sa get --id <id>` (redacts secrets; shows `traffic_allocation_config`).

## Evals — `divyam eval`
`divyam eval list|create|get|update` — evaluation datasets referenced by `selector create --eval-id`.

## Enums (from `client/specs/openapi.json`)
- `ModelApiType`: `COMPLETIONS`, `RESPONSES`, `GEMINI`
- `OptimizationGoal`: `COST_EFFICIENT`, `HIGH_QUALITY`
- `ModelAPIAuthMode`: `API_KEYS_SAVED`, `API_KEYS_IN_HEADER`
- `Modality`: `text`
