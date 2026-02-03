/*
 * Copyright 2024 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.uamp.media.library

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Base URL for all API endpoints
 */
const val BASE_URL = "http://10.0.2.2:8000"

/**
 * Source that fetches data from the MockServer API endpoints.
 * This is a base class for all API-based sources.
 */
abstract class ApiSource(private val endpoint: String) : AbstractMusicSource() {

    private var catalog: List<MediaItem> = emptyList()

    init {
        state = STATE_INITIALIZING
    }

    override fun iterator(): Iterator<MediaItem> = catalog.iterator()

    override suspend fun load() {
        val url = "$BASE_URL$endpoint"
        Log.d(TAG, "Loading from API: $url")
        
        fetchAndParseCatalog(url)?.let { updatedCatalog ->
            catalog = updatedCatalog
            state = STATE_INITIALIZED
            Log.d(TAG, "Successfully loaded ${catalog.size} items from $url")
        } ?: run {
            catalog = emptyList()
            state = STATE_ERROR
            Log.e(TAG, "Failed to load from $url")
        }
    }

    /**
     * Fetches data from API and converts to MediaItem list.
     * Subclasses should implement this to handle their specific response format.
     */
    protected abstract suspend fun fetchAndParseCatalog(url: String): List<MediaItem>?

    /**
     * Helper to download JSON from URL
     */
    protected suspend fun <T> downloadJson(url: String, clazz: Class<T>): T? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url)
                val reader = BufferedReader(InputStreamReader(connection.openStream()))
                Gson().fromJson(reader, clazz)
            } catch (e: IOException) {
                Log.e(TAG, "Error downloading JSON from $url", e)
                null
            }
        }
    }

    /**
     * Converts a JsonMusic object to a MediaItem
     */
    protected fun jsonMusicToMediaItem(song: JsonMusic): MediaItem {
        // Fix relative paths
        val sourceUrl = if (song.source.startsWith("http")) {
            song.source
        } else {
            BASE_URL + song.source
        }
        
        val imageUrl = if (song.image.startsWith("http")) {
            song.image
        } else {
            BASE_URL + song.image
        }

        val jsonImageUri = Uri.parse(imageUrl)
        val imageUri = AlbumArtContentProvider.mapUri(jsonImageUri)
        
        val mediaMetadata = MediaMetadata.Builder()
            .from(song)
            .apply {
                setArtworkUri(imageUri)
                val extras = Bundle()
                extras.putString(JsonSource.ORIGINAL_ARTWORK_URI_KEY, jsonImageUri.toString())
                extras.putInt("likes", song.likes)
                extras.putBoolean("isList", song.isList)
                // Store tags as ArrayList for easier retrieval
                extras.putStringArrayList("tags", ArrayList(song.tags))
                setExtras(extras)
            }
            .build()

        return MediaItem.Builder()
            .apply {
                setMediaId(song.id)
                setUri(sourceUrl)
                setMimeType(MimeTypes.AUDIO_MPEG)
                setMediaMetadata(mediaMetadata)
            }.build()
    }

    companion object {
        private const val TAG = "ApiSource"
    }
}

/**
 * Source for daily recommendations: GET /api/recommend/daily
 */
class DailyRecommendSource : ApiSource("/api/recommend/daily") {
    override suspend fun fetchAndParseCatalog(url: String): List<MediaItem>? {
        val response = downloadJson(url, Array<JsonMusic>::class.java) ?: return null
        return response.map { jsonMusicToMediaItem(it) }
    }
}

/**
 * Source for "guess you like": GET /api/recommend/guess
 */
class GuessLikeSource : ApiSource("/api/recommend/guess") {
    override suspend fun fetchAndParseCatalog(url: String): List<MediaItem>? {
        val response = downloadJson(url, Array<JsonMusic>::class.java) ?: return null
        return response.map { jsonMusicToMediaItem(it) }
    }
}

/**
 * Source for popular songs: GET /api/recommend/popular
 */
class PopularSource : ApiSource("/api/recommend/popular") {
    override suspend fun fetchAndParseCatalog(url: String): List<MediaItem>? {
        val response = downloadJson(url, Array<JsonMusic>::class.java) ?: return null
        return response.map { jsonMusicToMediaItem(it) }
    }
}

/**
 * Source for treasured playlists: GET /api/playlists/treasured
 */
class TreasuredPlaylistsSource : ApiSource("/api/playlists/treasured") {
    override suspend fun fetchAndParseCatalog(url: String): List<MediaItem>? {
        val response = downloadJson(url, Array<ApiPlaylist>::class.java) ?: return null
        return response.flatMap { playlist ->
            // For playlists, we return the songs within them as MediaItems
            // Each playlist becomes a browsable folder
            playlist.songs.map { song -> 
                jsonMusicToMediaItem(song).buildUpon().apply {
                    // Add playlist information to metadata
                    val metadata = song
                    val extras = Bundle()
                    extras.putString("playlistId", playlist.id)
                    extras.putString("playlistTitle", playlist.title)
                }.build()
            }
        }
    }
}

/**
 * Source for all songs with pagination support: GET /api/songs
 */
class AllSongsApiSource : ApiSource("/api/songs?pageSize=50") {
    override suspend fun fetchAndParseCatalog(url: String): List<MediaItem>? {
        // For simplicity, fetch first page. A more complete implementation
        // would handle pagination and load all pages.
        val response = downloadJson(url, ApiSongsResponse::class.java) ?: return null
        return response.items.map { jsonMusicToMediaItem(it) }
    }
}
