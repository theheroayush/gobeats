# Technical Handoff Report: FLAC Vorbis Tag Editor & Picture Block Writer (Feature F7)

**Author:** `explorer_m2_2` (Codebase & Spec Explorer)  
**Date:** 2026-10-06  
**Target Feature:** Milestone 2 — Feature F7 (`FLAC Vorbis Tag Editor & Picture Block Writer`)  
**Scope:** Specification Analysis, Test Coverage Review, Audio Frame Safety, and Architectural Design of `FlacTagWriter.kt`  

---

## 1. Observation

### 1.1 Existing Codebase State for Tag Editing
Direct observation of the tag editing subsystem:
- `app/src/main/java/com/example/gobeats/core/media/Id3TagWriter.kt`:
  - Implements ID3v2.3 tag writing exclusively for MP3 files (`writeTagsToMp3File(file, newTagBytes)`).
  - Uses `TagEditPayload` (lines 15–25):
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
- `app/src/main/java/com/example/gobeats/data/repository/Repositories.kt` (lines 73–99):
  - Currently restricts physical tag writing solely to MP3 format:
    ```kotlin
    val isMp3 = track.fileUri.endsWith(".mp3", ignoreCase = true) || track.format.contains("mp3", ignoreCase = true)
    if (isMp3) { ... Id3TagWriter.writeTagsToMp3File(file, id3Bytes) ... }
    ```
  - FLAC files (`.flac`) and M4A files (`.m4a`) are currently not written at the container level; only SQLite DB attributes and MediaStore columns are updated.
- `app/src/main/java/com/example/gobeats/core/media/`:
  - Contains `Id3TagWriter.kt`, `MediaScanner.kt`, `MetadataExtractor.kt`, `PlaybackConnection.kt`, `GobeatsMediaSessionService.kt`.
  - Does NOT yet contain `FlacTagWriter.kt`.

### 1.2 Existing Test Suite Baseline (F7 Coverage)
Direct observation of unit tests in `app/src/test/java/com/example/gobeats/`:
- `GobeatsTier1FeatureCoverageTest.kt` (lines 625–770):
  - Defines reference implementation `FlacTagEngine` (lines 628–696):
    - `FLAC_MAGIC`: exactly `byteArrayOf(0x66, 0x4C, 0x61, 0x43)` (`"fLaC"` in ASCII).
    - `buildVorbisCommentBlock(vendorString, comments, isLast)`: encodes vendor length (32-bit LE), UTF-8 vendor string, comment count (32-bit LE), user comments (`FIELD=value` length 32-bit LE + UTF-8 bytes). Prepends 4-byte FLAC block header (`[if (isLast) 0x80 else 0x00 | 4, length >> 16, length >> 8, length & 0xFF]`).
    - `parseVorbisComments(blockBytes)`: validates `blockType == 4` (throws `IllegalArgumentException` if not), reads vendor string, reads comment count, reads and splits each user comment on `=` with `limit = 2`.
  - 5 Tier 1 unit tests:
    - `f7_01_flacMagicHeader_isExact`: validates 4-byte magic.
    - `f7_02_vorbisCommentEncoding_roundtripsAccurately`: verifies header byte `0x84` when `isLast=true` and round-trips TITLE, ARTIST, ALBUM, DATE, GENRE, TRACKNUMBER.
    - `f7_03_flacAudioFrames_remainBitPerfectWhenRewritingTags`: verifies audio frames remain identical after metadata block replacement.
    - `f7_04_vorbisCommentBlock_isLastFlagTogglesCorrectly`: verifies `0x04` when `isLast=false` and `0x84` when `isLast=true`.
    - `f7_05_vorbisComments_handleUnicodeUtf8Characters`: verifies Hindi and Japanese UTF-8 characters roundtrip without corruption.
- `GobeatsTier2BoundaryCornerTest.kt` (lines 379–425):
  - 5 Tier 2 boundary tests:
    - `f7_b01_emptyCommentsMap_producesValidVorbisBlock`: empty map produces valid block >= 12 bytes (`4 bytes header + 4 bytes vendorLen + 0 vendor bytes + 4 bytes commentCount = 12 bytes`).
    - `f7_b02_commentValueWithMultipleEqualsSigns_parsedIntact`: URLs with query strings containing multiple `=` parsed intact via `split("=", limit = 2)`.
    - `f7_b03_massiveLyricsTag_encodesLengthAccurately`: encodes massive 25,000-character lyrics string without integer overflow.
    - `f7_b04_nonVorbisCommentBlockType_throwsException`: passing non-type 4 block throws `IllegalArgumentException`.
    - `f7_b05_specialCharactersInVendorString`: Unicode vendor string with copyright symbols encodes byte length accurately.
- `GobeatsTier3CrossFeatureTest.kt` (lines 135–171):
  - `cross_05_scopedStorageStagingWithFlacVorbisTagWriting`: validates staging file with `FLAC_MAGIC` before committing changes to storage.
  - `cross_07_losslessTagWritingRegistersDatabaseOverrideProtectingRescan`: registers database overrides protecting written tags against media scanner regression.
- `GobeatsTier4RealWorldWorkflowsTest.kt` (lines 102–151):
  - `workflow_02_audiophileLosslessFlacLibraryAndTagEditing`: end-to-end workflow verifying folder browsing, companion lyrics, tag editing, vorbis comment parsing, and database override protection.

### 1.3 Test Execution Verification
Command executed directly in terminal:
```powershell
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f7*"
.\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f7*"
```
Verbatim test runner result:
- Both test runs exited with code `0`.
- `BUILD SUCCESSFUL in 19s` / `BUILD SUCCESSFUL in 15s`.
- 100% of F7 tests passed with zero failures.

---

## 2. Logic Chain

### 2.1 FLAC Container & Metadata Block Specification
1. A valid FLAC stream begins with the mandatory 4-byte ASCII stream marker:
   $$\text{Magic} = \texttt{0x66, 0x4C, 0x61, 0x43} \quad (\text{"fLaC"})$$
2. Immediately following the magic marker is a contiguous sequence of one or more metadata blocks.
3. Every metadata block begins with a fixed 4-byte header:
   - **Byte 0**:
     - Bit 7: `isLast` flag. `1` indicates this is the final metadata block before audio frames; `0` indicates more metadata blocks follow.
     - Bits 0–6: `blockType` (0 to 127).
   - **Bytes 1–3**: 24-bit unsigned Big-Endian integer indicating the length $L$ of the block payload in bytes ($0 \le L \le 2^{24}-1 = 16,777,215$).
4. Defined block types (RFC 9639 / Xiph.org):
   - `0` = `STREAMINFO`: Mandatory first metadata block. Exactly 34 bytes payload containing audio sample rate, channels, bit depth, total samples, and 128-bit MD5 checksum of uncompressed audio.
   - `1` = `PADDING`: Filler bytes for fast in-place edits.
   - `2` = `APPLICATION`: Application-specific data.
   - `3` = `SEEKTABLE`: Seek points for fast audio seeking.
   - `4` = `VORBIS_COMMENT`: Human-readable text tags.
   - `5` = `CUESHEET`: CD cue sheet data.
   - `6` = `PICTURE`: Embedded artwork/images.
5. In any FLAC file, `STREAMINFO` (type 0) **must be the first metadata block**. Deleting or relocating `STREAMINFO` will immediately corrupt the audio file and cause playback engines (e.g. ExoPlayer) to reject the stream.

### 2.2 Endianness Duality: Big-Endian Container vs Little-Endian Vorbis Comment
A critical source of corruption in FLAC tag editors is endianness mismatch:
- **Container Level (FLAC Metadata Block Header)**:
  - 24-bit payload length is **Big-Endian**.
  - `header[1] = ((length shr 16) and 0xFF).toByte()`
  - `header[2] = ((length shr 8) and 0xFF).toByte()`
  - `header[3] = (length and 0xFF).toByte()`
- **Payload Level for `VORBIS_COMMENT` (Block Type 4)**:
  - Vorbis comments specification (Xiph.org) mandates that all integers are **32-bit Little-Endian**!
  - `vendor_length`: 32-bit LE integer.
  - `vendor_string`: UTF-8 string bytes (length = `vendor_length`).
  - `user_comment_list_length`: 32-bit LE integer (number of comments).
  - Each comment:
    - 32-bit LE length $N$ (UTF-8 byte length of `FIELD=value`).
    - $N$ bytes of UTF-8 string formatted as `FIELD=value`.
- **Payload Level for `PICTURE` (Block Type 6, RFC 9639)**:
  - Unlike Vorbis comments, FLAC native picture blocks use **32-bit Big-Endian** integers:
    - `picture_type`: 32-bit BE (3 = Front Cover).
    - `mime_type_length`: 32-bit BE.
    - `mime_type`: ASCII string (e.g. `"image/jpeg"`, `"image/png"`).
    - `description_length`: 32-bit BE (0 if empty).
    - `description`: UTF-8 string (empty if length 0).
    - `width`: 32-bit BE (0 if unknown).
    - `height`: 32-bit BE (0 if unknown).
    - `color_depth`: 32-bit BE (24 or 0 if unknown).
    - `indexed_colors`: 32-bit BE (0).
    - `data_length`: 32-bit BE ($M = \text{imageBytes.size}$).
    - `picture_data`: $M$ bytes of raw image binary data.

### 2.3 Audio Frame Preservation & Bit-Perfect Safety
1. FLAC audio frames begin immediately following the last metadata block (where `isLast == true`).
2. Audio frames start with a 14-bit sync code `11111111 111110xx` (`0xFF, 0xF8`..`0xFF`).
3. Audio frames consist of variable-length subframes holding Rice-coded residual samples and LPC coefficients.
4. **Preservation Algorithm**:
   - Parse input stream from byte 4 (`fLaC`):
     - Read 4-byte header: extract `isLast`, `blockType`, `length`.
     - Read `length` bytes of payload.
     - Repeat until a block with `isLast == true` is processed.
   - The current input stream offset $O_{\text{audio}}$ marks the exact starting byte of raw audio frames.
   - When rewriting:
     - Retain `STREAMINFO` (type 0) untouched.
     - Retain other auxiliary blocks (`SEEKTABLE`, `APPLICATION`).
     - Discard existing `VORBIS_COMMENT` (type 4).
     - If new artwork provided or artwork removal requested, discard existing `PICTURE` (type 6). Otherwise retain existing `PICTURE` block.
     - Append newly synthesized `VORBIS_COMMENT` block and optional `PICTURE` block.
     - Set `isLast = false` on all preceding blocks; set `isLast = true` on the final block.
     - Write 4-byte `fLaC` magic + all assembled metadata blocks.
     - Stream all remaining bytes from $O_{\text{audio}}$ to EOF directly to the output using a 32 KB buffer.
   - Result: Exactly zero audio frame bytes are altered, decoded, or re-encoded. The MD5 checksum embedded in `STREAMINFO` remains valid.

### 2.4 Staging File Pattern & Scoped Storage Integration
1. Modifying a FLAC file in-place on storage can lead to file corruption if an exception or process kill occurs during writing.
2. The safe pipeline:
   - Create temporary staging file (e.g. `"${file.name}.tmp_${System.currentTimeMillis()}"` or in `context.cacheDir`).
   - Write new stream into staging file.
   - Validate staging file:
     - `file.exists() && file.length() >= 16L`
     - First 4 bytes match `FLAC_MAGIC` (`0x66, 0x4C, 0x61, 0x43`).
   - If valid, atomically replace destination file (or stream validated staging content via `ContentResolver.openOutputStream(uri, "wt")` when scoped storage write permissions are granted).
   - If invalid or an exception occurs, delete staging file; original file remains untouched.

---

## 3. Caveats

1. **Multiple Picture Blocks**: The FLAC specification allows multiple `PICTURE` blocks (e.g. Front Cover type 3, Back Cover type 4, Artist type 7). For GoBeats' Tag Editor, we focus on type 3 (Front Cover). When replacing artwork, all front cover blocks (or all picture blocks if updating full album art) should be replaced, while retaining non-picture blocks.
2. **Maximum Block Size**: The FLAC metadata block header supports lengths up to $2^{24}-1$ bytes (16 MiB - 1 byte). Very large album art images (> 16 MB) would overflow the 24-bit length field. Images should be checked or compressed if exceeding 16 MB (though mobile cover art is typically 50 KB – 2 MB).
3. **ID3v2 Pre-Header / ID3v1 Post-Footer in FLAC**: Non-standard FLAC files generated by legacy tools sometimes have an ID3v2 tag prepended before `fLaC` or an ID3v1 tag at EOF. While the FLAC standard deprecates ID3v2 tags in FLAC, `FlacTagWriter` should expect the standard `fLaC` stream marker at byte 0. If an ID3v2 header precedes `fLaC`, the parser can detect `ID3`, skip `10 + tagSize` bytes, and locate `fLaC`.

---

## 4. Conclusion & Technical Recommendations

### 4.1 Recommended Class Design: `FlacTagWriter.kt`
Create `FlacTagWriter.kt` in `app/src/main/java/com/example/gobeats/core/media/` matching the interface and logic of `Id3TagWriter.kt`.

Complete implementation blueprint:

```kotlin
package com.example.gobeats.core.media

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object FlacTagWriter {

    val FLAC_MAGIC = byteArrayOf(0x66, 0x4C, 0x61, 0x43) // "fLaC"

    const val BLOCK_TYPE_STREAMINFO = 0
    const val BLOCK_TYPE_PADDING = 1
    const val BLOCK_TYPE_APPLICATION = 2
    const val BLOCK_TYPE_SEEKTABLE = 3
    const val BLOCK_TYPE_VORBIS_COMMENT = 4
    const val BLOCK_TYPE_CUESHEET = 5
    const val BLOCK_TYPE_PICTURE = 6

    const val PICTURE_TYPE_FRONT_COVER = 3
    const val DEFAULT_VENDOR_STRING = "GoBeats Universal Tag Editor"

    data class RawMetadataBlock(
        val type: Int,
        val isLast: Boolean,
        val payload: ByteArray
    )

    /**
     * Builds a Vorbis comment metadata block (Type 4) conforming to Xiph.org and FLAC specifications.
     * Block header length is 24-bit Big-Endian; comment fields are 32-bit Little-Endian.
     */
    fun buildVorbisCommentBlock(
        vendorString: String = DEFAULT_VENDOR_STRING,
        comments: Map<String, String>,
        isLast: Boolean = false
    ): ByteArray {
        val vendorBytes = vendorString.toByteArray(StandardCharsets.UTF_8)
        val userComments = comments.map { "${it.key}=${it.value}".toByteArray(StandardCharsets.UTF_8) }

        val payloadStream = ByteArrayOutputStream()
        val buf = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

        // 1. Vendor string length (32-bit LE) + vendor bytes
        buf.putInt(vendorBytes.size)
        payloadStream.write(buf.array())
        payloadStream.write(vendorBytes)

        // 2. User comment list count (32-bit LE)
        buf.clear()
        buf.putInt(userComments.size)
        payloadStream.write(buf.array())

        // 3. User comments (32-bit LE length + string bytes)
        userComments.forEach { commentBytes ->
            buf.clear()
            buf.putInt(commentBytes.size)
            payloadStream.write(buf.array())
            payloadStream.write(commentBytes)
        }

        val payload = payloadStream.toByteArray()

        // 4-byte FLAC block header: Byte 0 = [isLast bit 7 | type 4], Bytes 1-3 = 24-bit BE size
        val header = ByteArray(4)
        header[0] = ((if (isLast) 0x80 else 0x00) or BLOCK_TYPE_VORBIS_COMMENT).toByte()
        header[1] = ((payload.size shr 16) and 0xFF).toByte()
        header[2] = ((payload.size shr 8) and 0xFF).toByte()
        header[3] = (payload.size and 0xFF).toByte()

        return header + payload
    }

    /**
     * Parses a Vorbis comment metadata block (Type 4) into a key-value map.
     */
    fun parseVorbisComments(blockBytes: ByteArray): Map<String, String> {
        require(blockBytes.size >= 4) { "Block header too short" }
        val blockType = blockBytes[0].toInt() and 0x7F
        require(blockType == BLOCK_TYPE_VORBIS_COMMENT) { "Not a Vorbis comment block (type $blockType)" }

        val buffer = ByteBuffer.wrap(blockBytes, 4, blockBytes.size - 4).order(ByteOrder.LITTLE_ENDIAN)
        val vendorLen = buffer.int
        val vendorBytes = ByteArray(vendorLen)
        buffer.get(vendorBytes)

        val commentCount = buffer.int
        val comments = mutableMapOf<String, String>()
        for (i in 0 until commentCount) {
            val cLen = buffer.int
            val cBytes = ByteArray(cLen)
            buffer.get(cBytes)
            val cStr = String(cBytes, StandardCharsets.UTF_8)
            val split = cStr.split("=", limit = 2)
            if (split.size == 2) {
                comments[split[0]] = split[1]
            }
        }
        return comments
    }

    /**
     * Builds a FLAC METADATA_BLOCK_PICTURE (Type 6) conforming to RFC 9639.
     * All integer fields within the payload are 32-bit Big-Endian.
     */
    fun buildPictureBlock(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg",
        pictureType: Int = PICTURE_TYPE_FRONT_COVER,
        description: String = "",
        isLast: Boolean = false
    ): ByteArray {
        val mimeBytes = mimeType.toByteArray(StandardCharsets.US_ASCII)
        val descBytes = description.toByteArray(StandardCharsets.UTF_8)

        // Payload size: 4 + 4 + mimeLen + 4 + descLen + 4 + 4 + 4 + 4 + 4 + dataLen = 32 + mimeLen + descLen + dataLen
        val payloadSize = 32 + mimeBytes.size + descBytes.size + imageBytes.size
        val buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.BIG_ENDIAN)

        buf.putInt(pictureType)               // Picture type (3 = Front Cover)
        buf.putInt(mimeBytes.size)            // MIME length
        buf.put(mimeBytes)                    // MIME string
        buf.putInt(descBytes.size)            // Description length
        buf.put(descBytes)                    // Description string
        buf.putInt(0)                         // Width (0 = unspecified)
        buf.putInt(0)                         // Height (0 = unspecified)
        buf.putInt(24)                        // Color depth (24 bits/pixel)
        buf.putInt(0)                         // Indexed colors (0)
        buf.putInt(imageBytes.size)           // Image data length
        buf.put(imageBytes)                   // Image data bytes

        val payload = buf.array()

        // 4-byte FLAC block header: Byte 0 = [isLast bit 7 | type 6], Bytes 1-3 = 24-bit BE size
        val header = ByteArray(4)
        header[0] = ((if (isLast) 0x80 else 0x00) or BLOCK_TYPE_PICTURE).toByte()
        header[1] = ((payload.size shr 16) and 0xFF).toByte()
        header[2] = ((payload.size shr 8) and 0xFF).toByte()
        header[3] = (payload.size and 0xFF).toByte()

        return header + payload
    }

    /**
     * Parses a FLAC METADATA_BLOCK_PICTURE (Type 6) to extract raw image bytes.
     */
    fun parsePictureBlock(blockBytes: ByteArray): ByteArray? {
        if (blockBytes.size < 36) return null
        val blockType = blockBytes[0].toInt() and 0x7F
        if (blockType != BLOCK_TYPE_PICTURE) return null

        val buf = ByteBuffer.wrap(blockBytes, 4, blockBytes.size - 4).order(ByteOrder.BIG_ENDIAN)
        buf.int // pictureType
        val mimeLen = buf.int
        buf.position(buf.position() + mimeLen)
        val descLen = buf.int
        buf.position(buf.position() + descLen)
        buf.int // width
        buf.int // height
        buf.int // colorDepth
        buf.int // indexedColors
        val dataLen = buf.int
        if (dataLen <= 0 || dataLen > buf.remaining()) return null

        val imageBytes = ByteArray(dataLen)
        buf.get(imageBytes)
        return imageBytes
    }

    /**
     * Reads all metadata blocks from a FLAC input stream, leaving the stream positioned
     * at the exact beginning of raw audio frames.
     */
    fun readMetadataBlocks(input: InputStream): List<RawMetadataBlock> {
        val magic = ByteArray(4)
        val readMagic = input.read(magic)
        require(readMagic == 4 && magic.contentEquals(FLAC_MAGIC)) { "Invalid FLAC stream: missing fLaC magic" }

        val blocks = mutableListOf<RawMetadataBlock>()
        var isLast = false

        while (!isLast) {
            val header = ByteArray(4)
            val readHeader = input.read(header)
            require(readHeader == 4) { "Unexpected EOF in metadata header" }

            isLast = (header[0].toInt() and 0x80) != 0
            val type = header[0].toInt() and 0x7F
            val length = ((header[1].toInt() and 0xFF) shl 16) or
                    ((header[2].toInt() and 0xFF) shl 8) or
                    (header[3].toInt() and 0xFF)

            val payload = ByteArray(length)
            var bytesRead = 0
            while (bytesRead < length) {
                val r = input.read(payload, bytesRead, length - bytesRead)
                require(r != -1) { "Unexpected EOF in metadata payload" }
                bytesRead += r
            }

            blocks.add(RawMetadataBlock(type = type, isLast = isLast, payload = payload))
        }

        return blocks
    }

    /**
     * Safely updates tags and cover art on a FLAC file, writing via a temporary staging file
     * and copying raw audio frames verbatim to ensure bit-perfect audio preservation.
     */
    fun writeTagsToFlacFile(
        file: File,
        payload: TagEditPayload,
        vendorString: String = DEFAULT_VENDOR_STRING
    ): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val tempFile = File(file.parentFile, "${file.name}.tmp_${System.currentTimeMillis()}")

        return try {
            val comments = mutableMapOf<String, String>()
            if (payload.title.isNotBlank()) comments["TITLE"] = payload.title.trim()
            if (payload.artist.isNotBlank()) comments["ARTIST"] = payload.artist.trim()
            if (payload.album.isNotBlank()) comments["ALBUM"] = payload.album.trim()
            if (payload.year != null && payload.year > 0) comments["DATE"] = payload.year.toString()
            if (!payload.genre.isNullOrBlank()) comments["GENRE"] = payload.genre.trim()
            if (payload.trackNumber != null && payload.trackNumber > 0) comments["TRACKNUMBER"] = payload.trackNumber.toString()

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

            if (!success) {
                tempFile.delete()
                return false
            }

            // Staging validation
            if (!tempFile.exists() || tempFile.length() < 16L) {
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
    }

    /**
     * Streams rewritten metadata blocks and passes raw audio frames bit-perfectly from input to output.
     */
    fun writeTagsToStreams(
        input: InputStream,
        output: OutputStream,
        comments: Map<String, String>,
        artworkBytes: ByteArray? = null,
        removeArtwork: Boolean = false,
        vendorString: String = DEFAULT_VENDOR_STRING
    ): Boolean {
        val existingBlocks = readMetadataBlocks(input)
        require(existingBlocks.isNotEmpty() && existingBlocks[0].type == BLOCK_TYPE_STREAMINFO) {
            "First block must be STREAMINFO"
        }

        // 1. Write fLaC magic
        output.write(FLAC_MAGIC)

        // 2. Prepare metadata block list:
        // Keep STREAMINFO and non-comment, non-picture blocks
        val blocksToWrite = mutableListOf<RawMetadataBlock>()
        blocksToWrite.add(existingBlocks[0].copy(isLast = false)) // STREAMINFO

        for (i in 1 until existingBlocks.size) {
            val b = existingBlocks[i]
            if (b.type != BLOCK_TYPE_VORBIS_COMMENT && b.type != BLOCK_TYPE_PICTURE) {
                blocksToWrite.add(b.copy(isLast = false))
            } else if (b.type == BLOCK_TYPE_PICTURE && artworkBytes == null && !removeArtwork) {
                // Retain existing picture block if not updating or removing artwork
                blocksToWrite.add(b.copy(isLast = false))
            }
        }

        // Vorbis comment payload
        val vorbisBlock = buildVorbisCommentBlock(vendorString, comments, isLast = false)
        val vorbisPayload = vorbisBlock.copyOfRange(4, vorbisBlock.size)
        blocksToWrite.add(RawMetadataBlock(type = BLOCK_TYPE_VORBIS_COMMENT, isLast = false, payload = vorbisPayload))

        // Picture payload (if new artwork provided)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            val picBlock = buildPictureBlock(artworkBytes, isLast = false)
            val picPayload = picBlock.copyOfRange(4, picBlock.size)
            blocksToWrite.add(RawMetadataBlock(type = BLOCK_TYPE_PICTURE, isLast = false, payload = picPayload))
        }

        // Mark the very last block with isLast = true
        val totalBlocks = blocksToWrite.size
        for (i in 0 until totalBlocks) {
            val block = blocksToWrite[i]
            val isFinalBlock = (i == totalBlocks - 1)
            val header = ByteArray(4)
            header[0] = ((if (isFinalBlock) 0x80 else 0x00) or (block.type and 0x7F)).toByte()
            header[1] = ((block.payload.size shr 16) and 0xFF).toByte()
            header[2] = ((block.payload.size shr 8) and 0xFF).toByte()
            header[3] = (block.payload.size and 0xFF).toByte()

            output.write(header)
            output.write(block.payload)
        }

        // 3. Copy raw audio frames verbatim to EOF (bit-perfect)
        val buffer = ByteArray(32768)
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
        }

        output.flush()
        return true
    }
}
```

### 4.2 Integration with `UniversalTagWriter` and `Repositories.kt`
In `Repositories.kt` (lines 75–99), extend the format detection:
```kotlin
val isMp3 = track.fileUri.endsWith(".mp3", ignoreCase = true) || track.format.contains("mp3", ignoreCase = true)
val isFlac = track.fileUri.endsWith(".flac", ignoreCase = true) || track.format.contains("flac", ignoreCase = true)
val isM4a = track.fileUri.endsWith(".m4a", ignoreCase = true) || track.fileUri.endsWith(".mp4", ignoreCase = true) || track.format.contains("m4a", ignoreCase = true)

if (isMp3) {
    Id3TagWriter.writeTagsToMp3File(file, id3Bytes)
} else if (isFlac) {
    FlacTagWriter.writeTagsToFlacFile(file, finalPayload)
} else if (isM4a) {
    M4aTagWriter.writeTagsToM4aFile(file, finalPayload)
}
```
Or delegate directly to `UniversalTagWriter.writeTags(context, uri/file, payload)`.

---

## 5. Verification Method

1. **Feature Coverage Tests (Tier 1)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier1FeatureCoverageTest.f7*"
   ```
   *Expected Result*: All 5 tests (`f7_01` to `f7_05`) pass in < 5 seconds.
2. **Boundary & Corner Case Tests (Tier 2)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier2BoundaryCornerTest.f7*"
   ```
   *Expected Result*: All 5 boundary tests (`f7_b01` to `f7_b05`) pass in < 5 seconds.
3. **Cross-Feature Interaction Tests (Tier 3)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_05*"
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier3CrossFeatureTest.cross_07*"
   ```
   *Expected Result*: Scoped storage staging validation and tag sync pipeline override tests pass.
4. **Real-World Audiophile Workflow (Tier 4)**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.GobeatsTier4RealWorldWorkflowsTest.workflow_02*"
   ```
   *Expected Result*: Full audiophile workflow passes.
5. **Full Test Suite & Compilation**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   .\gradlew.bat testDebugUnitTest
   ```
   *Invalidation Conditions*: Any mismatch in FLAC magic, endianness inversion between header and Vorbis comment fields, failure to preserve audio bytes after `isLast == true`, or corrupted `STREAMINFO` block.
