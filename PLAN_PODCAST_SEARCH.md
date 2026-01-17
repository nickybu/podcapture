# Podcast Search Feature - Research and Implementation Plan

## Overview

This document outlines the research findings and implementation plan for adding podcast search functionality to the PodCapture Android app. The goal is to allow users to search for podcasts and stream episodes directly within the app.

---

## API Research Findings

### 1. Podcast Index API (podcastindex.org)

**Overview:**
Podcast Index is a free, open-source podcast directory founded in 2020 by Adam Curry and Dave Jones. It aims to preserve the open, independent nature of podcasting by providing a decentralized alternative to proprietary podcast directories.

**Key Features:**
- **Catalogue Size:** 4+ million podcasts indexed (largest open podcast directory)
- **Authentication:** Required - API Key + Secret (free registration)
- **Rate Limits:** 300 requests per minute (very generous)
- **Response Format:** JSON
- **Audio Streaming:** Direct enclosure URLs provided (no proxying)
- **Open Source:** Yes - fully open-source project
- **Data Freshness:** Near real-time updates via websocket feeds

**Endpoints Available:**
| Endpoint | Description |
|----------|-------------|
| `/search/byterm` | Search podcasts by keyword |
| `/search/bytitle` | Search podcasts by title |
| `/search/byperson` | Search by person/host name |
| `/podcasts/byfeedid` | Get podcast details by ID |
| `/podcasts/byfeedurl` | Get podcast by RSS feed URL |
| `/episodes/byfeedid` | Get episodes for a podcast |
| `/episodes/byid` | Get specific episode details |
| `/recent/episodes` | Get recently published episodes |
| `/categories/list` | List all categories |

**Authentication Method:**
- Requires HTTP headers:
  - `X-Auth-Key`: Your API key
  - `X-Auth-Date`: Current Unix epoch timestamp
  - `Authorization`: SHA-1 hash of (API Key + API Secret + Timestamp)

**Pros:**
- Largest open podcast catalogue
- Very generous rate limits (300/min)
- No cost, completely free
- Active development and community
- Supports Podcasting 2.0 features (chapters, transcripts, value4value)
- Excellent documentation
- Direct audio URLs (no streaming restrictions)

**Cons:**
- Requires API key registration (but free and instant)
- More complex authentication than iTunes
- Newer service (2020) - less proven longevity than Apple

---

### 2. iTunes Search API (Apple)

**Overview:**
Apple's iTunes Search API provides access to the Apple Podcasts directory, which is historically the largest and most established podcast catalogue.

**Key Features:**
- **Catalogue Size:** 2.5+ million podcasts
- **Authentication:** None required (public API)
- **Rate Limits:** ~20 requests per minute (strict)
- **Response Format:** JSON
- **Audio Streaming:** Provides RSS feed URL, not direct audio URLs
- **Open Source:** No - proprietary Apple service

**Endpoints Available:**
| Endpoint | Description |
|----------|-------------|
| `/search` | Search for podcasts by term |
| `/lookup` | Get podcast details by ID |

**Search Parameters:**
- `term` - Search keyword (required)
- `media=podcast` - Filter to podcasts
- `entity=podcast` or `podcastAuthor`
- `country` - Two-letter country code
- `limit` - Results per query (1-200)

**Example Request:**
```
https://itunes.apple.com/search?term=technology&media=podcast&limit=25
```

**Pros:**
- No authentication required
- Simple API structure
- Established service (10+ years)
- Good podcast metadata
- Wide geographic coverage

**Cons:**
- **Severe rate limits (20/min)** - problematic for production apps
- Does not return direct audio URLs (only RSS feed URLs)
- Requires additional RSS parsing to get episodes
- No episode search capability
- Apple-centric (may exclude some independent podcasts)
- No websocket/real-time updates
- Limited search capabilities

---

### 3. Taddy API (taddy.org)

**Overview:**
Taddy is a podcast API service offering a comprehensive catalogue with built-in episode transcripts.

**Key Features:**
- **Catalogue Size:** 4+ million podcasts, 180+ million episodes
- **Authentication:** API Key required (free registration)
- **Rate Limits:** 100 requests/hour (free tier), cached responses don't count
- **Response Format:** GraphQL (single endpoint with custom queries)
- **Audio Streaming:** Direct enclosure URLs provided
- **Transcripts:** Built-in episode transcripts available

**Pricing Tiers:**
| Tier | Price | Requests | Transcripts |
|------|-------|----------|-------------|
| Free | $0 | 100/hour | Limited |
| Starter | $75/month | Higher | 1,000/month |
| Business | $150/month | Higher | 2,000/month |
| Additional | +$100/month | - | +2,000 transcripts |

**Pros:**
- Large catalogue (4M+ podcasts)
- Built-in transcripts (unique feature)
- GraphQL allows flexible queries
- Cached responses are free
- Direct audio URLs

**Cons:**
- **Rate limits restrictive on free tier** (100/hour vs Podcast Index 300/min)
- Paid plans expensive ($75/month+)
- GraphQL may be more complex to implement
- Less documentation than Podcast Index

**Verdict:** Viable alternative if transcripts are critical, but Podcast Index offers better free tier limits.

---

### 4. Other Alternatives Considered

#### ListenNotes API
- **Rate Limits:** Free tier: 5 requests/second, 100/day
- **Cost:** Free tier very limited; paid plans start at $99/month
- **Verdict:** Not suitable - free tier too restrictive

#### Spotify Web API
- **Rate Limits:** Variable, requires OAuth
- **Cost:** Free but requires Spotify Premium for playback
- **Audio Streaming:** Cannot stream outside Spotify app
- **Verdict:** Not suitable - no direct audio streaming allowed

#### gpodder.net (Open Source)
- **Catalogue Size:** ~600,000 podcasts
- **Rate Limits:** Generous but undefined
- **Verdict:** Possible backup option but smaller catalogue

---

## Comparison Summary

| Feature | Podcast Index | Taddy | iTunes Search |
|---------|---------------|-------|---------------|
| **Catalogue Size** | 4+ million | 4+ million | 2.5+ million |
| **Rate Limit** | 300/min | 100/hour | 20/min |
| **Authentication** | API Key (free) | API Key (free) | None |
| **Direct Audio URLs** | Yes | Yes | No (RSS only) |
| **Episode Search** | Yes | Yes | No |
| **Built-in Transcripts** | Via Podcasting 2.0 | Yes (paid) | No |
| **API Style** | REST | GraphQL | REST |
| **Open Source** | Yes | No | No |
| **Cost** | Free | Free/$75+/mo | Free |
| **Documentation** | Excellent | Good | Basic |

---

## Recommendation

### Primary Choice: **Podcast Index API**

**Justification:**

1. **Rate Limits:** 300 requests/minute vs 20/minute makes Podcast Index 15x more suitable for a production app. iTunes would require extensive caching and throttling.

2. **Direct Audio URLs:** Podcast Index returns enclosure URLs directly, allowing immediate streaming. iTunes only returns RSS feed URLs, requiring additional parsing.

3. **Episode Search:** Podcast Index allows searching and browsing episodes directly. iTunes requires fetching the RSS feed and parsing it locally.

4. **Open Philosophy:** Podcast Index aligns with the open, independent nature of podcasting. Using it supports the open podcast ecosystem.

5. **Comprehensive Data:** Podcast Index provides chapters, transcripts, and other Podcasting 2.0 features that can enhance the app experience.

6. **Future-Proof:** Active development with new features being added regularly.

### Secondary/Fallback: **iTunes Search API**

iTunes can be used as a fallback or secondary search option since:
- Some users may specifically want Apple Podcasts results
- No authentication required (simpler for initial testing)
- Useful for podcast artwork URLs (high quality)

---

## Implementation Plan

### Phase 1: Core Infrastructure (Week 1)

#### 1.1 API Client Setup
- Add Retrofit/OkHttp dependencies for networking
- Create `PodcastIndexApiService` interface with endpoints
- Implement authentication header interceptor
- Create API response data classes

#### 1.2 Secure Credential Storage
- Store API key/secret in `local.properties` (not committed)
- Use BuildConfig fields to inject at compile time
- Document setup process for developers

#### 1.3 Repository Layer
- Create `PodcastSearchRepository` interface
- Implement `PodcastIndexRepository`
- Add caching layer (Room + in-memory)

### Phase 2: Data Models (Week 1)

#### 2.1 Core Models
```kotlin
// Podcast from search results
data class Podcast(
    val id: Long,                    // Podcast Index ID
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val feedUrl: String,
    val categories: List<Category>,
    val episodeCount: Int,
    val language: String,
    val lastUpdateTime: Long
)

// Episode details
data class Episode(
    val id: Long,                    // Episode ID
    val podcastId: Long,
    val title: String,
    val description: String,
    val datePublished: Long,
    val durationSeconds: Int,
    val enclosureUrl: String,        // Direct audio URL
    val enclosureType: String,       // audio/mpeg, etc.
    val enclosureLength: Long,       // File size in bytes
    val artworkUrl: String?,
    val chaptersUrl: String?,        // Podcasting 2.0
    val transcriptUrl: String?       // Podcasting 2.0
)

// Category
data class Category(
    val id: Int,
    val name: String
)

// Search result wrapper
data class PodcastSearchResult(
    val podcasts: List<Podcast>,
    val count: Int,
    val query: String
)
```

#### 2.2 Local Database Entities
```kotlin
// For caching subscribed/favorited podcasts
@Entity(tableName = "podcasts")
data class PodcastEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val author: String,
    val description: String,
    val artworkUrl: String,
    val feedUrl: String,
    val subscribedAt: Long?,
    val lastRefreshedAt: Long
)

// For caching episodes
@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: Long,
    val podcastId: Long,
    val title: String,
    val description: String,
    val datePublished: Long,
    val durationSeconds: Int,
    val enclosureUrl: String,
    val enclosureType: String,
    val enclosureLength: Long,
    val artworkUrl: String?,
    val downloadedPath: String?,     // Local file path if downloaded
    val playbackPosition: Long = 0,  // Resume position
    val isCompleted: Boolean = false
)
```

### Phase 3: UI Screens (Week 2)

#### 3.1 Podcast Search Screen
- Search bar with debounced input
- Search results list with podcast cards
- Loading states and error handling
- Empty state for no results
- Category/genre filters (optional)

#### 3.2 Podcast Detail Screen
- Podcast header with artwork, title, author
- Description (expandable)
- Episode list (paginated)
- Subscribe/Unsubscribe button
- Share button

#### 3.3 Episode Detail/Player Integration
- Episode metadata display
- Stream button (connects to existing PlayerScreen)
- Download option (future enhancement)
- Show notes/description

#### 3.4 Subscriptions Screen
- List of subscribed podcasts
- Badge for new episodes
- Pull-to-refresh
- Quick access to recent episodes

### Phase 4: Navigation Updates (Week 2)

```kotlin
// New navigation routes
sealed interface NavRoute {
    // Existing routes...

    @Serializable
    data object PodcastSearch : NavRoute

    @Serializable
    data class PodcastDetail(val podcastId: Long) : NavRoute

    @Serializable
    data object Subscriptions : NavRoute
}
```

### Phase 5: Integration with Existing Features (Week 3)

#### 5.1 Audio Player Integration
- Modify `AudioPlayerService` to accept streaming URLs
- Update `AudioFile` model or create `StreamingAudioSource`
- Handle network errors gracefully
- Implement buffering indicators

#### 5.2 Capture Feature Integration
- Allow captures on streamed podcasts
- Link captures to episodes (not just local files)
- Update `Capture` model to support episode references

#### 5.3 Home Screen Updates
- Add podcast search entry point
- Show recently played podcast episodes
- Quick access to subscriptions

---

## Technical Considerations

### Networking
- Use OkHttp with Retrofit for API calls
- Implement exponential backoff for retries
- Cache responses appropriately
- Handle offline scenarios

### Audio Streaming
- ExoPlayer (already in use) supports HTTP streaming
- Implement progressive download for better UX
- Cache audio chunks locally
- Handle stream interruptions

### Error Handling
- Network connectivity errors
- API rate limit errors
- Invalid/expired credentials
- Podcast feed unavailable

### Testing
- Unit tests for repository layer
- Integration tests for API client
- UI tests for search flow
- Mock API responses for testing

---

## Dependencies to Add

```kotlin
// In build.gradle.kts
dependencies {
    // Networking
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Image loading for podcast artwork
    implementation("io.coil-kt:coil-compose:2.5.0")
}
```

---

## File Structure

```
app/src/main/kotlin/com/podcapture/
├── data/
│   ├── api/
│   │   ├── PodcastIndexApi.kt           # Retrofit interface
│   │   ├── PodcastIndexAuthInterceptor.kt
│   │   └── dto/                          # API response DTOs
│   │       ├── SearchResponse.kt
│   │       ├── PodcastDto.kt
│   │       └── EpisodeDto.kt
│   ├── db/
│   │   ├── PodcastDao.kt
│   │   ├── EpisodeDao.kt
│   │   └── (update PodCaptureDatabase.kt)
│   ├── model/
│   │   ├── Podcast.kt
│   │   └── Episode.kt
│   └── repository/
│       ├── PodcastSearchRepository.kt
│       └── PodcastIndexRepository.kt
├── ui/
│   ├── search/
│   │   ├── PodcastSearchScreen.kt
│   │   └── PodcastSearchViewModel.kt
│   ├── podcast/
│   │   ├── PodcastDetailScreen.kt
│   │   └── PodcastDetailViewModel.kt
│   └── subscriptions/
│       ├── SubscriptionsScreen.kt
│       └── SubscriptionsViewModel.kt
└── di/
    └── (update AppModule.kt)
```

---

## Timeline Estimate

| Phase | Duration | Description |
|-------|----------|-------------|
| Phase 1 | 3-4 days | API client, authentication, repository |
| Phase 2 | 1-2 days | Data models and database entities |
| Phase 3 | 4-5 days | UI screens implementation |
| Phase 4 | 1 day | Navigation updates |
| Phase 5 | 3-4 days | Integration with existing features |
| Testing | 2-3 days | Unit, integration, and UI tests |
| **Total** | **~2-3 weeks** | Complete feature implementation |

---

## API Registration Steps

1. Visit https://podcastindex.org/
2. Click "Get API Keys" or navigate to the developer portal
3. Create a free account
4. Generate API credentials (Key + Secret)
5. Add to `local.properties`:
   ```
   PODCAST_INDEX_API_KEY=your_api_key
   PODCAST_INDEX_API_SECRET=your_api_secret
   ```

---

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| API service interruption | Low | High | Implement caching, consider iTunes fallback |
| Rate limit exceeded | Low | Medium | Implement proper throttling and caching |
| Audio URL expiration | Low | Low | Refresh URLs before playback |
| Large response payloads | Medium | Low | Implement pagination, limit results |

---

## Future Enhancements

1. **Offline Support:** Download episodes for offline listening
2. **Push Notifications:** New episode alerts for subscribed podcasts
3. **Chapters Support:** Display and navigate podcast chapters
4. **Transcript Support:** Show episode transcripts with timestamps
5. **iTunes Fallback:** Add iTunes as secondary search source
6. **Recommendations:** Suggest podcasts based on listening history
7. **OPML Import/Export:** Import subscriptions from other apps

---

## Conclusion

The Podcast Index API is the recommended choice for implementing podcast search in PodCapture due to its generous rate limits, direct audio URL access, comprehensive episode data, and alignment with open podcasting principles. The implementation will follow the phased approach outlined above, integrating seamlessly with the existing app architecture while adding powerful new podcast discovery and streaming capabilities.
