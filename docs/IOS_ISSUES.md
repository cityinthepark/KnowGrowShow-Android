# 移植中发现的 iOS 问题清单

移植安卓时逐行对照 iOS 代码发现的问题。每条都写明：**iOS 位置、问题、影响、安卓现在怎么做、iOS 修了之后安卓要不要跟着改**。

原则：安卓已直接修正的，**写入 Firestore 的数据格式与 iOS 完全相同**，所以两端现在就能互通；修 iOS 时以本表的"安卓做法"为准，两端行为就会一致。

状态：🔴 待修 · 🟡 待确认 / 产品决定 · ✅ 已处理

最后更新：2026-10-28（K 节）

---

## A. 文档与代码不一致

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| A1 🔴 | `docs/CRYPTO_SYNC_CONTRACT.md` §2.3、§3 | 正文里密钥路径还是旧的 `users/{uid}/secure_keys/note_key`；实际代码用 `private/{uid}/secureKeys/noteKey`（只在开头声明旧路径作废） | 新人照正文写会写错路径 | 用 `private/{uid}/secureKeys/noteKey` | 不用 |
| A2 🔴 | `docs/FIRESTORE_SYNC_CONTRACT.md` §2.4 | 文档说删除写 `deletedAt` + `isDeleted`；实际 `FirestoreTombstone.slimPayload()` 整个文档替换成 `{isDeleted: true, updatedAt}` | 文档误导 | 照代码：整文档替换为 `{isDeleted, updatedAt}`；读取时 `isDeleted == true` 或有 `deletedAt` 都算删除 | 不用（改文档即可） |
| A3 🔴 | `docs/USER_VERSE_CONTRACT.md` §1.1 | 文档说"新写入的 Note 只写 ord，不写 `refKey`、`chapterKey`"；实际 `saveVerseReference` / `applyBibleAnchor` / `refreshAnchorMetadata` 仍给锚点、感想、祷告写 `refKey = "VS:书:章:起-止"` 和 `chapterKey = "CH:书:章"` | 照文档实现的一端会少写字段 | 照代码写这两个字段 | 不用（改文档即可；若 iOS 停写，安卓同步停写） |

## B. 安全

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| B1 ✅ | `KnowGrowShow.xcscheme`（共享方案） | 演示账号邮箱、密码明文提交在 git | 密码进了私有仓库历史 | 安卓用 gitignore 的 `dev.properties` | — 已于 `b2e0b92` 移除；生产环境已关闭邮箱登录，旧密码不可用 |

## C. 同步引擎（会造成数据问题，优先修）

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| C1 🔴 | `FirestoreUserDataPush` → `PushFingerprintTables` | 判断"上传期间有没有被改过"只比较部分字段，笔记只比 `updatedAt` | 上传过程中改了置顶、标签、星级等其他字段，会被当成已同步，**改动永远不会上传** | 比较整行（除同步标记外的全部字段）；最坏情况只是多传一次 | 不用 |
| C2 🔴 | `FirestoreUserDataPull` 的 `applyTagIds`、`mergeNoteComments`（`noteId`）、`mergeSermons`（`seriesId`） | 引用的标签/笔记/系列还没同步到本机时，iOS **直接丢掉这个引用** | 之后本机一编辑，会把**少了标签的版本推回云端**，其他设备的标签也被抹掉 | 保留原始 id；界面只显示能找到的 | 不用 |
| C3 🔴 | `CloudSyncCoordinator.runAutoSync` / `runFullSync` | 拉取失败或被 K2 挡住（正文解不开）时，仍然记录"已跟上其他设备" | 下次按需同步不会再拉，**漏掉别的设备的改动**，直到下次冷启动 | 只有完整拉取成功才更新这个记录 | 不用 |
| C4 🔴 | `LocalTombstonePurge.hardDeleteNote` | 只删除 `note.noteComments` 列表里的评论；但从云端拉下来的评论只设了 `comment.note`，**没有加进这个列表**（只有本机新建的才加，见 `NotesManager` 793/829/844 行） | 笔记被彻底删除后，拉下来的评论成为孤儿留在本地 | 删除笔记时删除所有 `noteId` 等于它的评论 | 不用 |
| C5 🔴 | `FirestoreUserDataPull.mergeUserVerses` | 文档 id 与书卷/章/节不一致时 `precondition` **直接崩溃** | 一条坏数据（例如网页端写错）让 iOS 闪退且每次同步都崩 | 跳过这条 | 不用 |
| C6 🟡 | `FirestoreUserDataPush`（`sermonSeries.sermonIds`） | 顺序来自 Realm `LinkingObjects`，顺序不确定 | 各端顺序可能不同（目前拉取只用来判断成员，影响小） | 按 `preachedOn`、`createdAt`、`id` 排序 | 若 iOS 定义了顺序，安卓改成同样规则 |

## D. 读经与 Today

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| D1 🔴 | `NotesManager.swift` 1875/1920/1966 行调用 `BibleReadingManager.markChapterRead` | 这个函数实际是**切换**（`toggleChapterRead`）；手填新建讲道、YouTube 导入讲道时都对章节调用它 | **今天已经读过的章节会被取消已读** | 只"标记已读"，今天已读的不动（`ReadingRepository.markChapterRead`，阶段 3 S1） | 不用 |
| D2 🔴 | `NotesManager.createSermon(title:refText:preacher:series:)`（`CreateSermonSheet` 调用） | 存好用户选的系列后，又自动识别一次并覆盖 | 用户选择丢失 | 用户选的系列优先；没选时才自动识别（规则见 K3） | 不用 |
| D3 🔴 | `CreateSermonSheet`、`CreateYouTubeVideoSheet` | "讲道已存在 / 视频已保存"提示设好后弹窗立即关闭 | 提示永远看不到 | 打开原来的讲道或视频后，在那一页底部显示提示 | 不用 |
| D4 🔴 | `BibleReadingManager.toggleChapterRead` / `isChapterReadToday` | 用"`readOn` **等于**今天零点"判断今天是否读过 | 换时区（旅行）后，今天的记录认不出来：再点一次会**多建一条记录**而不是取消，颜色也不对 | 按"本地日期范围"判断；**写入仍是本地零点**，与 iOS 兼容 | 不用 |
| D5 🔴 | `BibleReadingManager.toggleChapterRead`（新建进度分支） | 新开一本书时不设 `completedOn`；只有一章的书（俄巴底亚书、腓利门书、约翰二/三书、犹大书）读完了也不算完成 | 这几卷不显示"重新开始"，统计不算读完 | 新建时若已全部读完就设 `completedOn` | 不用 |
| D6 🔴 | `BibleReadingManager.toggleChapterRead` | `readChapters[chapter - 1]` 没有越界检查 | 同步来的数据长度不对时**崩溃** | 越界直接忽略 | 不用 |
| D7 🔴 | `TodayView`（`today = Date()`） | "今天"在页面建立时固定，跨过午夜不更新 | 半夜开着 App，日期和活动都停在昨天 | 午夜自动切换，回到前台也会刷新 | 不用 |
| D8 🟡 | `BookCarouselShortcutCache` | 章节按钮窗口缓存整个 App 会话都不清 | 隔天不重启 App，按钮位置不会跟着最近阅读移动 | 当天保持不动（防止点按时跳位），跨日重置 | 不用 |
| D9 🔴 | `PrayerCreateView` 标签菜单 | 用本地化文字"+ Tag"判断是不是"不选标签" | 换语言或翻译变动时可能把"+ Tag"当成标签名存进去 | 用独立状态表示"不选标签" | 不用 |
| D10 🟡 | `TodayActivityBuilder`（已应允的祷告） | 统计"当天创建且已应允"，不是"当天应允"（`answeredAt`） | 产品语义问题 | **按你的决定保持与 iOS 一致** | 如果 iOS 改成按应允日期，安卓要跟着改 |

## E. 数据与本地化

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| E1 🔴 | `Models/Consts/BibleBooks.swift` 134 行（撒母耳记下缩写） | `["2Sa","2S","2Sam","2Sa","2S","2Sam"]` 重复，缺少带空格的 `"2 Sa"`、`"2 S"`、`"2 Sam"`（撒母耳记上有） | 输入"2 Sam 3:4"可能识别不了 | **照搬 iOS**（书卷数据由 iOS 源文件生成，保持一致） | **要**：iOS 改后重新生成安卓的 `BibleBooks.kt` |
| E2 🔴 | `zh-Hant.lproj/Localizable.strings` | 繁体里混了简体字：Starred = 加星标、Answered Prayers = 已應允祷告、Image = 图片、Quick Note = 快速笔记 | 繁体用户看到简体字 | 已改为 加星標、已應允禱告、圖片（快速筆記暂未用到） | 不用 |
| E3 🟡 | `en.lproj` / 各语言 | 缺少的键：英文 "Study Note"；三种语言都缺 "Review"；另有 "Book"、"Sermon Already Exists"、"Using YouTube link" 等 | 界面直接显示键名 | 安卓已补 Review（回顾/回顧）、Study Note | 不用（iOS 补齐即可） |
| E4 🟡 | `en.lproj`："%d chapters today" 等 | 没有单复数 | 英文显示 "1 chapters today" | 安卓用 plurals："1 chapter today" | 不用 |

## F. 经文引用（Today T2）

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| F1 🔴 | `BibleReferenceHelper.parseFlexible`（笔记正文识别，`detectExplicitBibleVerseReference`） | 只认半角 `:` `-`；引用框（`BibleReferenceValidator.normalize`）却会把全角 `：` `－` `–` `—` 转成半角 | 中文输入法在笔记开头打"约翰福音3：16 感恩"，**识别不到**，存成普通笔记；同样的字打在引用框里却可以 | 识别前同样把这几个全角符号转成半角（一对一替换，感想文字仍取原文，不被改动）；写入格式不变 | 不用 |
| F2 🔴 | `NotesManager.saveSeparatedNoteInput`（`vN` 简写） | 引用框写"Genesis 1"、正文写"v99 hello"时，先把引用改写成"Genesis 1:99"、正文改成"Hello"，再校验发现超出范围就丢弃引用 | 保存的笔记变成"Hello"，**用户打的"v99"丢了** | 只有改写后的引用能保存（到节且在范围内）时才改写；否则正文原样保存 | 不用 |
| F3 🔴 | `NotesManager.findVerseAnchor` | 去重查询要求 `verseEnd` 完全相等；`verseEnd` 为空的锚点（网页端或旧数据，单节时可能不写）永远匹配不上 | 同一天同一节**重复建锚点**，Today"经文"计数多算 | `verseEnd` 为空视为等于 `verseStart` | 不用 |
| F4 🔴 | `NoteInputSheetView`、`PrayerCreateView` 的说明页 | 显示的是**讲道**的说明和示例（"支持单本书和多章节"、"3, 5:1–3"、"6"），而这两个框要求到节、不支持多章、也不带书名示例 | 照示例输入会被判为不完整或无效 | 说明页改为节级示例："John 3:16"、"Jn 3:16-18"、"约 3:16"、"Jude 5"，并说明只写到章不会保存；笔记页另外说明正文开头写经文和 `vN` 的用法 | 不用 |
| F5 🟡 | `ResolvedBibleRef.displayText`（引用框下方的规范写法提示） | 调用 `BibleFormatter` 时不传译本，书名固定用**英文** | 中文界面输入"约 3:16"，下方提示却是"John 3:16" | 用主译本的书名语言（与书卷选择列表一致，和合本为繁体） | 不用 |
| F6 🟡 | `Utils/DataFiles/KJV.json` → `DownloadedVerse.verseContent` | JSON 里 1,302 节正文带首尾空格（1,280 节结尾、22 节开头），原样导入，再原样冻结进 `userVerses.content` | 显示时多一个空格，影响很小 | **照存**（两端同一节内容逐字节一致，已用脚本对比全部 62,202 节）；仅记录 | 若 iOS 改成导入时去空格，安卓同步改生成脚本并升级 `BibleTextDatabase.VERSION`（已冻结的 `userVerses` 不受影响） |

## G. 笔记列表、详情与回顾（N1、N2）

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| G1 🔴 | `NotesManager.updateNoteContent`（"⋯"→编辑） | 仍走已废弃的旧识别流程（`detectBibleVerseReference` / `parseFlexible`）：改后的文字若以"John 3:16 …"开头（或章级笔记写"v3 …"），整条笔记被改成 `.bibleVerse`、`content` 变成"·" | **用户写的文字丢失**；祷告也会被改成经文锚点；也不去重锚点、不建感想 | 编辑只改文字，不改类型和经文字段 | 不用 |
| G2 🔴 | `linkedReflectionNotes` / `parentBibleVerseAnchor` | 与 F3 同：要求 `verseEnd` 完全相等 | `verseEnd` 为空的锚点（网页端/旧数据）的感想挂不上，在列表里单独出现、锚点下显示 0 条 | 空 `verseEnd` 视为等于 `verseStart` | 不用 |
| G3 🟡 | `Note.displayBibleVerseRefPlain` | 只读 `refKey`；网页端只写 ords 不写 `refKey` 的经文锚点，标题显示为空 | 列表/详情的经文标签是空的 | 没有 `refKey` 时按 ords 生成"书卷 章:节" | 不用 |
| G4 🟡 | `NoteCellView` 列表里的"加评论" | 不去首尾空白，空内容也会保存（详情页的加评论有检查） | 可能存出空评论 | 统一去空白、空内容不保存 | 不用 |
| G5 🟡 | `addVerseReflection`（在经文锚点详情里"加评论"） | 走 `saveVerseReference`，按"今天"去重：给**以前某天**的锚点加感想，会在今天新建一个锚点，感想挂在今天的锚点下 | 在旧锚点详情里加的感想不出现在这个详情页（产品语义问题） | **照 iOS**（契约规定锚点按天） | 若 iOS 改为挂在原锚点下，安卓跟着改 |
| G6 🟡 | `TagPickerPopup.commit` | 在"添加新标签"里输入一个已选中的标签名，会把它**取消**（走的是切换） | 少见的误操作 | "添加"只添加不取消 | 不用 |
| G7 🟡 | 各语言 `Localizable.strings` | 这些页面缺键，界面直接显示英文键名："Chapter"、"Delete Note?"、"Answered Today"、"Copy"、"Star"、"Remove Star"、"Sort By"、"Just now"、"%d minutes ago"、"%d hours ago" 等；繁体里还有简体字：祷告（Prayers）、属灵成长、新增注释至、快速笔记、加星标、已應允祷告、图片 | 中文用户看到英文或简体字 | 安卓补齐三种语言并改为繁体字 | 不用 |
| G8 🟡 | `NotesReviewView.commentsSection`（Today 的"回顾"） | 「%d comments」用 `comments.count`，把「Answered」标记评论（正文是配对评论的 id）也算成一条 | 只写了一条评论并标记已应允时显示「2 comments」 | 不算标记，和列表、详情的评论数一致 | 不用 |
| G9 🟡 | `NotesManager.deleteNoteComment` | 只软删这一条：删掉「Answered」标记后祷告仍是「已应允」；删掉配对的那条评论后，标记还留在时间线上 | 删除后状态和时间线对不上（产品决定） | **照 iOS** | 若 iOS 改为联动（删标记即取消已应允，或一起删掉配对评论），安卓跟着改 |
| G10 🟡 | `EditCommentSheet`（评论"编辑"） | 「Save」没有空内容检查，可以把评论改成空白 | 时间线上出现空的评论卡片 | 空白内容不保存（与编辑笔记相同） | 不用 |

## H. 章节页（C1）

方案与验证见 [CHAPTER_PLAN.md](CHAPTER_PLAN.md)。

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| H1 🔴 | `ChapterNoteDetailsView.verseNote` → `getBibleVerseNote(…).first` | 锚点按天去重，同一节在不同日子会有好几个锚点；节详情只取其中一个（一般是最早的），只显示那一天的感想和那个锚点下的评论，列表摘要却数了所有日子的感想。查询还要求 `verseEnd` 完全相等（同 F3） | 列表写"3 条"，点进去只有 1 条；在节详情里新加的感想挂在今天的新锚点上（G5），加完在这一页看不到 | 节详情汇总这一节**所有锚点**（所有日子、译本、讲道/视频）的感想和锚点下的旧评论，按天排列；顶部"⋯"作用于最新的锚点。在这一页写感想，挂到今天、主译本的锚点（与 Today 新建相同）；这一节还没有锚点时也能写感想（iOS 只能写祷告） | iOS 照此修就不用 |
| H2 🔴 | `ChapterNoteView` 整章组的摘要行 | 整章笔记上的评论（`addNoteComment` 会把章节复制到评论上）被算进整章组的"N 条评论"；点进去的节详情按"节"查，整章组没有节 | 点进去显示"暂无评论或祷告" | 整章组不显示摘要行；每张整章笔记卡片显示自己的评论数，点开进笔记详情看评论 | 不用 |
| H3 🟡 | `ChapterNoteViewModel.groupedEntries` 的计数 | 摘要数的和节详情显示的不是同一批：感想笔记上的评论、其他带着节号的评论也算了进去 | 数量对不上 | 摘要只数节详情会显示的内容（感想 + 锚点下的旧评论，不含"已应允"标记） | 不用 |
| H4 🟡 | `ChapterNoteViewModel` 的四种来源 | 只保存了经文、没写感想的锚点（例如在 Today 只填"约翰福音 3:16"）不属于任何一种来源 | 这一节在章节笔记里完全不出现 | **显示**（2026-10-27 决定）：只显示经文，没有摘要行，点开可写感想 | 若 iOS 也改为显示，不用 |
| H5 🟡 | `saveSeparatedNoteInput(anchorChapter:)` | 引用框预填本章；用户改成别的章（只到章）时，正文 `vN` 按**改后的章**算，普通笔记却仍挂在**本章** | 笔记挂错章 | 两种情况都按引用框里的章；引用框为空或无效时挂本章（与 iOS 相同） | 不用 |
| H6 🟡 | `ChapterSermonView.groupedSermons` | 同一年内不排序（Realm 原始顺序）；没有日期的"Unknown"组排在最前（字母比较 `U` > `2`），且"Unknown"没有翻译 | 顺序不稳定 | 年内按讲道日期从新到旧；没有日期的放最后，显示"日期不详" | 不用 |
| H7 🟡 | `ChapterPlainNoteRow` | 整章笔记卡片不能点 | 在章节页里无法编辑、删除整章笔记 | 点开进笔记详情 | 不用 |
| H8 🟡 | `ChapterNoteDetailsView`、锚点的 `NoteDetailActionsMenuButton`、各语言 `Localizable.strings` | 经文锚点下面的内容叫"评论"："Comments (N)"、"No comments or prayers yet."、分段"Comment / Prayer"、"⋯"里的"Add Comment"；zh-Hans 的"Delete Comment?"译成"删除注释？"，zh-Hant 写成"刪除注释？"（混了简体字） | 用词与笔记详情不一致 | 锚点下一律叫"感想"：标题、空状态、分段、"⋯"里的"写感想"（笔记列表和详情的"⋯"也一起改了） | 不用 |
| H9 🟡 | `SermonRow` | 没有简介时也显示展开箭头；"Listen / Watch / Read"没有翻译 | 小问题 | 有简介才显示箭头；三种语言都翻译。阶段 3 S1 起，"听""看"进讲道页在 App 里播放（与 iOS 相同），"读"和不是 YouTube 的视频链接用浏览器打开 | 不用 |

## I. Moments 与 Calendar（M1）

方案与验证见 [MOMENTS_CALENDAR_PLAN.md](MOMENTS_CALENDAR_PLAN.md)。这两个 tab 只读，以下都不影响写入格式。

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| I1 🟡 | `CalendarSummaryView.loadData` | 彩条"笔记"把经文下面的感想（`.note` 带节号）也算进去，但下面的汇总（`TodayActivityBuilder`）不算 | 月历上有笔记彩条，点开那天却没有"笔记"方块 | 彩条直接用当天汇总的结果算，两者一定一致 | 不用 |
| I2 🟡 | `CustomCalendarView` | 未来的日子只是变淡，仍能点选 | 选到未来日期，汇总是空的 | 未来的日子不能点 | 不用 |
| I3 🟡 | `MomentsDateRangeSheet.presets` | "This Week"实际是最近 7 天，"This Year"实际是最近 365 天 | 名字与内容不符 | 范围不变，改名为"最近 7 天 / 最近 3 个月 / 最近 12 个月"（2026-10-27 决定） | 若 iOS 改成真正的本周 / 今年，安卓跟着改 |
| I4 🟡 | `MomentsTabView`（"最近 30 天"）、`CalendarSummaryView` | 范围和"今天"在设置 / 出现时算好，跨过午夜不更新（同 D7） | 半夜开着 App，会少显示当天、月历停在昨天 | 跨日、回到前台时刷新；停在今天的月历跟着到新的一天 | 不用 |
| I5 🟡 | `applyMomentsTypeFilter` 与 `NoteCreationView` 的筛选 | 两处规则略有不同：Moments 的"笔记"包含没有锚点的经文感想，"讲道 / 视频"不检查讲道或视频是否已删除 | 同一个筛选在两个页面结果不同 | 两处用同一套（`NoteFilterRules`，与笔记列表相同） | 不用 |
| I6 🟡 | `MomentsTabView.recomputeRows` | 因为范围内有评论而被列出的旧笔记，按它的**创建日期**分组 | 例如 3 月写、10 月评论的笔记排在最后，日期标题在范围之外 | 放在范围内它最近一次评论的那一天（2026-10-27 决定），与 Today 笔记列表一致 | 若 iOS 也这样改，不用 |
| I7 🟡 | `Views/TabViews/DaySummaryView.swift` | 整个文件没有被使用 | 无用代码 | 不移植 | 不用 |
| I8 🟡 | `TodayActivityView` 用在 Calendar 时 | 卡片副标题固定写"今日"："今日阅读 3 章"、"今日 1 篇讲道"，在 10 月 18 日的汇总里也这么写 | 用词错误 | 不是今天时写"阅读 3 章"、"1 篇讲道"（"未读"按钮与 iOS 相同，只在今天出现） | 不用 |
| I9 🟡 | 各语言 `Localizable.strings` | 缺"No notes yet."（Moments 空状态），中文界面显示英文 | 未翻译 | 三种语言都有 | 不用 |
| I10 🟡 | `CalendarSummaryView.onAppear` | 每次出现都把月份重置为本月，但选中的日期不变 | 回到 Calendar 时月历和下面的汇总可能不是同一个月 | 月份和选中日期一起保留 | 不用 |

## J. 本机互通测试（2026-10-28）

测试记录见 [INTEROP_TEST.md](INTEROP_TEST.md) §5。两条都不影响正式版的数据。

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| J1 🔴 | `Services/FirebaseEmulatorBootstrap.swift`（全量模拟） | 只调用了 `Firestore.firestore().useEmulator`；Firebase 12.11 的 `useEmulator` 只改地址、不关 SSL，而本地模拟器只收明文连接 | iOS 连本地 Firestore 时 TLS 握手失败、一直"离线"，登录后显示"Could not load your encryption key"，无法做本机互通测试 | 安卓不涉及（安卓连本地模拟器正常） | 不用。已在 iOS 工作区加上三行：读出 `settings`、`isSSLEnabled = false`、写回（**未提交**，等你确认） |
| J2 🟡 | Swift 包 `realm-core` 14.14.0 | 用 Xcode 27（iOS 27 SDK）从头编译失败：内带的 s2 几何库特化了 `std::is_pod`，还有 `std::less<void>` 的编译错误 | 现有的编译缓存还能增量编译；一旦 Clean Build Folder、换机器或换编译目录，iOS 就编不过 | 安卓不涉及 | 不用。建议升级 Realm |


## K. 讲道与视频（阶段 3 S1）

方案与验证见 [SERMONS_SERIES_PLAN.md](SERMONS_SERIES_PLAN.md)。K17–K20 属于 Series tab，已在 S2 实现。

K1 补充：笔记推送也省略空字段。目前笔记里会被清空的只有取消"已应允"时的 `answeredAt`；这时 `isAnswered` 明确写成 false，而两端都只在 `isAnswered` 为 true 时才读 `answeredAt`，所以云端留下的旧时间现在看不出影响。安卓（2026-10-28 起）清空时也写"删除该字段"，以免以后按应允日期统计（D10）时读到旧时间。

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| K1 🔴 | `FirestoreUserDataPush`（讲道、视频、系列；笔记也是同样写法） | 合并写入（`merge: true`）时，空的字段直接不写；拉取时又把缺的字段当成空 | 本机清空的值传不到云端：编辑讲道时删掉经文（`book`、`bookOrd`、`chapterRange` 留着旧值），视频索引行清掉的 `sermonId` 也留着。别的设备、换机恢复时拿到的还是旧值 | 讲道、视频、系列的可选字段为空时，以及笔记取消"已应允"后的 `answeredAt`，推送写"删除该字段"（`FieldValue.delete()`），云端文档与 iOS 新写的一样 | 不用 |
| K2 🟡 | `DateFormatter.systemFormatter`（`CalendarDayKey.from`） | 没有固定公历和数字格式 | 设备用佛历、日本年号或阿拉伯数字时，`preachedOn` / `startedOn` 写成"25691028"之类，讲道落到错误的日子，别的设备也解析不了 | 固定公历、半角数字 | 不用 |
| K3 🟡 | `NotesManager.detectSeries` | 同一卷书找不到系列时，不管讲道讲哪卷书，都挂到最新的主题系列；已结束的、教会导入的系列也会被选中（导入的系列只在本机，`seriesId` 指向别的设备没有的系列） | 讲道被挂进无关的系列 | **按你的决定**：只挂"同一卷书、6 个月内开始、还没结束、自己建的"系列，找不到就不挂；编辑页「识别系列」同一规则 | 如果 iOS 保留原规则，两端识别结果会不同（数据格式不受影响） |
| K4 🔴 | `parseSermonInfo`（从 YouTube 标题拆标题、经文、讲员） | 左边以经文开头时按第一个冒号拆，"John 3:16 \| Pastor Kim" 拆成讲员"John 3"、标题"16"；没有竖线时按"-"拆，"Romans 8:1-11" 拆成标题"Romans 8:1"、讲员"11" | 导入后标题、讲员错乱 | 左边整段是经文时保留整段；只按两边有空格的" - "拆 | 不用 |
| K5 🟡 | `CreateYouTubeVideoSheet`「添加为讲道」 | 先把标题里识别出的章节标为已读（同 D1，实际是切换），再用经文框覆盖经文 | 填的章节没有标，标题里的章节却被切换了 | 用最后确定的经文标记已读，只标不切换 | 不用 |
| K6 🔴 | `createSermon(from:)` → `upsertSermonYouTubeVideoIndex` | 只查有没有同一视频的讲道，不查个人视频；链接已经存为视频时，这条视频被改成讲道的隐藏索引 | 视频从今日活动和视频页消失，它的笔记再也回不到视频页 | 提示"这个链接已经存为视频"，打开那条视频，不改视频行 | 不用 |
| K7 🟡 | `deleteSermon`、`mergeSermon`、`deleteYouTubeVideo`、`updateSermon` | 只处理笔记和评论：笔记的置顶记录不删（单独删笔记时会删）；讲道的隐藏视频索引行不处理，`sermonId` 一直指向已删除的讲道，以后被清掉时会变成一条"个人视频"；编辑时换了视频链接，旧索引行也留着 | 多余的置顶记录；凭空出现视频 | 删除讲道或视频时一起删置顶记录和索引行；合并时索引行转给目标讲道；换视频链接时删掉旧索引行 | 不用 |
| K8 🟡 | `youtubeVideoIndexRowRevivingIfNeeded` | 重新添加删过的视频时恢复旧行，`createdAt` 还是第一次添加的日子，标题也不更新 | 今天的活动里看不到刚加的视频 | 恢复时 `createdAt`、`updatedAt` 设为现在，并重新取标题 | 不用 |
| K9 🟡 | `mergeSermon` | 只搬笔记和评论；被合并那篇的视频、音频、PDF、经文、讲员、简介、系列、标签全部丢掉；`preferredMergeTarget` 写了没用 | 合并后可能少了视频或音频 | 目标是自己建的讲道时，目标空着的字段用被合并那篇的补上，标签合在一起；目标是教会导入的不改 | 不用 |
| K10 🟡 | `SermonEditView`、`createSermon` | 没有地方改讲道日期，讲道永远在新建那一天 | 星期一补记星期天的讲道，日期就错了 | **按你的决定**：编辑页加"讲道日期"（不能选今天以后），新建仍默认今天；写入格式不变 | 如果 iOS 不加，iOS 上改不了，但能正常显示安卓改过的日期 |
| K11 🟡 | `updateSermon` 的视频链接 | 只认 `watch?v=` 和 `youtu.be`，不认 `/live/`、`/embed/`、`/shorts/`，与 `YouTubeLinkParser` 不一致；清空视频链接时 `URL(string: "")` 为空，`youtubeID` 不清 | 页面照样放原来的视频，删不掉；有些链接识别不了 | 用同一个链接解析；清空就去掉 `youtubeID` | 不用 |
| K12 🟡 | `CreateSermonSheet` | 贴 YouTube 链接失败（没网、链接打不开）时没有任何提示；创建中按钮不变灰 | 用户不知道发生了什么；连按可能建出两篇 | 显示原因；创建中按钮变灰并转圈 | 不用 |
| K13 🟡 | `SermonEditView`、`SeriesPickerView` | 编辑页的经文框没有校验（新建弹窗有）；不能把讲道移出系列（取消勾选没有作用）；新建弹窗里打开系列选择再按"取消"会清掉已选的系列 | 小问题 | 编辑页用同一个讲道经文框；系列旁有「移出」；"取消"不改原来的选择 | 不用 |
| K14 🟡 | `AudioPlayerManager.load` | 点"听"只加载，不播放 | 要再按一次播放 | 点"听"直接开始播放 | 不用 |
| K15 🟡 | `NoteReaderSermonView` / `NoteReaderListView`（回看页） | 经文锚点显示的是占位符"·"，不是经文；挂在锚点下的感想不显示 | 回看时看不到经文和感想 | 显示经文正文，感想和评论按时间列在下面 | 不用 |
| K16 🟡 | 各语言 `Localizable.strings`、`SeriesTabView` | "Ungrouped Sermons"、"Reader"、"Listen / Watch / Read"、"Using YouTube link"、"Sermon Already Exists" 及说明没有中文；新建系列的书卷菜单固定显示英文书名 | 界面中英混杂 | 三种语言都翻译；书名跟随界面语言 | 不用 |
| K17 🟡 | `SermonSeries.isVisibleInTopicalSeriesSection` | 自己建、带书卷的系列在「主题讲道」和「按书卷」两段都出现（代码注释说是有意的，但名字对不上） | 同一个系列出现两次 | **按你的决定（2026-10-28）**：带书卷的系列只在「按书卷」出现，「主题讲道」只放没有书卷的系列 | 如果 iOS 保留原样，两端分段会不同（数据格式不受影响） |
| K18 🟡 | `SeriesListLogic.sort` | 排序菜单只管「主题讲道」一段；"按书卷顺序""按书名"取系列里"第一篇"讲道，而这个顺序不确定 | 排序结果每次可能不同 | 系列自己的书卷，否则最早一篇讲道的书卷 | 不用 |
| K19 🟡 | `UngroupedSermonsRowView`、`SeriesRowView` | 未分组的讲道没有删除入口；系列列表里的 PDF 图标点不了 | 没有系列的讲道删不掉 | 编辑讲道页底部有「删除讲道」；Series tab 里所有讲道都能长按删除，PDF 图标可以点 | 不用 |
| K20 🟡 | `SeriesTabView` | 一个系列都没有时也写"没有匹配的系列" | 用词错误 | 没有系列时写"还没有系列"，搜索没有结果才写"没有匹配的系列" | 不用 |
| K21 🔴 | `SermonReferenceParser.parse` | 只把第一个词当书名：`"1 John 3"` 的书名是 `"1"`，`"Song of Solomon 2"` 是 `"Song"`，`"约翰一书3"` 整个是一个词，都认不出；而经文框认为它们有效，书卷选择器自己填进去的就是 `"1 John "` | 这些讲道没有 `bookOrd` 和 `chapters`：章节页看不到、不标已读、不能自动识别系列 | 先照 iOS 试第一个词，不行再试前面几个词连起来、再试数字前面的部分；iOS 能解析的结果完全不变 | 不用 |
| K22 🔴 | `BibleBookHelper.extractValidChapters` | 写反的章范围（`"Genesis 5-3"`）走到 `for c in start...end`，Swift 直接崩溃 | 新建讲道时闪退 | 反过来的范围当作 3–5 章 | 不用 |

---

## 安卓有意不同、但不算 iOS bug 的地方

这些写在 [DATA_LAYER.md](DATA_LAYER.md) "Deliberate differences" 里，不影响互通：
- 本地数据库是 Room（不是 Realm），时间按微秒存储，关联按 id 存储。
- 拉取强制读服务器（`Source.SERVER`），因为安卓的 Firestore 只用内存缓存。
- 内置经文：iOS 首启按系统语言只导入一个译本（解析 `KJV.json` 或 `CUV.xml` 进 Realm）；安卓两个译本都在**构建时**生成只读数据库随 App 安装，首启不解析。所选译本的默认值与 iOS 首启后的结果相同（中文系统 和合本，其他 KJV）。
- NIV / NKJV（API.Bible）暂未接入，只留接口；选了这两个译本时与 iOS 离线时一样：锚点照存，不建 `userVerses`。
