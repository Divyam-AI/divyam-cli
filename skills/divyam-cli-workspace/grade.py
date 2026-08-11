#!/usr/bin/env python3
"""Grade divyam-cli eval runs by pattern-checking each response.md.

Objective checks only (flag correctness, traffic math, confirmation/hand-off language).
Writes grading.json into each run dir with the schema the eval viewer expects:
  {"expectations": [{"text": ..., "passed": bool, "evidence": ...}]}
"""
import json
import re
import sys
from pathlib import Path

ITER = Path(sys.argv[1]) if len(sys.argv) > 1 else Path(__file__).parent / "iteration-1"


def has(text, pat, flags=re.I):
    return re.search(pat, text, flags) is not None


def grade(eval_dir_name, text):
    """Return list of (assertion_text, passed, evidence) for a given eval."""
    t = text
    checks = []

    def add(desc, cond, evidence=""):
        checks.append((desc, bool(cond), evidence))

    if eval_dir_name.startswith("eval-0"):
        add("model-info create with provider google + --api-type GEMINI",
            has(t, r"model-info\s+create") and has(t, r"--api-type\s+GEMINI") and has(t, r"--provider-name\s+google"))
        add("model name gemini-2.5-flash", has(t, r"gemini-2\.5-flash"))
        add("--provider-api-key is a bare flag (no key value after it)",
            has(t, r"--provider-api-key(?:\s*$|\s*\n|\s*#|\s*\\)") or has(t, r"--provider-api-key\b(?!\s+\S)"))
        add("does not echo the AIza... key value",
            not has(t, r"AIzaSy[A-Za-z0-9]", flags=0))
        add("hands off / does not auto-run the create",
            has(t, r"run (this|it) (in|from) your (terminal|shell)|hand(ing|ed)? off|prompt(s)? (you|for)"))

    elif eval_dir_name.startswith("eval-1"):
        add("provider switched to bedrock", has(t, r"--provider-name\s+bedrock"))
        add("does not reuse GEMINI api-type", not has(t, r"--api-type\s+GEMINI"))
        add("includes --input-price 3", has(t, r"--input-price\s+3\b"))
        add("includes --output-price 15", has(t, r"--output-price\s+15\b"))
        add("references the prior/same command shape",
            has(t, r"same|previous|earlier|reuse|like (the|before)"))

    elif eval_dir_name.startswith("eval-2"):
        add("selector create --name checkout-router",
            has(t, r"selector\s+create") and has(t, r"checkout-router"))
        add("includes an extractor strategy (-x / --extractor)",
            has(t, r"(-x|--extractor(-strategy)?)\b"))
        add("emits Selector Training dashboard link",
            has(t, r"training-dashboard"))
        add("asks for confirmation before --to-prod",
            has(t, r"--to-prod") and has(t, r"confirm|before (i|we) (run|promote)|okay to|proceed\?|shall i"))

    elif eval_dir_name.startswith("eval-3"):
        # Extract the applied allocation JSON if present.
        add("uses sa update --traffic-allocation-config",
            has(t, r"sa\s+update") and has(t, r"--traffic-allocation-config"))
        add("checkout-router set to 20", has(t, r'checkout-router"?\s*[:=]\s*20'))
        add("control kept >= ~2 (8 in the standard rebalance)",
            has(t, r'control"?\s*[:=]\s*(8|[2-9](\.\d+)?)\b'))
        # sum-to-100 check on any {...} map found
        sum_ok = False
        for m in re.finditer(r"\{[^{}]*\}", t):
            nums = re.findall(r':\s*([0-9]+(?:\.[0-9]+)?)', m.group(0))
            if len(nums) >= 2 and abs(sum(float(n) for n in nums) - 100) < 0.5:
                sum_ok = True
                break
        add("an allocation map sums to 100", sum_ok)
        add("shows before->after and asks confirmation",
            has(t, r"before|current|->|→") and
            has(t, r"confirm|proceed|okay to|shall i|before (i|we|apply)|explicit yes|your yes|reply[^\n]{0,8}yes|say yes"))

    elif eval_dir_name.startswith("eval-4"):
        add("sa update --optimization-goal COST_EFFICIENT",
            has(t, r"sa\s+update") and has(t, r"--optimization-goal\s+COST_EFFICIENT"))
        # Correct behavior applies the SA optimization goal and distinguishes it from the
        # per-selector lambda knob (mentioning the lambda as a clarifying alternative is good).
        add("distinguishes SA goal from selector lambda",
            has(t, r"--optimization-goal\s+COST_EFFICIENT") and
            has(t, r"not a selector lambda|different knob|if you (actually )?meant|per-selector|account-level|whole service account|alternative"))

    elif eval_dir_name.startswith("eval-5"):
        add("sa key create for the new key", has(t, r"sa\s+key\s+create"))
        add("warns key is shown once / unrecoverable",
            has(t, r"once|unrecoverab|can'?t be retriev|save (it|this) now|stored nowhere"))
        add("offers config set with --api-token",
            has(t, r"config\s+set") and has(t, r"--api-token"))
        add("revokes old key as a follow-up (sa key revoke)",
            has(t, r"sa\s+key\s+revoke"))

    return checks


def main():
    runs = sorted(ITER.glob("eval-*/*/outputs/response.md"))
    if not runs:
        print(f"no response.md under {ITER}", file=sys.stderr)
        return 1
    summary = {}
    for resp in runs:
        eval_dir = resp.parents[2].name  # eval-N-name
        config = resp.parents[1].name    # with_skill / without_skill
        text = resp.read_text()
        checks = grade(eval_dir, text)
        expectations = [
            {"text": d, "passed": p, "evidence": (ev or ("match" if p else "no match"))}
            for d, p, ev in checks
        ]
        out = resp.parent.parent / "grading.json"
        out.write_text(json.dumps({"expectations": expectations}, indent=2))
        passed = sum(1 for e in expectations if e["passed"])
        summary.setdefault(eval_dir, {})[config] = f"{passed}/{len(expectations)}"
    print(json.dumps(summary, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
