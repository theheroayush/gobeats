# Dispatch for explorer_m2_3

- **Role**: Codebase & Spec Explorer
- **Working Directory**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3
- **Original Request**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- **Project Specifications**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- **Test Baseline**: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md

## Mission
Investigate Milestone 2: Feature F8 (M4A/MP4 Tag Editor & stco Chunk Offsets) and Feature F9 (Tag Sync Pipeline).
Analyze:
1. MP4 / ISO Base Media File Format box structure: `ftyp`, `moov`, `udta`, `meta`, `ilst` atom hierarchy.
2. M4A atom keys: `©nam` (title), `©ART` (artist), `©alb` (album), `©day` (year), `©gen` / `gnre` (genre), `trkn` (track number), `covr` (artwork).
3. Chunk offset tables: `stco` (32-bit offsets) and `co64` (64-bit offsets) in `moov.trak.mdia.minf.stbl`. Calculating `moov` length delta and adjusting chunk offsets pointing to `mdat` samples to guarantee zero audio frame corruption.
4. Feature F9: Tag Sync Pipeline across raw file writing, MediaStore ContentValues update, MediaScannerConnection rescan, and active PlaybackConnection session notification.
5. Review existing tests in `app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (lines 771-973) and Tier 2/3/4 tests.
6. Formulate concrete implementation recommendations for `M4aTagWriter.kt` and `UniversalTagWriter.kt` and write handoff report to `handoff.md`.


## 2026-10-06T14:08:43Z
You are explorer_m2_3.
Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3
Read your dispatch assignment: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3\DISPATCH.md
Read the authoritative user request: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
Read project architecture: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
Read test suite status: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md

Mission: Investigate Milestone 2 Feature F8 (M4A/MP4 Tag Editor & stco Chunk Offsets) and Feature F9 (Tag Sync Pipeline).
Specifically examine:
- MP4 / ISO BMFF atom structure (`ftyp`, `moov`, `udta`, `meta`, `ilst` atom hierarchy).
- M4A atom keys (`©nam`, `©ART`, `©alb`, `©day`, `©gen`/`gnre`, `trkn`, `covr`).
- Chunk offset recalculation: adjusting `stco` / `co64` offsets in `moov.trak.mdia.minf.stbl` based on `moov` size delta when pointing into `mdat`.
- Feature F9 Tag Sync Pipeline: synchronizing raw file, MediaStore content values, MediaScannerConnection, and active PlaybackConnection session.
- Tests in `app/src/test/java/com/example/gobeats/` (GobeatsTier1FeatureCoverageTest.kt lines 771-973, Tier 2/3/4 tests).
- Design of `M4aTagWriter.kt` and `UniversalTagWriter.kt` in `app/src/main/java/com/example/gobeats/core/media/`.

Do NOT implement changes directly. Produce a thorough architectural and technical recommendation report in `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3\handoff.md` and send a completion message back to orchestrator_2.
