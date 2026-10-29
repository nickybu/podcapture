package com.podcapture.capture

import android.net.Uri
import com.podcapture.audio.AudioPlayerService
import com.podcapture.audio.PlayerState
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.transcription.TranscriptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class CaptureState(
    val isCapturing: Boolean = false,
    val captureProgress: String? = null,
    val lastCaptureSuccess: String? = null,
    val lastCaptureError: String? = null
)

private const val EPISODE_ID_PREFIX = "episode_"

private val CAPTURABLE_PLAYER_STATES = setOf(PlayerState.READY, PlayerState.PLAYING, PlayerState.PAUSED)

class CaptureManager(
    private val audioPlayerService: AudioPlayerService,
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    private val podcastRepository: PodcastRepository,
    private val settingsDataStore: SettingsDataStore,
    private val transcriptionService: TranscriptionService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _captureState = MutableStateFlow(CaptureState())
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    /**
     * Captures the window around the current playhead of whatever is loaded in the player —
     * either a local audio file or a downloaded podcast episode ("episode_{id}").
     *
     * Returns false without doing anything if nothing is loaded or a capture is already
     * running (transcription isn't safe to run concurrently). Also returns false, setting
     * [CaptureState.lastCaptureError], if the player isn't prepared (no known duration yet).
     * [onComplete] is invoked on the main thread with whether the capture was saved.
     */
    fun capture(onComplete: ((Boolean) -> Unit)? = null): Boolean {
        val audioFileId = audioPlayerService.currentAudioFileId.value ?: return false
        if (_captureState.value.isCapturing) return false

        // Snapshot the playhead now — transcription can take a while and playback keeps going.
        val currentPosition = audioPlayerService.getCurrentPosition()
        val duration = audioPlayerService.getDuration()

        // An unprepared player reports duration 0, which would give an empty window.
        val playerState = audioPlayerService.playbackState.value.playerState
        if (playerState !in CAPTURABLE_PLAYER_STATES || duration <= 0L) {
            _captureState.value = _captureState.value.copy(
                lastCaptureError = "Capture failed: player is not ready yet"
            )
            return false
        }

        _captureState.value = _captureState.value.copy(
            isCapturing = true,
            captureProgress = "Preparing...",
            lastCaptureError = null
        )

        scope.launch {
            try {
                val episodeId = audioFileId
                    .takeIf { it.startsWith(EPISODE_ID_PREFIX) }
                    ?.removePrefix(EPISODE_ID_PREFIX)
                    ?.toLongOrNull()

                val audioUri: Uri
                val saveCapture: suspend (Long, Long, String) -> Unit
                if (episodeId != null) {
                    val localPath = podcastRepository.getLocalEpisodePath(episodeId)
                        ?: throw IllegalStateException("Please download the episode first to use capture")
                    val episodeTitle = podcastRepository.getEpisodeById(episodeId)?.title ?: ""
                    audioUri = Uri.parse("file://$localPath")
                    saveCapture = { windowStart, windowEnd, transcription ->
                        captureRepository.createEpisodeCapture(
                            episodeId = episodeId,
                            episodeTitle = episodeTitle,
                            timestampMs = currentPosition,
                            windowStartMs = windowStart,
                            windowEndMs = windowEnd,
                            transcription = transcription
                        )
                    }
                } else {
                    val audioFile = audioFileRepository.getFileByIdOnce(audioFileId)
                        ?: throw IllegalStateException("Audio file not found")
                    audioUri = Uri.parse(audioFile.filePath)
                    saveCapture = { windowStart, windowEnd, transcription ->
                        captureRepository.createCapture(
                            audioFile = audioFile,
                            timestampMs = currentPosition,
                            windowStartMs = windowStart,
                            windowEndMs = windowEnd,
                            transcription = transcription
                        )
                    }
                }

                val windowSeconds = settingsDataStore.captureWindowSeconds.first()
                val windowMs = windowSeconds * 1000L
                val windowStart = (currentPosition - windowMs).coerceAtLeast(0L)
                val windowEnd = (currentPosition + windowMs).coerceAtMost(duration)

                // Ensure model is downloaded
                if (!transcriptionService.isModelReady()) {
                    _captureState.value = _captureState.value.copy(
                        captureProgress = "Downloading speech model..."
                    )
                    val downloaded = transcriptionService.ensureModelReady()
                    if (!downloaded) {
                        throw IllegalStateException("Failed to download speech recognition model")
                    }
                }

                // Transcribe the audio segment
                _captureState.value = _captureState.value.copy(
                    captureProgress = "Transcribing audio..."
                )

                val transcription = transcriptionService.transcribe(
                    audioUri = audioUri,
                    startMs = windowStart,
                    endMs = windowEnd
                )

                // Create capture
                saveCapture(windowStart, windowEnd, transcription)

                _captureState.value = _captureState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastCaptureSuccess = "Capture saved at ${formatDuration(currentPosition)}"
                )
                onComplete?.invoke(true)

            } catch (e: Exception) {
                _captureState.value = _captureState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastCaptureError = "Capture failed: ${e.message}"
                )
                onComplete?.invoke(false)
            }
        }
        return true
    }

    fun clearMessages() {
        _captureState.value = _captureState.value.copy(
            lastCaptureSuccess = null,
            lastCaptureError = null
        )
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
