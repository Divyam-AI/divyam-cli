# Rotate the API key for this service account

Rotation is a zero-downtime, overlap-based flow: **create a replacement key → move your callers to it → revoke the old one.** An account must always keep one active key, so the old key is only revoked at the end, and only after you confirm — I won't run the revoke on my own.

## Step 0 — Active service account (session check)

```
divyam config current
```

Active service account: `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai` (org `1`). That's the account this rotation targets.

## Step 1 — Create the replacement key (I run this now)

This is a safe create; it prints a one-time plaintext key.

```
divyam sa key create --name rotation-key
```

Example result:

```
{
  "key" : {
    "id" : "key_new_2",
    "service_account_id" : "sa_demo_123",
    "name" : "rotation-key",
    "created_at" : 1785923617
  },
  "api_key" : "divyam-v1-4f9c...<64 hex>"
}
```

**New API key — save this now, it's shown once and can't be retrieved later:**

```
divyam-v1-4f9c...<64 hex>
```

I have not written this key to any file, log, or the scratchpad. Want me to store it in the `acme-prod` config profile so it's persisted and usable? On your yes I'll hand you (or run, your call) an interactive `config set` that prompts for the key with no echo — it never touches argv or shell history:

```
divyam config set -c acme-prod -e https://api.divyam.ai -o 1 -s sa_demo_123 --api-token
divyam config use -c acme-prod
```

## Step 2 — Repoint your callers to the new key

Update whatever holds the current key (app secrets/env, `DIVYAM_*` config, etc.) to `divyam-v1-4f9c...`, and confirm traffic succeeds on it. Both keys authenticate during this overlap, so there's no gap.

## Step 3 — Revoke the old key (RISKY — needs your explicit yes)

Once you've confirmed the new key is live, I'll retire the old one. To be sure I revoke the right id (revoke is by id only, names aren't unique), I'll first list keys:

```
divyam sa key ls --format text
```

Then, after you confirm:

```
divyam sa key revoke --key-id key_old_1
```

What this does: permanently retires key id `key_old_1`. Any caller still using the old key stops authenticating within ~300s (`api_keys_cache_ttr`) across router replicas — so only revoke after Step 2 is verified. I will wait for your explicit "yes" before running this.

---

**Commands summary**

- Run now: `divyam sa key create --name rotation-key`
- After your confirmation: `divyam sa key revoke --key-id key_old_1`
- Optional (persist the key, interactive/no-echo): `divyam config set -c acme-prod -e https://api.divyam.ai -o 1 -s sa_demo_123 --api-token` then `divyam config use -c acme-prod`

Reply **yes** once the new key is in use and I'll revoke `key_old_1`.
