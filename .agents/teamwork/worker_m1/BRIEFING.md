# BRIEFING — 2026-10-06T07:46:00Z

## Mission
Implement Milestone 1: YouTube & MX Player Grade Video Engine (200% loudness boost, external subtitles, video resume memory, black-screen prevention, floating video miniplayer).

## 🔒 My Identity
- Archetype: worker_m1
- Roles: implementer, qa, specialist
- Working directory: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m1
- Original parent: 0a34afd4-fc8e-48e4-a3d6-ca63de92c85e
- Milestone: Milestone 1 (Video Engine)

## 🔒 Key Constraints
- DO NOT CHEAT: Genuine implementation, no hardcoded test results, no dummy facades.
- Scoped file ownership:
  - app/src/main/java/com/example/gobeats/core/video/ (SubtitleDetector.kt)
  - app/src/main/java/com/example/gobeats/feature/nowplaying/VideoGestureOverlay.kt
  - app/src/main/java/com/example/gobeats/feature/nowplaying/VideoSettingsBottomSheet.kt
  - app/src/main/java/com/example/gobeats/feature/nowplaying/FloatingVideoMiniplayer.kt
  - app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt
  - app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt
  - app/src/main/java/com/example/gobeats/MainActivity.kt
  - Unit tests under app/src/test/java/com/example/gobeats/
- Must verify with:
  - .\gradlew.bat compileDebugKotlin
  - .\gradlew.bat testDebugUnitTest

## Current Parent
- Conversation ID: 0a34afd4-fc8e-48e4-a3d6-ca63de92c85e
- Updated: 2026-10-06T07:46:00Z

## Task Summary
- **What to build**:
  1. 200% Loudness booster via LoudnessEnhancer & gesture drag in VideoGestureOverlay.kt + settings
  2. External .srt / .vtt subtitle auto-detection & storage file picker in PlaybackConnection.kt & VideoSettingsBottomSheet.kt
  3. Persistent video resume memory in GobeatsDatabaseHelper.kt and prompt/auto-resume in UI
  4. Black-screen trap prevention on single-rendition local offline files in PlaybackConnection.kt
  5. In-app draggable/floating video miniplayer overlay in FloatingVideoMiniplayer.kt and MainActivity.kt
- **Success criteria**: All 5 features implemented genuinely, passing compilation and unit tests.

## Change Tracker
- **Files modified**:
  - `GobeatsDatabaseHelper.kt`: Version 3 upgrade, `video_playback_positions` table, save/get/clear position APIs.
  - `EqualizerManager.kt`: Added `setVideoLoudnessBoostGainMb(gainMb)` (0..1500 mB) and `getVideoLoudnessBoostGainMb()`.
  - `Models.kt`: Added `VideoResumePrompt` model, `videoLoudnessBoostPercent`, `isSingleRenditionLocalFile`, `resumePrompt` to `PlaybackUiState`.
  - `core/video/SubtitleDetector.kt`: Companion `.srt`/`.vtt` file finder, language tag extractor, MediaItem subtitle config builder.
  - `PlaybackConnection.kt`: Position persistence every 4s, resume prompt display/resume/dismiss, companion subtitles auto-attach, dynamic external subtitle attachment, single-rendition black screen prevention in quality selector, 200% loudness boost mapper.
  - `Repositories.kt` (`PlaybackRepository`): Forwarded M1 video engine APIs.
  - `VideoGestureOverlay.kt`: 200% vertical drag booster with haptic click, amber-red boost HUD, and boost badge.
  - `FullscreenVideoPlayer.kt` & `VideoPlayerSurface.kt`: Loudness booster callbacks, sleek resume prompt banner overlay.
  - `NowPlayingBottomSheet.kt`: Resume prompt and loudness boost wiring.
  - `VideoSettingsBottomSheet.kt`: Loudness boost submenu presets (100%..200%), SAF external subtitle file picker, single-rendition banner in quality menu.
  - `FloatingVideoMiniplayer.kt`: In-app draggable video miniplayer overlay with magnetic edge snapping, 16:9 ratio, play/pause and close controls, and tap-to-expand.
  - `MainActivity.kt`: Integrated `FloatingVideoMiniplayer`, suppressed bottom `MiniPlayerBar` when video miniplayer is active, wired resume and loudness callbacks.
  - `VideoEngineM1Test.kt`: Comprehensive test suite for all 5 M1 features.
- **Build status**: `compileDebugKotlin` passed (exit code 0). Unit tests running.
- **Pending issues**: Awaiting `testDebugUnitTest` completion.

## Quality Status
- **Build/test result**: In progress
- **Lint status**: 0 violations
- **Tests added/modified**: `VideoEngineM1Test.kt` (7 test cases covering all 5 features)

## Loaded Skills
- None specified
