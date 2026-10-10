# BRIEFING — 2026-10-06T15:15:00Z

## Mission
Milestone 2 Round 2 Remediation: Implement atomic file replacement, FLAC artwork preservation, M4A non-faststart container preservation, structured ISO-BMFF box traversal, M4A ilst metadata/artwork preservation, ContentResolver probe fix, and regression tests.

## 🔒 My Identity
- Archetype: worker
- Roles: implementer, qa, specialist
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m2_r2
- Original parent: d086cafa-aeaa-4d54-8b78-806c4faa0b0a
- Milestone: Milestone 2 Round 2 Remediation

## 🔒 Key Constraints
- Write Ownership exclusively restricted to:
  - app/src/main/java/com/example/gobeats/core/media/tag/ScopedStorageRouter.kt
  - app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt
  - app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt
  - app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt
  - app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt
  - app/src/feature/library/TagEditorBottomSheet.kt
  - app/src/test/java/com/example/gobeats/Milestone2EmpiricalStressTest.kt
- Mandatory Integrity Mandate: No cheating, no hardcoded test values, no fake facades.
- Atomic file replacement: Never delete target file before rename/move succeeds.
- Preserve unedited metadata and cover art in FLAC and M4A.

## Current Parent
- Conversation ID: d086cafa-aeaa-4d54-8b78-806c4faa0b0a
- Updated: not yet

## Task Summary
- **What to build**:
  1. `ScopedStorageRouter.safeAtomicReplace(targetFile, stagingFile)` and adopt in `FlacTagWriter`, `M4aTagWriter`, `Id3TagWriter`.
  2. `TagEditPayload.removeArtwork` parameter wired from `TagEditorBottomSheet` and used in `FlacTagWriter`.
  3. M4A non-faststart layout detection, container order preservation, dynamic `mdatDelta` calculation.
  4. Structured hierarchical ISO-BMFF box traversal (`moov -> trak -> mdia -> minf -> stbl -> stco/co64`).
  5. M4A existing `ilst` atoms parsing and merging (preserve `covr`, `©wrt`, `©cmt`, `disk`, `cpil`, etc.).
  6. Fix `openOutputStream(contentUri, "r")` with `openFileDescriptor(contentUri, "rw")?.close()`.
  7. Add comprehensive regression tests in `Milestone2EmpiricalStressTest.kt`.
- **Success criteria**:
  - `.\gradlew.bat compileDebugKotlin` succeeds.
  - `.\gradlew.bat testDebugUnitTest` passes 100%.
- **Interface contracts**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md § Interface Contracts
- **Code layout**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md § Code Layout

## Key Decisions Made
- Use Files.move with REPLACE_EXISTING and ATOMIC_MOVE (API 26+) falling back to transactional backup-and-restore.
- Keep non-faststart M4A in original container order (ftyp -> mdat -> updatedMoov), keeping stco offsets identical.
- Descend strict box hierarchy (moov -> trak -> mdia -> minf -> stbl) ignoring udta.
- Extract existing ilst atoms map and merge in buildIlstBox.

## Artifact Index
- handoff.md — will be generated upon completion

## Change Tracker
- **Files modified**: pending implementation
- **Build status**: baseline running
- **Pending issues**: none

## Quality Status
- **Build/test result**: baseline in progress
- **Lint status**: clean
- **Tests added/modified**: pending implementation

## Loaded Skills
- None loaded
