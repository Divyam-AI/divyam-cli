# Evalm8 evals (`eval create`)

Use `divyam eval create` to register an eval that lives in evalm8 with the Divyam router. The CLI resolves the evaluator class for you, so no dotted Python path is needed.

This is step 3 of the router and evalm8 journey. Build the rubric, judges and eval in evalm8 first, then register the finished eval here. Full journey: [Router and Evalm8 Integration](https://github.com/Divyam-AI/divyam-cli/wiki/Router-and-Evalm8-Integration).

Common flags:

| Flag | Maps to |
|------|---------|
| `--name` | `name`, the eval's name in the router |
| `--evalm8-org` | `class_init_config.org` |
| `--evalm8-project` | `class_init_config.project` |
| `--evalm8-eval-name` | `class_init_config.eval_name`, the eval as named in evalm8 |
| `--evalm8-eval-ref` | `class_init_config.eval_ref`, default `latest` |
| `--evalm8-base-url` | `class_init_config.base_url`, default `https://evalm8.divyam.ai` |
| `--evalm8-api-key` | `class_init_config.api_key`, the evalm8 key, not the router key |
| `--state` | `state`, default `ACTIVE` |
| `--is-primary` | `is_primary`, default `false` |
| `--eval-config`, `--eval-config-file` | The whole payload, overridden by any flag above |
| `--skip-verify` | Register without checking the eval exists in evalm8 |

The evalm8 api key is a separate credential from the router api key, and Divyam provisions it. There is no environment-variable fallback for it, so pass it explicitly on every call.

Prerequisites for all examples: set org and SA context (`--org-id`, `--service-account-id`, or `DIVYAM_ORG_ID` / `DIVYAM_SA_ID`), plus auth (`--endpoint`, `--user` and `--password`, or an api token).

---

## 1) Register an evalm8 eval

```bash
divyam eval create \
  --name "Tutor Eval" \
  --evalm8-org acme \
  --evalm8-project tutor \
  --evalm8-eval-name "Tutor Eval" \
  --evalm8-api-key "<evalm8-key>"
```

Granularity is not passed. The router derives it from the evaluator class, which for evalm8 is always `LLM_REQUEST_RESPONSE`.

Before sending anything to the router the CLI fetches the eval from evalm8 to confirm the org, project, name and key are right. Registration is refused if it is not there, because the router stores the config without checking it, and a wrong value would otherwise only surface much later as an eval that quietly never scores.

The returned `id` is what `selector create --eval-id` consumes next.

## 2) Pin a specific version

```bash
divyam eval create \
  --name "Tutor Eval v3" \
  --evalm8-org acme \
  --evalm8-project tutor \
  --evalm8-eval-name "Tutor Eval" \
  --evalm8-eval-ref v3 \
  --evalm8-api-key "<evalm8-key>"
```

## 3) Register from a config file

Keep the whole eval in one reviewable document, one per environment:

```json
{
  "name": "Tutor Eval",
  "state": "ACTIVE",
  "is_primary": true,
  "class_name": "divyamlibs.evaluator.strategies.evalm8.evalm8_evaluation_criteria.Evalm8RequestResponseEvaluationCriteria",
  "class_init_config": {
    "base_url": "https://evalm8.divyam.ai",
    "org": "acme",
    "project": "tutor",
    "eval_name": "Tutor Eval",
    "eval_ref": "latest",
    "api_key": "<evalm8-key>"
  },
  "sampling_config": {}
}
```

```bash
divyam eval create --eval-config-file tutor-eval.json
```

Any flag overrides the document, which is how one file serves several environments:

```bash
divyam eval create --eval-config-file tutor-eval.json --evalm8-eval-ref v3
```

`--eval-config` takes the same document inline. The two are mutually exclusive. A config file is also the only way to set `sampling_config`, which has no flag.

## 4) Point at a different evalm8

```bash
divyam eval create \
  --name "Tutor Eval" \
  --evalm8-base-url "https://evalm8.internal.example" \
  --evalm8-org acme \
  --evalm8-project tutor \
  --evalm8-eval-name "Tutor Eval" \
  --evalm8-api-key "<evalm8-key>"
```

Verification follows the same URL, so a self-hosted evalm8 is checked exactly like the default one.

## Registering a built-in evaluator

The router also ships evaluator classes that run in process and need no external credentials. They are an advanced path, hidden from `--help`, and still work unchanged:

```bash
divyam eval create \
  --name "session-string-check" \
  --class-name "divyamlibs.evaluator.strategies.string_check_session_eval_criteria.StringCheckSessionEvaluationCriteria" \
  --class-init-config '{"string_expression": "done"}'
```

`--class-name` cannot be combined with the `--evalm8-*` flags, since both name the evaluator class. Granularity is derived from whichever class is named, so `--granularity` is only needed to override that.

## Updating an eval

`divyam eval update` still takes `--class-name` and `--class-init-config` directly. Changing only the class re-derives the granularity, so the two cannot drift apart.

## Related links

- [Router and Evalm8 Integration](https://github.com/Divyam-AI/divyam-cli/wiki/Router-and-Evalm8-Integration)
- [Gemini model info](gemini-model-info.md)
