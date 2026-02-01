package com.podcapture.data.repository

import android.content.Context
import com.podcapture.data.model.AudioFile
import com.podcapture.data.model.Capture
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MarkdownManager(
    private val context: Context
) {
    private val capturesDir: File
        get() = File(context.filesDir, "captures").also { it.mkdirs() }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    /**
     * Generates a unique filename based on the file path hash and name.
     * Format: {8-char-hash}_{sanitized_name}_captures.md
     */
    fun getMarkdownFileName(audioFile: AudioFile): String {
        val pathHash = audioFile.filePath.md5().take(8)
        val sanitizedName = audioFile.name
            .substringBeforeLast(".")  // Remove extension
            .lowercase()
            .replace(Regex("[^a-z0-9]"), "_")  // Replace non-alphanumeric with underscore
            .replace(Regex("_+"), "_")  // Collapse multiple underscores
            .trim('_')
            .take(50)  // Limit length

        return "${pathHash}_${sanitizedName}_captures.md"
    }

    fun getMarkdownFilePath(audioFile: AudioFile): String {
        return File(capturesDir, getMarkdownFileName(audioFile)).absolutePath
    }

    fun getMarkdownFile(audioFile: AudioFile): File {
        return File(capturesDir, getMarkdownFileName(audioFile))
    }

    fun updateMarkdownFile(audioFile: AudioFile, captures: List<Capture>) {
        val file = getMarkdownFile(audioFile)
        val content = generateMarkdownContent(audioFile, captures)
        file.writeText(content)
    }

    fun getMarkdownContent(audioFile: AudioFile): String? {
        val file = getMarkdownFile(audioFile)
        return if (file.exists()) file.readText() else null
    }

    fun deleteMarkdownFile(audioFile: AudioFile) {
        val file = getMarkdownFile(audioFile)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun generateMarkdownContent(audioFile: AudioFile, captures: List<Capture>): String {
        val sb = StringBuilder()

        // Header
        sb.appendLine("# Captures: ${audioFile.name}")
        sb.appendLine()
        sb.appendLine("**Source:** ${audioFile.filePath}")
        sb.appendLine("**Duration:** ${formatDuration(audioFile.durationMs)}")
        sb.appendLine("**Generated:** ${dateFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // Captures sorted by timestamp
        val sortedCaptures = captures.sortedBy { it.timestampMs }

        sortedCaptures.forEachIndexed { index, capture ->
            sb.appendLine("## Capture ${index + 1} at ${formatDuration(capture.timestampMs)}")
            sb.appendLine()
            sb.appendLine("**Window:** ${formatDuration(capture.windowStartMs)} → ${formatDuration(capture.windowEndMs)}")
            sb.appendLine("**Captured:** ${dateFormat.format(Date(capture.createdAt))}")
            sb.appendLine()

            // Add notes if present
            if (!capture.notes.isNullOrBlank()) {
                sb.appendLine("### Notes")
                sb.appendLine()
                sb.appendLine(capture.notes)
                sb.appendLine()
            }

            sb.appendLine("### Transcription")
            sb.appendLine()
            val transcriptionText = capture.formattedTranscription ?: capture.transcription
            sb.appendLine("> ${transcriptionText.replace("\n", "\n> ")}")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Generates Obsidian-formatted markdown content with frontmatter.
     */
    fun generateObsidianContent(
        audioFile: AudioFile,
        captures: List<Capture>,
        title: String,
        userTags: List<String>,
        defaultTags: String = "inbox/, resources/references/podcasts"
    ): String {
        val sb = StringBuilder()

        // Build tags list: default tags + user tags
        val allTags = defaultTags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        allTags.addAll(userTags.filter { it.isNotBlank() })

        // Frontmatter
        sb.appendLine("---")
        sb.appendLine("tags:")
        allTags.forEach { tag ->
            sb.appendLine("- $tag")
        }
        sb.appendLine("author: nicky")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine()
        sb.appendLine()
        sb.appendLine()

        // Title
        sb.appendLine("# $title")
        sb.appendLine()
        sb.appendLine("**Source:** ${audioFile.filePath}")
        sb.appendLine("**Duration:** ${formatDuration(audioFile.durationMs)}")
        audioFile.firstPlayedAt?.let { sb.appendLine("**First listened:** ${dateFormat.format(Date(it))}") }
        audioFile.lastPlayedAt?.let { sb.appendLine("**Last listened:** ${dateFormat.format(Date(it))}") }
        sb.appendLine("**Generated:** ${dateFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // Captures sorted by timestamp
        val sortedCaptures = captures.sortedBy { it.timestampMs }

        sortedCaptures.forEachIndexed { index, capture ->
            sb.appendLine("## Capture ${index + 1} at ${formatDuration(capture.timestampMs)}")
            sb.appendLine()
            sb.appendLine("**Window:** ${formatDuration(capture.windowStartMs)} → ${formatDuration(capture.windowEndMs)}")
            sb.appendLine("**Captured:** ${dateFormat.format(Date(capture.createdAt))}")
            sb.appendLine()

            // Add notes if present
            if (!capture.notes.isNullOrBlank()) {
                sb.appendLine("### Notes")
                sb.appendLine()
                sb.appendLine(capture.notes)
                sb.appendLine()
            }

            sb.appendLine("### Transcription")
            sb.appendLine()
            val transcriptionText = capture.formattedTranscription ?: capture.transcription
            sb.appendLine("> ${transcriptionText.replace("\n", "\n> ")}")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Generates Obsidian-formatted markdown content without requiring an AudioFile.
     * Used for podcast episode captures.
     */
    fun generateObsidianContentSimple(
        captures: List<Capture>,
        title: String,
        userTags: List<String>,
        defaultTags: String = "inbox/, resources/references/podcasts",
        firstListenedAt: Long? = null,
        lastListenedAt: Long? = null
    ): String {
        val sb = StringBuilder()

        // Build tags list: default tags + user tags
        val allTags = defaultTags.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        allTags.addAll(userTags.filter { it.isNotBlank() })

        // Frontmatter
        sb.appendLine("---")
        sb.appendLine("tags:")
        allTags.forEach { tag ->
            sb.appendLine("- $tag")
        }
        sb.appendLine("author: nicky")
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine()
        sb.appendLine()
        sb.appendLine()

        // Title
        sb.appendLine("# $title")
        sb.appendLine()
        firstListenedAt?.let { sb.appendLine("**First listened:** ${dateFormat.format(Date(it))}") }
        lastListenedAt?.let { sb.appendLine("**Last listened:** ${dateFormat.format(Date(it))}") }
        sb.appendLine("**Generated:** ${dateFormat.format(Date())}")
        sb.appendLine()
        sb.appendLine("---")
        sb.appendLine()

        // Captures sorted by timestamp
        val sortedCaptures = captures.sortedBy { it.timestampMs }

        sortedCaptures.forEachIndexed { index, capture ->
            sb.appendLine("## Capture ${index + 1} at ${formatDuration(capture.timestampMs)}")
            sb.appendLine()
            sb.appendLine("**Window:** ${formatDuration(capture.windowStartMs)} → ${formatDuration(capture.windowEndMs)}")
            sb.appendLine("**Captured:** ${dateFormat.format(Date(capture.createdAt))}")
            sb.appendLine()

            // Add notes if present
            if (!capture.notes.isNullOrBlank()) {
                sb.appendLine("### Notes")
                sb.appendLine()
                sb.appendLine(capture.notes)
                sb.appendLine()
            }

            sb.appendLine("### Transcription")
            sb.appendLine()
            val transcriptionText = capture.formattedTranscription ?: capture.transcription
            sb.appendLine("> ${transcriptionText.replace("\n", "\n> ")}")
            sb.appendLine()
            sb.appendLine("---")
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Sanitizes a filename by replacing problematic characters with a dash.
     */
    fun sanitizeFilename(name: String): String {
        return name
            .substringBeforeLast(".")  // Remove extension
            .replace(Regex("[:'\"?!|<>*\\\\#]"), "-")  // Replace problematic chars
            .replace(Regex("-[\\s-]*-"), "-")  // Collapse dashes with spaces/dashes between them
            .replace(Regex("\\s+"), " ")  // Collapse multiple spaces
            .trim('-', ' ')
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

    private fun String.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(this.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
