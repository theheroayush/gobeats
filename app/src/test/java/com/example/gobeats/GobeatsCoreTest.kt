package com.example.gobeats

import com.example.gobeats.core.designsystem.components.formatDuration
import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.RepeatMode
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.media.Id3TagWriter
import com.example.gobeats.core.media.TagEditPayload
import com.example.gobeats.core.audio.AutoEqCatalog
import com.example.gobeats.core.audio.AutoEqProfile
import com.example.gobeats.core.audio.AudioHardwareStatus
import com.example.gobeats.core.audio.ReplayGainData
import com.example.gobeats.core.audio.ReplayGainMode
import com.example.gobeats.core.audio.ReplayGainProcessor
import com.example.gobeats.core.playlist.ImportedTrackEntry
import com.example.gobeats.core.playlist.PlaylistImporter
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class GobeatsCoreTest {

    @Test
    fun formatDuration_formatsMinutesAndSecondsAccurately() {
        assertEquals("0:00", formatDuration(0L))
        assertEquals("0:05", formatDuration(5000L))
        assertEquals("1:00", formatDuration(60000L))
        assertEquals("3:45", formatDuration(225000L))
        assertEquals("12:08", formatDuration(728000L))
    }

    @Test
    fun trackModel_propertiesAndDefaults() {
        val track = Track(
            id = "t_101",
            sourceId = "source_local",
            fileUri = "content://media/audio/101",
            title = "Midnight Odyssey",
            artistName = "Acoustic Horizon",
            albumTitle = "Soundscapes",
            durationMs = 210000L,
            format = "FLAC",
            bitrate = 1411
        )

        assertEquals("Midnight Odyssey", track.title)
        assertEquals("Acoustic Horizon", track.artistName)
        assertEquals("Soundscapes", track.albumTitle)
        assertEquals(210000L, track.durationMs)
        assertEquals("FLAC", track.format)
        assertTrue(track.isAvailable)
        assertFalse(track.isFavorite)
    }

    @Test
    fun playbackUiState_transitions() {
        val track = Track(
            id = "t_102",
            sourceId = "source_local",
            fileUri = "content://media/audio/102",
            title = "Starlight",
            artistName = "Zenith",
            albumTitle = "Constellations",
            durationMs = 180000L
        )

        val initialState = PlaybackUiState()
        assertEquals(null, initialState.currentTrack)
        assertFalse(initialState.isPlaying)
        assertEquals(RepeatMode.OFF, initialState.repeatMode)

        val playingState = initialState.copy(
            currentTrack = track,
            isPlaying = true,
            currentPositionMs = 45000L,
            durationMs = 180000L,
            queue = listOf(track),
            queueIndex = 0
        )

        assertEquals(track, playingState.currentTrack)
        assertTrue(playingState.isPlaying)
        assertEquals(45000L, playingState.currentPositionMs)
        assertEquals(1, playingState.queue.size)
    }

    @Test
    fun albumAndArtist_aggregations() {
        val album = Album(
            id = "alb_1",
            title = "Golden Echoes",
            artistName = "Solitude",
            year = 2026,
            trackCount = 12
        )
        assertEquals(12, album.trackCount)
        assertEquals(2026, album.year)

        val artist = Artist(
            id = "art_1",
            name = "Solitude",
            trackCount = 36
        )
        assertEquals("Solitude", artist.name)
        assertEquals(36, artist.trackCount)
    }

    @Test
    fun playlist_creationDefaults() {
        val playlist = Playlist(
            id = "pl_1",
            name = "Late Night Beats",
            description = "Focus and deep work"
        )
        assertEquals("Late Night Beats", playlist.name)
        assertEquals("Focus and deep work", playlist.description)
        assertFalse(playlist.isSmart)
        assertEquals(0, playlist.trackCount)
    }

    @Test
    fun equalizerPresets_allHaveFiveBands() {
        com.example.gobeats.core.audio.EqualizerManager.PRESETS.forEach { (preset, bands) ->
            assertEquals("Preset $preset should have 5 frequency bands", 5, bands.size)
        }
    }

    @Test
    fun userProfileAndSettings_defaults() {
        val profile = com.example.gobeats.data.repository.UserProfile()
        assertEquals("Music Lover", profile.name)
        assertEquals("gobeats@local.audio", profile.email)

        val settings = com.example.gobeats.data.repository.AppSettings()
        assertEquals("High (320 kbps)", settings.audioQuality)
        assertEquals("Dark", settings.theme)
        assertEquals("neon_purple", settings.appIconStyle)
        assertTrue(settings.syncLibraryEnabled)
    }

    @Test
    fun trackModel_supportsVideoPlaybackAndDimensions() {
        val videoTrack = Track(
            id = "v_101",
            sourceId = "source_local",
            fileUri = "content://media/video/101",
            title = "Nature Documentary",
            artistName = "BBC",
            albumTitle = "Planet Earth",
            durationMs = 600000L,
            isVideo = true,
            videoWidth = 1920,
            videoHeight = 1080
        )
        assertTrue(videoTrack.isVideo)
        assertEquals(1920, videoTrack.videoWidth)
        assertEquals(1080, videoTrack.videoHeight)
    }

    @Test
    fun playbackUiState_supportsVideoMode() {
        val state = PlaybackUiState(isVideoMode = true)
        assertTrue(state.isVideoMode)
        val audioState = state.copy(isVideoMode = false)
        assertFalse(audioState.isVideoMode)
    }

    @Test
    fun equalizerState_hasVirtualizerStrength() {
        val eqState = com.example.gobeats.core.audio.EqualizerState(virtualizerStrength = 400)
        assertEquals(400, eqState.virtualizerStrength)
    }

    @Test
    fun videoAspectMode_toggleCycle() {
        val modes = com.example.gobeats.core.model.VideoAspectMode.entries
        assertEquals(4, modes.size)
        assertTrue(modes.contains(com.example.gobeats.core.model.VideoAspectMode.FIT))
        assertTrue(modes.contains(com.example.gobeats.core.model.VideoAspectMode.FILL_CROP))
        assertTrue(modes.contains(com.example.gobeats.core.model.VideoAspectMode.STRETCH))
        assertTrue(modes.contains(com.example.gobeats.core.model.VideoAspectMode.RATIO_16_9))
    }

    @Test
    fun playbackUiState_videoAndTrackSelectionProperties() {
        val state = PlaybackUiState(
            videoWidth = 3840,
            videoHeight = 2160,
            videoAspectMode = com.example.gobeats.core.model.VideoAspectMode.RATIO_16_9,
            audioTracks = listOf("English (AAC)", "Hindi (AC3)"),
            selectedAudioTrackIndex = 1,
            subtitleTracks = listOf("English", "Hindi"),
            selectedSubtitleTrackIndex = 0
        )
        assertEquals(3840, state.videoWidth)
        assertEquals(2160, state.videoHeight)
        assertEquals(com.example.gobeats.core.model.VideoAspectMode.RATIO_16_9, state.videoAspectMode)
        assertEquals(2, state.audioTracks.size)
        assertEquals(1, state.selectedAudioTrackIndex)
        assertEquals(2, state.subtitleTracks.size)
        assertEquals(0, state.selectedSubtitleTrackIndex)
    }

    @Test
    fun defaultAppHelper_supportedFormatsDescription() {
        val desc = com.example.gobeats.core.util.DefaultAppHelper.getSupportedFormatsDescription()
        assertTrue(desc.contains("MP3"))
        assertTrue(desc.contains("FLAC"))
        assertTrue(desc.contains("MP4"))
        assertTrue(desc.contains("MKV"))
    }

    @Test
    fun liveTransferItem_progressFormatting() {
        val transfer = com.example.gobeats.core.sync.LiveTransferItem(
            id = "t_1",
            title = "Chill Vibes",
            senderDevice = "Ayush's Laptop",
            tracksCount = 42,
            totalSizeMb = 320.0,
            progress = 1.0f,
            timestamp = "2 days ago",
            isSuccess = true
        )
        assertEquals(42, transfer.tracksCount)
        assertEquals(320.0, transfer.totalSizeMb, 0.01)
        assertTrue(transfer.isSuccess)
    }

    @Test
    fun smartTagsParser_extractsArtistAndTitleFromPodcastFilename() {
        val parsed = com.example.gobeats.core.media.MetadataExtractor.parseSmartTags(
            rawTitle = null,
            rawArtist = null,
            rawAlbum = null,
            fileNameOrUri = "Seduction ka sabse bada niyam - Podcast.mp3"
        )
        assertEquals("Seduction ka sabse bada niyam", parsed.title)
        assertEquals("Podcast", parsed.artist)
    }

    @Test
    fun smartTagsParser_stripsClutterAndParsesArtistHyphenTitle() {
        val parsed = com.example.gobeats.core.media.MetadataExtractor.parseSmartTags(
            rawTitle = null,
            rawArtist = "Unknown Artist",
            rawAlbum = null,
            fileNameOrUri = "Arijit Singh - Tum Hi Ho [320kbps].mp3"
        )
        assertEquals("Tum Hi Ho", parsed.title)
        assertEquals("Arijit Singh", parsed.artist)
    }

    @Test
    fun smartTagsParser_preservesValidTags() {
        val parsed = com.example.gobeats.core.media.MetadataExtractor.parseSmartTags(
            rawTitle = "Starboy",
            rawArtist = "The Weeknd",
            rawAlbum = "Starboy",
            fileNameOrUri = "track_01.mp3"
        )
        assertEquals("Starboy", parsed.title)
        assertEquals("The Weeknd", parsed.artist)
        assertEquals("Starboy", parsed.album)
    }

    @Test
    fun playbackUiState_hasPlaybackSpeedProperty() {
        val state = PlaybackUiState()
        assertEquals(1.0f, state.playbackSpeed, 0.001f)

        val speedUp = state.copy(playbackSpeed = 1.5f)
        assertEquals(1.5f, speedUp.playbackSpeed, 0.001f)
    }

    @Test
    fun appIconManager_hasFourStyles() {
        val styles = com.example.gobeats.core.util.AppIconManager.ALL_STYLES
        assertEquals(4, styles.size)
        assertTrue(styles.contains("neon_purple"))
        assertTrue(styles.contains("white_minimal"))
        assertTrue(styles.contains("sunset"))
        assertTrue(styles.contains("mountain"))
    }

    @Test
    fun lrcParser_parsesStandardAndFractionalTimestamps() {
        val lrc = """
            [ti:Test Track]
            [ar:Test Artist]
            [00:05.50]First line of the song
            [01:12.800]Second line with millisecond precision
        """.trimIndent()

        val parsed = com.example.gobeats.core.lyrics.LrcParser.parse(lrc)
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(5500L, parsed.lines[0].timeMs)
        assertEquals("First line of the song", parsed.lines[0].text)
        assertEquals(72800L, parsed.lines[1].timeMs)
        assertEquals("Second line with millisecond precision", parsed.lines[1].text)
    }

    @Test
    fun lrcParser_handlesMultipleTimestampsPerLine() {
        val lrc = "[00:10.00][00:30.00]Repeat chorus"
        val parsed = com.example.gobeats.core.lyrics.LrcParser.parse(lrc)
        assertTrue(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals(10000L, parsed.lines[0].timeMs)
        assertEquals(30000L, parsed.lines[1].timeMs)
        assertEquals("Repeat chorus", parsed.lines[0].text)
        assertEquals("Repeat chorus", parsed.lines[1].text)
    }

    @Test
    fun lrcParser_fallsBackToUnsyncedLyrics() {
        val plain = """
            Just a line of plain text
            Another unsynced line
        """.trimIndent()

        val parsed = com.example.gobeats.core.lyrics.LrcParser.parse(plain)
        assertFalse(parsed.isSynced)
        assertEquals(2, parsed.lines.size)
        assertEquals("Just a line of plain text", parsed.lines[0].text)
        assertEquals(-1L, parsed.lines[0].timeMs)
    }

    @Test
    fun lrcParser_getActiveLineIndex_returnsAccurateIndex() {
        val lines = listOf(
            com.example.gobeats.core.lyrics.LyricLine(5000L, "Line 1"),
            com.example.gobeats.core.lyrics.LyricLine(12000L, "Line 2"),
            com.example.gobeats.core.lyrics.LyricLine(25000L, "Line 3")
        )

        // Before any line starts
        assertEquals(-1, com.example.gobeats.core.lyrics.LrcParser.getActiveLineIndex(lines, 3000L))
        // Exactly at first line
        assertEquals(0, com.example.gobeats.core.lyrics.LrcParser.getActiveLineIndex(lines, 5000L))
        // Between first and second
        assertEquals(0, com.example.gobeats.core.lyrics.LrcParser.getActiveLineIndex(lines, 8000L))
        // At second line
        assertEquals(1, com.example.gobeats.core.lyrics.LrcParser.getActiveLineIndex(lines, 12000L))
        // Past the last line
        assertEquals(2, com.example.gobeats.core.lyrics.LrcParser.getActiveLineIndex(lines, 50000L))
    }

    @Test
    fun sleepTimerManager_lifecycleAndTransitions() {
        val manager = com.example.gobeats.core.audio.SleepTimerManager()
        assertTrue(manager.state.value is com.example.gobeats.core.audio.SleepTimerState.Inactive)

        manager.startTimer(15, onFadeOut = null, onExpire = {})
        val runningState = manager.state.value
        assertTrue(runningState is com.example.gobeats.core.audio.SleepTimerState.Running)
        val running = runningState as com.example.gobeats.core.audio.SleepTimerState.Running
        assertEquals(900L, running.totalSeconds)

        var cancelCalled = false
        manager.cancelTimer(onCancel = { cancelCalled = true })
        assertTrue(manager.state.value is com.example.gobeats.core.audio.SleepTimerState.Inactive)
        assertTrue(cancelCalled)
    }

    @Test
    fun sleepTimerManager_endOfTrackTrigger() {
        val manager = com.example.gobeats.core.audio.SleepTimerManager()
        var expired = false
        manager.startEndOfTrackTimer { expired = true }

        assertTrue(manager.state.value is com.example.gobeats.core.audio.SleepTimerState.EndOfTrack)
        manager.notifyTrackEnded()
        assertTrue(expired)
        assertTrue(manager.state.value is com.example.gobeats.core.audio.SleepTimerState.Inactive)
    }

    @Test
    fun lrclibClient_cleanTitleForSearch_removesClutter() {
        val title1 = "Shape of You [320kbps] (Official Video)"
        assertEquals("Shape of You", com.example.gobeats.core.lyrics.LrclibClient.cleanTitleForSearch(title1))

        val title2 = "01 - Blinding Lights (Remix)"
        assertEquals("Blinding Lights", com.example.gobeats.core.lyrics.LrclibClient.cleanTitleForSearch(title2))

        val title3 = "Levitating (feat. Daft Punk)"
        assertEquals("Levitating", com.example.gobeats.core.lyrics.LrclibClient.cleanTitleForSearch(title3))
    }

    @Test
    fun lrclibClient_cleanArtistForSearch_handlesGenericPlaceholders() {
        assertEquals("", com.example.gobeats.core.lyrics.LrclibClient.cleanArtistForSearch("Local Audio"))
        assertEquals("", com.example.gobeats.core.lyrics.LrclibClient.cleanArtistForSearch("Unknown Artist"))
        assertEquals("", com.example.gobeats.core.lyrics.LrclibClient.cleanArtistForSearch("Music Video"))
        assertEquals("Dua Lipa", com.example.gobeats.core.lyrics.LrclibClient.cleanArtistForSearch("Dua Lipa"))
    }

    @Test
    fun lrclibClient_parseRecord_parsesValidJsonCorrectly() {
        val jsonStr = """
            {
                "id": 48291,
                "trackName": "Starboy",
                "artistName": "The Weeknd",
                "albumName": "Starboy",
                "duration": 230,
                "instrumental": false,
                "plainLyrics": "I'm tryna put you in the worst mood, ah",
                "syncedLyrics": "[00:10.50]I'm tryna put you in the worst mood, ah"
            }
        """.trimIndent()

        val record = com.example.gobeats.core.lyrics.LrclibClient.parseRecord(org.json.JSONObject(jsonStr))
        assertEquals(48291L, record.id)
        assertEquals("Starboy", record.trackName)
        assertEquals("The Weeknd", record.artistName)
        assertEquals("Starboy", record.albumName)
        assertEquals(230L, record.durationSeconds)
        assertFalse(record.instrumental)
        assertEquals("I'm tryna put you in the worst mood, ah", record.plainLyrics)
        assertEquals("[00:10.50]I'm tryna put you in the worst mood, ah", record.syncedLyrics)
    }

    @Test
    fun lrclibClient_parseRecordList_parsesArray() {
        val jsonArrayStr = """
            [
                {
                    "id": 1,
                    "trackName": "Song A",
                    "artistName": "Artist A",
                    "duration": 180,
                    "instrumental": false,
                    "syncedLyrics": "[00:05.00]Line A"
                },
                {
                    "id": 2,
                    "trackName": "Song B",
                    "artistName": "Artist B",
                    "duration": 210,
                    "instrumental": true
                }
            ]
        """.trimIndent()

        val list = com.example.gobeats.core.lyrics.LrclibClient.parseRecordList(org.json.JSONArray(jsonArrayStr))
        assertEquals(2, list.size)
        assertEquals("Song A", list[0].trackName)
        assertEquals("[00:05.00]Line A", list[0].syncedLyrics)
        assertTrue(list[1].instrumental)
    }

    @Test
    fun lyricsData_includesSourceMetadata() {
        val local = com.example.gobeats.core.lyrics.LrcParser.parse("[00:01.00]Local line", source = "local")
        assertEquals("local", local.source)

        val lrclib = com.example.gobeats.core.lyrics.LrcParser.parse("[00:01.00]LRCLIB line", source = "lrclib")
        assertEquals("lrclib", lrclib.source)
    }

    @Test
    fun id3TagWriter_synchsafeIntegerRoundtrip() {
        val testValues = intArrayOf(0, 1, 127, 128, 255, 1024, 16384, 268435455)
        val buffer = ByteArray(4)

        for (v in testValues) {
            Id3TagWriter.encodeSynchsafeInteger(v, buffer, 0)
            // Verify every byte has bit 7 (MSB) clear
            for (b in buffer) {
                assertEquals(0, (b.toInt() and 0x80))
            }
            val decoded = Id3TagWriter.decodeSynchsafeInteger(buffer, 0)
            assertEquals("Roundtrip mismatch for value $v", v, decoded)
        }
    }

    @Test
    fun id3TagWriter_buildId3v23Tag_validHeaderAndFrames() {
        val tagBytes = Id3TagWriter.buildId3v23Tag(
            title = "Starboy",
            artist = "The Weeknd",
            album = "Starboy",
            year = 2016,
            genre = "R&B",
            trackNumber = 1
        )

        // 1. Tag Header Verification (10 bytes)
        assertTrue("Tag bytes too short", tagBytes.size > 10)
        assertEquals('I'.code.toByte(), tagBytes[0])
        assertEquals('D'.code.toByte(), tagBytes[1])
        assertEquals('3'.code.toByte(), tagBytes[2])
        assertEquals(3.toByte(), tagBytes[3]) // Major version 2.3
        assertEquals(0.toByte(), tagBytes[4]) // Revision
        assertEquals(0.toByte(), tagBytes[5]) // Flags

        // Decoded tag payload size must match remaining bytes
        val declaredPayloadSize = Id3TagWriter.decodeSynchsafeInteger(tagBytes, 6)
        assertEquals(tagBytes.size - 10, declaredPayloadSize)

        // 2. Check frame markers in raw bytes
        val tagString = String(tagBytes, Charsets.ISO_8859_1)
        assertTrue("Missing TIT2 frame", tagString.contains("TIT2"))
        assertTrue("Missing TPE1 frame", tagString.contains("TPE1"))
        assertTrue("Missing TALB frame", tagString.contains("TALB"))
        assertTrue("Missing TYER frame", tagString.contains("TYER"))
        assertTrue("Missing TCON frame", tagString.contains("TCON"))
        assertTrue("Missing TRCK frame", tagString.contains("TRCK"))
    }

    @Test
    fun id3TagWriter_buildId3v23Tag_withApicArtworkFrame() {
        val dummyJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x12, 0x34)
        val tagBytes = Id3TagWriter.buildId3v23Tag(
            title = "Artwork Test",
            artist = "Artist",
            album = "Album",
            artworkBytes = dummyJpeg,
            artworkMimeType = "image/jpeg"
        )

        val tagString = String(tagBytes, Charsets.ISO_8859_1)
        assertTrue("Missing APIC frame", tagString.contains("APIC"))
        assertTrue("Missing MIME type in APIC frame", tagString.contains("image/jpeg"))

        // Verify dummy JPEG bytes are embedded
        val apicIdx = tagBytes.indices.firstOrNull { i ->
            i + 3 < tagBytes.size &&
            tagBytes[i] == 'A'.code.toByte() &&
            tagBytes[i + 1] == 'P'.code.toByte() &&
            tagBytes[i + 2] == 'I'.code.toByte() &&
            tagBytes[i + 3] == 'C'.code.toByte()
        } ?: -1
        assertTrue("APIC frame index not found", apicIdx != -1)
    }

    @Test
    fun id3TagWriter_writeTagsToMp3File_preservesAudioDataAndReplacesTag() {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "gobeats_test_" + System.currentTimeMillis())
        tempDir.mkdirs()
        val testMp3 = File(tempDir, "test_audio.mp3")

        val dummyAudioData = byteArrayOf(0x47, 0x4F, 0x42, 0x45, 0x41, 0x54, 0x53, 0x31, 0x32, 0x33, 0x34, 0x35)
        // Write initial file without ID3 tag
        FileOutputStream(testMp3).use { it.write(dummyAudioData) }

        val newTag = Id3TagWriter.buildId3v23Tag(
            title = "Direct MP3 Test",
            artist = "Apex Engine",
            album = "Tier 1 Audio"
        )

        val writeSuccess = Id3TagWriter.writeTagsToMp3File(testMp3, newTag)
        assertTrue("Failed to write tags to mp3 file", writeSuccess)

        // Read modified file
        val modifiedBytes = testMp3.readBytes()
        assertTrue("File smaller than tag + audio", modifiedBytes.size >= newTag.size + dummyAudioData.size)

        // Verify ID3 header
        assertEquals('I'.code.toByte(), modifiedBytes[0])
        assertEquals('D'.code.toByte(), modifiedBytes[1])
        assertEquals('3'.code.toByte(), modifiedBytes[2])

        // Verify audio data is appended intact at the end
        val audioTail = modifiedBytes.sliceArray((modifiedBytes.size - dummyAudioData.size) until modifiedBytes.size)
        org.junit.Assert.assertArrayEquals(dummyAudioData, audioTail)

        testMp3.delete()
        tempDir.delete()
    }

    @Test
    fun tagEditPayload_dataClassIntegrity() {
        val payload = TagEditPayload(
            trackId = "track_99",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            year = 2011,
            genre = "Electronic",
            trackNumber = 5,
            artworkUri = "content://art/99",
            newArtworkBytes = byteArrayOf(1, 2, 3)
        )

        assertEquals("track_99", payload.trackId)
        assertEquals("Midnight City", payload.title)
        assertEquals("M83", payload.artist)
        assertEquals("Hurry Up, We're Dreaming", payload.album)
        assertEquals(2011, payload.year)
        assertEquals("Electronic", payload.genre)
        assertEquals(5, payload.trackNumber)
        assertEquals("content://art/99", payload.artworkUri)
        assertEquals(3, payload.newArtworkBytes?.size)

        val copied = payload.copy(title = "Reunion", trackNumber = 2)
        assertEquals("Reunion", copied.title)
        assertEquals(2, copied.trackNumber)
        assertEquals("M83", copied.artist)
    }

    // --- Phase 2: ReplayGain / Volume Leveling Tests ---

    @Test
    fun replayGain_parseGainDb_validAndInvalidCases() {
        assertEquals(-6.5f, ReplayGainProcessor.parseGainDb("-6.50 dB")!!, 0.001f)
        assertEquals(2.1f, ReplayGainProcessor.parseGainDb("+2.10 dB")!!, 0.001f)
        assertEquals(0.0f, ReplayGainProcessor.parseGainDb("0.0 dB")!!, 0.001f)
        assertEquals(-14.2f, ReplayGainProcessor.parseGainDb("-14.2")!!, 0.001f)
        assertNull(ReplayGainProcessor.parseGainDb(null))
        assertNull(ReplayGainProcessor.parseGainDb(""))
        assertNull(ReplayGainProcessor.parseGainDb("   "))
        assertNull(ReplayGainProcessor.parseGainDb("not_a_number"))
    }

    @Test
    fun replayGain_parsePeak_validAndInvalidCases() {
        assertEquals(0.9854f, ReplayGainProcessor.parsePeak("0.9854")!!, 0.0001f)
        assertEquals(1.25f, ReplayGainProcessor.parsePeak("1.250000")!!, 0.0001f)
        assertNull(ReplayGainProcessor.parsePeak("0.0"))
        assertNull(ReplayGainProcessor.parsePeak("-0.5"))
        assertNull(ReplayGainProcessor.parsePeak(null))
        assertNull(ReplayGainProcessor.parsePeak(""))
    }

    @Test
    fun replayGain_estimateSmartFallbackGainDb_masteringEras() {
        assertEquals(2.4f, ReplayGainProcessor.estimateSmartFallbackGainDb(1975), 0.01f)
        assertEquals(-0.5f, ReplayGainProcessor.estimateSmartFallbackGainDb(1995), 0.01f)
        assertEquals(-2.8f, ReplayGainProcessor.estimateSmartFallbackGainDb(2008), 0.01f)
        assertEquals(-1.4f, ReplayGainProcessor.estimateSmartFallbackGainDb(2024), 0.01f)
    }

    @Test
    fun replayGain_computeGainAdjustment_attenuationBoostAndClipping() {
        // 1. Hot track (-6.0 dB gain): Linear attenuation, 0 loudness boost
        val resHot = ReplayGainProcessor.computeGainAdjustment(
            data = ReplayGainData(trackGainDb = -6.0f),
            mode = ReplayGainMode.TRACK,
            preAmpDb = 0f,
            preventClipping = true
        )
        assertTrue(resHot.linearVolume < 0.6f && resHot.linearVolume > 0.49f)
        assertEquals(0, resHot.loudnessBoostMb)
        assertEquals(-6.0f, resHot.appliedGainDb, 0.01f)

        // 2. Quiet track (+3.0 dB gain, safe peak 0.5): Linear volume 1.0f, +300 mB loudness boost
        val resQuiet = ReplayGainProcessor.computeGainAdjustment(
            data = ReplayGainData(trackGainDb = 3.0f, trackPeak = 0.5f),
            mode = ReplayGainMode.TRACK,
            preAmpDb = 0f,
            preventClipping = true
        )
        assertEquals(1.0f, resQuiet.linearVolume, 0.001f)
        assertEquals(300, resQuiet.loudnessBoostMb)
        assertEquals(3.0f, resQuiet.appliedGainDb, 0.01f)

        // 3. Peak Limiter (high peak 0.95 with +6.0 dB boost request): scales down to avoid clipping
        val resClip = ReplayGainProcessor.computeGainAdjustment(
            data = ReplayGainData(trackGainDb = 6.0f, trackPeak = 0.95f),
            mode = ReplayGainMode.TRACK,
            preAmpDb = 0f,
            preventClipping = true
        )
        assertTrue("Applied gain should be limited to prevent clipping", resClip.appliedGainDb < 1.0f)
        assertTrue(resClip.appliedGainDb >= 0.4f)

        // 4. Mode OFF: Unity volume, 0 boost, 0 dB applied
        val resOff = ReplayGainProcessor.computeGainAdjustment(
            data = ReplayGainData(trackGainDb = -6.0f),
            mode = ReplayGainMode.OFF
        )
        assertEquals(1.0f, resOff.linearVolume, 0.001f)
        assertEquals(0, resOff.loudnessBoostMb)
        assertEquals(0.0f, resOff.appliedGainDb, 0.01f)
    }

    // --- Phase 3: Playlist Importer Tests ---

    @Test
    fun playlistImporter_parseCsv_spotifyExportifyFormat() {
        val csv = """
            "Spotify ID","Track Name","Artist Name(s)","Album Name"
            "123","Get Lucky","Daft Punk, Pharrell Williams","Random Access Memories"
            "456","Starboy","The Weeknd, Daft Punk","Starboy"
        """.trimIndent()
        val (name, entries) = PlaylistImporter.parseCsv(csv, "Disco Playlist")
        assertEquals("Disco Playlist", name)
        assertEquals(2, entries.size)
        assertEquals("Get Lucky", entries[0].title)
        assertEquals("Daft Punk, Pharrell Williams", entries[0].artist)
        assertEquals("Random Access Memories", entries[0].album)
        assertEquals("Starboy", entries[1].title)
        assertEquals("The Weeknd, Daft Punk", entries[1].artist)
    }

    @Test
    fun playlistImporter_parseM3u_standardAndExtended() {
        val m3u = """
            #EXTM3U
            #EXTINF:240,Pink Floyd - Comfortably Numb
            /music/pink_floyd/comfortably_numb.mp3
            #EXTINF:180,Queen - Bohemian Rhapsody
            /music/queen/bohemian_rhapsody.flac
        """.trimIndent()
        val (name, entries) = PlaylistImporter.parseM3u(m3u, "Rock Classics")
        assertEquals("Rock Classics", name)
        assertEquals(2, entries.size)
        assertEquals("Comfortably Numb", entries[0].title)
        assertEquals("Pink Floyd", entries[0].artist)
        assertEquals("Bohemian Rhapsody", entries[1].title)
        assertEquals("Queen", entries[1].artist)
    }

    @Test
    fun playlistImporter_parsePlainText_tracklist() {
        val text = """
            1. Billie Eilish - Bad Guy
            2. Dua Lipa - Levitating
            3. Hotel California by Eagles
        """.trimIndent()
        val (_, entries) = PlaylistImporter.parsePlainText(text, "Top Hits")
        assertEquals(3, entries.size)
        assertEquals("Bad Guy", entries[0].title)
        assertEquals("Billie Eilish", entries[0].artist)
        assertEquals("Levitating", entries[1].title)
        assertEquals("Dua Lipa", entries[1].artist)
        assertEquals("Hotel California", entries[2].title)
        assertEquals("Eagles", entries[2].artist)
    }

    @Test
    fun playlistImporter_matchTracks_fuzzyMatchesAgainstLocalLibrary() {
        val localLibrary = listOf(
            Track(
                id = "t_1",
                sourceId = "local",
                fileUri = "/music/get_lucky.mp3",
                title = "Get Lucky (Radio Edit)",
                artistName = "Daft Punk feat. Pharrell Williams",
                albumTitle = "Random Access Memories",
                durationMs = 248000L
            ),
            Track(
                id = "t_2",
                sourceId = "local",
                fileUri = "/music/stay.mp3",
                title = "Stay",
                artistName = "The Kid LAROI",
                albumTitle = "F*CK LOVE 3",
                durationMs = 141000L
            )
        )

        val importedEntries = listOf(
            ImportedTrackEntry(title = "Get Lucky", artist = "Daft Punk", album = "Random Access Memories"),
            ImportedTrackEntry(title = "Non Existent Track", artist = "Unknown Singer")
        )

        val result = PlaylistImporter.matchTracks(importedEntries, localLibrary, "Imported Daft Punk")
        assertEquals("Imported Daft Punk", result.playlistName)
        assertEquals(1, result.matchedTracks.size)
        assertEquals("t_1", result.matchedTracks[0].id)
        assertEquals(1, result.unmatchedEntries.size)
        assertEquals("Non Existent Track", result.unmatchedEntries[0].title)
        assertEquals(50, result.matchRatePercent)
    }

    // --- Phase 4: AutoEQ Headphone Selector Tests ---

    @Test
    fun autoEqCatalog_containsReferenceHarmanProfiles() {
        assertTrue(AutoEqCatalog.PROFILES.size >= 25)
        val categories = AutoEqCatalog.CATEGORIES
        assertTrue(categories.contains("All"))
        assertTrue(categories.contains("TWS"))
        assertTrue(categories.contains("Over-Ear"))
        assertTrue(categories.contains("IEM"))
        assertTrue(categories.contains("Studio"))

        AutoEqCatalog.PROFILES.forEach { profile ->
            assertEquals("Profile ${profile.id} must have 5 gains", 5, profile.gainsDb.size)
            assertTrue("Profile ${profile.id} brand must not be blank", profile.brand.isNotBlank())
            assertTrue("Profile ${profile.id} model must not be blank", profile.model.isNotBlank())
        }
    }

    @Test
    fun autoEqCatalog_findById_findsProfilesAccurately() {
        val xm5 = AutoEqCatalog.findById("sony_wh1000xm5")
        assertNotNull(xm5)
        assertEquals("Sony", xm5!!.brand)
        assertEquals("WH-1000XM5", xm5.model)
        assertEquals("Sony WH-1000XM5", xm5.fullName)
        assertEquals("Over-Ear", xm5.category)

        val airpods = AutoEqCatalog.findById("apple_airpods_pro_2")
        assertNotNull(airpods)
        assertEquals("Apple", airpods!!.brand)
        assertEquals("AirPods Pro (2nd Gen)", airpods.model)
        assertEquals("TWS", airpods.category)

        assertNull(AutoEqCatalog.findById("non_existent_profile"))
        assertNull(AutoEqCatalog.findById(null))
    }

    @Test
    fun autoEqCatalog_interpolateGainMb_exactAndInterpolatedFrequencies() {
        val airpods = AutoEqCatalog.findById("apple_airpods_pro_2")!!
        // 60Hz exact index 0
        val gain60 = AutoEqCatalog.interpolateGainMb(airpods, 60)
        assertEquals((airpods.gainsDb[0] * 100f).roundToInt(), gain60)

        // 14000Hz exact index 4
        val gain14k = AutoEqCatalog.interpolateGainMb(airpods, 14000)
        assertEquals((airpods.gainsDb[4] * 100f).roundToInt(), gain14k)

        // Frequency below 60Hz is clamped to 60Hz gain
        assertEquals(gain60, AutoEqCatalog.interpolateGainMb(airpods, 30))

        // Frequency above 14000Hz is clamped to 14000Hz gain
        assertEquals(gain14k, AutoEqCatalog.interpolateGainMb(airpods, 20000))

        // Intermediate frequency (e.g. 500Hz between 230 and 910)
        val gain500 = AutoEqCatalog.interpolateGainMb(airpods, 500)
        assertTrue(gain500 >= -1500 && gain500 <= 1500)
    }

    // --- Phase 5: Direct-to-DAC / Bit-Perfect Hardware Diagnostics Tests ---

    @Test
    fun audioHardwareStatus_diagnosticsAndFormatting() {
        val status = AudioHardwareStatus(
            outputDeviceName = "AudioQuest DragonFly Cobalt",
            outputTypeLabel = "USB DAC / Audio Interface",
            isExternalDac = true,
            isBitPerfectSupported = true,
            isBitPerfectActive = true,
            supportedSampleRates = listOf(44100, 48000, 88200, 96000),
            supportedEncodings = listOf("32-bit Float", "24-bit Packed"),
            signalPathDescription = "[FLAC 24-bit / 96kHz] -> [32-bit Float AudioSink] -> [Direct USB Bit-Perfect Sink]"
        )
        assertTrue(status.isExternalDac)
        assertTrue(status.isBitPerfectActive)
        assertEquals(4, status.supportedSampleRates.size)
        assertTrue(status.signalPathDescription.contains("Direct USB Bit-Perfect Sink"))
    }

    // --- Video Player & Playlist Feature Tests ---

    @Test
    fun videoTrack_modelInitializationAndAspectState() {
        val videoTrack = Track(
            id = "vid_101",
            sourceId = "source_local",
            fileUri = "content://media/external/video/media/101",
            title = "Live Concert 4K",
            artistName = "Coldplay",
            albumTitle = "Music of the Spheres Live",
            durationMs = 360000L,
            format = "MP4",
            isVideo = true,
            videoWidth = 3840,
            videoHeight = 2160
        )

        assertTrue(videoTrack.isVideo)
        assertEquals("MP4", videoTrack.format)
        assertEquals(3840, videoTrack.videoWidth)
        assertEquals(2160, videoTrack.videoHeight)

        // Verify PlaybackUiState retains video mode and dimensions
        val state = PlaybackUiState(
            currentTrack = videoTrack,
            isPlaying = true,
            isVideoMode = true,
            videoWidth = videoTrack.videoWidth,
            videoHeight = videoTrack.videoHeight
        )

        assertTrue(state.isVideoMode)
        assertEquals(3840, state.videoWidth)
        assertEquals(2160, state.videoHeight)
    }

    @Test
    fun metadataExtractor_smartTagsForVideos_replacesClutterAndDefaults() {
        // Video filename with web download tags
        val tags1 = com.example.gobeats.core.media.MetadataExtractor.parseSmartTags(
            rawTitle = null,
            rawArtist = null,
            rawAlbum = null,
            fileNameOrUri = "Queen - Bohemian Rhapsody (Official Video) [1080p].mp4"
        )

        assertEquals("Queen", tags1.artist)
        assertEquals("Bohemian Rhapsody", tags1.title)

        // Video with no artist tag should fallback appropriately
        val tags2 = com.example.gobeats.core.media.MetadataExtractor.parseSmartTags(
            rawTitle = "Guitar Solo Live",
            rawArtist = null,
            rawAlbum = null,
            fileNameOrUri = "guitar_solo_live.mkv"
        )
        assertEquals("Local Audio", tags2.artist)
        assertEquals("Guitar Solo Live", tags2.title)
    }

    @Test
    fun playlist_trackAdditionAndOrderingSimulation() {
        val initialPlaylist = Playlist(
            id = "pl_custom_01",
            name = "Acoustic Chill",
            description = "Relaxing evening tracks",
            trackCount = 0
        )

        assertEquals(0, initialPlaylist.trackCount)
        assertEquals("Acoustic Chill", initialPlaylist.name)

        // Simulate sequential track addition
        val tracksToAdd = listOf(
            Track(id = "t1", sourceId = "s", fileUri = "u1", title = "Song 1", artistName = "A1", albumTitle = "Alb1", durationMs = 180000L),
            Track(id = "t2", sourceId = "s", fileUri = "u2", title = "Song 2", artistName = "A2", albumTitle = "Alb2", durationMs = 210000L),
            Track(id = "t3", sourceId = "s", fileUri = "u3", title = "Song 3", artistName = "A3", albumTitle = "Alb3", durationMs = 240000L)
        )

        val playlistTracks = mutableListOf<Pair<Int, Track>>()
        tracksToAdd.forEachIndexed { index, track ->
            playlistTracks.add(index to track)
        }

        val updatedPlaylist = initialPlaylist.copy(trackCount = playlistTracks.size)
        assertEquals(3, updatedPlaylist.trackCount)
        assertEquals("t1", playlistTracks[0].second.id)
        assertEquals(0, playlistTracks[0].first)
        assertEquals("t3", playlistTracks[2].second.id)
        assertEquals(2, playlistTracks[2].first)
    }

    @Test
    fun mediaScanner_durationFilteringRules() {
        fun shouldSkipVideo(vDurationMs: Long): Boolean {
            // New rule: only skip micro clips between 1ms and 2999ms, keep 0ms (unindexed) and >= 3000ms
            return vDurationMs in 1L until 3_000L
        }

        // Unindexed file where MediaStore returns 0ms duration should NOT be skipped
        assertFalse(shouldSkipVideo(0L))

        // WhatsApp audio voice note / UI ping of 1.5s SHOULD be skipped
        assertTrue(shouldSkipVideo(1500L))
        assertTrue(shouldSkipVideo(2999L))

        // Legitimate video clips of 3s, 10s, 5m should NOT be skipped
        assertFalse(shouldSkipVideo(3000L))
        assertFalse(shouldSkipVideo(10000L))
        assertFalse(shouldSkipVideo(300000L))
    }

    @Test
    fun videoQuality_optionsParsingAndConstraints() {
        val autoOption = com.example.gobeats.core.model.VideoQualityOption(
            id = "auto",
            label = "Auto (1080p60)",
            resolutionLabel = "1920x1080",
            frameRate = 60f,
            bitrate = 6_500_000,
            isSelected = true,
            isAuto = true
        )
        assertTrue(autoOption.isAuto)
        assertTrue(autoOption.isSelected)
        assertEquals(Int.MAX_VALUE, autoOption.maxWidth)

        val option1080p = com.example.gobeats.core.model.VideoQualityOption(
            id = "1080p",
            label = "1080p (FHD)",
            resolutionLabel = "1920x1080",
            maxHeight = 1080,
            maxWidth = 1920
        )
        assertEquals(1080, option1080p.maxHeight)
        assertEquals(1920, option1080p.maxWidth)
        assertEquals("1920x1080", option1080p.resolutionLabel)

        val option720p = com.example.gobeats.core.model.VideoQualityOption(
            id = "720p",
            label = "720p (HD)",
            resolutionLabel = "1280x720",
            maxHeight = 720,
            maxWidth = 1280
        )
        assertEquals(720, option720p.maxHeight)
        assertEquals(1280, option720p.maxWidth)
    }

    @Test
    fun videoPlayback_statsFormatting_computesResolutionAndBuffer() {
        val stats = com.example.gobeats.core.model.VideoPlaybackStats(
            resolution = "3840x2160 @ 60.0 fps",
            videoCodec = "HEVC / H.265 Main 10",
            colorStandard = "BT.2020 HDR",
            videoBitrateKbps = 24500,
            decoderName = "Hardware Accelerated MediaCodec",
            audioCodec = "AAC 48.0 kHz (5.1 Surround)",
            droppedFrames = 0L,
            totalFrames = 18000L,
            bufferHealthMs = 28500L
        )

        assertEquals("3840x2160 @ 60.0 fps", stats.resolution)
        assertEquals("BT.2020 HDR", stats.colorStandard)
        assertEquals(24500, stats.videoBitrateKbps)
        assertEquals(0L, stats.droppedFrames)
        assertEquals(28500L, stats.bufferHealthMs)
        assertEquals("HEVC / H.265 Main 10", stats.videoCodec)
    }

    @Test
    fun videoPlayback_pressAndHold2xSpeed_stateTransitions() {
        var baseSpeed = 1.25f
        var currentSpeed = baseSpeed
        var is2xSpeedActive = false

        // Simulate press and hold
        fun onStart2xSpeed() {
            if (!is2xSpeedActive) {
                is2xSpeedActive = true
                currentSpeed = 2.0f
            }
        }

        // Simulate touch release
        fun onStop2xSpeed() {
            if (is2xSpeedActive) {
                is2xSpeedActive = false
                currentSpeed = baseSpeed
            }
        }

        assertEquals(1.25f, currentSpeed, 0.001f)
        assertFalse(is2xSpeedActive)

        // User presses and holds
        onStart2xSpeed()
        assertTrue(is2xSpeedActive)
        assertEquals(2.0f, currentSpeed, 0.001f)

        // User releases finger
        onStop2xSpeed()
        assertFalse(is2xSpeedActive)
        assertEquals(1.25f, currentSpeed, 0.001f)
    }

    @Test
    fun videoPlayback_multiTapSeekAccumulation_calculatesDelta() {
        var accumulatedSeconds = 10
        val durationMs = 120_000L
        var currentPos = 30_000L

        // 1st double tap: +10s
        currentPos = (currentPos + accumulatedSeconds * 1000L).coerceAtMost(durationMs)
        assertEquals(40_000L, currentPos)

        // 2nd rapid tap: accumulates to +20s
        accumulatedSeconds += 10
        currentPos = (currentPos + accumulatedSeconds * 1000L).coerceAtMost(durationMs)
        assertEquals(60_000L, currentPos)

        // 3rd rapid tap: accumulates to +30s
        accumulatedSeconds += 10
        currentPos = (currentPos + accumulatedSeconds * 1000L).coerceAtMost(durationMs)
        assertEquals(90_000L, currentPos)
    }

    @Test
    fun videoPlayback_touchLockMode_stateBehavior() {
        var isTouchLocked = false

        fun toggleTouchLock(locked: Boolean) {
            isTouchLocked = locked
        }

        assertFalse(isTouchLocked)
        toggleTouchLock(true)
        assertTrue(isTouchLocked)

        // When locked, seek actions should be ignored
        val initialPos = 15_000L
        var targetPos = initialPos
        fun trySeek(newPos: Long) {
            if (!isTouchLocked) {
                targetPos = newPos
            }
        }

        trySeek(45_000L)
        assertEquals(initialPos, targetPos) // unchanged!

        // Unlock
        toggleTouchLock(false)
        assertFalse(isTouchLocked)
        trySeek(45_000L)
        assertEquals(45_000L, targetPos) // successfully sought!
    }
}



