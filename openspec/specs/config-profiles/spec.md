# Capability: Config profiles

## Requirement: CLI config management supports profile lifecycle
The CLI MUST let operators set, get, list, select, and remove config values for repeatable environment targeting.

### Scenario: Config commands manage profile state for subsequent requests
Given config command handlers in `cli/src/main/kotlin/ai/divyam/cli/config`  
When a user runs `config set/use/get/list/delete/unset` flows  
Then active profile and associated values are persisted and retrievable for later command execution.

Verification:
- command: `./gradlew :cli:test`
- test_paths:
  - `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
- coverage_gap: no `artifacts.yaml` command mapping found for CLI tests.
