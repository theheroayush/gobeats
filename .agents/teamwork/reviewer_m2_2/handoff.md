# Handoff Report: Milestone 2 Review & Adversarial Audit

**Agent:** `reviewer_m2_2`  
**Roles:** `reviewer`, `critic`  
**Milestone:** Milestone 2 (Features F6, F7, F8, F9)  
**Date:** 2026-10-06  
**Verdict:** **REQUEST_CHANGES**

---

## 1. Observation

### 1.1 Source Code Observations
1. **Non-Atomic File Replacement (`FlacTagWriter.kt` & `M4aTagWriter.kt`)**:
   - `FlacTagWriter.kt` (lines 349–354):
     ```kotlin
     if (file.delete()) {
         tempFile.renameTo(file)
     } else {
         tempFile.delete()
         false
     }
     ```
   - `M4aTagWriter.kt` (lines 251–256):
     ```kotlin
     if (file.delete()) {
         tempFile.renameTo(file)
     } else {
         tempFile.delete()
         false
     }
     ```
   - The original user media file is deleted before the staging file is renamed into place. If `renameTo` fails or process terminates between delete and rename, the user file is permanently destroyed.

2. **Non-Faststart M4A/MP4 Box Reordering & Offset Desynchronization (`M4aTagWriter.kt`)**:
   - `M4aTagWriter.kt` (lines 224–239):
     ```kotlin
     FileOutputStream(tempFile).use { out ->
         out.write(ftypBytes)
         out.write(updatedMoov)
         // Stream mdat bit-perfectly from original file
         raf.seek(mdatStartOffset)
         ...
     ```
   - `M4aTagWriter.kt` (lines 325–346):
     ```kotlin
     val currentOffset = buf.getInt(entryOffset)
     if ((currentOffset.toLong() and 0xFFFFFFFFL) >= mdatStartOffset) {
         buf.putInt(entryOffset, currentOffset + moovDelta)
     }
     ```
   - Output files are always written as `ftyp -> updatedMoov -> mdat`. If an input file has `mdat` before `moov` (standard non-faststart layout), `mdat` is displaced by `updatedMoov.size`, but offsets are only adjusted by `moovDelta = updatedMoov.size - moovBytes.size`. This causes chunk offsets to point into `moov` instead of `mdat`, corrupting audio playback.

3. **Total Metadata Obliteration in M4A Rewrite (`M4aTagWriter.kt`)**:
   - `M4aTagWriter.kt` (lines 201–218, 101–133):
     ```kotlin
     val ilstBytes = buildIlstBox(payload)
     ...
     val updatedMoov = replaceOrAppendUdta(moovBytes, udtaBuf.array())
     ```
   - `buildIlstBox` only creates atoms for title, artist, album, year, genre, trackNumber, and `newArtworkBytes`. Existing atoms in the source `ilst` (composer `©wrt`, comments `©cmt`, disc number `disk`, compilation `cpil`, and existing album artwork `covr` if not re-selected) are discarded.

4. **Invalid Stream Mode `"r"` Passed to `openOutputStream` (`UniversalTagWriter.kt`)**:
   - `UniversalTagWriter.kt` (lines 84–92 and 227–233):
     ```kotlin
     var needsPermission = false
     try {
         // Test if write channel is available
         context.contentResolver.openOutputStream(contentUri, "r")?.close()
     } catch (_: SecurityException) {
         needsPermission = true
     }
     ```
   - Android `ContentResolver.openOutputStream` does not support `"r"` (read mode). Attempting to open an output stream with mode `"r"` causes `AssetFileDescriptor.createOutputStream()` to throw `IllegalArgumentException` (which is not caught by `catch (_: SecurityException)`), leading to crashes or false negative permission probes.

5. **Brute-Force Substring Search for `"stco"` / `"co64"` (`M4aTagWriter.kt`)**:
   - `M4aTagWriter.kt` (lines 329–336):
     ```kotlin
     for (i in 0 until moovBytes.size - 16) {
         if (moovBytes[i + 4] == 's'.code.toByte() &&
             moovBytes[i + 5] == 't'.code.toByte() &&
             moovBytes[i + 6] == 'c'.code.toByte() &&
             moovBytes[i + 7] == 'o'.code.toByte()
         ) {
             val entryCount = buf.getInt(i + 12)
             var entryOffset = i + 16
     ```
   - Rather than traversing `moov -> trak -> mdia -> minf -> stbl -> stco`, the code scans raw bytes. If image bytes in `covr` or metadata strings happen to contain `"stco"`, arbitrary data is treated as an entry count and rewritten.

6. **Flac Cover Art Deletion on Title-Only Edit (`FlacTagWriter.kt`)**:
   - `FlacTagWriter.kt` (lines 332):
     ```kotlin
     removeArtwork = payload.artworkUri == null && payload.newArtworkBytes == null
     ```
   - If a FLAC file has embedded artwork but `track.artworkUri` is null, editing only the title or artist results in `removeArtwork = true`, discarding the existing embedded picture block.

7. **Production Classes Not Exercised in Test Baseline**:
   - `GobeatsTier1FeatureCoverageTest.kt` (lines 550–571, 628–696, 774–809, 889–917):
     Features F6–F9 tests in Tier 1 and Tier 2 use mock simulator engines (`ScopedStorageRouter`, `FlacTagEngine`, `M4aChunkOffsetEngine`, `TagSyncSimulator`) embedded directly in the test file. The production classes in `com.example.gobeats.core.media.*` were not exercised by the test baseline.

### 1.2 Build & Test Results
- `.\gradlew.bat compileDebugKotlin`: `BUILD SUCCESSFUL in 14s` (Production code compiles cleanly).
- Baseline `.\gradlew.bat testDebugUnitTest`: `BUILD SUCCESSFUL in 14s` (Tests pass, but F6–F9 test simulator classes rather than production classes).

---

## 2. Logic Chain

1. **Premise 1 (Data Preservation Non-Negotiable)**: Audio tag editing must never result in the loss or corruption of user media files.
2. **Premise 2 (Observation 1.1.1)**: Executing `file.delete()` before verifying `tempFile.renameTo(file)` means that if `renameTo` fails or the process is interrupted, the original audio file is permanently lost.
3. **Premise 3 (Observation 1.1.2)**: For non-faststart M4A files (`mdat` before `moov`), reordering boxes to `ftyp -> moov -> mdat` shifts `mdat` by `updatedMoov.size`, whereas `adjustChunkOffsetsInMoovBuffer` only shifts offsets by `moovDelta`. This leads to corrupted chunk offsets and unplayable audio.
4. **Premise 4 (Observation 1.1.3 & 1.1.6)**: Discarding the existing `udta` box in M4A and passing `removeArtwork = true` when `artworkUri == null` in FLAC results in silent deletion of user metadata and embedded cover artwork on simple tag edits.
5. **Premise 5 (Observation 1.1.4)**: `openOutputStream(contentUri, "r")` is invalid on Android ContentResolver, throwing unhandled exceptions.
6. **Conclusion**: The implementation contains critical flaws that jeopardize audio playback, user media preservation, and container integrity under standard real-world conditions. A verdict of `REQUEST_CHANGES` is required.

---

## 3. Caveats

1. For fast-start M4A files where `moov` precedes `mdat`, `M4aTagWriter`'s offset arithmetic works as intended. The corruption occurs on files where `mdat` precedes `moov`.
2. On standard POSIX filesystems where `file.delete()` and `renameTo` succeed within the same directory, atomic swap failure is intermittent (triggered by process recreation, storage volume cross-mounts, or indexing locks).
3. `ScopedStorageRouter.resolveWriteMechanism` correctly categorizes API levels 28, 29, and 30–34 according to Android specifications.

---

## 4. Conclusion

**Verdict: REQUEST_CHANGES**

### Required Action Items for Milestone 2:
1. **Critical — Fix Non-Atomic File Swap**:
   - Replace `file.delete()` followed by `tempFile.renameTo(file)` with safe replacement logic: use `java.nio.file.Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)` on API 26+, or a backup-and-restore fallback pattern (`file.renameTo(backup)` -> `temp.renameTo(file)` -> delete backup, restoring on failure).
2. **Critical — Fix M4A Container Reordering & Offset Calculation**:
   - In `M4aTagWriter.kt`:
     - If the input file has `mdat` before `moov`, either maintain the original box order (`ftyp -> mdat -> updatedMoov`), OR if moving `moov` before `mdat`, recalculate chunk offsets using the actual delta between the old and new start positions of `mdat` (`newMdatOffset - oldMdatOffset`).
3. **Major — Preserve Existing M4A Tags & Embedded Cover Art**:
   - When rewriting `moov/udta/meta/ilst`, preserve existing atom boxes (`covr`, `©wrt`, `©cmt`, `disk`, `cpil`, etc.) rather than overwriting `udta` with only the basic fields.
4. **Major — Fix Output Stream Probe**:
   - In `UniversalTagWriter.kt`: replace `openOutputStream(contentUri, "r")` with a valid check (e.g. `openFileDescriptor(contentUri, "w")` or test the actual write stream and handle `SecurityException` / `RecoverableSecurityException`).
5. **Major — Safe Box Traversal for `stco`/`co64`**:
   - Replace linear byte scanning for `"stco"` / `"co64"` with structured atom box traversal (`moov -> trak -> mdia -> minf -> stbl -> stco/co64`) to prevent false-positive matches on raw image data.
6. **Major — Fix FLAC Artwork Preservation**:
   - In `FlacTagWriter.writeTagsToFlacFile`: do not set `removeArtwork = true` unless the user explicitly requested artwork deletion.

---

## 5. Verification Method

1. **Verify Kotlin Build**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```
   *Expected*: BUILD SUCCESSFUL with 0 errors.

2. **Verify M4A Non-Faststart Audio Preservation**:
   - Synthesize an M4A file with `ftyp -> mdat -> moov`.
   - Update tags using `M4aTagWriter.writeTagsToM4aFile`.
   - Verify that all `stco` offsets match the exact audio chunk offsets in `mdat` and audio payloads remain uncorrupted.

3. **Verify Atomic Replacement Safety**:
   - Simulate a failed rename and ensure original file is not deleted.

4. **Verify Full Test Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
