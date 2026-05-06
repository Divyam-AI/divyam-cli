# Module: cli-model-pricing

## Requirement: Model pricing module resolves local pricing metadata consistently
The model pricing store currently lacks direct module-scoped tests and is tracked as an explicit coverage gap until dedicated tests are added.

### Scenario: Coverage gap requires direct test mapping
Given no dedicated tests currently isolate `ModelPricingStore.kt` parsing and lookup behavior
When this module is changed
Then add direct pricing-store tests that assert parsing and lookup semantics before treating coverage as complete.

Verification:
- verification_status: coverage_gap
  evidence_type: test_file
  evidence_ref: `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
  gap_action: Add direct executable test evidence for this module and rerun mapped verification commands.
