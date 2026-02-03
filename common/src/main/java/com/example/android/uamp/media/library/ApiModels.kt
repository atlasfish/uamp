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

/**
 * API response wrapper for paginated song results from /api/songs
 */
data class ApiSongsResponse(
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val items: List<JsonMusic>
)

/**
 * Playlist structure from the MockServer API
 */
data class ApiPlaylist(
    val id: String,
    val title: String,
    val creatorId: String = "022",
    val description: String = "",
    val tags: List<String> = emptyList(),
    val isList: Boolean = true,
    val songs: List<JsonMusic> = emptyList()
)

/**
 * Response wrapper for playlists API
 */
data class ApiPlaylistsResponse(
    val playlists: List<ApiPlaylist>
)
