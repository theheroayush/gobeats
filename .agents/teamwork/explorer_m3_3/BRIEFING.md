# BRIEFING — 2026-10-06T20:27:48Z

## Mission
Investigate F12: Offline synced lyrics fallback (companion <song>.lrc auto-load via DATA path and embedded ID3 USLT/SYLT decoding before remote LRCLIB).

## 🔒 My Identity
- Archetype: explorer
- Roles: Codebase Explorer, Synthesizer
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_3
- Original parent: a201626e-f29e-4e6e-aae8-5e44b7075707
- Milestone: M3 Feature Investigation (F12)

## 🔒 Key Constraints
- Read-only investigation — do NOT implement
- Investigate offline synced lyrics fallback mechanism, tests, and gaps

## Current Parent
- Conversation ID: a201626e-f29e-4e6e-aae8-5e44b7075707
- Updated: 2026-10-06T20:38:00Z

## Investigation State
- **Explored paths**:
  - `LyricsRepository.kt`, `LrcParser.kt`, `LrclibClient.kt`
  - `SyncedLyricsView.kt`, `NowPlayingBottomSheet.kt`
  - `Id3TagWriter.kt`, `FlacTagWriter.kt`, `M4aTagWriter.kt`, `UniversalTagWriter.kt`
  - `SubtitleDetector.kt`, `ScopedStorageRouter.kt`, `MediaScanner.kt`
  - `GobeatsTier1FeatureCoverageTest.kt` (`f12_01`–`f12_05`, `OfflineLyricsResolver`)
  - `GobeatsTier2BoundaryCornerTest.kt` (`f12_b01`–`f12_b05`)
  - `GobeatsTier3CrossFeatureTest.kt` (`cross_09`)
  - `GobeatsTier4RealWorldWorkflowsTest.kt` (`workflow_02`)
- **Key findings**:
  1. `LyricsRepository.kt` only checks `uri.scheme == "file"` for companion `.lrc`, missing MediaStore content URIs (`content://...`) which require `MediaStore.MediaColumns.DATA` path query.
  2. Embedded lyrics parsing in `LyricsRepository` uses `METADATA_KEY_COMPILATION` fallback, completely missing real ID3 `USLT` / `SYLT` decoding.
  3. No `Id3TagReader.kt` exists in the codebase (`Id3TagWriter.kt` only writes).
  4. Contract `getOfflineLyrics(track: Track): SyncedLyrics?` from `PROJECT.md` is missing in `LyricsRepository`.
  5. Fallback priority order verified across tests: Cache -> Companion `.lrc` -> Embedded ID3/Vorbis/M4A -> Remote LRCLIB -> None.
- **Unexplored areas**: None. Ready for synthesis and handoff.

## Key Decisions Made
- Architected new `Id3TagReader.kt` with binary decoding for ID3v2.3/v2.4 headers, `USLT` text frames, and `SYLT` synchronized event frames.
- Designed `resolveFilePath` using `MediaStore.MediaColumns.DATA` for robust companion `.lrc` discovery across all Android URI schemes.
- Designed `getOfflineLyrics(track: Track)` in `LyricsRepository` matching `PROJECT.md` contracts.
- Kept `GobeatsTier1FeatureCoverageTest.OfflineLyricsResolver` semantics intact so 100% of existing tests remain green.

## Artifact Index
- handoff.md — Final 5-component handoff report
- progress.md — Heartbeat and execution progress
