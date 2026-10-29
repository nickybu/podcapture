package com.podcapture.data.rss

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class FeedUrlsTest {

    @Test
    fun `http feed is tried as https first with the original as fallback`() {
        assertThat(FeedUrls.candidates("http://example.com/feed.xml?token=abc"))
            .containsExactly("https://example.com/feed.xml?token=abc", "http://example.com/feed.xml?token=abc")
            .inOrder()
    }

    @Test
    fun `scheme match is case insensitive and whitespace is trimmed`() {
        assertThat(FeedUrls.candidates("  HTTP://Example.com/rss  "))
            .containsExactly("https://Example.com/rss", "HTTP://Example.com/rss")
            .inOrder()
    }

    @Test
    fun `https feed is tried as given with no fallback`() {
        assertThat(FeedUrls.candidates("https://example.com/feed")).containsExactly("https://example.com/feed")
    }

    @Test
    fun `non-http schemes are left alone`() {
        assertThat(FeedUrls.candidates("feed://example.com/rss")).containsExactly("feed://example.com/rss")
    }

    @Test
    fun `connection-level https failures fall back to http`() {
        assertThat(FeedUrls.shouldFallBack(ConnectException("refused"))).isTrue()
        assertThat(FeedUrls.shouldFallBack(NoRouteToHostException())).isTrue()
        assertThat(FeedUrls.shouldFallBack(SocketTimeoutException())).isTrue()
        assertThat(FeedUrls.shouldFallBack(SSLHandshakeException("bad cert"))).isTrue()
        assertThat(FeedUrls.shouldFallBack(SSLPeerUnverifiedException("hostname mismatch"))).isTrue()
    }

    @Test
    fun `DNS failures and HTTP errors do not fall back`() {
        assertThat(FeedUrls.shouldFallBack(UnknownHostException("no such host"))).isFalse()
        assertThat(FeedUrls.shouldFallBack(IOException("Feed request failed: HTTP 404"))).isFalse()
    }

    @Test
    fun `cleartext policy rejection is recognised`() {
        val blocked = UnknownServiceException("CLEARTEXT communication to example.com not permitted by network security policy")
        assertThat(FeedUrls.isCleartextBlocked(blocked)).isTrue()
        assertThat(FeedUrls.isCleartextBlocked(UnknownServiceException("Unable to find acceptable protocols"))).isFalse()
        assertThat(FeedUrls.isCleartextBlocked(IOException("CLEARTEXT"))).isFalse()
    }
}
