package com.podcapture.ui.settings

import com.google.common.truth.Truth.assertThat
import com.podcapture.data.repository.PodcastRepository
import com.podcapture.data.settings.PodcastIndexCredentials
import com.podcapture.transcription.ModelState
import org.junit.Test

class SettingsUiStateTest {

    private val stored = StoredSettings(
        captureWindowSeconds = 45,
        captureWindowStep = 10,
        skipIntervalSeconds = 25,
        headsetCaptureEnabled = false,
        headsetCaptureToneEnabled = true,
        obsidianVaultUri = "content://tree/primary%3AVault",
        obsidianDefaultTags = "inbox/",
        apiCallCount = 7,
        podcastIndexCredentials = PodcastIndexCredentials("key", "secret"),
        themeBackgroundColor = "#000000",
        themeAccent1Color = "#111111",
        themeAccent2Color = "#222222"
    )

    private fun map(
        stored: StoredSettings = this.stored,
        opml: OpmlState = OpmlState(),
        model: TranscriptionModelUiState = TranscriptionModelUiState(),
        lastPathSegment: (String) -> String? = { "primary:Vault" }
    ) = settingsUiState(stored, opml, model, lastPathSegment)

    @Test
    fun `copies every stored setting and marks loading done`() {
        val state = map()

        assertThat(state.captureWindowSeconds).isEqualTo(45)
        assertThat(state.captureWindowStep).isEqualTo(10)
        assertThat(state.skipIntervalSeconds).isEqualTo(25)
        assertThat(state.headsetCaptureEnabled).isFalse()
        assertThat(state.headsetCaptureToneEnabled).isTrue()
        assertThat(state.obsidianVaultUri).isEqualTo("content://tree/primary%3AVault")
        assertThat(state.obsidianDefaultTags).isEqualTo("inbox/")
        assertThat(state.apiCallCount).isEqualTo(7)
        assertThat(state.podcastIndexApiKey).isEqualTo("key")
        assertThat(state.podcastIndexApiSecret).isEqualTo("secret")
        assertThat(state.themeBackgroundColor).isEqualTo("#000000")
        assertThat(state.themeAccent1Color).isEqualTo("#111111")
        assertThat(state.themeAccent2Color).isEqualTo("#222222")
        assertThat(state.isLoading).isFalse()
    }

    @Test
    fun `carries OPML and model state through`() {
        val result = PodcastRepository.ImportResult(imported = 1, skipped = 0, failed = 2, errors = listOf("x"))
        val model = TranscriptionModelUiState(state = ModelState.Ready, sizeOnDiskBytes = 42)

        val state = map(
            opml = OpmlState(
                isImporting = true,
                importProgress = ImportProgress(1, 3, "Pod"),
                importResult = result,
                importError = "bad",
                exportError = "worse"
            ),
            model = model
        )

        assertThat(state.isImporting).isTrue()
        assertThat(state.importProgress).isEqualTo(ImportProgress(1, 3, "Pod"))
        assertThat(state.importResult).isEqualTo(result)
        assertThat(state.importError).isEqualTo("bad")
        assertThat(state.exportError).isEqualTo("worse")
        assertThat(state.transcriptionModel).isEqualTo(model)
    }

    @Test
    fun `vault display name strips the primary volume prefix`() {
        assertThat(vaultDisplayName("content://x") { "primary:Documents/Vault" })
            .isEqualTo("Documents/Vault")
    }

    @Test
    fun `vault display name falls back when the uri has no path segment`() {
        assertThat(vaultDisplayName("content://x") { null }).isEqualTo("Selected")
    }

    @Test
    fun `vault display name is empty when no vault is set`() {
        var parsed = false
        assertThat(vaultDisplayName("  ") { parsed = true; "unused" }).isEmpty()
        assertThat(parsed).isFalse()
    }

    @Test
    fun `formats megabytes rounded to whole SI megabytes`() {
        assertThat(formatMegabytes(103_627_191)).isEqualTo("104 MB")
        assertThat(formatMegabytes(12_400_000)).isEqualTo("12 MB")
        assertThat(formatMegabytes(1_000_000)).isEqualTo("1 MB")
        assertThat(formatMegabytes(400_000)).isEqualTo("<1 MB")
        assertThat(formatMegabytes(0)).isEqualTo("<1 MB")
    }

    @Test
    fun `import summary counts imported and skipped podcasts`() {
        assertThat(importSummary(PodcastRepository.ImportResult(12, 3, 0, emptyList())))
            .isEqualTo("Imported 12 podcasts, 3 already bookmarked")
        assertThat(importSummary(PodcastRepository.ImportResult(1, 0, 0, emptyList())))
            .isEqualTo("Imported 1 podcast")
    }
}
