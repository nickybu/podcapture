package com.podcapture.data.rss

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

class RssFeedParser {

    data class ParsedFeed(
        val title: String,
        val author: String,
        val description: String,
        val imageUrl: String,
        val language: String,
        val episodes: List<ParsedEpisode>
    )

    data class ParsedEpisode(
        val guid: String,
        val title: String,
        val description: String,
        val audioUrl: String,
        val audioType: String,
        val audioSize: Long,
        val publishedDate: Long,
        val duration: Int,
        val imageUrl: String,
        val link: String?
    )

    fun parse(inputStream: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var feedTitle = ""
        var feedAuthor = ""
        var feedDescription = ""
        var feedImageUrl = ""
        var feedLanguage = "en"
        val episodes = mutableListOf<ParsedEpisode>()

        var inChannel = false
        var inItem = false
        var inImage = false

        var itemTitle = ""
        var itemGuid = ""
        var itemDescription = ""
        var itemAudioUrl = ""
        var itemAudioType = "audio/mpeg"
        var itemAudioSize = 0L
        var itemPubDate = ""
        var itemDuration = ""
        var itemImageUrl = ""
        var itemLink = ""

        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "channel" -> inChannel = true
                    "item" -> {
                        inItem = true
                        itemTitle = ""; itemGuid = ""; itemDescription = ""
                        itemAudioUrl = ""; itemAudioType = "audio/mpeg"; itemAudioSize = 0L
                        itemPubDate = ""; itemDuration = ""; itemImageUrl = ""; itemLink = ""
                    }
                    "image" -> if (inChannel && !inItem) inImage = true
                    "enclosure" -> if (inItem) {
                        itemAudioUrl = parser.getAttributeValue(null, "url") ?: ""
                        itemAudioType = parser.getAttributeValue(null, "type") ?: "audio/mpeg"
                        itemAudioSize = parser.getAttributeValue(null, "length")?.toLongOrNull() ?: 0L
                    }
                    "itunes:image" -> {
                        val href = parser.getAttributeValue(null, "href") ?: ""
                        if (href.isNotEmpty()) {
                            if (inItem) itemImageUrl = href
                            else if (inChannel && feedImageUrl.isEmpty()) feedImageUrl = href
                        }
                    }
                    "title" -> {
                        val text = readText(parser)
                        when {
                            inItem -> itemTitle = text
                            inImage -> { /* skip image block title */ }
                            inChannel -> feedTitle = text
                        }
                    }
                    "description", "itunes:subtitle" -> {
                        val text = readText(parser)
                        when {
                            inItem -> if (itemDescription.isEmpty()) itemDescription = text
                            inChannel -> if (feedDescription.isEmpty()) feedDescription = text
                        }
                    }
                    "itunes:summary", "content:encoded" -> {
                        val text = readText(parser)
                        when {
                            inItem -> if (itemDescription.isEmpty()) itemDescription = text
                            inChannel -> if (feedDescription.isEmpty()) feedDescription = text
                        }
                    }
                    "itunes:author", "author", "managingEditor" -> {
                        val text = readText(parser)
                        if (inChannel && !inItem && feedAuthor.isEmpty()) feedAuthor = text
                    }
                    "language" -> {
                        val text = readText(parser)
                        if (inChannel && !inItem) feedLanguage = text
                    }
                    "pubDate", "dc:date" -> {
                        val text = readText(parser)
                        if (inItem) itemPubDate = text
                    }
                    "itunes:duration" -> {
                        val text = readText(parser)
                        if (inItem) itemDuration = text
                    }
                    "guid" -> {
                        val text = readText(parser)
                        if (inItem) itemGuid = text
                    }
                    "link" -> {
                        val text = readText(parser)
                        if (inItem) itemLink = text
                    }
                    "url" -> {
                        val text = readText(parser)
                        if (inImage && feedImageUrl.isEmpty()) feedImageUrl = text
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "item" -> {
                        if (itemAudioUrl.isNotEmpty()) {
                            episodes.add(
                                ParsedEpisode(
                                    guid = itemGuid.ifEmpty { itemAudioUrl },
                                    title = itemTitle,
                                    description = itemDescription,
                                    audioUrl = itemAudioUrl,
                                    audioType = itemAudioType,
                                    audioSize = itemAudioSize,
                                    publishedDate = parsePubDate(itemPubDate),
                                    duration = parseDuration(itemDuration),
                                    imageUrl = itemImageUrl,
                                    link = itemLink.ifEmpty { null }
                                )
                            )
                        }
                        inItem = false
                    }
                    "image" -> inImage = false
                    "channel" -> inChannel = false
                }
            }
            eventType = parser.next()
        }

        return ParsedFeed(
            title = feedTitle,
            author = feedAuthor,
            description = feedDescription,
            imageUrl = feedImageUrl,
            language = feedLanguage,
            episodes = episodes
        )
    }

    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        var next = parser.next()
        while (next == XmlPullParser.TEXT || next == XmlPullParser.CDSECT) {
            sb.append(parser.text ?: "")
            next = parser.next()
        }
        // Parser is now at END_TAG (or next START_TAG for mixed content)
        return sb.toString().trim()
    }

    private fun parsePubDate(pubDate: String): Long {
        if (pubDate.isEmpty()) return System.currentTimeMillis() / 1000
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "EEE, dd MMM yyyy HH:mm zzz",
            "dd MMM yyyy HH:mm:ss zzz",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        for (format in formats) {
            try {
                val date = SimpleDateFormat(format, Locale.ENGLISH).parse(pubDate)
                if (date != null) return date.time / 1000
            } catch (_: Exception) { }
        }
        return System.currentTimeMillis() / 1000
    }

    private fun parseDuration(duration: String): Int {
        if (duration.isEmpty()) return 0
        val parts = duration.trim().split(":")
        return when (parts.size) {
            3 -> (parts[0].toIntOrNull() ?: 0) * 3600 +
                 (parts[1].toIntOrNull() ?: 0) * 60 +
                 (parts[2].toIntOrNull() ?: 0)
            2 -> (parts[0].toIntOrNull() ?: 0) * 60 +
                 (parts[1].toIntOrNull() ?: 0)
            1 -> duration.trim().toIntOrNull() ?: 0
            else -> 0
        }
    }
}
