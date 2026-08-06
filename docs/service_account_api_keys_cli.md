# Service account API keys

A service account can hold several named API keys. That is what makes rotation possible without
downtime: create a second key, move your traffic to it, then revoke the first. Both work throughout.

Every account starts with one key, named `default_key` - the key whose plaintext `sa create` returns.

## Commands

```
divyam sa key ls      [-s|--sa-id ID]
divyam sa key create  [-s|--sa-id ID] --name NAME
divyam sa key revoke  [-s|--sa-id ID] --key-id KEY_ID
```

`--sa-id` falls back to the `DIVYAM_SA_ID` environment variable, then to the current config, like
every other service account command.

A service account can manage its own keys with nothing but its own token, which is how a customer
rotates a key without an administrator:

```
divyam sa key create --sa-id <own id> --name replacement -t <own api key>
```

Any other account's keys are refused with `403`.

## Rotating a key

```
$ divyam sa key create --sa-id <service account id> --name rotation-key
{
  "key" : {
    "id" : "<new key id>",
    "service_account_id" : "<service account id>",
    "name" : "rotation-key",
    "created_at" : 1785923617
  },
  "api_key" : "divyam-v1-<64 hex characters>"
}
```

**`api_key` is shown once and stored nowhere.** Save it before moving on - there is no command that
can retrieve it later. Point your traffic at it, confirm it works, then retire the old key:

```
$ divyam sa key revoke --sa-id <service account id> --key-id <old key id>
Revoked API key <old key id>
```

A revoked key stops authenticating within `api_keys_cache_ttr` (300s by default) on each router
replica, so allow for that before assuming the old key is dead.

## Listing

`sa key ls` shows every key, revoked ones included; `revoked_at` is what tells them apart. Add
`--format text` for a table:

```
┌────────────────┬────────────────┬────────────┬──────────┬──────────┐
│Id              │Service  Account│Name        │Created At│Revoked At│
│                │Id              │            │          │          │
├────────────────┼────────────────┼────────────┼──────────┼──────────┤
│aaaaaaaaaaaaaaaa│ssssssssssssssss│rotation-key│2026-08-05│          │
│                │                │            │15:23:37  │          │
│bbbbbbbbbbbbbbbb│ssssssssssssssss│default_key │2026-08-05│2026-08-05│
│                │                │            │15:23:26  │15:24:40  │
└────────────────┴────────────────┴────────────┴──────────┴──────────┘
```

The `Id` column is what `revoke --key-id` takes. Names are not unique, so revoke is by id only.

## Errors

| Message | Cause |
| --- | --- |
| `Can not revoke the only active API key for this service account` | Create the replacement first; an account must keep one working key |
| `Service account already has N active API keys` | At the configured cap. Revoke a key you no longer need |
| `Service account API key not found` | Unknown key id, or a key belonging to another account |
| `Can not access requested service_account_id` | Your token is for a different account |
| `Key name must not be blank` | `--name` is required and must not be only whitespace |

Revoking an already-revoked key succeeds rather than failing, so a retried rotation script does not
break on its second run.

## `sa update --regenerate-api-key` is gone

It rotated the key in place, which broke every caller still holding the old one. The replacement is
`sa key create` followed by `sa key revoke`, which is the same operation with an overlap during which
both keys work. The router refuses the old flag.
