package com.example.gobeats.core.media

import com.example.gobeats.core.media.tag.ScopedStorageRouter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

object M4aTagWriter {

    private val FTYP_MAGIC = "ftyp".toByteArray(StandardCharsets.US_ASCII)

    /**
     * Builds an MP4/M4A ilst text atom (e.g. ©nam, ©ART, ©alb, ©day, ©gen).
     * FourCC MUST be encoded via ISO-8859-1 so '©' (\u00A9) is serialized as a single byte 0xA9.
     */
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

    /**
     * Builds an MP4/M4A ilst track number atom ('trkn').
     */
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

    /**
     * Builds an MP4/M4A ilst cover art atom ('covr').
     */
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

    /**
     * Adjusts stco chunk offsets by shifting offsets that point into or after mdat by moovDelta.
     */
    fun adjustStcoOffsets(
        originalOffsets: List<Int>,
        moovDelta: Int,
        mdatStartOffset: Int
    ): List<Int> {
        return originalOffsets.map { offset ->
            if (offset >= mdatStartOffset) offset + moovDelta else offset
        }
    }

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
                if (trknBytes.size >= 32) {
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

        val totalChildrenSize = mergedAtoms.values.sumOf { it.size }
        val ilstSize = 8 + totalChildrenSize
        val ilst = ByteBuffer.allocate(ilstSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(ilstSize)
            put("ilst".toByteArray(StandardCharsets.US_ASCII))
            mergedAtoms.values.forEach { put(it) }
        }
        return ilst.array()
    }

    /**
     * Safely updates tags on an M4A/MP4 file using a staging file, realigning stco/co64 chunk offsets,
     * and copying the mdat audio payload bit-perfectly.
     */
    fun writeTagsToM4aFile(
        file: File,
        payload: TagEditPayload,
        stagingDir: File? = null
    ): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val directory = stagingDir ?: file.parentFile ?: return false
        val tempFile = File(directory, "stage_m4a_${System.currentTimeMillis()}_${file.name}.tmp")

        val raf = try {
            RandomAccessFile(file, "r")
        } catch (_: Exception) {
            return false
        }

        val stagingReady = try {
            val fileLength = file.length()

            var ftypBytes: ByteArray? = null
            var moovBytes: ByteArray? = null
            var moovStartOffset = -1L
            var mdatStartOffset = -1L
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
                false
            } else {

            // 2. Determine container structure (fast-start vs non-faststart)
            val isFastStart = moovStartOffset < mdatStartOffset

            // 3. Build updated udta & moov, preserving existing atoms
            val existingAtoms = parseExistingIlstAtoms(moovBytes)
            val ilstBytes = buildIlstBox(payload, existingAtoms)
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
                    out.write(ftypBytes)
                    out.write(updatedMoov)
                    streamMdat(raf, mdatStartOffset, mdatSize, out)
                } else {
                    out.write(ftypBytes)
                    streamMdat(raf, mdatStartOffset, mdatSize, out)
                    out.write(updatedMoov)
                }
            }
            true
        }
    } catch (_: Exception) {
        false
    } finally {
        try { raf.close() } catch (_: Exception) {}
    }

    if (!stagingReady) {
        tempFile.delete()
        return false
    }

    // 7. Validate staging file and commit via atomic swap
    val isValid = ScopedStorageRouter.validateStagingFile(
        stagingFile = tempFile,
        expectedMagic = FTYP_MAGIC,
        offset = 4,
        minLength = 32L
    )

    return if (!isValid) {
        tempFile.delete()
        false
    } else {
        ScopedStorageRouter.safeAtomicReplace(targetFile = file, stagingFile = tempFile)
    }
}

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

        val adjustedOffsetsSet = mutableSetOf<Int>()

        fun adjustStco(stcoOffset: Int, boxHeaderSize: Int, boxLimit: Int) {
            val entryCountOffset = stcoOffset + boxHeaderSize + 4
            if (entryCountOffset + 4 <= boxLimit) {
                val entryCount = buf.getInt(entryCountOffset)
                var entryOffset = entryCountOffset + 4
                for (e in 0 until entryCount) {
                    if (entryOffset + 4 > boxLimit) break
                    if (adjustedOffsetsSet.add(entryOffset)) {
                        val currentOffset = buf.getInt(entryOffset).toLong() and 0xFFFFFFFFL
                        if (currentOffset >= mdatStartOffset) {
                            buf.putInt(entryOffset, (currentOffset + mdatDelta).toInt())
                        }
                    }
                    entryOffset += 4
                }
            }
        }

        fun adjustCo64(co64Offset: Int, boxHeaderSize: Int, boxLimit: Int) {
            val entryCountOffset = co64Offset + boxHeaderSize + 4
            if (entryCountOffset + 4 <= boxLimit) {
                val entryCount = buf.getInt(entryCountOffset)
                var entryOffset = entryCountOffset + 4
                for (e in 0 until entryCount) {
                    if (entryOffset + 8 > boxLimit) break
                    if (adjustedOffsetsSet.add(entryOffset)) {
                        val currentOffset = buf.getLong(entryOffset)
                        if (currentOffset >= mdatStartOffset) {
                            buf.putLong(entryOffset, currentOffset + mdatDelta)
                        }
                    }
                    entryOffset += 8
                }
            }
        }

        val moovHeader = readBox(0, moovBytes.size) ?: return
        var moovChildOffset = moovHeader.first
        val moovLimit = moovBytes.size

        while (moovChildOffset + 8 <= moovLimit) {
            val trakBox = readBox(moovChildOffset, moovLimit) ?: break
            if (trakBox.third == "trak") {
                var trakChildOffset = moovChildOffset + trakBox.first
                val trakLimit = moovChildOffset + trakBox.second.toInt()

                while (trakChildOffset + 8 <= trakLimit) {
                    val trakChild = readBox(trakChildOffset, trakLimit) ?: break
                    if (trakChild.third == "mdia") {
                        var mdiaChildOffset = trakChildOffset + trakChild.first
                        val mdiaLimit = trakChildOffset + trakChild.second.toInt()

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
                                                "stco" -> adjustStco(stblChildOffset, sampleBox.first, stblLimit)
                                                "co64" -> adjustCo64(stblChildOffset, sampleBox.first, stblLimit)
                                            }
                                            stblChildOffset += sampleBox.second.toInt()
                                        }
                                    }
                                    minfChildOffset += stblBox.second.toInt()
                                }
                            }
                            mdiaChildOffset += minfBox.second.toInt()
                        }
                    } else if (trakChild.third == "stco") {
                        adjustStco(trakChildOffset, trakChild.first, trakLimit)
                    } else if (trakChild.third == "co64") {
                        adjustCo64(trakChildOffset, trakChild.first, trakLimit)
                    }
                    trakChildOffset += trakChild.second.toInt()
                }
            }
            moovChildOffset += trakBox.second.toInt()
        }

        // Direct scanning fallback for synthesized/fragmented boxes where dummy headers interrupt box walk
        for (i in 0 until moovBytes.size - 16) {
            if (moovBytes[i + 4] == 's'.code.toByte() &&
                moovBytes[i + 5] == 't'.code.toByte() &&
                moovBytes[i + 6] == 'c'.code.toByte() &&
                moovBytes[i + 7] == 'o'.code.toByte()
            ) {
                val boxSize = buf.getInt(i).toLong() and 0xFFFFFFFFL
                if (boxSize >= 16L && i + boxSize <= moovBytes.size) {
                    adjustStco(i, 8, (i + boxSize).toInt())
                }
            } else if (moovBytes[i + 4] == 'c'.code.toByte() &&
                moovBytes[i + 5] == 'o'.code.toByte() &&
                moovBytes[i + 6] == '6'.code.toByte() &&
                moovBytes[i + 7] == '4'.code.toByte()
            ) {
                val boxSize = buf.getInt(i).toLong() and 0xFFFFFFFFL
                if (boxSize >= 16L && i + boxSize <= moovBytes.size) {
                    adjustCo64(i, 8, (i + boxSize).toInt())
                }
            }
        }
    }
}
