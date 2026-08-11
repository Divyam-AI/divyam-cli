Command(s) I would run:

```bash
divyam models add \
  --provider google \
  --model "gemini-2.5-flash" \
  --endpoint gemini \
  --api-key AIzaSyEXAMPLE1234567890
```

If your CLI groups these under a "providers" or "models create" subcommand, the equivalent form would be:

```bash
divyam models create \
  --name "gemini-2.5-flash" \
  --provider google \
  --endpoint gemini \
  --api-key AIzaSyEXAMPLE1234567890
```

Message to the user:

I'll add the Gemini 2.5 Flash model for you using the Google provider and the Gemini endpoint, with the API key you supplied.

Before I run it, a couple of quick notes:

- This will register the model against your currently active config: service account `sa_demo_123` (acme-prod), org 1, endpoint `https://api.divyam.ai`. If you meant to add it to a different service account or org, let me know and I'll adjust.
- Heads up on the API key: you pasted `AIzaSyEXAMPLE1234567890` directly in chat. If that's a real Google API key, I'd recommend rotating it after we're done, since anything shared in plaintext should be treated as exposed. Going forward you can also pass the key via an environment variable (e.g. `--api-key $GOOGLE_API_KEY`) so it doesn't sit in your shell history or logs.

I don't have the exact flag names for your installed `divyam` version memorized, so if the command above errors out, run `divyam models add --help` (or `divyam models --help`) and I'll match the exact flags. Want me to proceed with the command as written?
