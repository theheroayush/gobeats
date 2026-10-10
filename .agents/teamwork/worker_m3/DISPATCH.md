# DISPATCH — worker_m3

## Identity
- Role: Full-Stack Android Implementation Worker (Milestone 3: Audiophile Offline & Folder Browser)
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m3

## Inputs
- ORIGINAL_REQUEST.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- TEST_INFRA.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
- explorer_m3_1 handoff (F10): c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_1\handoff.md
- explorer_m3_2 handoff (F11): c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_2\handoff.md
- explorer_m3_3 handoff (F12): c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_3\handoff.md

## Scope & Owned Files
Milestone 3 Implementation across F10, F11, and F12:
- F10:
  - `app/src/main/java/com/example/gobeats/feature/library/folders/FolderBrowserEngine.kt`
  - `app/src/main/java/com/example/gobeats/feature/library/folders/FolderBrowserRepository.kt`
  - `app/src/main/java/com/example/gobeats/feature/library/folders/FoldersScreen.kt`
  - `app/src/main/java/com/example/gobeats/MainActivity.kt` (Destination.Folders, onFoldersCategoryClick)
  - `app/src/main/java/com/example/gobeats/feature/library/LibraryScreen.kt` (Update Folders category tile)
  - `app/src/main/java/com/example/gobeats/GobeatsApplication.kt` (Expose FolderBrowserRepository)
- F11:
  - `app/src/main/java/com/example/gobeats/core/audio/CrossfadeEngine.kt`
  - `app/src/main/java/com/example/gobeats/core/model/Models.kt` (PlaybackUiState properties)
  - `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` (Crossfade ramping, settings sync, APIs)
  - `app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` (PlaybackRepository APIs)
  - `app/src/main/java/com/example/gobeats/core/media/GobeatsMediaSessionService.kt`
  - `app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (Ensure CrossfadeEngine compatibility)
- F12:
  - `app/src/main/java/com/example/gobeats/core/lyrics/EmbeddedLyricsExtractor.kt` / `Id3TagReader.kt`
  - `app/src/main/java/com/example/gobeats/core/lyrics/LyricsRepository.kt` (DATA path companion discovery, embedded extraction, getOfflineLyrics, priority chain)
  - `app/src/main/java/com/example/gobeats/core/lyrics/LrcParser.kt` (SyncedLyrics typealias)

## Verification
- Must run and pass: `.\gradlew.bat testDebugUnitTest`
- Must run and pass: `.\gradlew.bat compileDebugKotlin`


## 2026-10-06T20:38:07Z
You are worker_m3. Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m3.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

You MUST read the following files first:
1. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md (MANDATORY)
2. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
3. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
4. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md
5. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_1\handoff.md (F10 findings & specifications)
6. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_2\handoff.md (F11 findings & specifications)
7. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_3\handoff.md (F12 findings & specifications)
8. c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m3\DISPATCH.md
