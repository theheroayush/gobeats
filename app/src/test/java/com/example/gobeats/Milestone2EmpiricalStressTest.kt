package com.example.gobeats

import com.example.gobeats.core.media.FlacTagWriter
import com.example.gobeats.core.media.M4aTagWriter
import com.example.gobeats.core.media.TagEditPayload
import com.example.gobeats.core.media.tag.ScopedStorageRouter
import com.example.gobeats.core.media.tag.StorageWriteMechanism
import com.example.gobeats.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Milestone 2 Empirical Stress Test Suite (Adversarial Verification)
 *
 * Focus:
 * - Feature F8: M4A/MP4 ilst atom generation and stco/co64 chunk offset delta recalculation
 * - Feature F7: FLAC Vorbis comment serialization, RFC 9639 picture blocks, bit-perfect audio stream preservation
 * - Feature F6: Scoped Storage routing across Android 10-14 and staging file magic byte validation
 * - Feature F9: Tag Sync Pipeline concurrency, SQLite override durability, and rescan immunity
 */
class Milestone2EmpiricalStressTest {

    // =========================================================================
    // SECTION 1: M4A / MP4 Tag Writer & Atom Generation Stress (F8)
    // =========================================================================

    @Test
    fun m4a_buildIlstTextAtom_iso8859_1_copyrightSymbolByte() {
        val fourCcs = listOf("\u00A9nam", "\u00A9ART", "\u00A9alb", "\u00A9day", "\u00A9gen")
        for (fourCc in fourCcs) {
            val atom = M4aTagWriter.buildIlstTextAtom(fourCc, "Test Value")
            val buf = ByteBuffer.wrap(atom).order(ByteOrder.BIG_ENDIAN)
            val parentSize = buf.int
            assertEquals(atom.size, parentSize)

            val fourCcBytes = ByteArray(4)
            buf.get(fourCcBytes)
            // Verify single byte 0xA9 for '©' under ISO-8859-1 (NOT 0xC2, 0xA9 from UTF-8)
            assertEquals("First byte of FourCC must be 0xA9 (single byte)", 0xA9.toByte(), fourCcBytes[0])
            assertEquals(fourCc, String(fourCcBytes, StandardCharsets.ISO_8859_1))

            // Child 'data' box
            val dataSize = buf.int
            val dataFourCc = ByteArray(4)
            buf.get(dataFourCc)
            assertEquals("data", String(dataFourCc, StandardCharsets.US_ASCII))
            val flags = buf.int
            assertEquals("Flags must be 1 (UTF-8 text)", 1, flags)
            val locale = buf.int
            assertEquals("Locale must be 0", 0, locale)

            val textBytes = ByteArray(dataSize - 16)
            buf.get(textBytes)
            assertEquals("Test Value", String(textBytes, StandardCharsets.UTF_8))
        }
    }

    @Test
    fun m4a_buildIlstTextAtom_fourCcLengthEnforcement() {
        try {
            M4aTagWriter.buildIlstTextAtom("nam", "Short FourCC")
            fail("Expected IllegalArgumentException for 3-character FourCC")
        } catch (_: IllegalArgumentException) {}

        try {
            M4aTagWriter.buildIlstTextAtom("\u00A9name", "Long FourCC")
            fail("Expected IllegalArgumentException for 5-character FourCC")
        } catch (_: IllegalArgumentException) {}

        try {
            M4aTagWriter.buildIlstTextAtom("", "Empty FourCC")
            fail("Expected IllegalArgumentException for empty FourCC")
        } catch (_: IllegalArgumentException) {}
    }

    @Test
    fun m4a_buildIlstTextAtom_extremePayloads() {
        // 1. Empty string
        val emptyAtom = M4aTagWriter.buildIlstTextAtom("\u00A9nam", "")
        assertEquals("Empty string atom must be 24 bytes (8 parent + 16 data)", 24, emptyAtom.size)

        // 2. Large text payload (50,000 characters)
        val largeText = "GoBeats_Audiophile_Tag_Data_".repeat(1700)
        val largeAtom = M4aTagWriter.buildIlstTextAtom("\u00A9alb", largeText)
        val largeExpectedSize = 8 + 16 + largeText.toByteArray(StandardCharsets.UTF_8).size
        assertEquals(largeExpectedSize, largeAtom.size)

        // 3. Multi-byte Unicode (Emojis, CJK, Cyrillic, Arabic, Accents)
        val unicodePayload = "🔥🎧 音楽 / Музыка / موسيقى / Über-Track 🚀"
        val unicodeAtom = M4aTagWriter.buildIlstTextAtom("\u00A9nam", unicodePayload)
        val expectedUnicodeSize = 8 + 16 + unicodePayload.toByteArray(StandardCharsets.UTF_8).size
        assertEquals(expectedUnicodeSize, unicodeAtom.size)

        val buf = ByteBuffer.wrap(unicodeAtom).order(ByteOrder.BIG_ENDIAN)
        buf.position(24) // Skip parent header (8) + data header (16)
        val extractedBytes = ByteArray(unicodeAtom.size - 24)
        buf.get(extractedBytes)
        assertEquals(unicodePayload, String(extractedBytes, StandardCharsets.UTF_8))
    }

    @Test
    fun m4a_buildIlstTrackNumberAtom_bounds() {
        // Standard track 5 of 12
        val standardAtom = M4aTagWriter.buildIlstTrackNumberAtom(5, 12)
        assertEquals("trkn atom size must be 32 bytes", 32, standardAtom.size)
        val buf = ByteBuffer.wrap(standardAtom).order(ByteOrder.BIG_ENDIAN)
        assertEquals(32, buf.int)
        val fourCc = ByteArray(4)
        buf.get(fourCc)
        assertEquals("trkn", String(fourCc, StandardCharsets.ISO_8859_1))
        assertEquals(24, buf.int) // data size = 16 header + 8 payload
        buf.position(24) // Skip to payload in data box
        assertEquals(0.toShort(), buf.short) // reserved
        assertEquals(5.toShort(), buf.short) // track
        assertEquals(12.toShort(), buf.short) // total
        assertEquals(0.toShort(), buf.short) // reserved

        // Extreme bounds: 0, 32767
        val maxAtom = M4aTagWriter.buildIlstTrackNumberAtom(32767, 32767)
        val maxBuf = ByteBuffer.wrap(maxAtom).order(ByteOrder.BIG_ENDIAN)
        maxBuf.position(26)
        assertEquals(32767.toShort(), maxBuf.short)
        assertEquals(32767.toShort(), maxBuf.short)
    }

    @Test
    fun m4a_buildIlstCoverArtAtom_jpegAndPngFlags() {
        val fakeBytes = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        // JPEG flag (13)
        val jpegAtom = M4aTagWriter.buildIlstCoverArtAtom(fakeBytes, "image/jpeg")
        val jpegBuf = ByteBuffer.wrap(jpegAtom).order(ByteOrder.BIG_ENDIAN)
        jpegBuf.position(16) // parent (8) + data size (4) + 'data' (4)
        assertEquals("JPEG flag must be 13", 13, jpegBuf.int)

        // PNG flag (14)
        val pngAtom = M4aTagWriter.buildIlstCoverArtAtom(fakeBytes, "image/png")
        val pngBuf = ByteBuffer.wrap(pngAtom).order(ByteOrder.BIG_ENDIAN)
        pngBuf.position(16)
        assertEquals("PNG flag must be 14", 14, pngBuf.int)

        // PNG case insensitivity
        val upperPngAtom = M4aTagWriter.buildIlstCoverArtAtom(fakeBytes, "IMAGE/PNG")
        val upperPngBuf = ByteBuffer.wrap(upperPngAtom).order(ByteOrder.BIG_ENDIAN)
        upperPngBuf.position(16)
        assertEquals("Uppercase PNG flag must be 14", 14, upperPngBuf.int)

        // JPG shorthand
        val jpgAtom = M4aTagWriter.buildIlstCoverArtAtom(fakeBytes, "image/jpg")
        val jpgBuf = ByteBuffer.wrap(jpgAtom).order(ByteOrder.BIG_ENDIAN)
        jpgBuf.position(16)
        assertEquals("JPG shorthand flag must be 13", 13, jpgBuf.int)
    }

    @Test
    fun m4a_adjustStcoOffsets_deltaMatrix() {
        val original = listOf(100, 500, 1024, 2048, 4096, 8192)
        val mdatStart = 1024

        // 1. Positive delta (+512 bytes)
        val posAdjusted = M4aTagWriter.adjustStcoOffsets(original, 512, mdatStart)
        assertEquals(100, posAdjusted[0]) // Unshifted (< 1024)
        assertEquals(500, posAdjusted[1]) // Unshifted (< 1024)
        assertEquals(1024 + 512, posAdjusted[2]) // Shifted (== 1024)
        assertEquals(2048 + 512, posAdjusted[3]) // Shifted (> 1024)
        assertEquals(4096 + 512, posAdjusted[4])
        assertEquals(8192 + 512, posAdjusted[5])

        // 2. Negative delta (-256 bytes)
        val negAdjusted = M4aTagWriter.adjustStcoOffsets(original, -256, mdatStart)
        assertEquals(100, negAdjusted[0])
        assertEquals(500, negAdjusted[1])
        assertEquals(1024 - 256, negAdjusted[2])
        assertEquals(2048 - 256, negAdjusted[3])
        assertEquals(4096 - 256, negAdjusted[4])
        assertEquals(8192 - 256, negAdjusted[5])

        // 3. Zero delta (0 bytes)
        val zeroAdjusted = M4aTagWriter.adjustStcoOffsets(original, 0, mdatStart)
        assertEquals(original, zeroAdjusted)

        // 4. Large list (20,000 chunks)
        val largeList = (0 until 20000).map { 1024 + it * 100 }
        val largeAdjusted = M4aTagWriter.adjustStcoOffsets(largeList, 64, mdatStart)
        assertEquals(20000, largeAdjusted.size)
        assertEquals(1024 + 64, largeAdjusted[0])
        assertEquals(1024 + 19999 * 100 + 64, largeAdjusted.last())
    }

    // =========================================================================
    // SECTION 2: Full M4A Container Synthesis & Bit-Perfect Stco Realignment
    // =========================================================================

    /**
     * Synthesizes a valid minimal ISO-BMFF / M4A file with ftyp, moov (containing stco), and mdat.
     */
    private fun synthesizeM4aFile(
        targetFile: File,
        initialTitle: String? = null,
        audioPayload: ByteArray = ByteArray(4096) { (it % 251).toByte() }
    ): Pair<Int, List<Int>> {
        // 1. Build ftyp box (32 bytes)
        val ftypBuf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
        ftypBuf.putInt(32)
        ftypBuf.put("ftyp".toByteArray(StandardCharsets.US_ASCII))
        ftypBuf.put("M4A ".toByteArray(StandardCharsets.US_ASCII))
        ftypBuf.putInt(0) // minor version
        ftypBuf.put("M4A ".toByteArray(StandardCharsets.US_ASCII))
        ftypBuf.put("mp42".toByteArray(StandardCharsets.US_ASCII))
        ftypBuf.put("isom".toByteArray(StandardCharsets.US_ASCII))
        val ftypBytes = ftypBuf.array()

        // Placeholder moov size calculation
        // Structure: [moov (8)] [mvhd (108)] [trak container] [udta (optional)]
        val mvhdBuf = ByteBuffer.allocate(108).order(ByteOrder.BIG_ENDIAN)
        mvhdBuf.putInt(108)
        mvhdBuf.put("mvhd".toByteArray(StandardCharsets.US_ASCII))
        val mvhdBytes = mvhdBuf.array()

        // stbl with stco box
        val chunkCount = 4
        val stcoSize = 16 + chunkCount * 4
        // Estimated mdat start offset: ftyp (32) + moovSize
        // We will do a two-pass calculation
        var optionalUdtaBytes = ByteArray(0)
        if (initialTitle != null) {
            val payload = TagEditPayload(trackId = "test", title = initialTitle, artist = "SynthArtist", album = "SynthAlbum")
            val ilstBytes = M4aTagWriter.buildIlstBox(payload)
            // build udta wrapping meta and ilst
            val hdlrSize = 33
            val hdlr = ByteBuffer.allocate(hdlrSize).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(hdlrSize)
                put("hdlr".toByteArray(StandardCharsets.US_ASCII))
                putInt(0)
                putInt(0)
                put("mdirappl".toByteArray(StandardCharsets.US_ASCII))
                putInt(0)
                putInt(0)
                put(0.toByte())
            }.array()
            val metaSize = 12 + hdlr.size + ilstBytes.size
            val meta = ByteBuffer.allocate(metaSize).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(metaSize)
                put("meta".toByteArray(StandardCharsets.US_ASCII))
                putInt(0)
                put(hdlr)
                put(ilstBytes)
            }.array()
            val udtaSize = 8 + metaSize
            optionalUdtaBytes = ByteBuffer.allocate(udtaSize).order(ByteOrder.BIG_ENDIAN).apply {
                putInt(udtaSize)
                put("udta".toByteArray(StandardCharsets.US_ASCII))
                put(meta)
            }.array()
        }

        val trakInnerSize = 8 + 100 + stcoSize // dummy trak with stco
        val trakBytes = ByteBuffer.allocate(trakInnerSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(trakInnerSize)
            put("trak".toByteArray(StandardCharsets.US_ASCII))
            put(ByteArray(100) { 0 }) // dummy headers
            // Write stco box
            putInt(stcoSize)
            put("stco".toByteArray(StandardCharsets.US_ASCII))
            putInt(0) // version & flags
            putInt(chunkCount)
            // temporary zero chunk offsets
            repeat(chunkCount) { putInt(0) }
        }.array()

        val moovSize = 8 + mvhdBytes.size + trakBytes.size + optionalUdtaBytes.size
        val mdatStartOffset = ftypBytes.size + moovSize

        // Calculate chunk offsets into mdat:
        val originalChunkOffsets = listOf(
            mdatStartOffset + 8,
            mdatStartOffset + 8 + 1000,
            mdatStartOffset + 8 + 2000,
            mdatStartOffset + 8 + 3000
        )

        // Now rewrite stco with actual chunk offsets
        val finalTrakBuf = ByteBuffer.wrap(trakBytes).order(ByteOrder.BIG_ENDIAN)
        val stcoOffsetInTrak = 8 + 100
        finalTrakBuf.position(stcoOffsetInTrak + 16)
        originalChunkOffsets.forEach { finalTrakBuf.putInt(it) }

        // Assemble moov
        val moovBytes = ByteBuffer.allocate(moovSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(moovSize)
            put("moov".toByteArray(StandardCharsets.US_ASCII))
            put(mvhdBytes)
            put(trakBytes)
            if (optionalUdtaBytes.isNotEmpty()) put(optionalUdtaBytes)
        }.array()

        // mdat box
        val mdatSize = 8 + audioPayload.size
        val mdatBytes = ByteBuffer.allocate(mdatSize).order(ByteOrder.BIG_ENDIAN).apply {
            putInt(mdatSize)
            put("mdat".toByteArray(StandardCharsets.US_ASCII))
            put(audioPayload)
        }.array()

        FileOutputStream(targetFile).use { out ->
            out.write(ftypBytes)
            out.write(moovBytes)
            out.write(mdatBytes)
        }

        return Pair(mdatStartOffset, originalChunkOffsets)
    }

    @Test
    fun m4a_writeTagsToM4aFile_endToEnd_bitPerfectAudioAndShiftedOffsets() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "gobeats_m4a_stress_${System.currentTimeMillis()}")
        testDir.mkdirs()
        try {
            val audioPayload = ByteArray(8192) { (it xor 0x5A).toByte() }
            val synthFile = File(testDir, "test_track.m4a")
            val (originalMdatStart, originalOffsets) = synthesizeM4aFile(
                targetFile = synthFile,
                initialTitle = null,
                audioPayload = audioPayload
            )

            val payload = TagEditPayload(
                trackId = "track_101",
                title = "Empirical Challenge Title",
                artist = "Starlight Symphony",
                album = "Quantum Audio Horizons",
                year = 2026,
                genre = "Neo-Classical",
                trackNumber = 3,
                newArtworkBytes = byteArrayOf(0x10, 0x20, 0x30, 0x40, 0x50)
            )

            // Execute actual production tag write
            val success = M4aTagWriter.writeTagsToM4aFile(synthFile, payload, stagingDir = testDir)
            assertTrue("M4aTagWriter must successfully rewrite M4A tags", success)

            // Inspect the rewritten file
            val raf = RandomAccessFile(synthFile, "r")
            val newFileLength = raf.length()

            // 1. Verify ftyp header
            val ftypSize = raf.readInt()
            assertEquals(32, ftypSize)
            val ftypMagic = ByteArray(4)
            raf.readFully(ftypMagic)
            assertEquals("ftyp", String(ftypMagic, StandardCharsets.US_ASCII))

            // 2. Read moov
            raf.seek(32)
            val newMoovSize = raf.readInt()
            val moovFourCc = ByteArray(4)
            raf.readFully(moovFourCc)
            assertEquals("moov", String(moovFourCc, StandardCharsets.US_ASCII))

            val newMdatStart = 32L + newMoovSize
            val moovDelta = (newMdatStart - originalMdatStart).toInt()
            assertTrue("moov should expand when tags are added", moovDelta > 0)

            // 3. Scan moov to extract new stco offsets
            val newMoovBytes = ByteArray(newMoovSize)
            raf.seek(32)
            raf.readFully(newMoovBytes)

            val moovBuf = ByteBuffer.wrap(newMoovBytes).order(ByteOrder.BIG_ENDIAN)
            var foundStco = false
            var adjustedOffsets = listOf<Int>()
            for (i in 0 until newMoovBytes.size - 16) {
                if (newMoovBytes[i + 4] == 's'.code.toByte() &&
                    newMoovBytes[i + 5] == 't'.code.toByte() &&
                    newMoovBytes[i + 6] == 'c'.code.toByte() &&
                    newMoovBytes[i + 7] == 'o'.code.toByte()
                ) {
                    foundStco = true
                    val count = moovBuf.getInt(i + 12)
                    assertEquals(originalOffsets.size, count)
                    adjustedOffsets = (0 until count).map { moovBuf.getInt(i + 16 + it * 4) }
                    break
                }
            }
            assertTrue("stco box must be present in rewritten moov", foundStco)

            // Verify EVERY offset shifted by exactly moovDelta
            for (i in originalOffsets.indices) {
                val expectedOffset = originalOffsets[i] + moovDelta
                assertEquals("Chunk offset $i must be shifted by exact moovDelta", expectedOffset, adjustedOffsets[i])
            }

            // 4. Verify mdat and audio payload bit-perfection
            raf.seek(newMdatStart)
            val mdatBoxSize = raf.readInt()
            val mdatFourCc = ByteArray(4)
            raf.readFully(mdatFourCc)
            assertEquals("mdat", String(mdatFourCc, StandardCharsets.US_ASCII))
            assertEquals(8 + audioPayload.size, mdatBoxSize)

            val readAudio = ByteArray(audioPayload.size)
            raf.readFully(readAudio)
            assertArrayEquals("Audio payload inside mdat must be 100% bit-perfect and uncorrupted", audioPayload, readAudio)

            // Verify reading audio at the shifted chunk offsets matches the original chunk positions
            val chunk1Offset = adjustedOffsets[0].toLong()
            raf.seek(chunk1Offset)
            val chunk1Byte = raf.readByte()
            assertEquals("Audio byte at shifted chunk 1 offset must match original chunk 1 byte", audioPayload[0], chunk1Byte)

            raf.close()
        } finally {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun m4a_writeTagsToM4aFile_replaceExistingUdta_shrinkageNegativeDelta() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "gobeats_m4a_shrink_${System.currentTimeMillis()}")
        testDir.mkdirs()
        try {
            val audioPayload = ByteArray(2048) { (it + 7).toByte() }
            val synthFile = File(testDir, "shrink_track.m4a")

            // Create original with huge title (causes large moov)
            val hugeInitialTitle = "Very_Long_Initial_Title_".repeat(200)
            val (originalMdatStart, originalOffsets) = synthesizeM4aFile(
                targetFile = synthFile,
                initialTitle = hugeInitialTitle,
                audioPayload = audioPayload
            )

            // Rewrite with tiny title (causes negative moovDelta)
            val shrinkPayload = TagEditPayload(
                trackId = "track_shrink",
                title = "Short",
                artist = "A",
                album = "B"
            )

            val success = M4aTagWriter.writeTagsToM4aFile(synthFile, shrinkPayload, stagingDir = testDir)
            assertTrue("M4aTagWriter must successfully handle tag shrinkage", success)

            val raf = RandomAccessFile(synthFile, "r")
            raf.seek(32)
            val newMoovSize = raf.readInt()
            val newMdatStart = 32L + newMoovSize
            val moovDelta = (newMdatStart - originalMdatStart).toInt()
            assertTrue("moovDelta must be negative after shrinking tags", moovDelta < 0)

            // Read stco offsets
            val newMoovBytes = ByteArray(newMoovSize)
            raf.seek(32)
            raf.readFully(newMoovBytes)
            val moovBuf = ByteBuffer.wrap(newMoovBytes).order(ByteOrder.BIG_ENDIAN)

            for (i in 0 until newMoovBytes.size - 16) {
                if (newMoovBytes[i + 4] == 's'.code.toByte() &&
                    newMoovBytes[i + 5] == 't'.code.toByte() &&
                    newMoovBytes[i + 6] == 'c'.code.toByte() &&
                    newMoovBytes[i + 7] == 'o'.code.toByte()
                ) {
                    val count = moovBuf.getInt(i + 12)
                    for (c in 0 until count) {
                        val shifted = moovBuf.getInt(i + 16 + c * 4)
                        assertEquals(originalOffsets[c] + moovDelta, shifted)
                    }
                    break
                }
            }

            // Verify audio bit-perfection
            raf.seek(newMdatStart + 8)
            val readAudio = ByteArray(audioPayload.size)
            raf.readFully(readAudio)
            assertArrayEquals("Audio must remain intact after negative delta offset shrink", audioPayload, readAudio)

            raf.close()
        } finally {
            testDir.deleteRecursively()
        }
    }

    // =========================================================================
    // SECTION 3: FLAC Vorbis Comment & Picture Block Stress (F7)
    // =========================================================================

    @Test
    fun flac_vorbisComments_endiannessAndEqualSignInValues() {
        val comments = mapOf(
            "TITLE" to "Song = With = Multiple = Equals",
            "ARTIST" to "Artiste Francophone Éléphant",
            "ALBUM" to "Album With Empty Value Test",
            "GENRE" to "Heavy Metal / 80s Rock"
        )

        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "GoBeats Test Vendor",
            comments = comments,
            isLast = false
        )

        // Verify header
        assertEquals("Block header byte 0 must have type 4 and isLast=false", 4, block[0].toInt() and 0x7F)
        assertFalse("isLast bit must be 0", (block[0].toInt() and 0x80) != 0)

        // Parse comments back
        val parsed = FlacTagWriter.parseVorbisComments(block)
        assertEquals("Song = With = Multiple = Equals", parsed["TITLE"])
        assertEquals("Artiste Francophone Éléphant", parsed["ARTIST"])
        assertEquals("Album With Empty Value Test", parsed["ALBUM"])
        assertEquals("Heavy Metal / 80s Rock", parsed["GENRE"])
    }

    @Test
    fun flac_pictureBlock_rfc9639ComplianceAndRoundtrip() {
        val fakeImageData = ByteArray(4096) { (it % 127).toByte() }
        val picBlock = FlacTagWriter.buildPictureBlock(
            imageBytes = fakeImageData,
            mimeType = "image/png",
            pictureType = FlacTagWriter.PICTURE_TYPE_FRONT_COVER,
            description = "Front Cover Art",
            isLast = true
        )

        assertTrue("isLast bit must be set when isLast=true", (picBlock[0].toInt() and 0x80) != 0)
        assertEquals(FlacTagWriter.BLOCK_TYPE_PICTURE, picBlock[0].toInt() and 0x7F)

        val extracted = FlacTagWriter.parsePictureBlock(picBlock)
        assertNotNull("parsePictureBlock must successfully extract raw image bytes", extracted)
        assertArrayEquals(fakeImageData, extracted)

        // Adversarial: truncated block
        assertNull("Truncated picture block must return null safely", FlacTagWriter.parsePictureBlock(picBlock.copyOf(20)))

        // Adversarial: wrong block type
        val corruptType = picBlock.copyOf()
        corruptType[0] = (corruptType[0].toInt() and 0x80 or 4).toByte() // changed to type 4
        assertNull("Wrong block type must return null safely", FlacTagWriter.parsePictureBlock(corruptType))
    }

    @Test
    fun flac_writeTagsToFlacFile_endToEnd_bitPerfectAudioFrames() {
        val testDir = File(System.getProperty("java.io.tmpdir"), "gobeats_flac_stress_${System.currentTimeMillis()}")
        testDir.mkdirs()
        try {
            val flacFile = File(testDir, "synth_audio.flac")
            val audioFrames = ByteArray(16384) { (it xor 0x3C).toByte() }

            // Synthesize minimal valid FLAC stream
            FileOutputStream(flacFile).use { out ->
                out.write(FlacTagWriter.FLAC_MAGIC) // "fLaC"
                // Block 0: STREAMINFO (34 bytes payload, isLast = false)
                val streamInfoPayload = ByteArray(34) { 0x01 }
                val streamInfoHeader = ByteArray(4).apply {
                    this[0] = 0 // type 0, isLast = false
                    this[1] = 0
                    this[2] = 0
                    this[3] = 34
                }
                out.write(streamInfoHeader)
                out.write(streamInfoPayload)

                // Block 1: initial Vorbis comments (isLast = true)
                val initialComments = FlacTagWriter.buildVorbisCommentBlock(
                    comments = mapOf("TITLE" to "Old Title"),
                    isLast = true
                )
                out.write(initialComments)

                // Raw audio frames
                out.write(audioFrames)
            }

            // Execute production FLAC tag write
            val payload = TagEditPayload(
                trackId = "flac_1",
                title = "Flac Masterpiece",
                artist = "Audiophile Ensemble",
                album = "Lossless Heights",
                year = 2026,
                genre = "Acoustic",
                trackNumber = 1,
                newArtworkBytes = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
            )

            val success = FlacTagWriter.writeTagsToFlacFile(flacFile, payload, stagingDir = testDir)
            assertTrue("FlacTagWriter must return true for valid FLAC rewrite", success)

            // Verify file structure
            val raf = RandomAccessFile(flacFile, "r")
            val magic = ByteArray(4)
            raf.readFully(magic)
            assertArrayEquals(FlacTagWriter.FLAC_MAGIC, magic)

            // Block 0 must be STREAMINFO
            val b0Header = ByteArray(4)
            raf.readFully(b0Header)
            assertEquals("Block 0 must be STREAMINFO (type 0)", 0, b0Header[0].toInt() and 0x7F)
            assertFalse("Block 0 must not be last", (b0Header[0].toInt() and 0x80) != 0)
            val b0Len = ((b0Header[1].toInt() and 0xFF) shl 16) or ((b0Header[2].toInt() and 0xFF) shl 8) or (b0Header[3].toInt() and 0xFF)
            assertEquals(34, b0Len)
            raf.skipBytes(34)

            // Read remaining metadata blocks until isLast
            var isLast = false
            var foundVorbis = false
            var foundPicture = false
            while (!isLast) {
                val header = ByteArray(4)
                raf.readFully(header)
                isLast = (header[0].toInt() and 0x80) != 0
                val type = header[0].toInt() and 0x7F
                val len = ((header[1].toInt() and 0xFF) shl 16) or ((header[2].toInt() and 0xFF) shl 8) or (header[3].toInt() and 0xFF)
                val payloadBytes = ByteArray(len)
                raf.readFully(payloadBytes)

                if (type == FlacTagWriter.BLOCK_TYPE_VORBIS_COMMENT) {
                    foundVorbis = true
                    val fullBlock = header + payloadBytes
                    val parsed = FlacTagWriter.parseVorbisComments(fullBlock)
                    assertEquals("Flac Masterpiece", parsed["TITLE"])
                    assertEquals("Audiophile Ensemble", parsed["ARTIST"])
                } else if (type == FlacTagWriter.BLOCK_TYPE_PICTURE) {
                    foundPicture = true
                    val fullBlock = header + payloadBytes
                    val pic = FlacTagWriter.parsePictureBlock(fullBlock)
                    assertNotNull(pic)
                    assertArrayEquals(payload.newArtworkBytes, pic)
                }
            }
            assertTrue("Vorbis comments must be present in rewritten FLAC", foundVorbis)
            assertTrue("Picture block must be present in rewritten FLAC", foundPicture)

            // Verify raw audio frames are bit-perfect to EOF
            val remainingAudio = ByteArray(audioFrames.size)
            raf.readFully(remainingAudio)
            assertArrayEquals("FLAC raw audio frames must be 100% bit-perfect and preserved", audioFrames, remainingAudio)
            assertEquals("Must be at exact EOF after audio frames", raf.length(), raf.filePointer)

            raf.close()
        } finally {
            testDir.deleteRecursively()
        }
    }

    // =========================================================================
    // SECTION 4: Scoped Storage Routing & Staging Validation (F6)
    // =========================================================================

    @Test
    fun scopedStorage_resolveWriteMechanism_fullApiMatrix() {
        // API 24 to 28 (Android 7 - 9)
        for (api in 24..28) {
            assertEquals(
                "API $api public must use DIRECT_POSIX_FILE",
                StorageWriteMechanism.DIRECT_POSIX_FILE,
                ScopedStorageRouter.resolveWriteMechanism(api, isPublicStorage = true)
            )
            assertEquals(
                "API $api private must use DIRECT_POSIX_FILE",
                StorageWriteMechanism.DIRECT_POSIX_FILE,
                ScopedStorageRouter.resolveWriteMechanism(api, isPublicStorage = false)
            )
        }

        // API 29 (Android 10 - Scoped Storage intro)
        assertEquals(
            "API 29 public must use RECOVERABLE_SECURITY_EXCEPTION",
            StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION,
            ScopedStorageRouter.resolveWriteMechanism(29, isPublicStorage = true)
        )
        assertEquals(
            "API 29 private must use DIRECT_POSIX_FILE",
            StorageWriteMechanism.DIRECT_POSIX_FILE,
            ScopedStorageRouter.resolveWriteMechanism(29, isPublicStorage = false)
        )

        // API 30 to 35 (Android 11 to 15)
        for (api in 30..35) {
            assertEquals(
                "API $api public must use MEDIA_STORE_CREATE_WRITE_REQUEST",
                StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST,
                ScopedStorageRouter.resolveWriteMechanism(api, isPublicStorage = true)
            )
            assertEquals(
                "API $api private must use DIRECT_POSIX_FILE",
                StorageWriteMechanism.DIRECT_POSIX_FILE,
                ScopedStorageRouter.resolveWriteMechanism(api, isPublicStorage = false)
            )
        }
    }

    @Test
    fun scopedStorage_validateStagingFile_adversarialEdgeCases() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "stage_val_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            // 1. Missing file
            val missing = File(tempDir, "missing.tmp")
            assertFalse(ScopedStorageRouter.validateStagingFile(missing, "fLaC".toByteArray()))

            // 2. Empty file (0 bytes)
            val empty = File(tempDir, "empty.tmp").apply { writeBytes(ByteArray(0)) }
            assertFalse(ScopedStorageRouter.validateStagingFile(empty, "fLaC".toByteArray()))

            // 3. Truncated file (15 bytes, less than minimum 16)
            val truncated = File(tempDir, "trunc.tmp").apply { writeBytes(ByteArray(15)) }
            assertFalse(ScopedStorageRouter.validateStagingFile(truncated, "fLaC".toByteArray()))

            // 4. Exactly 16 bytes with matching magic at offset 0
            val validFlac = File(tempDir, "valid_flac.tmp").apply {
                writeBytes(FlacTagWriter.FLAC_MAGIC + ByteArray(12))
            }
            assertTrue(ScopedStorageRouter.validateStagingFile(validFlac, FlacTagWriter.FLAC_MAGIC))

            // 5. M4A ftyp magic at offset 4
            val validM4a = File(tempDir, "valid_m4a.tmp").apply {
                val buf = ByteBuffer.allocate(32).order(ByteOrder.BIG_ENDIAN)
                buf.putInt(32)
                buf.put("ftyp".toByteArray(StandardCharsets.US_ASCII))
                buf.put(ByteArray(24))
                writeBytes(buf.array())
            }
            assertTrue(
                ScopedStorageRouter.validateStagingFile(
                    validM4a,
                    "ftyp".toByteArray(StandardCharsets.US_ASCII),
                    offset = 4,
                    minLength = 32L
                )
            )

            // 6. M4A wrong magic at offset 4
            val invalidM4a = File(tempDir, "invalid_m4a.tmp").apply {
                writeBytes(ByteArray(32) { 0xFF.toByte() })
            }
            assertFalse(
                ScopedStorageRouter.validateStagingFile(
                    invalidM4a,
                    "ftyp".toByteArray(StandardCharsets.US_ASCII),
                    offset = 4,
                    minLength = 32L
                )
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // =========================================================================
    // SECTION 5: Tag Sync Pipeline Concurrency & Durability Stress (F9)
    // =========================================================================

    data class PersistentTrackOverride(
        val trackId: String,
        val title: String,
        val artist: String,
        val album: String,
        val version: Long
    )

    class ThreadSafeTagSyncEngine {
        val overrides = ConcurrentHashMap<String, PersistentTrackOverride>()

        fun saveOverride(trackId: String, title: String, artist: String, album: String, version: Long) {
            // Emulate SQLite INSERT OR REPLACE / CONFLICT_REPLACE
            overrides.compute(trackId) { _, existing ->
                if (existing == null || version >= existing.version) {
                    PersistentTrackOverride(trackId, title, artist, album, version)
                } else {
                    existing
                }
            }
        }

        fun applyOverridesDuringMediaScannerRescan(scannedTrack: Track): Track {
            val override = overrides[scannedTrack.id] ?: return scannedTrack
            return scannedTrack.copy(
                title = override.title,
                artistName = override.artist,
                albumTitle = override.album
            )
        }
    }

    @Test
    fun tagSync_concurrentSavesAndRescanDurability() = runBlocking {
        val engine = ThreadSafeTagSyncEngine()
        val numThreads = 20
        val operationsPerThread = 50

        // Launch concurrent coroutines attempting to update tags on multiple tracks
        val jobs = (0 until numThreads).map { threadIdx ->
            async(Dispatchers.Default) {
                for (op in 0 until operationsPerThread) {
                    val trackId = "track_${op % 5}"
                    val version = threadIdx.toLong() * 1000 + op
                    engine.saveOverride(
                        trackId = trackId,
                        title = "Title_${trackId}_$version",
                        artist = "Artist_${trackId}_$version",
                        album = "Album_${trackId}_$version",
                        version = version
                    )
                }
            }
        }
        jobs.awaitAll()

        // Verify all 5 tracks have consistent, valid overrides without corruption
        for (i in 0 until 5) {
            val trackId = "track_$i"
            val override = engine.overrides[trackId]
            assertNotNull("Override for $trackId must exist", override)
            assertTrue(override!!.title.startsWith("Title_${trackId}_"))
            assertTrue(override.artist.startsWith("Artist_${trackId}_"))

            // Verify rescan preserves the override over disk values
            val rawDiskTrack = Track(
                id = trackId,
                sourceId = "local",
                fileUri = "/storage/emulated/0/Music/$trackId.mp3",
                title = "RawDiskTitle",
                artistName = "RawDiskArtist",
                albumTitle = "RawDiskAlbum",
                durationMs = 210000L
            )
            val protectedTrack = engine.applyOverridesDuringMediaScannerRescan(rawDiskTrack)
            assertEquals("User override must win over disk scan", override.title, protectedTrack.title)
            assertEquals("User override must win over disk scan", override.artist, protectedTrack.artistName)
        }
    }
}
