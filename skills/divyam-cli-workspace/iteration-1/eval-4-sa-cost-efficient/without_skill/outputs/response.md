# Response: "make this service account cost efficient"

## Important caveat

"Cost efficient" isn't a single toggle — it maps to several concrete levers on a service account (model routing, token/spend caps, caching, rate limits, and turning off expensive options). I don't have the exact `divyam` command reference loaded, so the commands below are my best inference of the CLI surface. Please treat the exact flag names as provisional and verify against `divyam --help` before running. I've also kept every command read-only or reversible, and shown the inspection steps first so nothing is changed blind.

---

## Step 1 — Inspect the current account (read-only, run these first)

```
divyam service-account get sa_demo_123 --org 1
```

```
divyam service-account show-usage sa_demo_123 --org 1 --period 30d
```

```
divyam service-account get-config sa_demo_123 --org 1
```

These confirm which model(s) the account routes to, its current spend/token limits, whether caching is enabled, and where the spend is actually going — so we optimize the real cost drivers rather than guessing.

## Step 2 — Apply cost-efficiency levers

The typical high-impact changes, cheapest wins first:

**Route to a cheaper default model** (biggest single lever):
```
divyam service-account update sa_demo_123 --org 1 --default-model claude-haiku
```

**Enable prompt/response caching** (cuts repeated-context cost):
```
divyam service-account update sa_demo_123 --org 1 --enable-caching true
```

**Set a hard monthly spend cap** (prevents runaway cost):
```
divyam service-account update sa_demo_123 --org 1 --monthly-budget-usd 500
```

**Cap max output tokens per request** (trims over-long generations):
```
divyam service-account update sa_demo_123 --org 1 --max-output-tokens 2048
```

**Add a rate limit** (smooths spikes, protects the budget):
```
divyam service-account update sa_demo_123 --org 1 --rate-limit 60/min
```

## Step 3 — Verify

```
divyam service-account get-config sa_demo_123 --org 1
```

Re-check usage after a day or two:
```
divyam service-account show-usage sa_demo_123 --org 1 --period 1d
```

---

## User-facing message

"Cost efficiency" for a service account usually comes down to a handful of levers, so before changing anything I'd inspect `acme-prod` (sa_demo_123) to see where the money is actually going:

- `divyam service-account get sa_demo_123 --org 1`
- `divyam service-account show-usage sa_demo_123 --org 1 --period 30d`

Then the highest-impact, safe changes are typically:

1. **Cheaper default model** — routing everyday traffic to a smaller model (e.g. Haiku) is almost always the single biggest saving.
2. **Caching on** — avoids paying repeatedly for the same context.
3. **A monthly budget cap** — a hard ceiling so cost can't run away.
4. **Max-output-token and rate limits** — trim over-long responses and smooth spikes.

Two things to flag:
- I don't have the exact `divyam` command/flag reference loaded, so the flag names above are inferred — please confirm with `divyam --help` (or the relevant subcommand help) before running.
- `acme-prod` is a **production** account. Changing the default model can affect output quality, and a budget cap can start rejecting requests once hit. I'd suggest verifying the current usage first, then applying changes one at a time and watching the results, rather than all at once.

Want me to tailor this to a specific target (e.g. "cut spend 30%" vs "just add a safety cap"), or to your actual traffic mix once we've looked at the usage numbers?
