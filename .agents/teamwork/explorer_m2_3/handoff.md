# Handoff Report: Milestone 2 Feature F8 (M4A/MP4 Tag Editor & stco Chunk Offsets) and Feature F9 (Tag Sync Pipeline)

**Author:** `explorer_m2_3` (Codebase & Spec Explorer)  
**Date:** 2026-10-06  
**Status:** Investigation Complete & Verified  
**Working Directory:** `c:\Users\ayush\OneDrive\Documents\gobeats\.agents\teamwork\explorer_m2_3`  

---

## 1. Observation

### 1.1 Existing Codebase & Tag Handling Baseline
Direct code inspection revealed the current state of tag editing in GoBeats:
1. **`app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt` (lines 1-282)**:
   - GoBeats contains a pure Kotlin ID3v2.3 tag writer for MP3 files (`buildId3v23Tag`, `buildTextFrame`, `buildApicFrame`, `writeTagsToMp3File`).
   - Line 15-25: `data class TagEditPayload(trackId: String, title: String, artist: String, album: String, year: Int? = null, genre: String? = null, trackNumber: Int? = null, artworkUri: String? = null, newArtworkBytes: ByteArray? = null)`.
   - Lines 174-217: Uses staging file (`file.name + ".tmp_${System.currentTimeMillis()}"`), strips old ID3 tag, prepends new ID3 tag, streams MPEG audio frames, and replaces via atomic rename (`stagingFile.renameTo(file)`).
   - Lines 222-244: `updateMediaStoreTags(context, contentUri, title, artist, album, year)` updates Android `MediaStore.Audio.Media` `ContentValues`.
2. **`app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` (lines 63-119)**:
   - Lines 75-76: `val isMp3 = track.fileUri.endsWith(".mp3", ignoreCase = true) || track.format.contains("mp3", ignoreCase = true)`.
   - Only MP3 files have their raw files updated. For M4A/MP4 and FLAC tracks, raw files on disk are completely bypassed.
   - Lines 114-118:
     ```kotlin
     val updated = dbHelper.updateTrackMetadata(finalPayload)
     if (updated != null) {
         playbackConnection?.notifyTrackMetadataChanged(updated)
     }
     ```
3. **`app/src/main/java/com/example/gobeats/core/database/GobeatsDatabaseHelper.kt` (lines 229, 304, 887-898)**:
   - Line 229: Table `track_metadata_overrides` exists with schema: `(track_id TEXT PRIMARY KEY, title TEXT, artist TEXT, album TEXT, genre TEXT, year INTEGER, artwork_uri TEXT, updated_at INTEGER)`.
   - Lines 303-320: In `insertOrUpdateTrack()`, GoBeats queries `track_metadata_overrides` and overlays user edits over disk/scanner tags.
   - Lines 888-898: In `updateTrackMetadata()`, user edits are persisted into `track_metadata_overrides` via `CONFLICT_REPLACE`.
4. **`app/src/main/java/com/example/gobeats/core/media/PlaybackConnection.kt` (lines 904-927)**:
   - `notifyTrackMetadataChanged(updatedTrack: Track)`:
     - Updates `currentQueue[index] = updatedTrack`.
     - Updates `_playbackState` (queue and `currentTrack` if active).
     - Live-updates ExoPlayer/Media3 via `controller?.replaceMediaItem(index, buildMediaItem(updatedTrack))`.

### 1.2 Test Suite Specifications for Feature F8 & F9
Direct inspection of tests across all tiers revealed:
1. **`app/src/test/java/com/example/gobeats/GobeatsTier1FeatureCoverageTest.kt` (lines 771-876)**:
   - Object `M4aChunkOffsetEngine` specifies:
     ```kotlin
     fun adjustStcoOffsets(
         originalOffsets: List<Int>,
         moovDelta: Int,
         mdatStartOffset: Int
     ): List<Int> {
         return originalOffsets.map { offset ->
             if (offset >= mdatStartOffset) offset + moovDelta else offset
         }
     }
     ```
   - Lines 789-808: `buildIlstTextAtom(fourCc: String, value: String): ByteArray`:
     - Checks `require(fourCc.length == 4)`.
     - Parent box: 4-byte size (`8 + dataBoxSize`), 4-byte FourCC (`fourCc.toByteArray(StandardCharsets.ISO_8859_1)`).
     - Child `data` box: 4-byte size (`16 + textBytes.size`), 4-byte `'data'`, 4-byte flags `1` (UTF-8), 4-byte locale `0`, payload bytes.
   - Lines 812-875:
     - `f8_01_stcoChunkOffsets_shiftByExactDelta`: Offsets `[2048, 4096, 6144, 8192]`, `moovDelta = 120`, `mdatStart = 1024` -> all shift by +120.
     - `f8_02_stcoOffsets_beforeMdat_areNotShifted`: Offset `500 < 1000` remains unshifted; offsets `>= 1000` shift.
     - `f8_03_ilstTextAtom_createsValidBoxHierarchy`: FourCC `\u00A9nam`, `Starboy`, total size and flags verified.
     - `f8_04_m4aAudioPayload_remainsIntactDuringTagUpdate`: Audio payload in `mdat` copied bit-perfectly.
     - `f8_05_negativeMoovDelta_shrinksOffsetsAccurately`: Negative delta `-80` correctly decreases offsets.
2. **`GobeatsTier1FeatureCoverageTest.kt` (lines 880-972)**:
   - Lines 920-971:
     - `f9_01_tagSave_recordsDatabaseOverride`: Saves override in database.
     - `f9_02_activePlayingTrack_updatesPlaybackSessionImmediately`: Updates active playback session.
     - `f9_03_nonActiveTrack_doesNotTouchActivePlaybackSession`: Non-active tracks do not touch active session.
     - `f9_04_libraryRescan_preservesUserOverrides`: Rescan preserves user overrides.
     - `f9_05_tracksWithoutOverrides_useScannerMetadata`: Tracks without overrides use scanner metadata.
3. **`app/src/test/java/com/example/gobeats/GobeatsTier2BoundaryCornerTest.kt` (lines 430-526)**:
   - `f8_b01`: `moovDelta = 0` leaves offsets unchanged.
   - `f8_b02`: Empty offsets list returns empty list.
   - `f8_b03`: Empty string produces 24-byte box (8-byte parent + 16-byte data box with 0 payload).
   - `f8_b04`: FourCC length != 4 throws `IllegalArgumentException`.
   - `f8_b05`: Large `moovDelta = 500,000` shifts without truncation.
   - `f9_b01`: SQL injection payload (`Robert'); DROP TABLE tracks;--`) treated as literal string.
   - `f9_b02`: Multi-byte emoji (`🔥 Cosmic Drift 🚀 🎵`) retained intact.
   - `f9_b03`: Repeated saves update override with zero duplicate rows.
   - `f9_b04`: Empty string tags stored safely without NullPointerException.
   - `f9_b05`: Track ID mismatch does not apply override to unrelated track.
4. **`app/src/test/java/com/example/gobeats/GobeatsTier3CrossFeatureTest.kt` (lines 178-224)**:
   - `cross_06`: Public storage write mechanism on API 33 is `MEDIA_STORE_CREATE_WRITE_REQUEST`, with `stco` chunk offset adjustment.
   - `cross_07`: Lossless tag save registers DB override that protects against raw disk rescans.
5. **`app/src/test/java/com/example/gobeats/GobeatsTier4RealWorldWorkflowsTest.kt` (lines 158-205)**:
   - `workflow_03`: Full end-to-end workflow: scoped storage check -> build M4A atoms (`\u00A9nam`, `\u00A9ART`) -> compute `moovDelta` -> validate staging file (32-byte header with "ftyp" magic at offset 4) -> realign `stco` chunk offsets -> verify audio safety.

### 1.3 Execution Results
The test suite was run via `gradlew.bat`:
- Task: `.\gradlew.bat testDebugUnitTest --tests com.example.gobeats.GobeatsTier1FeatureCoverageTest` -> **BUILD SUCCESSFUL in 19s (0 failures, 100% green)**.
- Task: `.\gradlew.bat testDebugUnitTest --tests com.example.gobeats.GobeatsTier2BoundaryCornerTest --tests com.example.gobeats.GobeatsTier3CrossFeatureTest --tests com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest` -> **BUILD SUCCESSFUL in 15s (0 failures, 100% green)**.

---

## 2. Logic Chain

### 2.1 ISO BMFF Atom Architecture & M4A Tag Hierarchy
From Observation 1.1 and 1.2:
1. An M4A (or MPEG-4 Audio) file follows the ISO Base Media File Format (ISO/IEC 14496-12) and Apple QuickTime file structure:
   - Top-level atoms are consecutive 32-bit big-endian length + 4-byte FourCC blocks:
     - `ftyp`: File type atom (verifies branding, e.g., `M4A `, `mp42`, `isom`).
     - `moov`: Movie container atom holding all metadata, track declarations, and sample tables.
     - `mdat`: Media data atom containing raw AAC or ALAC compressed audio frames.
2. Metadata hierarchy inside `moov`:
   - Path: `moov` -> `udta` -> `meta` -> `ilst`.
   - `udta` (User Data Atom): Standard container box (`[size][udta]`).
   - `meta` (Metadata Atom): In ISO BMFF, `meta` is a **FullBox**:
     - 4 bytes size
     - 4 bytes `'meta'`
     - 1 byte version (`0x00`)
     - 3 bytes flags (`0x00, 0x00, 0x00`)
     - Total header = 12 bytes.
     - Followed by optional `hdlr` atom (QuickTime metadata handler: `'mdir'` / `'appl'`) and the `ilst` atom.
   - `ilst` (Item List Atom):
     - Contains tag key atoms.
     - Each tag key atom contains an inner `data` box.
3. M4A Atom Keys:
   - `\u00A9nam` (Title): UTF-8 text (`data` flags = 1).
   - `\u00A9ART` (Artist): UTF-8 text (`data` flags = 1).
   - `aART` (Album Artist): UTF-8 text (`data` flags = 1).
   - `\u00A9alb` (Album): UTF-8 text (`data` flags = 1).
   - `\u00A9day` (Release Year/Date): UTF-8 text (e.g., "2024", `data` flags = 1).
   - `\u00A9gen` (Genre): UTF-8 text (`data` flags = 1).
   - `trkn` (Track Number): 8-byte binary (`data` flags = 0, locale = 0):
     - `[0, 0, trackNumHi, trackNumLo, totalTracksHi, totalTracksLo, 0, 0]`.
   - `covr` (Cover Artwork): Raw image binary:
     - JPEG: `data` flags = 13 (`0x0D`).
     - PNG: `data` flags = 14 (`0x0E`).
4. FourCC Character Encoding Constraint:
   - The symbol `©` is Unicode `\u00A9`. In UTF-8, this is two bytes (`0xC2, 0xA9`), resulting in an invalid 5-byte FourCC.
   - In ISO-8859-1, `\u00A9` maps to exactly one byte `0xA9`.
   - Therefore, serializing FourCC identifiers MUST use `StandardCharsets.ISO_8859_1`.

### 2.2 `stco` / `co64` Chunk Offset Realignment Logic
From Observation 1.2:
1. Audio frames inside `mdat` are referenced by byte offset tables inside `moov.trak.mdia.minf.stbl`:
   - `stco`: 32-bit chunk offset table (FullBox header: 4 size, 4 'stco', 1 version, 3 flags, 4 entry count, entries * 4 bytes).
   - `co64`: 64-bit chunk offset table (FullBox header: 4 size, 4 'co64', 1 version, 3 flags, 4 entry count, entries * 8 bytes).
2. Physical displacement impact:
   - When tags are updated, `moov` expands or shrinks: `moovDelta = newMoovSize - oldMoovSize`.
   - In standard M4A files where `moov` precedes `mdat`, `mdat` is physically displaced in the file by exactly `moovDelta` bytes.
   - If chunk offsets are not adjusted, decoders (ExoPlayer, Media3, Android MediaCodec) read misaligned bytes, causing catastrophic decoding failure (`UnrecognizedInputFormatException` or acoustic screeching).
3. Offset recalculation rule:
   - For every entry in `stco` / `co64`:
     - If `offset >= mdatStartOffset`: `newOffset = offset + moovDelta`.
     - If `offset < mdatStartOffset`: `newOffset = offset` (remains unshifted).
4. Zero Secondary Delta Invariance:
   - Updating the 32-bit integers in `stco` or 64-bit longs in `co64` is an **in-place value substitution**.
   - It does NOT alter the entry count `N` or the byte size of `stco` / `co64`.
   - Thus, updating `stco`/`co64` does not alter `moov` size, eliminating any recursive delta cascade!

### 2.3 Feature F9 Tag Sync Pipeline Architecture
From Observation 1.1 and 1.2:
A robust, Tier-1 tag update pipeline must execute four deterministic stages:
1. **Stage 1 (Raw Audio File)**:
   - Build updated container using a staging file (`.tmp_<timestamp>`).
   - Validate staging file: magic bytes ("ftyp" at offset 4 for M4A, "ID3" for MP3, "fLaC" for FLAC) and expected size.
   - Perform atomic replacement (`staging.renameTo(target)` or SAF copy).
2. **Stage 2 (MediaStore ContentValues)**:
   - If the track has a `content://media/` URI, update `MediaStore.Audio.Media.TITLE`, `ARTIST`, `ALBUM`, `YEAR` via `contentResolver.update()`.
3. **Stage 3 (MediaScanner & Persistent DB Overrides)**:
   - Broadcast/trigger `MediaScannerConnection.scanFile()` so system services and third-party apps index the updated file immediately.
   - Save the user tags into GoBeats's `track_metadata_overrides` SQLite table via `insertWithOnConflict(..., CONFLICT_REPLACE)`.
   - This guarantees that subsequent app scans (`MediaScanner.scanMediaStore()`) merge the user's edits and never overwrite them with unindexed disk tags.
4. **Stage 4 (Active Playback Session)**:
   - Call `playbackConnection?.notifyTrackMetadataChanged(updatedTrack)`.
   - If the edited track is currently playing (`updatedTrack.id == currentTrack.id`), immediately replace the Media3 `MediaItem` (`player.replaceMediaItem(index, buildMediaItem(updatedTrack))`) so notification, lockscreen, and NowPlaying UI update with zero playback interruption.
   - If not currently playing, update the queued item in `currentQueue` without disturbing playback.

---

## 3. Caveats

1. **`co64` vs `stco` Boundary**:
   - For standard music tracks (< 4GB), `stco` is almost universally used. If an audio file is modified that already uses `co64`, the writer must scan for both `stco` and `co64` FourCC identifiers to ensure multi-gigabyte files or audiobooks are handled seamlessly.
2. **Multiple Audio Tracks**:
   - Some MP4 files contain multiple tracks (e.g. secondary commentary track or chapter track). The `stco`/`co64` offset updater must traverse ALL `trak` boxes inside `moov`, rather than stopping at the first track.
3. **Fast-Start Layout**:
   - If an M4A file has `mdat` before `moov`, `moovDelta` does not displace `mdat`. The condition `if (offset >= mdatStartOffset)` naturally handles this when `moov` is placed at the end, but standard practice is to place `moov` before `mdat` for instant playback.
4. **Scoped Storage Permission**:
   - Direct file writing (`File.canWrite()`) succeeds on app-internal files and on legacy storage, but fails on Android 11–14 (API 30–34) for public music files owned by another app. This flow is coordinated with Feature F6 (`MediaStore.createWriteRequest`).

---

## 4. Conclusion & Technical Recommendations

### 4.1 Recommended Architecture for `M4aTagWriter.kt`
Create `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`:

```kotlin
package com.example.gobeats.core.media

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object M4aTagWriter {

    fun buildIlstTextAtom(fourCc: String, value: String): ByteArray {
        require(fourCc.length == 4) { "FourCC must be exactly 4 characters" }
        val textBytes = value.toByteArray(StandardCharsets.UTF_8)
        val dataBoxSize = 16 + textBytes.size
        val dataBox = ByteBuffer.allocate(dataBoxSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(dataBoxSize)
            put("data".toByteArray(StandardCharsets.US_ASCII))
            putInt(1) // type = UTF-8 text
            putInt(0) // locale = 0
            put(textBytes)
        }

        val parentSize = 8 + dataBoxSize
        return ByteBuffer.allocate(parentSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(parentSize)
            put(fourCc.toByteArray(StandardCharsets.ISO_8859_1))
            put(dataBox.array())
        }.array()
    }

    fun buildIlstTrackNumberAtom(trackNumber: Int, totalTracks: Int = 0): ByteArray {
        val dataBoxSize = 16 + 8
        val dataBox = ByteBuffer.allocate(dataBoxSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(dataBoxSize)
            put("data".toByteArray(StandardCharsets.US_ASCII))
            putInt(0) // type = raw/implicit
            putInt(0) // locale = 0
            putShort(0) // reserved
            putShort(trackNumber.toShort())
            putShort(totalTracks.toShort())
            putShort(0) // reserved
        }

        val parentSize = 8 + dataBoxSize
        return ByteBuffer.allocate(parentSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(parentSize)
            put("trkn".toByteArray(StandardCharsets.ISO_8859_1))
            put(dataBox.array())
        }.array()
    }

    fun buildIlstCoverArtAtom(imageBytes: ByteArray, mimeType: String = "image/jpeg"): ByteArray {
        val flag = if (mimeType.contains("png", ignoreCase = true)) 14 else 13
        val dataBoxSize = 16 + imageBytes.size
        val dataBox = ByteBuffer.allocate(dataBoxSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(dataBoxSize)
            put("data".toByteArray(StandardCharsets.US_ASCII))
            putInt(flag) // 13 = JPEG, 14 = PNG
            putInt(0) // locale = 0
            put(imageBytes)
        }

        val parentSize = 8 + dataBoxSize
        return ByteBuffer.allocate(parentSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(parentSize)
            put("covr".toByteArray(StandardCharsets.ISO_8859_1))
            put(dataBox.array())
        }.array()
    }

    fun adjustStcoOffsets(
        originalOffsets: List<Int>,
        moovDelta: Int,
        mdatStartOffset: Int
    ): List<Int> {
        return originalOffsets.map { offset ->
            if (offset >= mdatStartOffset) offset + moovDelta else offset
        }
    }

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

        val totalChildrenSize = children.sumOf { it.size }
        val ilstSize = 8 + totalChildrenSize
        val ilst = ByteBuffer.allocate(ilstSize).order(ByteOrder.BIG_ENDIAN)
        ilst.putInt(ilstSize)
        ilst.put("ilst".toByteArray(StandardCharsets.US_ASCII))
        children.forEach { ilst.put(it) }
        return ilst.array()
    }

    fun writeTagsToM4aFile(file: File, payload: TagEditPayload): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val tempFile = File(file.parentFile, "${file.name}.tmp_${System.currentTimeMillis()}")

        return try {
            val fileLength = file.length()
            val raf = RandomAccessFile(file, "r")
            
            var ftypBytes: ByteArray? = null
            var moovBytes: ByteArray? = null
            var mdatStartOffset = -1L
            var mdatHeaderSize = 8
            var mdatSize = -1L

            // 1. Scan top-level boxes
            var pointer = 0L
            while (pointer < fileLength) {
                raf.seek(pointer)
                if (fileLength - pointer < 8) break
                val boxSizeInt = raf.readInt()
                val fourCcBytes = ByteArray(4)
                raf.readFully(fourCcBytes)
                val fourCc = String(fourCcBytes, StandardCharsets.US_ASCII)
                
                var actualSize = boxSizeInt.toLong() and 0xFFFFFFFFL
                var headerSize = 8
                if (actualSize == 1L) {
                    actualSize = raf.readLong()
                    headerSize = 16
                } else if (actualSize == 0L) {
                    actualSize = fileLength - pointer
                }

                when (fourCc) {
                    "ftyp" -> {
                        val buf = ByteArray(actualSize.toInt())
                        raf.seek(pointer)
                        raf.readFully(buf)
                        ftypBytes = buf
                    }
                    "moov" -> {
                        val buf = ByteArray(actualSize.toInt())
                        raf.seek(pointer)
                        raf.readFully(buf)
                        moovBytes = buf
                    }
                    "mdat" -> {
                        mdatStartOffset = pointer
                        mdatHeaderSize = headerSize
                        mdatSize = actualSize
                    }
                }
                pointer += actualSize
            }

            if (ftypBytes == null || moovBytes == null || mdatStartOffset < 0L) {
                raf.close()
                return false
            }

            // 2. Build new udta with ilst
            val ilstBytes = buildIlstBox(payload)
            val hdlrBox = buildStandardMetadataHdlr()
            val metaSize = 12 + hdlrBox.size + ilstBytes.size
            val metaBuf = ByteBuffer.allocate(metaSize).order(ByteOrder.BIG_ENDIAN)
            metaBuf.putInt(metaSize)
            metaBuf.put("meta".toByteArray(StandardCharsets.US_ASCII))
            metaBuf.putInt(0) // FullBox version 0, flags 0
            metaBuf.put(hdlrBox)
            metaBuf.put(ilstBytes)

            val udtaSize = 8 + metaSize
            val udtaBuf = ByteBuffer.allocate(udtaSize).order(ByteOrder.BIG_ENDIAN)
            udtaBuf.putInt(udtaSize)
            udtaBuf.put("udta".toByteArray(StandardCharsets.US_ASCII))
            udtaBuf.put(metaBuf.array())

            // 3. Inject udta into moov, removing any old udta
            val updatedMoov = replaceOrAppendUdta(moovBytes, udtaBuf.array())
            val moovDelta = updatedMoov.size - moovBytes.size

            // 4. Adjust stco / co64 chunk offsets
            adjustChunkOffsetsInMoovBuffer(updatedMoov, moovDelta, mdatStartOffset)

            // 5. Write to staging file
            FileOutputStream(tempFile).use { out ->
                out.write(ftypBytes)
                out.write(updatedMoov)
                // Stream mdat bit-perfectly from original file
                raf.seek(mdatStartOffset)
                val buffer = ByteArray(65536)
                var remaining = mdatSize
                while (remaining > 0) {
                    val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                    val read = raf.read(buffer, 0, toRead)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }
            raf.close()

            // 6. Validate staging file and atomic swap
            if (tempFile.exists() && tempFile.length() > 0) {
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
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    private fun buildStandardMetadataHdlr(): ByteArray {
        val size = 33
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(size)
            put("hdlr".toByteArray(StandardCharsets.US_ASCII))
            putInt(0) // version = 0, flags = 0
            putInt(0) // pre_defined
            put("mdir".toByteArray(StandardCharsets.US_ASCII)) // handler_type
            put("appl".toByteArray(StandardCharsets.US_ASCII)) // manufacturer
            putInt(0)
            putInt(0)
            put(0.toByte()) // null-terminated name
        }.array()
    }

    private fun replaceOrAppendUdta(moovBytes: ByteArray, newUdtaBytes: ByteArray): ByteArray {
        val buf = ByteBuffer.wrap(moovBytes).order(ByteOrder.BIG_ENDIAN)
        var pointer = 8 // skip moov size + 'moov'
        var udtaStart = -1
        var udtaEnd = -1

        while (pointer + 8 <= moovBytes.size) {
            val childSize = buf.getInt(pointer)
            val fourCcBytes = ByteArray(4)
            buf.position(pointer + 4)
            buf.get(fourCcBytes)
            val fourCc = String(fourCcBytes, StandardCharsets.US_ASCII)
            val actualChildSize = if (childSize == 1) buf.getLong(pointer + 8).toInt() else childSize
            if (actualChildSize <= 0 || pointer + actualChildSize > moovBytes.size) break

            if (fourCc == "udta") {
                udtaStart = pointer
                udtaEnd = pointer + actualChildSize
                break
            }
            pointer += actualChildSize
        }

        val out = ByteBuffer.allocate(
            if (udtaStart != -1) moovBytes.size - (udtaEnd - udtaStart) + newUdtaBytes.size
            else moovBytes.size + newUdtaBytes.size
        ).order(ByteOrder.BIG_ENDIAN)

        if (udtaStart != -1) {
            out.putInt(out.capacity())
            out.put("moov".toByteArray(StandardCharsets.US_ASCII))
            out.put(moovBytes, 8, udtaStart - 8)
            out.put(newUdtaBytes)
            out.put(moovBytes, udtaEnd, moovBytes.size - udtaEnd)
        } else {
            out.putInt(out.capacity())
            out.put("moov".toByteArray(StandardCharsets.US_ASCII))
            out.put(moovBytes, 8, moovBytes.size - 8)
            out.put(newUdtaBytes)
        }
        return out.array()
    }

    private fun adjustChunkOffsetsInMoovBuffer(moovBytes: ByteArray, moovDelta: Int, mdatStartOffset: Long) {
        if (moovDelta == 0) return
        val buf = ByteBuffer.wrap(moovBytes).order(ByteOrder.BIG_ENDIAN)
        
        // Scan for all 'stco' and 'co64' occurrences inside stbl
        for (i in 0 until moovBytes.size - 16) {
            if (moovBytes[i + 4] == 's'.code.toByte() &&
                moovBytes[i + 5] == 't'.code.toByte() &&
                moovBytes[i + 6] == 'c'.code.toByte() &&
                moovBytes[i + 7] == 'o'.code.toByte()
            ) {
                val boxSize = buf.getInt(i)
                val entryCount = buf.getInt(i + 12)
                var entryOffset = i + 16
                for (e in 0 until entryCount) {
                    if (entryOffset + 4 > moovBytes.size) break
                    val currentOffset = buf.getInt(entryOffset)
                    if (currentOffset.toLong() and 0xFFFFFFFFL >= mdatStartOffset) {
                        buf.putInt(entryOffset, currentOffset + moovDelta)
                    }
                    entryOffset += 4
                }
            } else if (
                moovBytes[i + 4] == 'c'.code.toByte() &&
                moovBytes[i + 5] == 'o'.code.toByte() &&
                moovBytes[i + 6] == '6'.code.toByte() &&
                moovBytes[i + 7] == '4'.code.toByte()
            ) {
                val entryCount = buf.getInt(i + 12)
                var entryOffset = i + 16
                for (e in 0 until entryCount) {
                    if (entryOffset + 8 > moovBytes.size) break
                    val currentOffset = buf.getLong(entryOffset)
                    if (currentOffset >= mdatStartOffset) {
                        buf.putLong(entryOffset, currentOffset + moovDelta)
                    }
                    entryOffset += 8
                }
            }
        }
    }
}
```

### 4.2 Recommended Architecture for `UniversalTagWriter.kt`
Create `app/src/main/java/com/example/gobeats/core/media/UniversalTagWriter.kt`:

```kotlin
package com.example.gobeats.core.media

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import com.example.gobeats.core.database.GobeatsDatabaseHelper
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object UniversalTagWriter {

    suspend fun writeTags(
        context: Context,
        track: Track,
        payload: TagEditPayload,
        dbHelper: GobeatsDatabaseHelper,
        playbackConnection: PlaybackConnection? = null
    ): Track? = withContext(Dispatchers.IO) {
        var finalPayload = payload

        // 1. Save cover artwork if new image bytes provided
        if (payload.newArtworkBytes != null && payload.newArtworkBytes.isNotEmpty()) {
            val savedArtUri = Id3TagWriter.saveArtworkToStorage(context, payload.newArtworkBytes, payload.trackId)
            if (savedArtUri != null) {
                finalPayload = finalPayload.copy(artworkUri = savedArtUri)
            }
        }

        // 2. Resolve target physical file path
        val filePath = when {
            track.fileUri.startsWith("file://") -> Uri.parse(track.fileUri).path
            track.fileUri.startsWith("/") -> track.fileUri
            else -> null
        }

        // 3. Stage 1: Raw file tag writing via format-specific engines
        if (filePath != null) {
            val targetFile = File(filePath)
            if (targetFile.exists() && targetFile.canWrite()) {
                val isMp3 = filePath.endsWith(".mp3", ignoreCase = true) || track.format.contains("mp3", ignoreCase = true)
                val isM4a = filePath.endsWith(".m4a", ignoreCase = true) || filePath.endsWith(".mp4", ignoreCase = true) || track.format.contains("m4a", ignoreCase = true)
                val isFlac = filePath.endsWith(".flac", ignoreCase = true) || track.format.contains("flac", ignoreCase = true)

                when {
                    isMp3 -> {
                        val id3Bytes = Id3TagWriter.buildId3v23Tag(
                            title = finalPayload.title,
                            artist = finalPayload.artist,
                            album = finalPayload.album,
                            year = finalPayload.year,
                            genre = finalPayload.genre,
                            trackNumber = finalPayload.trackNumber,
                            artworkBytes = finalPayload.newArtworkBytes
                        )
                        Id3TagWriter.writeTagsToMp3File(targetFile, id3Bytes)
                    }
                    isM4a -> {
                        M4aTagWriter.writeTagsToM4aFile(targetFile, finalPayload)
                    }
                    isFlac -> {
                        FlacTagWriter.writeTagsToFlacFile(targetFile, finalPayload)
                    }
                }
            }
        }

        // 4. Stage 2: MediaStore ContentValues update (if content URI)
        if (track.fileUri.startsWith("content://")) {
            Id3TagWriter.updateMediaStoreTags(
                context = context,
                contentUri = Uri.parse(track.fileUri),
                title = finalPayload.title,
                artist = finalPayload.artist,
                album = finalPayload.album,
                year = finalPayload.year
            )
        }

        // 5. Stage 3: MediaScanner rescan trigger & SQLite DB persistence
        if (filePath != null) {
            try {
                MediaScannerConnection.scanFile(context, arrayOf(filePath), null, null)
            } catch (_: Exception) {}
        }
        val updatedTrack = dbHelper.updateTrackMetadata(finalPayload)

        // 6. Stage 4: Live update active PlaybackConnection session
        if (updatedTrack != null) {
            playbackConnection?.notifyTrackMetadataChanged(updatedTrack)
        }

        updatedTrack
    }
}
```

---

## 5. Verification Method

### 5.1 Independent Test Suite Verification
Run the following Gradle commands to independently verify that all unit and regression tests pass without errors:

```powershell
# Verify Isolated Feature Coverage for F8 and F9
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest"

# Verify Boundary Corner Cases for F8 and F9
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest"

# Verify Cross-Feature Interaction Tests (cross_06, cross_07)
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest"

# Verify End-to-End Real-World Workflow (workflow_03)
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest"

# Full Debug Compilation Verification
.\gradlew.bat compileDebugKotlin
```

### 5.2 Specific Test Assertions to Inspect
1. **F8 Box Structure & FourCC**:
   - Inspect `GobeatsTier1FeatureCoverageTest.f8_03_ilstTextAtom_createsValidBoxHierarchy` in `GobeatsTier1FeatureCoverageTest.kt:837`.
   - Verify FourCC is exactly `\u00A9nam`, child `data` box size is 16 + string length, flags = 1 (UTF-8), locale = 0.
2. **F8 Chunk Offset Arithmetic**:
   - Inspect `GobeatsTier1FeatureCoverageTest.f8_01_stcoChunkOffsets_shiftByExactDelta` in `GobeatsTier1FeatureCoverageTest.kt:812`.
   - Verify `adjustStcoOffsets([2048, 4096, 6144, 8192], moovDelta = 120, mdatStart = 1024)` evaluates to `[2168, 4216, 6264, 8312]`.
   - Inspect `GobeatsTier1FeatureCoverageTest.f8_02_stcoOffsets_beforeMdat_areNotShifted`: Offset `500 < 1000` remains unshifted.
3. **F9 DB Overrides & Rescan Protection**:
   - Inspect `GobeatsTier1FeatureCoverageTest.f9_04_libraryRescan_preservesUserOverrides` in `GobeatsTier1FeatureCoverageTest.kt:952`.
   - Verify `track_metadata_overrides` preserves "Protected Title" when scanner re-indexes disk.
4. **F9 Live Playback Update**:
   - Inspect `GobeatsTier1FeatureCoverageTest.f9_02_activePlayingTrack_updatesPlaybackSessionImmediately` in `GobeatsTier1FeatureCoverageTest.kt:931`.
   - Verify `playerUpdatedTrack` is updated when `track.id == activePlayingTrackId`.

### 5.3 Invalidation Conditions
This architectural report will be invalidated if:
1. MP4 chunk offsets are adjusted unconditionally without checking `offset >= mdatStartOffset`, which would corrupt headers or offsets pointing outside `mdat`.
2. FourCC identifiers for `\u00A9` atoms are serialized using UTF-8 instead of ISO-8859-1, producing invalid 5-byte atom tags.
3. Audio frames in `mdat` are rewritten with loss or truncated rather than streamed verbatim.
4. User tag edits fail to insert into `track_metadata_overrides`, causing future library rescans to overwrite user changes.
