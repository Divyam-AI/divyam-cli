# Capability: Config profiles

## Purpose
Define CLI profile configuration behavior so operators can target environments consistently across commands.

## Requirements
### Requirement: CLI config management supports profile lifecycle
The CLI MUST let operators set, get, list, select, and remove config values for repeatable environment targeting.

#### Scenario: Config commands manage profile state for subsequent requests
- **GIVEN** config command handlers in `cli/src/main/kotlin/ai/divyam/cli/config`
- **WHEN** a user runs `config set/use/get/list/delete/unset` flows
- **THEN** active profile and associated values are persisted and retrievable for later command execution.

Verification:
- command: `./gradlew :cli:test`
- test_paths:
  - `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
- coverage_gap: no `artifacts.yaml` command mapping found for CLI tests.
