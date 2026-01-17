package com.podcapture.data.repository

import com.podcapture.data.api.PodcastIndexApi
import com.podcapture.data.model.Episode
import com.podcapture.data.model.Podcast
import com.podcapture.data.model.toDomain
import com.podcapture.data.settings.SettingsDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PodcastSearchRepository(
    private val api: PodcastIndexApi,
    private val settingsDataStore: SettingsDataStore
) {
    private suspend fun trackApiCall() {
        settingsDataStore.incrementApiCallCount()
    }

    suspend fun searchPodcasts(query: String, maxResults: Int = 20): Result<List<Podcast>> {
        return withContext(Dispatchers.IO) {
            try {
                trackApiCall()
                val response = api.searchPodcasts(query, maxResults)
                if (response.status == "true") {
                    Result.success(response.feeds.map { it.toDomain() })
                } else {
                    Result.failure(Exception("Search failed: ${response.description}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun searchByTitle(query: String, maxResults: Int = 20): Result<List<Podcast>> {
        return withContext(Dispatchers.IO) {
            try {
                trackApiCall()
                val response = api.searchByTitle(query, maxResults)
                if (response.status == "true") {
                    Result.success(response.feeds.map { it.toDomain() })
                } else {
                    Result.failure(Exception("Search failed: ${response.description}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getPodcastById(id: Long): Result<Podcast> {
        return withContext(Dispatchers.IO) {
            try {
                trackApiCall()
                val response = api.getPodcastById(id)
                if (response.status == "true" && response.feed != null) {
                    Result.success(response.feed.toDomain())
                } else {
                    Result.failure(Exception("Podcast not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getEpisodesByPodcastId(
        podcastId: Long,
        maxResults: Int = 50
    ): Result<List<Episode>> {
        return withContext(Dispatchers.IO) {
            try {
                trackApiCall()
                val response = api.getEpisodesByPodcastId(podcastId, maxResults, fulltext = true)
                if (response.status == "true") {
                    Result.success(response.items.map { it.toDomain() })
                } else {
                    Result.failure(Exception("Failed to fetch episodes: ${response.description}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getEpisodeById(id: Long): Result<Episode> {
        return withContext(Dispatchers.IO) {
            try {
                trackApiCall()
                val response = api.getEpisodeById(id)
                if (response.status == "true" && response.items.isNotEmpty()) {
                    Result.success(response.items.first().toDomain())
                } else {
                    Result.failure(Exception("Episode not found"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getRecentEpisodes(maxResults: Int = 20): Result<List<Episode>> {
        return withContext(Dispatchers.IO) {
            try {
                trackApiCall()
                val response = api.getRecentEpisodes(maxResults)
                if (response.status == "true") {
                    Result.success(response.items.map { it.toDomain() })
                } else {
                    Result.failure(Exception("Failed to fetch recent episodes"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
