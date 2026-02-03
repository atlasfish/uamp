# UAMP 媒体库客户端开发文档

本文档面向外部应用开发者，说明如何连接并使用 UAMP（Universal Android Music Player）提供的媒体库服务。

## 目录

1. [概述](#概述)
2. [媒体库服务路径](#媒体库服务路径)
3. [MediaBrowser 连接方法](#mediabrowser-连接方法)
4. [媒体库结构](#媒体库结构)
5. [搜索功能](#搜索功能)
6. [支持的搜索参数](#支持的搜索参数)
7. [示例代码](#示例代码)

---

## 概述

UAMP 实现了 Android 的 `MediaLibraryService`，允许外部应用（如 Android Auto、Google Assistant 等）通过 MediaBrowser API 访问其音乐库。

### 主要特性

- 支持媒体浏览（Browse）
- 支持媒体搜索（Search）
- 支持高级过滤参数
- 支持分页加载
- 兼容 Media3 API

---

## 媒体库服务路径

### Service 组件名称

```
Package: com.example.android.uamp
Class: com.example.android.uamp.media.MusicService
```

### 完整 ComponentName

```kotlin
ComponentName("com.example.android.uamp", "com.example.android.uamp.media.MusicService")
```

### Manifest 配置

UAMP 的媒体服务在 `app/src/main/AndroidManifest.xml` 中声明：

```xml
<service
    android:name=".media.MusicService"
    android:enabled="true"
    android:exported="true"
    android:foregroundServiceType="mediaPlayback">
    
    <intent-filter>
        <action android:name="androidx.media3.session.MediaLibraryService"/>
        <action android:name="android.media.browse.MediaBrowserService" />
    </intent-filter>
</service>
```

---

## MediaBrowser 连接方法

### 1. 使用 Media3 API（推荐）

Media3 是 Google 最新的媒体框架，推荐使用此方法连接 UAMP。

#### 添加依赖

在您的 `build.gradle` 中添加：

```gradle
dependencies {
    implementation 'androidx.media3:media3-session:1.1.1'
    implementation 'com.google.guava:guava:31.1-android'
}
```

#### 连接示例

```kotlin
import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.guava.await

class UampMediaConnection(private val context: Context) {
    
    private var mediaBrowser: MediaBrowser? = null
    
    suspend fun connect() {
        val serviceComponent = ComponentName(
            "com.example.android.uamp",
            "com.example.android.uamp.media.MusicService"
        )
        
        val sessionToken = SessionToken(context, serviceComponent)
        
        mediaBrowser = MediaBrowser.Builder(context, sessionToken)
            .setListener(MyBrowserListener())
            .buildAsync()
            .await()
    }
    
    suspend fun getLibraryRoot(): MediaItem? {
        return mediaBrowser?.getLibraryRoot(null)?.await()?.value
    }
    
    suspend fun getChildren(parentId: String, page: Int = 0, pageSize: Int = 100): List<MediaItem> {
        return mediaBrowser?.getChildren(parentId, page, pageSize, null)?.await()?.value
            ?: emptyList()
    }
    
    fun disconnect() {
        mediaBrowser?.release()
        mediaBrowser = null
    }
    
    private inner class MyBrowserListener : MediaBrowser.Listener {
        override fun onDisconnected(controller: MediaController) {
            // 处理断开连接
        }
    }
}
```

### 2. 使用兼容 API

如果需要支持较旧的 Android 版本，可以使用兼容库：

```gradle
dependencies {
    implementation 'androidx.media:media:1.6.0'
}
```

```kotlin
import android.content.ComponentName
import android.content.Context
import android.support.v4.media.MediaBrowserCompat

class UampMediaConnectionCompat(context: Context) {
    
    private val mediaBrowser = MediaBrowserCompat(
        context,
        ComponentName("com.example.android.uamp", 
                     "com.example.android.uamp.media.MusicService"),
        connectionCallback,
        null
    )
    
    private val connectionCallback = object : MediaBrowserCompat.ConnectionCallback() {
        override fun onConnected() {
            // 连接成功
        }
        
        override fun onConnectionFailed() {
            // 连接失败
        }
    }
    
    fun connect() {
        mediaBrowser.connect()
    }
    
    fun disconnect() {
        mediaBrowser.disconnect()
    }
}
```

---

## 媒体库结构

### Root Media IDs

UAMP 提供以下根目录：

| Media ID | 说明 | 用途 |
|----------|------|------|
| `/` | 主浏览根目录 | 默认浏览入口，包含所有分类 |
| `__RECENT__` | 最近播放 | 显示最近播放的歌曲 |

### 分类 Media IDs

主根目录（`/`）下包含以下分类：

| Media ID | 中文名称 | 说明 |
|----------|----------|------|
| `__DAILY_RECOMMEND__` | 今日推荐 | 每日推荐歌曲列表 |
| `__GUESS_LIKE__` | 猜你喜欢 | 个性化推荐 |
| `__POPULAR__` | 最近流行 | 热门歌曲 |
| `__TREASURED_PLAYLISTS__` | 精选歌单 | 精选播放列表 |
| `__ALL_SONGS__` | 全部歌曲 | 所有可用歌曲 |
| `__ALL_PLAYLISTS__` | 全部歌单 | 所有播放列表 |

### 播放列表展开

当 parentId 以 `playlist_` 开头时，系统会展开该播放列表的歌曲：

```kotlin
// 获取播放列表中的歌曲
val playlistSongs = mediaBrowser.getChildren("playlist_12345", 0, 100, null).await()
```

### 分页支持

所有的 `getChildren` 调用都支持分页，以避免超过 Binder 传输限制（1MB）：

```kotlin
// 获取第一页，每页100个项目
val firstPage = mediaBrowser.getChildren(parentId, 0, 100, null).await()

// 获取第二页
val secondPage = mediaBrowser.getChildren(parentId, 1, 100, null).await()
```

---

## 搜索功能

UAMP 提供强大的搜索功能，支持文本查询和参数过滤。

### 基本搜索

```kotlin
import androidx.media3.session.LibraryParams

suspend fun search(query: String): List<MediaItem> {
    // 发起搜索
    mediaBrowser?.search(query, null)?.await()
    
    // 获取搜索结果
    return mediaBrowser?.getSearchResult(query, 0, 100, null)?.await()?.value
        ?: emptyList()
}
```

### 高级搜索流程

1. **发起搜索**：调用 `search()` 方法
2. **等待通知**：服务会通过 `notifySearchResultChanged` 通知结果数量
3. **获取结果**：调用 `getSearchResult()` 获取实际的搜索结果

```kotlin
suspend fun searchWithCallback(query: String, params: LibraryParams?) {
    // 步骤 1: 发起搜索
    mediaBrowser?.search(query, params)?.await()
    
    // 步骤 2: 获取搜索结果（分页）
    val results = mediaBrowser?.getSearchResult(query, 0, 100, params)?.await()?.value
        ?: emptyList()
    
    // 处理结果
    results.forEach { mediaItem ->
        println("Found: ${mediaItem.mediaMetadata.title}")
    }
}
```

---

## 支持的搜索参数

### 查询字符串（query）

查询字符串会在以下字段中进行搜索：
- 歌曲标题（title）
- 艺术家（artist）
- 专辑（album）
- 流派（genre）

### 扩展过滤参数（extras）

通过 `LibraryParams` 的 extras 可以传递额外的过滤参数：

| 参数名 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `genre` | String | 按流派过滤 | "Rock", "Pop", "Jazz" |
| `artist` | String | 按艺术家过滤 | 精确匹配艺术家或专辑艺术家 |
| `album` | String | 按专辑过滤 | 精确匹配专辑标题 |
| `tag` | String | 按标签过滤 | 歌曲必须包含该标签 |
| `minLikes` | Int | 按点赞数过滤 | 最小点赞数阈值 |

### 过滤参数使用示例

#### 按流派过滤

```kotlin
import android.os.Bundle
import androidx.media3.session.LibraryParams

suspend fun searchByGenre(genre: String) {
    val extras = Bundle().apply {
        putString("genre", genre)
    }
    val params = LibraryParams.Builder().setExtras(extras).build()
    
    mediaBrowser?.search("", params)?.await()
    val results = mediaBrowser?.getSearchResult("", 0, 100, params)?.await()?.value
        ?: emptyList()
}
```

#### 按艺术家过滤

```kotlin
suspend fun searchByArtist(artist: String) {
    val extras = Bundle().apply {
        putString("artist", artist)
    }
    val params = LibraryParams.Builder().setExtras(extras).build()
    
    mediaBrowser?.search("", params)?.await()
    val results = mediaBrowser?.getSearchResult("", 0, 100, params)?.await()?.value
        ?: emptyList()
}
```

#### 按专辑过滤

```kotlin
suspend fun searchByAlbum(album: String) {
    val extras = Bundle().apply {
        putString("album", album)
    }
    val params = LibraryParams.Builder().setExtras(extras).build()
    
    mediaBrowser?.search("", params)?.await()
    val results = mediaBrowser?.getSearchResult("", 0, 100, params)?.await()?.value
        ?: emptyList()
}
```

#### 按标签过滤

```kotlin
suspend fun searchByTag(tag: String) {
    val extras = Bundle().apply {
        putString("tag", tag)
    }
    val params = LibraryParams.Builder().setExtras(extras).build()
    
    mediaBrowser?.search("", params)?.await()
    val results = mediaBrowser?.getSearchResult("", 0, 100, params)?.await()?.value
        ?: emptyList()
}
```

#### 按点赞数过滤

```kotlin
suspend fun searchByMinLikes(minLikes: Int) {
    val extras = Bundle().apply {
        putInt("minLikes", minLikes)
    }
    val params = LibraryParams.Builder().setExtras(extras).build()
    
    mediaBrowser?.search("", params)?.await()
    val results = mediaBrowser?.getSearchResult("", 0, 100, params)?.await()?.value
        ?: emptyList()
}
```

#### 组合多个过滤器

```kotlin
suspend fun searchWithMultipleFilters(
    query: String,
    genre: String? = null,
    artist: String? = null,
    minLikes: Int? = null
) {
    val extras = Bundle().apply {
        genre?.let { putString("genre", it) }
        artist?.let { putString("artist", it) }
        minLikes?.let { putInt("minLikes", it) }
    }
    val params = LibraryParams.Builder().setExtras(extras).build()
    
    mediaBrowser?.search(query, params)?.await()
    val results = mediaBrowser?.getSearchResult(query, 0, 100, params)?.await()?.value
        ?: emptyList()
}

// 使用示例：搜索包含 "love" 的 Rock 类型歌曲，且点赞数大于 100
val results = searchWithMultipleFilters(
    query = "love",
    genre = "Rock",
    minLikes = 100
)
```

---

## 示例代码

### 完整的连接和浏览示例

```kotlin
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.session.LibraryParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.guava.await

class UampClient(private val context: Context) {
    
    private var mediaBrowser: MediaBrowser? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    
    /**
     * 连接到 UAMP 媒体服务
     */
    suspend fun connect(): Boolean {
        return try {
            val serviceComponent = ComponentName(
                "com.example.android.uamp",
                "com.example.android.uamp.media.MusicService"
            )
            
            val sessionToken = SessionToken(context, serviceComponent)
            
            mediaBrowser = MediaBrowser.Builder(context, sessionToken)
                .setListener(object : MediaBrowser.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        println("MediaBrowser disconnected")
                    }
                })
                .buildAsync()
                .await()
            
            true
        } catch (e: Exception) {
            println("Failed to connect: ${e.message}")
            false
        }
    }
    
    /**
     * 获取媒体库根目录
     */
    suspend fun getRootId(): String? {
        return mediaBrowser?.getLibraryRoot(null)?.await()?.value?.mediaId
    }
    
    /**
     * 浏览指定目录下的媒体项目
     */
    suspend fun browse(parentId: String, page: Int = 0, pageSize: Int = 100): List<MediaItem> {
        return mediaBrowser?.getChildren(parentId, page, pageSize, null)?.await()?.value
            ?: emptyList()
    }
    
    /**
     * 获取所有分类
     */
    suspend fun getAllCategories(): List<MediaItem> {
        return browse("/")
    }
    
    /**
     * 获取今日推荐
     */
    suspend fun getDailyRecommendations(): List<MediaItem> {
        return browse("__DAILY_RECOMMEND__")
    }
    
    /**
     * 获取猜你喜欢
     */
    suspend fun getGuessLike(): List<MediaItem> {
        return browse("__GUESS_LIKE__")
    }
    
    /**
     * 获取热门歌曲
     */
    suspend fun getPopular(): List<MediaItem> {
        return browse("__POPULAR__")
    }
    
    /**
     * 获取所有歌曲
     */
    suspend fun getAllSongs(): List<MediaItem> {
        return browse("__ALL_SONGS__")
    }
    
    /**
     * 获取所有播放列表
     */
    suspend fun getAllPlaylists(): List<MediaItem> {
        return browse("__ALL_PLAYLISTS__")
    }
    
    /**
     * 获取播放列表中的歌曲
     */
    suspend fun getPlaylistSongs(playlistId: String): List<MediaItem> {
        return browse("playlist_$playlistId")
    }
    
    /**
     * 搜索媒体项目
     */
    suspend fun search(query: String, params: LibraryParams? = null): List<MediaItem> {
        mediaBrowser?.search(query, params)?.await()
        return mediaBrowser?.getSearchResult(query, 0, 100, params)?.await()?.value
            ?: emptyList()
    }
    
    /**
     * 按流派搜索
     */
    suspend fun searchByGenre(genre: String): List<MediaItem> {
        val extras = Bundle().apply {
            putString("genre", genre)
        }
        val params = LibraryParams.Builder().setExtras(extras).build()
        return search("", params)
    }
    
    /**
     * 按艺术家搜索
     */
    suspend fun searchByArtist(artist: String): List<MediaItem> {
        val extras = Bundle().apply {
            putString("artist", artist)
        }
        val params = LibraryParams.Builder().setExtras(extras).build()
        return search("", params)
    }
    
    /**
     * 高级搜索
     */
    suspend fun advancedSearch(
        query: String = "",
        genre: String? = null,
        artist: String? = null,
        album: String? = null,
        tag: String? = null,
        minLikes: Int? = null
    ): List<MediaItem> {
        val extras = Bundle().apply {
            genre?.let { putString("genre", it) }
            artist?.let { putString("artist", it) }
            album?.let { putString("album", it) }
            tag?.let { putString("tag", it) }
            minLikes?.let { putInt("minLikes", it) }
        }
        val params = LibraryParams.Builder().setExtras(extras).build()
        return search(query, params)
    }
    
    /**
     * 获取单个媒体项目的详细信息
     */
    suspend fun getMediaItem(mediaId: String): MediaItem? {
        return mediaBrowser?.getItem(mediaId)?.await()?.value
    }
    
    /**
     * 播放指定的媒体项目
     */
    fun play(mediaItem: MediaItem) {
        mediaBrowser?.setMediaItem(mediaItem)
        mediaBrowser?.prepare()
        mediaBrowser?.play()
    }
    
    /**
     * 断开连接
     */
    fun disconnect() {
        mediaBrowser?.release()
        mediaBrowser = null
    }
}

// 使用示例
fun example(context: Context) {
    val client = UampClient(context)
    
    CoroutineScope(Dispatchers.Main).launch {
        // 连接到服务
        if (client.connect()) {
            println("Connected to UAMP")
            
            // 获取所有分类
            val categories = client.getAllCategories()
            categories.forEach { category ->
                println("Category: ${category.mediaMetadata.title}")
            }
            
            // 获取今日推荐
            val recommendations = client.getDailyRecommendations()
            println("Daily recommendations: ${recommendations.size} items")
            
            // 搜索歌曲
            val searchResults = client.search("love")
            println("Search results: ${searchResults.size} items")
            
            // 按流派搜索
            val rockSongs = client.searchByGenre("Rock")
            println("Rock songs: ${rockSongs.size} items")
            
            // 高级搜索
            val advancedResults = client.advancedSearch(
                query = "summer",
                genre = "Pop",
                minLikes = 50
            )
            println("Advanced search results: ${advancedResults.size} items")
            
            // 断开连接
            client.disconnect()
        }
    }
}
```

### Activity 中的使用示例

```kotlin
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class MusicBrowserActivity : AppCompatActivity() {
    
    private lateinit var uampClient: UampClient
    private lateinit var recyclerView: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_music_browser)
        
        recyclerView = findViewById(R.id.recyclerView)
        uampClient = UampClient(this)
        
        lifecycleScope.launch {
            // 连接到 UAMP
            if (uampClient.connect()) {
                // 加载音乐分类
                loadCategories()
            }
        }
    }
    
    private suspend fun loadCategories() {
        val categories = uampClient.getAllCategories()
        // 更新 UI
        runOnUiThread {
            // 显示分类列表
            updateRecyclerView(categories)
        }
    }
    
    private fun updateRecyclerView(items: List<MediaItem>) {
        // 更新 RecyclerView 适配器
    }
    
    override fun onDestroy() {
        super.onDestroy()
        uampClient.disconnect()
    }
}
```

---

## 最佳实践

### 1. 连接管理

- 在 Activity/Fragment 的生命周期中妥善管理连接
- 使用完毕后及时调用 `disconnect()`
- 考虑使用单例模式管理连接

### 2. 异步处理

- 所有 MediaBrowser 操作都是异步的
- 使用 Kotlin 协程处理异步调用
- 避免在主线程中阻塞等待结果

### 3. 分页加载

- 对于大型媒体库，使用分页避免一次性加载过多数据
- 推荐每页 50-100 个项目
- 实现懒加载提升用户体验

### 4. 错误处理

- 始终处理连接失败的情况
- 捕获并处理可能的异常
- 提供用户友好的错误提示

### 5. 搜索优化

- 对于复杂搜索，优先使用过滤参数而非文本查询
- 组合使用多个过滤器以精确定位结果
- 考虑实现搜索建议和自动完成

---

## 故障排查

### 连接失败

**问题**：无法连接到 UAMP 服务

**解决方案**：
1. 确认 UAMP 应用已安装
2. 检查 package 名称和 class 名称是否正确
3. 确认 UAMP 服务已在 Manifest 中正确声明为 exported
4. 检查设备日志查看详细错误信息

### 搜索无结果

**问题**：搜索返回空列表

**解决方案**：
1. 确认媒体库已加载完成
2. 检查搜索参数是否正确
3. 尝试使用空字符串查询获取所有项目
4. 检查过滤参数的值是否精确匹配

### 性能问题

**问题**：浏览或搜索响应缓慢

**解决方案**：
1. 使用分页减少单次传输的数据量
2. 避免频繁的搜索请求
3. 考虑实现本地缓存
4. 使用更精确的过滤参数减少结果集

---

## 技术支持

如有问题或建议，请通过以下方式联系：

- GitHub Issues: [UAMP Repository](https://github.com/android/UAMP/issues)
- Stack Overflow: 使用 `android-uamp` 标签

---

## 版本历史

- **v1.0** (2024): 初始版本，包含基本浏览和搜索功能
- **v1.1** (2024): 增强搜索功能，支持高级过滤参数
- **v1.2** (2024): 支持多个 API 数据源和播放列表展开

---

## 许可证

本文档基于 UAMP 项目，遵循 Apache 2.0 许可证。

Copyright 2024 Google Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
