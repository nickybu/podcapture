package com.podcapture.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.settings.PodcastIndexCredentials
import com.podcapture.data.settings.SettingsDataStore
import com.podcapture.transcription.ModelState
import com.podcapture.transcription.WhisperModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext

data class SettingsUiState(
    val captureWindowSeconds: Int = 30,
    val captureWindowStep: Int = 5,
    val skipIntervalSeconds: Int = 10,
    val headsetCaptureEnabled: Boolean = true,
    val headsetCaptureToneEnabled: Boolean = true,
    val obsidianVaultUri: String = "",
    val obsidianVaultDisplayName: String = "",
    val obsidianDefaultTags: String = "inbox/, resources/references/podcasts",
    val apiCallCount: Int = 0,
    val podcastIndexApiKey: String = "",
    val podcastIndexApiSecret: String = "",
    val themeBackgroundColor: String = "#13293D",
    val themeAccent1Color: String = "#2A628F",
    val themeAccent2Color: String = "#3E92CC",
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val importResult: PodcastRepository.ImportResult? = null,
    val importError: String? = null,
    val exportError: String? = null,
    val transcriptionModel: TranscriptionModelUiState = TranscriptionModelUiState()
)

data class ImportProgress(
    val current: Int,
    val total: Int,
    val currentPodcast: String
)

/** What the Transcription card in Settings shows about the speech model. */
data class TranscriptionModelUiState(
    val state: ModelState = ModelState.NotDownloaded,
    /** Bytes on disk; only known (non-null) while the model is [ModelState.Ready]. */
    val sizeOnDiskBytes: Long? = null,
    val name: String = WhisperModelManager.MODEL_DISPLAY_NAME,
    val downloadSizeBytes: Long = WhisperModelManager.MODEL_DOWNLOAD_SIZE_BYTES
)

/** Every persisted setting the screen shows, read from [SettingsDataStore] in one flow. */
internal data class StoredSettings(
    val captureWindowSeconds: Int,
    val captureWindowStep: Int,
    val skipIntervalSeconds: Int,
    val headsetCaptureEnabled: Boolean,
    val headsetCaptureToneEnabled: Boolean,
    val obsidianVaultUri: String,
    val obsidianDefaultTags: String,
    val apiCallCount: Int,
    val podcastIndexCredentials: PodcastIndexCredentials,
    val themeBackgroundColor: String,
    val themeAccent1Color: String,
    val themeAccent2Color: String
)

/** Screen-local state that is not persisted: OPML import/export progress and failures. */
internal data class OpmlState(
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val importResult: PodcastRepository.ImportResult? = null,
    val importError: String? = null,
    val exportError: String? = null
)

/**
 * Builds the screen state. [vaultLastPathSegment] is `Uri.parse(it).lastPathSegment`, passed in
 * so this stays a pure function that unit tests can run without the Android framework.
 */
internal fun settingsUiState(
    stored: StoredSettings,
    opml: OpmlState,
    model: TranscriptionModelUiState,
    vaultLastPathSegment: (String) -> String?
): SettingsUiState = SettingsUiState(
    captureWindowSeconds = stored.captureWindowSeconds,
    captureWindowStep = stored.captureWindowStep,
    skipIntervalSeconds = stored.skipIntervalSeconds,
    headsetCaptureEnabled = stored.headsetCaptureEnabled,
    headsetCaptureToneEnabled = stored.headsetCaptureToneEnabled,
    obsidianVaultUri = stored.obsidianVaultUri,
    obsidianVaultDisplayName = vaultDisplayName(stored.obsidianVaultUri, vaultLastPathSegment),
    obsidianDefaultTags = stored.obsidianDefaultTags,
    apiCallCount = stored.apiCallCount,
    podcastIndexApiKey = stored.podcastIndexCredentials.apiKey,
    podcastIndexApiSecret = stored.podcastIndexCredentials.apiSecret,
    themeBackgroundColor = stored.themeBackgroundColor,
    themeAccent1Color = stored.themeAccent1Color,
    themeAccent2Color = stored.themeAccent2Color,
    isLoading = false,
    isImporting = opml.isImporting,
    importProgress = opml.importProgress,
    importResult = opml.importResult,
    importError = opml.importError,
    exportError = opml.exportError,
    transcriptionModel = model
)

/** "primary:Documents/Vault" -> "Documents/Vault"; "" when no vault is set. */
internal fun vaultDisplayName(uri: String, lastPathSegment: (String) -> String?): String =
    if (uri.isNotBlank()) lastPathSegment(uri)?.replace("primary:", "") ?: "Selected" else ""

/** Whole megabytes (SI, as download sizes are usually quoted), e.g. "104 MB". */
internal fun formatMegabytes(bytes: Long): String {
    val mb = (bytes + 500_000) / 1_000_000
    return if (mb < 1) "<1 MB" else "$mb MB"
}

class SettingsViewModel(
    private val settingsDataStore: SettingsDataStore,
    private val podcastRepository: PodcastRepository,
    // Defaulted from Koin so the existing `SettingsViewModel(get(), get())` registration keeps
    // working; pass it explicitly once the DI module is updated.
    private val modelManager: WhisperModelManager = GlobalContext.get().get()
) : ViewModel() {

    private val opmlState = MutableStateFlow(OpmlState())

    // One-shot success messages for the screen's snackbar. A channel (not a SharedFlow) so a
    // message sent while the screen is between collectors (e.g. rotating) is kept, not dropped.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private val storedSettings: Flow<StoredSettings> = combine(
        combine(
            settingsDataStore.captureWindowSeconds,
            settingsDataStore.captureWindowStep,
            settingsDataStore.skipIntervalSeconds,
            settingsDataStore.headsetCaptureEnabled,
            settingsDataStore.headsetCaptureToneEnabled,
            ::CaptureSettings
        ),
        combine(
            settingsDataStore.obsidianVaultUri,
            settingsDataStore.obsidianDefaultTags,
            settingsDataStore.apiCallCount,
            settingsDataStore.podcastIndexCredentials,
            ::IntegrationSettings
        ),
        combine(
            settingsDataStore.themeBackgroundColor,
            settingsDataStore.themeAccent1Color,
            settingsDataStore.themeAccent2Color,
            ::ThemeSettings
        )
    ) { capture, integration, theme ->
        StoredSettings(
            captureWindowSeconds = capture.windowSeconds,
            captureWindowStep = capture.windowStep,
            skipIntervalSeconds = capture.skipIntervalSeconds,
            headsetCaptureEnabled = capture.headsetEnabled,
            headsetCaptureToneEnabled = capture.headsetToneEnabled,
            obsidianVaultUri = integration.vaultUri,
            obsidianDefaultTags = integration.defaultTags,
            apiCallCount = integration.apiCallCount,
            podcastIndexCredentials = integration.credentials,
            themeBackgroundColor = theme.background,
            themeAccent1Color = theme.accent1,
            themeAccent2Color = theme.accent2
        )
    }

    private val transcriptionModel: Flow<TranscriptionModelUiState> =
        modelManager.modelState.map { state ->
            val size = if (state is ModelState.Ready) {
                withContext(Dispatchers.IO) { modelManager.modelSizeOnDiskBytes() }
            } else {
                null
            }
            TranscriptionModelUiState(state = state, sizeOnDiskBytes = size)
        }

    val uiState: StateFlow<SettingsUiState> =
        combine(storedSettings, opmlState, transcriptionModel) { stored, opml, model ->
            settingsUiState(stored, opml, model) { Uri.parse(it).lastPathSegment }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setCaptureWindowSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowSeconds(seconds)
        }
    }

    fun setCaptureWindowStep(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setCaptureWindowStep(seconds)
        }
    }

    fun setSkipIntervalSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsDataStore.setSkipIntervalSeconds(seconds)
        }
    }

    fun setHeadsetCaptureEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setHeadsetCaptureEnabled(enabled)
        }
    }

    fun setHeadsetCaptureToneEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsDataStore.setHeadsetCaptureToneEnabled(enabled)
        }
    }

    fun setObsidianVaultUri(uri: String) {
        viewModelScope.launch {
            settingsDataStore.setObsidianVaultUri(uri)
        }
    }

    fun setObsidianDefaultTags(tags: String) {
        viewModelScope.launch {
            settingsDataStore.setObsidianDefaultTags(tags)
        }
    }

    fun setPodcastIndexCredentials(apiKey: String, apiSecret: String) {
        viewModelScope.launch {
            settingsDataStore.setPodcastIndexCredentials(apiKey, apiSecret)
        }
    }

    fun setThemeBackgroundColor(color: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeBackgroundColor(color)
        }
    }

    fun setThemeAccent1Color(color: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeAccent1Color(color)
        }
    }

    fun setThemeAccent2Color(color: String) {
        viewModelScope.launch {
            settingsDataStore.setThemeAccent2Color(color)
        }
    }

    fun resetThemeColors() {
        viewModelScope.launch {
            settingsDataStore.resetThemeColors()
        }
    }

    // ============ Transcription model ============

    /** Downloads the speech model now. The download outlives this screen if the user leaves. */
    fun downloadModel() {
        viewModelScope.launch {
            if (modelManager.ensureModelDownloaded()) {
                _messages.trySend("Speech model downloaded")
            }
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            if (modelManager.deleteModel()) {
                _messages.trySend("Speech model deleted")
            }
        }
    }

    // ============ OPML Import/Export ============

    fun importOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            opmlState.update {
                it.copy(isImporting = true, importProgress = null, importResult = null, importError = null)
            }

            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        podcastRepository.importFromOpml(inputStream) { current, total, podcast ->
                            opmlState.update {
                                it.copy(importProgress = ImportProgress(current, total, podcast))
                            }
                        }
                    } ?: Result.failure(Exception("Could not open file for reading"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }

            val importResult = result.getOrNull()
            // A clean import fits in a snackbar; keep the dialog for failures, which list errors.
            val summary = importResult?.takeIf { it.failed == 0 }?.let(::importSummary)
            opmlState.update {
                it.copy(
                    isImporting = false,
                    importProgress = null,
                    importResult = if (summary == null) importResult else null,
                    importError = if (result.isFailure) {
                        result.exceptionOrNull()?.message ?: "Import failed"
                    } else {
                        null
                    }
                )
            }
            summary?.let { _messages.trySend(it) }
        }
    }

    fun exportOpml(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        podcastRepository.exportToOpml(outputStream)
                    } ?: Result.failure(Exception("Could not open file for writing"))
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
            result
                .onSuccess { count -> _messages.trySend("Exported $count podcasts to OPML file") }
                .onFailure { e ->
                    opmlState.update { it.copy(exportError = e.message ?: "Export failed") }
                }
        }
    }

    fun clearImportResult() {
        opmlState.update { it.copy(importResult = null, importError = null) }
    }

    fun clearExportError() {
        opmlState.update { it.copy(exportError = null) }
    }

    private data class CaptureSettings(
        val windowSeconds: Int,
        val windowStep: Int,
        val skipIntervalSeconds: Int,
        val headsetEnabled: Boolean,
        val headsetToneEnabled: Boolean
    )

    private data class IntegrationSettings(
        val vaultUri: String,
        val defaultTags: String,
        val apiCallCount: Int,
        val credentials: PodcastIndexCredentials
    )

    private data class ThemeSettings(
        val background: String,
        val accent1: String,
        val accent2: String
    )
}

/** One-line summary of a successful OPML import, e.g. "Imported 12 podcasts, 3 already bookmarked". */
internal fun importSummary(result: PodcastRepository.ImportResult): String = buildString {
    append("Imported ${result.imported} ")
    append(if (result.imported == 1) "podcast" else "podcasts")
    if (result.skipped > 0) append(", ${result.skipped} already bookmarked")
}
