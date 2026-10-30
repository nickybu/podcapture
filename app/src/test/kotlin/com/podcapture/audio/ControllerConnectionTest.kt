package com.podcapture.audio

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ControllerConnectionTest {

    /** Stands in for MediaController: records the commands applied to it. */
    private class FakeController {
        val log = mutableListOf<String>()
    }

    @Test
    fun `queued commands run in the order they were issued`() {
        val queue = PendingCommandQueue<FakeController>()
        queue.enqueue { it.log += "load" }
        queue.enqueue { it.log += "play" }
        queue.enqueue { it.log += "seek" }

        val controller = FakeController()
        val ran = queue.drainTo(controller)

        assertThat(ran).isEqualTo(3)
        assertThat(controller.log).containsExactly("load", "play", "seek").inOrder()
    }

    @Test
    fun `draining empties the queue so commands run once`() {
        val queue = PendingCommandQueue<FakeController>()
        queue.enqueue { it.log += "play" }
        val controller = FakeController()

        queue.drainTo(controller)
        val secondRun = queue.drainTo(controller)

        assertThat(secondRun).isEqualTo(0)
        assertThat(queue.isEmpty()).isTrue()
        assertThat(controller.log).containsExactly("play")
    }

    @Test
    fun `commands enqueued while draining also run, after the current ones`() {
        // seekRelative's command calls seekTo, which may enqueue again.
        val queue = PendingCommandQueue<FakeController>()
        queue.enqueue { controller ->
            controller.log += "seekRelative"
            queue.enqueue { it.log += "seekTo" }
        }
        queue.enqueue { it.log += "play" }

        val controller = FakeController()
        val ran = queue.drainTo(controller)

        assertThat(ran).isEqualTo(3)
        assertThat(controller.log).containsExactly("seekRelative", "play", "seekTo").inOrder()
    }

    @Test
    fun `clear drops pending commands`() {
        val queue = PendingCommandQueue<FakeController>()
        queue.enqueue { it.log += "load" }
        queue.enqueue { it.log += "play" }
        assertThat(queue.size).isEqualTo(2)

        queue.clear()
        val controller = FakeController()
        queue.drainTo(controller)

        assertThat(queue.size).isEqualTo(0)
        assertThat(controller.log).isEmpty()
    }

    @Test
    fun `connected controller runs commands immediately`() {
        assertThat(connectAction(controllerConnected = true, connectInFlight = false))
            .isEqualTo(ConnectAction.RUN_NOW)
    }

    @Test
    fun `no controller and no attempt in flight starts connecting`() {
        // The state after PlaybackService was destroyed and onDisconnected dropped the controller.
        assertThat(connectAction(controllerConnected = false, connectInFlight = false))
            .isEqualTo(ConnectAction.QUEUE_AND_CONNECT)
    }

    @Test
    fun `an attempt in flight is reused rather than starting another`() {
        assertThat(connectAction(controllerConnected = false, connectInFlight = true))
            .isEqualTo(ConnectAction.QUEUE_AND_WAIT)
    }

    @Test
    fun `losing the session resets to idle with nothing loaded`() {
        val playing = PlaybackState(
            playerState = PlayerState.PAUSED,
            currentPositionMs = 125_000L,
            durationMs = 3_600_000L,
            playbackSpeed = 1.5f,
            errorMessage = "stale"
        )

        val state = disconnectedPlaybackState(playing)

        assertThat(state.playerState).isEqualTo(PlayerState.IDLE)
        assertThat(state.currentPositionMs).isEqualTo(0L)
        assertThat(state.durationMs).isEqualTo(0L)
        assertThat(state.errorMessage).isNull()
        // The speed is the user's preference and carries over to the next load.
        assertThat(state.playbackSpeed).isEqualTo(1.5f)
    }
}
