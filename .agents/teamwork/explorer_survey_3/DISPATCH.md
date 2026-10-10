# Task Assignment: Explorer Survey 3 (Audiophile Offline, Process Death, Network, Build)

## Objective
Survey GoBeats codebase focusing on Audiophile Offline Experience, Folder Browser, Process Death, Network Hardening, and Build/Test Setup for R3, R4, and Verification.

## Scope & Guidelines
- Read `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md`.
- Investigate:
  1. Library & Folder Browser: Current Library UI tabs, storage hierarchy traversal, direct folder playback.
  2. Audiophile playback: Gapless playback in ExoPlayer/Media3, crossfade implementation options (dual player vs AudioProcessor vs volume ramp), lyrics support (current LRCLIB fetching, embedded USLT/SYLT ID3 tag parsing, local `.lrc` file detection and parsing).
  3. Process Death & State Restoration: Current Room/SQLite schema, player state / queue persistence, SavedStateHandle / lifecycle restoration.
  4. Network Hardening: Wi-Fi PC Connect transfer architecture, WifiLock acquisition, foreground service notification.
  5. Build & Verification setup: Gradle version, Kotlin version, Android SDK, existing unit tests, gradle tasks (`compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`).
- Identify all relevant files, classes, dependencies, extension points, and potential pitfalls.
- Produce a detailed survey report at `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_3\survey_report.md` and deliver `handoff.md`.

## 2026-10-05T20:59:00Z
From: orchestrator_1 (0a34afd4-fc8e-48e4-a3d6-ca63de92c85e)
You are the Offline & Platform Explorer (survey round).
Your assigned working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_3
Your parent is the Project Orchestrator (orchestrator_1, conversation ID: 0a34afd4-fc8e-48e4-a3d6-ca63de92c85e).

Mandatory instructions:
1. Read c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md.
2. Read your task assignment in c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_3\DISPATCH.md.
3. Explore the codebase using find_by_name, grep_search, view_file. Note: you are read-only.
4. Focus specifically on:
   - R3: Audiophile Offline Experience & Folder Browser:
     * Folders directory tree tab in Library screen (navigation, playing folders as instant playlists).
     * Gapless playback & configurable 1-5s crossfade between queue items.
     * Offline synced lyrics: embedded USLT/SYLT ID3 lyrics and local <song>.lrc files before LRCLIB.
   - R4: Process Death & Network Hardening:
     * Room/SQLite persistence of active queue track IDs and millisecond playback position across process death.
     * WifiLock and foreground service with ongoing notification for Wi-Fi PC Connect transfers.
   - Verification:
     * Gradle setup, Android SDK, existing tests, how `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` are configured.
5. Write your comprehensive analysis report to c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_3\survey_report.md.
6. Write handoff.md in your working directory following the Handoff Protocol.
7. Send a message to parent (0a34afd4-fc8e-48e4-a3d6-ca63de92c85e) with your completion status and path to survey_report.md.
