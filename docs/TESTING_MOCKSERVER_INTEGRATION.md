# MockServer API Integration Testing Guide

## Overview
This document provides guidance on testing the MockServer API integration in the UAMP application.

## Prerequisites
1. MockServer must be running at `http://10.0.2.2:8000` (Android emulator localhost)
2. The server should implement all the endpoints described in `docs/MockServer/`

## API Endpoints Integrated

### All API Endpoints (Each with its own Media Library)
All endpoints are exposed as separate browsable categories under ROOT:

- **今日推荐 (Daily Recommend)**: `/api/recommend/daily`
  - Returns 10 random songs
  - Accessible as a browsable category in the root menu

- **猜你喜欢 (Guess Like)**: `/api/recommend/guess`
  - Returns 10 random songs based on simulated preferences
  - Accessible as a browsable category in the root menu

- **最近流行 (Popular)**: `/api/recommend/popular`
  - Returns top 20 songs sorted by likes
  - Accessible as a browsable category in the root menu

- **宝藏歌单 (Treasured Playlists)**: `/api/playlists/treasured`
  - Returns 5 random playlists with their songs
  - Accessible as a browsable category in the root menu

- **全部歌曲 (All Songs)**: `/api/songs`
  - Returns paginated list of all songs (default 50 per page)
  - Accessible as a browsable category in the root menu

- **全部歌单 (All Playlists)**: `/api/playlists`
  - Returns list of all playlists
  - Accessible as a browsable category in the root menu
  - Click on any playlist to expand and see its songs

- **Albums**: Built from `/music_list.json`
  - Organizes songs by album
  - Accessible as a browsable category in the root menu

## Enhanced Search Functionality

The `onSearch` method in `MediaLibraryService` now supports advanced filtering:

### Search Parameters (via LibraryParams extras)
- `query`: Text search across title, artist, album, genre
- `genre`: Filter by specific genre (exact match)
- `artist`: Filter by specific artist (exact match)
- `album`: Filter by specific album (exact match)
- `tag`: Filter by specific tag from the tags array
- `minLikes`: Filter by minimum likes count

### Example Search Usage
```kotlin
// Search with genre filter
val extras = Bundle().apply {
    putString("genre", "Electronic")
}
val params = LibraryParams.Builder().setExtras(extras).build()
mediaController.search("music", params)

// Search with minimum likes filter
val extras = Bundle().apply {
    putInt("minLikes", 500)
}
val params = LibraryParams.Builder().setExtras(extras).build()
mediaController.search("popular", params)
```

## Testing Steps

### 1. Basic Browse Testing
1. Launch the UAMP app in an Android emulator
2. Navigate to the media browser
3. Verify the following categories appear:
   - 今日推荐 (Daily Recommend)
   - 猜你喜欢 (Guess Like)
   - 最近流行 (Popular)
   - 宝藏歌单 (Treasured Playlists)
   - 全部歌曲 (All Songs)
   - 全部歌单 (All Playlists)
   - Albums
4. Click on each category and verify songs are loaded
5. Click on "全部歌单" and verify playlists are displayed
6. Click on a playlist and verify its songs are loaded

### 2. Search Testing
1. Use the search feature in Android Auto or the media browser
2. Test basic search with keywords
3. Test filtered searches using the parameters above

### 3. Playback Testing
1. Select a song from any category
2. Verify it plays correctly
3. Test playback controls (play, pause, skip)
4. Verify album art is displayed

### 4. API Response Verification
Check that the MockServer returns data in the expected format:

```json
// Song format
{
  "id": "song_id",
  "title": "Song Title",
  "album": "Album Name",
  "artist": "Artist Name",
  "genre": "Genre",
  "source": "/music/file.mp3",
  "image": "/images/cover.jpg",
  "trackNumber": 1,
  "totalTrackCount": 10,
  "duration": 180,
  "site": "",
  "tags": ["tag1", "tag2"],
  "isList": false,
  "likes": 123
}

// Playlist format
{
  "id": "playlist_id",
  "title": "Playlist Title",
  "creatorId": "022",
  "description": "Description",
  "tags": ["tag1"],
  "isList": true,
  "songs": [/* array of song objects */]
}
```

## Known Limitations
1. Pagination for `/api/songs` currently only fetches the first page (50 items)
2. All sources must successfully load before browsing is available
3. Network errors are logged but may not be visible to the user

## Troubleshooting

### No categories appear
- Verify MockServer is running at `http://10.0.2.2:8000`
- Check logcat for network errors: `adb logcat | grep ApiSource`

### Songs don't play
- Verify the `source` URLs are accessible
- Check that BaseURL is correctly set to `http://10.0.2.2:8000`

### Search returns no results
- Verify the search query matches content in the catalog
- Check that filters are applied correctly in logcat

## Log Tags for Debugging
- `ApiSource`: API fetch operations
- `MultiBrowseTree`: Browse tree construction
- `MusicService`: Service lifecycle and callbacks
- `TAG` (in onSearch): Search operations and filtering
