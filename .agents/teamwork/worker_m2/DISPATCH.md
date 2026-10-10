# Dispatch for worker_m2

- **Role**: Milestone 2 Implementation Worker
- **Working Directory**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2
- **Original Request**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- **Project Specifications**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- **Test Baseline**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- **Explorer Reports**:
  - Scoped Storage: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_1\handoff.md
  - FLAC Vorbis: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_2\handoff.md
  - M4A & Tag Sync: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3\handoff.md

## Write Ownership
You exclusively own and may create/edit these files:
- `app/src/main/java/com/example/gobeats/core/media/tag/ScopedStorageRouter.kt`
- `app/src/main/java/com/example/gobeats/core/media/tag/ScopedStoragePermissionRequiredException.kt`
- `app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt`
- `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`
- `app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt`
- `app/src/main/java/com/example/gobeats/data/repository/Repositories.kt`
- `app/src/main/java/com/example/gobeats/feature/library/TagEditorBottomSheet.kt`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Objective
Implement Milestone 2: Scoped Storage & Universal Tag Editor:
1. Feature F6: Scoped Storage Hardening for Android 10–14 (API 29–34), MediaStore.createWriteRequest, ScopedStorageRouter, and staging file validation.
2. Feature F7: FLAC Vorbis Tag Editor & Picture Block Writer (`FlacTagWriter.kt`) with bit-perfect audio stream preservation.
3. Feature F8: M4A/MP4 Tag Editor & stco/co64 chunk offset delta realignment (`M4aTagWriter.kt`).
4. Feature F9: Tag Sync Pipeline (`UniversalTagWriter.kt`) connecting raw file, MediaStore columns, MediaScannerConnection rescan, database overrides, and live PlaybackConnection session.
5. Wire `UniversalTagWriter` into `Repositories.kt` (`LibraryRepository.updateTrackMetadata`) and ensure `TagEditorBottomSheet.kt` is clean and fully connected.
6. Verify implementation by running:
   - `.\gradlew.bat compileDebugKotlin`
   - `.\gradlew.bat testDebugUnitTest`
7. Produce a comprehensive completion report in `handoff.md` and notify orchestrator_2.

## 2026-10-06T14:18:33Z
You are worker_m2.
Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2
Read your dispatch assignment: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2\DISPATCH.md
Read the authoritative user request: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
Read project architecture: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
Read test suite status: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
Read Explorer Handoffs:
- c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_1\handoff.md
- c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_2\handoff.md
- c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3\handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Write Ownership:
You exclusively own and may create/edit these files:
- app/src/main/java/com/example/gobeats/core/media/tag/ScopedStorageRouter.kt
- app/src/main/java/com/example/gobeats/core/media/tag/ScopedStoragePermissionRequiredException.kt
- app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt
- app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt
- app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt
- app/src/main/java/com/example/gobeats/data/repository/Repositories.kt
- app/src/main/java/com/example/gobeats/feature/library/TagEditorBottomSheet.kt

Mission:
Implement Milestone 2: Scoped Storage & Universal Tag Editor (F6, F7, F8, F9).
1. Implement ScopedStorageRouter and ScopedStoragePermissionRequiredException.
2. Implement FlacTagWriter with Vorbis comment and picture block building, parsing, and bit-perfect raw audio frame preservation.
3. Implement M4aTagWriter with atom building (©nam, ©ART, ©alb, ©day, ©gen, trkn, covr), stco/co64 chunk offset delta recalculation, and bit-perfect mdat streaming.
4. Implement UniversalTagWriter with full 4-stage synchronization pipeline (raw file, MediaStore, scanner & db overrides, live playback session).
5. Integrate with Repositories.kt (LibraryRepository.updateTrackMetadata) and TagEditorBottomSheet.kt.
6. Verify your implementation by running:
   .\gradlew.bat compileDebugKotlin
   .\gradlew.bat testDebugUnitTest
   Document commands and test results verbatim.
7. Write your completion report to c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2\handoff.md and notify orchestrator_2.
