# DISPATCH — worker_m4

## Identity
- Role: Full-Stack Android Implementation Worker (Milestone 4: Process Death & Network Hardening)
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m4

## Inputs
- ORIGINAL_REQUEST.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- TEST_INFRA.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
- explorer_m4_1 handoff (F13): c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m4_1\handoff.md
- explorer_m4_2 handoff (F14): c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m4_2\handoff.md
- explorer_m4_3 handoff (Integration): c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m4_3\handoff.md

## Scope & Owned Files
Milestone 4 Implementation across F13 and F14:
- F13:
  - `app/src/main/java/com/example/gobeats/core/model/SavedPlaybackState.kt` (or inside Models.kt)
  - `app/src/main/java/com/example/gobeats/core/playback/ProcessDeathPersistenceEngine.kt`
  - `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt` (savePlaybackState, getPlaybackState, clearPlaybackState, getTracksByIds, onUpgrade table creation)
  - `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` (restore state on controller connect with playWhenReady = false, immediate state saves on pause/seek/transition/queue-mutations/release, throttled saves during playback)
  - `app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` (PlaybackRepository support)
  - `app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (Ensure ProcessDeathPersistenceEngine test backward compatibility)
- F14:
  - `app/src/main/AndroidManifest.xml` (FOREGROUND_SERVICE_DATA_SYNC permission, WAKE_LOCK permission, GobeatsSyncService declaration with foregroundServiceType="dataSync")
  - `app/src/main/java/com/example/gobeats/core/sync/TransferStats.kt`
  - `app/src/main/java/com/example/gobeats/core/sync/SyncLockPolicy.kt`
  - `app/src/main/java/com/example/gobeats/core/sync/GobeatsSyncService.kt` (Foreground Service, ongoing notification, WifiLock, WakeLock with 7,200,000ms timeout)
  - `app/src/main/java/com/example/gobeats/core/sync/GobeatsSyncServer.kt` (Throughput tracking, integration)
  - `app/src/main/java/com/example/gobeats/feature/devices/DevicesScreen.kt` (Route sync server lifecycle through service)
  - `app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (Ensure SyncLockPolicy and TransferStats test backward compatibility)

## Environment Note
- Use JDK 17 toolchain at `C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot` when running Gradle commands.
- Example: `cmd.exe /c "set JAVA_HOME=C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot&& .\gradlew.bat compileDebugKotlin"`

## Verification
- Must run and pass: `.\gradlew.bat compileDebugKotlin`
- Must run and pass: `.\gradlew.bat testDebugUnitTest` (100% pass rate)

## 2026-10-07T14:41:38Z
You are worker_m4. Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m4.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your mission: Implement Milestone 4 (Process Death & Network Hardening) across Features F13 and F14.
