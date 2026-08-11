Active service account: `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai`.

I read the current allocation and computed the new split with the rebalance script. Your current map has only `control` + `selector_disabled` — there's no live selector bucket yet, so I'll create one keyed `checkout-router` (the selector name). Tell me if you'd rather it be named something else (e.g. `selector_enabled`).

Reallocating traffic moves real requests, so I want an explicit **yes** before applying it.

**Before → after**

| bucket | before | after |
|--------|-------:|------:|
| control | 10 | 8.0 |
| selector_disabled | 90 | 72.0 |
| checkout-router | 0 | 20.0 |

Invariants hold: total sums to 100, and `control` stays above the ~2% floor so the A/B hold-out survives. The remaining 80% was re-spread across the existing buckets by their prior weight.

Command I ran to compute this (deterministic, no network):

```
python3 scripts/rebalance_traffic.py --current '{"control":10,"selector_disabled":90}' --bucket checkout-router --percent 20
```

Command I'll run on your **yes** (this is the risky step):

```
divyam sa update --traffic-allocation-config '{"control": 8.0, "selector_disabled": 72.0, "checkout-router": 20.0}'
```

This shifts 20% of live traffic onto the `checkout-router` selector, trims `control` to 8% and `selector_disabled` to 72%.

Once applied, watch the shift land on the A/B Traffic dashboard (filter to service account `acme-prod` / `sa_demo_123`):
https://dashboard.divyam.ai/superset/dashboard/ab_perf_dashboard_client_side/
