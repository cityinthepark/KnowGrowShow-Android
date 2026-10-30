# 抽认卡、标签、设置与个人页（阶段 4）— 安卓方案

**状态：待用户确认（2026-10-28 起草）。确认前不写实现代码。**

依据（iOS 代码为准，只读）：
- 抽认卡：`FlashCard`、`FlashCardListView`（含 `FlashCardListViewModel`、`FlashCardRow`）、`AddFlashCardSheet`、`FlashCardDetailView`（`.review` 翻牌、`.display` 滑动卡片）、`VerseCardDesign`、`DailyBibleVerseCardItem`（启动开关）、`MainTabView.presentActiveFlashCardsIfNeeded`、`TodayStudyToolsRow`（"记忆卡 N"）、`NoteCellView.bibleVerseBadgeMenu`（从笔记加入）。
- 数据：`NotesManager` 的 `addFlashCards`、`addFlashCard(fromBibleVerseNoteId:)`、`toggleFlashCardArchive`、`deleteFlashCard`、`flashCardActiveAndTotalCounts`、`firstActiveFlashCardId`、`flashCardMatching`、`displayContent(for card:)`；`FlashCardUserVerseBackfill`；`FirestoreUserDataPush` / `Pull` 的 flashCards、tags 部分。
- 标签：`Tag`、`MyTagsView`（含合并选择弹窗 `TagMergePickerSheet`）、`TagNotesView`、`TagPickerPopup`、`PresetTag`（`TrashTagAnimation.swift`）；`NotesManager` 的 `createTag`、`renameTag`、`deleteTag`、`mergeTags`、`taggedItemCount`、`reassignTag`、各种 `addTags` / `removeTag`。
- 设置与个人页：`SettingsView`、`ProfileView`、`AvailableVersionsView`、`BibleVersionCopyrightView`、`AppAppearance`（主题 / 字号）、`AppRootView`、`ScriptCaptureDurationPreference`、`LegalURLs`、`OnlineResourceDetailView`、`ChurchSermonImportService`、`HowToUseView`、`NavigationCoordinator`。

## 1. iOS 现在怎么做

### 1.1 抽认卡的数据和写入规则

安卓的 `FlashCardEntity`、`FlashCardDao` 和推送 / 拉取格式（阶段 1）已经与 iOS 一致，这一阶段只是开始在安卓上**新建和修改**。

| 项目 | iOS 的规则 |
| --- | --- |
| 卡片存什么 | 只存经文**范围**，不存经文正文：`bookOrd`、`chapterOrd`、`verseStart`、`verseEnd`（单节时为空）、`versionAbbr`（存储格式，大写，如 `CUV`）、`refKey`（`VS:书:章:起-止`，止缺省等于起）、`topic`（可选）、`isArchived`、`createdAt`、`updatedAt`。正文在 `userVerses` 里按版本逐节存，卡片显示时去读 |
| 新建（`+` 按钮） | 经文框用 `.flashCard` 规则：**必须到节**，只写到章不行。可再填"主题"（可选）。**对当前已选的每个译本各建一张卡**（选了两个译本就是两张），先把这段经文的正文冻结进 `userVerses`；某个译本取不到正文就跳过那个译本；全都取不到就报"无法添加" |
| 已存在 | 同一 `refKey` + 同一译本、没删除的卡：已归档 → 恢复为进行中；已在进行中 → 只刷新 `updatedAt`。两种情况都不新建 |
| 从笔记加入 | 经文笔记（`bibleVerse`）的经文标签点一下出菜单"加入抽认卡"。用这条笔记的范围和**它自己的译本**；没有该译本正文则失败；已存在同样处理（并且恢复），但结果没有任何提示 |
| 归档 / 恢复 | 切换 `isArchived`，同时 `updatedAt` = 现在，`needsSync` = true。列表里叫"进行中 / 已完成" |
| 删除 | 软删除：`deletedAt` = `updatedAt` = 现在。再次添加同一节会建一张**新卡**（新 id） |
| 日期 | `createdAt`、`updatedAt` 都是此刻的完整时间（不是本地零点） |
| 云端字段 | `ownerUid`、`createdAt`、`updatedAt`（服务器时间戳，同步水位）、`contentUpdatedAt`（本地 `updatedAt`）、`isArchived`、`schemaVersion`、可选的 `bookOrd`/`chapterOrd`/`verseStart`/`verseEnd`/`versionAbbr`/`refKey`/`topic`，并且每次写 `content` = 删除。删除时只发"墓碑"。安卓编码 / 解码已按此实现，本阶段不改 |

### 1.2 抽认卡界面

- **入口**：Today 的"记忆卡 N"（N = 进行中数量，为 0 时不显示数字）→ 抽认卡列表。
- **列表页**：
  - 标题"抽认卡"；左上"按书卷 / 按日期"切换（默认按日期，`updatedAt` 新的在前；按书卷是书卷 → 章 → 节 → 译本 → 日期）；右上是当前译本文字，点它进译本选择页；底部 `+`。
  - 两段："进行中"（段头右侧有 ▶ 开始复习）和"已完成"。
  - 每行：经文引用（含译本）、一行正文预览、归档 / 恢复图标、删除图标（点删除先弹确认）。点行进详情。
- **详情页（翻牌复习）**：一张卡，正面是引用（和主题），点一下翻到背面看经文；左右滑切换同一批进行中的卡，底部"第 N 张 / 共 M 张"。已完成的卡只显示它自己，不能滑。
- **滑动卡片（展示模式）**：全屏背景图 + 引用 + 大字经文，左右滑、圆点指示；最多 5 张（按 `updatedAt` 新的在前）；点任意处关闭，右上角"⋯"菜单只有一项"不再显示最近经文"（关掉启动开关并关闭）。经文上如果有网页端同步下来的划线 / 下划线，会叠加显示（iOS 只读）。
- **启动弹出**：App 启动、以及**每次从后台回到前台**，如果开关"启动时显示经文"开着、有进行中的卡，就延迟 0.35 秒全屏弹出滑动卡片（有待处理的 YouTube 分享时不弹）。
- **▶ 开始复习**：打开同一个滑动卡片，同样只有 5 张。

### 1.3 标签

| 项目 | iOS 的规则 |
| --- | --- |
| 标签文档 | `tags/{id}`：`ownerUid`、`name`、`createdAt`、`schemaVersion`、`updatedAt`（服务器时间）；删除发墓碑。`createdAt` 是创建那一刻 |
| 谁带标签 | 笔记、评论、`userVerses`、讲道、视频，各自存 `tagIds`。安卓的这些字段阶段 1 已有 |
| 新建 | 名字去空白；与现有未删除标签**不区分大小写**重名则什么都不做（不新建） |
| 改名 | 只改 `name`，`needsSync`；**不检查重名**（L7 见下） |
| 删除 | 从所有带它的笔记、评论、经文、讲道、视频里去掉，再给标签打墓碑 |
| 合并 | 把"源标签"在所有对象上换成"目标标签"（目标已有则只去掉源），源打墓碑。确认文案："把 X 合并到 Y，影响 N 项" |
| 同步标记 | 被改的对象 `needsSync` = true；**笔记**额外 `updatedAt` = 现在；**评论**把所属笔记 `needsSync`、`updatedAt` 也更新；经文、讲道、视频只设 `needsSync`。**这条规则对合并 / 删除标签是 iOS 的做法，安卓不照做，见 L11** |
| 选标签弹窗 | 已有的 `TagPickerPopup`（安卓笔记里已有 `TagPickerSheet`，讲道编辑页也在用）。给**笔记**打标签时多一栏"推荐"：还没建过的预设标签 **Trash（垃圾）** 和 **Milestone（里程碑）**，点一下就建；加上后在列表里播放动画（垃圾桶动画 / 纸屑）。预设标签在所有地方名字前带标记（Trash 是珊瑚色垃圾桶图标，Milestone 是 🏆），按各语言名字匹配 |
| 我的标签页 | 顶部"新标签"输入框 + 添加；列表按创建时间；每行：标签名（带预设标记）+ "N Notes • N Comments"；点进入标签笔记页；长按（或左滑）：改名、合并（多于一个标签时）、删除。改名是行内编辑 |
| 标签笔记页 | 标题是标签名。三段：讲道、视频（只含个人视频）、笔记（直接带标签的笔记 + 评论带标签的所属笔记，创建时间新的在前）。点进对应页面。都没有时写"还没有内容" |

### 1.4 设置 tab 和个人页

iOS 里相关入口分在**两处**（安卓现在只有设置 tab 的"账号与同步"和版本号）：

**设置 tab（`SettingsView`）**，从上到下：
1. 「在线资源」（`onlineResourcesEnabled`）：一行 Riverside Calvary Chapel（名称 + 副标题），点进 `OnlineResourceDetailView`。
2. 「应用设置」：
   - 外观主题：跟随系统 / 浅色 / 深色；
   - 字号：小 / 中 / 大 / 特大（只在整个 App 根上设 `dynamicTypeSize`，代码里写死字号的地方不跟着变）；
   - "启动时显示经文"开关（默认开，键 `showDailyBibleVerseCardsOnLaunch`）；
   - "字幕摘录时长"步进器，1–30 秒，默认 10（键 `scriptCaptureDurationSeconds`，与讲道页齿轮里是同一个设置；安卓讲道页已有）。
3. 「同步」→ "同步冲突"（带红色数字）：属于最后的账号与同步阶段。
4. 「法律」：隐私政策、使用条款（用浏览器打开）。

**个人页（`ProfileView`，Today 右上角头像进入）**：
- "我的标签"→ `MyTagsView`；
- "已选经文译本 / 当前 KJV…"→ `AvailableVersionsView`；
- "账号"→ 账号页（最后阶段）。

**译本选择页（`AvailableVersionsView`）**：按语言分段列出 `AVAILABLE_VERSIONS`（KJV、NIV、NKJV、和合本）。每行：名称 + 状态（"已下载" / "使用网络"）+ 右侧圆形勾选（最多 2 个，选满后再选弹提示；全取消时自动补回默认译本）；点行进版权说明页。右上角 ⓘ 弹简介。选择存在 `selectedBibleVersions`（"KJV,CUV"，设备本地，不上传）。

**其它**：
- `HowToUseView`（帮助）在 iOS 里**没有任何入口**（`goToHowToUse()` 从未被调用）；
- 在线资源详情 / 教会导入：见 §2.4。

## 2. 安卓做法

结构和信息照 iOS，组件用 Material，颜色用 `AppColors`（`flashChip` 已有）。**分三步做**（§4 问题 1），每步都可以单独验证。

### 2.1 步骤 P4-1：抽认卡

- **数据层**：新增 `FlashCardRepository`（Hilt），函数和 iOS 一一对应：
  `add(reference, topic)`（对每个已选译本各建一张；复用 `NoteRepository` 里已有的"取各译本经文正文并写入 userVerses"逻辑）、`addFromBibleVerseNote(noteId)`、`toggleArchive`、`delete`、`observeLive / observeCounts`。写入的字段、`refKey` 格式、`versionAbbr` 大写、时间用 `clock.instant()`，全部按 §1.1。
- **列表页** `FlashCardListScreen`：与 §1.2 相同；排序切换用文字按钮；行里归档 / 删除用 `IconButton`，删除用确认对话框；新建用底部弹窗，沿用 `BibleReferenceField` 的 `flashCard` 规则（必须到节）加"主题"输入框。"已存在"时给出提示（L1）。
- **详情页（翻牌）**：正面引用，点一下翻面看经文（用 Compose 的 `graphicsLayer.rotationY` 做翻转），左右滑切换（`HorizontalPager`），底部"第 N 张 / 共 M 张"。已完成的卡只显示它自己。
- **滑动卡片**：全屏、渐变遮罩 + 深色背景（iOS 的海洋背景图是 App 资源，安卓没有这张图，先用 `VerseCardDesign` 里的深绿渐变，等图片阶段再换，见 §4 问题 2）；`HorizontalPager` + 圆点；经文上叠加网页端的划线（沿用笔记详情已有的 `VerseMarks`）；"⋯"菜单只有"不再显示最近经文"。
- **启动弹出**：`MainScreen` 加载后，按设置开关和"有进行中的卡"弹出；时机见 §4 问题 3（推荐只在冷启动和离开超过 30 分钟再回来时弹）。分享来的 YouTube 链接待处理时不弹。
- **入口**：Today 的"记忆卡 N" → 列表页（现在是"即将推出"）；经文笔记的经文标签点一下 → 菜单"加入抽认卡"，结果用提示条告诉用户（L3）。
- **设置**：开关 `showDailyBibleVerseCardsOnLaunch` 存 `app_prefs`（与 iOS 同名键）；设置页的开关在 P4-3 加，这一步先读默认值（开）。

### 2.2 步骤 P4-2：标签

- **数据层**：新增 `TagRepository`：`create`（不区分大小写重名则不新建）、`rename`（重名时拒绝并提示，L7）、`delete`、`merge(sourceId, targetId)`，以及改笔记 / 评论 / 经文 / 讲道 / 视频的 `tagIds`；**合并和删除标签时，所有对象只设 `needsSync`，不改笔记的 `updatedAt`**（L11；评论也不带动所属笔记）；`counts(tag)` 给列表和合并确认用。`TagDao.observeActiveByCreation` 已有；需要给各个 DAO 补"含某个标签的未删除对象"的查询。
- **我的标签页** `MyTagsScreen`：顶部输入框 + 添加；列表按创建时间；点行进标签笔记页；长按弹菜单（改名、合并、删除），改名用行内编辑；合并先选目标再弹确认；删除弹确认。每行摘要写"N 条笔记 · N 条评论 · N 篇讲道 · N 个视频"（有才写，L8）。
- **标签笔记页** `TagNotesScreen`：讲道 / 视频 / 笔记三段，笔记卡沿用 `NoteCards`；点进对应页面。
- **预设标签**：Trash / Milestone 的名字匹配（英文 + 三种语言的译名）和名字前的标记（Milestone 🏆；Trash 用 Material 垃圾桶图标，珊瑚色），在 `TagPickerSheet`、标签页、标签笔记页、笔记卡上显示；给笔记打标签时加"推荐"一栏。**动画不做**（纯装饰，iOS 有、安卓暂无，记在这里）。
- **入口**：个人页的"我的标签"；本步先把个人页的"我的标签"做出来，个人页的其余项在 P4-3。

### 2.3 步骤 P4-3：设置与个人页

- **个人页** `ProfileScreen`（Today 头像进入）：我的标签、已选经文译本、账号（先放到账号页的现有入口）。
- **设置 tab**：
  1. 外观主题（系统 / 浅色 / 深色）：存键 `appThemePreference`（`system`/`light`/`dark`），在根 `Theme` 上生效；
  2. 字号（小 / 中 / 大 / 特大）：存键 `appFontSizePreference`（`small`/`medium`/`large`/`extraLarge`），映射到根上的 `fontScale`（0.9 / 1.0 / 1.15 / 1.3，叠加在系统字号上）；
  3. 启动时显示经文开关；
  4. 字幕摘录时长步进器（1–30，与讲道页同一个 `ScriptCaptureDuration` 键）；
  5. 法律：隐私政策、使用条款（`https://stoneofhope.app/privacy.html`、`terms.html`，用浏览器打开）；
  6. 保留现有"账号与同步"和版本号。"同步冲突"留到账号与同步阶段。
- **译本选择页** `BibleVersionsScreen`：按语言分段列 `BibleVersions.available`；最多 2 个、至少 1 个（全取消补回默认）；点行进版权说明页；ⓘ 弹简介。安卓两个本地译本（KJV、和合本）随 App 安装，状态一律写"已内置"，没有下载；NIV / NKJV 的做法见 §4 问题 2。
- **帮助页**：不做（iOS 没有入口，L5）。

### 2.4 在线资源 / 教会导入：建议推迟

iOS 只有一个教会（Riverside Calvary Chapel）：设置里一行 → 详情页（教会 Logo、简介、"导入全部讲道 / 导入最新更新"）→ 联网读该教会 Git 仓库的 `manifest.json` 和按版本命名的系列 / 讲道 JSON，写成 `isUserData=false` 的讲道和系列（只在本机，不上传），并记录已导入的版本号。

建议**推迟到阶段 4 之后单独做**，理由：
- 它依赖教会档案 JSON、Logo 图片、一个外部仓库的文件格式，与抽认卡 / 标签 / 设置没有关系；
- 安卓的讲道 / Series 页面已经能显示 `isUserData=false` 的内容（阶段 3），所以将来加导入只是"联网读取 + 写入"，不会改动已有页面；
- 阶段 4 的三步里不含网络请求，验证简单。

## 3. 发现的 iOS 问题（L 节，写入 IOS_ISSUES.md）

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| L1 🟡 | `FlashCardListView` 新建回调、`AddFlashCardSheet` | 经文已在进行中时，`alreadyActive` 被当作成功，"已添加"提示永远不会弹出，弹窗直接关闭；那张已有的卡悄悄跳到最前面 | 用户不知道这张卡早就有了 | 弹出"这张卡已经在进行中"，不关闭弹窗 | 不用 |
| L2 🟡 | `FlashCardListView` ▶、`FlashCardDetailView.displayCards` | "开始复习"只翻前 5 张（启动弹出才该限制 5 张） | 超过 5 张的卡无法通过 ▶ 复习 | ▶ 翻全部进行中的卡；启动弹出仍是 5 张 | 不用 |
| L3 🟡 | `NoteCellView.bibleVerseBadgeMenu` | "加入抽认卡"的结果被丢弃：没有成功 / 失败提示；对已归档的卡会悄悄恢复 | 点了没反应，不知道成没成功 | 用提示条说"已加入抽认卡 / 已恢复 / 已在抽认卡中 / 无法添加" | 不用 |
| L4 🟡 | `MainTabView.presentActiveFlashCardsIfNeeded` | 不只启动时弹，**每次从后台回到前台**都弹全屏卡片；名字叫 "daily" 但没有按天限制 | 每次切回 App 都被卡片挡住一次，要点关闭 | 见 §4 问题 3 | 若 iOS 改成每天一次，安卓跟着改 |
| L5 🟡 | `HowToUseView`、`NavigationCoordinator.goToHowToUse` | 帮助页写好了但没有任何入口，文案也没有本地化键 | 无用代码 | 不移植 | 不用 |
| L6 🟡 | `NotesManager.addFlashCards` | 经文只有一个译本取不到正文时，那个译本悄悄跳过，仍显示"成功" | 选了 NIV + KJV，NIV 没网时只得到 KJV 一张，用户不知道 | 成功后提示"以下译本没有经文正文：NIV，未添加" | 不用 |
| L7 🟡 | `NotesManager.renameTag` | 改名不检查重名（新建会检查）；可以改成与另一个标签相同的名字，之后按名字选标签会选到不确定的一个 | 出现两个同名标签 | 重名时拒绝，提示"已有同名标签，可以用合并" | 不用 |
| L8 🟡 | `MyTagsView.TagRowContent` | 摘要只统计笔记和评论，且包含已删除的笔记；"Notes / Comments" 写死英文；只带讲道 / 视频的标签摘要是空的 | 数字不对，中文界面有英文 | 统计未删除的笔记、评论、讲道、视频，用界面语言 | 不用 |
| L9 🟡 | `NotesManager.taggedItemCount` | 合并确认里的数字：经文、讲道、视频没有排除已删除的；笔记和它的评论分别计数 | 确认框里的数与列表不一致 | 与 L8 用同一套计数 | 不用 |
| L10 🟡 | `MyTagsView`、`TagPickerPopup`、`TagEditRow` | "Delete Tag"、"Delete"、"Cancel"、"Edit tag"、"Choose Tags"、"By Name"、"By Create Date"、"Close"、"Add new tag..." 等没有走本地化 | 中文界面有英文 | 三种语言都有 | 不用 |
| L11 🟡 | `NotesManager.reassignTag`（合并 / 删除标签） | 把受影响的笔记 `updatedAt` 改成现在，评论还带动所属笔记 | 合并或删除一个标签后，一批旧笔记的"最近修改"变成今天，排序和 Today / Moments 里的日期被打乱 | **按你的决定（2026-10-28）**：只设 `needsSync`，不动 `updatedAt`（`tagIds` 照常同步） | 不用 |

不影响数据格式；L1–L3、L6–L9、L11 是安卓直接修正的行为，L4 等待用户决定。

## 4. 需要用户确认的问题

1. **怎么拆**：推荐分三步 **P4-1 抽认卡 → P4-2 标签 → P4-3 设置与个人页**，每步做完测完再下一步（抽认卡里的"启动时显示"开关先读默认值，设置页开关在 P4-3 接上）。在线资源 / 教会导入推迟到阶段 4 之后（§2.4）。另一个选择是把教会导入也放进本阶段，会多一个联网和档案文件的步骤。
2. **NIV / NKJV 译本和卡片背景图**：
   - **NIV / NKJV**：安卓还没接 API.Bible（要联网和收费密钥），选了也取不到经文。推荐在译本选择页里**列出来但不能选**，注明"需要联网，暂不支持"，这样不会出现"选了却一张抽认卡也建不出来"。（iOS 允许选，离线时同样建不出。）另一个选择是照 iOS 允许选。
   - **滑动卡片背景图**：iOS 用 App 里的一张海洋照片。推荐先用深绿渐变，等图片阶段再换；另一个选择是你把图给我，我放进安卓资源。
3. **启动时弹抽认卡的时机（L4）**：iOS 是每次回到前台都弹。推荐安卓只在**冷启动**和**离开超过 30 分钟后回来**时弹，每次点"不再显示"或设置里的开关都能关；另一个选择是完全照 iOS（每次回前台都弹）。

## 5. 步骤与验证

**每一步共同的验证**：Robolectric 单元测试 + 变异检查（临时改坏一处看测试会不会失败）；安卓模拟器 + Firebase 本地模拟器实机走一遍；`lint` 不新增警告；提交前确认 `dev.properties`、`keystore.properties`、`google-services.json` 和 `.kotlin/` 没被暂存；只在用户要求时提交到 main，不推送。

- **P4-1 抽认卡**
  - 单测：`FlashCardRepository`（每个已选译本各一张、取不到正文的译本跳过、已存在恢复 / 刷新、`refKey` 与 `verseEnd` 空值、归档 / 删除的 `updatedAt` 与 `needsSync`、再次添加删除过的卡得到新 id）；列表排序（按日期 / 按书卷）；启动弹出的判断（开关、有无进行中的卡、时间间隔）。
  - 模拟器：Today 记忆卡数字 → 列表；新建（到节通过、只到章被拒）、归档 / 恢复 / 删除；翻牌与滑动；滑动卡片的启动弹出与关闭；从笔记经文标签加入；推送后在本地模拟器里检查文档字段（含 `contentUpdatedAt`、`content` 被删除、墓碑），并用 iOS 同一个模拟器账号确认两边互相可见。
- **P4-2 标签**
  - 单测：新建 / 改名 / 删除 / 合并对笔记、评论、经文、讲道、视频 `tagIds` 的影响与同步标记（合并 / 删除后笔记 `updatedAt` 不变、`needsSync` 为 true）；重名规则；计数；预设标签名字匹配。
  - 模拟器：我的标签页全部操作；标签笔记页三段；笔记里打 Trash / Milestone 推荐标签；推送后检查 `tags` 文档与被改对象的 `tagIds`。
- **P4-3 设置与个人页**
  - 单测：偏好读写（键名与 iOS 相同）、字号映射、译本选择规则（最多 2、至少 1、补回默认）。
  - 模拟器：主题与字号即时生效并在重启后保留；译本选择后新建抽认卡 / 经文用新译本；隐私 / 条款链接可打开；字幕摘录时长与讲道页齿轮同步。

## 6. 实现

**P4-1 抽认卡（已完成，未提交）**：`FlashCardRepository`、`FlashCards`/`LaunchDeckPolicy`、`FlashCardPreferences`；列表、新建弹窗、翻牌页、滑动卡片（iOS 海洋背景图转 JPEG 放进资源）；Today 入口、笔记⋯菜单"加入抽认卡"（放在菜单而不是经文标签上）、冷启动/离开30分钟后弹出。P4-2、P4-3 未做。

## 7. 验证

P4-1：337 个测试通过，lint 仍是 51 个警告。变异检查抓到 4 处，漏掉"按书卷排序同一节的日期顺序"（待补测试）。模拟器：新建、重复提示、章节被拒、列表、滑动卡片、翻牌、冷启动弹出且 30 分钟内不再弹、笔记菜单加入均正常。未做：推送到 Firebase 模拟器的字段检查（线格式已有同步测试覆盖）。
