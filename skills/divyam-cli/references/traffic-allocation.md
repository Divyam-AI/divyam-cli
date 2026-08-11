# Traffic allocation

A service account's `traffic_allocation_config` decides how live requests are split. It's a
free-form JSON map of `{bucket_name: percent}` that **must sum to 100**. The default a fresh
account carries is:

```json
{"control": 10.0, "selector_disabled": 90.0}
```

Meaning of the common buckets:
- **`control`** — a hold-out group used as the A/B baseline. Keep it alive: never let it drop
  below ~1–2%, or you lose the comparison the A/B dashboard is built on.
- **`selector_disabled`** — traffic that runs without the selector (default routing).
- **the selector's own bucket** — live traffic the selector actually decides. Because the map
  is free-form, the key name varies by setup (it may be the selector name, `selector_enabled`,
  or similar). **Don't assume the key** — read the current config first.

## "Allocate N% traffic to the selector" — the flow

This is a risky change (it moves real traffic), so it always ends in a confirmation.

1. **Read** the current allocation:
   ```
   divyam sa get --id <sa>        # or `divyam config current` to learn the active SA first
   ```
   Pull `traffic_allocation_config` out of the output.

2. **Identify the selector bucket.** If the map already has a clear selector bucket, use it. If
   it only has `control` + `selector_disabled` (no live selector bucket yet), tell the user the
   key you intend to create (e.g. the selector's name) and confirm it — don't guess silently.

3. **Compute** the new map with the bundled script (deterministic, no network):
   ```
   python3 <skill>/scripts/rebalance_traffic.py \
     --current '<current json>' --bucket '<selector bucket>' --percent <N>
   ```
   It pins the selector bucket to N%, re-spreads the remaining (100 − N) across the other
   buckets by their prior weight, keeps `control` ≥ the floor (default 2%), and normalizes the
   total to exactly 100. It exits non-zero if the request is infeasible (e.g. N=100 leaves no
   room for control) — surface that error instead of sending a bad config.

   Use `--control-key <name>` if the hold-out isn't called `control`, and `--control-floor <p>`
   to change the floor.

4. **Show before → after** as a small diff and state the invariants ("sums to 100, control
   stays at 2%"). Get an explicit **yes**.

5. **Apply**:
   ```
   divyam sa update --id <sa> --traffic-allocation-config '<new json>'
   ```

6. Offer the **A/B Traffic** dashboard link (see `references/dashboards.md`) so the user can
   watch the shift land.

## Worked example

Current: `{"control": 10, "selector_disabled": 90}`, user says "allocate 20% to checkout-router":

```
$ rebalance_traffic.py --current '{"control":10,"selector_disabled":90}' \
    --bucket checkout-router --percent 20
{"control": 8.0, "selector_disabled": 72.0, "checkout-router": 20.0}
```

control 10→8, selector_disabled 90→72, checkout-router 0→20; total 100; control ≥ 2. Confirm,
then `sa update --traffic-allocation-config '{"control":8.0,"selector_disabled":72.0,"checkout-router":20.0}'`.
