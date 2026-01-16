package com.podcapture.data.api

import com.podcapture.BuildConfig
import okhttp3.Interceptor
import okhttp3.Response
import java.security.MessageDigest

class PodcastIndexAuthInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val apiKey = BuildConfig.PODCAST_INDEX_API_KEY
        val apiSecret = BuildConfig.PODCAST_INDEX_API_SECRET

        if (apiKey.isBlank() || apiSecret.isBlank()) {
            return chain.proceed(chain.request())
        }

        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val authHash = sha1Hash(apiKey + apiSecret + timestamp)

        val request = chain.request().newBuilder()
            .addHeader("X-Auth-Key", apiKey)
            .addHeader("X-Auth-Date", timestamp)
            .addHeader("Authorization", authHash)
            .addHeader("User-Agent", "PodCapture/1.0")
            .build()

        return chain.proceed(request)
    }

    private fun sha1Hash(input: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
