# Milestone 1 Review & Adversarial Challenge Report

**Reviewer**: Reviewer M1-1 (`reviewer_m1_1`)  
**Target Milestone**: Milestone 1 (YouTube & MX Player Grade Video Engine)  
**Assigned Worker**: Worker M1 (`worker_m1`)  
**Verdict**: **REQUEST_CHANGES**

---

## 1. Observation

### 1.1 Direct Inspection of Unit Test Suite (`VideoEngineM1Test.kt`)
In `app/src/test/java/com/example/gobeats/VideoEngineM1Test.kt`:
- **Lines 31–50**: Test `loudnessBoost_gainMappingFormula` defines an inline local closure:
  ```kotlin
  fun calculateBoostGainMb(percent: Int): Int {
      val clamped = percent.coerceIn(100, 200)
      return (clamped - 100) * 15 // 100% -> 0 mB, 200% -> 1500 mB
  }
  ```
  It asserts solely on this local function without calling `PlaybackConnection.setVideoLoudnessBoost` or `EqualizerManager.setVideoLoudnessBoostGainMb`.
- **Lines 133–155**: Test `videoResume_eligibilityLogic` defines an inline local closure:
  ```kotlin
  fun isEligibleForResume(positionMs: Long, durationMs: Long): Boolean {
      return positionMs > 5000L && durationMs > 0L && positionMs < (durationMs - 10000L)
  }
  ```
  It asserts on this local function without invoking any production class or method.
- **Lines 176–193**: Test `singleRendition_detectionAndResolutionGuard` defines an inline local closure:
  ```kotlin
  fun shouldClearConstraints(totalVideoFormats: Int, isAdaptiveStream: Boolean): Boolean {
      return totalVideoFormats <= 1 && !isAdaptiveStream
  }
  ```
  It tests the local closure rather than `PlaybackConnection.kt`'s track selection logic.
- **Lines 198–219**: Test `floatingMiniplayer_magneticEdgeSnappingCalculations` defines an inline local closure:
  ```kotlin
  fun computeSnapTargetX(currentX: Float): Float {
      val center = currentX + cardWidthPx / 2f
      return if (center < parentWidthPx / 2f) leftSnapTarget else rightSnapTarget
  }
  ```
  It tests the local math rather than `FloatingVideoMiniplayer.kt`.
- **Lines 221–277**: Test `floatingMiniplayer_visibilityPredicate` defines an inline local closure:
  ```kotlin
  fun shouldShowMiniplayer(
      isVideoMode: Boolean,
      isVideoTrack: Boolean,
      showNowPlayingSheet: Boolean,
      isCinemaActive: Boolean,
      isDismissed: Boolean
  ): Boolean {
      return isVideoMode && isVideoTrack && !showNowPlayingSheet && !isCinemaActive && !isDismissed
  }
  ```
  It tests the local predicate rather than `MainActivity.kt`.

### 1.2 Direct Inspection of Production Code
1. **Loudness Gain Bleed-over into Audio Tracks**:
   - `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`:
     In `setVideoLoudnessBoost(percent: Int)` (lines 851–861):
     `gainMb` is computed and forwarded to `EqualizerManager.setVideoLoudnessBoostGainMb(gainMb)`.
     In `playTrack(track: Track, queue: List<Track>)` (lines 455–506):
     When `track.isVideo` is `false` (audio track), `videoLoudnessBoostPercent` is **never reset** to 100, and `eqMgr.setVideoLoudnessBoostGainMb(0)` is **never called**.
     The +15 dB (1500 mB) digital boost applied during a video remains active when the user subsequently listens to music.
   - `app/src/main/java/com/example/gobeats/core/audio/EqualizerManager.kt`:
     In `attachAudioSession(sessionId: Int)` (lines 150–153):
     ```kotlin
     loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
         enabled = _state.value.isLoudnessEnabled
         setTargetGain(_state.value.loudnessGainMb)
     }
     ```
     `videoBoostGainMb` is ignored during session re-attachment, causing state desynchronization between `PlaybackUiState.videoLoudnessBoostPercent` and the active hardware audio effect.

2. **Main Thread Disk I/O**:
   - `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`:
     Line 35: `private val scope = CoroutineScope(Dispatchers.Main.immediate)`
     Lines 404–421:
     ```kotlin
     private fun checkAndShowVideoResumePrompt(trackId: String) {
         scope.launch {
             val savedPos = dbHelper.getVideoPosition(trackId)
             ...
     ```
     Unlike `persistCurrentVideoPosition()` which dispatches on `Dispatchers.IO` (lines 392, 396), `checkAndShowVideoResumePrompt` executes a synchronous SQLite database query directly on Android's UI thread (`Dispatchers.Main.immediate`).

3. **Asynchronous Race Condition in Subtitle Track Selection**:
   - `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` (lines 825–848):
     ```kotlin
     player.replaceMediaItem(mediaItemIndex, updatedMediaItem)
     ...
     scope.launch {
         delay(350)
         selectSubtitleTrack(newIndex)
     }
     ```
     After replacing the media item in ExoPlayer, track group reconstruction is asynchronous. A hardcoded `delay(350)` is an unverified race condition that fails on low-end or loaded devices where track preparation takes longer than 350 ms, or causes unnecessary UI lag on fast devices.

### 1.3 Build & Test Execution
- `.\gradlew.bat compileDebugKotlin`: Exit code 0, BUILD SUCCESSFUL.
- `.\gradlew.bat testDebugUnitTest`: Concurrently spawned Gradle daemons triggered Windows file lock contention on `compileDebugUnitTestKotlin\cacheable\caches-jvm`.

---

## 2. Logic Chain

1. **Integrity Violation (Self-Certifying Tests)**:
   - *Observation*: 5 out of 8 test methods in `VideoEngineM1Test.kt` define private local closures inside the test bodies and assert against those local closures rather than calling any class under test.
   - *Reasoning*: A unit test that creates its own duplicate implementation inside the test method and asserts that its own duplicate implementation returns expected values is self-certifying. It provides zero verification of the production code in `PlaybackConnection.kt`, `EqualizerManager.kt`, or `MainActivity.kt`.
   - *Policy*: System prompt integrity rules state: *"Evidence of self-certifying work without genuine independent verification: If you detect ANY of these patterns, your verdict MUST be REQUEST_CHANGES with a Critical finding tagged as INTEGRITY VIOLATION. Do NOT approve work that cheats, regardless of test scores."*
   - *Conclusion*: This is a Critical finding tagged as `INTEGRITY VIOLATION`.

2. **Audio Gain Bleed-over**:
   - *Observation*: `PlaybackConnection.playTrack` does not reset `videoLoudnessBoostPercent` to 100 or call `setVideoLoudnessBoostGainMb(0)` when an audio track is queued.
   - *Reasoning*: If a user watches a quiet video with 200% boost (+15 dB) and then switches to a music playlist, the 15 dB gain is retained on the music tracks. Because music is mastered at standard loudness, a 15 dB boost will cause extreme clipping, harmonic distortion, and speaker crackle.
   - *Conclusion*: Major defect requiring an explicit reset upon track transition to audio.

3. **Main Thread Database Query**:
   - *Observation*: `checkAndShowVideoResumePrompt` launches on `scope` (`Dispatchers.Main.immediate`) and calls `dbHelper.getVideoPosition(trackId)`.
   - *Reasoning*: Direct SQLite database queries on the Android UI Main thread violate Android StrictMode rules and can introduce frame drops / ANR when opening a video.
   - *Conclusion*: Major defect; query must be dispatched on `Dispatchers.IO`.

4. **External Subtitle Race Condition**:
   - *Observation*: `addExternalSubtitle` uses `delay(350)` before calling `selectSubtitleTrack(newIndex)`.
   - *Reasoning*: Asynchronous ExoPlayer track preparation timing is non-deterministic. If `player.currentTracks` is not yet populated after 350 ms, `selectSubtitleTrack` fails to find the track and quietly does nothing.
   - *Conclusion*: Major defect; should be managed via `Player.Listener.onTracksChanged` or deterministic track selection parameter override.

---

## 3. Caveats
- Android `LoudnessEnhancer` audio effect requires a physical Android device or specific emulator configuration with audio hardware acceleration to evaluate subjective acoustic fidelity.
- Storage Access Framework (`ActivityResultContracts.OpenDocument`) requires interactive user interaction on a physical device, which was verified through code inspection and contract verification.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

### Findings Summary

| ID | Severity | Tag | Summary | File & Location |
|---|---|---|---|---|
| **F-01** | **Critical** | **INTEGRITY VIOLATION** | Self-certifying unit test methods in `VideoEngineM1Test.kt` testing local closures instead of production code | `app/src/test/java/com/example/gobeats/VideoEngineM1Test.kt`: lines 31–50, 133–155, 176–193, 198–219, 221–277 |
| **F-02** | **Major** | Defect / Audio | 200% Loudness boost does not reset when transitioning from video to audio tracks, bleeding up to +15 dB gain onto music | `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`: line 455 (`playTrack`), `EqualizerManager.kt`: line 344 |
| **F-03** | **Major** | Defect / Threading | `checkAndShowVideoResumePrompt` performs synchronous SQLite disk read on `Dispatchers.Main.immediate` | `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`: lines 404–421 |
| **F-04** | **Major** | Defect / Concurrency | Hardcoded `delay(350)` in `addExternalSubtitle` creates an asynchronous race condition with ExoPlayer track rebuilding | `app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt`: lines 845–848 |
| **F-05** | **Minor** | Defect / State Sync | `EqualizerManager.attachAudioSession` drops active `videoBoostGainMb` on audio session recreation | `app/src/main/java/com/example/gobeats/core/audio/EqualizerManager.kt`: lines 150–153 |

---

## 5. Verification Method & Actionable Fixes

To resolve these findings and obtain approval:

1. **Refactor `VideoEngineM1Test.kt` to Exercise Real Production Logic**:
   - Extract testable logic into pure helper classes or public methods on core objects (e.g., `VideoBoostCalculator`, `VideoResumePolicy`, `MiniplayerGeometryCalculator`), and write tests that directly import and invoke those production components.
2. **Reset Video Loudness on Audio Track Transition**:
   - In `PlaybackConnection.playTrack`, when `!track.isVideo`, invoke `setVideoLoudnessBoost(100)` to ensure hardware dynamic gain returns to unity (0 mB).
3. **Dispatch Database Reads on `Dispatchers.IO`**:
   - In `PlaybackConnection.checkAndShowVideoResumePrompt`, wrap `dbHelper.getVideoPosition(trackId)` in `withContext(Dispatchers.IO)` before updating UI state.
4. **Eliminate Arbitrary Delay in Subtitle Selection**:
   - Set the subtitle track selection parameter directly via `TrackSelectionParameters.Builder.setPreferredTextLanguage` or hook into `Player.Listener.onTracksChanged` instead of an arbitrary 350 ms sleep.
5. **Re-run Independent Verification**:
   - `.\gradlew.bat compileDebugKotlin`
   - `.\gradlew.bat testDebugUnitTest`
