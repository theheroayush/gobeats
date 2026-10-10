# Handoff Report: Fix Strategy for Non-Faststart M4A Containers & Structured Atom Box Traversal

**Agent:** `explorer_m2_r2_2`  
**Roles:** `explorer`, `investigator`  
**Milestone:** Milestone 2 (Features F8: M4A/MP4 Tag Editor & stco Offset Handling)  
**Date:** 2026-10-06  
**Status:** **INVESTIGATION COMPLETE**  

---

## 1. Observation

### 1.1 Non-Faststart M4A Container Reordering & Offset Desynchronization
In `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt`:

1. **Top-Level Box Offset Tracking (lines 157–193)**:
   ```kotlin
   var ftypBytes: ByteArray? = null
   var moovBytes: ByteArray? = null
   var mdatStartOffset = -1L
   var mdatSize = -1L
   ```
   The parser scans top-level boxes, recording `mdatStartOffset`, but does NOT record `moovStartOffset`. It therefore has no knowledge of whether `moov` precedes `mdat` (faststart layout) or `mdat` precedes `moov` (non-faststart layout).

2. **Hardcoded Fast-Start Output Assembly (lines 224–239)**:
   ```kotlin
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
   ```
   Regardless of original container box order, `M4aTagWriter` unconditionally writes `ftyp -> updatedMoov -> mdat`.

3. **Inadequate Offset Adjustment by `moovDelta` (lines 219–222 & 325–346)**:
   ```kotlin
   val moovDelta = updatedMoov.size - moovBytes.size

   // 4. Adjust stco / co64 chunk offsets
   adjustChunkOffsetsInMoovBuffer(updatedMoov, moovDelta, mdatStartOffset)
   ...
   val currentOffset = buf.getInt(entryOffset)
   if ((currentOffset.toLong() and 0xFFFFFFFFL) >= mdatStartOffset) {
       buf.putInt(entryOffset, currentOffset + moovDelta)
   }
   ```
   - In a non-faststart file (`ftyp -> mdat -> moov`), `mdatStartOffset` is e.g. 32 (directly after `ftyp`).
   - By writing `ftyp -> updatedMoov -> mdat`, `mdat` is physically moved from offset 32 to `32 + updatedMoov.size` (e.g. offset 35,032, a displacement of +35,000 bytes).
   - However, `moovDelta` is only `updatedMoov.size - moovBytes.size` (e.g. +120 bytes).
   - `adjustChunkOffsetsInMoovBuffer` shifts chunk offsets by only +120 bytes instead of +35,000 bytes.
   - **Result**: Every chunk offset in `stco` / `co64` points into `updatedMoov` (metadata header) instead of `mdat`, completely destroying audio playback for all non-faststart files.

---

### 1.2 Brute-Force Substring Search for `"stco"` / `"co64"` in Raw Bytes
In `app/src/main/java/com/example/gobeats/core/media/M4aTagWriter.kt` (lines 329–363):

```kotlin
// Scan for all 'stco' and 'co64' occurrences inside stbl
for (i in 0 until moovBytes.size - 16) {
    if (moovBytes[i + 4] == 's'.code.toByte() &&
        moovBytes[i + 5] == 't'.code.toByte() &&
        moovBytes[i + 6] == 'c'.code.toByte() &&
        moovBytes[i + 7] == 'o'.code.toByte()
    ) {
        val entryCount = buf.getInt(i + 12)
        var entryOffset = i + 16
        for (e in 0 until entryCount) {
            if (entryOffset + 4 > moovBytes.size) break
            val currentOffset = buf.getInt(entryOffset)
            if ((currentOffset.toLong() and 0xFFFFFFFFL) >= mdatStartOffset) {
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
```
1. `moovBytes` contains not only audio sample table boxes (`stbl`), but also user data (`udta`), metadata (`meta`), item lists (`ilst`), and embedded artwork (`covr`).
2. If binary image data inside `covr` (or any string tag) contains the 4-byte sequence `0x73 0x74 0x63 0x6F` (`"stco"`) or `0x63 0x6F 0x36 0x34` (`"co64"`):
   - Arbitrary image bytes at offset `i + 12` are interpreted as an integer `entryCount`.
   - The loop will iterate up to `entryCount` times, corrupting image bytes at 4- or 8-byte intervals.
   - If `entryCount` is negative or very large, it may cause out-of-bounds corruption or silent truncation.

---

## 2. Logic Chain

### 2.1 Chunk Offset Mathematics for ISO-BMFF Containers
1. **Premise 1 (Definition of Chunk Offset)**: In ISO/IEC 14496-12 (ISO Base Media File Format), entries in `stco` (32-bit) and `co64` (64-bit) boxes are absolute byte offsets from byte 0 of the container file to the beginning of each chunk of audio sample data inside `mdat`.
2. **Premise 2 (Physical Position of Chunks)**: If the `mdat` box begins at byte offset $O_{\text{old}}$ in the original file, chunk $k$ begins at $C_{\text{old}}[k] = O_{\text{old}} + \Delta_{\text{chunk}}[k]$.
3. **Premise 3 (Effect of Container Re-writing)**: In any rewritten file where `mdat` begins at $O_{\text{new}}$, chunk $k$ begins at $C_{\text{new}}[k] = O_{\text{new}} + \Delta_{\text{chunk}}[k]$.
4. **Premise 4 (Universal Offset Shift Formula)**:
   $$C_{\text{new}}[k] = C_{\text{old}}[k] + (O_{\text{new}} - O_{\text{old}})$$
   The required shift $\delta_{\text{mdat}}$ for any chunk offset pointing into `mdat` is strictly:
   $$\delta_{\text{mdat}} = O_{\text{new}} - O_{\text{old}} = \text{newMdatOffset} - \text{oldMdatOffset}$$
5. **Deduction 1 (Why `moovDelta` Failed)**:
   - In fast-start files (`ftyp -> moov -> mdat`):
     $O_{\text{old}} = L_{\text{ftyp}} + L_{\text{moov,old}}$, $O_{\text{new}} = L_{\text{ftyp}} + L_{\text{moov,new}}$.
     $O_{\text{new}} - O_{\text{old}} = L_{\text{moov,new}} - L_{\text{moov,old}} = \text{moovDelta}$.
     Here, and *only* here, does $\delta_{\text{mdat}} = \text{moovDelta}$.
   - In non-faststart files (`ftyp -> mdat -> moov`):
     $O_{\text{old}} = L_{\text{ftyp}}$, but $O_{\text{new}} = L_{\text{ftyp}} + L_{\text{moov,new}}$.
     $O_{\text{new}} - O_{\text{old}} = L_{\text{moov,new}}$ (the entire size of `moov`, e.g. 30 KB to 200 KB).
     Shifting by $\text{moovDelta}$ (e.g. 100 bytes) leaves offsets off by $L_{\text{moov,old}}$ (e.g. 30,000 bytes).

### 2.2 Container Order Preservation Strategy
1. **Preserving Order (`ftyp -> mdat -> updatedMoov`)**:
   - If the input file has $O_{\text{old}} < \text{moovStartOffset}$, keep `mdat` before `moov`.
   - Output order: `ftyp -> mdat -> updatedMoov`.
   - $O_{\text{new}} = L_{\text{ftyp}} = O_{\text{old}}$.
   - $\delta_{\text{mdat}} = O_{\text{new}} - O_{\text{old}} = 0$.
   - **Advantage A**: Zero offset recalculation needed. Existing `stco` offsets remain bit-for-bit identical.
   - **Advantage B**: Zero risk of 32-bit unsigned `stco` overflow (shifting chunks forward in files > 3.8 GB can overflow 32-bit `stco`, requiring conversion to `co64`).
   - **Advantage C**: Retains the original container layout expected by the encoder that created it.
2. **Faststart Preservation (`ftyp -> updatedMoov -> mdat`)**:
   - If the input file has $\text{moovStartOffset} < O_{\text{old}}$, keep `moov` before `mdat`.
   - $O_{\text{new}} = L_{\text{ftyp}} + L_{\text{moov,new}}$.
   - $\delta_{\text{mdat}} = O_{\text{new}} - O_{\text{old}} = L_{\text{moov,new}} - L_{\text{moov,old}}$.
   - Shift chunk offsets by $\delta_{\text{mdat}}$.

### 2.3 Structured Box Traversal vs Brute-Force Scanning
1. **Box Hierarchy of ISO-BMFF**:
   The ISO-BMFF specification establishes the exact tree hierarchy for chunk offset tables:
   ```
   moov (Movie container)
     └── trak (Track container)
           └── mdia (Media container)
                 └── minf (Media information container)
                       └── stbl (Sample table container)
                             ├── stco (32-bit chunk offset FullBox)
                             └── co64 (64-bit chunk offset FullBox)
   ```
2. **Isolation from Metadata & Cover Art**:
   Metadata and cover art are placed under:
   ```
   moov (Movie container)
     └── udta (User data container)
           └── meta (Metadata container)
                 └── ilst (Item list container)
                       └── covr (Cover art data)
   ```
   `udta` is a sibling of `trak`. A parser traversing down `moov -> trak -> mdia -> minf -> stbl` never inspects any child of `udta`.
3. **Conclusion**: Structured traversal eliminates 100% of false-positive matches in embedded cover art, artist/album strings, and lyrics.

---

## 3. Caveats

1. **Top-Level Padding Boxes (`free`, `skip`, `wide`)**:
   Some M4A encoders insert a small `free` or `wide` box (e.g. 8 bytes) before `mdat`. By computing $\delta_{\text{mdat}} = \text{newMdatOffset} - \text{oldMdatOffset}$ dynamically based on actual stream write position, any variance from preceding boxes is handled accurately.
2. **Multi-Track Files**:
   An M4A file may contain an audio `trak` and a chapter/subtitle `trak`. The structured box traversal must iterate *all* `trak` boxes inside `moov`, not just the first one.
3. **Public API Compatibility**:
   `M4aTagWriter.adjustStcoOffsets(originalOffsets: List<Int>, moovDelta: Int, mdatStartOffset: Int)` is called by existing tests in `Milestone2EmpiricalStressTest.kt` and `GobeatsTier1FeatureCoverageTest.kt`. This function signature must be preserved.
4. **Read-Only Scope**:
   As an explorer, this report contains the exact blueprint, algorithmic proofs, and code diffs for the implementer agent (`developer_m2_2` or equivalent). No production code was modified during this turn.

---

## 4. Conclusion & Concrete Fix Strategy

### 4.1 Fix Architecture Overview
The fix consists of three changes in `M4aTagWriter.kt`:

1. **Detect Container Layout & Calculate True `mdatDelta`**:
   - Record `moovStartOffset` and `mdatStartOffset` during initial top-level box scan.
   - Set `val isFastStart = moovStartOffset < mdatStartOffset`.
   - Calculate:
     - If `isFastStart`: `val newMdatOffset = ftypBytes.size.toLong() + updatedMoov.size.toLong()`.
     - If non-faststart: `val newMdatOffset = ftypBytes.size.toLong()`.
     - True delta: `val mdatDelta = newMdatOffset - mdatStartOffset`.
   - In output streaming:
     - If `isFastStart`: write `ftyp -> updatedMoov -> mdat`.
     - If non-faststart: write `ftyp -> mdat -> updatedMoov`.

2. **Hierarchical Atom Box Traversal for `stco` / `co64`**:
   - Replace linear byte scan with `traverseAndAdjustChunkOffsets(updatedMoov, mdatDelta, mdatStartOffset)`.
   - Descend: `moov` $\rightarrow$ iterate all `trak` $\rightarrow$ `mdia` $\rightarrow$ `minf` $\rightarrow$ `stbl` $\rightarrow$ `stco` or `co64`.
   - Check FullBox version/flags and entry bounds before modifying offsets.

---

### 4.2 Exact Implementation Code Blueprint

#### Component A: Structured Box Traversal Engine
Add this zero-allocation parser to `M4aTagWriter.kt`:

```kotlin
/**
 * Safely traverses the ISO-BMFF box tree:
 * moov -> trak -> mdia -> minf -> stbl -> stco / co64
 * strictly ignoring udta, meta, ilst, and covr to eliminate false positives.
 */
private fun adjustChunkOffsetsInMoovBuffer(
    moovBytes: ByteArray,
    mdatDelta: Long,
    mdatStartOffset: Long
) {
    if (mdatDelta == 0L) return
    val buf = ByteBuffer.wrap(moovBytes).order(ByteOrder.BIG_ENDIAN)

    // Helper to read box header: returns Triple(headerSize, boxSize, fourCc) or null
    fun readBox(offset: Int, limit: Int): Triple<Int, Long, String>? {
        if (offset + 8 > limit) return null
        val rawSize = buf.getInt(offset).toLong() and 0xFFFFFFFFL
        val fourCcBytes = ByteArray(4)
        buf.position(offset + 4)
        buf.get(fourCcBytes)
        val fourCc = String(fourCcBytes, StandardCharsets.ISO_8859_1)

        val (headerSize, totalSize) = when (rawSize) {
            1L -> {
                if (offset + 16 > limit) return null
                Pair(16, buf.getLong(offset + 8))
            }
            0L -> Pair(8, (limit - offset).toLong())
            else -> Pair(8, rawSize)
        }
        if (totalSize < headerSize || offset + totalSize > limit) return null
        return Triple(headerSize, totalSize, fourCc)
    }

    // moov payload starts after moov header (usually offset 8)
    val moovHeader = readBox(0, moovBytes.size) ?: return
    var moovChildOffset = moovHeader.first
    val moovLimit = moovBytes.size

    while (moovChildOffset + 8 <= moovLimit) {
        val trakBox = readBox(moovChildOffset, moovLimit) ?: break
        if (trakBox.third == "trak") {
            var trakChildOffset = moovChildOffset + trakBox.first
            val trakLimit = moovChildOffset + trakBox.second.toInt()

            while (trakChildOffset + 8 <= trakLimit) {
                val mdiaBox = readBox(trakChildOffset, trakLimit) ?: break
                if (mdiaBox.third == "mdia") {
                    var mdiaChildOffset = trakChildOffset + mdiaBox.first
                    val mdiaLimit = trakChildOffset + mdiaBox.second.toInt()

                    while (mdiaChildOffset + 8 <= mdiaLimit) {
                        val minfBox = readBox(mdiaChildOffset, mdiaLimit) ?: break
                        if (minfBox.third == "minf") {
                            var minfChildOffset = mdiaChildOffset + minfBox.first
                            val minfLimit = mdiaChildOffset + minfBox.second.toInt()

                            while (minfChildOffset + 8 <= minfLimit) {
                                val stblBox = readBox(minfChildOffset, minfLimit) ?: break
                                if (stblBox.third == "stbl") {
                                    var stblChildOffset = minfChildOffset + stblBox.first
                                    val stblLimit = minfChildOffset + stblBox.second.toInt()

                                    while (stblChildOffset + 8 <= stblLimit) {
                                        val sampleBox = readBox(stblChildOffset, stblLimit) ?: break
                                        when (sampleBox.third) {
                                            "stco" -> {
                                                // FullBox: 4 size, 4 'stco', 4 ver/flags, 4 entry_count
                                                val entryCountOffset = stblChildOffset + sampleBox.first + 4
                                                if (entryCountOffset + 4 <= stblLimit) {
                                                    val entryCount = buf.getInt(entryCountOffset)
                                                    var entryOffset = entryCountOffset + 4
                                                    for (e in 0 until entryCount) {
                                                        if (entryOffset + 4 > stblLimit) break
                                                        val currentOffset = buf.getInt(entryOffset).toLong() and 0xFFFFFFFFL
                                                        if (currentOffset >= mdatStartOffset) {
                                                            buf.putInt(entryOffset, (currentOffset + mdatDelta).toInt())
                                                        }
                                                        entryOffset += 4
                                                    }
                                                }
                                            }
                                            "co64" -> {
                                                // FullBox: 4 size, 4 'co64', 4 ver/flags, 4 entry_count
                                                val entryCountOffset = stblChildOffset + sampleBox.first + 4
                                                if (entryCountOffset + 4 <= stblLimit) {
                                                    val entryCount = buf.getInt(entryCountOffset)
                                                    var entryOffset = entryCountOffset + 4
                                                    for (e in 0 until entryCount) {
                                                        if (entryOffset + 8 > stblLimit) break
                                                        val currentOffset = buf.getLong(entryOffset)
                                                        if (currentOffset >= mdatStartOffset) {
                                                            buf.putLong(entryOffset, currentOffset + mdatDelta)
                                                        }
                                                        entryOffset += 8
                                                    }
                                                }
                                            }
                                        }
                                        stblChildOffset += sampleBox.second.toInt()
                                    }
                                }
                                minfChildOffset += stblBox.second.toInt()
                            }
                        }
                        mdiaChildOffset += minfBox.second.toInt()
                    }
                }
                trakChildOffset += mdiaBox.second.toInt()
            }
        }
        moovChildOffset += trakBox.second.toInt()
    }
}
```

---

#### Component B: Container Order Preservation & Dynamic Offset Math
In `M4aTagWriter.writeTagsToM4aFile`:

```kotlin
// In Step 1: Scan top-level boxes
var ftypBytes: ByteArray? = null
var moovBytes: ByteArray? = null
var moovStartOffset = -1L
var mdatStartOffset = -1L
var mdatSize = -1L

var pointer = 0L
while (pointer < fileLength) {
    raf.seek(pointer)
    if (fileLength - pointer < 8) break
    val boxSizeInt = raf.readInt()
    val fourCcBytes = ByteArray(4)
    raf.readFully(fourCcBytes)
    val fourCc = String(fourCcBytes, StandardCharsets.US_ASCII)

    var actualSize = boxSizeInt.toLong() and 0xFFFFFFFFL
    if (actualSize == 1L) {
        actualSize = raf.readLong()
    } else if (actualSize == 0L) {
        actualSize = fileLength - pointer
    }
    if (actualSize <= 0) break

    when (fourCc) {
        "ftyp" -> {
            val buf = ByteArray(actualSize.toInt())
            raf.seek(pointer)
            raf.readFully(buf)
            ftypBytes = buf
        }
        "moov" -> {
            moovStartOffset = pointer
            val buf = ByteArray(actualSize.toInt())
            raf.seek(pointer)
            raf.readFully(buf)
            moovBytes = buf
        }
        "mdat" -> {
            mdatStartOffset = pointer
            mdatSize = actualSize
        }
    }
    pointer += actualSize
}

if (ftypBytes == null || moovBytes == null || mdatStartOffset < 0L || moovStartOffset < 0L) {
    raf.close()
    return false
}

// 2. Determine container structure
val isFastStart = moovStartOffset < mdatStartOffset

// 3. Build updated udta & moov (preserving existing ilst atoms if present)
val updatedMoov = replaceOrAppendUdta(moovBytes, udtaBuf.array())

// 4. Calculate actual new mdat position and offset delta
val newMdatOffset = if (isFastStart) {
    ftypBytes.size.toLong() + updatedMoov.size.toLong()
} else {
    ftypBytes.size.toLong()
}
val mdatDelta = newMdatOffset - mdatStartOffset

// 5. Adjust stco / co64 chunk offsets via structured hierarchical traversal
adjustChunkOffsetsInMoovBuffer(updatedMoov, mdatDelta, mdatStartOffset)

// 6. Write to staging file preserving container order
FileOutputStream(tempFile).use { out ->
    if (isFastStart) {
        // Fast-start layout: ftyp -> updatedMoov -> mdat
        out.write(ftypBytes)
        out.write(updatedMoov)
        streamMdat(raf, mdatStartOffset, mdatSize, out)
    } else {
        // Non-faststart layout: ftyp -> mdat -> updatedMoov
        out.write(ftypBytes)
        streamMdat(raf, mdatStartOffset, mdatSize, out)
        out.write(updatedMoov)
    }
}
raf.close()
```

Helper for streaming `mdat`:
```kotlin
private fun streamMdat(raf: RandomAccessFile, startOffset: Long, size: Long, out: OutputStream) {
    raf.seek(startOffset)
    val buffer = ByteArray(65536)
    var remaining = size
    while (remaining > 0) {
        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
        val read = raf.read(buffer, 0, toRead)
        if (read == -1) break
        out.write(buffer, 0, read)
        remaining -= read
    }
}
```

---

## 5. Verification Method

### 5.1 New Regression Test Cases to Add to `Milestone2EmpiricalStressTest.kt`

#### Test 1: Non-Faststart Container Layout Preservation & Playback Integrity
```kotlin
@Test
fun m4a_nonFaststart_mdatPrecedesMoov_preservedAndPlayable() {
    val testDir = File(System.getProperty("java.io.tmpdir"), "m4a_nonfaststart_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
        val synthFile = File(testDir, "non_faststart.m4a")
        val audioPayload = ByteArray(4096) { (it xor 0x33).toByte() }

        // Synthesize ftyp -> mdat -> moov
        val ftypBytes = ByteArray(32).apply {
            ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(32)
                put("ftypM4A ".toByteArray(StandardCharsets.US_ASCII))
            }
        }
        val mdatBytes = ByteArray(8 + audioPayload.size).apply {
            ByteBuffer.wrap(this).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(size)
                put("mdat".toByteArray(StandardCharsets.US_ASCII))
                put(audioPayload)
            }
        }
        val originalChunkOffsets = listOf(ftypBytes.size + 8, ftypBytes.size + 8 + 1024)
        val moovBytes = synthesizeMoovWithStco(originalChunkOffsets)

        FileOutputStream(synthFile).use {
            it.write(ftypBytes)
            it.write(mdatBytes)
            it.write(moovBytes)
        }

        // Apply tag edit
        val payload = TagEditPayload(trackId = "t1", title = "New Title", artist = "New Artist", album = "New Album")
        val success = M4aTagWriter.writeTagsToM4aFile(synthFile, payload, stagingDir = testDir)
        assertTrue(success)

        // Verify container order is ftyp -> mdat -> moov
        RandomAccessFile(synthFile, "r").use { raf ->
            val b0Size = raf.readInt()
            val b0FourCc = ByteArray(4).also { raf.readFully(it) }
            assertEquals("ftyp", String(b0FourCc, StandardCharsets.US_ASCII))

            raf.seek(b0Size.toLong())
            val b1Size = raf.readInt()
            val b1FourCc = ByteArray(4).also { raf.readFully(it) }
            assertEquals("mdat", String(b1FourCc, StandardCharsets.US_ASCII))

            // Verify audio chunks in mdat are 100% bit-perfect
            val readAudio = ByteArray(audioPayload.size)
            raf.readFully(readAudio)
            assertArrayEquals(audioPayload, readAudio)

            // Verify stco chunk offsets are untouched (delta == 0)
            raf.seek((b0Size + b1Size).toLong())
            val b2Size = raf.readInt()
            val b2FourCc = ByteArray(4).also { raf.readFully(it) }
            assertEquals("moov", String(b2FourCc, StandardCharsets.US_ASCII))
            val readMoov = ByteArray(b2Size).also {
                raf.seek((b0Size + b1Size).toLong())
                raf.readFully(it)
            }
            val stcoOffsets = extractStcoOffsets(readMoov)
            assertEquals(originalChunkOffsets, stcoOffsets)
        }
    } finally {
        testDir.deleteRecursively()
    }
}
```

#### Test 2: Binary Image Payload Immunity to False-Positive `"stco"` / `"co64"` Matches
```kotlin
@Test
fun m4a_structuredTraversal_ignoresStcoSequenceInEmbeddedImage() {
    val testDir = File(System.getProperty("java.io.tmpdir"), "m4a_false_stco_${System.currentTimeMillis()}").apply { mkdirs() }
    try {
        val synthFile = File(testDir, "fake_stco_art.m4a")
        // Embed an image that maliciously contains 's','t','c','o', 0x00, 0x00, 0x05, 0x00
        val maliciousImage = ByteArray(1024) { 0xFF.toByte() }.apply {
            val fakeStco = "stco".toByteArray(StandardCharsets.US_ASCII)
            System.arraycopy(fakeStco, 0, this, 100, 4)
            this[108] = 0x00
            this[109] = 0x00
            this[110] = 0x04 // 4 fake entries
            this[111] = 0x00
        }

        val payload = TagEditPayload(
            trackId = "t2",
            title = "Test False Match",
            artist = "Artist",
            album = "Album",
            newArtworkBytes = maliciousImage
        )
        // Synthesize valid fast-start file with initial payload
        synthesizeM4aFile(synthFile, initialTitle = "Old", audioPayload = ByteArray(2048))

        val success = M4aTagWriter.writeTagsToM4aFile(synthFile, payload, stagingDir = testDir)
        assertTrue(success)

        // Verify image bytes in rewritten file were not mutated
        val extractedImage = extractCoverArtFromM4a(synthFile)
        assertArrayEquals("Image containing 'stco' bytes must remain completely intact", maliciousImage, extractedImage)
    } finally {
        testDir.deleteRecursively()
    }
}
```

### 5.2 Build & Test Verification Commands
Execute from project root `c:\Users\ayush\OneDrive\Documents\gobeats`:

1. **Verify Kotlin Compilation**:
   ```powershell
   .\gradlew.bat compileDebugKotlin
   ```
   *Expected*: `BUILD SUCCESSFUL` with 0 errors.

2. **Verify Milestone 2 Unit Test Suite**:
   ```powershell
   .\gradlew.bat testDebugUnitTest --tests "com.example.gobeats.Milestone2EmpiricalStressTest"
   ```
   *Expected*: All tests pass 100%.

3. **Verify Full Test Suite Baseline**:
   ```powershell
   .\gradlew.bat testDebugUnitTest
   ```
   *Expected*: 211+ tests passing with 0 failures.

### 5.3 Invalidation Conditions
This strategy is invalidated if:
1. An ISO-BMFF container has top-level `mdat` preceding `ftyp` (violating ISO/IEC 14496-12 standard where `ftyp` must be the first box).
2. A non-faststart M4A file's `moov` cannot fit in memory (M4A audio `moov` boxes are metadata headers typically under 500 KB; even for multi-hour audio, `moov` rarely exceeds 2 MB).
