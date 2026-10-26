# 移植中发现的 iOS 问题清单

移植安卓时逐行对照 iOS 代码发现的问题。每条都写明：**iOS 位置、问题、影响、安卓现在怎么做、iOS 修了之后安卓要不要跟着改**。

原则：安卓已直接修正的，**写入 Firestore 的数据格式与 iOS 完全相同**，所以两端现在就能互通；修 iOS 时以本表的"安卓做法"为准，两端行为就会一致。

状态：🔴 待修 · 🟡 待确认 / 产品决定 · ✅ 已处理

最后更新：2026-10-26

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
| D1 🔴 | `NotesManager.swift` 1875/1920/1966 行调用 `BibleReadingManager.markChapterRead` | 这个函数实际是**切换**（`toggleChapterRead`）；新建讲道时对章节调用它 | **今天已经读过的章节会被取消已读** | 新建讲道（T3，尚未实现）将只"标记已读"，不切换 | 不用 |
| D2 🔴 | `CreateSermonSheet` | 自动识别的系列覆盖用户手动选的系列 | 用户选择丢失 | T3 实现时用户选择优先 | 不用 |
| D3 🔴 | `CreateSermonSheet` | 讲道重复提示设好后弹窗立即关闭 | 提示永远看不到 | T3 实现时正常显示 | 不用 |
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

---

## 安卓有意不同、但不算 iOS bug 的地方

这些写在 [DATA_LAYER.md](DATA_LAYER.md) "Deliberate differences" 里，不影响互通：
- 本地数据库是 Room（不是 Realm），时间按微秒存储，关联按 id 存储。
- 拉取强制读服务器（`Source.SERVER`），因为安卓的 Firestore 只用内存缓存。
- 内置经文：iOS 首启按系统语言只导入一个译本（解析 `KJV.json` 或 `CUV.xml` 进 Realm）；安卓两个译本都在**构建时**生成只读数据库随 App 安装，首启不解析。所选译本的默认值与 iOS 首启后的结果相同（中文系统 和合本，其他 KJV）。
- NIV / NKJV（API.Bible）暂未接入，只留接口；选了这两个译本时与 iOS 离线时一样：锚点照存，不建 `userVerses`。
