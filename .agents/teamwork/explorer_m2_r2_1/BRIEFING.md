# BRIEFING — 2026-10-06T15:08:00Z

## Mission
Formulate concrete fix strategy for:
1. Atomic file replacement (safe backup-and-restore or atomic move) in FlacTagWriter and M4aTagWriter to eliminate data loss risks.
2. Preserving existing FLAC embedded artwork on text-only tag edits.

## 🔒 My Identity
- Archetype: explorer
- Roles: Codebase & Spec Explorer (Iteration 2)
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_r2_1
- Original parent: d086cafa-aeaa-4d54-8b78-806c4faa0b0a
- Milestone: Milestone 2 (Features F6, F7, F8, F9) - Iteration 2

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Produce structured handoff report in handoff.md
- Communicate with orchestrator_2 (d086cafa-aeaa-4d54-8b78-806c4faa0b0a) via send_message

## Current Parent
- Conversation ID: d086cafa-aeaa-4d54-8b78-806c4faa0b0a
- Updated: 2026-10-06T14:55:00Z

## Investigation State
- **Explored paths**:
  - `FlacTagWriter.kt` (lines 240–361): Analyzed `writeTagsToStreams` block filtering and `writeTagsToFlacFile` staging/delete logic.
  - `M4aTagWriter.kt` (lines 140–265): Analyzed box scanning, staging file creation, `raf` closure, and staging swap.
  - `Id3TagWriter.kt` (lines 15–25, 174–217): Analyzed `TagEditPayload` declaration and MP3 file replacement logic.
  - `TagEditorBottomSheet.kt` (lines 104–130, 253–275, 345–365): Analyzed UI artwork state management (`isArtworkRemoved`, `selectedArtworkBytes`) and payload creation.
  - `UniversalTagWriter.kt` (lines 30–175): Traced payload propagation and staging file handoff.
  - `ScopedStorageRouter.kt`: Analyzed existing staging validation and scoped storage routing.
  - `GobeatsDatabaseHelper.kt` (lines 843–900): Checked SQLite database override behavior on null vs non-null artwork URI.
  - `FlacVorbisScopedStorageStressTest.kt` & `Milestone2EmpiricalStressTest.kt`: Checked test coverage of FLAC picture blocks and file writing.
- **Key findings**:
  - `file.delete()` then `tempFile.renameTo(file)` is used in `FlacTagWriter.kt`, `M4aTagWriter.kt`, and `Id3TagWriter.kt`, creating catastrophic data loss vulnerability if rename fails or process dies.
  - `FlacTagWriter.kt` line 332 (`removeArtwork = payload.artworkUri == null && payload.newArtworkBytes == null`) unconditionally purges existing FLAC picture blocks whenever a user edits text tags on a track lacking a cached/custom `artworkUri`.
  - Adding `val removeArtwork: Boolean = false` to `TagEditPayload` and centralizing `ScopedStorageRouter.safeAtomicReplace` cleanly resolves both issues across the codebase with 0 breaking changes.
- **Unexplored areas**: None within the scope of Assignment 1 and 2.

## Key Decisions Made
- Centralize atomic replacement in `ScopedStorageRouter.safeAtomicReplace(targetFile: File, stagingFile: File): Boolean` using a two-tier strategy: `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)` on API 26+/JVM, backed by transactional backup-and-restore (`target -> target.bak` -> `staging -> target` -> delete backup, with automatic rollback on failure).
- Update `TagEditPayload` with `val removeArtwork: Boolean = false`.
- Wire `isArtworkRemoved` from `TagEditorBottomSheet.kt` to `TagEditPayload(..., removeArtwork = isArtworkRemoved)`.
- In `FlacTagWriter.writeTagsToFlacFile`: use `removeArtwork = payload.removeArtwork` instead of inferring from null URIs.
- Detailed design and implementation blueprint written to `handoff.md`.

## Artifact Index
- DISPATCH.md — Task assignment and incoming messages
- BRIEFING.md — Persistent working memory
- progress.md — Liveness heartbeat
- handoff.md — Final investigation report
