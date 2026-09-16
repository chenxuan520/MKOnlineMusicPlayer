# Android 客户端第二轮深度 Review

## Review

- **review 时间**: 2026-08-11
- **审查范围**: `android/app/src/main/java/com/mkonline/player/` 下全部 14 个目标文件 + 辅助文件（MkApp/PlaybackService/Prefs/Models/LrcParser/Downloads/SheetIds/AuthScreen/AndroidManifest/build.gradle/libs.versions.toml）
- **对照基线**: 当前 master 分支（无独立功能分支）；本次审查未基于 plan 对照，基于需求"搜索转圈已修复后第二轮深度检查"
- **审查结论**: 不通过（1 个崩溃级问题需修复）

### Findings 摘要

#### 🔴 必须立即修

1. **CollectionsScreen.kt:99-104 — `list.removeAt(i)` 协程中索引越界崩溃**
   - `trailing` 删除按钮在 `scope.launch` 协程中执行 `list.removeAt(i)`，`i` 是 `items(list.size)` 的闭包捕获值
   - 场景：list=[A,B,C]，用户在 3 个网络请求完成前快速点击 3 个删除按钮 → 协程1 完成 removeAt(0) 后 list=[B,C]，协程2 removeAt(1) 删错歌（删 C 不是 B），协程3 removeAt(2) 越界 → `IndexOutOfBoundsException` 崩溃
   - 网络越慢越容易触发；即使只点 2 次也会删错歌
   - 修复：用 `list.removeAll { it.key == song.key }` 代替 `list.removeAt(i)`（QueueScreen 的 `pm.removeAt` 是同步调用 + PlayerManager 内有边界检查，不受此问题影响）

#### 🔵 可改进（不阻塞）

2. **SearchScreen.kt:200-205 — 搜索中 LoadingBox 与 EmptyHint 同时显示**
   - 搜索发起后 `loading=true, searched=false, results=empty`，`LoadingBox(loading && results.isEmpty())` 显示转圈，同时 `results.isEmpty() && !searched` 命中显示"输入关键词开始搜索"
   - 状态能正常结束，不会卡死；只是 UI 状态判断不完整
   - 修复：`results.isEmpty() && !searched` 改为 `results.isEmpty() && !searched && !loading`

3. **CommentsScreen.kt:97/136 — 缺少分页加载更多**
   - 只有 `LaunchedEffect(Unit) { load(true) }` 加载第一页；`hasMore` 仅用于显示"没有更多了"，没有滚动到底部自动触发 `load(false)` 的逻辑
   - 用户最多看到第一页 50 条 normal + 若干 hot；属于功能缺失，不是崩溃

4. **SearchScreen.kt:58/109/131 — `SearchHolder.loading` 是死代码**
   - 只写入（第 109/131 行），从未被读取；不影响功能

5. **未使用的 import（不会编译失败，Kotlin 允许）**
   - SheetsScreens.kt:26 `CircularProgressIndicator`、:48 `sp` 未使用
   - PlayerScreen.kt:15、SearchScreen.kt:10、SheetsScreens.kt:18、SongListScreens.kt:7、QueueScreen.kt:7、RecommendScreen.kt:13 的 `androidx.compose.foundation.lazy.items` 未使用（这些文件都用 `items(count)` 成员重载，不需要 import 扩展函数）

### 待 builder 处理项

- [ ] 修复 CollectionsScreen.kt:102 的 removeAt 索引越界（必须）
- [ ] （可选）SearchScreen.kt:202 加 `&& !loading` 判断
- [ ] （可选）CommentsScreen.kt 补充分页加载更多
- [ ] （可选）清理未使用 import 与死代码

### 已参考的验证信息

- Compose BOM 2024.12.01 / Material3 1.3.x / Kotlin 2.0.21 / Media3 1.5.1
- `LinearProgressIndicator(progress = { ... })` lambda 签名与 Material3 1.3+ 匹配
- PlaybackService 已在 AndroidManifest.xml 注册（foregroundServiceType="mediaPlayback"）
- PlayerManager.removeAt 有边界检查；QueueScreen 的 removeAt 是同步调用不受索引漂移影响
- AppNav 所有 route 参数与导航调用匹配；clearSearchCache/clearSheetCache 定义存在
- Api.kt 的 `toMediaType` / `toRequestBody` import 均被使用

### 残余风险

- PlayerManager.onSongStart 的 picUrl 异步更新存在极窄竞态窗口（IO 线程检查 currentSong.key 通过后、withContext(Main) 执行前切歌），理论上会短暂把 currentPic 覆盖为旧歌封面，但新歌 onSongStart 会立即纠正，仅 UI 闪烁，不崩溃
- PlayerScreen 的 position/duration 没有以 song?.key 作为 remember key，切歌瞬间会短暂显示上一首进度，LaunchedEffect 首次循环即纠正，不崩溃

---

## Review（第四轮，2026-08-12）

- **review 时间**: 2026-08-12
- **审查范围**: `android/app/src/main/java/com/mkonline/player/` 全部 24 个 Kotlin 文件（含新增 CoverCache.kt）+ AndroidManifest.xml + 服务端 api.php / plugns/Download.php 下载链路对照
- **对照基线**: 本 plan 第二轮 review 记录；前三轮修复项（loading 卡死×3、removeAt 越界、SheetCard 轰炸、CoverImage 层级、主题模式、下拉刷新、CoverCache 等）逐一核对均已落实
- **审查结论**: 不通过（2 个崩溃级问题需修复）

### 第四轮 Findings

#### 🔴 必须立即修

1. **AuthScreen.kt:98-109 — 登录请求未捕获异常，填错地址/服务不可达必现崩溃**
   - `scope.launch { ... container.api.auth(...) }` 无任何 try/catch；`api.auth` → `postBlocking` 在 HTTP 非 2xx 时抛 ApiException、主机不可达时 OkHttp 抛 IOException、返回非 JSON 时抛 ApiException
   - `rememberCoroutineScope` 无 CoroutineExceptionHandler，异常直达默认未捕获处理器 → 应用崩溃
   - 登录页是用户输入任意地址的入口，这是最容易触发的网络异常路径

2. **PlayerManager.kt:310-319 — toggleFavorite 未捕获异常，网络失败即崩溃**
   - `scope.launch { api.collectionAdd/Remove }`，scope 为 `CoroutineScope(SupervisorJob() + Dispatchers.Main)`，无 handler
   - `collectionAction` 未像 picUrl/lyric/collectionCheck 那样内部 runCatching，HTTP 错误/JSON 解析失败/IO 异常都会抛出让 App 崩溃

#### 🟡 明显逻辑错误（不阻塞）

3. **SearchScreen.kt:215 — 播放高亮读 `pm.currentSong.value` 而非 collectAsState**
   - 其余列表（收藏/历史/歌单详情/队列）均用 collectAsState，只有搜索结果页直接读 `.value`，切歌后高亮不会随播放状态变化而重组，要等其他状态变化才刷新
   - 不崩溃，但与其他页面不一致，建议改为 `val current by pm.currentSong.collectAsState()`

### 待 builder 处理项

- [ ] AuthScreen 登录协程包 try/catch（或给 scope 加 handler），失败时显示 error 文案（必须）
- [ ] PlayerManager.toggleFavorite 包 try/catch 并向 _toasts 提示失败（必须）
- [ ] （可选）SearchScreen 高亮改 collectAsState

### 已核对无问题的重点项（第四轮验证记录）

- CoverCache：synchronizedMap + LinkedHashMap(accessOrder) LRU、设备级空串拦截，实现正确
- playSong 双重 playAt 已由 wasEmpty 守卫修复；insertNext/playAt 边界（currentIndex=-1、末位追加）推演均一致
- onSongStart CoverCache 写入时机：IO 拉取→key 校验→Main 回写+persistQueue，且切歌后仍有兜底写缓存分支，无越界/脏写
- CollectionsScreen/CommentsScreen/SheetDetailScreen/SearchScreen 的 loading 均有 finally 复位，无卡死路径；PullToRefreshBox 括号闭合正确（CommentsScreen.kt:169-170）
- Downloads 链路：Download.php 返回绝对 URL（`$protocol.$HTTP_HOST./temp/...`），DownloadManager.enqueue 不会因相对路径抛 IllegalArgumentException；usesCleartextTraffic=true 已配
- Models.toJson/fromJson 字段对称；Prefs themeMode 持久化读写对称；Api 的 toMediaType/toRequestBody import 均被使用

---

## Review（第五轮，2026-08-12）

- **review 时间**: 2026-08-12
- **审查范围**: 本轮新增/改动 8 个文件：data/CoverCache.kt、data/SheetCache.kt、ui/screens/SheetsScreens.kt、ui/components/Common.kt、playback/PlayerManager.kt、MkApp.kt、MainActivity.kt、data/Prefs.kt；联动核对 AppNav.kt / Api.kt / Models.kt / Theme.kt / SettingsScreen.kt / SheetIds.kt / build.gradle.kts
- **对照基线**: 本 plan 第二/四轮 review 记录；改动点与需求描述逐项核对
- **审查结论**: 不通过（1 个崩溃级并发问题 + 1 个缓存失效缺口）

### 第五轮 Findings

#### 🔴 必须立即修

1. **CoverCache.kt:56 — `put` 的整表序列化迭代未加锁，多个线程同时 put/get 可抛 ConcurrentModificationException 致崩溃**
   - `mem.forEach { (k, v) -> obj.put(k, v) }` 解析到 Kotlin 内联扩展 `Map.forEach((Map.Entry)->Unit)`（按 entrySet() 迭代），**不是** `Collections.SynchronizedMap` 带 `synchronized(mutex)` 的 `forEach(BiConsumer)` 成员——迭代过程不加锁
   - `put` 调用点跨线程：PlayerManager.kt:271 在 `withContext(Main)` 内、PlayerManager.kt:281 在 IO 调度器分支内（该分支是连切歌曲时最常命中的路径）；`get` 发生在 Main 组合期（Common.kt:92-93），且 access-order LinkedHashMap 的 `get` 经 `afterNodeAccess` 会递增 modCount（结构性修改）
   - 触发场景：用户快速连切下一首 → 多首封面请求并发返回，旧歌走 :281 IO 线程 put 做全表迭代，恰逢新歌 :271 Main put 或列表重组 get → fail-fast 迭代器抛 CME；PlayerManager 的 scope = SupervisorJob + Main，无 CoroutineExceptionHandler → 未捕获异常直达默认处理器 → 应用崩溃
   - 修复：序列化处改为 `synchronized(mem) { mem.forEach { (k, v) -> obj.put(k, v) } }`（同理 SheetCache.kt:56-62 的 persist 虽目前调用点全在 Main，建议一并加锁防御）

#### 🟡 明显逻辑错误（不阻塞）

2. **SheetsScreens.kt:71 — `clearSheetCache()` 只清 `data.SheetCache`，漏清同文件的 `FullSheetCache`，登出/换服务器后歌单详情展示旧服务器数据**
   - AppNav.kt:72-77 的契约是"认证失效时清理进程级缓存避免跨会话泄漏"，调用 `clearSheetCache()`
   - 但 `FullSheetCache`（含完整歌曲列表）未被清，SheetDetailScreen.kt:231 `remember(id) { FullSheetCache.get(id) }` 命中旧数据后，:253 的 LaunchedEffect 直接跳过重载 → 用户登录新服务器后看到的是旧服务器的歌单歌曲，需手动下拉刷新才纠正
   - 修复：`clearSheetCache()` 内同时 `FullSheetCache.clear()`（同一文件内可直接访问；FullSheetCache 需补一个 clear 方法）

### 待 builder 处理项

- [ ] CoverCache.put / SheetCache.persist 的整表迭代包 `synchronized(mem)`（必须，崩溃级）
- [ ] clearSheetCache() 同步清理 FullSheetCache（应修，跨会话脏数据）

### 已核对无问题的重点项（第五轮验证记录）

- MkApp.kt:18-19 init 时序正确：AppContainer 构造即 init 两个缓存，早于 lazy api/playerManager 及任何 API 调用；且 init 前 get/put 退化为仅内存，无崩溃路径
- CoverCache/SheetCache init：幂等守卫（`sp != null` return）、磁盘解析 runCatching、空串/空名过滤齐全；写磁盘用 `apply()` 异步提交，时机正确
- SheetCard（SheetsScreens.kt:159-176）懒加载逻辑正确：remember/LaunchedEffect 均按 meta.id 键控，写缓存用 sheet.id（即请求 id，与 meta.id 一致），失败静默 + finally 复位 loaded
- PlayerManager.kt:264-284 CoverCache 写入时机正确：pic 为空才补、key 校验后 Main 回写 currentPic + 队列 + persistQueue，切歌后仍有 :281 兜底写缓存分支
- 第四轮修复项仍在：toggleFavorite try/catch（:313-321）
- ThemeMode 链路：`MKTheme(themeMode = settings.themeMode)`（MainActivity.kt:33）签名匹配 Theme.kt:44-47；Prefs.kt:57/71 读写对称；SettingsScreen.kt:72-73/194 接线完整
- SheetCache.put 的 LRU 淘汰为 FIFO 语义（LinkedHashMap 插入序 first 淘汰），且 map 满时对已存在 key 也会多淘汰一条——仅效率瑕疵，非错误，不列 finding
- SheetsScreens.kt:284 `error!!` / :287 `sheet!!` 均有前置分支保护，无 NPE 路径

### 验证缺口

- 本机无 JDK（`/usr/libexec/java_home` 无输出），无法本地执行 `compileDebugKotlin`；已用静态方式逐一核对所有新引用的字段/类型/签名（Sheet.creatorName、Song.copy(pic=)、SheetMeta、缓存 API、ThemeMode 等）均匹配，编译风险低，建议以 CI 构建结果为准

### 残余风险

- CoverCache 在登出/换服务器时无 clear 入口（键为 source:id，跨服务器同 id 封面一致，低风险）
- SheetCache.put 无空名守卫，若服务器对有效 id 返回空 name，SheetCard 会显示空白名（现实中 playlist 接口总会返回 name，低概率）
