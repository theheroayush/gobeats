# Handoff Report: R2 Scoped Storage & Universal Tag Editor Survey

**Agent**: `explorer_survey_2` (Tag Editor & Scoped Storage Explorer)  
**Recipient**: `orchestrator_1` (Project Orchestrator)  
**Date**: 2026-10-06  
**Artifact**: `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_2\survey_report.md`  

---

## 1. Observation

1. **`app/build.gradle.kts`**:
   - `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`.
   - Dependencies: `media3` (1.4.1), `coil.compose` (2.7.0), `documentfile` (1.0.1).
   - No audio tagging library (such as `jaudiotagger` or `mp3agic`) is present in `build.gradle.kts` or `gradle/libs.versions.toml`.
   - `settings.gradle.kts` sets `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` and includes only `google` and `mavenCentral()`.

2. **`core/media/Id3TagWriter.kt`**:
   - Lines 34–100 implement `buildId3v23Tag`: pure Kotlin generator for ID3v2.3 tags (`TIT2`, `TPE1`, `TALB`, `TYER`, `TCON`, `TRCK`, `APIC`).
   - Line 174 implements `writeTagsToMp3File(file: File, newTagBytes: ByteArray)`: takes a `java.io.File`, writes tags, skips old ID3v2 header using synchsafe size, and copies MPEG audio frames to a temp file before renaming.
   - Line 178 checks `if (!file.exists() || !file.canWrite()) return false`.
   - Lines 222–244 implement `updateMediaStoreTags(context, contentUri, title, artist, album, year)`: updates MediaStore SQLite columns via `contentResolver.update`.

3. **`data/repository/Repositories.kt` (`LibraryRepository.updateTrackMetadata`)**:
   - Line 75: `val isMp3 = track.fileUri.endsWith(".mp3", ignoreCase = true) || track.format.contains("mp3", ignoreCase = true)`.
   - Lines 77–83:
     ```kotlin
     val filePath = if (track.fileUri.startsWith("file://")) {
         Uri.parse(track.fileUri).path
     } else if (track.fileUri.startsWith("/")) {
         track.fileUri
     } else {
         null
     }
     ```
   - For all tracks scanned via `MediaScanner.kt`, `track.fileUri` is `content://media/external/audio/media/<id>`. Therefore, `filePath` evaluates to `null`.
   - Result: File-level tag writing is **never executed** for tracks in the library, only `Id3TagWriter.updateMediaStoreTags(...)` at lines 101–110.

4. **Scoped Storage Permissions**:
   - Grep search for `createWriteRequest` across `app/src/main/java` returned **0 results**.
   - Grep search for `RecoverableSecurityException` across `app/src/main/java` returned **0 results**.
   - Neither `MainActivity.kt` nor `TagEditorBottomSheet.kt` has an `ActivityResultLauncher` registered for `ActivityResultContracts.StartIntentSenderForResult()`.

5. **Existing Verification Baseline**:
   - Command `.\gradlew.bat compileDebugKotlin` exited with code 0 (19s).
   - Command `.\gradlew.bat testDebugUnitTest` passed all 46 unit tests in `GobeatsCoreTest.kt`.

---

## 2. Logic Chain

1. **Premise 1 (From Observation 3)**: Tracks scanned by `MediaScanner.kt` store `fileUri` as `content://media/external/audio/media/<id>`. Because `Repositories.kt:77-83` only checks for `file://` or `/`, `filePath` is always `null` for scanned library tracks.
2. **Premise 2 (From Observation 2 & 4)**: On Android 11+ (targetSdk 36), modifying unowned audio files in public storage (e.g. `Downloads`, SD Card) via `ContentResolver` throws a `SecurityException` unless user consent is obtained via `MediaStore.createWriteRequest(...)` and an `ActivityResultLauncher`.
3. **Premise 3 (From Observation 1)**: `jaudiotagger` cannot be cleanly introduced via Gradle because: (a) it is not available in Maven Central under official artifacts, (b) standard `jaudiotagger` uses `java.awt.image.BufferedImage` which crashes on Android ART/Dalvik with `NoClassDefFoundError`.
4. **Premise 4 (From Observation 2 & Code Analysis)**: Pure Kotlin byte-level parsing and writing (as already demonstrated by `Id3TagWriter.kt`) is fast, self-contained, and completely safe on Android.
5. **Premise 5 (Format Rules)**:
   - For **FLAC**: `STREAMINFO` must be preserved as the first block. Replacing `VORBIS_COMMENT` (type 4) and `PICTURE` (type 6) while copying raw audio frames verbatim guarantees bit-perfect audio stream preservation.
   - For **M4A/MP4/ALAC**: In files where `moov` precedes `mdat`, altering `moov/udta/meta/ilst` shifts the byte offset of `mdat` by $\Delta = \text{newMoovSize} - \text{oldMoovSize}$. Adjusting every chunk offset in `moov/trak/mdia/minf/stbl/stco` (or `co64`) by $\Delta$ preserves exact audio synchronization and prevents audio corruption.
6. **Premise 6 (Dual-Layer Sync)**: To fulfill Acceptance Criteria, tag edits must be committed to the raw file (`ContentResolver.openOutputStream(uri, "wt")`), synced to MediaStore columns (`contentResolver.update`), indexed via `MediaScannerConnection.scanFile`, saved to SQLite overrides (`GobeatsDatabaseHelper.updateTrackMetadata`), and pushed to active playback (`PlaybackConnection.notifyTrackMetadataChanged`).

---

## 3. Caveats

1. **SAF Document Tree for Non-MediaStore SD Card Files**: If an untagged music file on an SD card is opened solely via directory tree browsing (R3) without being indexed by MediaStore, writing may require `DocumentFile` output stream rather than `MediaStore.createWriteRequest`.
2. **Exotic MP4 Features**: Rare fragmented MP4 files (`moof` boxes) or 64-bit `co64` chunk offsets are uncommon for standard music tracks (<100MB), but the implementation should support `stco` with a fallback or conversion to `co64` if an offset exceeds $2^{32}-1$.

---

## 4. Conclusion

The GoBeats codebase is in a clean baseline state, but currently has no Scoped Storage write permission handling and only supports MP3 at the database/MediaStore level without physical file persistence for `content://` URIs.
To deliver R2:
1. Implement pure-Kotlin `FlacTagWriter` (Vorbis comments & METADATA_BLOCK_PICTURE) and `M4aTagWriter` (`ilst` atoms & `stco` offset delta adjustment).
2. Refactor `Id3TagWriter` to operate on input/output streams.
3. Introduce `UniversalTagWriter` with isolated cache staging, pre-commit validation, and safe commit.
4. Implement `MediaStore.createWriteRequest` with `ActivityResultContracts.StartIntentSenderForResult()` in `MainActivity` / `TagEditorBottomSheet` for Android 10–14 (API 29–34).
5. Synchronize raw file, MediaStore, `MediaScannerConnection`, `GobeatsDatabaseHelper`, and `PlaybackConnection`.

---

## 5. Verification Method

1. **Compilation**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```
2. **Unit Tests**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   Inspect tests in `app/src/test/java/com/example/gobeats/GobeatsCoreTest.kt`.
3. **Report Inspection**:
   Inspect complete survey report at:
   `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_survey_2\survey_report.md`
