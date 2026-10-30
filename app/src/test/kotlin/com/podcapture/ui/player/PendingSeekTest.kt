package com.podcapture.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingSeekTest {

    @Test
    fun `requested position is returned exactly once`() {
        val pendingSeek = PendingSeek(95_000L)

        assertEquals(95_000L, pendingSeek.consume())
        // Re-entry (back from Viewer, rotation, a second load after download) must not
        // re-apply the deep-link timestamp.
        assertNull(pendingSeek.consume())
        assertNull(pendingSeek.consume())
    }

    @Test
    fun `no request yields null so the resume position is used`() {
        val pendingSeek = PendingSeek(null)

        assertNull(pendingSeek.consume())
        val resumePositionMs = 42_000L
        assertEquals(resumePositionMs, pendingSeek.consume() ?: resumePositionMs)
    }

    @Test
    fun `request takes precedence over the resume position`() {
        val pendingSeek = PendingSeek(10_000L)
        val resumePositionMs = 600_000L

        assertEquals(10_000L, pendingSeek.consume() ?: resumePositionMs)
        assertEquals(resumePositionMs, pendingSeek.consume() ?: resumePositionMs)
    }

    @Test
    fun `zero is a valid request`() {
        assertEquals(0L, PendingSeek(0L).consume())
    }

    @Test
    fun `negative request is clamped to zero`() {
        assertEquals(0L, PendingSeek(-1L).consume())
    }
}
