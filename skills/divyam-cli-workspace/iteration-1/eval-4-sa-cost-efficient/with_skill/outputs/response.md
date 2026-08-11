# Response: "make this service account cost efficient"

## (a) Verbatim divyam command(s)

Session-start check (run once — confirm the active service account):

```
divyam config current
```

Then set the cost-efficient optimization goal on the active service account:

```
divyam sa update --optimization-goal COST_EFFICIENT
```

Notes on the command choices:
- "Make this service account cost efficient" is the **service-account optimization goal**, not a selector lambda. Per the skill, that is `sa update --optimization-goal COST_EFFICIENT` (the `HIGH_QUALITY` value is the quality-first / default profile).
- `--sa-id` / `--org-id` are omitted — they resolve from the active config (`acme-prod` / `sa_demo_123`, org `1`, `https://api.divyam.ai`).
- This is a config knob, not one of the three risky changes (promotion, traffic reallocation, delete/retire/revoke), so it runs directly — no confirmation gate required.

## (b) User-facing message

Active service account: `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai`.

I set this service account's optimization goal to cost-efficient:

```
divyam sa update --optimization-goal COST_EFFICIENT
```

That flips the account-level profile to `COST_EFFICIENT`, so routing decisions for `acme-prod` now favor lower-cost models where quality allows (the previous default is `HIGH_QUALITY`). This applies to the whole service account, not to any single selector.

If what you actually meant was tuning a specific selector's own cost-vs-quality tradeoff, that is a different knob (`selector update --lambda …` / the `--high-quality-lambda` + `--cost-savings-lambda` pair) — let me know and I'll set that instead. Want me to revert to `HIGH_QUALITY` at any point? Just say so.
