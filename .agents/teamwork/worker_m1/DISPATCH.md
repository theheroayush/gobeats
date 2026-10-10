# Dispatch Assignment: Worker M1 (Video Engine)

## Objective
Implement Milestone 1 (YouTube & MX Player Grade Video Engine) per ORIGINAL_REQUEST.md and PROJECT.md.

## Reference Documents
- `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\ORIGINAL_REQUEST.md` (Read this first!)
- `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\PROJECT.md`
- `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_1\survey_report.md`
- `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_1\handoff.md`

## Scope & File Ownership
You exclusively own and may modify:
- `app/src/main/java/com/example/gobeats/core/video/` (create video resume helper / subtitle manager if needed)
- `app/src/main/java/com/example/gobeats/feature/nowplaying/VideoGestureOverlay.kt`
- `app/src/main/java/com/example/gobeats/feature/nowplaying/VideoSettingsBottomSheet.kt`
- `app/src/main/java/com/example/gobeats/feature/nowplaying/FloatingVideoMiniplayer.kt`
- `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`
- `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt`
- `app/src/main/java/com/example/gobeats/MainActivity.kt`
- Unit tests for M1 under `app/src/test/java/com/example/gobeats/`

## Requirements to Implement
1. **F1: 200% Loudness Booster**:
   - In `VideoGestureOverlay.kt`, allow vertical volume swipe drag up to 2.0 (200%).
   - Between 0% and 100%, control `AudioManager.STREAM_MUSIC`.
   - Between 100% and 200%, hold stream volume at max and apply `LoudnessEnhancer` target gain (0 to 1500 mB) via `PlaybackConnection` / audio session ID. Display visual indicator (e.g. "Volume: 150% [Boost]").
2. **F2: External Subtitles**:
   - In `PlaybackConnection.kt`, when loading a video, auto-detect companion `.srt` and `.vtt` files in the same folder or via MediaStore.
   - Attach subtitle configurations (`MediaItem.SubtitleConfiguration` with `MimeTypes.APPLICATION_SUBRIP` or `MimeTypes.TEXT_VTT`, selection flags) to `MediaItem`.
   - In `VideoSettingsBottomSheet.kt`, provide option to pick external subtitle file from storage and dynamically attach it to the playing video.
3. **F3: Video Resume Memory**:
   - In `GobeatsDatabaseHelper.kt`, create/upgrade table `video_playback_positions (video_id TEXT PRIMARY KEY, position_ms INTEGER, duration_ms INTEGER, updated_at INTEGER)`.
   - Save playback position every 4–5s and on pause/stop.
   - Upon opening a video with saved position > 5000ms and < duration - 10000ms, display a smooth prompt/dialog or auto-resume banner to resume from last saved timestamp.
4. **F4: Black-Screen Quality Trap Fix**:
   - In `PlaybackConnection.kt:selectVideoQuality`, verify available video tracks.
   - For single-rendition local offline files, disable/clear downscaling constraints (`clearVideoSizeConstraints()`) so ExoPlayer does not disable video rendering to a black screen. Keep adaptive ladder constraints active for HLS/DASH streams.
5. **F5: Floating Video Miniplayer**:
   - Implement `FloatingVideoMiniplayer.kt` as an in-app draggable, edge-snapping overlay hosting the video `PlayerView`.
   - When user minimizes or navigates away from the now-playing video screen while video is playing, show the floating miniplayer overlay so video continues playing smoothly while browsing the app. Include close and expand actions.

## Mandatory Integrity Warning
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

## Verification Requirement
Run and verify:
- `.\gradlew.bat compileDebugKotlin`
- `.\gradlew.bat testDebugUnitTest`
Report full results in your handoff.md.


## 2026-10-06T07:13:52Z
[Message] timestamp=2026-10-06T07:13:52Z sender=0a34afd4-fc8e-48e4-a3d6-ca63de92c85e priority=MESSAGE_PRIORITY_HIGH
You are Worker M1 (Video Engine implementation).
Your assigned working directory is: c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\worker_m1
Your parent is the Project Orchestrator (orchestrator_1, conversation ID: 0a34afd4-fc8e-48e4-a3d6-ca63de92c85e).

## 2026-10-06T07:37:43Z
[Message] timestamp=2026-10-06T07:37:43Z sender=92085d5c-a141-4300-932a-1359637f6fbc priority=MESSAGE_PRIORITY_HIGH content=**Context**: E2E test verification blocked by compilation failure in VideoPlayerSurface.kt.
**Content**: Compilation failed with unresolved references in app/src/main/java/com/example/gobeats/feature/nowplaying/VideoPlayerSurface.kt:
- line 241: Unresolved reference 'Icons'
- line 342: Unresolved reference 'BorderStroke'
**Action**: Please add `import androidx.compose.material.icons.Icons` and `import androidx.compose.foundation.BorderStroke` to `VideoPlayerSurface.kt`.
