package com.podcapture.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.podcapture.BuildConfig
import com.podcapture.data.api.PodcastIndexApi
import com.podcapture.data.api.PodcastIndexAuthInterceptor
import com.podcapture.data.repository.PodcastSearchRepository
import com.podcapture.data.rss.FeedFetcher
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

private const val HTTP_CACHE_SIZE_BYTES = 20L * 1024 * 1024
private val BASE_HTTP_CLIENT = named("baseHttpClient")

/** Request logging is for debug builds only; release builds don't log request URLs. */
private fun OkHttpClient.Builder.addDebugLogging(): OkHttpClient.Builder = apply {
    if (BuildConfig.DEBUG) {
        addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
    }
}

val networkModule = module {
    // JSON serializer
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    // Base client: owns the connection pool, dispatcher and HTTP cache. The clients below are
    // derived from it with newBuilder(), so they share all three.
    single(BASE_HTTP_CLIENT) {
        OkHttpClient.Builder()
            .cache(Cache(File(androidContext().cacheDir, "http"), HTTP_CACHE_SIZE_BYTES))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    // OkHttp client for Podcast Index; the only client that carries the API credentials
    single {
        get<OkHttpClient>(BASE_HTTP_CLIENT).newBuilder()
            .addInterceptor(PodcastIndexAuthInterceptor(get()))
            .addDebugLogging()
            .build()
    }

    // RSS feeds live on arbitrary third-party hosts, so this client has no auth interceptor.
    single {
        FeedFetcher(
            get<OkHttpClient>(BASE_HTTP_CLIENT).newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addDebugLogging()
                .build()
        )
    }

    // Retrofit for Podcast Index API
    single {
        Retrofit.Builder()
            .baseUrl(PodcastIndexApi.BASE_URL)
            .client(get())
            .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
            .build()
    }

    // API interface
    single { get<Retrofit>().create(PodcastIndexApi::class.java) }

    // Repository
    single { PodcastSearchRepository(get(), get()) }
}
