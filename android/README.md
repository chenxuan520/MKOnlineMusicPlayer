# MKOnline 在线音乐 · Android 客户端

原生 Kotlin + Jetpack Compose + Media3 (ExoPlayer) 实现的安卓端在线音乐播放器，
与 Web 端共用同一套 `api.php` 后端接口。

## 功能

- 服务器地址 + 入口密码认证（对齐 Web 端 `types=auth`）
- 搜索（网易云 / 酷狗 / QQ音乐，VIP 过滤，分页自动加载）
- 内置网易云榜单歌单（36 个，进入详情页才加载，不预加载全集）
- 播放：后台播放、通知栏 / 锁屏媒体控制、三种播放模式（顺序 / 随机 / 单曲循环）
- 桌面挂件：4×2 播放控制条（封面 + 歌名/进度条 + 上一首/播放暂停/下一首，反样式 QQ 音乐）
- 歌词：LRC 解析 + 翻译合并 + 自动滚动高亮
- 播放队列管理、播放历史（本地持久化）
- 收藏（服务端 `types=collections`，增删查，支持下拉刷新）
- 评论（热门 + 全部，支持滚动分页 + 下拉刷新）
- 封面：Coil 缓存 + 进程级 CoverCache 内存缓存（播放补全后列表共享），避免重复调 API
- 主题：设置页可选「跟随系统 / 浅色 / 深色」
- 下载（经服务端缓存后用系统 DownloadManager 落到公共 Music 目录）
- 智能推荐（外部推荐服务，对齐 Web 端推荐流程）

## 构建

### 手动构建 (CI/CD)

本仓库配置了 GitHub Actions 自动构建，无需本地安装环境。

- **每次提交/PR 到 `android/` 目录**：自动构建 Debug APK，产物可在 Actions 页面下载（保留 7 天）
- **打 `v*` 开头的 tag**：自动构建 Release APK 并上传 GitHub Release 下载页

```bash
# 打 tag 触发 Release 构建
git tag v1.0.0-android -m "Android 客户端 v1.0.0"
git push origin v1.0.0-android
```

### 本地构建（可选）

如需本地编译，最小命令行方案（无需 Android Studio）：

| 组件 | 大小 | 安装命令 |
|---|---|---|
| JDK 17 | ~300 MB | `brew install openjdk@17` |
| Android cmd-line tools | ~150 MB | `brew install --cask android-commandlinetools` |
| SDK Platform 35 + Build-Tools | ~300 MB | `sdkmanager "platforms;android-35" "build-tools;35.0.0"` |
| Gradle 8.9 | ~150 MB | `brew install gradle` 或官网下载 |

```bash
cd android
./gradlew assembleDebug          # 生成 debug APK
./gradlew installDebug           # 安装到已连接设备
```

APK 产物在 `android/app/build/outputs/apk/debug/`。

## 配置

1. 首次启动 App，在登录页填写**服务器地址**（部署了 `api.php` 的站点，如 `http://192.168.1.2:4000`）和**访问密码**（即 `api.php` 中的 `MKPLAYER_PASSWORD`，默认 `123456`）。
2. 登录后进入主界面。可在「设置」Tab 中修改服务器地址、VIP 过滤、推荐服务 Token 等。

### 推荐服务（可选）

如需智能推荐功能，在「设置」中填写推荐服务域名和 Bearer Token。
推荐服务接口路径为 `/api/v1/recommend/*`，需与 `api.php` 同域部署（或通过反向代理同域化）。
详见仓库根目录的 `nginx.conf` 示例。

## 架构

```
app/src/main/java/com/mkonline/player/
├── MkApp.kt                       Application + AppContainer（手动 DI）
├── MainActivity.kt                ComponentActivity + Compose 入口
├── data/
│   ├── Models.kt                  Song / Sheet / CommentItem / LyricLine
│   ├── Prefs.kt                   SharedPreferences 封装（设置 + 队列 + 历史）
│   ├── Api.kt                     OkHttp 封装 api.php + 推荐服务
│   ├── LrcParser.kt               LRC 歌词解析
│   └── SheetIds.kt                内置榜单 ID 列表
├── playback/
│   ├── PlaybackService.kt         Media3 MediaSessionService + ResolvingDataSource
│   └── PlayerManager.kt           MediaController 封装、队列、歌词、收藏状态
├── ui/
│   ├── AppNav.kt                  NavHost + 底部导航 + MiniPlayer
│   ├── theme/Theme.kt             Compose 深色主题
│   ├── components/                CoverImage / SongRow / MiniPlayer 等通用组件
│   └── screens/                   登录 / 搜索 / 歌单 / 播放 / 收藏 / 历史 / 评论 / 推荐 / 设置
└── util/
    └── Downloads.kt               下载辅助
```

### 播放地址解析

歌曲播放地址有时效性，MediaItem 使用 `mk://source/id` 占位 URI。
播放时 `ResolvingDataSource` 同步调用 `api.php?types=url` 解析为真实地址，
与 Web 端 "播放时再临时抓取" 的策略一致。

网易云空链接自动回退到 `music.163.com/song/media/outer/url` 外链。

## 注意事项

- `network_security_config.xml` 已放开明文流量（`cleartextTrafficPermitted="true"`），
  因为自建 `api.php` 和酷狗/QQ 音源常返回 `http://` 链接。
  若你的部署全链路 HTTPS，可将其改为 `false`。
- 队列和历史保存在应用私有 SharedPreferences 中，卸载 App 后清除。
- 收藏保存在服务端 `collections/collections.json`。
