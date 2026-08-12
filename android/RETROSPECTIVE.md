# Android 客户端开发复盘

**项目：** MKOnlineMusicPlayer Android 客户端
**日期：** 2026-08-11
**任务：** 基于已有 Web 端（PHP + jQuery）和 Cursor 历史会话，完成 Android 版开发

---

## 1. 最终产出

- 23 个 Kotlin 文件，覆盖完整功能（搜索/播放/歌单/收藏/历史/评论/推荐/下载/设置/主题）
- 最小化命令行编译环境（JDK 17 + Android SDK + Gradle，约 2.8GB）
- APK 编译通过（21MB，debug 版）
- 三轮 code review，发现并修复 10+ 问题

---

## 2. 关键失误

### 2.1 同类 bug 重复出现（最严重）

**问题：** `loading` 初始值 `true` + `if(loading) return` 守卫 = 首次调用直接 return，请求永远发不出，页面永久转圈。

- SearchScreen：第一轮发现 → 修复
- CommentsScreen：第二轮发现 → 修复
- CollectionsScreen：第三轮发现 → 修复

**根因：** 修第一个时只看了当前文件，没有全局 grep `mutableStateOf(true)` 扫一遍所有 screen。同一个 bug 模式在三个文件各犯一次，各修一次，浪费了三轮 review 和你的时间。

**教训：** 修一个 bug 后，立即全局搜索同类模式（`grep -rn "mutableStateOf(true)"`），一次扫干净。不要等 reviewer 再报一次。

### 2.2 图标改了四五轮

**过程：**
1. 第一次：只删了 anydpi 目录，v26 adaptive icon 的 foreground 还指向旧矢量 → 旧图标
2. 第二次：换成 PNG foreground，但没删旧的 drawable XML → 旧图标
3. 第三次：全删 adaptive icon，用纯 PNG → 终于对了

**根因：** 对 Android adaptive icon 机制理解不透彻。每次只改一部分就构建验证，没有一次性理清 `mipmap-anydpi-v26` / `mipmap-anydpi` / `drawable/ic_launcher_foreground` 之间的引用关系。

**教训：** 改资源文件前，先 grep 全部引用链（Manifest → mipmap → drawable → color），一次性改完再构建。

### 2.3 CoverImage 图标层级写反

**问题：** Compose `Box` 中后声明的子组件在上层。写了 Icon 在 AsyncImage 之后（顶层），导致已加载封面被 MusicNote 图标覆盖。还配了注释说"图片盖在上面"——注释和代码完全相反。

**根因：** 没验证视觉效果就提交，注释是凭想象写的。

**教训：** UI 改动要验证渲染逻辑；不确定的 Compose 行为查文档，别凭记忆写注释。

### 2.4 没有主动跑 reviewer

**问题：** 改完直接给你结果，没主动跑验证。你说"启动 reviewer 自己检查"我才跑。

**教训：** 每轮改动后主动启动 code-reviewer 验证，不要等用户催。

### 2.5 改动拆太碎

**问题：** 歌单轰炸 API、封面兜底、下拉刷新、主题模式这些可以一次性改完构建一次，拆成了好几轮。

**教训：** 相关改动批量做完再构建，减少来回。

---

## 3. 做得对的地方

- 从 Cursor 历史会话中断处精确接续，没有重复已完成的工作
- 最小化编译环境搭建（纯命令行，不装 Android Studio，约 2.8GB）
- API 契约与 Web 端逐字段对齐（Meting.php format 输出、comments 结构、download 返回等）
- reviewer 发现的问题修完后会重新构建验证
- 主题切换、下拉刷新等功能与设置页联动完整

---

## 4. 待办

- [ ] 写单元测试 / UI 测试（目前 0 测试覆盖）
- [ ] release 构建签名配置
- [ ] 图标圆角适配（部分启动器对非 adaptive icon 圆角处理不一致）
- [ ] PlayerManager 资源释放（#8，低风险遗留）
- [ ] 下载文件名冲突保护（#10）
- [ ] failStreak 阈值调优（#11，3 次偏激进）

---

## 5. 改进清单（给下次）

1. **修 bug 后全局扫同类模式**，不要只改当前文件
2. **改资源前理清引用链**，一次改完再构建
3. **UI 改动验证渲染逻辑**，别凭想象写注释
4. **每轮改动后主动跑 reviewer**
5. **相关改动批量做完再构建**
