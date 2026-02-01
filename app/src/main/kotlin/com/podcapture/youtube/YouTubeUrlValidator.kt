package com.podcapture.youtube

/**
 * Validates and parses YouTube URLs.
 * Supports:
 * - youtube.com/watch?v=VIDEO_ID
 * - youtu.be/VIDEO_ID
 * - youtube.com/shorts/VIDEO_ID
 * - m.youtube.com/watch?v=VIDEO_ID
 */
object YouTubeUrlValidator {

    private val YOUTUBE_PATTERNS = listOf(
        // Standard youtube.com/watch URLs
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})"""),
        // youtu.be short URLs
        Regex("""(?:https?://)?youtu\.be/([a-zA-Z0-9_-]{11})"""),
        // YouTube Shorts
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/shorts/([a-zA-Z0-9_-]{11})"""),
        // Mobile youtube.com URLs
        Regex("""(?:https?://)?m\.youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})"""),
        // Embedded URLs
        Regex("""(?:https?://)?(?:www\.)?youtube\.com/embed/([a-zA-Z0-9_-]{11})"""),
        // YouTube Music URLs
        Regex("""(?:https?://)?music\.youtube\.com/watch\?.*v=([a-zA-Z0-9_-]{11})""")
    )

    /**
     * Validates if the given string is a valid YouTube URL.
     */
    fun isValidUrl(url: String): Boolean {
        return extractVideoId(url) != null
    }

    /**
     * Extracts the video ID from a YouTube URL.
     * Returns null if the URL is not a valid YouTube URL.
     */
    fun extractVideoId(url: String): String? {
        val trimmedUrl = url.trim()
        for (pattern in YOUTUBE_PATTERNS) {
            val match = pattern.find(trimmedUrl)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1]
            }
        }
        return null
    }

    /**
     * Normalizes a YouTube URL to the standard format.
     * Returns null if the URL is not valid.
     */
    fun normalizeUrl(url: String): String? {
        val videoId = extractVideoId(url) ?: return null
        return "https://www.youtube.com/watch?v=$videoId"
    }

    /**
     * Returns a user-friendly error message if the URL is invalid.
     */
    fun getValidationError(url: String): String? {
        val trimmedUrl = url.trim()

        if (trimmedUrl.isEmpty()) {
            return "Please enter a YouTube URL"
        }

        if (!trimmedUrl.contains("youtube") && !trimmedUrl.contains("youtu.be")) {
            return "Please enter a valid YouTube URL"
        }

        if (extractVideoId(trimmedUrl) == null) {
            return "Could not find a valid video ID in the URL"
        }

        return null
    }
}
