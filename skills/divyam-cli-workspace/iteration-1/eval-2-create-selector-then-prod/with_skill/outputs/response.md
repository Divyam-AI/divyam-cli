# Response — "create a selector called checkout-router using the default extractor over the last 30 days, then move it to prod"

## (a) Commands

**Step 1 — First skill use of session: confirm the active config (run once).**
```
divyam config current
```
Reports: Active service account `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai`.

**Step 2 — Create the selector (a create command → run directly).**
The user said "the default extractor," so pass it as the extractor strategy. "Over the last 30 days" is exactly the CLI's default training window when no timestamps are supplied, so I deliberately omit `--start-timestamp`/`--end-timestamp` (they must be given as a pair, and leaving them off yields the last 30 days of router logs).
```
divyam selector create --name checkout-router -x default
```
(Assume this returns selector id `42`.)

**Step 3 — Move it to prod (RISKY: promotion → show, confirm, then run).**
Not run yet — presented for explicit confirmation:
```
divyam selector update --id 42 --to-prod
```

---

## (b) User-facing message

Active service account: `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai`.

Created the selector. It starts in a non-prod (training) state:

```
divyam selector create --name checkout-router -x default
```

- Name: `checkout-router`
- Extractor strategy: `default`
- Training window: last 30 days — I left the timestamps off, which is exactly what gives you the default 30-day router-logs window (id `42`).

Training the selector now. Watch progress here (filter to service account `acme-prod` / `sa_demo_123`):
https://dashboard.divyam.ai/superset/dashboard/training-dashboard/

Next you asked to move it to prod. Promotion is a production change, so I want your OK before running it. This command will flip `checkout-router` (id 42) to the PROD state, putting it in the live routing path:

```
divyam selector update --id 42 --to-prod
```

Reply "yes" and I'll run it. Once it's promoted I'll give you the A/B Traffic dashboard link to watch it live. Note it usually makes sense to let training finish before promoting — say the word if you'd rather wait.
