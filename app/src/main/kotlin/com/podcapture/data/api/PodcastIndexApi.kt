package com.podcapture.data.api

import com.podcapture.data.api.dto.EpisodesResponse
import com.podcapture.data.api.dto.PodcastByIdResponse
import com.podcapture.data.api.dto.PodcastSearchResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PodcastIndexApi {

    @GET("search/byterm")
    suspend fun searchPodcasts(
        @Query("q") query: String,
        @Query("max") max: Int = 20,
        @Query("clean") clean: Boolean = true
    ): PodcastSearchResponse

    @GET("search/bytitle")
    suspend fun searchByTitle(
        @Query("q") query: String,
        @Query("max") max: Int = 20
    ): PodcastSearchResponse

    @GET("podcasts/byfeedid")
    suspend fun getPodcastById(
        @Query("id") id: Long
    ): PodcastByIdResponse

    @GET("podcasts/byfeedurl")
    suspend fun getPodcastByFeedUrl(
        @Query("url") feedUrl: String
    ): PodcastByIdResponse

    @GET("episodes/byfeedid")
    suspend fun getEpisodesByPodcastId(
        @Query("id") podcastId: Long,
        @Query("max") max: Int = 100,
        @Query("since") since: Long? = null,  // Return episodes published after this timestamp
        @Query("fulltext") fulltext: Boolean? = null  // Return full description text
    ): EpisodesResponse

    @GET("episodes/byid")
    suspend fun getEpisodeById(
        @Query("id") id: Long
    ): EpisodesResponse

    @GET("recent/episodes")
    suspend fun getRecentEpisodes(
        @Query("max") max: Int = 20,
        @Query("excludeString") excludeString: String? = null
    ): EpisodesResponse

    companion object {
        const val BASE_URL = "https://api.podcastindex.org/api/1.0/"
    }
}
