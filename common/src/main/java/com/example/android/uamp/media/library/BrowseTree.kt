/*
 * Copyright 2019 Google Inc. All rights reserved.
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
import com.example.android.uamp.media.MusicService
import com.example.android.uamp.media.R
import com.example.android.uamp.media.extensions.urlEncoded

/**
 * Represents a tree of media that's used by [MusicService.onLoadChildren].
 *
 * [BrowseTree] maps a media id (see: [MediaMetadataCompat.METADATA_KEY_MEDIA_ID]) to one (or
 * more) [MediaMetadataCompat] objects, which are children of that media id.
 *
 * For example, given the following conceptual tree:
 * root
 *  +-- Albums
 *  |    +-- Album_A
 *  |    |    +-- Song_1
 *  |    |    +-- Song_2
 *  ...
 *  +-- Artists
 *  ...
 *
 *  Requesting `browseTree["root"]` would return a list that included "Albums", "Artists", and
 *  any other direct children. Taking the media ID of "Albums" ("Albums" in this example),
 *  `browseTree["Albums"]` would return a single item list "Album_A", and, finally,
 *  `browseTree["Album_A"]` would return "Song_1" and "Song_2". Since those are leaf nodes,
 *  requesting `browseTree["Song_1"]` would return null (there aren't any children of it).
 */
class BrowseTree(
    val context: Context,
    val musicSource: MusicSource,
    val recentMediaId: String? = null
) {
    private val mediaIdToChildren = mutableMapOf<String, MutableList<MediaItem>>()
    private val mediaIdToMediaItem = mutableMapOf<String, MediaItem>()

    /**
     * Whether to allow clients which are unknown (not on the allowed list) to use search on this
     * [BrowseTree].
     */
    val searchableByUnknownCaller = true

    /**
     * In this example, there's a single root node (identified by the constant
     * [UAMP_BROWSABLE_ROOT]). The root's children are each album included in the
     * [MusicSource], and the children of each album are the songs on that album.
     * (See [BrowseTree.buildAlbumRoot] for more details.)
     *
     * TODO: Expand to allow more browsing types.
     */
    init {
        val rootList = mediaIdToChildren[UAMP_BROWSABLE_ROOT] ?: mutableListOf()

        val recommendedCategory = MediaMetadata.Builder().apply {
            setTitle(context.getString(R.string.recommended_title))
            setArtworkUri(
                Uri.parse(
                    RESOURCE_ROOT_URI +
                            context.resources.getResourceEntryName(R.drawable.ic_recommended)
                )
            )
            setFolderType(MediaMetadata.FOLDER_TYPE_MIXED)
            setIsBrowsable(true)
            setIsPlayable(false)
        }.build()
        rootList += MediaItem.Builder().apply {
            setMediaId(UAMP_RECOMMENDED_ROOT)
            setMediaMetadata(recommendedCategory)
        }.build()

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
        musicSource.forEach { mediaItem ->
            val albumMediaId = mediaItem.mediaMetadata.albumTitle.toString().urlEncoded
            val albumChildren = mediaIdToChildren[albumMediaId] ?: buildAlbumRoot(mediaItem)
            albumChildren += mediaItem

            Log.d("BrowseTree", "loading catalogue for " + mediaItem.mediaId)

            // 调试日志：检查 trackNumber 的值
            val trackNum = mediaItem.mediaMetadata.trackNumber
            Log.d("BrowseTree", "Check Recommend: mediaId=${mediaItem.mediaId}, trackNumber=$trackNum")

            // Add the first track of each album to the 'Recommended' category
            // 如果 JSON 中没有 trackNumber (默认为0)，这个条件将永远为 false
            if (mediaItem.mediaMetadata.trackNumber == 1 || mediaItem.mediaMetadata.trackNumber == 0) {
                // 临时修复：如果是 0 (默认值) 也加入推荐，或者你可以完全去掉这个 if 条件把所有歌都加进去
                Log.d("BrowseTree", "adding to recommended: " + mediaItem.mediaId)
                val recommendedChildren = mediaIdToChildren[UAMP_RECOMMENDED_ROOT]
                    ?: mutableListOf()
                recommendedChildren += mediaItem
                mediaIdToChildren[UAMP_RECOMMENDED_ROOT] = recommendedChildren
            }

            // If this was recently played, add it to the recent root.
            if (mediaItem.mediaId == recentMediaId) {
                mediaIdToChildren[UAMP_RECENT_ROOT] = mutableListOf(mediaItem)
            }
            mediaIdToMediaItem[mediaItem.mediaId] = mediaItem
        }
    }

    /**
     * Provides access to the list of children with the `get` operator.
     * i.e.: `browseTree\[UAMP_BROWSABLE_ROOT\]`
     */
    operator fun get(mediaId: String) = mediaIdToChildren[mediaId]

    /** Provides access to the media items by media id. */
    fun getMediaItemByMediaId(mediaId: String) = mediaIdToMediaItem[mediaId]

    /**
     * Builds a node, under the root, that represents an album, given
     * a [MediaItem] object that's one of the songs on that album,
     * marking the item as [MediaMetadata.FOLDER_TYPE_ALBUMS], since it will have child
     * node(s) AKA at least 1 song.
     */
    private fun buildAlbumRoot(mediaItem: MediaItem): MutableList<MediaItem> {
        // Get or create the albums Category list.
        val rootList = mediaIdToChildren[UAMP_ALBUMS_ROOT] ?: mutableListOf()
        // Create the album and add it to the 'Albums' category.
        val albumMetadata = mediaItem.mediaMetadata.buildUpon().apply {
            setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
            setIsBrowsable(true)
            // UAMP Config: Albums are folders and generally not playable directly as a single item in this implementation.
            // If you want albums to be playable (e.g. play all tracks), you would change this to true
            // and handle the playback logic for the group.
            setIsPlayable(false)
        }.build()
        val albumMediaItem= mediaItem.buildUpon().apply {
            setMediaId(albumMetadata.albumTitle.toString().urlEncoded)
            setMediaMetadata(albumMetadata)
        }.build()
        rootList += albumMediaItem
        // Set the album root list for look up later,
        mediaIdToChildren[UAMP_ALBUMS_ROOT] = rootList
        // Insert the album's root with an empty list for its children, and return the list.
        return mutableListOf<MediaItem>().also {
            mediaIdToChildren[albumMediaItem.mediaId] = it
        }
    }
}

const val UAMP_BROWSABLE_ROOT = "/"
const val UAMP_EMPTY_ROOT = "@empty@"
const val UAMP_RECOMMENDED_ROOT = "__RECOMMENDED__"
const val UAMP_ALBUMS_ROOT = "__ALBUMS__"
const val UAMP_RECENT_ROOT = "__RECENT__"

const val MEDIA_SEARCH_SUPPORTED = "android.media.browse.SEARCH_SUPPORTED"

const val RESOURCE_ROOT_URI = "android.resource://com.example.android.uamp.next/drawable/"
