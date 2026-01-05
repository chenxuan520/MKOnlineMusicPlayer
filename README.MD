MKOnlineMusicPlayer（ChenPlay 优化版）
========
基于 `Meting` 的在线音乐播放器，支持搜索、播放、下载、歌词同步、收藏与播放历史，已集成“智能推荐歌单”。前端采用响应式设计，PC/移动端均可良好使用。

> 本项目仅用于学习与交流，请勿用于商业用途或传播盗版资源。

### 快速开始
- Docker 启动：在项目根目录执行 `docker-compose up -d`，默认访问 `http://localhost:4000`。
- 手动启动：使用 Apache/PHP，或 `php -S 127.0.0.1:4000` 开启内置服务器。
- 必要目录：确保 `collections/` 可写（收藏功能），`temp/` 可写（下载功能）。

### 界面欣赏
- 主界面(电脑端)：![主界面](https://user-images.githubusercontent.com/35069089/52914931-e9655d00-3308-11e9-8347-a1f9be614e10.png)
- 歌曲播放(电脑端)：![歌曲播放](https://user-images.githubusercontent.com/35069089/52914962-2f222580-3309-11e9-87ce-5feb50697d69.png)
- 歌曲搜索(电脑端)：![歌曲搜索](https://user-images.githubusercontent.com/35069089/52914970-42cd8c00-3309-11e9-9eb1-2cd3f6b7a242.png)
- 播放列表界面(电脑端)：![播放列表](https://user-images.githubusercontent.com/35069089/52914976-65f83b80-3309-11e9-97fc-b3409ccb2d09.png)
- 下载歌曲/歌词(电脑端)：![下载功能](https://user-images.githubusercontent.com/35069089/63268311-785a2e80-c2c6-11e9-858a-a5629d80731a.png)

### 相关链接
- 原版仓库：<https://github.com/mengkunsoft/MKOnlineMusicPlayer>
- 优化版仓库：<https://github.com/chenxuan520/MKOnlineMusicPlayer>

### 智能推荐服务
- 推荐引擎仓库：`https://github.com/chenxuan520/recommend_engine`。
- 默认端口：`8080`。
- 前端调用接口（同域名下）：
  - `POST /api/v1/recommend/music?async=true`（提交喜好，返回 `task_id`）
  - `GET  /api/v1/recommend/result/{task_id}`（轮询任务结果）
- Token：必填。可在播放器设置里填写；域名未填写时默认回落为当前页面域名（自动带 `http/https`）。逻辑位置：`js/ajax.js:403`、`js/ajax.js:516`。
- 合并去重：推荐结果合并时按“歌曲名”去重（忽略大小写与空格，保留首次出现的顺序）。逻辑位置：`js/ajax.js:567`。

### 同域名部署（Nginx 反向代理）
- 配置文件：`nginx.conf`（已随项目提供）。
- 路由约定：
  - 推荐服务：`location ^~ /api/v1/recommend/` -> `http://127.0.0.1:8080`（见 `nginx.conf:31`）。
  - 其余路径：代理到 PHP 应用（Apache，容器/服务名 `webapp`）。
- 说明：将推荐服务与应用部署在同一台机器后，通过同一个域名访问；无需跨域。若推荐服务不在本机，按需修改 `proxy_pass`。

### 入口密码（服务端配置）
- 为了保护入口，密码在服务端写死：`api.php:30` 的 `MKPLAYER_PASSWORD` 常量。
- 前端验证流程：`js/player.js:95` 调用 `api.php?types=auth` 校验后再放行；失败会提示。

### Docker Compose（现有）
- 当前 `docker-compose.yml` 启动 `php:7.4-apache` 并映射 `4000:80`。
- 如需用 Nginx 统一入口：
  - 启动 Nginx（挂载 `nginx.conf`），监听 `80/443`；
  - 推荐服务跑在 `127.0.0.1:8080` 或容器网络可达地址；
  - 应用保持 Apache 对内监听 `80`，由 Nginx 反向代理统一对外域名。

### 运行环境与必要配置
- PHP 5.4+，启用 `curl_exec`、`file_get_contents`、`json_decode`、`openssl_encrypt`。
- 定时清理缓存（建议每分钟）：`api.php?types=cache` 或定时删除 `cache/*.json`。
- 下载目录头设置：
  - Apache：`apache.conf:18` 针对 `/temp/` 设置下载头。
  - Nginx：参考 README 的 `location ~/temp/` 示例。

### 其它说明
- 收藏数据保存在 `collections/collections.json`（由后端 `api.php:308` 维护）。
- 播放历史最多 300 条，持久化在本地存储；播放历史仅用于偏好输入，无有效期限制。逻辑位置：`js/functions.js:1655`。

### 版权与致谢
- 原作者：[mengkun(https://mkblog.cn)](https://mkblog.cn)。
- 依赖：jQuery、Meting、layui、mCustomScrollbar、background-blur 等。

### 更新与计划
- 已新增：智能llm推荐、VIP过滤/跳过、热评显示、设置控制、收藏导入导出等。
- 后续按需迭代，欢迎提 Issue。
