# Module: cli-entity-command-groups

## Requirement: Entity command modules provide stable CRUD routing and output
Entity command group modules currently lack direct module-scoped tests and are tracked as an explicit coverage gap until dedicated tests are added.

### Scenario: Coverage gap requires direct test mapping
Given no dedicated tests currently isolate org/user/sa/model/selector/eval command group module behavior
When these modules are changed
Then add direct command-group tests per module before treating behavior coverage as complete.

Verification:
- verification_status: coverage_gap
  evidence_type: test_file
  evidence_ref: `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
  gap_action: Add direct executable test evidence for this module and rerun mapped verification commands.
