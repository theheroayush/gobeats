package com.example.gobeats.core.media

import com.example.gobeats.core.media.tag.ScopedStorageRouter
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
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as RawMetadataBlock
            if (type != other.type) return false
            if (isLast != other.isLast) return false
            if (!payload.contentEquals(other.payload)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = type
            result = 31 * result + isLast.hashCode()
            result = 31 * result + payload.contentHashCode()
            return result
        }
    }

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
        require(blockType == BLOCK_TYPE_VORBIS_COMMENT) { "Not a Vorbis comment block" }

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
        if (mimeLen < 0 || mimeLen > buf.remaining()) return null
        buf.position(buf.position() + mimeLen)

        val descLen = buf.int
        if (descLen < 0 || descLen > buf.remaining()) return null
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
        val blocksToWrite = mutableListOf<RawMetadataBlock>()
        blocksToWrite.add(existingBlocks[0].copy(isLast = false)) // STREAMINFO

        for (i in 1 until existingBlocks.size) {
            val b = existingBlocks[i]
            if (b.type != BLOCK_TYPE_VORBIS_COMMENT && b.type != BLOCK_TYPE_PICTURE) {
                blocksToWrite.add(b.copy(isLast = false))
            } else if (b.type == BLOCK_TYPE_PICTURE && artworkBytes == null && !removeArtwork) {
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

    /**
     * Safely updates tags and cover art on a FLAC file, writing via a temporary staging file
     * and copying raw audio frames verbatim to ensure bit-perfect audio preservation.
     */
    fun writeTagsToFlacFile(
        file: File,
        payload: TagEditPayload,
        vendorString: String = DEFAULT_VENDOR_STRING,
        stagingDir: File? = null,
        removeArtwork: Boolean = payload.removeArtwork
    ): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val directory = stagingDir ?: file.parentFile ?: return false
        val tempFile = File(directory, "stage_flac_${System.currentTimeMillis()}_${file.name}.tmp")

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
                        removeArtwork = removeArtwork,
                        vendorString = vendorString
                    )
                }
            }

            if (!success) {
                tempFile.delete()
                return false
            }

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
    }
}
