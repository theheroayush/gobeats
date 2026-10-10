# GoBeats E2E Testing Infrastructure (TEST_INFRA.md)

**Author:** E2E Test Writer (`test_writer_e2e`)  
**Target Project:** GoBeats Tier-1 Production Enhancements  
**Document Version:** 1.0.0  
**Status:** ACTIVE  

---

## 1. Overview & Architecture

This document establishes the opaque-box and regression testing architecture for GoBeats Tier-1 production enhancements across all 14 features (F1 through F14) specified in `PROJECT.md` and `ORIGINAL_REQUEST.md`.

The testing suite is designed with **zero placeholders**, **deterministic assertions**, **high test integrity**, and **progressive testability**. Tests run within the JVM test runner (`.\gradlew.bat testDebugUnitTest`) without relying on external network dependencies or flaky emulator hardware mocks, exercising real mathematical models, bitwise binary decoders, state machines, and lifecycle transitions.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        GoBeats E2E Test Suite                          │
├────────────────────────────────────────────────────────────────────────┤
│  Tier 1: Feature Coverage Suite (F1 – F14, >=5 tests/feature)           │
│  Tier 2: Boundary & Corner Case Suite (F1 – F14, >=5 tests/feature)    │
│  Tier 3: Cross-Feature Pairwise Interaction Suite                      │
│  Tier 4: Real-World End-to-End Application Workflow Suite              │
├────────────────────────────────────────────────────────────────────────┤
│                 Underlying Execution & Verification Engine             │
│        JUnit 4 + Kotlin Coroutines Test + JVM Local Byte Streams       │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Test Taxonomy (The 4 Tiers)

### Tier 1: Feature Coverage (>=5 Tests per Feature, >=70 Total)
Isolated functional verification covering the primary contract, state transitions, and happy path of each feature from F1 to F14.
- **F1: 200% Loudness Booster**: Volume scaling, 0–100% stream mapping, 101–200% LoudnessEnhancer millibel boost calculation, HUD label generation.
- **F2: External Subtitles**: Sibling companion file discovery (`.srt`/`.vtt`), MIME resolution, SubtitleConfiguration attributes, language extraction.
- **F3: Video Resume Memory**: Record persistence, minimum 5s threshold, credit skip threshold, prompt action routing.
- **F4: Black-Screen Trap Fix**: Single-rendition local video detection, constraint clearing, downscaling option suppression, adaptive ladder preservation.
- **F5: Floating Video Miniplayer**: 16:9 aspect ratio geometry, screen coordinate boundary clamping, magnetic edge snapping physics, visibility predicates.
- **F6: Scoped Storage Hardening**: Android API level routing (API 24–34), staging file creation, pre-commit validation, atomic swap semantics.
- **F7: FLAC Vorbis Tag Editor**: `fLaC` stream marker, STREAMINFO preservation, Vorbis Comment block encoding, cover picture block, audio frame zero-corruption.
- **F8: M4A/MP4 Tag Editor**: Atom tree traversal, `ilst` metadata box creation, chunk offset delta calculation, `stco` offset shifting, `mdat` audio byte immutability.
- **F9: Tag Sync Pipeline**: 5-layer synchronization, MediaStore column mapping, scanner notification, `track_metadata_overrides` rescan protection.
- **F10: Folders Directory Tree**: File format filtering, natural sorting (directories first), breadcrumb navigation stack, instant playlist generation.
- **F11: Audiophile Gapless & Crossfade**: Crossfade duration configuration, linear and equal-power volume curves, transition boundary triggering.
- **F12: Offline Synced Lyrics**: 4-tier resolution chain, companion `.lrc` detection, embedded ID3 `USLT` text decoding, embedded `SYLT` synchronized events.
- **F13: Process Death Persistence**: SQLite `playback_state` schema, JSON queue serialization, strict queue order restoration, playback position restore.
- **F14: Network Transfer Hardening**: Foreground service declaration, `WifiLock` mode selection, `WakeLock` timeouts, transfer throughput computation.

### Tier 2: Boundary & Corner Cases (>=5 Tests per Feature, >=70 Total)
Adversarial verification testing extreme inputs, negative values, empty payloads, maximum limits, corrupted headers, and format oddities:
- Negative volume gestures and overflows beyond 200%.
- Malformed subtitles, missing timestamps, mismatched case extensions.
- Videos shorter than resume threshold, corrupted timestamps, timestamp overflow.
- Single-rendition videos with missing dimensions, corrupted aspect ratios.
- Dragging miniplayer beyond screen boundaries, zero-dimension viewports.
- Interrupted staging file writes, zero-byte audio files, write-protected directories.
- Corrupted Vorbis vendor strings, huge comments exceeding buffer limits, non-ASCII Unicode tags.
- Negative delta in M4A atoms, empty ilst box, 64-bit co64 chunks.
- Overriding tags with empty strings, special punctuation, null fields.
- Deep nested empty directories, symbolic links, folders with no supported audio.
- Crossfade duration exceeding track duration, crossfade on 1-track queues.
- Corrupted `.lrc` timestamps, lyrics without closing brackets, multi-gigabyte lyric tags.
- Restoring corrupt JSON queues, missing track IDs, zero-position restores.
- Socket dropouts mid-transfer, zero-byte transfers, IP change events.

### Tier 3: Cross-Feature Interactions (Pairwise Combinations)
Testing multi-feature interplay and shared state:
1. **F1 + F3**: Loudness boost level retained across video resume prompts.
2. **F2 + F4**: External subtitle loaded while selecting quality without causing black screens.
3. **F3 + F13**: Video resume timestamps co-existing with audio queue process death state.
4. **F5 + F1**: Floating miniplayer active while volume booster is gesture-controlled.
5. **F6 + F7**: Scoped Storage staging protocol executing FLAC Vorbis comment modification.
6. **F6 + F8**: Scoped Storage staging protocol executing M4A ilst & stco chunk offset delta updates.
7. **F7/F8 + F9**: Lossless tag editing updating internal database overrides to protect against rescans.
8. **F10 + F11**: Playing folder directory as an instant queue with audiophile crossfade enabled.
9. **F10 + F12**: Folder browser discovering offline companion `.lrc` files in the same folder.
10. **F13 + F11**: Relaunching from process death with crossfade engine initialized.

### Tier 4: Real-World Scenarios (End-to-End Workflows)
Complete, multi-step application lifecycles mimicking real user interactions:
- **Scenario 1 (Video Cinema Workflow)**: Local video opening -> prompt auto-resume -> volume swipe to 175% boost -> attach external `.srt` subtitle -> transition to floating miniplayer -> return to fullscreen.
- **Scenario 2 (Audiophile Lossless Library Workflow)**: Scan directory of FLAC tracks -> load companion `.lrc` files -> modify metadata & cover art -> verify byte-for-byte audio stream invariance -> verify library rescan stability.
- **Scenario 3 (M4A Scoped Storage Workflow)**: User edits M4A in Downloads folder -> triggers scoped storage staging -> calculates moov delta -> shifts stco chunk offsets -> verifies mdat frames intact -> validates playback without audio jitter.
- **Scenario 4 (Process Death & Resurrection Workflow)**: User builds queue from multiple folders -> plays track to midpoint -> simulates Android process recreation -> restores queue order, track ID, and millisecond position in paused state.
- **Scenario 5 (Wi-Fi PC Connect Transfer & Hardening Workflow)**: Start Wi-Fi sync server -> acquire WifiLock & WakeLock -> stream multiple lossless albums -> calculate transfer rate -> clean release of locks -> scan tracks into library.

---

## 3. Test File Layout & Organization

All test suites reside in `app/src/test/java/com/example/gobeats/`:

| Test Suite File | Scope | Test Count |
|---|---|---|
| `GobeatsTier1FeatureCoverageTest.kt` | Tier 1: F1 through F14 isolated coverage | >= 70 tests |
| `GobeatsTier2BoundaryCornerTest.kt` | Tier 2: F1 through F14 boundary & corner cases | >= 70 tests |
| `GobeatsTier3CrossFeatureTest.kt` | Tier 3: Cross-feature pairwise interactions | >= 10 tests |
| `GobeatsTier4RealWorldWorkflowsTest.kt` | Tier 4: Real-world end-to-end workflows | >= 5 tests |
| `GobeatsCoreTest.kt` | Existing baseline core tests | 56 tests |

---

## 4. Execution & Verification Commands

All test suites are executed via standard Gradle tasks:

```powershell
# Run the complete test suite
.\gradlew.bat testDebugUnitTest

# Run specific tier test classes
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest"
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest"
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest"
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest"
```

---

## 5. Expected Output Derivation & Verification Integrity

1. **Mathematical Models**:
   - Volume boost: $gainMb = \text{clamp}((volume - 1.0) \times 1500, 0, 1500)$.
   - Equal-power crossfade: $vol_{out} = \cos(\frac{\pi}{2} \cdot \frac{t}{T})$, $vol_{in} = \sin(\frac{\pi}{2} \cdot \frac{t}{T})$ with $vol_{out}^2 + vol_{in}^2 = 1.0$.
   - M4A chunk offsets: $offset_{new} = offset_{old} + \Delta moov$.
2. **Binary Protocols**:
   - ID3v2 synchsafe integers: 7-bit per byte encoding ($N = \sum b_i \cdot 128^{3-i}$).
   - FLAC Vorbis comments: Little-endian 32-bit field lengths followed by UTF-8 strings.
   - ISO BMFF 4CC atoms: Big-endian 32-bit box lengths followed by ASCII FourCC.
3. **No Facades**:
   - Every test asserts actual output properties, byte sequences, data integrity, and error states.
