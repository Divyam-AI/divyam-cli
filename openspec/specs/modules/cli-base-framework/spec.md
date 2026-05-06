# Module: cli-base-framework

## Requirement: Base command framework enforces shared command contract behavior
Base framework modules currently lack direct module-scoped tests and are tracked as an explicit coverage gap until dedicated tests are added.

### Scenario: Coverage gap requires direct test mapping
Given no dedicated tests currently isolate behavior in `BaseCommand.kt`, `BaseSubCommand.kt`, `HasSecurityPolicy.kt`, and `SaSpecificCommand.kt`
When these modules are changed
Then add direct base-framework tests before treating behavior coverage as complete.

Verification:
- verification_status: coverage_gap
  evidence_type: test_file
  evidence_ref: `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
  gap_action: Add direct executable test evidence for this module and rerun mapped verification commands.
