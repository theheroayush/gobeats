# Dispatch for worker_m2_r2

- **Role**: Milestone 2 Implementation Worker (Round 2 Remediation)
- **Working Directory**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2_r2
- **Original Request**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- **Project Specifications**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- **Test Baseline**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- **Reviewer Feedback**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\reviewer_m2_2\handoff.md
- **Explorer Fix Blueprints**:
  - Atomic Swap & FLAC Artwork: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_1\handoff.md
  - M4A Non-Faststart & Box Traversal: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_2\handoff.md
  - M4A Metadata Retention & Stream Probe: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_3\handoff.md

## Write Ownership
You exclusively own and may edit:
- `app/src/main/java/com/example/gobeats/core/media/tag/ScopedStorageRouter.kt`
- `app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt`
- `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`
- `app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt`
- `app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt`
- `app/src/main/java/com/example/gobeats/feature/library/TagEditorBottomSheet.kt`
- `app/src/test/java/com/example/gobeats/Milestone2EmpiricalStressTest.kt`

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Objective
Implement all Round 2 remediations for Milestone 2:
1. **Atomic File Replacement**: Add `ScopedStorageRouter.safeAtomicReplace(targetFile, stagingFile)` and use it across `FlacTagWriter`, `M4aTagWriter`, and `Id3TagWriter` (no premature `file.delete()`).
2. **FLAC Artwork Preservation**: Add `removeArtwork: Boolean = false` to `TagEditPayload`, wire `isArtworkRemoved` from `TagEditorBottomSheet.kt`, and update `FlacTagWriter` to use `payload.removeArtwork`.
3. **M4A Non-Faststart Container Preservation**: Detect layout (`moovStartOffset < mdatStartOffset`), preserve container order (`ftyp -> mdat -> updatedMoov` for non-faststart, `ftyp -> updatedMoov -> mdat` for faststart), and compute `mdatDelta = newMdatOffset - mdatStartOffset`.
4. **Structured Box Traversal for `stco`/`co64`**: Implement hierarchical traversal (`moov -> trak -> mdia -> minf -> stbl -> stco/co64`) ignoring `udta/meta/ilst/covr`.
5. **M4A ilst Metadata & Cover Art Preservation**: Implement `parseExistingIlstAtoms` and merge untouched atoms (`©wrt`, `©cmt`, `disk`, `cpil`, `covr`, etc.) into `buildIlstBox`.
6. **UniversalTagWriter Stream Probe**: Replace invalid `openOutputStream(contentUri, "r")` with `openFileDescriptor(contentUri, "rw")?.close()`.
7. **Regression Tests**: Add the non-faststart and false-positive image stress tests to `Milestone2EmpiricalStressTest.kt`.
8. Verify via:
   - `.\gradlew.bat compileDebugKotlin`
   - `.\gradlew.bat testDebugUnitTest`
9. Write completion report to `handoff.md` and notify orchestrator_2.

## 2026-10-06T15:10:28Z
You are worker_m2_r2.
Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2_r2
Read your dispatch assignment: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2_r2\DISPATCH.md
Read the authoritative user request: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
Read project architecture: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
Read test suite status: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
Read Reviewer feedback: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\reviewer_m2_2\handoff.md
Read Explorer fix blueprints:
- c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_1\handoff.md
- c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_2\handoff.md
- c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_3\handoff.md
