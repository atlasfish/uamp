# MockServer API Integration - Quick Start Guide

## Overview
UAMP has been enhanced to integrate with the MockServer API, providing multiple curated music sources and advanced search capabilities.

## Prerequisites
1. Deploy and run the MockServer at `http://10.0.2.2:8000` (for Android emulator)
2. Ensure the server implements all endpoints described in `docs/MockServer/`

## New Features

### 1. Multiple Music Sources
The app now browses music from multiple API endpoints, all accessible under ROOT:

| Category | API Endpoint | Description |
|----------|-------------|-------------|
| 今日推荐 | `/api/recommend/daily` | 10 daily recommended songs |
| 猜你喜欢 | `/api/recommend/guess` | 10 songs you might like |
| 最近流行 | `/api/recommend/popular` | Top 20 popular songs |
| 宝藏歌单 | `/api/playlists/treasured` | 5 featured playlists |
| 全部歌曲 | `/api/songs` | All songs (paginated) |
| 全部歌单 | `/api/playlists` | All playlists (browsable, click to expand) |
| Albums | `/music_list.json` | Full music catalog organized by album |

### 2. Enhanced Search with Filtering
Search now supports advanced filtering via `LibraryParams`:

```kotlin
// Example: Search for Electronic music with high likes
val extras = Bundle().apply {
    putString("genre", "Electronic")
    putInt("minLikes", 500)
}
val params = LibraryParams.Builder().setExtras(extras).build()
mediaController.search("music", params)
```

**Supported Filters:**
- `genre`: Filter by genre (exact match)
- `artist`: Filter by artist name (exact match)
- `album`: Filter by album name (exact match)
- `tag`: Filter by tag (from tags array)
- `minLikes`: Minimum like count (>=)

### 3. Enhanced Metadata
Songs now include:
- **likes**: Popularity metric (0-1000)
- **tags**: Array of descriptive tags
- **isList**: Indicates playlist vs. song

## API Requirements

### JSON Format
The MockServer must return data in this format:

**Song Object:**
```json
{
  "id": "song_001",
  "title": "Song Title",
  "album": "Album Name",
  "artist": "Artist Name",
  "genre": "Electronic",
  "source": "/music/song.mp3",
  "image": "/images/cover.jpg",
  "trackNumber": 1,
  "totalTrackCount": 10,
  "duration": 180,
  "site": "https://example.com",
  "tags": ["chill", "electronic"],
  "isList": false,
  "likes": 523
}
```

**Playlist Object:**
```json
{
  "id": "playlist_001",
  "title": "My Playlist",
  "creatorId": "022",
  "description": "A great playlist",
  "tags": ["work", "focus"],
  "isList": true,
  "songs": [/* array of song objects */]
}
```

### Required Endpoints

1. **GET `/api/recommend/daily`**
   - Returns: Array of 10 song objects

2. **GET `/api/recommend/guess`**
   - Returns: Array of 10 song objects

3. **GET `/api/recommend/popular`**
   - Returns: Array of 20 song objects (sorted by likes desc)

4. **GET `/api/playlists/treasured`**
   - Returns: Array of 5 playlist objects

5. **GET `/api/playlists/treasured`**
   - Returns: Array of 5 playlist objects

6. **GET `/api/songs`**
   - Parameters: `page`, `pageSize`, `keyword`, `genre`, `tag`
   - Returns: `{ page, pageSize, total, items: [song objects] }`

7. **GET `/api/playlists`**
   - Parameters: `keyword`, `includeSong`
   - Returns: Array of playlist objects

8. **GET `/api/playlists/{id}`**
   - Returns: Single playlist object with full song details

9. **GET `/music_list.json`**
   - Returns: `{ "music": [/* array of song objects */] }`

## Usage Examples

### Browsing Music
1. Launch the UAMP app
2. Open the media browser
3. Navigate through the categories:
   - 今日推荐 for daily picks
   - 最近流行 for trending songs
   - 全部歌曲 for browsing all songs
   - 全部歌单 for browsing all playlists
   - Albums for browsing by album
4. Click on a playlist to expand and view its songs

### Searching
Use the search feature in Android Auto or media browser:
- Simple search: Type keywords to search across all fields
- Filtered search: Use LibraryParams to apply specific filters

### Playing Music
1. Browse to any category
2. Select a song
3. Use standard media controls (play, pause, skip, etc.)

## Architecture

```
MusicService (MediaLibraryService)
│
├── musicSource (JsonSource) → /music_list.json
├── dailyRecommendSource → /api/recommend/daily
├── guessLikeSource → /api/recommend/guess
├── popularSource → /api/recommend/popular
├── treasuredPlaylistsSource → /api/playlists/treasured
├── allSongsSource → /api/songs
└── allPlaylistsSource → /api/playlists
        ↓
    MultiBrowseTree
        ↓
    MediaLibrarySession (exposed to clients)
```

All sources are accessible as browsable categories under ROOT:
- 今日推荐 (Daily Recommend)
- 猜你喜欢 (Guess Like)
- 最近流行 (Popular)
- 宝藏歌单 (Treasured Playlists)
- 全部歌曲 (All Songs)
- 全部歌单 (All Playlists)
- Albums

## Configuration

### Changing the Base URL
To change the API base URL, edit `ApiSource.kt`:

```kotlin
const val BASE_URL = "http://your-server:port"
```

### Adding New Sources
1. Create a new class extending `ApiSource`:
```kotlin
class MyCustomSource : ApiSource("/api/my-endpoint") {
    override suspend fun fetchAndParseCatalog(url: String): List<MediaItem>? {
        // Implementation
    }
}
```

2. Add to `MusicService`:
```kotlin
private lateinit var myCustomSource: MusicSource

override fun onCreate() {
    // ...
    myCustomSource = MyCustomSource()
    serviceScope.launch {
        myCustomSource.load()
    }
}
```

3. Update `MultiBrowseTree` to include the new source

## Troubleshooting

### Categories Don't Appear
- **Check**: Is MockServer running at `http://10.0.2.2:8000`?
- **Check**: Are all API endpoints returning valid JSON?
- **Debug**: Run `adb logcat | grep -E "ApiSource|MultiBrowseTree"`

### Songs Don't Play
- **Check**: Are media file URLs accessible?
- **Check**: Is BaseURL correctly configured?
- **Debug**: Run `adb logcat | grep -E "MusicService|ExoPlayer"`

### Search Returns No Results
- **Check**: Does the query match any content?
- **Check**: Are filters too restrictive?
- **Debug**: Run `adb logcat | grep "onSearch"`

## Performance Considerations

1. **Initial Load**: All sources load in parallel on app start
2. **Memory**: Each source caches its catalog in memory
3. **Network**: Failed API calls are logged but don't block the app
4. **Pagination**: Currently loads first page only (50 items max per source)

## Further Reading

- **Implementation Details**: See `docs/IMPLEMENTATION_SUMMARY.md`
- **Testing Guide**: See `docs/TESTING_MOCKSERVER_INTEGRATION.md`
- **API Specification**: See `docs/MockServer/readme.md` and `docs/MockServer/detail_design.md`

## Support

For issues or questions:
1. Check the logs: `adb logcat | grep -E "MusicService|ApiSource"`
2. Verify MockServer is responding: `curl http://10.0.2.2:8000/api/recommend/daily`
3. Review the documentation in `docs/`

## Version
- UAMP Version: Based on Media3 AndroidX Media3
- API Version: MockServer API v1
- Last Updated: 2026-02-03
