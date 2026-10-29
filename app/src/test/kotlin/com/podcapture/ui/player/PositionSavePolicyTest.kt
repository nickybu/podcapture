package com.podcapture.ui.player

import com.podcapture.audio.PlayerState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PositionSavePolicyTest {

    @Test
    fun `playing saves once the interval has elapsed`() {
        assertTrue(shouldSavePosition(PlayerState.PLAYING, PlayerState.PLAYING, 13_000L, 10_000L))
    }

    @Test
    fun `playing does not save before the interval has elapsed`() {
        assertFalse(shouldSavePosition(PlayerState.PLAYING, PlayerState.PLAYING, 12_900L, 10_000L))
    }

    @Test
    fun `playing saves a backward skip`() {
        assertTrue(shouldSavePosition(PlayerState.PLAYING, PlayerState.PLAYING, 50_000L, 60_000L))
    }

    @Test
    fun `position zero is never saved`() {
        assertFalse(shouldSavePosition(PlayerState.PLAYING, PlayerState.PLAYING, 0L, 60_000L))
        assertFalse(shouldSavePosition(PlayerState.PAUSED, PlayerState.PLAYING, 0L, 60_000L))
    }

    @Test
    fun `pausing saves the current position`() {
        assertTrue(shouldSavePosition(PlayerState.PAUSED, PlayerState.PLAYING, 11_000L, 10_000L))
    }

    @Test
    fun `pausing does not rewrite an already saved position`() {
        assertFalse(shouldSavePosition(PlayerState.PAUSED, PlayerState.PLAYING, 10_000L, 10_000L))
    }

    @Test
    fun `seeking while paused does not save`() {
        assertFalse(shouldSavePosition(PlayerState.PAUSED, PlayerState.PAUSED, 90_000L, 10_000L))
    }

    @Test
    fun `loading and idle do not save`() {
        assertFalse(shouldSavePosition(PlayerState.LOADING, PlayerState.IDLE, 90_000L, 10_000L))
        assertFalse(shouldSavePosition(PlayerState.IDLE, null, 90_000L, 10_000L))
    }
}
