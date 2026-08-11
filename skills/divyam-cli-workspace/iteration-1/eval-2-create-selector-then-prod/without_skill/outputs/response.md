# Response

## Commands to run

Create the selector `checkout-router` using the default extractor over the last 30 days:

```bash
divyam selector create \
  --name checkout-router \
  --extractor default \
  --lookback 30d
```

Then, assuming the created selector is assigned id `42`, promote it to prod:

```bash
divyam selector promote 42 --env prod
```

## User-facing message

Here are the two commands to create the selector and then promote it to prod.

First, create `checkout-router` with the default extractor over a 30-day lookback window:

```bash
divyam selector create --name checkout-router --extractor default --lookback 30d
```

Once that returns, it will print the new selector's id. Then move it to prod (using id `42` from the create step):

```bash
divyam selector promote 42 --env prod
```

Notes:
- These will run against your active config: service account `acme-prod` (`sa_demo_123`), org `1`, endpoint `https://api.divyam.ai`.
- Promoting to prod is typically a one-way/production-affecting action, so double-check the selector looks right before running the second command.

## Caveats (baseline / no skill reference)

I don't have the authoritative divyam CLI reference loaded, so the exact subcommand names and flags above (`selector create`, `--extractor`, `--lookback`, `selector promote`, `--env prod`) are my best-guess reconstruction of the likely interface and may not match the real CLI. Before running, verify against:

```bash
divyam selector --help
divyam selector create --help
divyam selector promote --help
```

In particular, confirm:
- Whether "default extractor" is expressed as `--extractor default` vs. omitting the flag entirely.
- Whether the 30-day window is `--lookback 30d`, `--days 30`, or an explicit `--from/--to` date range.
- Whether "move to prod" is a `promote` subcommand, an `update --env prod`, or a separate deploy/publish command, and whether it takes the selector id or name.
