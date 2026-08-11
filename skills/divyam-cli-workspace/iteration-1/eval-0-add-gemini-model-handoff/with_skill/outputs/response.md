## Commands

First, since this is the first use this session, I confirm the active service account (safe read, I run it myself):

```
divyam config current
```

→ Active service account: `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai`.

Then, the model registration. Because a provider API key is involved, I do **not** run this — I hand it off for you to run in your own terminal so the key never lands in argv, shell history, or logs:

```
divyam model-info create \
  --provider-name google \
  --api-type GEMINI \
  --model-names gemini-2.5-flash \
  --provider-base-url "" \
  --provider-api-key
# ^ run this in your terminal; it will prompt you to paste the key (no echo). Do NOT type the key on the command line.
```

## Message to the user

Active service account: `acme-prod` (`sa_demo_123`) on `https://api.divyam.ai`.

Here's the command to register Gemini 2.5 Flash. A few notes on the choices:

- `--provider-name google` + `--api-type GEMINI` — that's the "Gemini endpoint" mapping.
- `--provider-base-url ""` — an AI-Studio Gemini key (your `AIza…` key is one) uses the empty base URL.
- No pricing flags — Gemini is in the built-in pricing card, so input/output prices are filled automatically.

I'm intentionally **not** running this for you and **not** putting your key on the command line — that would leak it into your shell history and process list. Instead, run the command above in your own terminal. The `--provider-api-key` flag is interactive: it will prompt you to paste the key with no echo. (You already shared the key in chat, but I'm routing it through the prompt rather than inlining it — you can rotate it later if you'd like.)

Once it succeeds, `gemini-2.5-flash` will be registered under provider `google` and available as a candidate model for selectors. Want me to line up a selector that includes it next?
