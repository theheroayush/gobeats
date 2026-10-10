package com.example.gobeats

import com.example.gobeats.core.model.PlaybackUiState
import com.example.gobeats.core.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Empirical Adversarial Challenger Test Suite:
 * Stress-testing Floating Video Miniplayer overlay geometry, drag bounds,
 * magnetic edge snapping, PlayerView lifecycle, and navigation/dismiss state machine.
 */
class FloatingMiniplayerStressTest {

    // =========================================================================
    // 1. Overlay Geometry & 16:9 Aspect Ratio Preservation
    // =========================================================================

    @Test
    fun testMiniplayerAspectRatio_exact16_9Preservation() {
        val cardWidthDp = 208f
        val cardHeightDp = 117f

        val calculatedRatio = cardWidthDp / cardHeightDp
        val expected16_9Ratio = 16f / 9f

        // Mathematically verify 208x117 is an exact 16:9 representation
        val delta = abs(calculatedRatio - expected16_9Ratio)
        assertTrue(
            "Miniplayer aspect ratio ($calculatedRatio) must match 16:9 ($expected16_9Ratio) within 0.001",
            delta < 0.001f
        )

        // Verify integer proportionality: 208 * 9 = 1872 == 117 * 16 = 1872
        assertEquals(
            "Width * 9 must equal Height * 16 for exact integer 16:9",
            (cardWidthDp * 9f).toInt(),
            (cardHeightDp * 16f).toInt()
        )
    }

    @Test
    fun testContentAspectRatios_fitWithoutDistortion() {
        // Test various video content aspect ratios mapped into the 16:9 miniplayer frame
        val testContentAspects = listOf(
            "16:9 standard" to (1920f / 1080f),
            "4:3 classic" to (1440f / 1080f),
            "21:9 ultrawide cinema" to (2560f / 1080f),
            "9:16 vertical reel" to (1080f / 1920f),
            "1:1 square" to 1.0f,
            "2.39:1 anamorphic" to 2.39f
        )

        val frameWidth = 208f
        val frameHeight = 117f

        for ((label, contentAspect) in testContentAspects) {
            // Emulate RESIZE_MODE_FIT scaling inside the frame
            val frameAspect = frameWidth / frameHeight
            val renderedWidth: Float
            val renderedHeight: Float

            if (contentAspect > frameAspect) {
                // Letterbox (black bars top & bottom)
                renderedWidth = frameWidth
                renderedHeight = frameWidth / contentAspect
            } else {
                // Pillarbox (black bars left & right)
                renderedHeight = frameHeight
                renderedWidth = frameHeight * contentAspect
            }

            assertTrue("$label: Rendered width must not exceed frame width", renderedWidth <= frameWidth + 0.01f)
            assertTrue("$label: Rendered height must not exceed frame height", renderedHeight <= frameHeight + 0.01f)
            assertTrue("$label: Rendered width must be positive", renderedWidth > 0f)
            assertTrue("$label: Rendered height must be positive", renderedHeight > 0f)

            // Content aspect ratio is strictly preserved
            val renderedAspect = renderedWidth / renderedHeight
            assertEquals("$label: Content aspect ratio must be preserved without distortion",
                contentAspect, renderedAspect, 0.01f)
        }
    }

    // =========================================================================
    // 2. Drag Bounds & Coordinate Clamping Under Adversarial Inputs
    // =========================================================================

    @Test
    fun testMiniplayerDragBounds_normalScreen_boundariesAndClamping() {
        val density = 2.625f // Pixel 7 standard density
        val parentWidthPx = 1080f
        val parentHeightPx = 2400f
        val cardWidthPx = 208f * density
        val cardHeightPx = 117f * density
        val marginPx = 16f * density
        val topMarginPx = 56f * density
        val bottomMarginPx = 92f * density

        val minX = marginPx
        val maxX = (parentWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)
        val minY = topMarginPx
        val maxY = (parentHeightPx - cardHeightPx - bottomMarginPx).coerceAtLeast(topMarginPx)

        fun clampX(x: Float): Float = x.coerceIn(minX, maxX)
        fun clampY(y: Float): Float = y.coerceIn(minY, maxY)

        // Inside bounds
        val midX = (minX + maxX) / 2f
        val midY = (minY + maxY) / 2f
        assertEquals(midX, clampX(midX), 0.01f)
        assertEquals(midY, clampY(midY), 0.01f)

        // Extreme negative coordinates (user fling towards top-left)
        assertEquals(minX, clampX(-100f), 0.01f)
        assertEquals(minX, clampX(-999_999f), 0.01f)
        assertEquals(minY, clampY(-500f), 0.01f)
        assertEquals(minY, clampY(-999_999f), 0.01f)

        // Extreme positive coordinates (user fling towards bottom-right)
        assertEquals(maxX, clampX(parentWidthPx + 500f), 0.01f)
        assertEquals(maxX, clampX(999_999f), 0.01f)
        assertEquals(maxY, clampY(parentHeightPx + 500f), 0.01f)
        assertEquals(maxY, clampY(999_999f), 0.01f)

        // Verify min bounds are strictly positive and safe from cutoffs
        assertTrue("Min X must be at least marginPx", minX >= marginPx)
        assertTrue("Min Y must be at least topMarginPx (clearing status bar)", minY >= topMarginPx)
        assertTrue("Max Y must clear bottom navigation bar", maxY <= parentHeightPx - cardHeightPx - bottomMarginPx + 0.01f)
    }

    @Test
    fun testMiniplayerDragBounds_extremeScreens_narrow_splitScreen_noCrash() {
        val density = 2f
        val cardWidthPx = 208f * density // 416 px
        val cardHeightPx = 117f * density // 234 px
        val marginPx = 16f * density // 32 px
        val topMarginPx = 56f * density // 112 px
        val bottomMarginPx = 92f * density // 184 px

        // Test with tiny screen / narrow split-screen window where parentWidth < cardWidth + 2*margin
        val narrowWidths = listOf(100f, 200f, 300f, 400f, 450f)
        val shortHeights = listOf(100f, 200f, 300f, 400f, 500f)

        for (pWidth in narrowWidths) {
            for (pHeight in shortHeights) {
                // Must NOT throw IllegalArgumentException: Cannot coerce value to an empty range
                val minX = marginPx
                val maxX = (pWidth - cardWidthPx - marginPx).coerceAtLeast(marginPx)
                assertTrue("maxX must be >= minX even on narrow screen", maxX >= minX)

                val minY = topMarginPx
                val maxY = (pHeight - cardHeightPx - bottomMarginPx).coerceAtLeast(topMarginPx)
                assertTrue("maxY must be >= minY even on short screen", maxY >= minY)

                val clampedX = 150f.coerceIn(minX, maxX)
                val clampedY = 150f.coerceIn(minY, maxY)

                assertTrue("Clamped X must be within range", clampedX in minX..maxX)
                assertTrue("Clamped Y must be within range", clampedY in minY..maxY)
            }
        }
    }

    // =========================================================================
    // 3. Magnetic Edge-Snapping Math Oracle & Generators
    // =========================================================================

    @Test
    fun testMagneticEdgeSnapping_oraclePropertyAcrossRandomPositions() {
        val parentWidthPx = 1080f
        val cardWidthPx = 546f // 208dp * 2.625
        val marginPx = 42f // 16dp * 2.625

        val snapLeft = marginPx
        val snapRight = (parentWidthPx - cardWidthPx - marginPx).coerceAtLeast(marginPx)

        fun computeSnapTarget(offsetX: Float): Float {
            val center = offsetX + cardWidthPx / 2f
            return if (center < parentWidthPx / 2f) snapLeft else snapRight
        }

        val parentCenter = parentWidthPx / 2f // 540f

        // Position where card center is exactly at 100f (< 540)
        assertEquals(snapLeft, computeSnapTarget(100f - cardWidthPx / 2f), 0.01f)

        // Position where card center is at 539.9f (just left of screen center)
        assertEquals(snapLeft, computeSnapTarget(539.9f - cardWidthPx / 2f), 0.01f)

        // Position where card center is at 540.0f (exact center -> snap right)
        assertEquals(snapRight, computeSnapTarget(540.0f - cardWidthPx / 2f), 0.01f)

        // Position where card center is at 540.1f (just right of screen center)
        assertEquals(snapRight, computeSnapTarget(540.1f - cardWidthPx / 2f), 0.01f)

        // Generator: test 500 linearly distributed positions across screen
        for (i in 0..500) {
            val x = marginPx + (snapRight - marginPx) * (i / 500f)
            val center = x + cardWidthPx / 2f
            val target = computeSnapTarget(x)

            if (center < parentCenter) {
                assertEquals("Left half must snap left", snapLeft, target, 0.01f)
                val distToLeft = abs(x - snapLeft)
                val distToRight = abs(x - snapRight)
                assertTrue("Left snap distance ($distToLeft) must be <= right distance ($distToRight)", distToLeft <= distToRight)
            } else {
                assertEquals("Right half must snap right", snapRight, target, 0.01f)
                val distToLeft = abs(x - snapLeft)
                val distToRight = abs(x - snapRight)
                assertTrue("Right snap distance ($distToRight) must be <= left distance ($distToLeft)", distToRight <= distToLeft)
            }
        }
    }

    // =========================================================================
    // 4. Progress Bar Safe Coercion & Zero Division Immunity
    // =========================================================================

    @Test
    fun testProgressBar_calculationImmunity() {
        fun computeProgress(positionMs: Long, durationMs: Long): Float {
            return if (durationMs > 0) {
                (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            } else 0f
        }

        // Zero duration
        assertEquals(0f, computeProgress(0L, 0L), 0.0001f)
        assertEquals(0f, computeProgress(1000L, 0L), 0.0001f)

        // Negative duration (corrupt metadata)
        assertEquals(0f, computeProgress(1000L, -500L), 0.0001f)

        // Negative position
        assertEquals(0f, computeProgress(-100L, 10_000L), 0.0001f)

        // Position beyond duration
        assertEquals(1f, computeProgress(15_000L, 10_000L), 0.0001f)
        assertEquals(1f, computeProgress(999_999L, 10_000L), 0.0001f)

        // Normal values
        assertEquals(0.25f, computeProgress(2_500L, 10_000L), 0.0001f)
        assertEquals(0.50f, computeProgress(5_000L, 10_000L), 0.0001f)
        assertEquals(0.75f, computeProgress(7_500L, 10_000L), 0.0001f)
    }

    // =========================================================================
    // 5. Miniplayer & Audio Bar Mutual Exclusivity State Machine
    // =========================================================================

    @Test
    fun testMiniplayerAndAudioBar_mutualExclusivityTruthTable() {
        fun computeVisibility(
            hasTrack: Boolean,
            isVideoTrack: Boolean,
            isVideoMode: Boolean,
            showNowPlayingSheet: Boolean,
            isCinemaActive: Boolean,
            isDismissed: Boolean
        ): Pair<Boolean, Boolean> {
            val showFloatingMiniplayer = isVideoMode &&
                isVideoTrack &&
                !showNowPlayingSheet &&
                !isCinemaActive &&
                !isDismissed

            val showAudioMiniPlayerBar = hasTrack && !showFloatingMiniplayer

            return Pair(showFloatingMiniplayer, showAudioMiniPlayerBar)
        }

        // Exhaustive 32-state truth table permutation testing
        val booleans = listOf(false, true)
        var floatingMiniplayerCount = 0

        for (hasTrack in booleans) {
            for (isVideoTrack in booleans) {
                for (isVideoMode in booleans) {
                    for (showNowPlayingSheet in booleans) {
                        for (isCinemaActive in booleans) {
                            for (isDismissed in booleans) {
                                val (floating, audioBar) = computeVisibility(
                                    hasTrack = hasTrack,
                                    isVideoTrack = isVideoTrack,
                                    isVideoMode = isVideoMode,
                                    showNowPlayingSheet = showNowPlayingSheet,
                                    isCinemaActive = isCinemaActive,
                                    isDismissed = isDismissed
                                )

                                // INVARIANT: Floating video miniplayer and audio miniplayer bar must NEVER BOTH be visible at the same time
                                assertFalse(
                                    "Both floating miniplayer and audio bar must never be shown simultaneously",
                                    floating && audioBar
                                )

                                if (floating) {
                                    floatingMiniplayerCount++
                                    // When floating is true, must strictly satisfy all conditions
                                    assertTrue(isVideoTrack)
                                    assertTrue(isVideoMode)
                                    assertFalse(showNowPlayingSheet)
                                    assertFalse(isCinemaActive)
                                    assertFalse(isDismissed)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Exactly 2 combinations where floating is true (hasTrack true or false, with all other 5 variables fixed to required values)
        assertEquals(2, floatingMiniplayerCount)
    }

    // =========================================================================
    // 6. Navigation Transitions & Lifecycle Simulation
    // =========================================================================

    @Test
    fun testNavigationTransitions_nowPlayingSheetAndMiniplayerHandoff() {
        var isVideoMode = true
        var isVideoTrack = true
        var showNowPlayingSheet = true // Starts in Now Playing Fullscreen Sheet
        var isCinemaActive = false
        var isMiniplayerDismissed = false
        var currentTrackId = "track_1"

        fun isMiniplayerVisible() = isVideoMode && isVideoTrack && !showNowPlayingSheet && !isCinemaActive && !isMiniplayerDismissed

        // 1. Initially inside NowPlayingSheet -> Miniplayer hidden
        assertFalse(isMiniplayerVisible())

        // 2. User minimizes sheet (e.g. back gesture or swipe down)
        showNowPlayingSheet = false
        assertTrue("Miniplayer must appear upon sheet minimize", isMiniplayerVisible())

        // 3. User taps miniplayer expand -> NowPlayingSheet opens
        showNowPlayingSheet = true
        assertFalse("Miniplayer must hide when sheet expands", isMiniplayerVisible())

        // 4. User minimizes sheet again
        showNowPlayingSheet = false
        assertTrue("Miniplayer must reappear", isMiniplayerVisible())

        // 5. User taps close (X) on miniplayer
        isMiniplayerDismissed = true
        isVideoMode = false
        assertFalse("Miniplayer must hide upon dismiss", isMiniplayerVisible())

        // 6. User plays a new video track
        currentTrackId = "track_2"
        isMiniplayerDismissed = false // Reset by LaunchedEffect(track.id)
        isVideoMode = true
        assertTrue("New track must restore miniplayer visibility", isMiniplayerVisible())

        // 7. User switches orientation to landscape cinema
        isCinemaActive = true
        assertFalse("Miniplayer must hide in cinema mode", isMiniplayerVisible())

        // 8. User exits cinema mode
        isCinemaActive = false
        assertTrue("Miniplayer must restore when exiting cinema", isMiniplayerVisible())
    }

    // =========================================================================
    // 7. PlayerView Detachment & Reattachment Safety Simulation
    // =========================================================================

    @Test
    fun testPlayerViewHandover_doesNotClearStateOnHandover() {
        // Simulates PlayerView attachment / detachment logic without Android UI dependencies
        class MockPlayer {
            var currentSurfaceView: Any? = null
            var isPlaying = true
            var positionMs = 45_000L
            val listeners = mutableListOf<String>()

            fun clearVideoSurfaceView(view: Any?) {
                if (view == currentSurfaceView) {
                    currentSurfaceView = null
                }
            }

            fun setVideoSurfaceView(view: Any?) {
                currentSurfaceView = view
            }
        }

        class MockPlayerView(val id: String) {
            var player: MockPlayer? = null
                set(value) {
                    if (field == value) return
                    if (field != null) {
                        field?.listeners?.remove(id)
                        field?.clearVideoSurfaceView(this)
                    }
                    field = value
                    if (value != null) {
                        value.listeners.add(id)
                        value.setVideoSurfaceView(this)
                    }
                }
        }

        val player = MockPlayer()
        val miniPlayerView = MockPlayerView("mini")
        val fullPlayerView = MockPlayerView("full")

        // 1. Miniplayer attaches player
        miniPlayerView.player = player
        assertEquals(miniPlayerView, player.currentSurfaceView)
        assertEquals(listOf("mini"), player.listeners)
        assertTrue(player.isPlaying)
        assertEquals(45_000L, player.positionMs)

        // 2. Expand: Full player attaches player FIRST (or in same composition frame)
        fullPlayerView.player = player
        assertEquals(fullPlayerView, player.currentSurfaceView)
        assertEquals(listOf("mini", "full"), player.listeners)

        // 3. Miniplayer is disposed and sets player = null
        miniPlayerView.player = null
        // Crucial invariant: clearing miniPlayerView must NOT clear fullPlayerView's surface!
        assertEquals("Full player surface must remain attached after mini player disposal",
            fullPlayerView, player.currentSurfaceView)
        assertEquals(listOf("full"), player.listeners)
        assertTrue("Playback must not be stopped during surface handover", player.isPlaying)
        assertEquals("Playback position must be preserved", 45_000L, player.positionMs)

        // 4. Minimize: Mini player attaches again
        miniPlayerView.player = player
        assertEquals(miniPlayerView, player.currentSurfaceView)

        // 5. Full player disposed
        fullPlayerView.player = null
        assertEquals(miniPlayerView, player.currentSurfaceView)
        assertEquals(listOf("mini"), player.listeners)
        assertTrue(player.isPlaying)
    }
}
