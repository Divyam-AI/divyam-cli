# Gemini model info (`model-info create`)

Use `divyam model-info create` to register Google Gemini models with the Divyam router. Credentials and Vertex settings map to `ModelProviderInfoCreation` on `POST /v1/orgs/{org_id}/models_info/`.

Full field reference and GCP commands: [divyam_router_controller — Gemini model provider config](https://github.com/Divyam-AI/divyam_router_controller/blob/main/docs/gemini_model_provider_config.md).

Common flags:

| Flag | Maps to |
|------|---------|
| `--provider-name google` | `name_provider` |
| `--model-names` | `name_model` (comma-separated) |
| `--api-type` | `GEMINI` or `COMPLETIONS` |
| `--provider-api-key` | `api_key_model` (API key or SA JSON string; omit for Vertex ADC) |
| `--provider-base-url` | `endpoint` |
| `--model-configs-json` | `configs_model` (JSON string) |

Prerequisites for all examples: set org/SA context (`--org-id`, `--service-account-id`, or `DIVYAM_ORG_ID` / `DIVYAM_SA_ID`), plus auth (`--endpoint`, `--user` / `--password` or API token).

---

## 1) AI Studio with API key

```bash
divyam model-info create \
  --org-id 1 \
  --service-account-id "<sa-id>" \
  --provider-name google \
  --model-names gemini-2.0-flash \
  --api-type GEMINI \
  --provider-api-key "AIzaSy..." \
  --provider-base-url "" \
  --model-configs-json '{}'
```

OpenAI-compatible routing:

```bash
divyam model-info create \
  --org-id 1 \
  --service-account-id "<sa-id>" \
  --provider-name google \
  --model-names gemini-2.0-flash \
  --api-type COMPLETIONS \
  --provider-api-key "AIzaSy..." \
  --provider-base-url "https://generativelanguage.googleapis.com/v1beta/openai" \
  --model-configs-json '{}'
```

No GCP setup required; create a key at [Google AI Studio](https://aistudio.google.com/apikey).

---

## 2) Vertex AI with service account JSON key

```bash
# One-time GCP (customer project): enable Vertex, create SA, grant roles/aiplatform.user, download key.

export VERTEX_SA_KEY_JSON=$(cat /path/to/vertex-key.json)

divyam model-info create \
  --org-id 1 \
  --service-account-id "<sa-id>" \
  --provider-name google \
  --model-names gemini-2.0-flash \
  --api-type GEMINI \
  --provider-api-key "$VERTEX_SA_KEY_JSON" \
  --provider-base-url "" \
  --model-configs-json '{"project_id":"my-vertex-project","location":"us-central1"}'
```

Pass the service account JSON as the `--provider-api-key` value (file contents or a single-line JSON string).

---

## 3) Vertex AI with Divyam default credentials (ADC)

Router uses Application Default Credentials (GKE Workload Identity, GCE SA, or local `gcloud auth application-default login`). Omit the API key.

```bash
divyam model-info create \
  --org-id 1 \
  --service-account-id "<sa-id>" \
  --provider-name google \
  --model-names gemini-2.0-flash \
  --api-type GEMINI \
  --provider-base-url "" \
  --model-configs-json '{"project_id":"customer-vertex-project","location":"us-central1"}'
```

Customer GCP: grant Divyam’s runtime service account `roles/aiplatform.user` on `customer-vertex-project`. See [router GCP section](https://github.com/Divyam-AI/divyam_router_controller/blob/main/docs/gemini_model_provider_config.md#3-vertex-ai-with-divyam-default-credentials-adc).

---

## 4) Vertex AI with impersonation (customer service principal / WIF)

Divyam’s ADC impersonates the customer’s Vertex service account. Complete the GCP steps below, then register the model with `divyam model-info create`.

### Environment variables

Set these in your shell (replace values for your environment):

```bash
export VERTEX_PROJECT_ID="customer-vertex-project"          # GCP project with Vertex AI enabled
export VERTEX_SA_NAME="divyam-vertex-runner"                # SA account ID (no @domain)
export DIVYAM_RUNTIME_SA="router-controller-prod-sa@divyam-production.iam.gserviceaccount.com"  # Divyam hosted SA (from your onboarding contact)
export VERTEX_LOCATION="us-central1"

export VERTEX_SA_EMAIL="${VERTEX_SA_NAME}@${VERTEX_PROJECT_ID}.iam.gserviceaccount.com"

export MODEL_CONFIGS_JSON=$(cat <<EOF
{
  "project_id": "${VERTEX_PROJECT_ID}",
  "location": "${VERTEX_LOCATION}",
  "target_principal": "${VERTEX_SA_EMAIL}"
}
EOF
)
```

### Customer GCP setup

**Step 1 — Select project and enable APIs**

```bash
gcloud config set project "$VERTEX_PROJECT_ID"

gcloud services enable aiplatform.googleapis.com iam.googleapis.com
```

**Step 2 — Create the Vertex service account**

Skip if the account already exists.

```bash
gcloud iam service-accounts create "$VERTEX_SA_NAME" \
  --project="$VERTEX_PROJECT_ID" \
  --display-name="Divyam Vertex runner"
```

**Step 3 — Grant Vertex AI access to the service account**

```bash
gcloud projects add-iam-policy-binding "$VERTEX_PROJECT_ID" \
  --member="serviceAccount:${VERTEX_SA_EMAIL}" \
  --role="roles/aiplatform.user"
```

**Step 4 — Allow Divyam to impersonate the service account**

Grants Divyam’s runtime service account permission to obtain tokens for your Vertex SA (`target_principal` in `MODEL_CONFIGS_JSON`).

```bash
gcloud iam service-accounts add-iam-policy-binding "$VERTEX_SA_EMAIL" \
  --project="$VERTEX_PROJECT_ID" \
  --member="serviceAccount:${DIVYAM_RUNTIME_SA}" \
  --role="roles/iam.serviceAccountTokenCreator"
```

### Register the model in Divyam

Omit `--provider-api-key` so Divyam uses ADC to impersonate `target_principal`:

```bash
divyam model-info create \
  --org-id 1 \
  --service-account-id "<sa-id>" \
  --provider-name google \
  --model-names gemini-2.0-flash \
  --api-type GEMINI \
  --provider-base-url "" \
  --model-configs-json "$MODEL_CONFIGS_JSON"
```

---

## Updating model info

```bash
divyam model-info update \
  --org-id 1 \
  --id <model-info-id> \
  --model-configs-json '{"project_id":"customer-vertex-project","location":"global"}' \
  --provider-api-key "AIzaSy..."
```

Use `--skip-pricing-update` when changing only credentials or configs.

---

## Related links

- [divyam_python_libs — Gemini provider configuration](https://github.com/Divyam-AI/divyam_python_libs#gemini-provider-configuration)
- [Integration test env example](https://github.com/Divyam-AI/divyam_python_libs/blob/main/tests/divyamlibs_tests/integration_tests/model_providers/.env.gemini.integration.example)
