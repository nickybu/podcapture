package com.podcapture.di

import retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.podcapture.data.api.PodcastIndexApi
import com.podcapture.data.api.PodcastIndexAuthInterceptor
import com.podcapture.data.repository.PodcastSearchRepository
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

val networkModule = module {
    // JSON serializer
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
    }

    // OkHttp client for Podcast Index
    single {
        OkHttpClient.Builder()
            .addInterceptor(PodcastIndexAuthInterceptor())
            .addInterceptor(
                HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                }
            )
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
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
