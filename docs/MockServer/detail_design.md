# Music Server 模拟接口详细设计文档

## 1. 概述
本项目旨在将原本的本地音乐测试服务器升级为一个功能完善的 Mock API Server。保留原有的本地文件扫描能力，新增歌单管理、点赞统计、流行度排序以及多种推荐算法模拟。

核心变更：
- **移除** 临时的 `ALL` 和 `RECOMMAND` 文件夹设计，回归单一 `music/` 目录。
- **新增** `collection/` 目录用于持久化存储歌单。
- **重构** API 接口，提供分页、搜索、推荐等特定业务场景模拟。

## 2. 数据结构定义

### 2.1 单曲对象 (Song)
在原有基础上增加 `isList` 和 `likes` 字段。

```json
{
  "id": "wake_up_01",
  "title": "Intro - The Way Of Waking Up",
  "album": "Wake Up",
  "artist": "The Kyoto Connection",
  "genre": "Electronic",
  "source": "http://localhost/music/xxx.mp3",
  "image": "http://localhost/images/xxx.jpg",
  "trackNumber": 1,
  "totalTrackCount": 13,
  "duration": 90,
  "site": "",
  "tags": ["relax", "electronic"],
  
  // 新增属性
  "isList": false,      // 单曲固定为 false
  "likes": 123          // 随机生成的点赞数 (0-1000)，启动时随机分配，用于计算热门度
}
```

### 2.2 歌单对象 (Playlist)
每个歌单单独存储为一个 JSON 文件。

```json
{
  "id": "playlist_uuid_123",
  "title": "工作专注歌单",
  "creatorId": "022",       // 默认值 022 (匿名)
  "description": "适合写代码时候听的背景音乐",
  "tags": ["work", "focus"],
  "isList": true,           // 歌单固定为 true
  "songs": [                // 包含完整的歌曲对象数组
    { "id": "song_1", "title": "...", "isList": false, ... },
    { "id": "song_2", "title": "...", "isList": false, ... }
  ]
}
```

## 3. 文件系统与存储策略

| 目录/文件 | 说明 | 读写策略 |
| :--- | :--- | :--- |
| `music/` | 仅存放 .mp3, .flac 等音频及 .lrc 歌词文件 | 只读扫描，不修改文件内容 |
| `collection/` | 存放歌单 JSON 文件 (如 `playlist_123.json`) | 允许 API 进行创建(POST)和读取(GET) |
| `images/` | 存放封面图片 | 扫描生成，只读 |
| `music_list.json` | 全量单曲索引 | **每次启动时**根据 `music/` 目录扫描生成 |

## 4. 接口定义 (API Endpoints)

所有 API 建议增加 `/api` 前缀。

### 4.1 歌曲查询接口

#### A. 分页获取/搜索歌曲
**URL:** `GET /api/songs`

**参数 (Query Params):**
- `page`: 页码 (默认 1)
- `pageSize`: 每页数量 (默认 20, 最大 50)
- `keyword`: 搜索关键字 (可选，匹配 title, album, artist, genre, tags)
- `genre`: 精确筛选流派 (可选)
- `tag`: 精确筛选标签 (可选)

**逻辑:**
1. 基于全量歌曲列表。
2. 应用 keyword/genre/tag 过滤。
3. 返回分页后的数据。

---

### 4.2 歌单管理接口

#### C. 获取歌单列表 (搜索)
**URL:** `GET /api/playlists`

**参数:**
- `keyword`: 搜索关键字 (匹配 title, description, tags)
- `includeSong`: 是否根据包含的歌曲名搜索 (可选，true/false)

**逻辑:**
1. 遍历 `collection/` 目录下所有 JSON。
2. 过滤符合条件的歌单。
3. 返回歌单摘要列表 (为减少流量，可选择不返回 `songs` 详情，仅返回 ID 和基础信息)。

#### D. 获取特定歌单
**URL:** `GET /api/playlists/{id}`
**逻辑:** 读取并返回指定 ID 的完整歌单 JSON。

#### E. 创建新歌单
**URL:** `POST /api/playlists`

**Body:**
```json
{
  "title": "新建歌单",
  "description": "描述",
  "tags": ["tag1"],
  "songIds": ["wake_up_01", "song_2"] // 客户端只传 ID
}
```
**逻辑:**
1. 生成唯一歌单 ID。
2. `creatorId` 默认为 "022"。
3. 验证 `songIds` 是否在现有库中存在。
4. **组装**: 从内存库中查找完整的 Song Object 填入 `songs` 数组。
5. 保存为 `collection/{id}.json`。

---

### 4.3 推荐与特色接口

#### F. 今日推荐 (Random Hit)
**URL:** `GET /api/recommend/daily`
**逻辑:** 从全量歌曲中随机抽取 10 首返回。

#### G. 猜你喜欢 (Guess Like)
**URL:** `GET /api/recommend/guess`
**逻辑:** (模拟) 目前逻辑同上，随机抽取 10 首。后续可扩展基于 Tags 的加权随机。

#### H. 最近流行 (Top List)
**URL:** `GET /api/recommend/popular`
**逻辑:**
1. 获取全量歌曲。
2. 根据 `likes` 字段倒序排列。
3. 取前 20 首。

#### I. 宝藏歌单 (Featured Playlists)
**URL:** `GET /api/playlists/treasured`
**逻辑:**
1. 读取 `collection/` 下所有歌单。
2. 随机抽取 5 个返回。

## 5. 实现阶段规划

1.  **环境还原**: 清除 `ALL`, `RECOMMAND` 相关代码，恢复 `music/` 扫描逻辑。
2.  **核心重构**:
    - 修改 `extract_metadata` 增加 `isList` 和 `likes` 默认值。
    - 实现 `load_metadata` 和 `save_metadata` 处理点赞持久化。
3.  **歌单层**:
    - 实现 `PlaylistManager` 类，负责增删改查 `collection/` 目录。
4.  **路由实现**:默认值。
    - 在扫描流程中为 `likes` 分配随机值 (e.g. 0-1000)
    - 实现搜索和分页辅助函数。
