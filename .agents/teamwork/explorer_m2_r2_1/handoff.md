# Handoff Report: Fix Strategy for Atomic File Replacement & FLAC Artwork Preservation

**Agent:** `explorer_m2_r2_1`  
**Roles:** `Codebase & Spec Explorer (Iteration 2)`  
**Milestone:** Milestone 2 (Features F6, F7, F8, F9)  
**Date:** 2026-10-06  
**Status:** Complete Analysis & Concrete Blueprint

---

## 1. Observation

### 1.1 Non-Atomic File Replacement (`FlacTagWriter.kt`, `M4aTagWriter.kt`, `Id3TagWriter.kt`)
Inspection of media tag writing implementations reveals a destructive two-step file swap pattern:

1. **`FlacTagWriter.kt`** (lines 349–354):
   ```kotlin
   if (file.delete()) {
       tempFile.renameTo(file)
   } else {
       tempFile.delete()
       false
   }
   ```
2. **`M4aTagWriter.kt`** (lines 250–260):
   ```kotlin
   if (isValid) {
       if (file.delete()) {
           tempFile.renameTo(file)
       } else {
           tempFile.delete()
           false
       }
   } else {
       tempFile.delete()
       false
   }
   ```
3. **`Id3TagWriter.kt`** (lines 208–212):
   ```kotlin
   if (file.delete()) {
       tempFile.renameTo(file)
   } else {
       false
   }
   ```

**Failure Mechanism**:
- The user's original audio file (`file`) is deleted *before* ensuring that `tempFile.renameTo(file)` succeeds.
- If the Android operating system terminates the process between `file.delete()` and `renameTo()` (e.g. low memory killer, power loss, user force-close), the user's audio file is permanently deleted.
- If `tempFile.renameTo(file)` fails due to file locks (Windows indexer, Android MediaScanner, antivirus, or cross-filesystem mount), the fallback branch deletes `tempFile`. The original file was already deleted in the `if` condition, leaving the user with total data loss.

---

### 1.2 FLAC Embedded Artwork Deletion on Text-Only Edits (`FlacTagWriter.kt`)
Inspection of `FlacTagWriter.writeTagsToFlacFile` and `TagEditPayload`:

1. **`FlacTagWriter.kt`** (lines 325–336):
   ```kotlin
   val success = FileInputStream(file).use { input ->
       FileOutputStream(tempFile).use { output ->
           writeTagsToStreams(
               input = input,
               output = output,
               comments = comments,
               artworkBytes = payload.newArtworkBytes,
               removeArtwork = payload.artworkUri == null && payload.newArtworkBytes == null,
               vendorString = vendorString
           )
       }
   }
   ```
2. **`FlacTagWriter.kt`** (lines 255–262 in `writeTagsToStreams`):
   ```kotlin
   for (i in 1 until existingBlocks.size) {
       val b = existingBlocks[i]
       if (b.type != BLOCK_TYPE_VORBIS_COMMENT && b.type != BLOCK_TYPE_PICTURE) {
           blocksToWrite.add(b.copy(isLast = false))
       } else if (b.type == BLOCK_TYPE_PICTURE && artworkBytes == null && !removeArtwork) {
           blocksToWrite.add(b.copy(isLast = false))
       }
   }
   ```
3. **`Id3TagWriter.kt`** (lines 15–25):
   ```kotlin
   data class TagEditPayload(
       val trackId: String,
       val title: String,
       val artist: String,
       val album: String,
       val year: Int? = null,
       val genre: String? = null,
       val trackNumber: Int? = null,
       val artworkUri: String? = null,
       val newArtworkBytes: ByteArray? = null
   )
   ```
4. **`TagEditorBottomSheet.kt`** (lines 104–106, 350–361):
   - `var currentArtworkUri by remember { mutableStateOf(track.artworkUri) }`
   - `var isArtworkRemoved by remember { mutableStateOf(false) }`
   - For an unindexed or local track, `track.artworkUri` is often `null`.
   - When a user modifies only the title or artist name without selecting a new picture, `currentArtworkUri` remains `null`, `selectedArtworkBytes` remains `null`, and `isArtworkRemoved` remains `false`.
   - In `TagEditorBottomSheet.kt` (line 350):
     `val finalArtworkUri = if (isArtworkRemoved) null else currentArtworkUri` -> Evaluates to `null`.
   - In `FlacTagWriter.kt` (line 332):
     `removeArtwork = payload.artworkUri == null && payload.newArtworkBytes == null` -> Evaluates to `true`!
   - Result: In `writeTagsToStreams`, `b.type == BLOCK_TYPE_PICTURE && artworkBytes == null && !removeArtwork` evaluates to `false`. The existing `METADATA_BLOCK_PICTURE` is silently discarded, stripping the album cover art from the user's FLAC file permanently.

---

## 2. Logic Chain

1. **Premise 1 (Zero Data Loss Non-Negotiable)**: Audio tag editing must never destroy user files or discard existing metadata under any failure mode.
2. **Premise 2 (File Swap Atomicity)**: `file.delete()` preceding `renameTo()` is an anti-pattern. If any interruption or filesystem error occurs, the original file is already destroyed. The destination file must remain intact until the replacement file is guaranteed to take its place.
3. **Premise 3 (Dual-Layer Replacement Architecture)**:
   - On Android API 26+ and modern JVM, `java.nio.file.Files.move(src, dst, REPLACE_EXISTING, ATOMIC_MOVE)` executes an atomic filesystem rename without separate delete steps.
   - On filesystems where `ATOMIC_MOVE` is not supported (e.g. FAT32/exFAT SD cards or cross-directory moves) or Android < API 26, a transactional **backup-and-restore** pattern (`target -> backup` -> `staging -> target` -> delete backup, with automatic rollback to `target` on failure) ensures that the original file is never deleted until the new file is confirmed in place.
4. **Premise 4 (Preservation of User Artwork Intent)**:
   - A `null` artwork URI on a track does not mean "delete embedded album art". It means the track does not have a separate custom cached image.
   - Embedded artwork in FLAC is stored in Type 6 `METADATA_BLOCK_PICTURE` blocks.
   - Artwork must only be removed if the user explicitly clicked "Remove Artwork" (`removeArtwork == true`).
   - Adding an explicit `removeArtwork: Boolean = false` field to `TagEditPayload` allows the UI (`TagEditorBottomSheet`) to communicate explicit removal, while defaulting to `false` for all text edits and test payloads.
5. **Conclusion**:
   - Centralize safe atomic file replacement in `ScopedStorageRouter.safeAtomicReplace(targetFile: File, stagingFile: File): Boolean`.
   - Refactor `FlacTagWriter.writeTagsToFlacFile`, `M4aTagWriter.writeTagsToM4aFile`, and `Id3TagWriter.writeTagsToMp3File` to use `ScopedStorageRouter.safeAtomicReplace`.
   - Add `val removeArtwork: Boolean = false` to `TagEditPayload`.
   - Change `FlacTagWriter.writeTagsToFlacFile` to use `removeArtwork = payload.removeArtwork`.
   - Wire `isArtworkRemoved` from `TagEditorBottomSheet` into `TagEditPayload(..., removeArtwork = isArtworkRemoved)`.

---

## 3. Caveats

1. **Filesystem Boundaries (`EXDEV`)**: If a caller directs `stagingDir` to a filesystem different from `targetFile.parentFile` (e.g. app internal cache vs external SD card), POSIX `rename()` fails with `EXDEV` (Cross-device link). `safeAtomicReplace` includes a stream copy fallback with rollback protection to handle this safely.
2. **Windows NTFS File Locks**: On Windows (host OS), open file streams prevent renames. All `FileInputStream`, `FileOutputStream`, and `RandomAccessFile` instances must be closed before invoking `safeAtomicReplace`. In `M4aTagWriter.kt`, `RandomAccessFile` should be wrapped in `.use { ... }` to guarantee closure even if an exception occurs during atom parsing.
3. **Other Container Writers**: While the mission dispatched `FlacTagWriter` and `M4aTagWriter`, `Id3TagWriter.writeTagsToMp3File` suffers from the identical non-atomic swap flaw and benefits directly from using `ScopedStorageRouter.safeAtomicReplace`.

---

## 4. Conclusion & Actionable Fix Strategy

### 4.1 Fix 1: Implement `ScopedStorageRouter.safeAtomicReplace`

#### Target File: `app/src/main/java/com/example/gobeats/core/media/tag/ScopedStorageRouter.kt`
Add the following method to `ScopedStorageRouter`:

```kotlin
    /**
     * Atomically replaces targetFile with stagingFile with zero data loss risk.
     * Uses Files.move with ATOMIC_MOVE where supported (API 26+ / JVM),
     * backed by a transactional backup-and-restore pattern:
     * 1. Rename targetFile -> backupFile (.bak_<timestamp>)
     * 2. Rename stagingFile -> targetFile
     * 3. On success: delete backupFile
     * 4. On failure: restore backupFile -> targetFile and clean up stagingFile
     *
     * Guarantee: targetFile is NEVER deleted before stagingFile is safely verified and in place.
     */
    fun safeAtomicReplace(targetFile: File, stagingFile: File): Boolean {
        if (!stagingFile.exists() || stagingFile.length() == 0L) {
            stagingFile.delete()
            return false
        }
        val parentDir = targetFile.parentFile ?: stagingFile.parentFile ?: run {
            stagingFile.delete()
            return false
        }

        // If target file doesn't exist yet, simply move staging to target
        if (!targetFile.exists()) {
            return stagingFile.renameTo(targetFile) || tryCopyAndCleanup(stagingFile, targetFile)
        }

        // Tier 1: Primary ATOMIC_MOVE via Files.move (API 26+ / JVM)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O || Build.VERSION.SDK_INT == 0) {
            try {
                java.nio.file.Files.move(
                    stagingFile.toPath(),
                    targetFile.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE
                )
                return true
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                // Filesystem does not support ATOMIC_MOVE; fall through to transactional backup
            } catch (_: UnsupportedOperationException) {
                // Fall through to transactional backup
            } catch (_: Throwable) {
                // Any other filesystem exception, fall through to transactional backup
            }
        }

        // Tier 2: Transactional Backup-and-Restore
        val backupFile = File(parentDir, "${targetFile.name}.bak_${System.currentTimeMillis()}_${System.nanoTime() % 10000}")
        if (backupFile.exists()) backupFile.delete()

        // Step A: Move targetFile to backupFile
        if (!targetFile.renameTo(backupFile)) {
            // Cannot rename targetFile; targetFile remains 100% intact. Clean up staging and fail safely.
            stagingFile.delete()
            return false
        }

        // Step B: Move stagingFile to targetFile
        val swapSuccess = if (stagingFile.renameTo(targetFile)) {
            true
        } else {
            // Cross-filesystem or rename failure: copy staging to target
            tryCopyAndCleanup(stagingFile, targetFile)
        }

        if (swapSuccess) {
            // Success: Clean up backup
            backupFile.delete()
            return true
        } else {
            // Failure: Rollback to original backup
            if (!backupFile.renameTo(targetFile)) {
                try {
                    backupFile.inputStream().use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    backupFile.delete()
                } catch (_: Exception) {
                    // Backup file is preserved on disk so user data is never lost!
                }
            }
            stagingFile.delete()
            return false
        }
    }

    private fun tryCopyAndCleanup(source: File, destination: File): Boolean {
        return try {
            source.inputStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            source.delete()
            true
        } catch (_: Exception) {
            false
        }
    }
```

---

### 4.2 Fix 2: Refactor `FlacTagWriter.kt` to use `safeAtomicReplace`

#### Target File: `app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt`
Replace lines 343–358:

**Before:**
```kotlin
            // Staging validation
            if (!ScopedStorageRouter.validateStagingFile(tempFile, FLAC_MAGIC)) {
                tempFile.delete()
                return false
            }

            if (file.delete()) {
                tempFile.renameTo(file)
            } else {
                tempFile.delete()
                false
            }
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
```

**After:**
```kotlin
            // Staging validation
            if (!ScopedStorageRouter.validateStagingFile(tempFile, FLAC_MAGIC)) {
                tempFile.delete()
                return false
            }

            ScopedStorageRouter.safeAtomicReplace(targetFile = file, stagingFile = tempFile)
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
```

---

### 4.3 Fix 3: Refactor `M4aTagWriter.kt` to use `safeAtomicReplace`

#### Target File: `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`
Replace lines 242–264:

**Before:**
```kotlin
            // 6. Validate staging file and commit via atomic swap
            val isValid = ScopedStorageRouter.validateStagingFile(
                stagingFile = tempFile,
                expectedMagic = FTYP_MAGIC,
                offset = 4,
                minLength = 32L
            )

            if (isValid) {
                if (file.delete()) {
                    tempFile.renameTo(file)
                } else {
                    tempFile.delete()
                    false
                }
            } else {
                tempFile.delete()
                false
            }
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
```

**After:**
```kotlin
            // 6. Validate staging file and commit via atomic swap
            val isValid = ScopedStorageRouter.validateStagingFile(
                stagingFile = tempFile,
                expectedMagic = FTYP_MAGIC,
                offset = 4,
                minLength = 32L
            )

            if (!isValid) {
                tempFile.delete()
                false
            } else {
                ScopedStorageRouter.safeAtomicReplace(targetFile = file, stagingFile = tempFile)
            }
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
```

*(Note for worker: In `M4aTagWriter.kt`, wrap `RandomAccessFile(file, "r")` in `.use { raf -> ... }` to guarantee handle release on exception).*

---

### 4.4 Fix 4: Add `removeArtwork` to `TagEditPayload` & Wire UI

#### Target File: `app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt` (lines 15–25)

**Before:**
```kotlin
data class TagEditPayload(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val artworkUri: String? = null,
    val newArtworkBytes: ByteArray? = null
)
```

**After:**
```kotlin
data class TagEditPayload(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: Int? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val artworkUri: String? = null,
    val newArtworkBytes: ByteArray? = null,
    val removeArtwork: Boolean = false
)
```

#### Target File: `app/src/main/java/com/example/gobeats/feature/library/TagEditorBottomSheet.kt` (lines 350–362)

**Before:**
```kotlin
val finalArtworkUri = if (isArtworkRemoved) null else currentArtworkUri
val payload = TagEditPayload(
    trackId = track.id,
    title = title.ifBlank { track.title },
    artist = artist.ifBlank { track.artistName },
    album = album.ifBlank { track.albumTitle },
    year = yearText.toIntOrNull(),
    genre = genreText.takeIf { it.isNotBlank() },
    trackNumber = trackNumberText.toIntOrNull(),
    artworkUri = finalArtworkUri,
    newArtworkBytes = selectedArtworkBytes
)
```

**After:**
```kotlin
val finalArtworkUri = if (isArtworkRemoved) null else currentArtworkUri
val payload = TagEditPayload(
    trackId = track.id,
    title = title.ifBlank { track.title },
    artist = artist.ifBlank { track.artistName },
    album = album.ifBlank { track.albumTitle },
    year = yearText.toIntOrNull(),
    genre = genreText.takeIf { it.isNotBlank() },
    trackNumber = trackNumberText.toIntOrNull(),
    artworkUri = finalArtworkUri,
    newArtworkBytes = selectedArtworkBytes,
    removeArtwork = isArtworkRemoved
)
```

---

### 4.5 Fix 5: Update `FlacTagWriter.writeTagsToFlacFile` Artwork Handling

#### Target File: `app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt` (lines 306–336)

**Before:**
```kotlin
    fun writeTagsToFlacFile(
        file: File,
        payload: TagEditPayload,
        vendorString: String = DEFAULT_VENDOR_STRING,
        stagingDir: File? = null
    ): Boolean {
        ...
            val success = FileInputStream(file).use { input ->
                FileOutputStream(tempFile).use { output ->
                    writeTagsToStreams(
                        input = input,
                        output = output,
                        comments = comments,
                        artworkBytes = payload.newArtworkBytes,
                        removeArtwork = payload.artworkUri == null && payload.newArtworkBytes == null,
                        vendorString = vendorString
                    )
                }
            }
```

**After:**
```kotlin
    fun writeTagsToFlacFile(
        file: File,
        payload: TagEditPayload,
        vendorString: String = DEFAULT_VENDOR_STRING,
        stagingDir: File? = null,
        removeArtwork: Boolean = payload.removeArtwork
    ): Boolean {
        ...
            val success = FileInputStream(file).use { input ->
                FileOutputStream(tempFile).use { output ->
                    writeTagsToStreams(
                        input = input,
                        output = output,
                        comments = comments,
                        artworkBytes = payload.newArtworkBytes,
                        removeArtwork = removeArtwork,
                        vendorString = vendorString
                    )
                }
            }
```

---

## 5. Verification Method

### 5.1 Test Unit 1: Verify Safe Atomic Replacement & Rollback
Add the following tests to `Milestone2EmpiricalStressTest.kt` or `FlacVorbisScopedStorageStressTest.kt`:

```kotlin
@Test
fun atomicSwap_replacesTargetFile_withoutDataLoss() {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "swap_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()
    try {
        val target = File(tempDir, "target.txt").apply { writeText("ORIGINAL_CONTENT") }
        val staging = File(tempDir, "staging.tmp").apply { writeText("NEW_CONTENT") }

        val result = ScopedStorageRouter.safeAtomicReplace(target, staging)
        assertTrue("safeAtomicReplace must return true", result)
        assertEquals("NEW_CONTENT", target.readText())
        assertFalse("Staging file must be cleaned up", staging.exists())
    } finally {
        tempDir.deleteRecursively()
    }
}

@Test
fun atomicSwap_invalidStaging_preservesOriginalTargetFile() {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "swap_fail_test_${System.currentTimeMillis()}")
    tempDir.mkdirs()
    try {
        val target = File(tempDir, "target.txt").apply { writeText("ORIGINAL_CONTENT") }
        val staging = File(tempDir, "non_existent.tmp")

        val result = ScopedStorageRouter.safeAtomicReplace(target, staging)
        assertFalse("safeAtomicReplace must return false for missing staging", result)
        assertEquals("Original content must be preserved intact", "ORIGINAL_CONTENT", target.readText())
    } finally {
        tempDir.deleteRecursively()
    }
}
```

### 5.2 Test Unit 2: Verify FLAC Artwork Preservation on Text-Only Edits
```kotlin
@Test
fun flac_textOnlyEdit_preservesEmbeddedArtworkBlock() {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "flac_art_preservation_${System.currentTimeMillis()}")
    tempDir.mkdirs()
    try {
        val flacFile = File(tempDir, "art_test.flac")
        val dummyCoverBytes = ByteArray(4096) { 0x7E.toByte() }
        val picBlock = FlacTagWriter.buildPictureBlock(
            imageBytes = dummyCoverBytes,
            mimeType = "image/jpeg",
            pictureType = FlacTagWriter.PICTURE_TYPE_FRONT_COVER,
            isLast = false
        )
        val picBlockObj = FlacTagWriter.RawMetadataBlock(
            type = FlacTagWriter.BLOCK_TYPE_PICTURE,
            isLast = false,
            payload = picBlock.copyOfRange(4, picBlock.size)
        )

        // Synthesize FLAC stream with STREAMINFO + PICTURE + VORBIS_COMMENT + Audio
        flacFile.writeBytes(createSyntheticFlacStream(
            includeComments = true,
            extraBlocks = listOf(picBlockObj),
            audioFrameBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        ))

        // Edit ONLY text metadata (artworkUri is null, newArtworkBytes is null, removeArtwork is false)
        val textOnlyPayload = TagEditPayload(
            trackId = "flac_text_edit",
            title = "Updated Title",
            artist = "Updated Artist",
            album = "Updated Album",
            artworkUri = null,
            newArtworkBytes = null,
            removeArtwork = false
        )

        val written = FlacTagWriter.writeTagsToFlacFile(flacFile, textOnlyPayload, stagingDir = tempDir)
        assertTrue("writeTagsToFlacFile must succeed", written)

        // Read rewritten file blocks
        val blocks = FlacTagWriter.readMetadataBlocks(flacFile.inputStream())
        val foundPicBlock = blocks.find { it.type == FlacTagWriter.BLOCK_TYPE_PICTURE }
        assertNotNull("METADATA_BLOCK_PICTURE must be retained on text-only tag edit", foundPicBlock)

        val picHeader = ByteArray(4).apply {
            this[0] = 0x06.toByte()
            this[1] = ((foundPicBlock!!.payload.size shr 16) and 0xFF).toByte()
            this[2] = ((foundPicBlock.payload.size shr 8) and 0xFF).toByte()
            this[3] = (foundPicBlock.payload.size and 0xFF).toByte()
        }
        val extractedPic = FlacTagWriter.parsePictureBlock(picHeader + foundPicBlock.payload)
        assertNotNull("Extracted image bytes must be valid", extractedPic)
        assertArrayEquals("Extracted image bytes must match original embedded artwork bit-for-bit", dummyCoverBytes, extractedPic)
    } finally {
        tempDir.deleteRecursively()
    }
}
```

### 5.3 Test Unit 3: Verify Explicit Artwork Removal
```kotlin
@Test
fun flac_explicitArtworkRemoval_deletesPictureBlock() {
    val tempDir = File(System.getProperty("java.io.tmpdir"), "flac_art_removal_${System.currentTimeMillis()}")
    tempDir.mkdirs()
    try {
        val flacFile = File(tempDir, "art_remove_test.flac")
        val dummyCoverBytes = ByteArray(1024) { 0x33.toByte() }
        val picBlock = FlacTagWriter.buildPictureBlock(dummyCoverBytes, isLast = false)
        val picBlockObj = FlacTagWriter.RawMetadataBlock(
            type = FlacTagWriter.BLOCK_TYPE_PICTURE,
            isLast = false,
            payload = picBlock.copyOfRange(4, picBlock.size)
        )

        flacFile.writeBytes(createSyntheticFlacStream(
            includeComments = true,
            extraBlocks = listOf(picBlockObj)
        ))

        val removalPayload = TagEditPayload(
            trackId = "flac_remove_art",
            title = "Title Without Art",
            artist = "Artist",
            album = "Album",
            removeArtwork = true
        )

        val written = FlacTagWriter.writeTagsToFlacFile(flacFile, removalPayload, stagingDir = tempDir)
        assertTrue("writeTagsToFlacFile must succeed", written)

        val blocks = FlacTagWriter.readMetadataBlocks(flacFile.inputStream())
        assertNull("METADATA_BLOCK_PICTURE must be removed when removeArtwork is true",
            blocks.find { it.type == FlacTagWriter.BLOCK_TYPE_PICTURE })
    } finally {
        tempDir.deleteRecursively()
    }
}
```

### 5.4 Command Execution
```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```
*Expected*: BUILD SUCCESSFUL with 0 errors and all unit tests passing.
