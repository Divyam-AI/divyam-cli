#!/usr/bin/env python3
"""Rebalance a Divyam service-account traffic_allocation_config.

The traffic_allocation_config is a free-form map of {bucket_name: percent} that must
sum to 100. Exactly one bucket is "control" (a hold-out that should never starve), the
rest carry selector / selector-disabled traffic. When the user says "allocate N% to the
selector", we pin that bucket to N and re-spread the remaining (100 - N) across the other
buckets in proportion to their current weights, while keeping control at or above a small
floor so the hold-out never disappears.

Usage:
  rebalance_traffic.py --current '<json>' --bucket <name> --percent <N> \
      [--control-key control] [--control-floor 2]

Prints the new config as JSON on stdout. Exits non-zero (message on stderr) when the
request can't satisfy the constraints, so the caller never sends an invalid config.

  --selftest   run built-in checks instead of reading args
"""
from __future__ import annotations

import argparse
import json
import sys


def rebalance(
    current: dict,
    bucket: str,
    percent: float,
    control_key: str = "control",
    control_floor: float = 2.0,
) -> dict:
    cfg = {k: float(v) for k, v in current.items()}
    if not (0.0 <= percent <= 100.0):
        raise ValueError(f"--percent must be between 0 and 100, got {percent}")
    if control_floor < 0 or control_floor > 100:
        raise ValueError(f"--control-floor out of range: {control_floor}")

    new = dict(cfg)
    new[bucket] = float(percent)
    remaining = 100.0 - float(percent)
    if remaining < -1e-9:
        raise ValueError("target percent leaves nothing for the other buckets")

    others = [k for k in new if k != bucket]
    if control_key not in new:
        new[control_key] = 0.0
        others.append(control_key)

    # Spread the remaining budget across the non-target buckets by their prior weight.
    weights = {k: cfg.get(k, 0.0) for k in others}
    total_w = sum(weights.values())
    if total_w <= 1e-9:
        share = remaining / len(others) if others else 0.0
        for k in others:
            new[k] = share
    else:
        for k in others:
            new[k] = remaining * weights[k] / total_w

    # Never let the control hold-out fall below the floor (unless the target itself ate
    # the whole budget, e.g. 100% -> then control can't be honored and we say so).
    if others and new.get(control_key, 0.0) < control_floor - 1e-9:
        deficit = control_floor - new[control_key]
        donors = [k for k in others if k != control_key]
        donor_total = sum(new[k] for k in donors)
        if donor_total + 1e-9 < deficit:
            raise ValueError(
                f"cannot keep {control_key} >= {control_floor}% while giving "
                f"{bucket} {percent}%: not enough room in the other buckets"
            )
        new[control_key] = control_floor
        if donor_total > 0:
            for k in donors:
                new[k] -= deficit * new[k] / donor_total

    # Clean up float noise and force the total to exactly 100.
    new = {k: round(v, 4) for k, v in new.items()}
    drift = round(100.0 - sum(new.values()), 4)
    if abs(drift) > 1e-9:
        # Absorb the rounding drift into the largest bucket that isn't the pinned target.
        absorb = max(
            (k for k in new if k != bucket),
            key=lambda k: new[k],
            default=bucket,
        )
        new[absorb] = round(new[absorb] + drift, 4)

    for k, v in new.items():
        if v < -1e-9:
            raise ValueError(f"bucket {k} went negative ({v}); request is infeasible")
    return new


def _selftest() -> int:
    checks = []

    def check(name, cond):
        checks.append((name, cond))

    r = rebalance({"control": 10, "selector_disabled": 90}, "my-selector", 20)
    check("sums to 100", abs(sum(r.values()) - 100) < 1e-6)
    check("target pinned", abs(r["my-selector"] - 20) < 1e-6)
    check("control floored", r["control"] >= 2 - 1e-9)

    r = rebalance({"control": 1, "selector_disabled": 99}, "sel", 50, control_floor=2)
    check("control raised to floor", abs(r["control"] - 2) < 1e-6)
    check("still sums to 100", abs(sum(r.values()) - 100) < 1e-6)

    try:
        rebalance({"control": 10, "sel": 90}, "sel", 100, control_floor=2)
        check("100% target rejected", False)
    except ValueError:
        check("100% target rejected", True)

    ok = all(c for _, c in checks)
    for name, cond in checks:
        print(f"[{'ok' if cond else 'FAIL'}] {name}", file=sys.stderr)
    return 0 if ok else 1


def main() -> int:
    if "--selftest" in sys.argv:
        return _selftest()
    p = argparse.ArgumentParser(description="Rebalance Divyam traffic_allocation_config")
    p.add_argument("--current", required=True, help="current config as a JSON object")
    p.add_argument("--bucket", required=True, help="bucket to pin (the selector's traffic)")
    p.add_argument("--percent", required=True, type=float, help="percent for --bucket")
    p.add_argument("--control-key", default="control", help="name of the hold-out bucket")
    p.add_argument("--control-floor", default=2.0, type=float, help="min %% for control")
    args = p.parse_args()
    try:
        current = json.loads(args.current)
        if not isinstance(current, dict):
            raise ValueError("--current must be a JSON object")
        result = rebalance(
            current, args.bucket, args.percent, args.control_key, args.control_floor
        )
    except (ValueError, json.JSONDecodeError) as e:
        print(f"error: {e}", file=sys.stderr)
        return 1
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
