# 安卓跟进 iOS 11-08 更新

制定：2026-11-08。基准：iOS main `456b7e6`（11-09 补：`27f0fee` 恢复 studyNote）。总账：[`THREE_PLATFORM_SYNC_PLAN.md`](THREE_PLATFORM_SYNC_PLAN.md)。
**以下各项都不改 Firestore 写入格式**（字段、类型、取值都和 iOS 现在写的一样）。

## 进度（2026-11-09）

| 项 | 状态 |
| --- | --- |
| 0 PinLog | ✅ 已提交 `f77975f`（`iOS@b283ead`），顺手删了 `NoteActionDispatcher` 里没人用的 `unmarkReferenceDay` |
| 1 撤销按经文分组 | ✅ `NoteListBuilder` 每条一行，删掉 `NoteRowUi.VerseGroup` / `ReflectionGroupCard`；Today 格子改回"快速笔记" |
| 3 每个已选译本 | ✅ `VerseDisplay.versionBlocks` / `markedBody` / `joined`；笔记列表、讲道/视频页、讲道阅读、详情（每个译本一段，带译本标签）、每日回顾、Moments、标签页、章节页和经节页都换了 |
| 2 补取 + 提示 | ✅ `VerseDisplay.isMissingVerseText`；Today 笔记列表和讲道/视频页的行出现时、网络变化时补取；`VerseReferenceSaveResult.textMissing`，讲道/视频页 Snackbar 6 秒（三语）。iOS 只在引用框路径提示，记为 IOS_ISSUES O1；安卓两条路径都提示 |
| 4 讲道/视频面板 | ✅ `ContainerNoteSheet`：书卷按钮 + "章节笔记"开关（默认关）+ 正文框；`SermonRepository` / `VideoRepository.saveNote(chapterNote)` |
| 模拟器（11-09，Medium_Phone_API_36） | ✅ 面板（开关关 → "📖 Sermon Note"，开 → "Chapter · John 3"；"v5 …"、选书 Romans + "8:28 …" 都存成经文）；加选和合本后旧锚点自动补出中文，列表 "…[KJV]" 空行 "…[和合本]"，详情两段各带译本标签，章节页同样；删掉锚点后两条 3:5 的笔记在 Today 是两行；Today 格子"Quick Note"；无闪退 |
| 测试 | 全量通过（410 个，0 失败）；lint 52 条，多出的 1 条是 `libs.versions.toml` 有新版本，改动的文件里没有；5 处"故意改坏"都被测试抓到 |

## 0. 先收尾：PinLog 取消置顶新规则（`iOS@b283ead`，未提交）

- 改动：`LocalSyncOps.daysToRemoveOnUnpin` / `applyUnpin`，`NoteActionRepository.setPinned` 去掉 `unmarkReferenceDay`；测试 `PinLogUnpinRuleTest`（照 iOS `PinLogUnpinRuleTests`）+ `NoteActionRepositoryTest` 6 条。
- 全量 `assembleDebug + testDebugUnitTest + lintDebug`：**通过**，404 个测试 0 失败，lint 51 条警告（与上次相同，全是依赖版本/KTX 类，没有新增）。
- 小尾巴：`NoteActionDispatcher.dispatch` 的 `unmarkReferenceDay` 参数现在没人用了，建议提交前顺手删掉（只是清理，行为不变）。

## 1. 撤销"按经文分组"（`iOS@3175662`、`iOS@577ab14`）

**你的决定**：经文评论只收在 `.bibleVerse` 锚点下面，不单独成行。E1 已取消。

| 点 | iOS 还原后 | 安卓现状 | 要改 |
| --- | --- | --- | --- |
| 有同日锚点的经文笔记 | 隐藏，收在锚点下 | 一样（`NoteRelations.isHiddenFromTopLevel`） | 不用改 |
| Quick Note 过滤 / Today 计数 | 排除所有经文快速笔记 | 一样 | 不用改 |
| **没有同日锚点的经文笔记**（锚点删了 / 不同天） | 每条单独一行 | 安卓移植了 `cca76ed`：同一经文的几条合成一行（`NoteRowUi.VerseGroup` / `ReflectionGroupCard`，`NoteListBuilder.rowsForDay` 聚拢 + 按圣经顺序排） | **删掉分组**，每条一行，按时间排 |
| Today 快速笔记格子标题 | "Quick Note / 快速笔记" | "Note / 笔记"（`cca76ed` 改的） | 改回 `label_quick_note` |
| `studyNote` 类型（Web 写的"学习笔记"） | 还原时一并删掉了，已在 `iOS@27f0fee` 恢复（Today 格子、过滤、日历色条、每日回顾、章节/经文查询、经文角标；不含经文分组） | 有：Today 格子、过滤器、日历色条、每日回顾都认 | **不用改**（保留，即原方案 A） |

**`studyNote`：已定，安卓保留。** 还原 `cca76ed` 时 iOS 把 `.studyNote` 一起删了，导致 Web 的学习笔记在 iOS 上变成快速笔记、再推送时被改写成 `"note"`。iOS 已在 `27f0fee`（2026-11-09）恢复这个类型，经文分组仍然去掉。安卓一直保留，不用改。已记入 `IOS_ISSUES.md` E5（✅）。

顺带记一条 iOS 小问题：`zh-Hant` 的 "Quick Note" 写成了简体"快速笔记"（`27f0fee` 也没改），安卓用"快速筆記"。已记入 `IOS_ISSUES.md` E6。

## 2. 取不到经文时照常保存 + 自动补取（`iOS@642cb8f`，`iOS@456b7e6` 的 `isMissingVerseText`）

- iOS：保存经文时若所有译本都没取到正文，笔记照常保存，并发通知；**讲道/视频笔记页**弹提示（没网："没有蜂窝或 Wi-Fi…"，有网但取不到："暂时拿不到经文…"，显示 6 秒）。列表行出现时、网络恢复时，只要有任一已选译本缺正文就调 `ensureUserVerses` 补取。
- 安卓现状：保存本来就不会失败；Today 列表的经文分组行出现时会补取一次（随第 1 项一起删掉），其他列表行不补取；没有提示。安卓的 KJV / 和合本是内置的，**自己保存的经文几乎不会缺正文**；会缺的是 iOS 同步来的 NKJV / NIV 锚点（安卓暂时取不到这两个译本）。
- 要改：
  - `VerseDisplay.isMissingVerseText`（同 iOS：已选译本 + 锚点自己的译本，有一个没正文就算缺）。
  - 列表（Today 笔记列表、讲道/视频页）里的 `.bibleVerse` 行：出现时、`NetworkMonitor` 从离线变在线时，缺正文就补取一次。
  - 保存结果带上"正文没取到"，讲道/视频页用 Snackbar 提示（和 iOS 一样只在这两页）；三语文案照抄 iOS（繁体用"行動網路"）。
- 测试：缺正文判定、补取后显示正文、保存结果带标记。

## 3. `.bibleVerse` 行显示每个已选译本（`iOS@456b7e6`）

- iOS：`verseVersionBlocks` = 已选译本（锚点自己的译本不在已选里时放最前），每个有正文的译本一块；只有一块时只显示正文；多于一块时 "正文 [NKJV]" 空一行 "正文 [和合本]"（标签用 `toDisplay`，CUV 显示"和合本"），荧光笔/下划线照旧画在各自译本上。
- 安卓现状：只显示锚点主译本（`VerseDisplay.resolve` 只读自己译本的 `userVerses`）。
- 要改：`VerseDisplay` 加 `versionBlocks` / 多译本的 `displayContent` 和带标记的 `AnnotatedString`；所有显示 `.bibleVerse` 正文的地方都改用它（笔记列表、讲道/视频页、详情、每日回顾、Moments、标签页、讲道阅读、章节页的经文、复制）。读取范围从"自己译本的 id"扩到"已选译本 + 自己译本的 id"。闪卡、祷告的经文不变（iOS 也没改）。
- 测试：一个译本 / 两个译本 / 缺一个译本 / 锚点译本不在已选里，四种情况的拼接结果。

## 4. 讲道/视频加笔记面板（`iOS@642cb8f` + `iOS@456b7e6`）

- iOS：讲道/视频页的加笔记面板**没有经文引用输入框、没有章节标签、没有提示文字**；只有"书卷"按钮 +"章节笔记"开关（默认关）+ 正文框。选书后把书名写到正文开头并把光标放回正文；保存需要有正文。
  - 开关关：没有经节的文字存成普通讲道/视频笔记（不挂章）；开：挂到讲道所在章（`CH:b:c`）。
  - "v3 …" 不管开关都用讲道那一章；"Hebrews 4:3 …" 照常识别。
- 安卓现状：用的是 Today 那个 `NoteSheet`，有引用输入框并预填讲道的章；没有经节的文字**总是**挂到章上。
- 要改：`NoteSheet` 加一个讲道模式（书卷按钮 + Material `Switch`"章节笔记" + 正文框），保存时不传引用，`saveNoteFromContent(anchorPlainNote = 开关)`；"v3" 走现有的 `anchorChapter`。文案"章节笔记"三语已有同义 key，补一个面板用的。
- 同一提交的"导航栏不透明、信息栏压在列表上方"：安卓本来就是（`TopAppBar` 不透明，信息栏在列表上方的 `Column` 里），**不用改**，实现时在模拟器上看一眼。
- 测试：开关关/开、"v3" 在开关关时仍挂讲道那章、选书写进正文。

## 5. 讲道/视频笔记列表与键盘（`iOS@fcc9c6d`）

- iOS 的问题：评论面板挂在列表行上，列表随键盘缩短时最后一行被回收，面板和键盘反复弹出。
- 安卓：评论面板挂在页面层（`NoteActionsHost`），不挂在行上，结构上不会有这个问题。
- **模拟器实测（11-08，13 条笔记的讲道页）：通过。** 最后一条普通笔记、最后一条经文锚点（加感想）、中间一条、最后一条只露出一半，四种情况键盘都稳定弹出、不来回跳，评论都保存成功；键盘弹出时后面的列表不缩、不跳，面板的取消/保存按钮在键盘上方。**不用改。**
- 实测时顺带看到的小问题（和这次 iOS 更新无关，要不要修由你定）：
  1. 给最后一条笔记加完第一条评论后，卡片变高但列表不自动滚，卡片底部被"+"按钮压住一点（手动能滚到底）。
  2. 新建讲道面板弹出键盘时，"取消/创建"按钮大半被键盘挡住，要先收起键盘才能点"创建"。

## 6. 只列入、这次不做（除非你确认）

E2 NoteDetails 改版、E3 已应允标记折进评论首行、E4 已应允的祷告不再出现"标记为已应允"、E5 Transform 预设标签（`iOS@373d3a5`）。另外总账里还没做的安卓项：§2.1 两条核对（预设标签显示、CUV 生成）、E6 月标题本地化核对、跨端变更表（步 1）。

## 7. 做法

按顺序 1 → 3 → 2 → 4（2 依赖 3 的多译本判定）；每项单元测试 + 一次"故意改坏、确认测试会失败"；模拟器验证，需要时配合 Firebase 本地模拟器放一条 iOS 格式的 NKJV 锚点看补取和多译本。做完更新总账（安卓状态 + iOS 提交号）、`IOS_ISSUES.md` 加新条目（编辑前重新读，只加自己的行）。提交前问你，不推送。
