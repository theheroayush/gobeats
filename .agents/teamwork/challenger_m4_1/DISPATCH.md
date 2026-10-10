# DISPATCH — challenger_m4_1

## Identity
- Role: Challenger (Milestone 4 Empirical Verifier)
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_1

## Inputs
- ORIGINAL_REQUEST.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- TEST_INFRA.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
- worker_m4 handoff: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m4\handoff.md

## Objective
Empirically stress-test Milestone 4 (F13 Process Death Persistence and F14 Network Transfer Hardening).
Run the compilation and test suites (using JDK 17):
- `cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat compileDebugKotlin"`
- `cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest"`
Verify:
- Process death queue serialization/deserialization with corrupted inputs, special characters, and empty lists.
- Restored state initializes with paused playback (`playWhenReady = false`).
- WifiLock mode selection on API >= 29 vs < 29, 2-hour WakeLock timeout, and safe lock release on error or cancellation.
- TransferStats progress calculation and zero division protection.
Write your report and verdict (APPROVE or CHALLENGE_FAILED) to `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_1\handoff.md`.
Notify orchestrator via send_message.


## 2026-10-07T15:02:15Z
You are challenger_m4_1. Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_1.
You MUST read:
1. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md (MANDATORY)
2. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
3. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
4. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
5. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m4\handoff.md
6. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_1\DISPATCH.md

Empirically test and stress Milestone 4 (F13 Process Death Persistence and F14 Network Transfer Hardening).
Run:
- cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat compileDebugKotlin"
- cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat testDebugUnitTest"
Verify boundaries: corrupted queue JSON, large queues, negative position clamping, pause state on restoration, WifiLock API level selection, 2h WakeLock timeout, and transfer cancellation lock release.
Write your report and verdict (APPROVE or CHALLENGE_FAILED) to:
c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\challenger_m4_1\handoff.md
Notify parent via send_message.
