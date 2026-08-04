# Service account API keys in the CLI

Design for CLI support of named, individually revocable service account API keys, added by
divyam_router_controller [#307](https://github.com/Divyam-AI/divyam_router_controller/pull/307)
and specified in [#306](https://github.com/Divyam-AI/divyam_router_controller/pull/306).

A customer can now create a second API key, move their traffic to it, and revoke the old one with
no downtime. That is only true if the CLI can drive it, which is what this covers.

## The contract

Three routes, public in OpenAPI.

| Route | Generated method | Returns |
| --- | --- | --- |
| `GET /v1/service_accounts/{id}/api_keys/` | `listServiceAccountApiKeys` | `List<ServiceAccountApiKeyRecord>`, active and revoked |
| `POST /v1/service_accounts/{id}/api_keys/` | `createServiceAccountApiKey` | `IssuedServiceAccountApiKey` |
| `POST /v1/service_accounts/{id}/api_keys/{key_id}/revoke/` | `revokeServiceAccountApiKey` | `kotlin.Any` — the route declares no `response_model`, so the body is `{"detail": …}` |

Shapes, all server-defined:

```
ServiceAccountApiKeyRecord         { id, service_account_id, name, created_at, revoked_at? }
IssuedServiceAccountApiKey         { key: ServiceAccountApiKeyRecord, api_key: <plaintext, returned once> }
ServiceAccountApiKeyCreateRequest  { name }
```

Failure modes, none of which the CLI represents today:

| Status | When |
| --- | --- |
| 409 | account is at the active-key cap (server config, default 10) |
| 400 | revoking the only active key |
| 404 | unknown key id, or a key belonging to another account |
| 403 | caller is not the account, an org admin, or an admin |
| 422 | blank or over-long key name |

Authorization allows a service account to manage its own keys, so every command below works with
either an admin/org-admin login or the account's own token via `-t`.

## Commands

```
divyam sa key ls      [-s|--sa-id ID]
divyam sa key create  [-s|--sa-id ID] --name NAME
divyam sa key revoke  [-s|--sa-id ID] --key-id KEY_ID
```

`key` is a nested subcommand group under `sa`. The three leaves extend `SaSpecificCommand`, so
`--sa-id` follows the usual resolution order: flag, then `DIVYAM_SA_ID`, then the current config.

### ls

`printObjs(keys)`.

Revoked keys stay in the listing; the `Revoked At` column is the state, so there is no
`--include-revoked` flag. Every field the API returns is shown, in both text and structured output.

### create

The response is printed as it came and `api_key` is a field like any other. That is what `sa create`
already does with the key it hands back, and the plaintext key here has the same one-time,
stored-nowhere property, so it gets the same treatment rather than a special callout. The warning to
save it lives where `sa create`'s already does - `Setup-Your-Account.md` in the wiki - which needs a
`sa key` section adding.

`IssuedServiceAccountApiKey` nests the record under `key`, which the table printer would otherwise
render as a single `toString()` column with a raw epoch in it, so text output passes
`flattenKeys = setOf("key")`. See the table printer notes below.

A blank `--name` is refused locally rather than sent, since the server's 422 for it arrives in an
envelope that reads poorly (see below).

### revoke

Prints `Revoked API key <id>` and exits 0, following `ModelSelectorDeleteCommand`'s precedent of
confirming with the id rather than echoing the server's `detail`. Revoking an already-revoked key is
a 200 on the server and succeeds here too.

## Changes to existing commands

**`SaUpdateCommand`** — `--regenerate-api-key` is removed, and `regenerateApiKey` is no longer
passed to `ServiceAccountUpdateRequest`. The generated property is `kotlin.Boolean? = null` and the
client mapper drops nulls, so the field disappears from the request body entirely. That is the
precondition for deleting the server-side field in a later release; until then the controller
refuses `regenerate_api_key: true` with a 400 naming the replacement routes, which now applies only
to direct HTTP callers.

**`sa get` / `ls` / `create` / `update`** — no code change. Their output loses the
`Divyam Auth Key Hashed` column once the spec is regenerated.

## Error surfacing

The generated methods raise `Error: <status> - <raw body>` for anything that is not 200 or 201, and
these commands do the same as every other command in the CLI. A refusal reads:

```
Error: 400 - {"detail":"Can not revoke the only active API key for this service account"}
```

The reason is in there and the exit code is 1, so nothing is lost. An earlier draft unwrapped
`detail` into a bare message for these three commands; it was dropped as cosmetic, and because
having three commands report errors differently from the other thirty is worse than the noise it
removed. The fix worth making is in
`client/openapi-templates/ktor/libraries/jvm-ktor/api.mustache`, which would improve every command
at once - separate work, deliberately not bundled here.

A blank `--name` is still refused locally rather than sent, because the server's 422 for it arrives
as a stringified Python error list inside `{"error": {"message": …}}`, which is the one case where
the raw form genuinely obscures the reason.

## `flattenKeys` on the table printer

`printObjs` gains a fourth opt-in key set beside `skipKeys`, `includeKeys` and `redactKeys`, threaded
through `Printing.printObjs` and `BaseCommand.printObjs`. A field named in it contributes one column
per field of its own type, instead of one column holding that object's `toString()`. `--format text`
only, because it is a table concern: json and yaml keep the shape the API returned.

`sa key create` passes `setOf("key")`, without which the record renders as
`ServiceAccountApiKeyRecord(id=…, createdAt=1785912967, …)` in a single column. Expanding also means
nested timestamps get the same date formatting as top-level ones, which the `toString()` does not.

Opt-in rather than automatic, because flattening only improves small nested types. `ServiceAccount`
would go from 12 columns to 24 if its two policies were expanded - `RetryFallbackPolicy` alone is 10
fields - leaving every column too narrow to read at 100 characters. The printer cannot tell the two
cases apart, so the caller says.

Adding it meant reworking how the printer builds a table, from an array of reflected fields to a list
of columns, because a column no longer maps one-to-one onto a declared field. That affects every
command that prints a table, so `sa get` (nested `securityPolicy` and `retryFallbackPolicy`, left
unflattened) and `org ls` were checked by hand alongside the existing printer tests.

One thing found and left alone: `skipKeys` is matched literally, against Kotlin property names on the
text path and against `@JsonProperty` keys on json and yaml, so one spelling can only ever reach one
format. `OutputRedactor.matchesKey` already solves this for `redactKeys` and `skipKeys` never adopted
it. No command here passes `skipKeys`, so fixing it belongs in its own change.

## Files

New:

| File | Purpose |
| --- | --- |
| `cli/src/main/kotlin/ai/divyam/cli/sa/SaKeyCommand.kt` | `key` group, extends `BaseSubCommand` |
| `cli/src/main/kotlin/ai/divyam/cli/sa/SaKeyListCommand.kt` | `ls` |
| `cli/src/main/kotlin/ai/divyam/cli/sa/SaKeyCreateCommand.kt` | `create` |
| `cli/src/main/kotlin/ai/divyam/cli/sa/SaKeyRevokeCommand.kt` | `revoke` |

Modified:

| File | Change |
| --- | --- |
| `client/specs/openapi.json` | regenerated: three paths, three schemas, `divyam_auth_key_hashed` gone from `ServiceAccount` |
| `cli/…/sa/SaCommand.kt` | register `SaKeyCommand` |
| `cli/…/sa/SaUpdateCommand.kt` | remove `--regenerate-api-key`, stop sending the field |
| `cli/…/table/ObjectAsciiTablePrinter.kt` | add `revokedAt` to `timestampFields`, otherwise it prints a raw epoch; null already renders empty. Add `flattenKeys`, above |
| `cli/…/format/Printing.kt`, `cli/…/base/BaseCommand.kt` | thread `flattenKeys` through to the table printer |
| `cli/src/test/…/format/PrintingTest.kt` | one case for `flattenKeys` |
| `mock/…/MockDivyamServer.kt` | drop `divyamAuthKeyHashed` (the only compile break from the spec change); add a key store, the three routes with the cap and last-key guards, and a 400 for `regenerate_api_key: true`. Creating an account seeds one key named `default_key`, matching the server |
| `cli/src/test/…/DivyamCliTest.kt` | tests below |

Untouched: `DivyamClient.kt` (the generated methods are enough), `DivyamCliMain.kt` (`sa` is already
registered), `Config.kt`, and the GraalVM reflection config — generated models carry `@Reflectable`,
so new ones are picked up automatically.

## Tests

Against the mock, in `DivyamCliTest`:

- `create`, then `ls` shows both keys and `api_key` comes back at the top level
- `revoke`, then `ls` shows `revoked_at` set on that key
- revoking the only active key exits non-zero carrying the server's message, with no `Error: 400`
  prefix leaking through
- `ls` resolves the account from `DIVYAM_SA_ID`
- `sa update --regenerate-api-key` is rejected as an unknown option

No test for the cap. The CLI behaviour it would exercise — a non-2xx surfacing as the server's
`detail` — is already covered by the last-key case, and the cap itself belongs to the server.

Null fields are dropped from CLI output, so an active key omits `revoked_at` rather than showing it
as null. Tests assert "absent or null" rather than either alone.

## Compatibility

**New CLI against an old router.** `sa key *` returns 404. Everything else is unaffected: the extra
`divyam_auth_key_hashed` in responses is ignored, since the client disables
`FAIL_ON_UNKNOWN_PROPERTIES`.

**Old CLI against a new router: broken, and not fixable from this repo.** Removing
`divyam_auth_key_hashed` from the response means an already-installed CLI cannot deserialize
`ServiceAccount` at all — the generated property is non-null with no default. Verified by building
the current CLI and running it against a mock returning the post-#307 payload:

```
Illegal json parameter found: Instantiation of [simple type, class ai.divyam.data.model.ServiceAccount]
value failed for JSON property divyam_auth_key_hashed due to missing (therefore NULL) value for
creator parameter divyamAuthKeyHashed which is a non-nullable type
```

Affected: `sa get`, `sa ls`, `sa update`, `sa create`, and `eval create` (`EvalCreateCommand:77`
fetches the account). Unaffected: `org`, `user`, `selector`, `model-info`, `chat`, `config`, and
`eval ls|get|update`.

`sa create` is the costly one. The POST commits the account and its key row, then deserialization
fails before `printObjs`, so the one-time `api_key` is discarded after arriving. It is recoverable
only with a new CLI (`sa key create`) or by recreating the account.

Two related findings, both verified the same way:

- Returning `"divyam_auth_key_hashed": null` fails identically. Only a non-null value keeps old
  clients working, so a nullable response field would not have been a compatibility shim.
- The field's removal is also what hides the `regenerate_api_key` 400 from CLI callers: an old CLI
  fails at the GET that `sa update` performs first, so the POST carrying the flag is never sent.

**Therefore the CLI upgrade is not optional and has to land in the same window as the #307 deploy.**

## Sequencing

1. #307 merges.
2. Run the controller locally with `divyamlibs` pointed at the local checkout, regenerate
   `client/specs/openapi.json`.
3. This CLI change, released alongside the controller deploy.

## Not in scope

- Revoke by key name. Names are not unique, per the LLD.
- Writing a newly created key into `~/.divyam/config.json`.
- An `--include-revoked` filter on `ls`.
- Error handling for commands other than the three added here.
