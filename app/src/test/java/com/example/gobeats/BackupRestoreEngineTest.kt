package com.example.gobeats

import com.example.gobeats.core.backup.BackupPlaylistRecord
import com.example.gobeats.core.backup.BackupRestoreEngine
import com.example.gobeats.core.backup.BackupTrackRecord
import com.example.gobeats.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class BackupRestoreEngineTest {

    @Test
    fun testSerializationAndParsingRoundTrip() {
        val tracks = listOf(
            BackupTrackRecord("content://media/audio/1", "Bohemian Rhapsody", "Queen"),
            BackupTrackRecord("content://media/audio/2", "Hotel California", "Eagles")
        )
        val playlists = listOf(
            BackupPlaylistRecord("Classic Rock", "Best 70s rock", tracks),
            BackupPlaylistRecord("Empty Playlist", "", emptyList())
        )
        val favorites = listOf(
            BackupTrackRecord("content://media/audio/1", "Bohemian Rhapsody", "Queen")
        )

        val json = BackupRestoreEngine.serializeBackup(playlists, favorites, exportedAt = 123456789L)
        assertTrue(json.contains("GoBeats"))
        assertTrue(json.contains("Classic Rock"))
        assertTrue(json.contains("Bohemian Rhapsody"))

        val parsed = BackupRestoreEngine.parseBackup(json)
        assertEquals(1, parsed.version)
        assertEquals("GoBeats", parsed.app)
        assertEquals(123456789L, parsed.exportedAt)
        assertEquals(2, parsed.playlists.size)
        assertEquals("Classic Rock", parsed.playlists[0].name)
        assertEquals("Best 70s rock", parsed.playlists[0].description)
        assertEquals(2, parsed.playlists[0].tracks.size)
        assertEquals("Bohemian Rhapsody", parsed.playlists[0].tracks[0].title)
        assertEquals(1, parsed.favorites.size)
        assertEquals("Queen", parsed.favorites[0].artist)
    }

    @Test
    fun testTrackMatchingExactUri() {
        val available = listOf(
            Track("t1", "local", "content://media/audio/100", "Song A", "Artist X", "Album 1", 180000L),
            Track("t2", "local", "content://media/audio/200", "Song B", "Artist Y", "Album 2", 200000L)
        )
        val record = BackupTrackRecord("content://media/audio/200", "Different Title", "Different Artist")
        val matched = BackupRestoreEngine.matchBackupTrack(record, available)
        assertNotNull(matched)
        assertEquals("t2", matched?.id)
    }

    @Test
    fun testTrackMatchingTitleAndArtistFallback() {
        val available = listOf(
            Track("t1", "local", "content://media/audio/new_path_1", "Stairway to Heaven", "Led Zeppelin", "IV", 480000L),
            Track("t2", "local", "content://media/audio/new_path_2", "Kashmir", "Led Zeppelin", "Physical Graffiti", 500000L)
        )
        // Record has old URI but exact title and artist
        val record = BackupTrackRecord("content://media/audio/old_path_1", "stairway to heaven", "led zeppelin")
        val matched = BackupRestoreEngine.matchBackupTrack(record, available)
        assertNotNull(matched)
        assertEquals("t1", matched?.id)
    }

    @Test
    fun testTrackMatchingNonExistentReturnsNull() {
        val available = listOf(
            Track("t1", "local", "content://media/audio/1", "Song A", "Artist X", "Album 1", 180000L)
        )
        val record = BackupTrackRecord("content://media/audio/999", "Unknown Song", "Unknown Artist")
        val matched = BackupRestoreEngine.matchBackupTrack(record, available)
        assertNull(matched)
    }

    @Test
    fun testParseInvalidJsonThrowsException() {
        val invalidJson = """{"app": "AnotherPlayer", "data": []}"""
        try {
            BackupRestoreEngine.parseBackup(invalidJson)
            fail("Expected IllegalArgumentException for unknown backup format")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Not a recognized GoBeats backup") == true)
        }
    }

    @Test
    fun testSpecialCharactersAndEmojis() {
        val trickyPlaylists = listOf(
            BackupPlaylistRecord(
                name = "Chill & Beats \uD83C\uDFA7\uD83C\uDFB5 \"Vibes\"",
                description = "Path \\ with / quotes \" and symbols <>&",
                tracks = listOf(
                    BackupTrackRecord("content://media/audio/emoji%201", "Song \uD83D\uDD25", "Artist \u2728")
                )
            )
        )
        val json = BackupRestoreEngine.serializeBackup(trickyPlaylists, emptyList())
        val parsed = BackupRestoreEngine.parseBackup(json)
        assertEquals(1, parsed.playlists.size)
        assertEquals("Chill & Beats \uD83C\uDFA7\uD83C\uDFB5 \"Vibes\"", parsed.playlists[0].name)
        assertEquals("Path \\ with / quotes \" and symbols <>&", parsed.playlists[0].description)
        assertEquals("Song \uD83D\uDD25", parsed.playlists[0].tracks[0].title)
    }
}
