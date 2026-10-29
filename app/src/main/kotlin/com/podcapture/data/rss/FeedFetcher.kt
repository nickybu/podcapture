package com.podcapture.data.rss

import com.podcapture.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownServiceException
import javax.net.ssl.SSLException

/**
 * Downloads RSS feeds through the app's shared [OkHttpClient], so feed requests reuse its
 * connection pool and HTTP cache and follow redirects across schemes (http -> https).
 *
 * Feeds are always revalidated: the request asks for `no-cache`, so when the cache holds the
 * feed OkHttp sends `If-None-Match` / `If-Modified-Since` and a 304 is served from the cache
 * (reported as [Fetched.unchanged]).
 */
class FeedFetcher(private val client: OkHttpClient) {

    data class Fetched<T>(
        val value: T,
        /** True when the server answered 304 Not Modified and the body came from the cache. */
        val unchanged: Boolean
    )

    /**
     * Fetches [feedUrl] and hands the body to [read]. `http://` URLs are tried as `https://`
     * first; see [FeedUrls]. Throws [IOException] on network errors and non-2xx responses.
     */
    suspend fun <T> fetch(feedUrl: String, read: (InputStream) -> T): Fetched<T> =
        withContext(Dispatchers.IO) {
            val candidates = FeedUrls.candidates(feedUrl)
            var lastError: IOException? = null
            for ((index, url) in candidates.withIndex()) {
                try {
                    return@withContext fetchOnce(url, read)
                } catch (e: IOException) {
                    lastError = e
                    val hasFallback = index < candidates.lastIndex
                    if (!hasFallback || !FeedUrls.shouldFallBack(e)) break
                }
            }
            throw describe(lastError ?: IOException("No URL to fetch"))
        }

    private fun <T> fetchOnce(url: String, read: (InputStream) -> T): Fetched<T> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "PodCapture/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/rss+xml, application/xml, text/xml, */*")
            .cacheControl(CacheControl.Builder().noCache().build())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Feed request failed: HTTP ${response.code}")
            }
            val unchanged = response.networkResponse?.code == 304
            val value = response.body?.byteStream()?.use(read)
                ?: throw IOException("Feed response had no body")
            return Fetched(value, unchanged)
        }
    }

    /** Replaces OkHttp's cleartext-policy message with one a user can act on. */
    private fun describe(e: IOException): IOException =
        if (FeedUrls.isCleartextBlocked(e)) {
            IOException("This feed is only available over plain HTTP, which Android blocks", e)
        } else {
            e
        }
}

/** Pure URL decisions for [FeedFetcher], kept separate so they can be unit tested. */
object FeedUrls {

    /**
     * URLs to try, in order. An `http://` feed is tried as `https://` first, because Android
     * blocks cleartext traffic for this app and most hosts serve the same feed over https;
     * the original URL is kept as a fallback. Other URLs are tried as given.
     */
    fun candidates(feedUrl: String): List<String> {
        val url = feedUrl.trim()
        if (!url.startsWith("http://", ignoreCase = true)) return listOf(url)
        return listOf("https://" + url.substring("http://".length), url)
    }

    /**
     * Whether a failed https attempt should fall back to the original http URL: only when
     * https could not be reached at all (refused, unroutable, timed out, TLS failure). DNS
     * failures are scheme-independent, and HTTP error statuses mean the server answered.
     */
    fun shouldFallBack(e: IOException): Boolean = when (e) {
        is ConnectException, is NoRouteToHostException, is SocketTimeoutException -> true
        is SSLException -> true
        else -> false
    }

    /** OkHttp raises [UnknownServiceException] when the network security policy forbids http. */
    fun isCleartextBlocked(e: IOException): Boolean =
        e is UnknownServiceException && e.message?.contains("CLEARTEXT", ignoreCase = true) == true
}
