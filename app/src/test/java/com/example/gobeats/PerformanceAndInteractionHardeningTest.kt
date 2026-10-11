package com.example.gobeats

import com.example.gobeats.core.model.Album
import com.example.gobeats.core.model.Artist
import com.example.gobeats.core.model.LibraryDataState
import com.example.gobeats.core.model.Playlist
import com.example.gobeats.core.model.Track
import com.example.gobeats.core.ui.ClickGuard
import com.example.gobeats.core.video.SubtitleDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class PerformanceAndInteractionHardeningTest {

    @Test
    fun clickGuard_blocksRapidSuccessiveTapsWithinDebounceInterval() {
        val guard = ClickGuard(debounceMs = 350L)

        // First tap must always be allowed
        val firstAllowed = guard.allowClick()
        assertTrue("First click must be permitted immediately", firstAllowed)

        // Immediate secondary tap (< 350ms) must be dropped
        val secondAllowed = guard.allowClick()
        assertFalse("Second immediate click within debounce interval must be dropped to prevent ANR", secondAllowed)

        // Third immediate tap must also be dropped
        val thirdAllowed = guard.allowClick()
        assertFalse("Third immediate click within debounce interval must be dropped", thirdAllowed)
    }

    @Test
    fun libraryDataState_providesAtomicImmutableDataContainer() {
        val dummyTrack = Track(
            id = "t_1",
            sourceId = "local",
            fileUri = "file:///dummy.mp3",
            title = "Test Track",
            artistName = "Test Artist",
            albumTitle = "Test Album",
            durationMs = 180000L
        )
        val dummyAlbum = Album(
            id = "alb_1",
            title = "Test Album",
            artistName = "Test Artist",
            trackCount = 1
        )
        val dummyArtist = Artist(
            id = "art_1",
            name = "Test Artist",
            trackCount = 1
        )
        val dummyPlaylist = Playlist(
            id = "pl_1",
            name = "Favorites",
            trackCount = 1
        )

        val state = LibraryDataState(
            tracks = listOf(dummyTrack),
            videos = emptyList(),
            albums = listOf(dummyAlbum),
            artists = listOf(dummyArtist),
            playlists = listOf(dummyPlaylist),
            recentlyPlayed = listOf(dummyTrack),
            recentlyAdded = listOf(dummyTrack)
        )

        assertEquals(1, state.tracks.size)
        assertEquals(1, state.albums.size)
        assertEquals(1, state.artists.size)
        assertEquals(1, state.playlists.size)
        assertEquals(1, state.recentlyPlayed.size)
        assertEquals(0, state.videos.size)
    }

    @Test
    fun rapidPlaybackJobs_cancelInFlightOperationsDeterministically() = runTest {
        var completedCounter = 0
        var activeJob: Job? = null

        // Simulate user clicking 5 songs in 50 milliseconds
        for (i in 1..5) {
            activeJob?.cancel()
            activeJob = launch {
                kotlinx.coroutines.delay(100L)
                completedCounter++
            }
        }

        // Allow coroutine scheduler to advance past delay
        testScheduler.advanceTimeBy(200L)

        // Exactly 1 job (the final one) should finish; the previous 4 must have been cancelled
        assertEquals("Only the final selected playback job should finish", 1, completedCounter)
    }

    @Test
    fun subtitleDetector_extractLanguageInfo_correctlyIdentifiesVariations() {
        val (codeEn, labelEn) = SubtitleDetector.extractLanguageInfo("Movie", "Movie.en.srt")
        assertEquals("en", codeEn)
        assertEquals("English", labelEn)

        val (codeHi, labelHi) = SubtitleDetector.extractLanguageInfo("Cinema", "Cinema.hindi.vtt")
        assertEquals("hi", codeHi)
        assertEquals("Hindi", labelHi)

        val (codeDefault, labelDefault) = SubtitleDetector.extractLanguageInfo("Video", "Video.srt")
        assertEquals("und", codeDefault)
        assertEquals("Subtitles", labelDefault)
    }
}
