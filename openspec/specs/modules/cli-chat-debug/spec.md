# Module: cli-chat-debug

## Requirement: Chat/debug modules expose diagnostic workflows safely
Chat/debug modules currently lack direct module-scoped tests and are tracked as an explicit coverage gap until dedicated tests are added.

### Scenario: Coverage gap requires direct test mapping
Given no dedicated tests currently isolate `DebugCommand.kt`, `ChatDebug.kt`, and `ChatCommand.kt` behavior
When these modules are changed
Then add direct chat/debug tests that assert diagnostic invocation semantics before treating coverage as complete.

Verification:
- verification_status: coverage_gap
  evidence_type: test_file
  evidence_ref: `cli/src/test/kotlin/ai/divyma/cli/test/DivyamCliTest.kt`
  gap_action: Add direct executable test evidence for this module and rerun mapped verification commands.
