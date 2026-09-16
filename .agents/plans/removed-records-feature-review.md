# 失效清除记录功能 Review（api.php / js/functions.js / css/player.css / README.MD 未提交改动）

## Review

- **review 时间**: 2026-09-16
- **审查范围**: 工作区未提交改动（`git diff`：api.php +141、js/functions.js +220、css/player.css +94、README.MD +1），基线为 master（origin/master）
- **对照基线**: 本次审查未基于 plan 对照（`.agents/plans/` 下仅有无关的 android-runtime-deep-review.md），以主进程提供的功能说明为需求基线
- **审查结论**: 不通过（1 个 🟡 应修项，4 个 🔵 可选项）

### Findings 摘要

#### 🟡 应修

1. **弹窗操作无串行/防重入，removed_restore 等新 action 的读改写竞态可造成"提示恢复成功但实际丢失"**
   - 位置：`js/functions.js:3317-3393`（removedRestoreSong / removedRemoveSong / doRemovedClear）、`api.php:575-612`（removed_restore）
   - 同一弹窗内快速连点两行"恢复"（间隔小于一次请求往返），两个 removed_restore 并发执行，各自基于旧快照读改写 collections.json + removed.json，后写者覆盖先写者 → 其中一次恢复丢失（toast 却显示成功），removed.json 留下脏记录；跨标签页（恢复 vs 一键移除失效批量删除）同理，还可能把刚批量移除的歌"复活"回收藏
   - 本次改动在 removeFailedCollections 里专门做了串行（注释明确"避免并发读改写 collections.json 互相覆盖"），说明对这类风险有认知，但弹窗操作没有沿用同一防护；removed_restore 还同时写两个文件，暴露面更大
   - 后果可自愈（歌仍在 removed.json，可再恢复，非永久丢失），但属可复现的错误行为
   - **修复建议（前端最小修复）**: 弹窗操作加 in-flight 标志或复用 removeFailedCollections 的串行 next() 链模式，操作进行中排队/禁止后续点击

#### 🔵 可改进（不阻塞）

2. **shadeClose 关闭弹窗不触发 cancel 回调，rem._removedList 缓存清理意图落空**
   - `js/functions.js:3243-3263`；本仓库 layui layer 3.1.1 源码确认：点遮罩直接 r.close()，cancel 只绑 X 按钮 → 点遮罩关闭后缓存残留
   - 实际无害（关闭后行内 onclick 元素销毁，缓存不可达；重开弹窗立即重拉）。建议改用 end 回调清理，使注释与行为一致
3. **removed_add 补记录保真度低于 reason=failed 路径**
   - `js/functions.js:2935`：多歌手只记 artist[0]（与 toggleCollection 收藏时"数组→逗号串"口径也不一致）；`js/functions.js:2934-2940`：music 缺 url_id/pic_id/lyric_id 时记空串，恢复后封面/歌词降级（播放不受影响，ajaxUrl 只用 id）
   - 主路径（reason=failed 保留完整原对象）不受影响
4. **普通取消收藏的探活带来固定请求成本与"假失效"记录噪音**
   - `js/functions.js:2922-2953`：每次取消收藏触发 1-3 次 types=url（服务端再请求音源）+ 1-3 次媒体探测。单事件有界、非请求风暴，但成本加在每次取消收藏上
   - 假阳性来源：https 页面 + kugou/tencent（http CDN 链接被混合内容拦截，探测必失败 → 该源所有取消收藏都被记为"失效"）；网络瞬时抖动。均为沿用 checkMusicUrl 既有口径（检测收藏同病），记录惰性、不影响收藏数据
5. **json_encode 失败会清空整个 removed.json（继承既有模式）**
   - `api.php:435/544`：GET 参数混入非法 UTF-8 时 json_encode 返回 false → 写入空串。collections.json 的 add/remove 一直是同样写法，属继承性风险，概率极低

### 已核对无问题项

- XSS：弹窗 name/artist 均经 escapeHtmlText（转义顺序正确）；time 来自 formatDate（数字/null）；tips 用 .text()。无注入点（存量 addItem 渲染收藏列表不转义，非本次引入）
- 边界：removed.json 不存在/损坏均有 file_exists + is_array 守卫；重复恢复优雅降级；收藏为空时入口仍渲染（addListbar 在 if/else 之后）
- 索引一致性：行内 onclick 只带索引，操作按 id+source 寻址，索引错位最多界面短暂陈旧（每次操作后重拉）；弹窗关闭后 in-flight 响应有 $body.length 守卫
- 回归：removeCollectionItem 两个调用方（搜索弹窗/批量移除失效）行为符合预期；Android remove 不传 reason（getParam 默认 ''，'' === 'failed' 为 false），响应格式不变
- 最小改动：4 文件改动全部可追溯到需求，无顺手改动；helper 均必要；无调用放大

### 待 builder 处理项

- [ ] 修复 🟡-1：弹窗操作串行化/防重入（必须，修后可复审通过）
- [ ] （可选）🔵-2 用 end 回调替代 cancel 清理缓存
- [ ] （可选）🔵-3 removed_add 的 artist 口径与收藏 add 对齐
- [ ] （可选）🔵-4 https + NO_HTTPS 源跳过媒体探测
- [ ] （可选）🔵-5 json_encode false 判断

### 已参考的验证信息

- `php -l api.php` 通过；`node --check js/functions.js` 通过
- layui layer 3.1.1 源码（plugns/layui/lay/modules/layer.js）确认 shadeClose/close/cancel 行为
- Android `Api.kt:284` collectionAction("remove") 确认不传 reason

### 残余风险 / 验证缺口

- 未做浏览器端实际功能验证（无自动化测试框架）：弹窗布局（layer 70% 高度 + flex 滚动）、真实失效歌曲端到端记录/恢复流程未实测
- PHP 文件级无锁并发为全仓既有架构约束，本记录仅要求前端侧缓解（与 removeFailedCollections 同级别防护）

---

## Review（第三轮·原 reviewer 确认）

- **review 时间**: 2026-09-16
- **审查范围**: 第二轮修复后的工作区 diff（js/functions.js 由 +220 增至 +240；api.php +141 / css/player.css +94 / README.MD +1 与第一轮完全一致，hunk 分布核对无其它改动混入），基线仍为 master
- **对照基线**: 第一轮 review findings（本文件上一章节）
- **审查结论**: 通过（🟡-1 与 🔵-2 均已正确修复，修复本身未引入新问题）

### Findings 确认

#### 🟡-1 修复确认：`rem._removedBusy` in-flight 防重入 — 已正确修复 ✅

- 实现核对（js/functions.js:3321-3346 / 3350-3376 / 3393-3413）：三个写操作均为「`if (busy) return` → 取 m（`!m` 守卫）→ `busy = true` → `$.ajax` → `complete` 复位」
- 正确性逐项验证：
  - 检查与置位为同步 JS（单线程），单标签页内无 TOCTOU 窗口
  - `complete` 在 success/error 后均触发（jQuery 保证）→ 无卡死 busy 的路径；即使关闭弹窗（end 已清缓存），在途请求仍会正常走 complete 复位
  - HTTP 响应到达 ⇒ PHP 脚本已结束 ⇒ 文件写已完成 ⇒ 下一个写请求严格晚于上一个写完成——单用户流程的写-写串行化成立，与 removeFailedCollections 同级别防护（正是第一轮 finding 要求的修复方式之一，注释 3316-3318 的理由表述准确）
  - `refreshRemovedList` 为读操作不设守卫，正确（读不写文件）；busy 复位先于 refresh 响应到达的窗口内点击，操作按 id+source 寻址，索引陈旧不会误伤别的歌，重复点同一行由服务端"记录中不存在该歌曲"优雅降级
  - busy 窗口内点击被静默忽略（约一次请求往返）为刻意设计（注释"重复点击直接忽略"），可接受；`removedClearAll` 不设守卫、守卫落在实际写的 `doRemovedClear`，confirm 自带遮罩也不会叠双确认框
- 残余（与第一轮记录一致，非本次修复范围）：跨标签页/多客户端并发写仍可互相覆盖——全仓既有架构约束（所有 collections action 同病），第一轮已按"前端侧缓解"标准关闭该 finding

#### 🔵-2 修复确认：`cancel` → `end` 回调 — 已正确修复 ✅

- 实现核对（js/functions.js:3259-3261）：`end` 中置空 `rem._removedList`
- 依据第一轮已核对的 layui layer 3.1.1 源码：所有关闭路径（X 按钮 cancel→r.close、shadeClose 直接 r.close、程序化 layer.close/closeAll）都汇集到 `r.close()`，其清理函数必定调用 `o.end[index]`（带出场动画时延迟 200ms 但必然触发）→ end 是唯一"任何关闭路径必经"的回调，选型正确
- end 在弹窗 DOM 移除后触发，行内 onclick 元素已销毁，置空后缓存不可达；内层 layer.confirm 的开关不会触发外层 end（end 按实例注册），不会误清缓存

#### 快速复查（第二轮遗漏扫描）

- diff hunk 分布核对：functions.js 的 7 个 hunk 与第一轮一致，仅 removed-records 块 +20 行修复代码；api.php / css / README 与第一轮逐 hunk 相同，无未声明改动混入
- `_removedBusy` 全仓仅 9 处出现，全部位于三个写函数内，无作用域泄漏、无与其它功能的状态冲突；初始 undefined 为 falsy，首次调用正常放行
- `node --check js/functions.js` 通过
- 第一轮其余 🔵 项（-3 记录保真度 / -4 探活成本与假失效噪音 / -5 json_encode 边缘）维持"可选、不阻塞"结论，未变化
- 未见第一轮遗漏的新问题

### 已参考的验证信息

- 代码路径分析 + layui 3.1.1 源码（end 触发路径）+ `node --check` 语法检查
- 主进程浏览器实测结论（busy 拦截连点 / 正确索引恢复 / 过期索引被 !m 拦截 / shadeClose 后缓存置空）与本次代码路径分析一致

### 残余风险 / 注意事项

- 跨标签页/多客户端并发写仍受全仓无锁架构限制（既有约束，非本功能引入）
- 🔵-3 / 🔵-4 / 🔵-5 仍为可选优化项，不阻塞
- busy 窗口内（约一次请求往返）重复点击被静默忽略，属预期交互

## Review（第二轮）

- **review 时间**: 2026-09-16
- **审查范围**: 工作区未提交改动（`git diff`：api.php +141、js/functions.js +240、css/player.css +94、README.MD +1），基线为 master（origin/master）。相对第一轮（js/functions.js +220）的增量约 20 行 = 三组 `rem._removedBusy` 守卫 + `end` 回调，api.php / css / README 无变化
- **对照基线**: 本文件第一轮 review 记录 + 主进程第二轮复审要求（验证 🟡-1 / 🔵-2 修复 + 审查修复本身是否引入新问题）
- **审查结论**: 通过（两项修复均正确落实；本轮新发现 2 个 🔵 可改进项，不阻塞）

### 上轮两项修复验证

1. **🟡-1 弹窗写操作防重入 — 已正确落实**
   - 三个写操作均有守卫：`removedRestoreSong`（js/functions.js:3322 检查 / 3325 置位 / 3343-3345 complete 复位）、`removedRemoveSong`（3351 / 3354 / 3372-3374）、`doRemovedClear`（3394 / 3395 / 3409-3411）
   - 置位时机正确：均在 `!m` 守卫之后、`$.ajax` 调度之前，无误锁路径
2. **🔵-2 shadeClose 清理缓存 — 已正确落实**
   - js/functions.js:3259-3261 改用 `end` 回调置 `rem._removedList = null`；layer 3.1.1 源码确认 `end` 注册进 `o.end[index]`，`r.close` 收尾时无条件调用（shadeClose 路径 `shade → r.close(t.index)` 同样经过），X 按钮 / 遮罩 / closeAll 全部覆盖

### 修复本身引入风险的专项核对（均无 🟡/🔴 级问题）

- **复位时机（complete vs refresh 完成后）无并发窗口**：jQuery 回调顺序为 success → error → complete；success 内 dispatch 的 `refreshRemovedList()` 是只读 GET。PHP 侧 `file_put_contents` 在返回响应前已落盘，故 complete 复位时服务端写已完成，下一个写请求不会与前一个写在服务端重叠——3316-3318 行注释的声明经核对成立。busy 复位后至 refresh GET 返回前的窗口内点击旧索引，按 id+source 寻址 + 后端"记录中不存在"守卫兜底，与第一轮已接受的索引陈旧语义一致
- **complete 执行路径**：jQuery 1.x 语义下 success / error / timeout / abort 所有"终止"路径均会触发 complete，无死锁；唯一不触发的是连接挂死（见 🔵-6）
- **removedClearAll 与行内操作交错**：layer.confirm 弹出后自带 shade（默认 0.3、无 shadeClose），遮罩挡住下层弹窗，confirm 期间无法触发行内操作；confirm 打开前发起的行内操作在途时确认清空，由 doRemovedClear 的 busy 守卫拦截（静默 no-op，窗口为一个 HTTP 往返，极窄）
- **end 与 success 顺序**：end 只在 `r.close` 内触发，弹窗存活期间不影响 success；关闭后到达的响应中 `layer.msg` / `loadCollections()` 正常执行（后者是期望行为），`refreshRemovedList` 有入口守卫（其局限见 🔵-7）

### Findings 摘要

#### 🔵 可改进（不阻塞）

6. **三个写请求无 timeout，连接挂死时 busy 永久卡死且拦截无反馈**
    - 位置：js/functions.js:3327-3346 / 3356-3375 / 3397-3412（removed_restore / removed_remove / removed_clear 的 $.ajax 配置）
    - 场景：请求挂死（服务端 hang / 网络黑洞）→ complete 永不触发 → `rem._removedBusy` 永久为 true → 弹窗所有写操作静默失效；重新打开弹窗也不复位（end 只清 `_removedList`），只能刷新页面恢复
    - 缓解因素：三个 action 均为纯本地文件 I/O 不出网，挂死概率低；`removeFailedCollections` 串行链同样无超时（同级别防护）；歌词请求加 timeout 的先例（js/ajax.js:448）说明作者对该类风险有认知但未覆盖此处
    - 建议：三个写请求补 `timeout: 20000`（对齐 ajax.js:448）；可选在 busy 拦截分支补一条 `layer.msg('操作处理中')` 改善静默无反馈
7. **在途 refresh 在弹窗关闭后仍会回填 `rem._removedList`，入口守卫只覆盖"发起时点"**
    - 位置：js/functions.js:3266-3268（$body 入口捕获 + 守卫）、3282（回填）、3284（live 查询 `#removed-list-tips`）
    - 场景：GET 在途时弹窗关闭（shadeClose），$body 捕获的 detached 节点 `.length` 仍为 1 → success 仍执行：回填 `_removedList`（end 已置 null 后被重新填充）、向 live 查询到的 tips 写入（若已重开新弹窗会写入新弹窗）、`$body.html()` 写入 detached 节点（不可见，无害）
    - 危害评估：行内 onclick 元素已随弹窗销毁，缓存无人可达；重开弹窗必发新 GET 覆盖；即使旧响应晚于新响应到达，行内操作按 id+source 寻址 + 后端守卫兜底，最坏是 tips 计数与列表体短暂 cosmetic 不一致，无数据损坏、无错误写行为
    - 建议：可保留现状；如需注释与行为严格一致（end 注释声称"关闭即作废"），需引入代际计数器，属过度设计倾向，不建议为此加复杂度

#### 上轮可选项状态（未处理，维持非阻塞）

- 🔵-3 removed_add 的 artist 口径：未处理
- 🔵-4 https + NO_HTTPS 源跳过媒体探测：未处理
- 🔵-5 json_encode false 判断：未处理

### 已核对无问题项

- 最小改动：相对第一轮的 20 行增量全部可追溯到两项修复（3 组守卫 12 行 + end 回调 4 行 + 注释），无顺手改动、无新抽象、无调用放大
- XSS / 边界 / 回归：与第一轮结论一致，本轮增量未触及这些面（api.php / css / README 零变化，已重新 `git diff` 逐行核对）

### 已参考的验证信息

- `php -l api.php` 通过；`node --check js/functions.js` 通过
- jQuery 1.x 源码（js/jquery.min.js）：确认 complete 在 success/error 后、所有终止路径触发；timeout 经 abort → error → complete
- layui layer 3.1.1 源码（plugns/layui/lay/modules/layer.js）：确认 `end` / `cancel` / shadeClose / `r.close` 行为及 confirm 的 shade 遮挡
- 主进程提供的浏览器验证记录（连点被 busy 拦截 / 正确索引恢复正常 / 过期索引被 `!m` 守卫拦截 / shadeClose 后 `_removedList = null`），与代码行为一致，已逐项代码层面复核

### 残余风险 / 验证缺口

- 本轮复审未做新的浏览器端验证（只读审查边界）；🔵-6 的挂死场景无法在正常环境下复现验证，仅代码层面推导
- 同标签页跨流程写并发仍未防护（既有暴露面，非本轮修复引入）：批量移除失效（removeFailedCollections 串行链）与弹窗操作可并行；普通取消收藏触发的 removed_add（无守卫、fire-and-forget）与弹窗操作可并行；跨标签页同理。后端 PHP 文件级无锁为全仓既有架构约束，第一轮已明确仅要求前端侧同级别缓解
- 待 builder 处理项：无（🔵-6 / 🔵-7 为可选改进；上轮 🔵-3/4/5 维持可选）
