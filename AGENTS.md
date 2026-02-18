# MKOnlineMusicPlayer – 架构速览

## 1) 项目概览

MKOnlineMusicPlayer 是一个运行在浏览器里的在线音乐播放器。

- 前端：静态页面 `index.html`，使用 jQuery + layui 组件，并加载仓库内的多个 JS 模块（`js/*.js`）。
- 后端：单一 PHP 入口 `api.php`，通过 `plugns/Meting.php` 代理各音乐源接口，输出歌曲链接/封面/歌词/歌单/评论等数据；通过 `plugns/Download.php` 处理下载；并将收藏持久化到磁盘。
- 可选外部服务：浏览器侧调用的“智能推荐”服务，接口路径为 `/api/v1/recommend/*`（同域反向代理示例见 `nginx.conf`）。

**核心数据流**

- 搜索/播放：`index.html` 加载脚本；`js/ajax.js` 以 `types=search/url/pic/lyric/playlist/comments` 等参数调用 `api.php`。
- 播放状态：由 `js/functions.js` 的本地存储封装写入 `localStorage`（`mkPlayer2_*` 前缀）。
- 收藏（“我的收藏”）：通过 `api.php?types=collections&action=...` 读写 `collections/collections.json`；前端在 `js/functions.js` 中渲染。
- 下载：浏览器请求 `api.php?types=download` → 服务端把音频保存到 `temp/<source>/...mp3`，并返回 `/temp/...` 下可访问的 URL。

**入口**

- 页面入口：`index.html`
- 后端 API：`api.php`

## 2) 启动与常用命令

本仓库不依赖 Node/打包构建流程，直接以静态资源 + PHP 方式提供服务。

**本地（PHP 内置服务器）**

- 启动：`php -S 127.0.0.1:4000`
- 访问：`http://127.0.0.1:4000`

**Docker（Apache + PHP 7.4）**

- 启动：`docker-compose up -d`
- 停止：`docker-compose down`
- 快捷脚本：`./run.sh`（先 `docker-compose down` 再 `docker-compose up -d`）
- 访问：`http://localhost:4000`

**反向代理（可选，用于推荐服务同域部署）**

- 示例配置：`nginx.conf`
  - 将 `/api/v1/recommend/` 转发到 `http://127.0.0.1:8080`
  - 其余路径转发到 PHP 应用 upstream（`webapp`）

## 3) 代码风格与约定

本仓库使用原生 JS（无打包器）+ 过程式 PHP。

**JavaScript 结构**

- 全局配置对象：`mkPlayer`（`js/player.js`，包含 API 地址、功能开关、默认设置等）。
- 全局运行态：`rem`（`js/player.js` / `js/functions.js`）。
- 歌单模型：`musicList`（`js/musicList.js`）。
  - 文件中明确提示：前 3 个为系统保留列表（搜索/正在播放/播放历史），不要改动，否则可能导致异常。
- 网络请求模块：`js/ajax.js`（与 `api.php` 及推荐服务的全部请求）。
- UI/工具函数：`js/functions.js`（layui 弹窗、列表渲染、本地存储封装、收藏 UI 等）。

**客户端持久化状态**

- 大多数设置/状态通过 `js/functions.js` 的封装写入（统一加 `mkPlayer2_` 前缀）：
  - `playerSavedata()` / `playerReaddata()`
- 入口密码遮罩使用了单独的 key：
  - `localStorage['mkplayer_authenticated']`（`js/player.js`；在 `types=auth` 成功后仅用于隐藏遮罩，属于 UI 层门禁）

**PHP 结构**

- `api.php` 通过 `types` 参数分发（例如 `types=search`、`types=url`、`types=collections`）。
- 主要配置通过修改 `api.php` 顶部常量完成（见“配置”）。

## 4) 测试

仓库内未包含自动化测试框架（如 PHPUnit / Jest 等）。

调试主要依赖：

- 浏览器开发者工具（尤其是 Network 面板）
- 可选前端调试输出：`js/player.js` 中的 `mkPlayer.debug`
- 可选后端调试输出：`api.php` 中的 `DEBUG`
  - 说明：开启 `DEBUG` 后，直接访问 `api.php` 会渲染服务器能力检测信息（见 `api.php` 默认分支输出）。

## 5) 安全注意事项

**入口密码（服务端写死）**

- 服务端密码常量：`api.php` 中的 `MKPLAYER_PASSWORD`。
- 前端通过 `api.php?types=auth&password=...` 验证（见 `js/player.js`）。
- 验证通过后前端写入 `localStorage['mkplayer_authenticated']=true` 并隐藏遮罩；这是 UI 级别门禁。

**客户端敏感数据**

- 推荐服务使用 Bearer Token：在设置页输入后被存入 `localStorage`（`mkPlayer2_recommendToken`）。应当按密钥对待。

**CORS**

- `apache.conf` 配置了较宽松的 CORS（`Access-Control-Allow-Origin: *`）。若对公网部署，应结合实际需求评估。

**外部脚本**

- `index.html` 动态注入了来自 `https://w.cnzz.com/...` 的外部统计脚本。如进行隐私/安全审计，可优先关注此处。

**调试模式暴露信息**

- `api.php` 的调试模式（`DEBUG=true`）在文件注释中提示：正常使用应关闭，避免暴露环境信息。

**下载与文件写入**

- 服务端会把 MP3 写入 `temp/<source>/`，收藏写入 `collections/collections.json`。部署时需要保证目录可写，并考虑磁盘占用与清理策略。

## 6) 配置

**后端配置（修改 `api.php`）**

- 网易云 Cookie：`$netease_cookie`（当网易云访问受限/失效时可配置）。
- 协议替换：`HTTPS`（在未定义 `NO_HTTPS` 时，会把返回数据中的 `http://` 替换为 `https://`）。
- 音乐源限制：当源为 `kugou` / `tencent` 时，`api.php` 会定义 `NO_HTTPS`，避免强制替换为 HTTPS。
- 调试模式：`DEBUG`（见“测试/安全注意事项”）。
- JSONP 模式：`JSONP`（当存在 `callback` 参数时输出 JSONP）。
- 缓存目录：`CACHE_PATH`（歌词/歌单/搜索/评论等 JSON 文件缓存）。
- 下载目录：`TEMP_PATH`（服务端下载 MP3 的临时目录）。
- 入口密码：`MKPLAYER_PASSWORD`。
- 运行依赖可在 `api.php` 的调试页中检查，包括 `curl_exec`、`file_get_contents`、`json_decode`、`hex2bin`、`openssl_encrypt`。

**前端配置（修改 `js/player.js` / `js/musicList.js`）**

- `mkPlayer.api`：后端接口地址（默认 `api.php`）。
- 功能开关：评论、自动播放、封面背景虚化、VIP 过滤等。
- `musicList`：内置歌单及歌单 ID（用于拉取远端歌单）。

**需要可写的目录**

- `collections/`：收藏功能需要可写。
- `temp/`：下载功能需要可写。
- `cache/`：启用 `CACHE_PATH` 时需要可写。

**Docker 相关**

- `docker-compose.yml` 使用 `php:7.4-apache`，并将仓库挂载到 `/var/www/html`。
- 容器启动命令会确保 `collections/` 存在，并将其权限设置为可写（`chmod -R 777 /var/www/html/collections`）。

**Web 服务器头（下载）**

- `apache.conf` 针对 `/temp/` 设置了下载相关 Header（`Content-Type: application/octet-stream`、`Content-Disposition: attachment`）。

**配置/规则文件**

- 仓库内未发现 Cursor 规则（`.cursor/rules/`）、Trae 规则（`.trae/rules/`）或 Copilot 指令（`.github/copilot-instructions.md`）。
