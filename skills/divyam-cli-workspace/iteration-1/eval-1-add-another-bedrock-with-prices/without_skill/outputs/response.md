Here's the command to add Claude Sonnet on AWS Bedrock, following the same `model-info create` pattern we used for the Gemini model, plus the input/output pricing you specified.

## Command

```
divyam model-info create --provider-name aws --api-type BEDROCK --model-names anthropic.claude-3-5-sonnet-20241022-v2:0 --provider-base-url "" --provider-api-key "<AWS_BEDROCK_CREDENTIALS>" --input-token-price 3 --output-token-price 15
```

## A few things I need to confirm with you before running this

Because Bedrock differs from the Gemini setup in a couple of ways, some of the values above are my best guess. Please double-check these against your `divyam model-info create --help` output:

1. **`--api-type` value.** I used `BEDROCK`. Bedrock is an AWS gateway rather than a native Anthropic endpoint, so the exact enum the CLI expects may instead be something like `ANTHROPIC_BEDROCK` or `AWS_BEDROCK`. Please verify the accepted value.

2. **Model name / model ID.** On Bedrock, models are referenced by their Bedrock model ID, not the plain "claude sonnet" name. I used `anthropic.claude-3-5-sonnet-20241022-v2:0` as a representative Claude Sonnet ID. Confirm the exact model (e.g. 3.5 Sonnet vs 3.7 Sonnet vs Sonnet 4) and region-qualified ID you actually want, for example an inference-profile ID like `us.anthropic.claude-3-5-sonnet-20241022-v2:0`.

3. **Authentication.** Gemini used a single `--provider-api-key`. Bedrock normally authenticates with AWS credentials (access key ID, secret access key, session token) and a **region**, not one API key. I don't see a region or AWS-credential flag in the pattern we used, so this is the biggest open question:
   - Does the CLI accept AWS credentials packed into `--provider-api-key`?
   - Is there a separate flag for the AWS region (e.g. `us-east-1`) and for the secret key?
   
   Please check the help text for the AWS/Bedrock-specific auth flags before running.

4. **Pricing flags.** You asked for input 3 and output 15 USD per million tokens. I used `--input-token-price 3` and `--output-token-price 15`, but the exact flag names and the expected unit (per-million vs per-token) may differ. Confirm the flag names and whether the value should be `3` (per million) or a per-token figure like `0.000003`.

If you can paste the output of `divyam model-info create --help`, I'll give you the exact, verified command with the correct Bedrock auth, region, model ID, and pricing flags.
