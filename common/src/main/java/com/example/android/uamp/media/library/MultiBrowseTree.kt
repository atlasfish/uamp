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

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.android.uamp.media.R
import com.example.android.uamp.media.extensions.urlEncoded

/**
 * Media IDs for the different featured categories from MockServer API
 */
const val UAMP_DAILY_RECOMMEND_ROOT = "__DAILY_RECOMMEND__"
const val UAMP_GUESS_LIKE_ROOT = "__GUESS_LIKE__"
const val UAMP_POPULAR_ROOT = "__POPULAR__"
const val UAMP_TREASURED_PLAYLISTS_ROOT = "__TREASURED_PLAYLISTS__"
const val UAMP_ALL_SONGS_ROOT = "__ALL_SONGS__"

/**
 * Enhanced BrowseTree that supports multiple API sources for MockServer integration.
 * Each featured API endpoint gets its own category in the browse tree.
 */
class MultiBrowseTree(
    private val context: Context,
    private val mainSource: MusicSource,
    private val dailySource: MusicSource,
    private val guessLikeSource: MusicSource,
    private val popularSource: MusicSource,
    private val treasuredSource: MusicSource,
    private val recentMediaId: String? = null
) {
    private val mediaIdToChildren = mutableMapOf<String, MutableList<MediaItem>>()
    private val mediaIdToMediaItem = mutableMapOf<String, MediaItem>()
    private val mediaIdToSource = mutableMapOf<String, MusicSource>()

    val searchableByUnknownCaller = true

    init {
        buildBrowseTree()
    }

    private fun buildBrowseTree() {
        val rootList = mutableListOf<MediaItem>()

        // Add Daily Recommend category
        val dailyRecommendMetadata = MediaMetadata.Builder().apply {
            setTitle("今日推荐")
            setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            setIsBrowsable(true)
            setIsPlayable(false)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_DAILY_RECOMMEND_ROOT)
            setMediaMetadata(dailyRecommendMetadata)
        }.build()
        mediaIdToSource[UAMP_DAILY_RECOMMEND_ROOT] = dailySource

        // Add Guess Like category
        val guessLikeMetadata = MediaMetadata.Builder().apply {
            setTitle("猜你喜欢")
            setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            setIsBrowsable(true)
            setIsPlayable(false)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_GUESS_LIKE_ROOT)
            setMediaMetadata(guessLikeMetadata)
        }.build()
        mediaIdToSource[UAMP_GUESS_LIKE_ROOT] = guessLikeSource

        // Add Popular category
        val popularMetadata = MediaMetadata.Builder().apply {
            setTitle("最近流行")
            setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            setIsBrowsable(true)
            setIsPlayable(false)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_POPULAR_ROOT)
            setMediaMetadata(popularMetadata)
        }.build()
        mediaIdToSource[UAMP_POPULAR_ROOT] = popularSource

        // Add Treasured Playlists category
        val treasuredMetadata = MediaMetadata.Builder().apply {
            setTitle("宝藏歌单")
            setFolderType(MediaMetadata.FOLDER_TYPE_PLAYLISTS)
            setIsBrowsable(true)
            setIsPlayable(false)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_TREASURED_PLAYLISTS_ROOT)
            setMediaMetadata(treasuredMetadata)
        }.build()
        mediaIdToSource[UAMP_TREASURED_PLAYLISTS_ROOT] = treasuredSource

        // Add Albums category (from main source)
        val albumsMetadata = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.albums_title))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_album)
                )
            )
            setIsPlayable(false)
            setIsBrowsable(true)
            setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_ALBUMS_ROOT)
            setMediaMetadata(albumsMetadata)
        }.build()

        mediaIdToChildren[UAMP_BROWSABLE_ROOT] = rootList

        // Build children for each category
        buildCategoryChildren(UAMP_DAILY_RECOMMEND_ROOT, dailySource)
        buildCategoryChildren(UAMP_GUESS_LIKE_ROOT, guessLikeSource)
        buildCategoryChildren(UAMP_POPULAR_ROOT, popularSource)
        buildCategoryChildren(UAMP_TREASURED_PLAYLISTS_ROOT, treasuredSource)
        
        // Build albums from main source
        mainSource.forEach { mediaItem ->
            val albumMediaId = mediaItem.mediaMetadata.albumTitle.toString().urlEncoded
            val albumChildren = mediaIdToChildren[albumMediaId] ?: buildAlbumRoot(mediaItem)
            albumChildren += mediaItem

            // If this was recently played, add it to the recent root.
            if (mediaItem.mediaId == recentMediaId) {
                mediaIdToChildren[UAMP_RECENT_ROOT] = mutableListOf(mediaItem)
            }
            mediaIdToMediaItem[mediaItem.mediaId] = mediaItem
        }
    }

    private fun buildCategoryChildren(categoryId: String, source: MusicSource) {
        val children = mutableListOf<MediaItem>()
        source.forEach { mediaItem ->
            children += mediaItem
            mediaIdToMediaItem[mediaItem.mediaId] = mediaItem
        }
        mediaIdToChildren[categoryId] = children
        Log.d(TAG, "Built category $categoryId with ${children.size} items")
    }

    private fun buildAlbumRoot(mediaItem: MediaItem): MutableList<MediaItem> {
        val rootList = mediaIdToChildren[UAMP_ALBUMS_ROOT] ?: mutableListOf()
        val albumMetadata = mediaItem.mediaMetadata.buildUpon().apply {
            setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
            setIsBrowsable(true)
            setIsPlayable(false)
        }.build()
        val albumMediaItem = mediaItem.buildUpon().apply {
            setMediaId(albumMetadata.albumTitle.toString().urlEncoded)
            setMediaMetadata(albumMetadata)
        }.build()
        rootList += albumMediaItem
        mediaIdToChildren[UAMP_ALBUMS_ROOT] = rootList
        return mutableListOf<MediaItem>().also {
            mediaIdToChildren[albumMediaItem.mediaId] = it
        }
    }

    operator fun get(mediaId: String) = mediaIdToChildren[mediaId]

    fun getMediaItemByMediaId(mediaId: String) = mediaIdToMediaItem[mediaId]

    /**
     * Gets the MusicSource associated with a given category mediaId
     */
    fun getSourceForCategory(categoryId: String): MusicSource? = mediaIdToSource[categoryId]

    /**
     * Searches across all sources
     */
    fun searchAll(query: String, extras: android.os.Bundle): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        
        // Search in each source
        listOf(mainSource, dailySource, guessLikeSource, popularSource, treasuredSource).forEach { source ->
            results.addAll(source.search(query, extras))
        }
        
        return results.distinctBy { it.mediaId }
    }

    companion object {
        private const val TAG = "MultiBrowseTree"
    }
}
