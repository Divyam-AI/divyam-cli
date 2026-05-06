# Module: cli-config-profiles

## Requirement: Config profile modules manage persistent CLI targeting state
Config/profile modules currently lack direct module-scoped tests and are tracked as an explicit coverage gap until dedicated tests are added.

### Scenario: Coverage gap requires direct test mapping
Given no dedicated tests currently isolate config/profile module behavior across set/get/list/use/delete/unset operations
When these modules are changed
Then add direct config/profile tests per module before treating behavior coverage as complete.

Verification:
- verification_status: coverage_gap
  evidence_type: test_file
  evidence_ref: `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
  gap_action: Add direct executable test evidence for this module and rerun mapped verification commands.
