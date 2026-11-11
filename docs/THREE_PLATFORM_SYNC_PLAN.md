# 三端对齐计划（iOS / Web / 安卓）

制定：2026-11-06。目的：iOS、Web、安卓各自都在往前走，用一份**总账**把"谁做了什么、谁还欠什么"管起来，不再靠记忆。

## 1. 文件在哪

| 内容 | 位置 |
| --- | --- |
| 安卓移植时抓出的 iOS 问题（A–M 节，共约 100 条） | `KnowGrowShow-Android/docs/IOS_ISSUES.md` |
| iOS 功能地图（三端对标基准） | `KnowGrowShow/docs/iOS_WEB_FEATURE_ROADMAP.md`（与 `Development/` 下那份是同一份拷贝，**只保留一份**） |
| Web 改动记录 | `StoneOfHope_Website/WEB_CHANGES.md` |
| iOS 开发日志 | `KnowGrowShow/docs/DEV_LOG.md`（最后一条还停在 2026-08） |
| 数据格式契约 | iOS 仓库 `FIRESTORE_SYNC_CONTRACT.md`、`CRYPTO_SYNC_CONTRACT.md`、`USER_VERSE_CONTRACT.md` |

## 2. 现状盘点（2026-11-06 核对）

安卓最后一次提交是 10-30（C4）。iOS 在那之后的变化**已全部提交**（11-06 补记：`b283ead` PinLog；`dab1a12` 经文笔记；`373d3a5` NoteDetails 改版 + Transform；`490dc68` 构建设置，安卓不受影响）。

### 2.1 iOS 已提交、安卓还没有

| 项 | iOS 提交 | 安卓现状 |
| --- | --- | --- |
| PinLog 取消置顶新规则（更早的日子保留；昨天在则保留今天，不在则去掉今天；晚于今天的去掉；清空则删整条） | b283ead（11-06），Web 已有 `pinDaysToRemoveOnUnpin` | ~~旧规则~~ ✅ 已改并提交（安卓 `f77975f`，`iOS@b283ead`）：`LocalSyncOps.daysToRemoveOnUnpin` / `applyUnpin`，`setPinned` 去掉 `unmarkReferenceDay`；测试照 `PinLogUnpinRuleTests` |
| 预设标签 Trash 用珊瑚色垃圾桶符号、Trash/Milestone 名字前加 🗑️/🏆 | ea717f3、1caae08（10-26） | 11-09 已核对：Trash / Milestone 的名字集和显示一致（安卓 Trash 用 🗑️ 表情代替珊瑚色符号，有意的）；Transform 11-09 已补（E5，特效未做）。顺带发现 iOS 繁体缺这两个预设名（IOS_ISSUES O3） |
| CUV 导入与 KJV 对齐、修复已冻结的错位 UserVerse、不显示"併於上節" | 101ad61、d9ca2c6、e4bb313（10-27） | 11-09 核对：~~不受影响~~ **受影响**——安卓构建时直接用同一份 CUV.xml，同样少 4 节、多 2 节、历代志上 22 章错一节、69 处"併於上節"。已移植三个提交（未提交）：构建时修正（buildSrc `CuvVersificationFix`，结果必须与 KJV 的 31,102 节完全一致，否则构建失败；`BibleTextDatabase.VERSION` 2 让已安装的重新拷贝）、区间里重复的合并节只显示一次、已冻结的错误 `userVerses` 一次性修复（`CuvUserVerseRepair`） |

> **11-08 更新：E1 取消。** 你决定撤销 `dab1a12`（带正文的经文笔记算普通笔记）和 10-11 的 `cca76ed`（按经文分组），iOS 在分支 `verse-notes-revert` 里还原（提交 `577ab14`、`3175662`，另有补取经文和输入面板改动，尚未合入 main）。**安卓保持现状（评论收在经文锚点下），不要做 E1。** 合入 main 后，iOS 的 `isVerseAnchorPlaceholderNote` 会消失。
> 另外新增三端待对齐项（iOS 分支 `verse-notes-revert`，提交 `642cb8f`、`fcc9c6d`、`456b7e6`，11-08）：
> ① 取不到经文时保存仍成功，列表行出现时 / 网络恢复时自动补取所有已选译本，并提示"没有蜂窝或 Wi-Fi"；
> ② `.bibleVerse` 行显示**每个已选译本**（"经文 [NKJV]" 空行 "经文 [和合本]"，只有一个译本时不加标签）；
> ③ 讲道/视频加笔记面板：没有章节标签和提示文字；书卷按钮选书把书名写进正文；"章节笔记"开关默认关——关时无经节的文字是普通讲道笔记，开时挂到讲道那一章；"v3 …" 总是用讲道那一章；
> ④ 讲道/视频笔记列表不随键盘缩短（最后一条加评论时键盘反复弹出的 bug）。
> 安卓逐项核对现状后再排期。
>
> **11-08 晚补记：** 上述分支已合入 iOS main（`456b7e6`）。安卓核对结果和计划见 [`IOS_SYNC_1108_PLAN.md`](IOS_SYNC_1108_PLAN.md)：安卓移植过 `cca76ed` 的按经文分组（要删）；iOS 还原时连带删掉了 Web 的 `studyNote` 类型，已在 iOS `27f0fee` 恢复，安卓保留不用改（IOS_ISSUES E5）。
>
> **11-09 安卓已跟进**（`iOS@3175662` / `577ab14` / `642cb8f` / `456b7e6` / `27f0fee`；进度表见 [`IOS_SYNC_1108_PLAN.md`](IOS_SYNC_1108_PLAN.md)）：①补取 + 提示 ✅；②每个已选译本 ✅；③讲道/视频面板 ✅；④键盘：安卓结构上没有这个问题，模拟器实测通过，不用改；按经文分组已删、Today 格子改回"快速笔记" ✅。新发现的 iOS 问题记在 IOS_ISSUES O1（正文里写经节时不提示）、E6（繁体"快速笔记"）。

### 2.2 iOS 11-06 20:37 的提交（E1 = `dab1a12`；E2–E5 = `373d3a5`）

| 项 | 内容 | 安卓 |
| --- | --- | --- |
| ~~**E1 带正文的经文笔记算普通笔记**~~（已取消，见上） | `isVerseReflection` 的语义变了：有正文的经文快速笔记是 Quick Note 分类下的**独立列表项**（详情里经文卡片放在正文上方），`.bibleVerse` 锚点只把它列为 Reflection；只有**占位正文**的才隐藏。新增 `isVerseAnchorPlaceholderNote`；`shouldHideFromTopLevelList`、Quick Note 过滤、Today 的 quick note 计数都改用它 | `NoteListBuilder.kt:41,110` 仍是旧规则（`isVerseAnchoredQuickNote` 一律隐藏 / 排除）。**行为会不一致，需要改，影响列表、Today 计数、过滤三处** |
| **E2 NoteDetails 改版** | 笔记卡片（`noteCard`）+ 时间轴 + 底部评论输入条；笔记级操作图标（`NoteDetailActionBar`）直接放页面上，评论操作图标常驻；同日评论之间加分隔线；日期块 `NoteDateBlock`；连接线只连连续的日子；日号用纯数字 | ✅ 11-09 按你选的方案 A 跟 iOS 结构，外观用 Material（未提交）；对照表与不跟的地方见 [`NOTE_DETAILS_E2E4_PLAN.md`](NOTE_DETAILS_E2E4_PLAN.md) |
| **E3 Answered 折进评论首行** | "已应允"标记配对一条正文评论时，不单独成行，而是 🙌 显示在该评论首行（和标签、星级同一行）；无可配对时仍单独显示。`threadItemCount` 同步改 | ✅ 11-09（未提交）：`NoteThread.foldAnswered` / `count`，详情页与回顾页 |
| **E4 已应允的祷告不再出现"标记为已应允"输入** | 评论输入条里的 Answered 勾选，在祷告已应允后隐藏，避免重复生成"今日已应允"标记；🙌 动作按钮关掉后才重新出现。新增文案 "Answered Today"、"Mark as Answered"（en / zh-Hans / zh-Hant） | ✅ 11-09（未提交）：规则本来一致；勾选只在输入时出现，隐藏时也不生效（不跟 iOS O4）；「今日已应允」三语对齐 |
| **E5 Transform 预设标签 🦋** | 新预设标签（名字集：transform / transformed / transformation / 转化 / 轉化 / 蜕变 / 蛻變），标签上显示 🦋，加到笔记上播蝴蝶 + 星星特效。iOS 是 `TransformTagAnimation.swift`，Web 是 `shared/lib/transformEffect.ts`；Web 还加了"点预设标签 chip 重播特效"、评论上也能播 | **11-09 安卓已做识别和显示**：`PresetTag.Transform`（名字集与 iOS 一致）、标签前加 🦋、笔记标签面板「推荐」行出现 Transform（三语）；单元测试 + 变异检查；模拟器核对过。**还没做**：蝴蝶 + 星星特效、点标签重播、评论上播（可以排在后面）。**名字集必须三端一致**（它决定标签是否被识别为预设） |
| E6 其他小项 | `monthYearTitle` 改为 locale 自适应（中文变"2026年10月"）；`TagFlowLayout` 支持右对齐；`TodayActivityView` 计数（见 E1） | ✅ 11-09：详情页月份行用 `monthYearTitle`（「October 2026」/「2026年10月」）；其余是 iOS 内部 |

> E1–E6 已提交，可以作为基准（安卓提交信息写 `iOS@dab1a12` / `iOS@373d3a5`）。

### 2.3 Web 有、iOS / 安卓没有

| 项 | 说明 |
| --- | --- |
| Transform 标签与特效 | 同 E5 |
| 评论首行放 Answered 图标 + 标签 + 只读星级；已应允祷告保留 Answered 勾选（3501813、516e585） | iOS 未提交改动里已有同类（E3/E4），说明两边在趋同，但**两边是否一模一样没核对过** |
| 日/夜主题首屏前设置、i18n（复用 iOS `Localizable.strings`）、教会关注与主教会（与安卓共享 `settings/preferences.churches`） | 安卓已做主教会；iOS 还没做（见 IOS_ISSUES M8） |

### 2.4 安卓抓出、iOS 仍未修的问题

`IOS_ISSUES.md` 里状态为 🔴 / 🟡 的条目，iOS 一条都还没回应（目前没有任何一条被标 ✅ 且 iOS 已改）。其中**会让数据出错或丢数据的 🔴 优先**：C 节（同步引擎）、K22（闪退）、M1（导入进度永久丢失）等。另有产品决定类 🟡 需要你逐条拍板（"iOS 修后安卓要改吗"那一列已写好）。

## 3. 防乱的规则（以后都按这个来）

1. **iOS 先提交，安卓才跟。** 安卓对照的基准是 iOS 的某个**提交号**，不是工作区。每个安卓提交信息里写 `iOS@<hash>`。E1–E6 等 iOS 提交后再动手。
2. **一份总账。** 在 `iOS_WEB_FEATURE_ROADMAP.md` 末尾加一张"跨端变更表"，每行：变更项 / 发起端 / iOS 状态+提交 / Web 状态+提交 / 安卓状态+提交 / 对应 IOS_ISSUES 编号。任何一端改了行为，**当天**加一行。上面 §2 就是第一版内容。
3. **谁发现问题谁记。** 安卓发现 iOS bug → 写 `IOS_ISSUES.md`；Web 发现 → 同一张表加到 `IOS_ISSUES.md` 新增一节 N（Web 来源），不另起文件。iOS 修了就把对应行改 ✅ 并写 iOS 提交号。
4. **行为分歧先决定再动手。** 凡是"产品语义"类的（E1、E3 这种），先在总账里写一句**决定**，三端按同一句话实现，而不是各自理解。
5. **预设值与文案三端共用。** 预设标签名字集、Firestore 字段名、`Localizable.strings` 键：以 iOS 为准，Web / 安卓复制不改写。新增键在同一次变更里补齐 en / zh-Hans / zh-Hant。
6. **每个功能一个"验收单"。** 三端各自过一遍同一份清单（用同一条 iOS 格式的数据，在三端打开看是否一样）。安卓已有 `INTEROP_TEST.md`，沿用并补上 Web。
7. **iOS 的 DEV_LOG 补记。** 现在停在 2026-08，之后的改动只有 git log。每次 iOS 提交行为变更，顺手在 DEV_LOG 加一条，这样别的端能读到"为什么"。

## 4. 执行顺序

| 步 | 谁 | 内容 | 前置 |
| --- | --- | --- | --- |
| 0 | 你 ✅ | （已完成）把 iOS 工作区（E1–E6）分成 2–3 个提交：①E1 + 计数 ②NoteDetails 改版 + E3/E4 ③Transform；提交后告诉我提交号 | — |
| 1 | 我 | 建总账表（§3.2），填入 §2 的全部条目与当前状态 | — |
| 2 | 安卓 ✅ | **PinLog 取消置顶新规则**（iOS 已提交，现在就能做）+ 单元测试（照 iOS `PinLogUnpinRuleTests` 的用例） | — |
| 3 | 安卓 | 核对 §2.1 的两条（预设标签显示、CUV 生成）；有差异再修 | — |
| 4 | ~~安卓~~ 取消 | E1（经文笔记语义）：列表、过滤、Today 计数；加互通用例（锚点 + 带正文的经文笔记） | 步 0① |
| 5 | 安卓 | E2–E4（详情改版 + Answered 折入评论首行 + 输入条规则 + 三语言文案） | 步 0② |
| 6 | 你定 | 逐条拍板 `IOS_ISSUES.md` 里 🟡 条目的"iOS 修不修"，我把结果写回表；🔴 的 iOS 修复排期 | — |
| 7 | 三端 | E5 Transform：先定名字集（以 iOS 的为准），三端各做预设标签识别；特效最后做 | 步 0③ |
| 8 | 三端 | 每完成一项，按 §3.6 的验收单跑一遍，总账打勾 | — |

## 5. 我没有核对到的地方

- 安卓 N1/N2 的详情页和 iOS 这次改版**逐项差异**还没对过（E2），只确认了不是同一版；对之前需要看你的设计稿 `docs/design/note-details` 和 iOS 现稿。
- E3 的安卓现状是根据 `NoteDisplay.kt` 里有 `linkedAnsweredCommentId` 推断，没跑 App 确认。
- Web 与 iOS 的 Answered/评论首行是否逐像素一致没核对。
- `StoneOfHope_Website` 只看了 10-20 之后的提交标题，没通读 `WEB_CHANGES.md`；Web 里可能还有 iOS/安卓没跟的小项。
