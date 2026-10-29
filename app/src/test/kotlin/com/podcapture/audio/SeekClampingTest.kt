package com.podcapture.audio

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class SeekClampingTest {

    @Test
    fun `unknown duration keeps the requested position`() {
        assertEquals(125_000L, clampSeekPosition(125_000L, C.TIME_UNSET))
    }

    @Test
    fun `unknown duration still clamps negative positions to zero`() {
        assertEquals(0L, clampSeekPosition(-5_000L, C.TIME_UNSET))
    }

    @Test
    fun `skip forward while preparing does not reset to zero`() {
        // Resume position 125s, user taps +10s before the duration is known.
        val current = 125_000L
        assertEquals(135_000L, clampSeekPosition(current + 10_000L, C.TIME_UNSET))
    }

    @Test
    fun `rewind while preparing does not reset to zero`() {
        val current = 125_000L
        assertEquals(115_000L, clampSeekPosition(current - 10_000L, C.TIME_UNSET))
    }

    @Test
    fun `zero duration is treated as unknown`() {
        assertEquals(42_000L, clampSeekPosition(42_000L, 0L))
    }

    @Test
    fun `known duration clamps to the end`() {
        assertEquals(60_000L, clampSeekPosition(70_000L, 60_000L))
    }

    @Test
    fun `known duration clamps to the start`() {
        assertEquals(0L, clampSeekPosition(-1L, 60_000L))
    }

    @Test
    fun `known duration keeps in-range positions`() {
        assertEquals(30_000L, clampSeekPosition(30_000L, 60_000L))
    }
}
