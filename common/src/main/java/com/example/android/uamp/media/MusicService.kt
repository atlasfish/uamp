/*
 * Copyright 2017 Google Inc. All rights reserved.
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

package com.example.android.uamp.media

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.ConditionVariable
import android.util.Log
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.SessionAvailabilityListener
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_MEDIA_ITEM_TRANSITION
import androidx.media3.common.Player.EVENT_PLAY_WHEN_READY_CHANGED
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.Listener
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.android.uamp.media.library.BrowseTree
import com.example.android.uamp.media.library.JsonSource
import com.example.android.uamp.media.library.MEDIA_SEARCH_SUPPORTED
import com.example.android.uamp.media.library.MusicSource
import com.example.android.uamp.media.library.MultiBrowseTree
import com.example.android.uamp.media.library.DailyRecommendSource
import com.example.android.uamp.media.library.GuessLikeSource
import com.example.android.uamp.media.library.PopularSource
import com.example.android.uamp.media.library.TreasuredPlaylistsSource
import com.example.android.uamp.media.library.UAMP_BROWSABLE_ROOT
import com.example.android.uamp.media.library.UAMP_RECENT_ROOT
import com.google.android.gms.cast.framework.CastContext
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.text.get

/**
 * Service for browsing the catalogue and and receiving a [MediaController] from the app's UI
 * and other apps that wish to play music via UAMP (for example, Android Auto or
 * the Google Assistant).
 *
 * Browsing begins with the method [MusicService.MusicServiceCallback.onGetLibraryRoot], and
 * continues in the callback [MusicService.MusicServiceCallback.onGetChildren].
 *
 * This class also handles playback for Cast sessions. When a Cast session is active, playback
 * commands are passed to a [CastPlayer].
 */
@OptIn(UnstableApi::class)
open class MusicService : MediaLibraryService() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    protected lateinit var mediaSession: MediaLibrarySession
    private var currentMediaItemIndex: Int = 0

    private lateinit var musicSource: MusicSource
    private lateinit var dailyRecommendSource: MusicSource
    private lateinit var guessLikeSource: MusicSource
    private lateinit var popularSource: MusicSource
    private lateinit var treasuredPlaylistsSource: MusicSource
    private lateinit var packageValidator: PackageValidator
    private lateinit var storage: PersistentStorage

    /**
     * This must be `by lazy` because the sources won't initially be ready. Use
     * [callWhenSourcesReady] to be sure they are safely ready for usage.
     */
    private val multiBrowseTree: MultiBrowseTree by lazy {
        MultiBrowseTree(
            applicationContext,
            musicSource,
            dailyRecommendSource,
            guessLikeSource,
            popularSource,
            treasuredPlaylistsSource
        )
    }

    private val recentRootMediaItem: MediaItem by lazy {
        MediaItem.Builder()
            .setMediaId(UAMP_RECENT_ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                    .setIsPlayable(false)
                    .build())
            .build()
    }

    private val catalogueRootMediaItem: MediaItem by lazy {
        MediaItem.Builder()
            .setMediaId(UAMP_BROWSABLE_ROOT)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setFolderType(MediaMetadata.FOLDER_TYPE_ALBUMS)
                    .setIsPlayable(false)
                    .build())
            .build()
    }

    private val executorService by lazy {
        MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    }

    private val remoteJsonSource: Uri =
        Uri.parse("http://10.0.2.2:8000/music_list.json")

    private val uAmpAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val playerListener = PlayerEventListener()

    /**
     * Configure ExoPlayer to handle audio focus for us. See [ExoPlayer.Builder.setAudioAttributes]
     * for details.
     */
    private val exoPlayer: Player by lazy {
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(uAmpAudioAttributes, true)
            setHandleAudioBecomingNoisy(true)
            addListener(playerListener)
        }
        player.addAnalyticsListener(EventLogger(null, "exoplayer-uamp"))
        player
    }

    /**
     * If Cast is available, create a CastPlayer to handle communication with a Cast session.
     */
    private val castPlayer: CastPlayer? by lazy {
        try {
            val castContext = CastContext.getSharedInstance(this)
            CastPlayer(castContext, CastMediaItemConverter()).apply {
                setSessionAvailabilityListener(UampCastSessionAvailabilityListener())
                addListener(playerListener)
            }
        } catch (e : Exception) {
            // We wouldn't normally catch the generic `Exception` however
            // calling `CastContext.getSharedInstance` can throw various exceptions, all of which
            // indicate that Cast is unavailable.
            // Related internal bug b/68009560.
            Log.i(TAG, "Cast is not available on this device. " +
                    "Exception thrown when attempting to obtain CastContext. " + e.message)
            null
        }
    }

    private val replaceableForwardingPlayer: ReplaceableForwardingPlayer by lazy {
        ReplaceableForwardingPlayer(exoPlayer)
    }

    /** @return the {@link MediaLibrarySessionCallback} to be used to build the media session. */
    open fun getCallback(): MediaLibrarySession.Callback {
        return MusicServiceCallback()
    }

    override fun onCreate() {
        super.onCreate()

        if (castPlayer?.isCastSessionAvailable == true) {
            replaceableForwardingPlayer.setPlayer(castPlayer!!)
        }

        mediaSession = with(MediaLibrarySession.Builder(
            this, replaceableForwardingPlayer, getCallback())) {
            setId(packageName)
            packageManager?.getLaunchIntentForPackage(packageName)?.let { sessionIntent ->
                setSessionActivity(
                    PendingIntent.getActivity(
                        /* context= */ this@MusicService,
                        /* requestCode= */ 0,
                        sessionIntent,
                        if (Build.VERSION.SDK_INT >= 23) FLAG_IMMUTABLE
                        else FLAG_UPDATE_CURRENT
                    )
                )
            }
            build()
        }

        // Initialize all music sources from MockServer API endpoints
        musicSource = JsonSource(source = remoteJsonSource)
        dailyRecommendSource = DailyRecommendSource()
        guessLikeSource = GuessLikeSource()
        popularSource = PopularSource()
        treasuredPlaylistsSource = TreasuredPlaylistsSource()
        
        // Load all sources asynchronously
        serviceScope.launch {
            musicSource.load()
            dailyRecommendSource.load()
            guessLikeSource.load()
            popularSource.load()
            treasuredPlaylistsSource.load()
        }

        packageValidator = PackageValidator(this, R.xml.allowed_media_browser_callers)
        storage = PersistentStorage.getInstance(applicationContext)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
//        return if ("android.media.session.MediaController" == controllerInfo.packageName
//            || packageValidator.isKnownCaller(controllerInfo.packageName, controllerInfo.uid)) {
//            mediaSession
//        } else null
        // 测试简单起见，允许所有应用连接
        return mediaSession
    }

    /** Called when swiping the activity away from recents. */
    override fun onTaskRemoved(rootIntent: Intent) {
        saveRecentSongToStorage()
        super.onTaskRemoved(rootIntent)
        // The choice what to do here is app specific. Some apps stop playback, while others allow
        // playback to continue and allow users to stop it with the notification.
        releaseMediaSession()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaSession()
    }

    private fun releaseMediaSession() {
        mediaSession.run {
            release()
            if (player.playbackState != Player.STATE_IDLE) {
                player.removeListener(playerListener)
                player.release()
            }
        }
        // Cancel coroutines when the service is going away.
        serviceJob.cancel()
    }

    private fun saveRecentSongToStorage() {
        // Obtain the current song details *before* saving them on a separate thread, otherwise
        // the current player may have been unloaded by the time the save routine runs.
        val currentMediaItem = replaceableForwardingPlayer.currentMediaItem ?: return
        serviceScope.launch {
            val mediaItem =
                multiBrowseTree.getMediaItemByMediaId(currentMediaItem.mediaId) ?: return@launch
            storage.saveRecentSong(mediaItem, replaceableForwardingPlayer.currentPosition)
        }
    }

    private fun preparePlayerForResumption(mediaItem: MediaItem) {
        callWhenSourcesReady {
            val playableMediaItem = multiBrowseTree.getMediaItemByMediaId(mediaItem.mediaId)
            val startPositionMs =
                mediaItem.mediaMetadata.extras?.getLong(
                    MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS
                ) ?: 0
            playableMediaItem?.let {
                exoPlayer.setMediaItem(playableMediaItem)
                exoPlayer.seekTo(startPositionMs)
                exoPlayer.prepare()
            }
        }
    }

    /** Returns a function that opens the condition variable when called. */
    private fun openWhenReady(conditionVariable: ConditionVariable): (Boolean) -> Unit = {
        val successfullyInitialized = it
        if (!successfullyInitialized) {
            Log.e(TAG, "loading music source failed")
        }
        conditionVariable.open()
    }

    /**
     * Returns a future that executes the action when all music sources are ready. This may be an
     * immediate execution if all sources are ready, or a deferred asynchronous execution if any
     * source is still loading.
     *
     * @param action The function to be called when all sources are ready.
     */
    private fun <T> callWhenSourcesReady(action: () -> T): ListenableFuture<T> {
        val conditionVariable = ConditionVariable()
        val sources = listOf(
            musicSource, 
            dailyRecommendSource, 
            guessLikeSource, 
            popularSource, 
            treasuredPlaylistsSource
        )
        
        // Check if all sources are ready
        val allReady = sources.all { source ->
            source.whenReady(openWhenReady(conditionVariable))
        }
        
        return if (allReady) {
            Futures.immediateFuture(action())
        } else {
            executorService.submit<T> {
                conditionVariable.block()
                action()
            }
        }
    }

    /**
     * Returns a future that executes the action when the main music source is ready.
     * This is kept for backward compatibility with code that only needs the main source.
     */
    private fun <T> callWhenMusicSourceReady(action: () -> T): ListenableFuture<T> {
        val conditionVariable = ConditionVariable()
        return if (musicSource.whenReady(openWhenReady(conditionVariable))) {
            Futures.immediateFuture(action())
        } else {
            executorService.submit<T> {
                conditionVariable.block()
                action()
            }
        }
    }

    open inner class MusicServiceCallback: MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession, browser: MediaSession.ControllerInfo, params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // By default, all known clients are permitted to search, but only tell unknown callers
            // about search if permitted by the [MultiBrowseTree].
            // always true for testing
            val isKnownCaller = packageValidator.isKnownCaller(browser.packageName, browser.uid) || true
            val rootExtras = Bundle().apply {
                putBoolean(
                    MEDIA_SEARCH_SUPPORTED,
                    isKnownCaller || multiBrowseTree.searchableByUnknownCaller
                )
                putBoolean(CONTENT_STYLE_SUPPORTED, true)
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST)
            }
            val libraryParams = LibraryParams.Builder().setExtras(rootExtras).build()
            val rootMediaItem = if (!isKnownCaller) {
                Log.e(TAG, "Unknown Caller!Return Empty MediaItem Id")
                MediaItem.EMPTY
            } else if (params?.isRecent == true) {
                if (exoPlayer.currentTimeline.isEmpty) {
                    storage.loadRecentSong()?.let {
                        preparePlayerForResumption(it)
                    }
                }
                recentRootMediaItem
            } else {
                catalogueRootMediaItem
            }
            return Futures.immediateFuture(LibraryResult.ofItem(rootMediaItem, libraryParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == recentRootMediaItem.mediaId) {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(
                        storage.loadRecentSong()?.let {
                                song -> listOf(song)
                        }!!,
                        LibraryParams.Builder().build()
                    )
                )
            }
            return callWhenSourcesReady {
                val children = multiBrowseTree[parentId] ?: ImmutableList.of()

                // === Implement pagination logic to avoid Binder exceeding 1MB limit ===
                val fromIndex = (page * pageSize).coerceIn(0, children.size)
                val toIndex = (fromIndex + pageSize).coerceIn(fromIndex, children.size)

                LibraryResult.ofItemList(
                    children.subList(fromIndex, toIndex),
                    LibraryParams.Builder().build()
                )
            }
        }


        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return callWhenSourcesReady {
                LibraryResult.ofItem(
                    multiBrowseTree.getMediaItemByMediaId(mediaId) ?: MediaItem.EMPTY,
                    LibraryParams.Builder().build())
            }
        }

        /**
         * Enhanced onSearch implementation that supports filtering through MediaBrowser.
         * Supports filtering by:
         * - query: text search across title, artist, album, genre
         * - extras in LibraryParams:
         *   - "genre": filter by specific genre
         *   - "artist": filter by specific artist
         *   - "album": filter by specific album
         *   - "tag": filter by specific tag
         *   - "minLikes": filter by minimum likes count
         */
        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            return callWhenSourcesReady {
                val extras = params?.extras ?: Bundle()
                
                // Start with search results from all sources
                var searchResult = multiBrowseTree.searchAll(query, extras)
                
                // Apply additional filters from params if provided
                extras.getString("genre")?.let { genre ->
                    searchResult = searchResult.filter { 
                        it.mediaMetadata.genre?.toString() == genre 
                    }
                    Log.d(TAG, "Filtered by genre: $genre, results: ${searchResult.size}")
                }
                
                extras.getString("artist")?.let { artist ->
                    searchResult = searchResult.filter { item ->
                        item.mediaMetadata.artist?.toString() == artist ||
                        item.mediaMetadata.albumArtist?.toString() == artist
                    }
                    Log.d(TAG, "Filtered by artist: $artist, results: ${searchResult.size}")
                }
                
                extras.getString("album")?.let { album ->
                    searchResult = searchResult.filter { 
                        it.mediaMetadata.albumTitle?.toString() == album 
                    }
                    Log.d(TAG, "Filtered by album: $album, results: ${searchResult.size}")
                }
                
                extras.getString("tag")?.let { tag ->
                    searchResult = searchResult.filter { item ->
                        item.mediaMetadata.extras?.getStringArrayList("tags")?.contains(tag) ?: false
                    }
                    Log.d(TAG, "Filtered by tag: $tag, results: ${searchResult.size}")
                }
                
                extras.getInt("minLikes", -1).let { minLikes ->
                    if (minLikes >= 0) {
                        searchResult = searchResult.filter { item ->
                            (item.mediaMetadata.extras?.getInt("likes") ?: 0) >= minLikes
                        }
                        Log.d(TAG, "Filtered by minLikes: $minLikes, results: ${searchResult.size}")
                    }
                }
                
                Log.d(TAG, "Search completed. Query: '$query', Total results: ${searchResult.size}")
                mediaSession.notifySearchResultChanged(browser, query, searchResult.size, params)
                LibraryResult.ofVoid()
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return callWhenSourcesReady {
                val extras = params?.extras ?: Bundle()
                var searchResult = multiBrowseTree.searchAll(query, extras)
                
                // Apply the same filters as in onSearch
                extras.getString("genre")?.let { genre ->
                    searchResult = searchResult.filter { 
                        it.mediaMetadata.genre?.toString() == genre 
                    }
                }
                
                extras.getString("artist")?.let { artist ->
                    searchResult = searchResult.filter { item ->
                        item.mediaMetadata.artist?.toString() == artist ||
                        item.mediaMetadata.albumArtist?.toString() == artist
                    }
                }
                
                extras.getString("album")?.let { album ->
                    searchResult = searchResult.filter { 
                        it.mediaMetadata.albumTitle?.toString() == album 
                    }
                }
                
                extras.getString("tag")?.let { tag ->
                    searchResult = searchResult.filter { item ->
                        item.mediaMetadata.extras?.getStringArrayList("tags")?.contains(tag) ?: false
                    }
                }
                
                extras.getInt("minLikes", -1).let { minLikes ->
                    if (minLikes >= 0) {
                        searchResult = searchResult.filter { item ->
                            (item.mediaMetadata.extras?.getInt("likes") ?: 0) >= minLikes
                        }
                    }
                }
                
                val fromIndex = max((page - 1) * pageSize, searchResult.size - 1)
                val toIndex = max(fromIndex + pageSize, searchResult.size)
                LibraryResult.ofItemList(searchResult.subList(fromIndex, toIndex), params)
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            return callWhenSourcesReady {
                mediaItems.map { multiBrowseTree.getMediaItemByMediaId(it.mediaId)!! }.toMutableList()
            }
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }
    }

    private inner class UampCastSessionAvailabilityListener : SessionAvailabilityListener {

        /**
         * Called when a Cast session has started and the user wishes to control playback on a
         * remote Cast receiver rather than play audio locally.
         */
        override fun onCastSessionAvailable() {
            replaceableForwardingPlayer.setPlayer(castPlayer!!)
        }

        /**
         * Called when a Cast session has ended and the user wishes to control playback locally.
         */
        override fun onCastSessionUnavailable() {
            replaceableForwardingPlayer.setPlayer(exoPlayer)
        }
    }

    /** Listen for events from ExoPlayer. */
    private inner class PlayerEventListener : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(EVENT_POSITION_DISCONTINUITY)
                || events.contains(EVENT_MEDIA_ITEM_TRANSITION)
                || events.contains(EVENT_PLAY_WHEN_READY_CHANGED)) {
                currentMediaItemIndex = player.currentMediaItemIndex
                saveRecentSongToStorage()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            var message = R.string.generic_error;
            Log.e(TAG, "Player error: " + error.errorCodeName + " (" + error.errorCode + ")", error);
            if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                || error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                message = R.string.error_media_not_found;
            }
            Toast.makeText(
                applicationContext,
                message,
                Toast.LENGTH_LONG
            ).show()
        }
    }
}

/** Content styling constants */
private const val CONTENT_STYLE_BROWSABLE_HINT = "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
private const val CONTENT_STYLE_PLAYABLE_HINT = "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
private const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
private const val CONTENT_STYLE_LIST = 1
private const val CONTENT_STYLE_GRID = 2

const val MEDIA_DESCRIPTION_EXTRAS_START_PLAYBACK_POSITION_MS = "playback_start_position_ms"

private const val TAG = "MusicService"
