# Dispatch Assignment: E2E Test Writer

## 2026-10-06T07:13:52Z
You are the E2E Test Writer.
Your assigned working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\test_writer_e2e
Your parent is the Project Orchestrator (orchestrator_1, conversation ID: 0a34afd4-fc8e-48e4-a3d6-ca63de92c85e).

Mandatory instructions:
1. Read c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md.
2. Read your task assignment in c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\test_writer_e2e\DISPATCH.md.
3. Read c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md.
4. Design and implement comprehensive tests across all 4 tiers (Feature Coverage >=5 per feature, Boundary/Corner >=5 per feature, Cross-Feature Pairwise, Real-World Workflows).
5. Output c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md and test suites under app/src/test/java/com/example/gobeats/.
6. Publish c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md when tests are ready and passing.
7. Run and verify .\gradlew.bat testDebugUnitTest.
8. Write handoff.md in your working directory and message parent (0a34afd4-fc8e-48e4-a3d6-ca63de92c85e) when ready.

## Objective
Design and implement the comprehensive, opaque-box E2E testing suite per ORIGINAL_REQUEST.md and PROJECT.md, and publish TEST_READY.md.

## Reference Documents
- `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md` (Read this first!)
- `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md`

## Methodology & Requirements
Design test cases using the systematic 4-tier approach covering ALL features in PROJECT.md § Feature Inventory (F1 through F14):
- **Tier 1: Feature Coverage (>=5 per feature)**: Isolated functional verification for each feature (F1-F14).
- **Tier 2: Boundary & Corner Cases (>=5 per feature)**: Extreme values, zero/negative, empty inputs, max limits, format oddities.
- **Tier 3: Cross-Feature Interactions (Pairwise combinations)**: Interaction between video resume and queue persistence, tag editing and library refresh, folder browsing and playback, etc.
- **Tier 4: Real-World Scenarios (>=5 application workflows)**: Complete end-to-end user workflows (e.g. importing folder -> reading lyrics -> editing tags -> playing with crossfade -> process death recovery).

## Deliverables
1. `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md` summarizing the test architecture and coverage plan.
2. Comprehensive unit/integration test files in `app/src/test/java/com/example/gobeats/` (e.g. `GobeatsE2ETest.kt` or modular test suites).
3. `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md` containing the test runner command and full coverage matrix.
4. Verify by running `.\gradlew.bat testDebugUnitTest` and ensure all tests pass.
5. Report in `handoff.md`.
