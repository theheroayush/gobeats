# Handoff Report: Explorer Survey 1 (Video Engine)

## 1. Observation

- **Audio Volume & Loudness Booster**:
  - In `app/src/main/java/com/example/gobeats/feature/nowplaying/VideoGestureOverlay.kt` (lines 309–317):
    ```kotlin
    val newVol = (volumeLevel + dragDelta).coerceIn(0f, 1f)
    volumeLevel = newVol
    val index = (newVol * max).toInt()
    am.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0)
    ```
    The volume swipe gesture is hard-clamped at `1.0f` (100% device stream volume).
  - In `app/src/main/java/com/example/gobeats/core/audio/EqualizerManager.kt` (lines 333–340):
    ```kotlin
    fun setLoudnessGain(gainMb: Int) {
        val clamped = gainMb.coerceIn(0, 800)
        try {
            loudnessEnhancer?.setTargetGain(clamped)
        } catch (_: Exception) {}
        prefs.edit().putInt("eq_loudness_gain", clamped).apply()
        _state.value = _state.value.copy(loudnessGainMb = clamped)
    }
    ```
    `LoudnessEnhancer` is restricted to 0–800 mB (+8.0 dB) for music replaygain headroom and is completely disconnected from video playback and gestures.

- **External Subtitles**:
  - In `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` (lines 330–349):
    ```kotlin
    private fun buildMediaItem(track: Track): MediaItem {
        val parsedUri = Uri.parse(track.fileUri)
        val metadata = MediaMetadata.Builder()...build()
        val requestMetadata = MediaItem.RequestMetadata.Builder()...build()
        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(parsedUri)
            .setRequestMetadata(requestMetadata)
            .setMediaMetadata(metadata)
            .build()
    }
    ```
    `MediaItem` is built with zero `SubtitleConfiguration` entries. No scanning for sibling `.srt` or `.vtt` files is performed.
  - In `app/src/main/java/com/example/gobeats/feature/nowplaying/VideoSettingsBottomSheet.kt` (lines 305–330):
    The subtitles sub-menu only lists embedded text tracks with no option to pick or load external subtitle files.

- **Playback Resume Memory**:
  - In `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt` (lines 199–209):
    ```sql
    CREATE TABLE playback_state (
        id INTEGER PRIMARY KEY CHECK (id = 1),
        current_track_id TEXT,
        position_ms INTEGER NOT NULL DEFAULT 0,
        is_playing INTEGER NOT NULL DEFAULT 0,
        queue_json TEXT NOT NULL DEFAULT '[]',
        updated_at INTEGER NOT NULL
    );
    ```
    Only a single global `playback_state` row exists. There is no table or mechanism to store individual video positions per video ID, and no resume prompt upon reopening videos.

- **Black-Screen Quality Selection Bug**:
  - In `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` (lines 214–236 and 636–657):
    ```kotlin
    val ladder = listOf(
        Triple("2160p", "4K (2160p)", 2160 to 3840),
        Triple("1440p", "1440p (QHD)", 1440 to 2560),
        Triple("1080p", "1080p (FHD)", 1080 to 1920),
        Triple("720p", "720p (HD)", 720 to 1280),
        Triple("480p", "480p (SD)", 480 to 854),
        Triple("360p", "360p (Data Saver)", 360 to 640)
    )
    ...
    fun selectVideoQuality(optionId: String) {
        controller?.let { player ->
            val paramsBuilder = player.trackSelectionParameters.buildUpon()
            when (optionId) {
                "auto" -> paramsBuilder.clearVideoSizeConstraints()
                "2160p" -> paramsBuilder.setMaxVideoSize(3840, 2160)
                "1080p" -> paramsBuilder.setMaxVideoSize(1920, 1080)
                "720p" -> paramsBuilder.setMaxVideoSize(1280, 720)
                ...
            }
            player.trackSelectionParameters = paramsBuilder.build()
    ```
    On a single-rendition 1080p local file, selecting "720p" sets `maxVideoSize(1280, 720)`. Because the only rendition is 1080p, ExoPlayer deselects the video track, resulting in a black screen.

- **In-App Floating Video Miniplayer**:
  - In `app/src/main/java/com/example/gobeats/MainActivity.kt` (lines 569–584):
    When `showNowPlayingSheet` is false, only `MiniPlayerBar` (audio-only bar) is displayed. No floating/draggable video player overlay exists within the app navigation hierarchy.

- **Build & Test Baseline**:
  - Ran `.\gradlew.bat testDebugUnitTest`: Task exited with code 0 (`BUILD SUCCESSFUL in 11s`). All 31 existing unit tests in `GobeatsCoreTest.kt` passed.

---

## 2. Logic Chain

1. **Loudness Booster**:
   - Because `VideoGestureOverlay` clamps volume at 1.0 (Observation 1) and `EqualizerManager` clamps gain at 800 mB for music (Observation 1), the video player cannot boost low-volume dialogue.
   - Extending `VideoGestureOverlay` to allow drag fraction up to 2.0 and mapping values between 1.0 and 2.0 to `LoudnessEnhancer.setTargetGain(0..1500 mB)` while holding system stream volume at max will achieve safe 200% audio boost without clipping.

2. **External Subtitles**:
   - Because `buildMediaItem` omits `SubtitleConfiguration` (Observation 2), external subtitles are never parsed by ExoPlayer.
   - Scanning the video file's directory for matching `.srt` / `.vtt` files and providing an `OpenDocument` storage contract, then converting them to `MediaItem.SubtitleConfiguration` with MIME `application/x-subrip` or `text/vtt`, allows automatic and user-driven subtitle display.

3. **Persistent Resume**:
   - Because the database only tracks a single active track in `playback_state` (Observation 3), prior video positions are lost when another track plays.
   - Adding a `video_playback_positions(video_id, position_ms, duration_ms, updated_at)` table in SQLite v3 migration and polling position every 4 seconds enables tracking per-video progress and displaying a resume prompt on launch.

4. **Black-Screen Trap**:
   - Because `selectVideoQuality` imposes `setMaxVideoSize` lower than the single local stream's height (Observation 4), ExoPlayer finds zero eligible video tracks and disables video rendering.
   - Guarding `selectVideoQuality` by checking available formats and hiding/disabling downscaling constraints for single-rendition local files eliminates the black-screen bug while preserving adaptive quality selection for HLS/DASH streams.

5. **In-App Floating Miniplayer**:
   - Because `MainActivity.kt` only renders `MiniPlayerBar` when the now-playing sheet is dismissed (Observation 5), video playback is obscured while browsing the app.
   - Introducing a top-level draggable, edge-snapping `FloatingVideoMiniplayer` Composable hosting `PlayerView` allows continuous video viewing across all library screens.

---

## 3. Caveats

- **Scoped Storage Permissions on External Subtitles**: Sibling detection via `File` operations works for `file://` schemes and internal app storage, but on Android 10+ (API 29+) MediaStore URIs (`content://`), sibling access requires querying `MediaStore.Files` or user SAF picking.
- **Audio HAL Differences**: Some Android emulator builds and low-end devices exhibit bugs with `LoudnessEnhancer` if initialized before playback starts; error handling (`try-catch`) is required around effect initialization.
- **Surface Transfer Latency**: Reassigning `playerView.player` between `FloatingVideoMiniplayer` and `NowPlayingBottomSheet` must ensure `onDispose` completes before the new surface attaches to prevent surface collision.

---

## 4. Conclusion

The architecture of GoBeats provides all necessary extension hooks in Media3 and Jetpack Compose to fulfill R1 at Tier-1 quality. The exact failure modes, data contracts, and UI integration points have been isolated and documented in `survey_report.md`. The codebase is healthy, compiles cleanly, and passes all existing unit tests.

---

## 5. Verification Method

- **Unit Tests**:
  - Run `.\gradlew.bat testDebugUnitTest` to confirm existing test suite passes.
  - Inspect `app/src/test/java/com/example/gobeats/GobeatsCoreTest.kt` for existing test patterns.
- **Code Inspection**:
  - `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` lines 214–236, 636–657.
  - `app/src/main/java/com/example/gobeats/feature/nowplaying/VideoGestureOverlay.kt` lines 309–317.
  - `app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt` lines 199–209.
- **Invalidation Condition**:
  - If selecting 720p on a local 1080p MP4 results in ExoPlayer dropping the video track, the black-screen bug is confirmed active.
