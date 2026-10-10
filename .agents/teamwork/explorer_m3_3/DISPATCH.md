# DISPATCH — explorer_m3_3

## Identity
- Role: Codebase Explorer (F12 Focus: Offline Synced Lyrics Fallback)
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_3

## Inputs
- ORIGINAL_REQUEST.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md
- PROJECT.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md
- TEST_READY.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_READY.md
- TEST_INFRA.md: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\TEST_INFRA.md

## Objective
Investigate F12: Offline synced lyrics fallback (companion <song>.lrc auto-load via DATA path and embedded ID3 USLT/SYLT decoding before remote LRCLIB).
- Inspect `LyricsRepository.kt`, `LyricsViewModel.kt`, `NowPlayingScreen.kt`, `Id3TagReader`/`Id3TagWriter`.
- Check how local companion `<song>.lrc` file is located from track `_data` or URI path.
- Check how embedded ID3 `USLT` (unsynced) and `SYLT` (synced) lyrics frames are parsed in MP3/FLAC/M4A files.
- Inspect the fallback order: embedded ID3 -> companion `.lrc` file -> remote LRCLIB.
- Inspect existing test assertions for F12 in `GobeatsTier1FeatureCoverageTest.kt` (`f12_01`–`f12_05`), Tier 2, 3, 4.
- Produce a detailed technical handoff report in `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_3\handoff.md`.

## 2026-10-06T20:27:48Z
You are explorer_m3_3. Your working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m3_3.
Focus is Feature F12: Offline synced lyrics fallback (companion <song>.lrc auto-load via DATA path and embedded ID3 USLT/SYLT decoding before remote LRCLIB).
Investigate:
- `LyricsRepository.kt`, `LyricsViewModel.kt`, `NowPlayingScreen.kt`, ID3 tag decoding in `com.example.gobeats.core.media.tag` or media decoders.
- Companion `<song>.lrc` file auto-discovery via track `_data` or URI file path.
- Embedded ID3 `USLT` (unsynchronized lyrics) and `SYLT` (synchronized lyrics) extraction and parsing in MP3/FLAC/M4A.
- Fallback resolution order: embedded ID3 -> companion `.lrc` -> remote LRCLIB.
- Existing test assertions in `GobeatsTier1FeatureCoverageTest.kt` (`f12_01`–`f12_05`), Tier 2, Tier 3, Tier 4.
- Determine existing gaps, required files to create/modify, and step-by-step implementation recommendations for the worker.
