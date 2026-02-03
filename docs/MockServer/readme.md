# 本地http/https音乐服务器（测试用）
这是一个使用Python搭建的本地音乐服务器，支持HTTP和HTTPS协议，适用于向外提供音乐源用于测试。

## Json源格式定义
```json
{
  "music": [
    {
      "id": "wake_up_01",
      "title": "Intro - The Way Of Waking Up (feat. Alan Watts)",
      "album": "Wake Up",
      "artist": "The Kyoto Connection",
      "genre": "Electronic",
      "source": "/music/xxx.mp3",
      "image": "/images/xxx.jpg",
      "trackNumber": 1,
      "totalTrackCount": 13,
      "duration": 90,
      "site": "http://freemusicarchive.org/music/The_Kyoto_Connection/Wake_Up_1957/",
      "tags": ["relax","morning","electronic"],
      "isList": false,
      "likes": 123
    },
    {...},
    ]
}
```

## 音乐文件
将音乐文件放置在`music/`目录下，支持多种音频格式（如MP3、WAV、FLAC等），扁平结构。

## 自动解析
服务器会自动解析`music/`目录下的音乐文件，并生成全库索引`music_list.json`（服务启动时更新）。
- 默认生成的id为文件名（不含扩展名，空格用下划线替代，忽略特殊字符）
- site字段默认值为空字符串
- tags字段默认值为空列表
- isList字段默认值为false
- likes字段为随机roll值（0~1000）
- source与image为相对路径，避免IP变化导致失效

## 歌单存储
根目录下的`collection/`用于持久化存储歌单JSON，每个歌单一个文件。歌单包含：歌单ID、歌单名称、创建者ID（默认022）、描述、标签列表、歌曲数组（完整单曲JSON）。

## 客户端接口使用说明

### BaseURL 与相对路径
接口返回的 `source` 与 `image` 为相对路径，客户端需自行拼接 BaseURL。

示例：
- BaseURL：`http://127.0.0.1:8000`
- source：`/music/xxx.mp3`
- 完整地址：`http://127.0.0.1:8000/music/xxx.mp3`

### 完整 JSON 结构

#### Song
```json
{
  "id": "wake_up_01",
  "title": "Intro - The Way Of Waking Up",
  "album": "Wake Up",
  "artist": "The Kyoto Connection",
  "genre": "Electronic",
  "source": "/music/xxx.mp3",
  "image": "/images/xxx.jpg",
  "trackNumber": 1,
  "totalTrackCount": 13,
  "duration": 90,
  "site": "",
  "tags": ["relax", "electronic"],
  "isList": false,
  "likes": 123
}
```

#### Playlist
```json
{
  "id": "playlist_uuid_123",
  "title": "工作专注歌单",
  "creatorId": "022",
  "description": "适合写代码时候听的背景音乐",
  "tags": ["work", "focus"],
  "isList": true,
  "songs": [
    { "id": "song_1", "title": "...", "isList": false, "likes": 123 }
  ]
}
```

### 1. 歌曲列表与查询
- 分页/查询：`GET /api/songs`
  - 参数：`page`（默认1）、`pageSize`（最大20）、`keyword`、`title`、`album`、`artist`、`genre`、`tag`
  - 返回：`{ page, pageSize, total, items }`

### 2. 推荐与流行
- 今日推荐：`GET /api/recommend/daily`（随机10首）
- 猜你喜欢：`GET /api/recommend/guess`（随机10首）
- 最近流行：`GET /api/recommend/popular`（likes排序前20首）

### 3. 歌单
- 获取歌单列表：`GET /api/playlists`
  - 参数：`keyword`、`title`、`description`、`tag`、`song`（歌单内歌曲关键词）
- 获取单个歌单：`GET /api/playlists/{id}`
- 创建歌单：`POST /api/playlists`
  - Body示例：
    ```json
    {
      "title": "新建歌单",
      "description": "描述",
      "tags": ["work", "focus"],
      "songIds": ["wake_up_01", "song_2"]
    }
    ```
- 宝藏歌单：`GET /api/playlists/treasured`（随机5个歌单）

## 运行环境
- Python 3.x