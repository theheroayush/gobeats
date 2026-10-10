# Project: GoBeats Tier-1 Production Enhancements

## Architecture
GoBeats is an offline & streaming music and video player for Android built with Jetpack Compose, Media3 (ExoPlayer), Room/SQLiteOpenHelper, and Kotlin Coroutines.
- **Audio/Video Playback**: Media3 `MediaController` connects to `GobeatsMediaSessionService`. Video rendering is handled via Compose `AndroidView(PlayerView)` in `NowPlayingBottomSheet` and the new `FloatingVideoMiniplayer`.
- **Database Layer**: `GobeatsDatabaseHelper` manages SQLite tables for tracks, playlists, favorites, play count, playback state, and video resume positions.
- **Tag Editing**: `UniversalTagWriter` routes requests to `Id3TagWriter` (MP3 ID3v2.3), `FlacTagWriter` (FLAC Vorbis comments), and `M4aTagWriter` (M4A `ilst` atoms & `stco` offset delta adjustment) with staging files to guarantee audio frame safety. `MediaStore.createWriteRequest` handles Android 10–14 Scoped Storage permissions.
- **Library & Navigation**: Jetpack Compose navigation (`Destination`) manages screens. A new `FoldersScreen` displays directory trees and generates instant queues.
- **Network Sync & Services**: `GobeatsSyncService` runs as a Foreground Service with `FOREGROUND_SERVICE_DATA_SYNC`, `WifiLock`, and `WakeLock` to ensure Wi-Fi PC Connect transfers survive Android Doze mode.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | F1: 200% Loudness Booster | Audio boost up to 200% via LoudnessEnhancer & gesture drag in VideoGestureOverlay | M1 | ORIGINAL_REQUEST §R1 |
| 2 | F2: External Subtitles | Auto-detect sibling .srt/.vtt and SAF file picker integration into MediaItem SubtitleConfiguration | M1 | ORIGINAL_REQUEST §R1 |
| 3 | F3: Video Resume Memory | Save playback position per video ID in SQLite and prompt auto-resume on open | M1 | ORIGINAL_REQUEST §R1 |
| 4 | F4: Black-Screen Trap Fix | Disable/clear invalid quality constraints on single-rendition local files | M1 | ORIGINAL_REQUEST §R1 |
| 5 | F5: Floating Video Miniplayer | In-app draggable/floating video miniplayer overlay across library screens | M1 | ORIGINAL_REQUEST §R1 |
| 6 | F6: Scoped Storage Hardening | MediaStore.createWriteRequest with ActivityResultLauncher for Android 10–14 | M2 | ORIGINAL_REQUEST §R2 |
| 7 | F7: FLAC Vorbis Tag Editor | Read/write FLAC Vorbis comments and picture blocks without corrupting audio stream | M2 | ORIGINAL_REQUEST §R2 |
| 8 | F8: M4A/MP4 Tag Editor | Read/write M4A ilst atoms and adjust stco/co64 chunk offsets to avoid frame corruption | M2 | ORIGINAL_REQUEST §R2 |
| 9 | F9: Tag Sync Pipeline | Synchronize raw file, MediaStore columns, MediaScannerConnection, and active playback | M2 | ORIGINAL_REQUEST §R2 |
| 10 | F10: Folders Directory Tree | Dedicated Folders browser tab in Library with breadcrumbs and instant folder queue | M3 | ORIGINAL_REQUEST §R3 |
| 11 | F11: Audiophile Gapless & Crossfade | Gapless transitions and configurable 1–5s crossfade between queue items in PlaybackConnection | M3 | ORIGINAL_REQUEST §R3 |
| 12 | F12: Offline Synced Lyrics | Companion <song>.lrc auto-load via DATA path and embedded ID3 USLT/SYLT decoding | M3 | ORIGINAL_REQUEST §R3 |
| 13 | F13: Process Death Persistence | Save active queue and millisecond position in SQLite playback_state and restore on launch | M4 | ORIGINAL_REQUEST §R4 |
| 14 | F14: Network Transfer Hardening | GobeatsSyncService foreground service, ongoing notification, WifiLock, and WakeLock | M4 | ORIGINAL_REQUEST §R4 |
| 15 | F15: E2E Verification Test Suite | 4-tier E2E unit & regression test suite and full clean Gradle build verification | E2E | ORIGINAL_REQUEST §Verification |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| M1 | Video Engine Enhancements | F1, F2, F3, F4, F5 | none | DONE |
| M2 | Scoped Storage & Universal Tag Editor | F6, F7, F8, F9 | none | DONE |
| M3 | Audiophile Offline & Folder Browser | F10, F11, F12 | none | DONE |
| M4 | Process Death & Network Hardening | F13, F14 | none | DONE |
| E2E | E2E Testing Track | F15 (Tiers 1-4 test suite, TEST_READY.md) | none | DONE |
| M5 | Final Integration & Full Verification | Pass 100% E2E tests, Tier 5 adversarial hardening, assembleDebug | M1, M2, M3, M4, E2E | DONE |

## Interface Contracts

### M1: Video Playback & Subtitle Contracts
- `VideoResumeManager`:
  - `savePosition(videoId: String, positionMs: Long, durationMs: Long)`
  - `getPosition(videoId: String): Long?`
- `SubtitleManager`:
  - `findCompanionSubtitles(context: Context, videoUri: Uri): List<SubtitleFile>`
  - `createSubtitleConfiguration(subtitleUri: Uri, mimeType: String, label: String): MediaItem.SubtitleConfiguration`
- `LoudnessBooster`:
  - `applyBoost(loudnessEnhancer: LoudnessEnhancer?, boostFraction: Float)` (fraction: 1.0f .. 2.0f -> 0 .. 1500 mB)

### M2: Universal Tag Editor Contracts
- `UniversalTagWriter`:
  - `writeTags(context: Context, uri: Uri, metadata: TagMetadata): Result<Unit>`
  - Dispatches to `Id3TagWriter`, `FlacTagWriter`, or `M4aTagWriter` based on MIME/extension.
  - Returns `ScopedStoragePermissionRequiredException(intentSender)` on Android 11+ if permission is needed.

### M3: Folder Browser & Lyrics Contracts
- `FolderBrowserRepository`:
  - `listDirectory(path: String): List<FolderItem>`
  - `getFolderTracks(folderPath: String): List<Track>`
- `LyricsRepository`:
  - `getOfflineLyrics(track: Track): SyncedLyrics?` (Checks embedded ID3 USLT/SYLT, then companion `<name>.lrc`).

### M4: Process Death & Network Contracts
- `GobeatsDatabaseHelper`:
  - `savePlaybackState(trackId: String?, positionMs: Long, queueIds: List<String>, isPlaying: Boolean)`
  - `getPlaybackState(): SavedPlaybackState?`
- `GobeatsSyncService`:
  - Starts Foreground Service with `FOREGROUND_SERVICE_DATA_SYNC` notification, acquires `WifiLock` and `WakeLock` on sync start, releases on stop.

## Code Layout
- `app/src/main/java/com/example/gobeats/core/video/`: Video resume manager, subtitle discovery, loudness booster.
- `app/src/main/java/com/example/gobeats/feature/nowplaying/`: `FloatingVideoMiniplayer.kt`, `VideoGestureOverlay.kt`, `VideoSettingsBottomSheet.kt`.
- `app/src/main/java/com/example/gobeats/core/media/tag/`: `UniversalTagWriter.kt`, `FlacTagWriter.kt`, `M4aTagWriter.kt`, `Id3TagWriter.kt`.
- `app/src/main/java/com/example/gobeats/feature/library/folders/`: `FoldersScreen.kt`, `FolderBrowserRepository.kt`.
- `app/src/main/java/com/example/gobeats/core/sync/`: `GobeatsSyncService.kt`, `GobeatsSyncServer.kt`.
- `app/src/test/java/com/example/gobeats/`: Unit test suites for all features and regression tests.
