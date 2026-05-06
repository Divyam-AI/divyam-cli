# Common Patterns

## Requirement style
- Start with a user-visible behavior.
- Keep implementation details in scenario notes, not requirement titles.

## Scenario format
- `Scenario:` short behavior statement
- `Given/When/Then:` expected flow
- `Verification:` map to command + path
- `coverage_gap:` add when mapping is incomplete

## Verification template
- command: `<artifact-command>`
- test_paths:
  - `<path>`
- coverage_gap: `<none|reason>`
