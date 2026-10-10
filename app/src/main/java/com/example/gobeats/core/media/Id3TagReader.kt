package com.example.gobeats.core.media

import com.example.gobeats.core.lyrics.LyricLine
import com.example.gobeats.core.lyrics.LyricsData
import com.example.gobeats.core.lyrics.LrcParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * ID3v2 tag reader and frame parser.
 * Reads ID3v2.3 / ID3v2.4 tags and extracts USLT (unsynchronized lyrics)
 * and SYLT (synchronized lyrics) frames.
 */
object Id3TagReader {

    private val ID3_MAGIC = "ID3".toByteArray(StandardCharsets.US_ASCII)

    fun decodeSynchsafe(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0x7F) shl 21) or
                ((b1.toInt() and 0x7F) shl 14) or
                ((b2.toInt() and 0x7F) shl 7) or
                (b3.toInt() and 0x7F)
    }

    fun decodeInt32BE(b0: Byte, b1: Byte, b2: Byte, b3: Byte): Int {
        return ((b0.toInt() and 0xFF) shl 24) or
                ((b1.toInt() and 0xFF) shl 16) or
                ((b2.toInt() and 0xFF) shl 8) or
                (b3.toInt() and 0xFF)
    }

    /**
     * Parses a USLT (Unsynchronized lyrics/text transcription) frame payload.
     * Byte 0: Text encoding (0=ISO-8859-1, 1=UTF-16 with BOM, 2=UTF-16BE, 3=UTF-8)
     * Bytes 1-3: Language (ISO-639-2)
     * Followed by Content descriptor (terminated by null)
     * Followed by Lyric text (to the end of payload)
     */
    fun parseUsltFrame(framePayload: ByteArray): String {
        if (framePayload.size < 5) return ""

        val encodingByte = framePayload[0].toInt() and 0xFF
        val charset: Charset = when (encodingByte) {
            0x00 -> StandardCharsets.ISO_8859_1
            0x01 -> StandardCharsets.UTF_16
            0x02 -> StandardCharsets.UTF_16BE
            0x03 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }

        var offset = 4 // skip encoding (1 byte) and language (3 bytes)

        if (encodingByte == 0x01 || encodingByte == 0x02) {
            // 16-bit null terminator: 0x00, 0x00 aligned
            while (offset + 1 < framePayload.size) {
                if (framePayload[offset] == 0.toByte() && framePayload[offset + 1] == 0.toByte()) {
                    offset += 2
                    break
                }
                offset += 2
            }
        } else {
            // 8-bit null terminator: 0x00
            while (offset < framePayload.size && framePayload[offset] != 0.toByte()) {
                offset++
            }
            offset++ // skip null terminator
        }

        if (offset >= framePayload.size) return ""

        return try {
            val text = String(framePayload, offset, framePayload.size - offset, charset).trim()
            if (text.isEmpty() && charset != StandardCharsets.UTF_8) {
                String(framePayload, offset, framePayload.size - offset, StandardCharsets.UTF_8).trim()
            } else {
                text
            }
        } catch (_: Exception) {
            try {
                String(framePayload, offset, framePayload.size - offset, StandardCharsets.UTF_8).trim()
            } catch (_: Exception) {
                ""
            }
        }
    }

    /**
     * Parses a SYLT (Synchronized lyrics/text) frame payload.
     * Byte 0: Text encoding
     * Bytes 1-3: Language
     * Byte 4: Time stamp format (1 = ms, 2 = MPEG frames)
     * Byte 5: Content type (1 = lyrics)
     * Followed by Content descriptor (terminated by null)
     * Followed by synchronized entries: text token (null-terminated) + 4-byte timestamp
     */
    fun parseSyltFrame(framePayload: ByteArray): LyricsData? {
        if (framePayload.size < 6) return null

        val encodingByte = framePayload[0].toInt() and 0xFF
        val charset: Charset = when (encodingByte) {
            0x00 -> StandardCharsets.ISO_8859_1
            0x01 -> StandardCharsets.UTF_16
            0x02 -> StandardCharsets.UTF_16BE
            0x03 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }
        val is16Bit = encodingByte == 0x01 || encodingByte == 0x02

        var offset = 6 // skip encoding, language, timestamp format, content type

        // Skip descriptor
        if (is16Bit) {
            while (offset + 1 < framePayload.size) {
                if (framePayload[offset] == 0.toByte() && framePayload[offset + 1] == 0.toByte()) {
                    offset += 2
                    break
                }
                offset += 2
            }
        } else {
            while (offset < framePayload.size && framePayload[offset] != 0.toByte()) {
                offset++
            }
            offset++
        }

        val lines = mutableListOf<LyricLine>()
        val fullTextBuilder = StringBuilder()

        while (offset < framePayload.size) {
            val textStart = offset
            if (is16Bit) {
                while (offset + 1 < framePayload.size &&
                    !(framePayload[offset] == 0.toByte() && framePayload[offset + 1] == 0.toByte())
                ) {
                    offset += 2
                }
                val textLength = offset - textStart
                offset += 2 // skip 2-byte null
                if (offset + 4 > framePayload.size) break
                val timestampMs = ByteBuffer.wrap(framePayload, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
                offset += 4

                val token = try {
                    String(framePayload, textStart, textLength, charset).trim()
                } catch (_: Exception) { "" }

                if (token.isNotEmpty()) {
                    lines.add(LyricLine(timestampMs, token))
                    fullTextBuilder.append(token).append("\n")
                }
            } else {
                while (offset < framePayload.size && framePayload[offset] != 0.toByte()) {
                    offset++
                }
                val textLength = offset - textStart
                offset++ // skip 1-byte null
                if (offset + 4 > framePayload.size) break
                val timestampMs = ByteBuffer.wrap(framePayload, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong()
                offset += 4

                val token = try {
                    String(framePayload, textStart, textLength, charset).trim()
                } catch (_: Exception) { "" }

                if (token.isNotEmpty()) {
                    lines.add(LyricLine(timestampMs, token))
                    fullTextBuilder.append(token).append("\n")
                }
            }
        }

        if (lines.isEmpty()) return null
        return LyricsData(
            isSynced = true,
            lines = lines.sortedBy { it.timeMs },
            rawText = fullTextBuilder.toString().trim(),
            source = "embedded"
        )
    }

    /**
     * Extracts lyrics from an MP3 stream by reading its ID3v2 tag.
     * Prefers SYLT (synchronized lyrics) over USLT (unsynchronized lyrics).
     */
    fun extractLyricsFromMp3(inputStream: InputStream): LyricsData? {
        val header = ByteArray(10)
        val read = inputStream.read(header)
        if (read < 10) return null

        if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
            return null
        }

        val majorVersion = header[3].toInt() and 0xFF
        if (majorVersion != 3 && majorVersion != 4) return null

        val tagSize = decodeSynchsafe(header[6], header[7], header[8], header[9])
        if (tagSize <= 0 || tagSize > 10 * 1024 * 1024) return null // Max 10MB tag safety

        val tagPayload = ByteArray(tagSize)
        var totalRead = 0
        while (totalRead < tagSize) {
            val r = inputStream.read(tagPayload, totalRead, tagSize - totalRead)
            if (r == -1) break
            totalRead += r
        }

        var offset = 0
        var syltLyrics: LyricsData? = null
        var usltLyrics: String? = null

        while (offset + 10 <= totalRead) {
            val frameId = String(tagPayload, offset, 4, StandardCharsets.US_ASCII)
            if (frameId.all { it == '\u0000' } || !frameId.all { it in 'A'..'Z' || it in '0'..'9' }) {
                break
            }

            val frameSize = if (majorVersion == 4) {
                decodeSynchsafe(tagPayload[offset + 4], tagPayload[offset + 5], tagPayload[offset + 6], tagPayload[offset + 7])
            } else {
                decodeInt32BE(tagPayload[offset + 4], tagPayload[offset + 5], tagPayload[offset + 6], tagPayload[offset + 7])
            }

            if (frameSize <= 0 || offset + 10 + frameSize > totalRead) break

            val payloadStart = offset + 10
            val frameBytes = tagPayload.copyOfRange(payloadStart, payloadStart + frameSize)

            if (frameId == "SYLT") {
                val parsed = parseSyltFrame(frameBytes)
                if (parsed != null && parsed.lines.isNotEmpty()) {
                    syltLyrics = parsed
                }
            } else if (frameId == "USLT") {
                val decoded = parseUsltFrame(frameBytes)
                if (decoded.isNotBlank()) {
                    usltLyrics = decoded
                }
            }

            offset += 10 + frameSize
        }

        // Return SYLT if available
        if (syltLyrics != null) return syltLyrics

        // Fallback to USLT parsed through LrcParser (in case it contains LRC timestamps or plain text)
        if (!usltLyrics.isNullOrBlank()) {
            return LrcParser.parse(usltLyrics, source = "embedded")
        }

        return null
    }
}
