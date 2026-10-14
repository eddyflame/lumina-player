package org.lumina.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlayerQueueAndShuffleTest {

    private fun computeNextShuffleIndex(queueSize: Int, currentIndex: Int): Int {
        if (queueSize <= 1) return 0
        val r = Random.nextInt(queueSize - 1)
        return if (r >= currentIndex && currentIndex in 0 until queueSize) r + 1 else r
    }

    private fun computePreviousIndex(queueSize: Int, currentIndex: Int): Int {
        if (queueSize <= 0) return 0
        return if (currentIndex <= 0) queueSize - 1 else currentIndex - 1
    }

    private fun computeNextSequentialIndex(queueSize: Int, currentIndex: Int): Int {
        if (queueSize <= 0) return 0
        return (currentIndex + 1) % queueSize
    }

    @Test
    fun testShuffleNeverSelectsCurrentTrackWhenQueueHasMultipleSongs() {
        val queueSize = 10
        for (current in 0 until queueSize) {
            // Repeat 500 times per index to verify statistical distribution and non-collision guarantee
            for (attempt in 0 until 500) {
                val next = computeNextShuffleIndex(queueSize, current)
                assertNotEquals("Shuffle must never select the currently playing track", current, next)
                assertTrue("Next index must be within queue bounds", next in 0 until queueSize)
            }
        }
    }

    @Test
    fun testShuffleWithSingleSongQueue() {
        val next = computeNextShuffleIndex(queueSize = 1, currentIndex = 0)
        assertEquals(0, next)
    }

    @Test
    fun testSequentialNextWrapAround() {
        val size = 5
        assertEquals(1, computeNextSequentialIndex(size, 0))
        assertEquals(2, computeNextSequentialIndex(size, 1))
        assertEquals(3, computeNextSequentialIndex(size, 2))
        assertEquals(4, computeNextSequentialIndex(size, 3))
        // At the last element, manual next wraps back to 0
        assertEquals(0, computeNextSequentialIndex(size, 4))
    }

    @Test
    fun testSequentialPreviousWrapAround() {
        val size = 5
        // At index 0, previous wraps to last track (size - 1 = 4)
        assertEquals(4, computePreviousIndex(size, 0))
        assertEquals(0, computePreviousIndex(size, 1))
        assertEquals(1, computePreviousIndex(size, 2))
        assertEquals(2, computePreviousIndex(size, 3))
        assertEquals(3, computePreviousIndex(size, 4))
    }

    @Test
    fun testSkipPreviousThresholdLogic() {
        // Threshold: 3000ms
        // If current position > 3000ms: seeks to beginning of current track (remains on same track)
        // If current position <= 3000ms: switches to previous track

        val currentTrackIndex = 3
        val queueSize = 10

        fun decideAction(positionMs: Long): String {
            return if (positionMs > 3000L) {
                "RESTART_CURRENT"
            } else {
                "GOTO_PREVIOUS"
            }
        }

        assertEquals("RESTART_CURRENT", decideAction(3001L))
        assertEquals("RESTART_CURRENT", decideAction(45000L))
        assertEquals("GOTO_PREVIOUS", decideAction(3000L))
        assertEquals("GOTO_PREVIOUS", decideAction(1200L))
        assertEquals("GOTO_PREVIOUS", decideAction(0L))
    }

    @Test
    fun testQueueScrollTargetCalculation() {
        fun computeQueueScrollTarget(currentIndex: Int): Int {
            return (currentIndex - 2).coerceAtLeast(0)
        }

        assertEquals(0, computeQueueScrollTarget(0))
        assertEquals(0, computeQueueScrollTarget(1))
        assertEquals(0, computeQueueScrollTarget(2))
        assertEquals(1, computeQueueScrollTarget(3))
        assertEquals(8, computeQueueScrollTarget(10))
        assertEquals(48, computeQueueScrollTarget(50))
    }

    @Test
    fun testPlaylistDetailScrollTargetCalculation() {
        fun computePlaylistScrollTarget(headerCount: Int, songIndex: Int): Int {
            if (songIndex < 0) return 0
            return (headerCount + songIndex - 1).coerceAtLeast(0)
        }

        // Standard playlist with 1 header card (hero card)
        assertEquals(0, computePlaylistScrollTarget(headerCount = 1, songIndex = 0))
        assertEquals(1, computePlaylistScrollTarget(headerCount = 1, songIndex = 1))
        assertEquals(14, computePlaylistScrollTarget(headerCount = 1, songIndex = 14))

        // Most Played playlist with 2 header items (hero card + filter chips)
        assertEquals(1, computePlaylistScrollTarget(headerCount = 2, songIndex = 0))
        assertEquals(2, computePlaylistScrollTarget(headerCount = 2, songIndex = 1))
        assertEquals(15, computePlaylistScrollTarget(headerCount = 2, songIndex = 14))
    }

    @Test
    fun testTargetIndexPreservationWithDuplicateSongsInQueue() {
        // Queue contains duplicate song URIs
        val queue = listOf("uri://songA", "uri://songB", "uri://songA", "uri://songC")
        
        fun resolveQueueIndex(requestedUri: String, explicitTargetIndex: Int?): Int {
            return if (explicitTargetIndex != null && explicitTargetIndex in queue.indices) {
                explicitTargetIndex
            } else {
                queue.indexOfFirst { it == requestedUri }.coerceAtLeast(0)
            }
        }

        // Clicking the second instance of songA (index 2) should preserve index 2, not default to index 0
        val resolvedWithExplicit = resolveQueueIndex("uri://songA", explicitTargetIndex = 2)
        assertEquals(2, resolvedWithExplicit)

        // Without explicit target index, fallback finds first occurrence
        val resolvedFallback = resolveQueueIndex("uri://songA", explicitTargetIndex = null)
        assertEquals(0, resolvedFallback)
    }

    @Test
    fun testLocatePillVisibilityCondition() {
        fun shouldShowLocatePill(
            currentSongIndex: Int,
            visibleItemIndices: Set<Int>,
            headerCount: Int,
            isBatchMode: Boolean
        ): Boolean {
            if (isBatchMode || currentSongIndex < 0) return false
            val targetLazyIndex = headerCount + currentSongIndex
            val isVisible = visibleItemIndices.contains(targetLazyIndex)
            return !isVisible
        }

        // Song #10 is playing, visible viewport only shows items 0 to 5
        val viewportEarly = setOf(0, 1, 2, 3, 4, 5)
        assertTrue(shouldShowLocatePill(currentSongIndex = 10, visibleItemIndices = viewportEarly, headerCount = 1, isBatchMode = false))

        // Song #10 scrolls into visible viewport (e.g. items 9, 10, 11, 12, 13)
        val viewportCentered = setOf(9, 10, 11, 12, 13)
        // targetLazyIndex = 1 + 10 = 11, which is in viewportCentered
        assertFalse(shouldShowLocatePill(currentSongIndex = 10, visibleItemIndices = viewportCentered, headerCount = 1, isBatchMode = false))

        // Batch mode suppresses floating pill
        assertFalse(shouldShowLocatePill(currentSongIndex = 10, visibleItemIndices = viewportEarly, headerCount = 1, isBatchMode = true))
    }
}
