# Handoff Report: M4A ilst Tag & Artwork Preservation and UniversalTagWriter Stream Mode Fix Strategy

**Agent:** `explorer_m2_r2_3`  
**Roles:** `codebase & spec explorer`  
**Milestone:** Milestone 2 (Universal Tag Editor & Scoped Storage)  
**Date:** 2026-10-06  
**Type:** Hard Handoff  

---

## 1. Observation

### 1.1 Source Code Observations: M4A ilst Metadata Obliteration
File: `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`
- **Lines 101–132 (`buildIlstBox`)**:
  ```kotlin
  fun buildIlstBox(payload: TagEditPayload): ByteArray {
      val children = mutableListOf<ByteArray>()
      if (payload.title.isNotBlank()) {
          children.add(buildIlstTextAtom("\u00A9nam", payload.title.trim()))
      }
      if (payload.artist.isNotBlank()) {
          children.add(buildIlstTextAtom("\u00A9ART", payload.artist.trim()))
      }
      if (payload.album.isNotBlank()) {
          children.add(buildIlstTextAtom("\u00A9alb", payload.album.trim()))
      }
      payload.year?.takeIf { it > 0 }?.let {
          children.add(buildIlstTextAtom("\u00A9day", it.toString()))
      }
      payload.genre?.takeIf { it.isNotBlank() }?.let {
          children.add(buildIlstTextAtom("\u00A9gen", it.trim()))
      }
      payload.trackNumber?.takeIf { it > 0 }?.let {
          children.add(buildIlstTrackNumberAtom(it))
      }
      payload.newArtworkBytes?.takeIf { it.isNotEmpty() }?.let {
          children.add(buildIlstCoverArtAtom(it))
      }
      ...
  ```
- **Lines 200–219 (`writeTagsToM4aFile`)**:
  ```kotlin
  val ilstBytes = buildIlstBox(payload)
  val hdlrBox = buildStandardMetadataHdlr()
  val metaSize = 12 + hdlrBox.size + ilstBytes.size
  ...
  val udtaSize = 8 + metaSize
  val udtaBuf = ByteBuffer.allocate(udtaSize)...
  val updatedMoov = replaceOrAppendUdta(moovBytes, udtaBuf.array())
  ```
- **Observed Behavior**:
  1. `buildIlstBox` constructs an `ilst` box strictly from the fields present in `payload` without reading or preserving the source file's existing `ilst` atoms.
  2. If the user edits only the track title or artist, `payload.newArtworkBytes` is `null`. Any existing embedded album cover art (`covr` atom) in the M4A file is silently discarded.
  3. Metadata atoms not included in `TagEditPayload`—such as composer (`©wrt`), comments (`©cmt`), disc number (`disk`), compilation (`cpil`), album artist (`aART`), tempo/BPM (`tmpo`), grouping (`©grp`), lyrics (`©lyr`), and custom reverse-DNS tags (`----`)—are completely obliterated when `replaceOrAppendUdta` replaces the container box.

### 1.2 Source Code Observations: Invalid Stream Mode `"r"` in UniversalTagWriter
File: `app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt`
- **Lines 79–94 (`writeTags` 4-stage pipeline)**:
  ```kotlin
  if (mechanism == StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST && !canDirectWrite && contentUri != null) {
      val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, contentUri)
      if (intentSender != null) {
          var needsPermission = false
          try {
              // Test if write channel is available
              context.contentResolver.openOutputStream(contentUri, "r")?.close()
          } catch (_: SecurityException) {
              needsPermission = true
          }
          if (needsPermission) {
              throw ScopedStoragePermissionRequiredException(intentSender, contentUri)
          }
      }
  }
  ```
- **Lines 223–236 (`writeTags` URI overload)**:
  ```kotlin
  if (mechanism == StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST) {
      val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, uri)
      if (intentSender != null) {
          var needsPermission = false
          try {
              context.contentResolver.openOutputStream(uri, "r")?.close()
          } catch (_: SecurityException) {
              needsPermission = true
          }
          if (needsPermission) {
              return@withContext Result.failure(ScopedStoragePermissionRequiredException(intentSender, uri))
          }
      }
  }
  ```
- **Observed Behavior**:
  1. Android's official contract for `ContentResolver.openOutputStream(Uri uri, String mode)` only supports write modes: `"w"`, `"wa"`, `"rw"`, and `"rwt"`. Mode `"r"` (read-only) is invalid for an output stream.
  2. Passing `"r"` causes Android `AssetFileDescriptor.createOutputStream()` or underlying `ContentProvider` implementations to throw `IllegalArgumentException: Invalid mode: r` or fail creating a `FileOutputStream` on a read-only descriptor.
  3. Because the probe catch block only catches `SecurityException`, any `IllegalArgumentException` goes unhandled and crashes execution.
  4. Even if `"r"` were accepted by a provider, it would test read access, not write access. On Android 11+ (API 30+), read access to public media is granted by `READ_MEDIA_AUDIO`/`READ_EXTERNAL_STORAGE`, so a read probe never throws `SecurityException` for write permissions.

### 1.3 Supporting Observations
- `TagEditPayload` in `app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt` (lines 15–25):
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
- `TagEditorBottomSheet.kt` (line 106) already maintains an `isArtworkRemoved` Boolean state:
  ```kotlin
  var isArtworkRemoved by remember { mutableStateOf(false) }
  ```
  However, `TagEditPayload` currently lacks an explicit `removeArtwork` flag, making it impossible for downstream tag writers to distinguish between "user kept existing artwork" (`newArtworkBytes == null`) and "user explicitly deleted artwork" (`isArtworkRemoved == true`).
- `UniversalTagWriter.kt` (line 154) uses `openOutputStream(contentUri, "wt")` when streaming the validated staging file back into ContentResolver. That write-truncate operation is valid for output streaming once the staging file is validated.

---

## 2. Logic Chain

1. **Premise 1 (Preservation of Non-Edited Metadata)**:
   In Tier-1 audio management software, editing a subset of metadata tags (e.g. correcting a misspelled title) must be non-destructive to existing metadata. Specifically, embedded cover art (`covr`), composer (`©wrt`), comments (`©cmt`), disc number (`disk`), compilation (`cpil`), and custom reverse-DNS atoms (`----`) must survive tag updates unless explicitly altered by the user.
2. **Premise 2 (ISO-BMFF / MP4 Box Atom Structure)**:
   In standard M4A containers:
   - `moov` contains `udta` (or optionally `meta` directly).
   - `udta` contains `meta` (a FullBox with 4 bytes of version/flags).
   - `meta` contains `hdlr` and `ilst`.
   - `ilst` contains individual metadata item atoms, each structured as `[size: 4 bytes][fourCc: 4 bytes][payload: (size - 8) bytes]`.
   - Each item atom is completely self-contained. Any atom whose FourCC is not in the list of edited fields can be retained bit-for-bit without requiring re-encoding or schema transformation.
3. **Premise 3 (Artwork Intent Disambiguation)**:
   - When `payload.newArtworkBytes != null && payload.newArtworkBytes.isNotEmpty()`: User selected a new image $\rightarrow$ Replace `covr`.
   - When `payload.removeArtwork == true`: User tapped remove $\rightarrow$ Delete `covr`.
   - When `payload.newArtworkBytes == null && !payload.removeArtwork`: User did not touch artwork $\rightarrow$ Preserve existing `covr` atom from the source file.
4. **Premise 4 (ContentResolver Output Stream Contract & Safe Probing)**:
   - Probing write permission by attempting to open a stream must neither corrupt the user's file nor violate Android's API contracts.
   - Calling `openOutputStream(contentUri, "wt")` or `"w"` before staging would immediately truncate the user's audio file to 0 bytes.
   - Calling `openOutputStream(contentUri, "r")` violates Android's valid output stream modes (`"w"`, `"wa"`, `"rw"`, `"rwt"`), causing `IllegalArgumentException`.
   - Opening a file descriptor via `context.contentResolver.openFileDescriptor(contentUri, "rw")` requests read-write access without `O_TRUNC` (preserving file contents), triggers `MediaProvider`'s write permission check (throwing `SecurityException` if unauthorized), and immediately closing `pfd?.close()` leaves the file 100% intact.

---

## 3. Caveats

1. **Legacy QuickTime vs. ISO FullBox `meta` Header**:
   Most modern MP4/M4A files encode `meta` as an ISO FullBox with a 12-byte header (4-byte size + 4-byte `"meta"` + 1-byte version + 3-byte flags). Ancient Apple QuickTime files (circa 2004) sometimes used an 8-byte header without version/flags. The parser must detect whether `ilst` begins at offset +12 or +8 inside `meta`.
2. **Direct `moov -> meta` vs. `moov -> udta -> meta`**:
   While iTunes and MediaStore place `meta` inside `udta`, some third-party tools place `meta` directly as a child of `moov`. The parser should inspect `moov -> udta -> meta -> ilst` first, falling back to `moov -> meta -> ilst` if `udta` is absent.
3. **Multiple Image Boxes Inside `covr`**:
   Some M4A files contain multiple images (e.g. front and back covers) within a single `covr` atom. By copying the entire raw `covr` atom byte array when artwork is preserved, all embedded images are maintained without data loss.
4. **Total Track Count in `trkn`**:
   The `trkn` atom contains both current track number and total track count (`[reserved: 2][track: 2][total: 2][reserved: 2]`). If the user updates only `trackNumber`, the existing `totalTracks` should be extracted from the original `trkn` atom and preserved rather than reset to 0.

---

## 4. Conclusion & Concrete Fix Strategy

### 4.1 Solution Architecture

```
M4A Metadata Update Flow:
Original File (moov)
       │
       ▼
parseExistingIlstAtoms(moovBytes)
       │
       ├─► Extracted: ©wrt, ©cmt, disk, cpil, covr, etc.
       │
       ▼
buildIlstBox(payload, existingAtoms)
       │
       ├─► Merge edited fields: ©nam, ©ART, ©alb, ©day, ©gen, trkn
       ├─► If newArtworkBytes != null ──► Replace covr
       ├─► If removeArtwork == true  ──► Delete covr
       ├─► Else                      ──► Preserve original covr
       ├─► Preserve untouched atoms: ©wrt, ©cmt, disk, cpil, aART, etc.
       │
       ▼
Assemble updated ilst ──► udta ──► updatedMoov
```

```
UniversalTagWriter Permission Probe:
contentUri & MEDIA_STORE_CREATE_WRITE_REQUEST
       │
       ▼
openFileDescriptor(contentUri, "rw")?.close()
       ├─► Throws SecurityException ──► Needs permission ──► Throw ScopedStoragePermissionRequiredException
       └─► Success (No exception)   ──► Has permission   ──► Proceed without prompt (File NOT truncated)
```

### 4.2 Proposed Code Changes

#### Change 1: Extend `TagEditPayload` with `removeArtwork`
In `app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt` (lines 15–26):
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
*Rationale*: Adding `removeArtwork: Boolean = false` with a default parameter maintains 100% backward compatibility with all existing callers and tests while cleanly conveying explicit artwork deletion.

#### Change 2: Connect `TagEditorBottomSheet.kt` and `FlacTagWriter.kt`
In `app/src/main/java/com/example/gobeats/feature/library/TagEditorBottomSheet.kt` (line 361):
```kotlin
    newArtworkBytes = selectedArtworkBytes,
    removeArtwork = isArtworkRemoved
)
```
In `app/src/main/java/com/example/gobeats/core/media/FlacTagWriter.kt` (line 332):
```kotlin
    removeArtwork = payload.removeArtwork,
```
*Rationale*: Eliminates the bug where FLAC/M4A cover art was discarded when editing only text tags.

#### Change 3: Implement Atom Parsing and Merging in `M4aTagWriter.kt`
In `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`:

```kotlin
    data class BoxHeader(
        val fourCc: String,
        val offset: Int,
        val headerSize: Int,
        val totalSize: Int
    )

    fun findChildBox(bytes: ByteArray, startOffset: Int, endOffset: Int, targetFourCc: String): BoxHeader? {
        if (startOffset + 8 > endOffset || endOffset > bytes.size) return null
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        var p = startOffset
        while (p + 8 <= endOffset) {
            val sizeInt = buf.getInt(p)
            val fourCcBytes = ByteArray(4)
            buf.position(p + 4)
            buf.get(fourCcBytes)
            val fourCc = String(fourCcBytes, StandardCharsets.ISO_8859_1)

            var headerSize = 8
            var boxSize = (sizeInt.toLong() and 0xFFFFFFFFL).toInt()
            if (sizeInt == 1) {
                if (p + 16 > endOffset) break
                val largeSize = buf.getLong(p + 8)
                headerSize = 16
                boxSize = largeSize.toInt()
            } else if (sizeInt == 0) {
                boxSize = endOffset - p
            }

            if (boxSize < headerSize || p + boxSize > endOffset) break

            if (fourCc == targetFourCc) {
                return BoxHeader(fourCc, p, headerSize, boxSize)
            }
            p += boxSize
        }
        return null
    }

    /**
     * Parses existing metadata atoms from the moov box (moov -> udta -> meta -> ilst, or moov -> meta -> ilst).
     * Returns a LinkedHashMap of FourCC to full raw atom ByteArrays (including atom header).
     */
    fun parseExistingIlstAtoms(moovBytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val atoms = LinkedHashMap<String, ByteArray>()
        if (moovBytes.size < 16) return atoms

        // Locate 'meta' box: check under 'udta' first, then direct under 'moov'
        val udtaBox = findChildBox(moovBytes, 8, moovBytes.size, "udta")
        val metaBox = if (udtaBox != null) {
            findChildBox(moovBytes, udtaBox.offset + udtaBox.headerSize, udtaBox.offset + udtaBox.totalSize, "meta")
        } else {
            findChildBox(moovBytes, 8, moovBytes.size, "meta")
        } ?: return atoms

        // Locate 'ilst' box inside 'meta':
        // Standard ISO/Apple MP4 meta box is a FullBox with 12-byte header (size + 'meta' + 4-byte version/flags)
        // Legacy QuickTime meta has an 8-byte header. Test both.
        val metaEnd = metaBox.offset + metaBox.totalSize
        var ilstBox = if (metaBox.offset + 12 <= metaEnd) {
            findChildBox(moovBytes, metaBox.offset + 12, metaEnd, "ilst")
        } else null
        if (ilstBox == null && metaBox.offset + 8 <= metaEnd) {
            ilstBox = findChildBox(moovBytes, metaBox.offset + 8, metaEnd, "ilst")
        }
        if (ilstBox == null) return atoms

        // Iterate through all child item atoms in ilst
        var p = ilstBox.offset + ilstBox.headerSize
        val ilstEnd = ilstBox.offset + ilstBox.totalSize
        val buf = ByteBuffer.wrap(moovBytes).order(ByteOrder.BIG_ENDIAN)

        while (p + 8 <= ilstEnd) {
            val atomSize = buf.getInt(p)
            if (atomSize < 8 || p + atomSize > ilstEnd) break
            val fourCcBytes = ByteArray(4)
            buf.position(p + 4)
            buf.get(fourCcBytes)
            val fourCc = String(fourCcBytes, StandardCharsets.ISO_8859_1)

            val atomBytes = moovBytes.copyOfRange(p, p + atomSize)
            atoms[fourCc] = atomBytes
            p += atomSize
        }

        return atoms
    }

    /**
     * Assembles the 'ilst' container box, merging fields from TagEditPayload with any existing
     * atoms parsed from the original file (preserving composer ©wrt, comments ©cmt, disc number disk,
     * compilation cpil, album artist aART, and existing artwork covr unless replaced or removed).
     */
    fun buildIlstBox(
        payload: TagEditPayload,
        existingAtoms: Map<String, ByteArray> = emptyMap()
    ): ByteArray {
        val mergedAtoms = LinkedHashMap<String, ByteArray>(existingAtoms)

        // 1. Title (©nam)
        if (payload.title.isNotBlank()) {
            mergedAtoms["\u00A9nam"] = buildIlstTextAtom("\u00A9nam", payload.title.trim())
        }

        // 2. Artist (©ART)
        if (payload.artist.isNotBlank()) {
            mergedAtoms["\u00A9ART"] = buildIlstTextAtom("\u00A9ART", payload.artist.trim())
        }

        // 3. Album (©alb)
        if (payload.album.isNotBlank()) {
            mergedAtoms["\u00A9alb"] = buildIlstTextAtom("\u00A9alb", payload.album.trim())
        }

        // 4. Year (©day)
        if (payload.year != null && payload.year > 0) {
            mergedAtoms["\u00A9day"] = buildIlstTextAtom("\u00A9day", payload.year.toString())
        }

        // 5. Genre (©gen)
        if (!payload.genre.isNullOrBlank()) {
            mergedAtoms["\u00A9gen"] = buildIlstTextAtom("\u00A9gen", payload.genre.trim())
            mergedAtoms.remove("gnre") // Remove legacy ID3v1 genre atom to avoid conflicts
        }

        // 6. Track Number (trkn)
        if (payload.trackNumber != null && payload.trackNumber > 0) {
            var totalTracks = 0
            existingAtoms["trkn"]?.let { trknBytes ->
                if (trknBytes.size >= 30) {
                    val buf = ByteBuffer.wrap(trknBytes).order(ByteOrder.BIG_ENDIAN)
                    totalTracks = buf.getShort(28).toInt() and 0xFFFF
                }
            }
            mergedAtoms["trkn"] = buildIlstTrackNumberAtom(payload.trackNumber, totalTracks)
        }

        // 7. Cover Artwork (covr)
        if (payload.newArtworkBytes != null && payload.newArtworkBytes.isNotEmpty()) {
            mergedAtoms["covr"] = buildIlstCoverArtAtom(payload.newArtworkBytes)
        } else if (payload.removeArtwork) {
            mergedAtoms.remove("covr")
        }
        // If payload.newArtworkBytes == null && !payload.removeArtwork, existingAtoms["covr"] is kept!

        val totalChildrenSize = mergedAtoms.values.sumOf { it.size }
        val ilstSize = 8 + totalChildrenSize
        val ilst = ByteBuffer.allocate(ilstSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(ilstSize)
            put("ilst".toByteArray(StandardCharsets.US_ASCII))
            mergedAtoms.values.forEach { put(it) }
        }
        return ilst.array()
    }
```

In `writeTagsToM4aFile` (lines 200–202):
```kotlin
            // 2. Build new udta with meta and ilst, merging with existing atoms
            val existingAtoms = parseExistingIlstAtoms(moovBytes)
            val ilstBytes = buildIlstBox(payload, existingAtoms)
            val hdlrBox = buildStandardMetadataHdlr()
            val metaSize = 12 + hdlrBox.size + ilstBytes.size
            ...
```

#### Change 4: Fix Output Stream Mode Probe in `UniversalTagWriter.kt`
In `app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt`:

Replace lines 83–93:
```kotlin
            val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, contentUri)
            if (intentSender != null) {
                var needsPermission = false
                try {
                    // Test if write channel is available using non-truncating read-write file descriptor probe
                    context.contentResolver.openFileDescriptor(contentUri, "rw")?.close()
                } catch (_: SecurityException) {
                    needsPermission = true
                } catch (_: Exception) {
                    // Ignore non-permission probe failures
                }
                if (needsPermission) {
                    throw ScopedStoragePermissionRequiredException(intentSender, contentUri)
                }
            }
```

Replace lines 226–236:
```kotlin
                val intentSender = ScopedStorageRouter.requestWritePermissionIntentSender(context, uri)
                if (intentSender != null) {
                    var needsPermission = false
                    try {
                        // Test if write channel is available using non-truncating read-write file descriptor probe
                        context.contentResolver.openFileDescriptor(uri, "rw")?.close()
                    } catch (_: SecurityException) {
                        needsPermission = true
                    } catch (_: Exception) {
                    }
                    if (needsPermission) {
                        return@withContext Result.failure(ScopedStoragePermissionRequiredException(intentSender, uri))
                    }
                }
```

---

## 5. Verification Method

### 5.1 Compilation Verification
Execute Gradle Kotlin compilation to confirm zero type errors or signature mismatches:
```powershell
.\gradlew.bat compileDebugKotlin
```
*Expected Result*: `BUILD SUCCESSFUL` with 0 warnings or errors.

### 5.2 Unit Test Verification
Execute the test baseline to verify regression safety:
```powershell
.\gradlew.bat testDebugUnitTest
```
*Expected Result*: All 211 tests pass with 100% green status.

### 5.3 M4A Metadata Preservation Empirical Verification
Write a unit test in `Milestone2EmpiricalStressTest.kt`:
1. Synthesize an M4A file containing:
   - `©nam`: "Original Title"
   - `©ART`: "Original Artist"
   - `©wrt`: "Ludwig van Beethoven" (composer)
   - `©cmt`: "Remastered 2026" (comment)
   - `disk`: Disc 1 of 2
   - `covr`: JPEG byte array `[0xFF, 0xD8, ...]`
2. Call `M4aTagWriter.writeTagsToM4aFile` updating ONLY `title = "Updated Title"`.
3. Parse the rewritten file:
   - Verify `©nam` == "Updated Title".
   - Verify `©wrt` is still present and equals "Ludwig van Beethoven".
   - Verify `©cmt` is still present and equals "Remastered 2026".
   - Verify `disk` atom is preserved.
   - Verify `covr` byte array is identical to the original JPEG bytes.
4. Call `M4aTagWriter.writeTagsToM4aFile` with `removeArtwork = true`:
   - Verify `covr` is absent.
   - Verify other metadata remains intact.

### 5.4 ContentResolver Probe Invalidation & Safety Verification
1. Call probe logic on a mock or Robolectric ContentResolver:
   - Confirm `openFileDescriptor(contentUri, "rw")` does not throw `IllegalArgumentException`.
   - Confirm throwing `SecurityException` accurately yields `ScopedStoragePermissionRequiredException`.
   - Confirm the target file size is identical before and after the probe (0% truncation).
