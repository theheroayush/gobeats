package com.example.gobeats

import com.example.gobeats.core.media.FlacTagWriter
import com.example.gobeats.core.media.TagEditPayload
import com.example.gobeats.core.media.tag.ScopedStoragePermissionRequiredException
import com.example.gobeats.core.media.tag.ScopedStorageRouter
import com.example.gobeats.core.media.tag.StorageWriteMechanism
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Random

/**
 * Empirical Stress Test Suite for Milestone 2:
 * Directly tests production implementations:
 * - com.example.gobeats.core.media.FlacTagWriter
 * - com.example.gobeats.core.media.tag.ScopedStorageRouter
 * - com.example.gobeats.core.media.tag.ScopedStoragePermissionRequiredException
 *
 * Covers:
 * 1. Vorbis comments with large payloads (64KB, 1MB, 1,000 distinct tags)
 * 2. Full Unicode resilience (Devanagari, CJK, Arabic, Cyrillic, Umlauts, multi-byte emojis)
 * 3. Bit-perfect raw audio frame preservation across metadata rewrites
 * 4. FLAC stream edge cases, corrupt payloads, and RFC 9639 picture blocks
 * 5. ScopedStorageRouter write mechanism resolution across Android API 21-40
 * 6. Staging file validation oracles (magic at offset 0 and offset 4)
 */
class FlacVorbisScopedStorageStressTest {

    // Helper to generate synthetic FLAC stream with STREAMINFO + custom blocks + audio frames
    private fun createSyntheticFlacStream(
        includeComments: Boolean = true,
        extraBlocks: List<FlacTagWriter.RawMetadataBlock> = emptyList(),
        audioFrameBytes: ByteArray = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
    ): ByteArray {
        val out = ByteArrayOutputStream()
        // 1. FLAC magic
        out.write(FlacTagWriter.FLAC_MAGIC)

        // 2. STREAMINFO block (Type 0, length 34)
        val streamInfoPayload = ByteArray(34) { (it * 7).toByte() }
        val streamInfoHeader = ByteArray(4)
        val isStreamInfoLast = !includeComments && extraBlocks.isEmpty()
        streamInfoHeader[0] = (if (isStreamInfoLast) 0x80 else 0x00).toByte() // type 0
        streamInfoHeader[1] = 0
        streamInfoHeader[2] = 0
        streamInfoHeader[3] = 34
        out.write(streamInfoHeader)
        out.write(streamInfoPayload)

        // 3. Extra auxiliary blocks (e.g. SEEKTABLE, PADDING)
        extraBlocks.forEachIndexed { idx, block ->
            val isLast = !includeComments && idx == extraBlocks.size - 1
            val header = ByteArray(4)
            header[0] = ((if (isLast) 0x80 else 0x00) or (block.type and 0x7F)).toByte()
            header[1] = ((block.payload.size shr 16) and 0xFF).toByte()
            header[2] = ((block.payload.size shr 8) and 0xFF).toByte()
            header[3] = (block.payload.size and 0xFF).toByte()
            out.write(header)
            out.write(block.payload)
        }

        // 4. Existing Vorbis comment block if requested
        if (includeComments) {
            val vorbisBlock = FlacTagWriter.buildVorbisCommentBlock(
                vendorString = "Synthetic Vendor",
                comments = mapOf("TITLE" to "Old Song", "ARTIST" to "Old Artist"),
                isLast = true
            )
            out.write(vorbisBlock)
        }

        // 5. Audio frames
        out.write(audioFrameBytes)
        return out.toByteArray()
    }

    private fun sha256(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    // =========================================================================
    // SECTION 1: FLAC Magic & Vorbis Comment Payload Stress Tests
    // =========================================================================

    @Test
    fun flac_01_flacMagicHeader_exactFourBytesMatchSpec() {
        assertArrayEquals(byteArrayOf(0x66, 0x4C, 0x61, 0x43), FlacTagWriter.FLAC_MAGIC)
    }

    @Test
    fun flac_02_largeLyricsPayload_1MB_roundtripsAccurately() {
        // Generate 1 MB string payload
        val charPool = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 \n"
        val sb = StringBuilder(1_000_000)
        for (i in 0 until 1_000_000) {
            sb.append(charPool[i % charPool.length])
        }
        val hugeLyrics = sb.toString()

        val comments = mapOf(
            "TITLE" to "Grand Odyssey",
            "UNSYNCEDLYRICS" to hugeLyrics
        )

        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "GoBeats Stress Harness",
            comments = comments,
            isLast = true
        )

        // Verify FLAC block header: bit 7 = 1 (isLast), type = 4
        assertEquals(0x84.toByte(), block[0])

        // Verify parsing retrieves the exact 1MB payload
        val parsed = FlacTagWriter.parseVorbisComments(block)
        assertEquals("Grand Odyssey", parsed["TITLE"])
        assertEquals(hugeLyrics, parsed["UNSYNCEDLYRICS"])
        assertEquals(1_000_000, parsed["UNSYNCEDLYRICS"]?.length)
    }

    @Test
    fun flac_03_thousandsOfDistinctCommentTags_stressTest() {
        val tagCount = 1_000
        val tagMap = (0 until tagCount).associate { idx ->
            "CUSTOM_TAG_$idx" to "VALUE_PAYLOAD_FOR_INDEX_$idx"
        }

        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "GoBeats 1000 Tags Vendor",
            comments = tagMap,
            isLast = false
        )

        assertEquals(0x04.toByte(), block[0]) // isLast = false, type = 4

        val parsed = FlacTagWriter.parseVorbisComments(block)
        assertEquals(tagCount, parsed.size)
        for (i in 0 until tagCount) {
            assertEquals("VALUE_PAYLOAD_FOR_INDEX_$i", parsed["CUSTOM_TAG_$i"])
        }
    }

    // =========================================================================
    // SECTION 2: Unicode & Character Encoding Resilience
    // =========================================================================

    @Test
    fun flac_04_multilingualScripts_encodedAndParsedLosslessly() {
        val complexTags = mapOf(
            "TITLE_JA" to "東京事变 - 群青日和",
            "TITLE_HI" to "कबीर के दोहे - मोको कहाँ ढूंढे बन्दे",
            "TITLE_AR" to "فيروز - كيفك انت",
            "TITLE_RU" to "Пётр Ильич Чайковский - Лебединое озеро",
            "TITLE_DE" to "Mötley Crüe - Über den Wolken & Äpfel",
            "TITLE_ZH" to "周杰伦 - 青花瓷",
            "TITLE_EL" to "Μίκης Θεοδωράκης - Ζορμπάς"
        )

        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "Universal UTF-8 Vendor テスト",
            comments = complexTags,
            isLast = true
        )

        val parsed = FlacTagWriter.parseVorbisComments(block)
        complexTags.forEach { (key, value) ->
            assertEquals("Mismatch for $key", value, parsed[key])
        }
    }

    @Test
    fun flac_05_multiByteEmojisAndSpecialSymbols() {
        val emojiTags = mapOf(
            "EMOJI_TITLE" to "🔥 Cosmic Drift 🚀 🎵 🎧 🎼 🖤 🤘",
            "MATHEMATICAL" to "∀x ∈ ℝ : x² ≥ 0 ∧ ∑(1/n²) = π²/6",
            "CURRENCY" to "$100 €200 £300 ¥400 ₹500 ₿1.5"
        )

        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "GoBeats 🚀 Vendor",
            comments = emojiTags,
            isLast = true
        )

        val parsed = FlacTagWriter.parseVorbisComments(block)
        emojiTags.forEach { (key, value) ->
            assertEquals("Mismatch for $key", value, parsed[key])
        }
    }

    @Test
    fun flac_06_specialCharactersInValues_newlinesTabsQuotesMultipleEquals() {
        val edgeTags = mapOf(
            "URL_WITH_EQUALS" to "https://example.com/stream?codec=flac&bitrate=1411&id=99&sig=a==b==",
            "MULTI_LINE" to "Line 1\nLine 2\r\nLine 3\tTabbed",
            "QUOTED" to "\"Double quoted\" and 'Single quoted' and `Backticked`",
            "BACKSLASHES" to "C:\\Music\\FLAC\\Artist\\Album\\01.flac",
            "EMPTY_VAL" to ""
        )

        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "GoBeats Special Characters",
            comments = edgeTags,
            isLast = true
        )

        val parsed = FlacTagWriter.parseVorbisComments(block)
        edgeTags.forEach { (key, value) ->
            assertEquals("Mismatch for $key", value, parsed[key])
        }
    }

    @Test
    fun flac_07_emptyCommentsMap_producesValidVorbisBlock() {
        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = "Vendor",
            comments = emptyMap(),
            isLast = true
        )
        val parsed = FlacTagWriter.parseVorbisComments(block)
        assertTrue(parsed.isEmpty())
    }

    @Test
    fun flac_08_endiannessDualityValidation() {
        // Block header size is 24-bit Big-Endian
        // Vorbis vendor string and comment lengths are 32-bit Little-Endian
        val vendor = "AB" // 2 bytes
        val comments = mapOf("K" to "V") // "K=V" = 3 bytes
        val block = FlacTagWriter.buildVorbisCommentBlock(
            vendorString = vendor,
            comments = comments,
            isLast = false
        )

        // Block header is 4 bytes
        // Payload:
        // vendorLen (4 bytes LE): 0x02, 0x00, 0x00, 0x00
        // vendorBytes (2 bytes): 'A', 'B'
        // count (4 bytes LE): 0x01, 0x00, 0x00, 0x00
        // commentLen (4 bytes LE): 0x03, 0x00, 0x00, 0x00
        // commentBytes (3 bytes): 'K', '=', 'V'
        // Total payload size = 4 + 2 + 4 + 4 + 3 = 17 bytes
        val payloadSize = 17
        assertEquals(0x04.toByte(), block[0])
        assertEquals(((payloadSize shr 16) and 0xFF).toByte(), block[1])
        assertEquals(((payloadSize shr 8) and 0xFF).toByte(), block[2])
        assertEquals((payloadSize and 0xFF).toByte(), block[3])

        // Verify Little-Endian vendor length at offset 4..7
        assertEquals(2.toByte(), block[4])
        assertEquals(0.toByte(), block[5])
        assertEquals(0.toByte(), block[6])
        assertEquals(0.toByte(), block[7])
    }

    // =========================================================================
    // SECTION 3: Stream Integrity & Bit-Perfect Audio Verification
    // =========================================================================

    @Test
    fun flac_09_streamIntegrity_audioFramesPreservedBitPerfect() {
        // Construct 128KB of pseudo-random audio frames
        val random = Random(42)
        val originalAudioFrames = ByteArray(131072)
        random.nextBytes(originalAudioFrames)
        val originalAudioDigest = sha256(originalAudioFrames)

        val initialStream = createSyntheticFlacStream(
            includeComments = true,
            audioFrameBytes = originalAudioFrames
        )

        val updatedComments = mapOf(
            "TITLE" to "New Stream Title",
            "ARTIST" to "New Stream Artist",
            "ALBUM" to "New Stream Album",
            "TRACKNUMBER" to "7"
        )

        val inputStream = ByteArrayInputStream(initialStream)
        val outputStream = ByteArrayOutputStream()

        val success = FlacTagWriter.writeTagsToStreams(
            input = inputStream,
            output = outputStream,
            comments = updatedComments
        )
        assertTrue(success)

        val rewrittenStream = outputStream.toByteArray()

        // 1. Verify FLAC magic
        val magic = rewrittenStream.copyOfRange(0, 4)
        assertArrayEquals(FlacTagWriter.FLAC_MAGIC, magic)

        // 2. Read rewritten metadata blocks using FlacTagWriter.readMetadataBlocks
        val verifyInput = ByteArrayInputStream(rewrittenStream)
        val rewrittenBlocks = FlacTagWriter.readMetadataBlocks(verifyInput)

        // First block must be STREAMINFO
        assertEquals(FlacTagWriter.BLOCK_TYPE_STREAMINFO, rewrittenBlocks[0].type)
        assertFalse(rewrittenBlocks[0].isLast)

        // Second block must be Vorbis comments
        assertEquals(FlacTagWriter.BLOCK_TYPE_VORBIS_COMMENT, rewrittenBlocks[1].type)
        assertTrue(rewrittenBlocks[1].isLast)

        // Verify parsed comments
        val vorbisWithHeader = ByteArray(4 + rewrittenBlocks[1].payload.size)
        vorbisWithHeader[0] = 0x84.toByte()
        System.arraycopy(rewrittenBlocks[1].payload, 0, vorbisWithHeader, 4, rewrittenBlocks[1].payload.size)
        val parsedComments = FlacTagWriter.parseVorbisComments(vorbisWithHeader)
        assertEquals("New Stream Title", parsedComments["TITLE"])
        assertEquals("New Stream Artist", parsedComments["ARTIST"])
        assertEquals("New Stream Album", parsedComments["ALBUM"])
        assertEquals("7", parsedComments["TRACKNUMBER"])

        // 3. Verify audio frames following metadata are BIT-PERFECT
        val remainingAudioFrames = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var read: Int
        while (verifyInput.read(buf).also { read = it } != -1) {
            remainingAudioFrames.write(buf, 0, read)
        }
        val extractedAudio = remainingAudioFrames.toByteArray()
        val extractedDigest = sha256(extractedAudio)

        assertArrayEquals(
            "Audio frames were modified during FLAC metadata rewrite!",
            originalAudioDigest,
            extractedDigest
        )
    }

    @Test
    fun flac_10_preservesAuxiliaryMetadataBlocks_SEEKTABLE_PADDING_APPLICATION() {
        val seekTablePayload = ByteArray(72) { (it + 1).toByte() }
        val paddingPayload = ByteArray(1024) { 0 }
        val appPayload = ByteArray(16) { 0xAA.toByte() }

        val extraBlocks = listOf(
            FlacTagWriter.RawMetadataBlock(FlacTagWriter.BLOCK_TYPE_SEEKTABLE, isLast = false, payload = seekTablePayload),
            FlacTagWriter.RawMetadataBlock(FlacTagWriter.BLOCK_TYPE_PADDING, isLast = false, payload = paddingPayload),
            FlacTagWriter.RawMetadataBlock(FlacTagWriter.BLOCK_TYPE_APPLICATION, isLast = false, payload = appPayload)
        )

        val originalAudio = byteArrayOf(0x01, 0x02, 0x03, 0x04)
        val initialStream = createSyntheticFlacStream(
            includeComments = true,
            extraBlocks = extraBlocks,
            audioFrameBytes = originalAudio
        )

        val out = ByteArrayOutputStream()
        FlacTagWriter.writeTagsToStreams(
            input = ByteArrayInputStream(initialStream),
            output = out,
            comments = mapOf("TITLE" to "Preserved Auxiliary")
        )

        val resultBlocks = FlacTagWriter.readMetadataBlocks(ByteArrayInputStream(out.toByteArray()))

        // Should contain STREAMINFO (0), SEEKTABLE (3), PADDING (1), APPLICATION (2), and VORBIS_COMMENT (4)
        val types = resultBlocks.map { it.type }
        assertEquals(
            listOf(
                FlacTagWriter.BLOCK_TYPE_STREAMINFO,
                FlacTagWriter.BLOCK_TYPE_SEEKTABLE,
                FlacTagWriter.BLOCK_TYPE_PADDING,
                FlacTagWriter.BLOCK_TYPE_APPLICATION,
                FlacTagWriter.BLOCK_TYPE_VORBIS_COMMENT
            ),
            types
        )

        // Only the final block should have isLast = true
        for (i in 0 until resultBlocks.size - 1) {
            assertFalse("Block $i has isLast=true unexpectedly", resultBlocks[i].isLast)
        }
        assertTrue("Final block must have isLast=true", resultBlocks.last().isLast)

        // Check auxiliary payloads were not corrupted
        assertArrayEquals(seekTablePayload, resultBlocks[1].payload)
        assertArrayEquals(paddingPayload, resultBlocks[2].payload)
        assertArrayEquals(appPayload, resultBlocks[3].payload)
    }

    // =========================================================================
    // SECTION 4: RFC 9639 Picture Blocks & Artwork Manipulation
    // =========================================================================

    @Test
    fun flac_11_pictureBlock_rfc9639Structure_and_roundtrip() {
        val dummyJpegData = ByteArray(2048) { (it % 256).toByte() }
        val picBlock = FlacTagWriter.buildPictureBlock(
            imageBytes = dummyJpegData,
            mimeType = "image/jpeg",
            pictureType = FlacTagWriter.PICTURE_TYPE_FRONT_COVER,
            description = "Album Front Cover",
            isLast = false
        )

        // Verify block header: type 6 (METADATA_BLOCK_PICTURE)
        assertEquals(0x06.toByte(), picBlock[0])

        // Parse picture block
        val extractedImage = FlacTagWriter.parsePictureBlock(picBlock)
        assertNotNull(extractedImage)
        assertArrayEquals(dummyJpegData, extractedImage)
    }

    @Test
    fun flac_12_pictureBlock_pngMimeType_and_roundtrip() {
        val dummyPngData = ByteArray(1024) { 0x55.toByte() }
        val picBlock = FlacTagWriter.buildPictureBlock(
            imageBytes = dummyPngData,
            mimeType = "image/png",
            pictureType = FlacTagWriter.PICTURE_TYPE_FRONT_COVER,
            description = "PNG Cover",
            isLast = true
        )

        assertEquals(0x86.toByte(), picBlock[0]) // isLast = true, type 6

        val extractedImage = FlacTagWriter.parsePictureBlock(picBlock)
        assertNotNull(extractedImage)
        assertArrayEquals(dummyPngData, extractedImage)
    }

    @Test
    fun flac_13_parsePictureBlock_truncatedData_returnsNullGracefully() {
        val truncated = ByteArray(20) { 0 }
        truncated[0] = 0x06 // type 6
        assertNull(FlacTagWriter.parsePictureBlock(truncated))

        val nonPicture = ByteArray(40) { 0 }
        nonPicture[0] = 0x00 // type 0
        assertNull(FlacTagWriter.parsePictureBlock(nonPicture))
    }

    @Test
    fun flac_14_streamIntegrity_artworkReplacementAndRemoval() {
        val dummyJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val initialStream = createSyntheticFlacStream(
            includeComments = true,
            audioFrameBytes = byteArrayOf(0x01, 0x02)
        )

        // 1. Add artwork
        val withArtOut = ByteArrayOutputStream()
        FlacTagWriter.writeTagsToStreams(
            input = ByteArrayInputStream(initialStream),
            output = withArtOut,
            comments = mapOf("TITLE" to "With Art"),
            artworkBytes = dummyJpeg
        )
        val withArtBlocks = FlacTagWriter.readMetadataBlocks(ByteArrayInputStream(withArtOut.toByteArray()))
        assertTrue(withArtBlocks.any { it.type == FlacTagWriter.BLOCK_TYPE_PICTURE })

        // 2. Remove artwork
        val removeArtOut = ByteArrayOutputStream()
        FlacTagWriter.writeTagsToStreams(
            input = ByteArrayInputStream(withArtOut.toByteArray()),
            output = removeArtOut,
            comments = mapOf("TITLE" to "Art Removed"),
            artworkBytes = null,
            removeArtwork = true
        )
        val removedArtBlocks = FlacTagWriter.readMetadataBlocks(ByteArrayInputStream(removeArtOut.toByteArray()))
        assertFalse(removedArtBlocks.any { it.type == FlacTagWriter.BLOCK_TYPE_PICTURE })
    }

    // =========================================================================
    // SECTION 5: Corrupt Stream Handling & Oracle Edge Cases
    // =========================================================================

    @Test(expected = IllegalArgumentException::class)
    fun flac_15_corruptStream_invalidMagic_throwsException() {
        val badMagic = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), 0, 0, 0, 0)
        FlacTagWriter.readMetadataBlocks(ByteArrayInputStream(badMagic))
    }

    @Test(expected = IllegalArgumentException::class)
    fun flac_16_corruptStream_firstBlockNotStreamInfo_throwsException() {
        val out = ByteArrayOutputStream()
        out.write(FlacTagWriter.FLAC_MAGIC)
        // Add Vorbis comment as block 0 instead of STREAMINFO
        val vorbis = FlacTagWriter.buildVorbisCommentBlock("V", mapOf("A" to "B"), isLast = true)
        out.write(vorbis)
        out.write(byteArrayOf(1, 2, 3))

        FlacTagWriter.writeTagsToStreams(
            input = ByteArrayInputStream(out.toByteArray()),
            output = ByteArrayOutputStream(),
            comments = mapOf("T" to "V")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun flac_17_corruptStream_truncatedMetadataHeader_throwsException() {
        val out = ByteArrayOutputStream()
        out.write(FlacTagWriter.FLAC_MAGIC)
        out.write(byteArrayOf(0, 0)) // truncated 2 bytes header instead of 4
        FlacTagWriter.readMetadataBlocks(ByteArrayInputStream(out.toByteArray()))
    }

    @Test(expected = IllegalArgumentException::class)
    fun flac_18_corruptStream_truncatedMetadataPayload_throwsException() {
        val out = ByteArrayOutputStream()
        out.write(FlacTagWriter.FLAC_MAGIC)
        val header = byteArrayOf(0x00, 0x00, 0x00, 0x20) // says length is 32
        out.write(header)
        out.write(byteArrayOf(1, 2, 3)) // but only 3 bytes provided!
        FlacTagWriter.readMetadataBlocks(ByteArrayInputStream(out.toByteArray()))
    }

    // =========================================================================
    // SECTION 6: File-Level Atomic Staging & Fallback Behavior
    // =========================================================================

    @Test
    fun flac_19_writeTagsToFlacFile_fullFileRoundtrip() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gobeats_flac_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        val flacFile = File(tempDir, "test_track.flac")

        try {
            val originalAudio = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte())
            flacFile.writeBytes(createSyntheticFlacStream(includeComments = false, audioFrameBytes = originalAudio))

            val payload = TagEditPayload(
                trackId = "track_flac_1",
                title = "Empirical Test Title",
                artist = "Empirical Challenger",
                album = "Stress Album",
                year = 2026,
                genre = "Lossless",
                trackNumber = 3
            )

            val written = FlacTagWriter.writeTagsToFlacFile(
                file = flacFile,
                payload = payload,
                vendorString = "GoBeats Full Test",
                stagingDir = tempDir
            )
            assertTrue("writeTagsToFlacFile failed", written)

            // Re-read file and verify comments
            val blocks = FlacTagWriter.readMetadataBlocks(flacFile.inputStream())
            val vorbisBlock = blocks.find { it.type == FlacTagWriter.BLOCK_TYPE_VORBIS_COMMENT }
            assertNotNull("VORBIS_COMMENT block missing after rewrite", vorbisBlock)

            val vorbisWithHeader = ByteArray(4 + vorbisBlock!!.payload.size)
            vorbisWithHeader[0] = 0x84.toByte()
            System.arraycopy(vorbisBlock.payload, 0, vorbisWithHeader, 4, vorbisBlock.payload.size)
            val comments = FlacTagWriter.parseVorbisComments(vorbisWithHeader)

            assertEquals("Empirical Test Title", comments["TITLE"])
            assertEquals("Empirical Challenger", comments["ARTIST"])
            assertEquals("Stress Album", comments["ALBUM"])
            assertEquals("2026", comments["DATE"])
            assertEquals("Lossless", comments["GENRE"])
            assertEquals("3", comments["TRACKNUMBER"])
        } finally {
            flacFile.delete()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun flac_20_writeTagsToFlacFile_nonExistentFile_returnsFalse() {
        val nonExistent = File("non_existent_flac_${System.currentTimeMillis()}.flac")
        val payload = TagEditPayload("t", "T", "A", "Alb")
        val result = FlacTagWriter.writeTagsToFlacFile(nonExistent, payload)
        assertFalse(result)
    }

    // =========================================================================
    // SECTION 7: ScopedStorageRouter Stress & Matrix Tests
    // =========================================================================

    @Test
    fun scoped_01_resolveWriteMechanism_fullApiMatrix() {
        // Legacy Android (< 29): Always DIRECT_POSIX_FILE
        for (api in listOf(21, 23, 24, 26, 27, 28)) {
            assertEquals("API $api public", StorageWriteMechanism.DIRECT_POSIX_FILE,
                ScopedStorageRouter.resolveWriteMechanism(apiLevel = api, isPublicStorage = true))
            assertEquals("API $api private", StorageWriteMechanism.DIRECT_POSIX_FILE,
                ScopedStorageRouter.resolveWriteMechanism(apiLevel = api, isPublicStorage = false))
        }

        // Android 10 (API 29): RECOVERABLE_SECURITY_EXCEPTION for public, DIRECT_POSIX_FILE for private
        assertEquals(StorageWriteMechanism.RECOVERABLE_SECURITY_EXCEPTION,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 29, isPublicStorage = true))
        assertEquals(StorageWriteMechanism.DIRECT_POSIX_FILE,
            ScopedStorageRouter.resolveWriteMechanism(apiLevel = 29, isPublicStorage = false))

        // Android 11 to 15+ (API 30-40): MEDIA_STORE_CREATE_WRITE_REQUEST for public, DIRECT_POSIX_FILE for private
        for (api in listOf(30, 31, 32, 33, 34, 35, 36, 40)) {
            assertEquals("API $api public", StorageWriteMechanism.MEDIA_STORE_CREATE_WRITE_REQUEST,
                ScopedStorageRouter.resolveWriteMechanism(apiLevel = api, isPublicStorage = true))
            assertEquals("API $api private", StorageWriteMechanism.DIRECT_POSIX_FILE,
                ScopedStorageRouter.resolveWriteMechanism(apiLevel = api, isPublicStorage = false))
        }
    }

    @Test
    fun scoped_02_validateStagingFile_standardMagic() {
        val temp = File.createTempFile("stage_magic_test", ".tmp")
        try {
            // Valid FLAC file >= 16 bytes
            temp.writeBytes("fLaC_padding_data_123456789".toByteArray())
            assertTrue(ScopedStorageRouter.validateStagingFile(temp, "fLaC".toByteArray()))

            // Wrong magic
            assertFalse(ScopedStorageRouter.validateStagingFile(temp, "RIFF".toByteArray()))

            // File < 16 bytes
            temp.writeBytes("fLaC_short".toByteArray()) // 10 bytes
            assertFalse(ScopedStorageRouter.validateStagingFile(temp, "fLaC".toByteArray()))

            // Non-existent file
            val missing = File(temp.parentFile, "missing_${System.currentTimeMillis()}.tmp")
            assertFalse(ScopedStorageRouter.validateStagingFile(missing, "fLaC".toByteArray()))
        } finally {
            temp.delete()
        }
    }

    @Test
    fun scoped_03_validateStagingFile_withOffsetAndMinLength() {
        val temp = File.createTempFile("stage_offset_test", ".tmp")
        try {
            // M4A standard format: 4 bytes size, 4 bytes "ftyp", rest data (>= 32 bytes)
            val m4aHeader = ByteArray(36)
            m4aHeader[0] = 0; m4aHeader[1] = 0; m4aHeader[2] = 0; m4aHeader[3] = 36 // size = 36
            m4aHeader[4] = 'f'.code.toByte()
            m4aHeader[5] = 't'.code.toByte()
            m4aHeader[6] = 'y'.code.toByte()
            m4aHeader[7] = 'p'.code.toByte()
            temp.writeBytes(m4aHeader)

            // Validate with offset = 4, minLength = 32
            assertTrue(ScopedStorageRouter.validateStagingFile(
                stagingFile = temp,
                expectedMagic = "ftyp".toByteArray(),
                offset = 4,
                minLength = 32L
            ))

            // Wrong offset
            assertFalse(ScopedStorageRouter.validateStagingFile(
                stagingFile = temp,
                expectedMagic = "ftyp".toByteArray(),
                offset = 0,
                minLength = 32L
            ))

            // minLength greater than file
            assertFalse(ScopedStorageRouter.validateStagingFile(
                stagingFile = temp,
                expectedMagic = "ftyp".toByteArray(),
                offset = 4,
                minLength = 100L
            ))
        } finally {
            temp.delete()
        }
    }
}
