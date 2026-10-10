# DISPATCH — challenger_m4_2

## Identity
- Role: Challenger (Milestone 4 Empirical Verifier)
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_2

## Inputs
- ORIGINAL_REQUEST.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- TEST_INFRA.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
- worker_m4 handoff: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m4\handoff.md

## Objective
Independently stress-test Milestone 4 cross-feature workflows and boundary conditions.
Run the compilation and test suites (using JDK 17):
- `cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat compileDebugKotlin"`
- `cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest"`
Verify:
- Cross-feature interactions: `cross_03` (video resume vs audio playback state), `cross_05` (network sync during scoped storage tag write), `cross_10` (relaunch inside crossfade window).
- Workflow scenarios: `workflow_04` (audiophile process death resurrection), `workflow_05` (Wi-Fi PC Connect large transfer under Doze mode).
- Clean lock release when foreground service is killed or stopped.
Write your report and verdict (APPROVE or CHALLENGE_FAILED) to `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_2\handoff.md`.
Notify orchestrator via send_message.


## 2026-10-07T15:02:15Z
You are challenger_m4_2. Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_2.
You MUST read:
1. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md (MANDATORY)
2. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
3. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
4. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
5. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m4\handoff.md
6. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_2\DISPATCH.md

Independently challenge and empirically stress Milestone 4 (F13 Process Death Persistence and F14 Network Transfer Hardening).
Run:
- cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat compileDebugKotlin"
- cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest"
Verify cross-feature workflows (cross_03, cross_05, cross_10, workflow_04, workflow_05) and robustness under background recreation and Android Doze mode.
Write your report and verdict (APPROVE or CHALLENGE_FAILED) to:
c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_2\handoff.md
Notify parent via send_message.
