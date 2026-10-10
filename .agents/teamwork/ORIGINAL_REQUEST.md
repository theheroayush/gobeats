# Original User Request

## 2026-10-05T20:56:37Z

Build and integrate Tier-1 production-ready enhancements into GoBeats: YouTube-grade video player capabilities (200% audio boost, external subtitle loading, playback resume memory, floating miniplayer), hardened scoped-storage tag editing for all audio formats (MP3/FLAC/M4A), folder-tree browsing, and state persistence across process death.

Working directory: c:\Users\ayush\OneDrive\Documents\gobeats  
Integrity mode: development

---

## Requirements

### R1. YouTube & MX Player Grade Video Engine
- Implement a software loudness booster (up to 200% gain via `LoudnessEnhancer` or audio processor) for quiet dialogue tracks.
- Support external `.srt` and `.vtt` subtitles (auto-detect matching filename in directory and allow picking from storage).
- Implement persistent video resume memory: save playback position per video ID in database and prompt auto-resume upon opening.
- Prevent local video black-screen trap: disable/hide quality degradation constraints on single-rendition local offline files while keeping quality selection active for HLS/DASH streams.
- Implement an in-app draggable/floating video miniplayer overlay that stays active while browsing the app.

### R2. Scoped Storage & Universal Tag Editor
- Harden file writing for Android 10–14 (API 29–34) using `MediaStore.createWriteRequest` with an `ActivityResultLauncher` contract when modifying tracks in public storage (`Downloads`, SD Card).
- Extend tag editor beyond MP3 to support FLAC (Vorbis comments) and M4A/MP4/ALAC (MP4 `ilst` atoms) without corrupting audio frames.

### R3. Audiophile Offline Experience & Folder Browser
- Add a physical "Folders" directory tree browser under the Library screen, allowing direct navigation and playback of SD card/storage folders for untagged music.
- Implement true gapless playback transitions and configurable smooth crossfade (1–5s) between queue items.
- Support offline synced lyrics fallback: read embedded `USLT`/`SYLT` ID3 lyrics and local `<song>.lrc` files before querying LRCLIB.

### R4. Process Death & Network Hardening
- Persist active queue track IDs and millisecond playback position into SQLite/Room so the player state seamlessly survives background process recreation.
- Acquire `WifiManager.WifiLock` and run a foreground service with an ongoing notification during Wi-Fi PC Connect transfers to prevent Android Doze mode socket termination.

---

## Acceptance Criteria

### Video Playback & Subtitles
- [ ] Local videos with quiet audio can be boosted beyond 100% up to 200% without distortion or crashes.
- [ ] Videos with adjacent `.srt` files automatically display subtitles, and users can load custom `.srt` files.
- [ ] Re-opening a previously watched video presents an option to resume from the last saved timestamp.
- [ ] Selecting video quality on a local single-stream MP4 does not cause a black screen.

### Tag Editor & Storage Permissions
- [ ] Modifying tags on a file in `Downloads` or SD card prompts for Android scoped storage write permission on Android 11+ and persists changes to both MediaStore and file.
- [ ] Tag editing successfully updates tags on MP3, FLAC, and M4A audio files.

### Library & Ergonomics
- [ ] A "Folders" navigation tab displays storage directory hierarchy and plays folders as instant playlists.
- [ ] Local `.lrc` files located in the same folder as an audio track load and sync when offline.
- [ ] Force-killing the app and relaunching restores the last playing track and position in the queue.

### Verification
- [ ] `.\gradlew.bat compileDebugKotlin` succeeds with zero errors.
- [ ] `.\gradlew.bat testDebugUnitTest` passes all unit tests.
- [ ] `.\gradlew.bat assembleDebug` builds the final APK cleanly.


## 2026-10-06T14:03:08Z

Resume and execute remaining Milestones (M2, M3, M4, M5) for GoBeats Tier-1 Production Enhancements in .agents/teamwork/PROJECT.md.

Working directory: c:\Users\ayush\OneDrive\Documents\gobeats  
Integrity mode: development

Status:
- Milestone 1 (Video Engine: F1 200% Loudness, F2 External Subtitles, F3 Video Resume Memory, F4 Black-Screen Trap Fix, F5 Floating Video Miniplayer) is IMPLEMENTED and all tests are passing.
- Next to execute:
  - Milestone 2: Scoped Storage & Universal Tag Editor (F6 MediaStore.createWriteRequest, F7 FLAC Vorbis, F8 M4A ilst, F9 Tag Sync Pipeline)
  - Milestone 3: Audiophile Offline & Folder Browser (F10 Folders Directory Tree, F11 Gapless & Crossfade, F12 Offline Synced Lyrics)
  - Milestone 4: Process Death & Network Hardening (F13 Process Death Persistence, F14 Network Transfer Hardening)
  - Milestone 5: Final Integration & Full Verification (Pass 100% tests, assembleDebug).


## 2026-10-06T20:23:23Z

Resume GoBeats Tier-1 Production Enhancements in .agents/teamwork/PROJECT.md.

Working directory: c:\Users\ayush\OneDrive\Documents\gobeats  
Integrity mode: development

Current Status:
- Milestone 1 (Video Engine: F1-F5) is 100% COMPLETE & VERIFIED.
- Milestone 2 (Universal Tag Editor & Scoped Storage: F6-F9) is 100% COMPLETE & VERIFIED (All 266 tests in testDebugUnitTest are passing).
- Next to execute:
  - Milestone 3: Audiophile Offline & Folder Browser (F10 Folders Directory Tree, F11 Audiophile Gapless & Crossfade, F12 Offline Synced Lyrics)
  - Milestone 4: Process Death & Network Hardening (F13 Process Death Persistence, F14 Network Transfer Hardening)
  - Milestone 5: Final Integration & Full Verification (Pass 100% E2E tests, assembleDebug).


## 2026-10-07T14:20:59Z

Milestone 3 has achieved clearance with unanimous approval (auditor_m3_1: CLEAN with 282/282 unit tests passing, reviewer_m3_1: APPROVE, reviewer_m3_2: APPROVE).

The user has explicitly approved proceeding:
"Check for clearance and then tell the team to go and execute Milestone 4 and continue working."

Please certify Milestone 3 Gate clearance in GATE_STATUS.md and immediately dispatch orchestrator/workers to execute Milestone 4 (Process Death & Network Hardening: F13 Process Death Persistence and F14 Network Transfer Hardening with WifiLock), followed by Milestone 5 (Final Integration, 100% E2E test verification, and clean assembleDebug APK build). Continue working now!
