# Capability: CLI commands

## Purpose
Define stable CLI command-group behavior for CRUD-style operations across core entities.

## Requirements
### Requirement: Command groups expose stable CRUD interactions
The CLI MUST provide predictable create/get/list/update/delete style operations for core entities (org, user, service account, model selector, model info, eval).

#### Scenario: Entity subcommands execute through dedicated command handlers
- **GIVEN** command handlers under `cli/src/main/kotlin/ai/divyam/cli/*`
- **WHEN** a user invokes an entity command group
- **THEN** the invocation is routed through the matching handler class and produces structured CLI output.

Verification:
- command: `./gradlew :cli:test`
- test_paths:
  - `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
- coverage_gap: `artifacts.yaml` mapping missing in repository root.
