# Allocate 20% traffic to the checkout-router selector

## Allocation math

There are three arms in play:

- `control`
- `selector` (the checkout-router selector itself — the arm you want to send traffic to)
- `selector_disabled`

Current allocation:

| Arm | Before |
|---|---|
| control | 10 |
| selector | 0 |
| selector_disabled | 90 |
| **Total** | **100** |

You want the `selector` arm at **20%**. That leaves **80%** to split across the other two arms. Keeping their existing 10:90 (1:9) ratio, the rebalance is:

- `control` = 80 × (10/100) = **8**
- `selector_disabled` = 80 × (90/100) = **72**

| Arm | Before | After |
|---|---|---|
| control | 10 | 8 |
| selector | 0 | 20 |
| selector_disabled | 90 | 72 |
| **Total** | **100** | **100** |

If instead you'd rather pull the full 20 points out of `selector_disabled` only (leaving `control` untouched), the after would be control 10 / selector 20 / selector_disabled 70. Let me know which you prefer — the command below uses the proportional rebalance.

## Command(s)

> Note: I don't have the exact Divyam CLI schema loaded, so please treat the flag names below as a best guess and verify against `divyam selector allocate --help` before running. The arm weights and totals are correct regardless of the exact flag syntax.

```bash
divyam selector allocate \
  --selector checkout-router \
  --org 1 \
  --allocation control=8,selector=20,selector_disabled=72
```

To confirm the change afterward:

```bash
divyam selector show --selector checkout-router --org 1
```

Config in effect: service account `acme-prod` (`sa_demo_123`), org `1`, endpoint `https://api.divyam.ai`.

## User-facing message

I'll move the `checkout-router` selector arm to 20% traffic. Since the selector arm was at 0%, the 20 points come out of the other two arms, keeping their current 10:90 split:

- **control:** 10 -> 8
- **selector:** 0 -> 20
- **selector_disabled:** 90 -> 72

(Total stays at 100.)

Command I'd run:

```bash
divyam selector allocate --selector checkout-router --org 1 --allocation control=8,selector=20,selector_disabled=72
```

Two things to flag: (1) I'm not 100% certain of the exact CLI flag names here, so I'd verify against `divyam selector allocate --help` first. (2) If you'd rather leave `control` at 10 and take all 20 points from `selector_disabled` (giving 10/20/70), tell me and I'll adjust.
