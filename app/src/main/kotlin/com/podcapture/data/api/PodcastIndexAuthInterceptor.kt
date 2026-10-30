package com.podcapture.data.api

import com.podcapture.BuildConfig
import com.podcapture.data.settings.PodcastIndexCredentials
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class PodcastIndexAuthInterceptor(
    settingsDataStore: SettingsDataStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : Interceptor {

    // Latest credentials from Settings, kept current by the collector below so a request never
    // has to read DataStore.
    @Volatile
    private var userCredentials: PodcastIndexCredentials? = null

    // Released once the first DataStore value has arrived (or the flow has failed).
    private val firstLoad = CountDownLatch(1)

    init {
        scope.launch {
            try {
                settingsDataStore.podcastIndexCredentials
                    .catch { /* unreadable settings: fall back to the build-time keys */ }
                    .collect {
                        userCredentials = it
                        firstLoad.countDown()
                    }
            } finally {
                firstLoad.countDown()
            }
        }
    }

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
        val userCredentials = userCredentials ?: awaitFirstLoad()
        if (userCredentials != null && userCredentials.isComplete) return userCredentials

        val buildCredentials = PodcastIndexCredentials(
            apiKey = BuildConfig.PODCAST_INDEX_API_KEY,
            apiSecret = BuildConfig.PODCAST_INDEX_API_SECRET
        )
        return buildCredentials.takeIf { it.isComplete }
    }

    /**
     * Only reached by requests made before the collector's first value, i.e. right after
     * process start. Waiting briefly there (on an OkHttp worker thread, never per request once
     * loaded) avoids failing a user who has saved a key with [MissingApiKeyException].
     */
    private fun awaitFirstLoad(): PodcastIndexCredentials? {
        firstLoad.await(FIRST_LOAD_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        return userCredentials
    }

    private fun sha1Hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val FIRST_LOAD_TIMEOUT_MS = 2_000L
    }
}

class MissingApiKeyException : IOException(
    "Podcast Index API key not set. Add your key and secret in Settings."
)
