package com.podcapture.capture

import android.net.Uri
import com.podcapture.audio.AudioPlayerService
import com.podcapture.data.model.AudioFile
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.transcription.VoskModelManager
import com.podcapture.transcription.VoskTranscriptionService
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

class CaptureManager(
    private val audioPlayerService: AudioPlayerService,
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    private val settingsDataStore: SettingsDataStore,
    private val voskModelManager: VoskModelManager,
    private val transcriptionService: VoskTranscriptionService
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _captureState = MutableStateFlow(CaptureState())
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    fun capture() {
        val audioFileId = audioPlayerService.currentAudioFileId.value ?: return

        scope.launch {
            val audioFile = audioFileRepository.getFileByIdOnce(audioFileId) ?: return@launch
            val currentPosition = audioPlayerService.getCurrentPosition()
            val duration = audioPlayerService.getDuration()

            _captureState.value = _captureState.value.copy(
                isCapturing = true,
                captureProgress = "Preparing...",
                lastCaptureError = null
            )

            try {
                val windowSeconds = settingsDataStore.captureWindowSeconds.first()
                val windowMs = windowSeconds * 1000L
                val windowStart = (currentPosition - windowMs).coerceAtLeast(0L)
                val windowEnd = (currentPosition + windowMs).coerceAtMost(duration)

                // Ensure model is downloaded
                if (!voskModelManager.isModelReady()) {
                    _captureState.value = _captureState.value.copy(
                        captureProgress = "Downloading speech model..."
                    )
                    val downloaded = voskModelManager.ensureModelDownloaded()
                    if (!downloaded) {
                        throw IllegalStateException("Failed to download speech recognition model")
                    }
                }

                // Transcribe the audio segment
                _captureState.value = _captureState.value.copy(
                    captureProgress = "Transcribing audio..."
                )

                val transcription = transcriptionService.transcribe(
                    audioUri = Uri.parse(audioFile.filePath),
                    startMs = windowStart,
                    endMs = windowEnd
                )

                // Create capture
                captureRepository.createCapture(
                    audioFile = audioFile,
                    timestampMs = currentPosition,
                    windowStartMs = windowStart,
                    windowEndMs = windowEnd,
                    transcription = transcription
                )

                _captureState.value = _captureState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastCaptureSuccess = "Capture saved at ${formatDuration(currentPosition)}"
                )

            } catch (e: Exception) {
                _captureState.value = _captureState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    lastCaptureError = "Capture failed: ${e.message}"
                )
            }
        }
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
