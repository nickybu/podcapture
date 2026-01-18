package com.podcapture.data.opml

import com.podcapture.data.model.BookmarkedPodcast
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.InputStream
import java.io.OutputStream
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class OpmlFeed(
    val title: String,
    val feedUrl: String,
    val htmlUrl: String? = null
)

data class OpmlDocument(
    val title: String,
    val feeds: List<OpmlFeed>
)

class OpmlManager {

    private val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)

    fun parseOpml(inputStream: InputStream): Result<OpmlDocument> {
        return try {
            val feeds = mutableListOf<OpmlFeed>()
            var documentTitle = "Imported Subscriptions"

            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            var inHead = false
            var inBody = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "head" -> inHead = true
                            "body" -> inBody = true
                            "title" -> {
                                if (inHead) {
                                    documentTitle = parser.nextText() ?: documentTitle
                                }
                            }
                            "outline" -> {
                                if (inBody) {
                                    val feed = parseOutline(parser)
                                    if (feed != null) {
                                        feeds.add(feed)
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.lowercase()) {
                            "head" -> inHead = false
                            "body" -> inBody = false
                        }
                    }
                }
                eventType = parser.next()
            }

            Result.success(OpmlDocument(documentTitle, feeds))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseOutline(parser: XmlPullParser): OpmlFeed? {
        val type = parser.getAttributeValue(null, "type")?.lowercase()
        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
            ?: parser.getAttributeValue(null, "xmlurl")

        // Only process RSS/podcast outlines with a feed URL
        if (xmlUrl.isNullOrBlank()) {
            return null
        }

        // Accept type="rss" or no type (some OPML files omit it)
        if (type != null && type != "rss" && type != "podcast") {
            return null
        }

        val title = parser.getAttributeValue(null, "text")
            ?: parser.getAttributeValue(null, "title")
            ?: "Unknown Podcast"

        val htmlUrl = parser.getAttributeValue(null, "htmlUrl")
            ?: parser.getAttributeValue(null, "htmlurl")

        return OpmlFeed(
            title = title,
            feedUrl = xmlUrl,
            htmlUrl = htmlUrl
        )
    }

    fun generateOpml(podcasts: List<BookmarkedPodcast>): String {
        val writer = StringWriter()
        val factory = XmlPullParserFactory.newInstance()
        val serializer = factory.newSerializer()

        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.text("\n")

        // <opml version="2.0">
        serializer.startTag(null, "opml")
        serializer.attribute(null, "version", "2.0")
        serializer.text("\n")

        // <head>
        serializer.startTag(null, "head")
        serializer.text("\n")

        serializer.startTag(null, "title")
        serializer.text("PodCapture Subscriptions")
        serializer.endTag(null, "title")
        serializer.text("\n")

        serializer.startTag(null, "dateCreated")
        serializer.text(dateFormat.format(Date()))
        serializer.endTag(null, "dateCreated")
        serializer.text("\n")

        serializer.endTag(null, "head")
        serializer.text("\n")

        // <body>
        serializer.startTag(null, "body")
        serializer.text("\n")

        for (podcast in podcasts) {
            if (podcast.feedUrl.isNotBlank()) {
                serializer.startTag(null, "outline")
                serializer.attribute(null, "type", "rss")
                serializer.attribute(null, "text", podcast.title)
                serializer.attribute(null, "title", podcast.title)
                serializer.attribute(null, "xmlUrl", podcast.feedUrl)
                serializer.endTag(null, "outline")
                serializer.text("\n")
            }
        }

        serializer.endTag(null, "body")
        serializer.text("\n")

        serializer.endTag(null, "opml")
        serializer.endDocument()

        return writer.toString()
    }

    fun writeOpml(outputStream: OutputStream, podcasts: List<BookmarkedPodcast>) {
        val opmlContent = generateOpml(podcasts)
        outputStream.write(opmlContent.toByteArray(Charsets.UTF_8))
        outputStream.flush()
    }
}
