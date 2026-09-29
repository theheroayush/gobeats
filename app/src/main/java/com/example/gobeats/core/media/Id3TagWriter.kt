package com.example.gobeats.core.media

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.security.MessageDigest

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

object Id3TagWriter {

    private val UTF_16: Charset = Charsets.UTF_16

    /**
     * Builds a valid ID3v2.3 tag byte array from the given metadata.
     */
    fun buildId3v23Tag(
        title: String,
        artist: String,
        album: String,
        year: Int? = null,
        genre: String? = null,
        trackNumber: Int? = null,
        artworkBytes: ByteArray? = null,
        artworkMimeType: String = "image/jpeg"
    ): ByteArray {
        val framesStream = ByteArrayOutputStream()

        // 1. TIT2 - Title
        if (title.isNotBlank()) {
            framesStream.write(buildTextFrame("TIT2", title.trim()))
        }

        // 2. TPE1 - Lead Artist
        if (artist.isNotBlank()) {
            framesStream.write(buildTextFrame("TPE1", artist.trim()))
        }

        // 3. TALB - Album Title
        if (album.isNotBlank()) {
            framesStream.write(buildTextFrame("TALB", album.trim()))
        }

        // 4. TYER - Release Year
        if (year != null && year > 0) {
            framesStream.write(buildTextFrame("TYER", year.toString()))
        }

        // 5. TCON - Genre
        if (!genre.isNullOrBlank()) {
            framesStream.write(buildTextFrame("TCON", genre.trim()))
        }

        // 6. TRCK - Track Number
        if (trackNumber != null && trackNumber > 0) {
            framesStream.write(buildTextFrame("TRCK", trackNumber.toString()))
        }

        // 7. APIC - Attached Picture (Front Cover)
        if (artworkBytes != null && artworkBytes.isNotEmpty()) {
            framesStream.write(buildApicFrame(artworkBytes, artworkMimeType))
        }

        val framesBytes = framesStream.toByteArray()
        val tagPayloadSize = framesBytes.size

        // Build 10-byte ID3v2.3 header
        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03 // Major version 2.3
        header[4] = 0x00 // Revision 0
        header[5] = 0x00 // Flags

        // Synchsafe size (4 bytes, each using only 7 bits)
        encodeSynchsafeInteger(tagPayloadSize, header, 6)

        val finalTagStream = ByteArrayOutputStream()
        finalTagStream.write(header)
        finalTagStream.write(framesBytes)
        return finalTagStream.toByteArray()
    }

    /**
     * Builds a standard ID3v2.3 text frame with UTF-16 encoding (with BOM) for full Unicode support.
     */
    fun buildTextFrame(frameId: String, text: String): ByteArray {
        val out = ByteArrayOutputStream()
        val textBytesWithBom = text.toByteArray(UTF_16) // Java UTF-16 automatically includes 0xFEFF BOM

        val framePayload = ByteArray(1 + textBytesWithBom.size)
        framePayload[0] = 0x01 // Encoding 0x01 = UTF-16 with BOM
        System.arraycopy(textBytesWithBom, 0, framePayload, 1, textBytesWithBom.size)

        // 4-byte Frame ID
        out.write(frameId.take(4).padEnd(4, ' ').toByteArray(Charsets.ISO_8859_1))

        // 4-byte Frame Size (standard 32-bit int in ID3v2.3)
        val size = framePayload.size
        out.write((size shr 24 and 0xFF).toByte().toInt())
        out.write((size shr 16 and 0xFF).toByte().toInt())
        out.write((size shr 8 and 0xFF).toByte().toInt())
        out.write((size and 0xFF).toByte().toInt())

        // 2-byte Frame Flags
        out.write(0x00)
        out.write(0x00)

        // Frame Payload
        out.write(framePayload)
        return out.toByteArray()
    }

    /**
     * Builds an ID3v2.3 APIC (Attached Picture) frame.
     */
    fun buildApicFrame(
        imageBytes: ByteArray,
        mimeType: String = "image/jpeg"
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val mimeBytes = mimeType.toByteArray(Charsets.ISO_8859_1)

        val framePayload = ByteArrayOutputStream()
        framePayload.write(0x00) // Encoding: 0x00 = ISO-8859-1 for mime/description
        framePayload.write(mimeBytes)
        framePayload.write(0x00) // Null terminator for MIME
        framePayload.write(0x03) // Picture Type: 0x03 = Front Cover
        framePayload.write(0x00) // Description: empty, null terminator
        framePayload.write(imageBytes)

        val payloadBytes = framePayload.toByteArray()
        val size = payloadBytes.size

        // 4-byte Frame ID
        out.write("APIC".toByteArray(Charsets.ISO_8859_1))

        // 4-byte Frame Size
        out.write((size shr 24 and 0xFF).toByte().toInt())
        out.write((size shr 16 and 0xFF).toByte().toInt())
        out.write((size shr 8 and 0xFF).toByte().toInt())
        out.write((size and 0xFF).toByte().toInt())

        // 2-byte Flags
        out.write(0x00)
        out.write(0x00)

        // Payload
        out.write(payloadBytes)
        return out.toByteArray()
    }

    /**
     * Safely updates an MP3 audio file with new ID3v2.3 tags, preserving all MPEG audio frames.
     */
    fun writeTagsToMp3File(
        file: File,
        newTagBytes: ByteArray
    ): Boolean {
        if (!file.exists() || !file.canWrite()) return false
        val tempFile = File(file.parentFile, file.name + ".tmp_${System.currentTimeMillis()}")

        return try {
            FileInputStream(file).use { input ->
                FileOutputStream(tempFile).use { output ->
                    // Write new ID3v2 tag at the very beginning
                    output.write(newTagBytes)

                    // Check if existing file has an ID3v2 tag
                    val header = ByteArray(10)
                    val read = input.read(header)
                    if (read == 10 && header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                        val existingTagSize = decodeSynchsafeInteger(header, 6)
                        // Skip the remainder of the existing ID3 tag
                        input.skip(existingTagSize.toLong())
                    } else if (read > 0) {
                        // Not an ID3 tag; write back the first 10 bytes of audio data
                        output.write(header, 0, read)
                    }

                    // Copy the audio frames
                    val buffer = ByteArray(32768)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }

            if (file.delete()) {
                tempFile.renameTo(file)
            } else {
                false
            }
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    /**
     * Updates MediaStore record attributes if the track points to a content://media/ URI.
     */
    fun updateMediaStoreTags(
        context: Context,
        contentUri: Uri,
        title: String,
        artist: String,
        album: String,
        year: Int?
    ): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
                if (year != null && year > 0) {
                    put(MediaStore.Audio.Media.YEAR, year)
                }
            }
            val updated = context.contentResolver.update(contentUri, values, null, null)
            updated > 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Saves raw cover art bytes into the app's persistent artwork directory and returns its file URI string.
     */
    fun saveArtworkToStorage(context: Context, imageBytes: ByteArray, trackId: String): String? {
        return try {
            val artDir = File(context.filesDir, "artworks").apply { mkdirs() }
            val hash = md5(trackId + System.currentTimeMillis().toString())
            val artFile = File(artDir, "custom_$hash.jpg")
            FileOutputStream(artFile).use { out ->
                out.write(imageBytes)
            }
            Uri.fromFile(artFile).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun encodeSynchsafeInteger(value: Int, buffer: ByteArray, offset: Int) {
        buffer[offset] = (value shr 21 and 0x7F).toByte()
        buffer[offset + 1] = (value shr 14 and 0x7F).toByte()
        buffer[offset + 2] = (value shr 7 and 0x7F).toByte()
        buffer[offset + 3] = (value and 0x7F).toByte()
    }

    fun decodeSynchsafeInteger(buffer: ByteArray, offset: Int): Int {
        return ((buffer[offset].toInt() and 0x7F) shl 21) or
                ((buffer[offset + 1].toInt() and 0x7F) shl 14) or
                ((buffer[offset + 2].toInt() and 0x7F) shl 7) or
                (buffer[offset + 3].toInt() and 0x7F)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
