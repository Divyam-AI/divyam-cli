# Project: divyam-cli

## Purpose
Command-line interface for managing Divyam control-plane entities and validating deployments.

## Tech and layout
- Kotlin CLI code under `cli/src/main/kotlin/ai/divyam/cli`
- Tests under `cli/src/test/kotlin/ai/divyma/cli/test`
- Gradle build via `build.gradle.kts` and module-level `cli/build.gradle.kts`

## OpenSpec usage
- Capability specs live in `openspec/specs/<capability>/spec.md`
- Module-level implementation reference specs live in `openspec/specs/modules/<module-id>/spec.md`
- Cross-cutting writing conventions live in `openspec/specs/templates/common-patterns.md`
- Capability specs remain umbrella behavior statements; module specs are the implementation-facing reference.

## Verification artifacts
- `coverage_gap`: no root `artifacts.yaml` was found in this repository at onboarding time.
- Current executable test artifact: `./gradlew :cli:test`

## Per-source-file module specs policy
- Wave 1B and later MUST maintain one module spec per meaningful source file under the defined primary roots.
- Required location format: `openspec/specs/modules/<normalized-file-id>/spec.md`.
- `<normalized-file-id>` MUST be generated from the repository-relative source file path by lowercasing and replacing non-alphanumeric separators with `-` (including extension token).
- Existing capability specs remain umbrella behavior specs and MUST be retained; they do not replace per-file module specs.
- Each per-file spec MUST include Requirement/Scenario plus verification mapping to test paths, artifact commands, and a `coverage_gap` note when direct coverage is absent.
