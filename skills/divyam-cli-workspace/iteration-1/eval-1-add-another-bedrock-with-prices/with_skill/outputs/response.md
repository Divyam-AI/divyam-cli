Here's the Bedrock model registration, built off the same flow as the Gemini one. Two things changed a lot, so let me restate the diff before you run it:

- **Provider** `google` → `bedrock`
- **API type** `GEMINI` → `COMPLETIONS` (Claude on Bedrock is a chat/messages-style endpoint, not a Gemini endpoint)
- **Model** `gemini-2.5-flash` → your Bedrock Claude Sonnet model id
- **Prices added** — Bedrock isn't in the built-in pricing card, so I'm passing your quoted `--input-price 3 --output-price 15` (USD, per 1,000,000 tokens, both defaults)

Command to run in your terminal (it will prompt for the provider key with no echo):

```
divyam model-info create --provider-name bedrock --api-type COMPLETIONS --model-names anthropic.claude-sonnet-4-20250514-v1:0 --provider-base-url "" --input-price 3 --output-price 15 --provider-api-key
# ^ run this yourself; it prompts you to paste the key (no echo)
```

Two things to confirm before you run it, since I had to fill them in:

1. **Exact model id** — I put `anthropic.claude-sonnet-4-20250514-v1:0` as the Bedrock model string. Swap in whichever Claude Sonnet Bedrock model id you actually want (e.g. a `3-5-sonnet` or inference-profile id); the `$3 / $15` per-million pricing matches the Sonnet tier either way.
2. **Base URL** — I carried over the empty `--provider-base-url ""` from the Gemini command. If your Bedrock setup needs a regional runtime endpoint (or if region/creds live in `--model-configs-json`), tell me and I'll adjust. `--provider-base-url` is required, so it can't be dropped.

As before, I'm handing this off rather than running it because `--provider-api-key` carries your secret — running it here would put the key into argv/history. Paste the key straight into the prompt instead.
