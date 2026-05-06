# OpenSpec Agent Guidance

## Scope
Use OpenSpec files to capture behavior for CLI-facing capabilities before broad refactors.
Module-level specs under `openspec/specs/modules/<module-id>/spec.md` are the implementation reference for code-level changes.

## Editing rules
- Keep requirement statements behavior-first.
- Prefer concrete scenario examples tied to command handlers under `cli/src/main/kotlin/ai/divyam/cli`.
- Map each scenario verification to an executable test command and path when available.
- If test command coverage cannot be mapped to `artifacts.yaml`, mark `coverage_gap`.

## Verification notes
- Primary test command: `./gradlew :cli:test`
- Primary test path: `cli/src/test/kotlin/ai/divyma/cli/test`

## Strict per-source-file granularity
- Create or update exactly one module spec for each meaningful source file in scope under `openspec/specs/modules/<normalized-file-id>/spec.md`.
- Normalize `<normalized-file-id>` from the repo-relative source file path (lowercase, non-alphanumeric runs => `-`).
- Keep existing umbrella capability specs and README pointers intact; add detail in module specs instead of replacing umbrella files.
- Every scenario verification block must map to executable artifact command(s), concrete test path(s) when available, and an explicit `coverage_gap`.

## Verification normalization semantics
- In each `Verification:` block, use explicit entries with `verification_status`, `evidence_type`, and `evidence_ref`; include `artifact_ref` when sourced from `artifacts.yaml`; include `gap_action` for every `coverage_gap` entry.
- Set `verification_status: mapped` only for direct, executable evidence available now.
- Downgrade indirect or aspirational mappings to `verification_status: coverage_gap` with a concrete, actionable `gap_action`.
