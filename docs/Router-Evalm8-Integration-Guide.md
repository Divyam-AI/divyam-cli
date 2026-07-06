# Divyam Router + evalm8: Route on Quality

You drive both from the `divyam` CLI. Full command reference and per-topic guides live in the [divyam CLI wiki](https://github.com/Divyam-AI/divyam-cli/wiki). New to the CLI? Start with [Installation](https://github.com/Divyam-AI/divyam-cli/wiki/Installation).

```text
Your app ──▶ Divyam Router ──▶ chosen model (OpenAI, Gemini, Claude, ...)
                   │
                   └─ scores (sampled) traffic with your evalm8 eval ──▶ dashboards + selectors
```

### Endpoints

| Use | URL |
| --- | --- |
| Router API | `https://api.divyam.ai` |
| evalm8 service | `https://evalm8.divyam.ai` |

---

## Step 1: Onboard and route (drop-in)

> Wiki: [Setup Your Account](https://github.com/Divyam-AI/divyam-cli/wiki/Setup-Your-Account) · [Config](https://github.com/Divyam-AI/divyam-cli/wiki/Config) · [Manage your LLM models](https://github.com/Divyam-AI/divyam-cli/wiki/Manage-your-LLM-models) · [Onboard Your Application](https://github.com/Divyam-AI/divyam-cli/wiki/Onboard-Your-Application-to-Divyam)

```bash
# 1. Create your org and a service account. Save the printed API key, it is shown only once.
divyam org create --name "Acme"
export DIVYAM_ORG_ID=<org-id-from-output>

divyam sa create --name "acme-prod"
export DIVYAM_API_TOKEN=divyam-v1-********        # from output

# 2. Save a reusable CLI config and activate it.
divyam config set -c acme-prod -e https://api.divyam.ai \
  -o $DIVYAM_ORG_ID -s <service-account-id> -t $DIVYAM_API_TOKEN
divyam config use acme-prod

# 3. Register a provider and the model(s) you run today.
divyam model-info create --provider-name openai \
  --provider-base-url https://api.openai.com/v1 \
  --provider-api-key <your-openai-key> \
  --model-names gpt-4o,gpt-4o-mini
```

Point your app at the router (OpenAI SDK):

```python
from openai import OpenAI
client = OpenAI(base_url="https://api.divyam.ai/v1", api_key="divyam-v1-********")
client.chat.completions.create(model="openai:gpt-4o", messages=[...])
```

The router is now a transparent passthrough, returning the same responses as before with full logging. Watch requests arrive in real time on your [dashboards](https://github.com/Divyam-AI/divyam-cli/wiki/Access-your-Dashboards).

**Optional request headers** for analytics and multi-turn evals:

| Header | Purpose |
| --- | --- |
| `x-user-id` | consistent routing and per-user analytics |
| `x-session-id` | required for `SESSION_BASED` evals |
| `x-eval-request-id` | required for `TURN_BASED` evals (agentic flows) |
| `x-flow-id` | tag traffic for analytics |

**Tip:** test any model through the router without touching your app: `divyam chat --model-name openai:gpt-4o`.

---

## Step 2: Build an eval in evalm8

Sign in to evalm8 (`https://evalm8.divyam.ai`), open your org and project, then register a **Connection** so evalm8 can reach your router and models: Integrations → Connections → Create New Connection. Pick **DivyamConnection**, set the Base URL, paste your API key, click Test Connection, then Register.

<details><summary>🖼️ evalm8 → Create Connection</summary>

![Create Connection](image/Router-Evalm8-Integration-Guide/connection.png)

</details>

The eval is built in three phases: **define** it once, **evaluate** raw traffic with the LLM judge, then build a **golden dataset** from human annotations to sharpen the judge. Example throughout: a **Tutor Eval** scoring tutor answers on Correctness and Understandability.

### Phase 1: Define (one-time)

**a. Rubric** (Evaluation → Rubrics) sets the dimensions, scales, and pass threshold.

<details><summary>Rubric definition (JSON)</summary>

```json
{
  "name": "Tutor Effectiveness",
  "description": "Correctness and understandability of tutor responses",
  "dimensions": [
    {"name": "Understandability", "scale": {"type": "integer", "min": 1, "max": 5}, "min_passing_score": 2, "weight": 1, "is_inverted": false},
    {"name": "Correctness",       "scale": {"type": "integer", "min": 1, "max": 5}, "min_passing_score": 2, "weight": 1, "is_inverted": false}
  ],
  "passing_score_threshold": 0.7
}
```

</details>

<details><summary>🖼️ evalm8 → Rubric builder</summary>

![Rubric builder](image/Router-Evalm8-Integration-Guide/1783316382879.png)

</details>

**b. Model providers** (Integrations → Model Providers): add the models the judges and your candidates run on.

<details><summary>🖼️ evalm8 → Model providers</summary>

![Model providers](image/Router-Evalm8-Integration-Guide/1783316571405.png)

</details>

**c. Judges** (Evaluation → Judges): one LLM judge per dimension. The judge name becomes its slug ID (`Correctness JUDGE` becomes `correctness-judge`), which the eval references. Use origin `bespoke` for a fixed prompt, or `fine_tuned` so the judge learns from your golden dataset over time. The template holds the 1-to-5 scoring guide and reads `{{query}}` and `{{response}}`.

<details><summary>Judge definition (JSON)</summary>

```json
{
  "type": "llm",
  "origin": "fine_tuned",
  "name": "Correctness JUDGE",
  "inputs": [
    {"name": "query",    "target_type": "string", "description": "The prompt sent to the model."},
    {"name": "response", "target_type": "string", "description": "The model response to evaluate."}
  ],
  "template": "You are an expert evaluator assessing the correctness of a model response. Score 1 to 5, then explain briefly. Query: {{query}} Response: {{response}}",
  "mode": {"type": "pointwise", "scale": {"type": "integer", "min": 1, "max": 5}}
}
```

</details>

<details><summary>🖼️ evalm8 → Judge builder</summary>

![Judge builder](image/Router-Evalm8-Integration-Guide/judge-builder.png)

</details>

**d. Eval** (Evaluation → Evals): name it, select the rubric, and for each dimension click Configure Judge, set `judge_id` to the judge slug, and paste the pipeline. The pipeline runs per trace: pick the last LLM span, guard against missing spans, extract `query` and `response` from `llm.input_messages` and `llm.output_messages`, then call the judge for a 1-to-5 score. No ground-truth field is needed, so it works on raw production traces.

<details><summary>Eval pipeline (JSON, abbreviated)</summary>

```json
{
  "steps": [
    { "id": "correctness_prompt_prefix", "type": "static",
      "result": "You are an expert evaluator assessing correctness..." },
    { "id": "llm_span", "type": "select",
      "selector": { "type": "cel",
        "expression": "[last(sortByKey(trace.spans.filter(s, s[\"openinference.span.kind\"] == \"LLM\"), \"end_time\"))[\"span_id\"]]" } },
    { "id": "guard_llm_span", "type": "guard",
      "condition": { "type": "cel", "condition": "size(trace.spans) > 0" },
      "on_fail": { "result": { "reason": "No LLM span found.", "score": 1 } } },
    { "id": "extracted_inputs", "type": "extract",
      "resolver": { "type": "cel", "mapping": {
        "query": "trace.spans[0].attributes[\"llm.input_messages\"][0][\"message.content\"]",
        "response": "trace.spans[0].attributes[\"llm.output_messages\"][0][\"message.content\"]"
      } } },
    { "id": "correctness_score", "type": "call",
      "judge_id": "correctness-judge",
      "input_mapping": {
        "query":    { "type": "declarative", "value": "extracted_inputs.query" },
        "response": { "type": "declarative", "value": "extracted_inputs.response" }
      } }
  ]
}
```

The Understandability pipeline is identical except `judge_id` is `understandability-judge`.

</details>

<details><summary>🖼️ evalm8 → Eval builder</summary>

![Eval builder](image/Router-Evalm8-Integration-Guide/eval-builder.png)

</details>

### Phase 2: Evaluate raw traffic

**e. Import the raw dataset** (Data → Datasets → Raw): upload `raw.jsonl`. Each record is a trace with at least one LLM span carrying `llm.input_messages` and `llm.output_messages`.

<details><summary>Required trace structure (JSON)</summary>

```json
{
  "trace_id": "uuid",
  "spans": [
    {
      "kind": "LLM",
      "attributes": {
        "llm.model_name": "gemini-2.5-flash-lite",
        "llm.input_messages":  [{ "message.role": "user",      "message.content": "your question here" }],
        "llm.output_messages": [{ "message.role": "assistant", "message.content": "model response here" }]
      }
    }
  ]
}
```

</details>

**f. Run Evaluate Dataset** (Workflows → Evaluate Dataset): pick the eval and the raw dataset, then Run. It scores every trace 1 to 5 (shown normalised 0 to 1) with judge reasoning, visible in the Evaluations tab.

<details><summary>🖼️ evalm8 → Evaluate Dataset (running)</summary>

![Evaluate Dataset](image/Router-Evalm8-Integration-Guide/evaluate-run.png)

</details>

<details><summary>What the normalised scores mean</summary>

| Displayed | Raw | Meaning |
| --- | --- | --- |
| 0.00 | n/a | Pipeline guard exited before the judge (usually missing `llm.input_messages` or `llm.output_messages`) |
| 0.25 | 1 | Completely incorrect or incomprehensible |
| 0.50 | 2 | Mostly incorrect or mostly unclear |
| 0.75 | 3 | Partially correct or partially clear |
| 0.88 | 4 | Mostly correct or mostly clear |
| 1.00 | 5 | Perfectly correct or exceptionally clear |

</details>

### Phase 3: Golden dataset (human annotation)

**g. Run Create Eval Golden Dataset** (Workflows → Create Eval Golden Dataset): pick the eval and raw dataset, then Run. It samples traces, creates an annotation task, and pauses at **Await Data Annotation** for human reviewers.

<details><summary>🖼️ evalm8 → Create Eval Golden Dataset (completed)</summary>

![Golden Dataset workflow](image/Router-Evalm8-Integration-Guide/golden-dataset.png)

</details>

**h. Annotate in Argilla**: each record shows the `query`, the `response`, and the judge's pre-scored suggestion as a starting point. Enter your own score (1 to 5) and reasoning, then **Submit** each record (drafts do not advance the workflow). Submitted labels become the train and test splits.

<details><summary>🖼️ Argilla → annotation task</summary>

![Argilla annotation](image/Router-Evalm8-Integration-Guide/argilla-annotation.png)

</details>

**i. Refine Eval (optional)** (Workflows → Refine Eval): fine-tunes a `fine_tuned` judge on the golden train and test splits, so automated scores track human judgement more closely each cycle. Repeat Phases 2 and 3 as you gather more labels.

> The evalm8 UI walkthrough for Refine Eval is not documented yet.

<details><summary>Common issues</summary>

| Symptom | Fix |
| --- | --- |
| All traces score 0.00 or 1 | Pipeline guard is failing. Confirm `llm.input_messages` and `llm.output_messages` exist in the span. |
| `JudgeNotFound` in a trace | `judge_id` must match the judge slug exactly (`correctness-judge`, not `correctness`). |
| Annotation count stuck | Records must be **Submitted** in Argilla, not saved as draft. |
| `No trainable LLM judges found` on Refine | Run Evaluate Dataset first, and use a `fine_tuned` judge with completed golden splits. |

</details>

---

## Step 3: Connect the eval to the router

> Wiki: [Manage Evals](https://github.com/Divyam-AI/divyam-cli/wiki/Manage-Evals)

Register the eval on your service account so the router scores traffic with it:

```bash
divyam eval create --name evalm8 --granularity LLM_REQUEST_RESPONSE --state ACTIVE \
  --class-name "divyamlibs.evaluator.strategies.evalm8.evalm8_evaluation_criteria.Evalm8RequestResponseEvaluationCriteria" \
  --class-init-config '{
    "base_url": "https://evalm8.divyam.ai",
    "org": "<your-org>",
    "project": "Tutor",
    "eval_name": "Tutor Eval",
    "eval_ref": "latest",
    "api_key": "<change-me>"
  }'
```

<details><summary>Field reference: <code>--class-init-config</code></summary>

| Field | Meaning |
| --- | --- |
| `base_url` | evalm8 service URL |
| `org` / `project` | your evalm8 workspace and project |
| `eval_name` | the eval you built in Step 2 |
| `eval_ref` | version to use (`latest` or a pinned ref) |
| `api_key` | evalm8 API key |

</details>

`--granularity LLM_REQUEST_RESPONSE` scores each request/response pair. Use `TURN_BASED` or `SESSION_BASED` for multi-turn or full-session scoring (see the header requirements in Step 1).

If you run more than one eval, mark the one routing should optimize toward as primary:

```bash
divyam eval update --id <eval-id> --is-primary true
```

Sampled traffic is now scored against your rubric, and scores show up in your [dashboards](https://github.com/Divyam-AI/divyam-cli/wiki/Access-your-Dashboards) as quality trends per model.

---

## Step 4: Route on quality

> Wiki: [Setup Model Routing](https://github.com/Divyam-AI/divyam-cli/wiki/Setup-Model-Routing) · [Safely Remove a Model](https://github.com/Divyam-AI/divyam-cli/wiki/Safely-Remove-a-Model)

Once the eval is live, use it to move to a better or cheaper model without guesswork. Routing is driven by a **selector**: a policy trained on your traffic and scored by your eval, which the router serves only after you promote it. Prerequisites: the eval from Step 3, and at least two candidate models registered.

> **Order matters.** A selector trains on your logged traffic but never on the `control` bucket (a held-out baseline). Route some traffic out of `control` before you send it, otherwise there is nothing to train on.

The selector moves through `TRAINED` → `SHADOW` (staged, not served) → `PROD` (serving live traffic).

**1. Register the candidate model(s)** you want to consider.

```bash
divyam model-info create --provider-name openai \
  --provider-base-url https://api.openai.com/v1 \
  --provider-api-key <key> --model-names gpt-4o-mini
```

**2. Create the selector** across the candidates, linked to your eval.

```bash
divyam selector create --name tutor-selector \
  -m "openai:gpt-4o,openai:gpt-4o-mini" \
  -x message_history --eval-id <eval-id>
export DIVYAM_SELECTOR=<selector-id>
```

Use `-c <config.yaml>` instead of `-x` to drive it from a config file (see the wiki's `sample-selector-config.yaml`).

**3. Set traffic allocation before sending traffic.** Route traffic out of pure `control` so the selector has data to learn from. It is not serving yet, so it is not in the split.

```bash
divyam sa update --traffic-allocation-config '{"control": 10.0, "selector_disabled": 90.0}'
```

**4. Send real traffic through the router** (your application, or `divyam chat`). This logged, non-`control` traffic is what the selector trains on.

```bash
divyam chat --model-name openai:gpt-4o
```

**5. Let it train.** The pipeline advances through its stages to `TRAINED`. Track it on the [training dashboard](https://github.com/Divyam-AI/divyam-cli/wiki/Access-your-Dashboards).

```bash
divyam selector get --id $DIVYAM_SELECTOR   # state progresses to TRAINED
```

**6. Stage it in shadow.** Set the cost/quality lambda. This advances the trained selector to `SHADOW`, where it is validated against your traffic but does not serve live requests and is not in the traffic split (`0.0` is max quality, `1.0` is max savings).

```bash
divyam selector update --id $DIVYAM_SELECTOR --lambda 0.5
# or split the two goals
divyam selector update --id $DIVYAM_SELECTOR --high-quality-lambda 0.8 --cost-savings-lambda 0.2
divyam selector get --id $DIVYAM_SELECTOR    # state: SHADOW
```

**7. Promote shadow to production.** Only a `SHADOW` selector can be promoted.

```bash
divyam selector update --id $DIVYAM_SELECTOR --to-prod
```

**8. Give it live traffic and ramp.** Now that it is `PROD`, add its share to the allocation and raise it as it proves out. Monitor routing decisions and the model split on your [dashboards](https://github.com/Divyam-AI/divyam-cli/wiki/Access-your-Dashboards).

```bash
divyam sa update --traffic-allocation-config \
  '{"control": 10.0, "selector_disabled": 80.0, "'"$DIVYAM_SELECTOR"'": 10.0}'
```

evalm8 keeps scoring live traffic throughout, so you see the quality and cost impact as you ramp.

---

## Inspect and manage

```bash
divyam eval ls                              # your registered evals
divyam model-info ls                        # registered providers and models
divyam selector get --id $DIVYAM_SELECTOR   # state: TRAINED, SHADOW, PROD, INACTIVE, ...
```

Retire a selector with `divyam selector update --id $DIVYAM_SELECTOR --retire`. To pull a model out of rotation cleanly, follow [Safely Remove a Model](https://github.com/Divyam-AI/divyam-cli/wiki/Safely-Remove-a-Model).

---

**The loop:** onboard, route as passthrough, define quality in evalm8, register the eval, then let selectors route on that signal. Repeat as new models ship.

*More detail on any step: [divyam CLI wiki](https://github.com/Divyam-AI/divyam-cli/wiki).*
