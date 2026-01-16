package com.podcapture.ui.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.audio.AudioPlayerService
import com.podcapture.audio.PlaybackState
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.Capture
import com.podcapture.data.repository.AudioFileRepository
import com.podcapture.data.repository.CaptureRepository
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.transcription.ModelState
import com.podcapture.transcription.TranscriptionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val audioFile: AudioFile? = null,
    val playbackState: PlaybackState = PlaybackState(),
    val captures: List<Capture> = emptyList(),
    val captureWindowSeconds: Int = 30,
    val skipIntervalSeconds: Int = 10,
    val isCapturing: Boolean = false,
    val captureProgress: String? = null,
    val captureSuccess: String? = null,
    val captureError: String? = null,
    val modelState: ModelState = ModelState.NotDownloaded,
    val activeCapture: Capture? = null,
    val activeCaptureIndex: Int = 0,
    val isLoading: Boolean = true
)

class PlayerViewModel(
    private val audioFileId: String,
    private val audioFileRepository: AudioFileRepository,
    private val captureRepository: CaptureRepository,
    private val audioPlayerService: AudioPlayerService,
    private val settingsDataStore: SettingsDataStore,
    private val transcriptionService: TranscriptionService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Track which captures have been shown to avoid re-showing
    private val shownCaptureIds = mutableSetOf<String>()

    init {
        loadAudioFile()
        observePlaybackState()
        observeCaptures()
        observeSettings()
        observeModelState()
    }

    private fun loadAudioFile() {
        viewModelScope.launch {
            audioFileRepository.getFileById(audioFileId).collect { audioFile ->
                audioFile?.let {
                    val isFirstLoad = _uiState.value.audioFile == null
                    _uiState.value = _uiState.value.copy(
                        audioFile = it,
                        isLoading = false
                    )

                    if (isFirstLoad) {
                        val uri = Uri.parse(it.filePath)

                        // Check if this audio is already loaded in the player
                        if (audioPlayerService.isLoadedUri(uri)) {
                            // Audio already loaded - don't reload, just keep current position
                            // The playback state will sync via observePlaybackState()
                        } else {
                            // Load new audio into player with MIME type hint and title for notification
                            val mimeType = getMimeTypeForFormat(it.format)
                            audioPlayerService.loadAudio(uri, audioFileId, mimeType, it.name)

                            // Seek to last saved position if resuming
                            if (it.lastPositionMs > 0) {
                                audioPlayerService.seekTo(it.lastPositionMs)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun observePlaybackState() {
        viewModelScope.launch {
            audioPlayerService.playbackState.collect { playbackState ->
                _uiState.value = _uiState.value.copy(playbackState = playbackState)

                // Check if playhead is within any capture's window
                checkForActiveCapturePopup(playbackState.currentPositionMs)
            }
        }
    }

    private fun checkForActiveCapturePopup(currentPositionMs: Long) {
        val captures = _uiState.value.captures
        val activeCapture = _uiState.value.activeCapture

        // Reset shown status for captures we've moved before (allows re-showing on replay)
        captures.forEach { capture ->
            if (currentPositionMs < capture.windowStartMs && capture.id in shownCaptureIds) {
                shownCaptureIds.remove(capture.id)
            }
        }

        // Auto-hide popup if we've moved outside the capture's window
        if (activeCapture != null) {
            val isOutsideWindow = currentPositionMs < activeCapture.windowStartMs ||
                                  currentPositionMs > activeCapture.windowEndMs
            if (isOutsideWindow) {
                _uiState.value = _uiState.value.copy(activeCapture = null)
            }
            return // Don't show new popup while one is active
        }

        if (captures.isEmpty()) return

        // Find capture whose window contains the current position
        val captureInWindow = captures.find { capture ->
            currentPositionMs >= capture.windowStartMs &&
            currentPositionMs <= capture.windowEndMs &&
            capture.id !in shownCaptureIds
        }

        if (captureInWindow != null) {
            val index = captures.indexOf(captureInWindow) + 1
            _uiState.value = _uiState.value.copy(
                activeCapture = captureInWindow,
                activeCaptureIndex = index
            )
            shownCaptureIds.add(captureInWindow.id)
        }
    }

    fun dismissCapturePopup() {
        _uiState.value = _uiState.value.copy(activeCapture = null)
    }

    private fun observeCaptures() {
        viewModelScope.launch {
            captureRepository.getCapturesForFile(audioFileId).collect { captures ->
                _uiState.value = _uiState.value.copy(captures = captures)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsDataStore.captureWindowSeconds.collect { seconds ->
                _uiState.value = _uiState.value.copy(captureWindowSeconds = seconds)
            }
        }
        viewModelScope.launch {
            settingsDataStore.skipIntervalSeconds.collect { seconds ->
                _uiState.value = _uiState.value.copy(skipIntervalSeconds = seconds)
            }
        }
    }

    private fun observeModelState() {
        viewModelScope.launch {
            transcriptionService.modelState.collect { state ->
                _uiState.value = _uiState.value.copy(modelState = state)
            }
        }
    }

    fun onPlayPause() {
        audioPlayerService.togglePlayPause()
    }

    fun onSeekTo(positionMs: Long) {
        audioPlayerService.seekTo(positionMs)
    }

    fun onRewind() {
        val intervalMs = _uiState.value.skipIntervalSeconds * 1000L
        audioPlayerService.rewind(intervalMs)
    }

    fun onFastForward() {
        val intervalMs = _uiState.value.skipIntervalSeconds * 1000L
        audioPlayerService.fastForward(intervalMs)
    }

    fun onSpeedChange(speed: Float) {
        audioPlayerService.setPlaybackSpeed(speed)
    }

    fun onCapture() {
        val audioFile = _uiState.value.audioFile ?: return
        val currentPosition = audioPlayerService.getCurrentPosition()
        val duration = audioPlayerService.getDuration()
        val windowMs = _uiState.value.captureWindowSeconds * 1000L

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isCapturing = true,
                captureError = null,
                captureProgress = "Preparing..."
            )

            try {
                val windowStart = (currentPosition - windowMs).coerceAtLeast(0L)
                val windowEnd = (currentPosition + windowMs).coerceAtMost(duration)

                // Ensure model is downloaded
                if (!transcriptionService.isModelReady()) {
                    _uiState.value = _uiState.value.copy(
                        captureProgress = "Downloading speech model..."
                    )
                    val downloaded = transcriptionService.ensureModelReady()
                    if (!downloaded) {
                        throw IllegalStateException("Failed to download speech recognition model")
                    }
                }

                // Transcribe the audio segment
                _uiState.value = _uiState.value.copy(
                    captureProgress = "Transcribing audio..."
                )

                val transcription = transcriptionService.transcribe(
                    audioUri = Uri.parse(audioFile.filePath),
                    startMs = windowStart,
                    endMs = windowEnd
                )

                // Create capture with real transcription
                val capture = captureRepository.createCapture(
                    audioFile = audioFile,
                    timestampMs = currentPosition,
                    windowStartMs = windowStart,
                    windowEndMs = windowEnd,
                    transcription = transcription
                )

                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    captureSuccess = "Capture saved at ${formatDuration(currentPosition)}"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCapturing = false,
                    captureProgress = null,
                    captureError = "Capture failed: ${e.message}"
                )
            }
        }
    }

    fun onCaptureSuccessDismissed() {
        _uiState.value = _uiState.value.copy(captureSuccess = null)
    }

    fun onCaptureErrorDismissed() {
        _uiState.value = _uiState.value.copy(captureError = null)
    }

    fun savePlaybackPosition() {
        viewModelScope.launch {
            val position = audioPlayerService.getCurrentPosition()
            audioFileRepository.updatePlaybackState(audioFileId, position)
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Save position when leaving player
        viewModelScope.launch {
            val position = audioPlayerService.getCurrentPosition()
            audioFileRepository.updatePosition(audioFileId, position)
        }
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

    private fun getMimeTypeForFormat(format: String): String? {
        return when (format.lowercase()) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "wma" -> "audio/x-ms-wma"
            "webm" -> "audio/webm"
            else -> null
        }
    }
}
