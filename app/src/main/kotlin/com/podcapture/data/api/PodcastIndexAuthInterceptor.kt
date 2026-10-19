package com.podcapture.data.api

import com.podcapture.BuildConfig
import com.podcapture.data.settings.PodcastIndexCredentials
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest

class PodcastIndexAuthInterceptor(
    private val settingsDataStore: SettingsDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val credentials = resolveCredentials()
            ?: throw MissingApiKeyException()

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val authHash = sha1Hash(credentials.apiKey + credentials.apiSecret + timestamp)

        val request = chain.request().newBuilder()
            .addHeader("X-Auth-Key", credentials.apiKey)
            .addHeader("X-Auth-Date", timestamp)
            .addHeader("Authorization", authHash)
            .addHeader("User-Agent", "PodCapture/${BuildConfig.VERSION_NAME}")
            .build()

        return chain.proceed(request)
    }

    /**
     * Credentials entered in Settings take priority; keys baked in at build time
     * (local.properties) are a fallback for development builds.
     */
    private fun resolveCredentials(): PodcastIndexCredentials? {
        // Runs on an OkHttp worker thread; DataStore serves this from its in-memory cache after the first read.
        val userCredentials = runBlocking { settingsDataStore.podcastIndexCredentials.first() }
        if (userCredentials.isComplete) return userCredentials

        val buildCredentials = PodcastIndexCredentials(
            apiKey = BuildConfig.PODCAST_INDEX_API_KEY,
            apiSecret = BuildConfig.PODCAST_INDEX_API_SECRET
        )
        return buildCredentials.takeIf { it.isComplete }
    }

    private fun sha1Hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

class MissingApiKeyException : IOException(
    "Podcast Index API key not set. Add your key and secret in Settings."
)
