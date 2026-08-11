# Rotating the API key for service account "acme-prod"

Rotating a key is a two-step operation: first mint a **new** key, then revoke the **old** one only after you've confirmed the new key is deployed and working. Doing it in this order avoids an outage window where the service account has no valid credential.

## (a) Commands to run

### Step 1 — Create a new key for the service account

```
divyam sa key create --sa sa_demo_123 --org 1 --endpoint https://api.divyam.ai
```

Expected output (the secret is shown **once** — copy it immediately):

```
divyam-v1-<hex>
```

### Step 2 — Deploy the new key, verify traffic works, THEN revoke the old key

Only after the new key is in place and confirmed working:

```
divyam sa key revoke --sa sa_demo_123 --key-id key_old_1 --org 1 --endpoint https://api.divyam.ai
```

> Tip: if you're not sure which key IDs currently exist, list them first with:
>
> ```
> divyam sa key list --sa sa_demo_123 --org 1
> ```

## (b) User-facing message

Here's how I'd rotate the API key for **acme-prod** (`sa_demo_123`). It's a two-step process, and the order matters so you don't get a gap where the account has no working key.

**1. Create the new key:**

```
divyam sa key create --sa sa_demo_123 --org 1 --endpoint https://api.divyam.ai
```

This prints the new secret (format `divyam-v1-<hex>`) **exactly once** — copy it right away and store it in your secrets manager. It won't be shown again.

**2. Update wherever the old key is used** (env vars, CI/CD secrets, deployed services) with the new value, and confirm traffic is authenticating successfully.

**3. Only once the new key is confirmed working, revoke the old one** (`key_old_1`):

```
divyam sa key revoke --sa sa_demo_123 --key-id key_old_1 --org 1 --endpoint https://api.divyam.ai
```

A couple of important notes:

- **I have not run either command** — the two steps are separated by your deploy/verify step, so revoking is on you to trigger once you've cut over. Revoking `key_old_1` is immediate and irreversible; anything still using it will start getting auth failures.
- I assumed the key currently in use is `key_old_1`. If you have multiple keys on this service account, run `divyam sa key list --sa sa_demo_123 --org 1` first to confirm which one to revoke.
- All commands target your active config: org `1`, endpoint `https://api.divyam.ai`.

Want me to run step 1 now?
