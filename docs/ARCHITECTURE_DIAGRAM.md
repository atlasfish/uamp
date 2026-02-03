# MockServer API Integration - Architecture Diagram

## Complete System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        Android Client Layer                      │
│  (Android Auto, Cast, Google Assistant, Media Browser UI)       │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Media3 API
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      MusicService                                │
│                  (MediaLibraryService)                           │
│                                                                  │
│  Manages 7 MusicSource instances:                               │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 1. musicSource           → JsonSource                    │  │
│  │ 2. dailyRecommendSource  → DailyRecommendSource         │  │
│  │ 3. guessLikeSource       → GuessLikeSource              │  │
│  │ 4. popularSource         → PopularSource                │  │
│  │ 5. treasuredPlaylistsSource → TreasuredPlaylistsSource  │  │
│  │ 6. allSongsSource        → AllSongsApiSource            │  │
│  │ 7. allPlaylistsSource    → AllPlaylistsSource           │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  MusicServiceCallback:                                          │
│  ├─ onGetLibraryRoot()  → Returns ROOT with all categories     │
│  ├─ onGetChildren()     → Returns items for each category      │
│  ├─ onSearch()          → Enhanced with filtering              │
│  ├─ onGetSearchResult() → Returns filtered search results      │
│  └─ onGetItem()         → Returns specific media item          │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Uses
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      MultiBrowseTree                             │
│                                                                  │
│  Responsibilities:                                               │
│  ├─ Build browse hierarchy from all sources                     │
│  ├─ Map media IDs to source instances                          │
│  ├─ Handle playlist expansion (fetch songs on-demand)          │
│  ├─ Provide unified search across all sources                  │
│  └─ Manage media ID to MediaItem mappings                      │
│                                                                  │
│  Browse Structure Created:                                       │
│  ROOT                                                            │
│  ├─ 今日推荐 (Daily Recommend)    [dailySource]                │
│  ├─ 猜你喜欢 (Guess Like)         [guessLikeSource]            │
│  ├─ 最近流行 (Popular)             [popularSource]             │
│  ├─ 宝藏歌单 (Treasured Playlists) [treasuredSource]           │
│  ├─ 全部歌曲 (All Songs)           [allSongsSource]            │
│  ├─ 全部歌单 (All Playlists)       [allPlaylistsSource]        │
│  │   └─ [Individual playlists - expanded on-demand]            │
│  └─ Albums                         [mainSource]                 │
│      └─ [Individual albums - organized by album title]         │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ Fetch from
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      API Sources Layer                           │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ ApiSource (Abstract Base Class)                          │  │
│  │                                                           │  │
│  │ Features:                                                 │  │
│  │ ├─ Async loading with coroutines                        │  │
│  │ ├─ URL construction (baseUrl + endpoint)                │  │
│  │ ├─ Relative path resolution                             │  │
│  │ ├─ JSON to MediaItem conversion                         │  │
│  │ └─ State management (CREATED → INITIALIZED/ERROR)       │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  Concrete Implementations:                                       │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ DailyRecommendSource     → GET /api/recommend/daily      │  │
│  │ GuessLikeSource          → GET /api/recommend/guess      │  │
│  │ PopularSource            → GET /api/recommend/popular    │  │
│  │ TreasuredPlaylistsSource → GET /api/playlists/treasured │  │
│  │ AllSongsApiSource        → GET /api/songs                │  │
│  │ AllPlaylistsSource       → GET /api/playlists            │  │
│  │                             GET /api/playlists/{id}       │  │
│  │ JsonSource               → GET /music_list.json          │  │
│  └──────────────────────────────────────────────────────────┘  │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ HTTP Requests
                             │
┌────────────────────────────▼────────────────────────────────────┐
│                      MockServer API                              │
│                  (http://10.0.2.2:8000)                         │
│                                                                  │
│  Endpoints:                                                      │
│  ├─ GET /api/recommend/daily      → 10 random songs            │
│  ├─ GET /api/recommend/guess      → 10 recommended songs       │
│  ├─ GET /api/recommend/popular    → Top 20 songs by likes      │
│  ├─ GET /api/playlists/treasured  → 5 featured playlists       │
│  ├─ GET /api/songs                → All songs (paginated)       │
│  ├─ GET /api/playlists            → All playlists               │
│  ├─ GET /api/playlists/{id}       → Specific playlist details  │
│  └─ GET /music_list.json          → Full music catalog         │
│                                                                  │
│  Data Models:                                                    │
│  ├─ Song: {id, title, artist, album, genre, source, image,     │
│  │         trackNumber, duration, tags, isList, likes}         │
│  └─ Playlist: {id, title, creatorId, description, tags,        │
│               isList, songs[]}                                  │
└──────────────────────────────────────────────────────────────────┘
```

## Data Flow Examples

### Example 1: Browsing Daily Recommendations

```
User                    Client App              MusicService           MultiBrowseTree        DailyRecommendSource      MockServer
 │                          │                        │                       │                         │                    │
 │ Open "今日推荐"         │                        │                       │                         │                    │
 │─────────────────────────>│                        │                       │                         │                    │
 │                          │ onGetChildren(         │                       │                         │                    │
 │                          │   "DAILY_RECOMMEND")   │                       │                         │                    │
 │                          │───────────────────────>│                       │                         │                    │
 │                          │                        │ get["DAILY_RECOMMEND"]│                         │                    │
 │                          │                        │──────────────────────>│                         │                    │
 │                          │                        │                       │ (songs already loaded)  │                    │
 │                          │                        │<──────────────────────│                         │                    │
 │                          │<───────────────────────│                       │                         │                    │
 │<─────────────────────────│                        │                       │                         │                    │
 │ Display 10 songs         │                        │                       │                         │                    │
```

### Example 2: Searching with Filters

```
User                    Client App              MusicService           MultiBrowseTree        All Sources              MockServer
 │                          │                        │                       │                         │                    │
 │ Search "rock" +         │                        │                       │                         │                    │
 │ filter: genre=Rock      │                        │                       │                         │                    │
 │─────────────────────────>│                        │                       │                         │                    │
 │                          │ onSearch(              │                       │                         │                    │
 │                          │   query="rock",        │                       │                         │                    │
 │                          │   params={genre=Rock}) │                       │                         │                    │
 │                          │───────────────────────>│                       │                         │                    │
 │                          │                        │ searchAll(            │                         │                    │
 │                          │                        │   "rock", extras)     │                         │                    │
 │                          │                        │──────────────────────>│                         │                    │
 │                          │                        │                       │ search each source      │                    │
 │                          │                        │                       │ + apply genre filter    │                    │
 │                          │                        │<──────────────────────│                         │                    │
 │                          │<───────────────────────│                       │                         │                    │
 │<─────────────────────────│                        │                       │                         │                    │
 │ Display filtered results │                        │                       │                         │                    │
```

### Example 3: Expanding a Playlist

```
User                    Client App              MusicService           AllPlaylistsSource      MockServer
 │                          │                        │                         │                    │
 │ Click on "工作专注歌单"  │                        │                         │                    │
 │─────────────────────────>│                        │                         │                    │
 │                          │ onGetChildren(         │                         │                    │
 │                          │   "playlist_123")      │                         │                    │
 │                          │───────────────────────>│                         │                    │
 │                          │                        │ getPlaylistSongs(123)   │                    │
 │                          │                        │────────────────────────>│                    │
 │                          │                        │                         │ GET /api/          │
 │                          │                        │                         │ playlists/123      │
 │                          │                        │                         │───────────────────>│
 │                          │                        │                         │<───────────────────│
 │                          │                        │                         │ {id, title, songs[]}
 │                          │                        │<────────────────────────│                    │
 │                          │<───────────────────────│                         │                    │
 │<─────────────────────────│                        │                         │                    │
 │ Display playlist songs   │                        │                         │                    │
```

## Source Loading Sequence

```
App Start
    │
    ▼
MusicService.onCreate()
    │
    ├─> Initialize all 7 sources
    │   ├─ musicSource = JsonSource(...)
    │   ├─ dailyRecommendSource = DailyRecommendSource()
    │   ├─ guessLikeSource = GuessLikeSource()
    │   ├─ popularSource = PopularSource()
    │   ├─ treasuredPlaylistsSource = TreasuredPlaylistsSource()
    │   ├─ allSongsSource = AllSongsApiSource()
    │   └─ allPlaylistsSource = AllPlaylistsSource()
    │
    └─> Launch parallel loading (serviceScope)
        ├─ musicSource.load()           ──> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
        ├─ dailyRecommendSource.load()  ──> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
        ├─ guessLikeSource.load()       ──> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
        ├─ popularSource.load()         ──> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
        ├─ treasuredPlaylistsSource.load() ─> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
        ├─ allSongsSource.load()        ──> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
        └─ allPlaylistsSource.load()    ──> STATE_INITIALIZING ──> HTTP ──> STATE_INITIALIZED
                                                                                │
                                                                                ▼
                                                                    All sources ready
                                                                                │
                                                                                ▼
                                                                    MultiBrowseTree builds
                                                                    browse hierarchy
                                                                                │
                                                                                ▼
                                                                    Ready to serve clients
```

## Key Design Decisions

### 1. Separate Source for Each Endpoint
- **Why**: Media3 best practice, clean separation of concerns
- **Benefit**: Easy to add/remove endpoints, independent error handling

### 2. MultiBrowseTree Pattern
- **Why**: Support multiple sources in a unified hierarchy
- **Benefit**: Clean architecture, easy to extend with new sources

### 3. Async Loading with Synchronization
- **Why**: Non-blocking UI, parallel loading for performance
- **Benefit**: Fast startup, better user experience

### 4. Playlist On-Demand Loading
- **Why**: Reduce initial load time, fetch only when needed
- **Benefit**: Better performance for large playlist catalogs

### 5. Enhanced Search with Filtering
- **Why**: Required by spec, provides better UX
- **Benefit**: Users can find music more efficiently

## Technology Stack

- **Language**: Kotlin
- **Media Framework**: AndroidX Media3
- **Network**: Standard Java URL/HTTP (no external library)
- **JSON Parsing**: Gson
- **Concurrency**: Kotlin Coroutines
- **Architecture**: MediaLibraryService pattern

## Performance Characteristics

| Operation | Time Complexity | Notes |
|-----------|----------------|-------|
| Initial Load | O(n) | Parallel loading of all sources |
| Browse Root | O(1) | Pre-built category list |
| Browse Category | O(1) | Pre-loaded during initialization |
| Search | O(n) | Searches all sources sequentially |
| Playlist Expand | O(1) + HTTP | On-demand fetch |
| Get Item | O(1) | HashMap lookup |

---

**Document Version**: 1.0  
**Last Updated**: 2026-02-03  
**Status**: Architecture Finalized
