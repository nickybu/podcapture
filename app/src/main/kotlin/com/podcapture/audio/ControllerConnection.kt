package com.podcapture.audio

/**
 * Commands issued while the MediaController is not connected, replayed in the order they
 * were issued once a connection exists.
 *
 * Not thread-safe: enqueue and drain from the controller's application thread (main).
 */
internal class PendingCommandQueue<T> {
    private val commands = ArrayDeque<(T) -> Unit>()

    val size: Int get() = commands.size

    fun isEmpty(): Boolean = commands.isEmpty()

    fun enqueue(command: (T) -> Unit) {
        commands.addLast(command)
    }

    /**
     * Runs and removes every queued command in FIFO order against [target], including any
     * command enqueued by another command while draining. Returns how many commands ran.
     */
    fun drainTo(target: T): Int {
        var ran = 0
        while (true) {
            val command = commands.removeFirstOrNull() ?: break
            command(target)
            ran++
        }
        return ran
    }

    fun clear() {
        commands.clear()
    }
}

/** What to do with a player command, given the state of the controller connection. */
internal enum class ConnectAction {
    /** The controller is connected: run the command now. */
    RUN_NOW,

    /** No connection and none in progress: queue the command and start connecting. */
    QUEUE_AND_CONNECT,

    /** A connection attempt is already in flight: queue the command and wait for it. */
    QUEUE_AND_WAIT
}

internal fun connectAction(controllerConnected: Boolean, connectInFlight: Boolean): ConnectAction =
    when {
        controllerConnected -> ConnectAction.RUN_NOW
        connectInFlight -> ConnectAction.QUEUE_AND_WAIT
        else -> ConnectAction.QUEUE_AND_CONNECT
    }

/**
 * The state to publish once the session is gone (PlaybackService destroyed): nothing is
 * loaded any more, so the position and duration are reset. The speed is kept because it is
 * the user's preference, not a property of the lost media.
 */
internal fun disconnectedPlaybackState(state: PlaybackState): PlaybackState =
    state.copy(
        playerState = PlayerState.IDLE,
        currentPositionMs = 0L,
        durationMs = 0L,
        errorMessage = null
    )
