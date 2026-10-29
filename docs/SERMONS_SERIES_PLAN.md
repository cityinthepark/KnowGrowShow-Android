# 讲道、视频与 Series tab（阶段 3）— 安卓方案

**已确认（2026-10-28）**：§4 四个问题都照推荐。**状态：S1 已完成（未提交）**，实现与验证见 §6、§7；S2（Series tab）还没开始。

依据（iOS 代码为准）：
- 讲道和视频页：`SermonNoteView`、`YouTubeVideoNoteView`、共用框架 `ContainerNoteListView`、回看页 `NoteReaderSermonView` / `NoteReaderListView`、编辑页 `SermonEditView`。
- 新建：`CreateSermonSheet`、`CreateYouTubeVideoSheet`、`SeriesPickerView`。
- 播放：`AudioPlayerManager`（含悬浮播放条 `FloatingAudioPlayer`）、`YouTubeInlinePlayerView`、`YouTubeTranscriptService`、`ScriptCaptureDurationPreference`。
- 数据：`NotesManager` 的 `createSermon` ×2、`updateSermon`、`deleteSermon`、`mergeSermon`、`detectSeries`、`addSermonToSeries`、`createSeries`、`softDeleteSermonSeries`、`addNote(_:to: sermon/video)`、`saveSeparatedNoteInput`；`NotesManager-Sermons`（`createSermon(from:)`、`parseSermonInfo`）、`NotesManager-YouTubeVideos`；`SermonReferenceParser`、`BibleBookHelper.extractValidChapters`、`YouTubeLinkParser`、`YouTubeVideoTitleParser`、`YouTubeMetadataService`、`CalendarDayKey`。
- Series tab：`SeriesTabView`（`SeriesViewModel`、`SeriesListLogic`、`SeriesRowView`、`UngroupedSermonsRowView`）、`SeriesDetailView`。
- 入口：`TodayView`、`TodayActivityView`（`sermonListItems`）、`DaySummaryView.swift` 里的 `SummaryListRowView` 和合并弹窗 `SermonMergePickerOverlay`、`ChapterSermonView`、`MainTabView`（分享进来的 YouTube 链接）。
- 同步：`FirestoreUserDataPush` / `FirestoreUserDataPull` 的讲道、视频、系列部分。

## 1. iOS 现在怎么做

### 1.1 三种数据和写入规则

安卓的数据层（`SermonEntity`、`YouTubeVideoEntity`、`SermonSeriesEntity`）和推送 / 拉取格式在阶段 1 已经和 iOS 一致，这一阶段只是开始在安卓上**新建和修改**它们。写入时要照下面的规则：

| 集合 | 什么时候写 | 字段和日期的含义 |
| --- | --- | --- |
| `sermons` | 新建（手填或 YouTube）、编辑、合并（被合并的那篇删除）、删除 | `preachedOn` 是讲道日，本地日期 `yyyyMMdd`，新建时固定为今天；`createdAt` 是新建那天的**本地零点**，不是此刻；`chapters` 是章号数组，`chapterRange` 是显示文字（例如"约翰福音 3, 5:1-3"），另有 `book`、`bookOrd`；YouTube 导入时写 `youtubeID`，`videoUrl` 写成 `https://www.youtube.com/watch?v=…`；`audioUrl`、`pdfUrl`、`intro`、`preacher`、`seriesId`、`tagIds`；`isUserData` 永远是 true（教会导入的讲道只在本机，不上传） |
| `youtubeVideos` | 存为视频时；讲道带 YouTube 时也写一行 | 文档 id 就是视频 id。有两种行：**个人视频**（`sermonId` 为空，显示在视频界面）；**讲道的隐藏索引**（`sermonId` 是讲道 id，只用来查重，界面不显示）。`createdAt`、`updatedAt` 是此刻；`title`、`channelName`、`channelId`、`thumbnailUrl`、`bookOrd`、`chapterOrd`。隐藏索引行里，`channelName` 写讲员，`chapterOrd` 写第一章，封面固定为 `img.youtube.com/vi/{id}/hqdefault.jpg` |
| `sermonSeries` | 新建系列、改名、进行中 / 已完成、删除；讲道进出系列时也要写（重算 `sermonIds`） | `startedOn` 是新建那天的 `yyyyMMdd`；`bookOrd` 可以为空；`isFinished`；`sermonIds` 由各讲道的 `seriesId` 推出 |
| `notes` / `noteComments` | 在讲道页、视频页写笔记时 | 笔记带 `sermonId` 或 `youtubeID`，两者只能有一个；评论照抄所属笔记的 `sermonId` / `youtubeID`；经文锚点按"哪篇讲道 / 哪个视频"分开去重。安卓的保存函数（T2、N1）已经支持这些 |

### 1.2 新建讲道（Today 的「讲道」按钮）
- **两种填法，二选一**：
  - **手填**：标题、经文、讲员、系列（可选）。经文框带书卷选择，用讲道格式，可以写多章，例如"3, 5:1-3"。标题空着时用当天日期作标题（"2026年10月28日"，重名时加" - 2"）。标题和经文至少要填一个。
  - **贴 YouTube 链接**：一贴链接，手填的几项就变灰不用；Shorts 不能存为讲道。用免密钥的 oEmbed 取视频标题和频道名，再从标题里拆出标题、经文、讲员（例如"标题: 经文 | 讲员"）；拆不出讲员就用频道名。
- **保存后**：
  - 把讲道的章节标为已读，实际调用的是"切换"（D1）；
  - 自动识别系列，结果会覆盖用户手选的系列（D2）；
  - 打开讲道页。
- **同一个 YouTube 视频已经有讲道**：打开原来的讲道，并想弹"已存在"提示，但弹窗随即关闭，提示看不到（D3）。贴链接失败（没网、链接打不开）时什么提示都没有（K12）。

### 1.3 新建视频（Today 的「视频」按钮；从 YouTube App 分享过来也用这个弹窗）
- 贴链接，上方分段选「YouTube 视频 | 添加为讲道」（Shorts 只能存为视频）。下面一个经文框，写到章即可，也可以不填。
- **存为视频**：用 **YouTube Data API**（要 API 密钥）取标题、频道、封面。经文框空着就从标题识别书卷和章（只有书名时当第 1 章），填了就用填的。
  - 已经存过：打开原来的视频；如果这个链接已经是某篇讲道，就打开那篇讲道。"已保存"提示同样看不到（D3）。
  - 删过的视频会被恢复（K8）。
- **添加为讲道**：和 1.2 的 YouTube 方式相同，最后用经文框里的经文覆盖。
- **从 YouTube App 分享**：链接和视频标题预填进弹窗，经文框按标题预填。

### 1.4 讲道页和视频页
两个页面用同一个框架（`ContainerNoteListView`），从上到下：
- **内嵌播放器**（有 YouTube 时）：点封面开始播放。右上角四个按钮：
  - CC：字幕开关；
  - 截图：存成图片笔记；
  - 「字幕」：把当前时间前后几秒的字幕存成一条笔记，时长 1–30 秒可调，默认 10 秒；
  - 在 YouTube 打开。

  手机横过来时视频全屏。
- **信息栏**：
  - 讲道页：经文标签、所选译本（如"[KJV] [CUV]"）、耳机按钮（音频）、PDF 按钮；
  - 视频页：频道名、经文标签、标签。
- **笔记列表**：这篇讲道或这个视频的笔记，最早的在上面；感想收在锚点下，不单独成行。每条都可以直接写评论，或用"⋯"操作。
- **底部**：
  - 拍照、相册：存成图片笔记，并识别图里的文字另存一条；
  - "＋"新建笔记：引用框预填讲道的第一章；从章节页进来的，预填那一章。保存规则与章节页相同，只是多挂上讲道或视频；正文开头写 `vN` 也按这一章算。
- **右上角**：讲道页有「编辑」（只限自己建的讲道）；视频页有「删除」（连笔记一起删）。
- **回看页**：在 Today 里，过去日子的讲道有笔记时，点「笔记」进入。只读：标题、听 / 看 / 读按钮、经文，再按时间列出笔记和评论；右上角「编辑笔记」进讲道页。

### 1.5 编辑讲道（`SermonEditView`）
- **可以改的**：
  - 标题、经文（普通文本框，没有校验）、讲员；
  - 系列：已有系列时显示「更换」，没有时显示「识别系列」「添加到系列」；
  - 标签；
  - 视频、音频、PDF 链接，各带一个复制按钮；
  - 简介。
- **什么时候保存**：系列和标签一选就保存，其余的按「保存」才写。
- **没有的**：
  - 讲道日期不能改（K10）；
  - 没有删除按钮，删讲道只能在 Series tab 的系列里长按。

### 1.6 播放
- **音频**：全 App 共用一个悬浮播放条，在底部 tab 上方，进入别的页面也在。
  - 播放条上有标题、经文、讲员、进度条、时间、「字幕」（用系统语音识别把当前一段音频转成文字，存为讲道笔记）、播放 / 暂停、关闭。
  - 切到后台继续播放，锁屏和控制中心可以控制。
  - 各处的耳机按钮只**加载、不播放**，要再按一次播放（K14）。
- **YouTube**：讲道页、视频页里的内嵌播放器。各处的"看"都是打开讲道页并自动播放。
- **PDF**：交给系统浏览器打开，App 里不显示 PDF。

### 1.7 合并讲道
- **入口**：只在 Today 和日历汇总的讲道卡片里。同一天有两篇以上讲道、这一篇有笔记时，这一行多一个「合并」。
- **弹窗**：标题"合并讲道？"，说明"将 N 条笔记从「…」合并到："，下面列出同一天的其他讲道，只有**没有笔记**的才能选。
- **合并做什么**：把这篇的笔记和评论改挂到目标讲道，然后删除这篇。这篇的视频、音频、经文等信息都不带过去（K9）。

### 1.8 系列：自动识别和手选
- **自动识别**（新建讲道时，以及编辑页的「识别系列」）：
  - 先找同一卷书、6 个月内开始的系列，取最新的一个；
  - 找不到时**不管书卷**，挂到最新的主题系列（没有书卷的系列）；
  - 已结束的系列、教会导入的系列也会被选中（K3）。
- **手选**（系列选择弹窗）：
  - 顶部「＋ 新建系列」，只有标题，没有书卷；
  - 下面是排序、搜索、全部系列（单选，再点一次取消）。

### 1.9 Series tab
- **顶部**：
  - 标题「讲道系列」；
  - 排序菜单：按书卷顺序、按书名、按开始日期、按系列名称；
  - "＋"新建系列（标题，加上可选的书卷）；
  - 搜索框：搜系列名、书卷名，以及系列里讲道的标题和经文。
- **列表分三段**：
  - 「未分组的讲道」：一张可展开的卡片，放没有系列的讲道；
  - 「主题讲道」：自己建的系列，加上没有书卷的导入系列。自己建、带书卷的系列也在这里（K17）。排序菜单只管这一段；
  - 「按书卷」：带书卷的系列，按书卷分组；右侧可以切换成"按最近讲道"。
- **每个系列一张卡片**：
  - 标题、讲道数、编辑（或详情）按钮、展开箭头；
  - 展开后按讲道日期从早到晚列出讲道：经文、标题、日期、听 / 看 / PDF 图标；
  - 点讲道进讲道页；长按可以删除（只限自己建的）。
- **系列详情**：标题（可改）、开始日期、"进行中"开关、讲道数；没有讲道时可以删除系列。导入的系列只能看，不能改。
- **教会导入**：在设置的"网上资源"里选一个教会，导入它公开的讲道目录。导入的讲道和系列只在本机，不上传。

### 1.10 入口
- **Today**：
  - 「讲道」「视频」两个快捷按钮；
  - 今日活动的讲道卡片，每行显示"经文 - 标题"、笔记数、标签，再加几个按钮：「合并」；「打开」（今天）或「笔记」（过去的日子）；听、看、读；
  - 视频卡片：只有一个视频时，点整张卡片打开它；多个时展开成列表。
- **日历汇总**：直接复用今日活动，和上面一样。
- **章节页讲道 tab**：点标题进讲道页（预填这一章）；"⋯"里的「听」「看」进讲道页并开始播放，「读」用浏览器打开。

安卓现在这些入口都是"即将推出"：Today 的两个快捷按钮、今日活动的讲道 / 视频行（日历汇总也一样）、章节页的讲道标题，以及 `MainScreen` 里的 Series tab。章节页"⋯"里的看 / 听 / 读暂时交给系统打开（`CHAPTER_PLAN.md` §4 问题 3）。

## 2. 安卓做法

- **分两步做**（§4 问题 1）：
  - **S1 讲道与视频**：新建、编辑、合并、删除、讲道页、视频页、回看页、播放、所有入口，以及系列选择弹窗（新建和编辑讲道要用）；
  - **S2 Series tab**：列表、排序、搜索、新建带书卷的系列、系列详情、删除讲道。
- **结构和信息照 iOS，组件用 Material**：
  - 新建讲道、新建视频、系列选择用底部弹窗；合并用对话框；
  - 经文框沿用 T2 的 `BibleReferenceField`，新建讲道和编辑讲道都用讲道格式（可以写多章）；
  - 颜色用 `AppColors` 里已有的 `sermonChip` / `videoChip`。
- **讲道页和视频页**：
  - 笔记列表用 Today 笔记列表的卡片；点开进笔记详情，在那里写评论、做操作。iOS 在列表里直接展开评论框；安卓的评论一直都在详情页，与其他列表一致；
  - "＋"沿用章节页的新建笔记弹窗，保存时多挂上讲道或视频。
- **播放**：
  - **YouTube**：用 YouTube 官方的网页播放器（IFrame），iOS 也是这样做的。保留 CC、在 YouTube 打开、横屏全屏。「字幕」摘录照 iOS：用 YouTube 网页自己用的公开字幕接口，免费、不需要你的密钥；它不是官方开放接口，YouTube 改版时可能失效（iOS 也一样）。时长设置放在标题栏的齿轮里，默认 10 秒。
  - **音频**：用安卓官方的 Media3 播放器，切到后台继续播放，通知栏和锁屏可以控制。悬浮播放条放在底部 tab 上方，各页面都在。点"听"直接开始播放（K14）。（实现时确认：媒体播放的通知不需要通知权限，所以不会弹出权限请求。）
  - **PDF**：用浏览器打开，与 iOS 相同。
- **这一阶段不做**：
  - 截图、拍照、相册：都是图片笔记，等升级 Blaze 后和图片功能一起做；
  - 音频的「字幕」：安卓的系统语音识别不能直接转写一段音频文件，要另找办法，以后再做；
  - 教会导入：属于设置里的"网上资源"，放到阶段 4 或以后。拉下来或以后导入的教会讲道照常显示，但不能编辑。
- **联网**：
  - 手填讲道不联网也能用；
  - 贴链接、播放要联网，失败时说明原因；
  - YouTube 相关功能统一挂在一个开关后面，以后中国版用 Gradle 变体关掉。
- **重复提醒**（D3）：打开已有的讲道或视频后，在那一页底部显示提示（"这个 YouTube 讲道已经添加过，已打开原来的讲道"）。
- **只写已有字段**：写入的字段、类型、日期含义都照 §1.1，与 iOS 相同。唯一的不同：清空一个字段时写"删除该字段"，而不是不写（K1）；云端文档的样子与 iOS 新写的一样。
- **自动刷新**：页面数据来自 Room，有变化时自动更新。

## 3. 发现的 iOS 问题（安卓直接修正，会写进 `IOS_ISSUES.md` 新的 K 节）

**已经记过的 D1–D3**，这次对照代码后补充：
- D1：除了手填新建，YouTube 导入新建也会切换（`createSermon(youtubeID:…)`），见 K5。
- D2：覆盖发生在 `NotesManager.createSermon(title:…)` 里，不在 `CreateSermonSheet`。
- D3：`CreateYouTubeVideoSheet` 的"视频已保存"也一样。

**H9**（章节页讲道卡片）：S1 完成后，「听」「看」改成在 App 里播放，「读」仍用浏览器。

### S1 相关

实现时又发现两条，见 `IOS_ISSUES.md` K21（讲道经文只认第一个词作书名，"1 John 3" 解析不了）、K22（写反的章范围让 iOS 崩溃）。

| | iOS 位置 | 问题 | 影响 | 安卓做法 |
| --- | --- | --- | --- | --- |
| K1 🔴 | `FirestoreUserDataPush`（讲道、视频、系列；笔记也是同样写法） | 合并写入（`merge: true`）时，空的字段直接不写；拉取时又把缺的字段当成空 | 本机清空的值传不到云端，例如编辑讲道时删掉经文（`book`、`bookOrd`、`chapterRange` 留着旧值），或视频索引行清掉的 `sermonId`。别的设备、换机恢复时拿到的还是旧值 | 这一阶段会被清空的字段，推送时写"删除该字段"。笔记的 `answeredAt` 等可能也有这个问题，另开一项检查 |
| K2 🟡 | `DateFormatter.systemFormatter`（`CalendarDayKey.from`） | 没有固定公历和数字格式 | 设备用佛历、日本年号或阿拉伯数字时，`preachedOn` / `startedOn` 会写成"25691028"之类，讲道落到错误的日子，别的设备也解析不了 | 固定公历、半角数字 |
| K3 🟡 | `NotesManager.detectSeries` | 同一卷书找不到系列时，不管讲道讲哪卷书，都挂到最新的主题系列；已结束的系列、教会导入的系列也会被选中（导入的系列只在本机，`seriesId` 指向别的设备没有的系列） | 讲道被挂进无关的系列 | 见 §4 问题 3 |
| K4 🔴 | `parseSermonInfo`（从 YouTube 标题拆标题、经文、讲员） | 左边以经文开头时按第一个冒号拆，"John 3:16 \| Pastor Kim" 会拆成讲员"John 3"、标题"16"；没有竖线时按"-"拆，"Romans 8:1-11"会拆成标题"Romans 8:1"、讲员"11" | 导入后标题、讲员错乱 | 先判断左边整段是不是经文；只按两边有空格的" - "拆 |
| K5 🟡 | `CreateYouTubeVideoSheet`「添加为讲道」 | 先把标题里识别出的章节标为已读（同 D1，实际是切换），再用经文框覆盖经文 | 填的章节没有标，标题里的章节却被切换了 | 用最后确定的经文标记已读，只标不切换 |
| K6 🔴 | `createSermon(from:)` | 只查有没有同一视频的讲道，不查个人视频。链接已经存为视频时，这条视频会被改成讲道的隐藏索引 | 视频从今日活动和视频界面消失，它的笔记再也回不到视频页 | 提示"这个链接已经存为视频"，打开那条视频（与反方向的查重一致） |
| K7 🟡 | `deleteSermon`、`mergeSermon`、`deleteYouTubeVideo`、`updateSermon` | 只处理笔记和评论：笔记的置顶记录不删（单独删笔记时会删）；讲道的隐藏视频索引行不处理，`sermonId` 一直指向已删除的讲道，以后被清掉时会变成一条"个人视频"冒出来；编辑时换了视频链接，旧的索引行也留着 | 多余的置顶记录；凭空出现视频 | 删除讲道或视频时，一起删置顶记录和索引行；合并时索引行转给目标讲道；换视频链接时清掉旧索引行 |
| K8 🟡 | `youtubeVideoIndexRowRevivingIfNeeded` | 重新添加删过的视频时恢复旧行，`createdAt` 还是第一次添加的日子，标题也不更新 | 今天的活动里看不到刚加的视频 | 恢复时 `createdAt`、`updatedAt` 设为现在，并重新取标题（iOS 恢复经文锚点时就是这么做的） |
| K9 🟡 | `mergeSermon` | 只搬笔记和评论；被合并那篇的视频、音频、PDF、经文、讲员、简介、系列、标签全部丢掉；写好的 `preferredMergeTarget` 没有用到 | 合并后可能少了视频或音频 | 目标是自己建的讲道时，目标空着的字段用被合并那篇的补上，标签合在一起；目标是教会导入的不改 |
| K10 🟡 | `SermonEditView`、`createSermon` | 没有地方改讲道日期，讲道永远在新建那一天 | 星期一补记星期天的讲道，日期就错了 | 见 §4 问题 4 |
| K11 🟡 | `updateSermon` 的视频链接 | 只认 `watch?v=` 和 `youtu.be`，不认 `/live/`、`/embed/`、`/shorts/`，与 `YouTubeLinkParser` 不一致；清空视频链接时 `youtubeID` 不清（空字符串变不成网址），页面照样放原来的视频 | 视频删不掉；有些链接识别不了 | 用同一个链接解析；清空就去掉 `youtubeID` |
| K12 🟡 | `CreateSermonSheet` | 贴 YouTube 链接失败（没网、链接打不开）时没有任何提示；创建中按钮不变灰，可以连按 | 用户不知道发生了什么；可能建出两篇 | 显示错误；创建中按钮变灰 |
| K13 🟡 | `SermonEditView`、`SeriesPickerView` | 编辑页的经文框没有校验（新建弹窗有）；编辑页不能把讲道移出系列（取消勾选没有作用）；新建弹窗里打开系列选择再按"取消"，会清掉已经选好的系列 | 小问题 | 编辑页用新建弹窗同一个经文框；可以移出系列；"取消"不改原来的选择 |
| K14 🟡 | `AudioPlayerManager.load` | 点"听"只加载，不播放 | 要再按一次播放 | 点"听"直接开始播放 |
| K15 🟡 | `NoteReaderSermonView` / `NoteReaderListView`（回看页） | 经文锚点显示的是占位符"·"，不是经文；挂在锚点下的感想不显示 | 回看时看不到经文和感想 | 显示经文正文和感想 |
| K16 🟡 | 各语言 `Localizable.strings`、`SeriesTabView` | "Ungrouped Sermons"、"Reader"、"Listen / Watch / Read"、"Using YouTube link"、"Sermon Already Exists" 及说明没有中文；新建系列的书卷菜单固定显示英文书名 | 界面中英混杂 | 三种语言都翻译；书名跟随界面语言 |

### S2 相关（做 S2 时再核对一次）

| | iOS 位置 | 问题 | 影响 | 安卓做法 |
| --- | --- | --- | --- | --- |
| K17 🟡 | `SermonSeries.isVisibleInTopicalSeriesSection` | 自己建、带书卷的系列在「主题讲道」和「按书卷」两段都出现。代码注释说这是有意的，但"主题讲道"这个名字和它对不上 | 同一个系列出现两次 | S2 开始前再问你 |
| K18 🟡 | `SeriesListLogic.sort` | 排序菜单只管「主题讲道」一段；"按书卷顺序""按书名"取的是系列里"第一篇"讲道，而这个顺序不确定 | 排序结果每次可能不同 | 用系列里最早一篇讲道的书卷，结果稳定 |
| K19 🟡 | `UngroupedSermonsRowView`、`SeriesRowView` | 未分组的讲道没有删除入口；系列列表里的 PDF 图标点不了 | 没有系列的讲道删不掉 | 都能长按删除；PDF 图标可以点开 |
| K20 🟡 | `SeriesTabView` | 一个系列都没有时也写"没有匹配的系列" | 用词错误 | 没有系列时写"还没有系列"，搜索没有结果时才写"没有匹配的系列" |

## 4. 需要你确认

1. **分两步做，以及这一阶段做哪些**：
   - **推荐**：先做 S1 讲道与视频（包括 YouTube 的「字幕」摘录），再做 S2 Series tab。截图、拍照 / 相册、音频「字幕」以后和图片功能一起做。
   - 或者：S1 和 S2 一次做完。
   - 或者：「字幕」也放到以后，S1 会快一些。
2. **新建视频时从哪里取标题、频道和封面**（iOS 用 YouTube Data API，要 API 密钥）：
   - **推荐**：用免密钥的 oEmbed（新建讲道已经在用），免费，标题、频道名、封面都有，只缺频道 id（`channelId`）。iOS 界面不用频道 id，安卓建的视频就不写这个字段。
   - 或者：你在 Google Cloud 申请一个 YouTube Data API 密钥（有免费额度），安卓照 iOS 用它。
3. **系列自动识别**（K3、D2）：
   - **推荐**：用户手选的系列优先；没选时，只挂到"同一卷书、6 个月内开始、还没结束、自己建的"系列；找不到就不挂，不再退回到最新的主题系列。编辑页的「识别系列」用同一个规则。
   - 或者：照 iOS，同一卷书找不到时挂到最新的主题系列。
4. **讲道日期能不能改**（K10）：
   - **推荐**：编辑页加一个"讲道日期"，用 Material 的日期选择器，不能选今天以后的日子。新建时仍默认今天。改了日期，讲道会移到日历上那一天。写入格式不变（`preachedOn` 仍是 `yyyyMMdd`）。
   - 或者：照 iOS，讲道日期固定为新建那天。

## 5. 步骤与验证

### S1 讲道与视频
1. **数据**（只做计算的部分都写成纯函数，方便单元测试）：
   - **照 iOS 移植的解析**：`SermonReferenceParser`、`extractValidChapters`、`YouTubeLinkParser`、oEmbed 标题拆分（含 K4 的修正）、`YouTubeVideoTitleParser`、默认标题（日期加" - 2"）。
   - **`SermonRepository`**：
     - 手填新建、YouTube 新建（查重：已有讲道、已存为视频的链接 K6）；
     - 编辑（视频链接 K11、讲道日期）；
     - 删除（K7）、合并（K9）；
     - 系列：自动识别（§4 问题 3）、加入或移出系列、新建主题系列；
     - 隐藏索引行；标签。
   - **`VideoRepository`**：新建（oEmbed 或 API，看 §4 问题 2）、恢复删过的视频（K8）、手填经文、删除、标签。
   - **笔记保存**：`saveNoteFromContent` 加上讲道或视频的关联；Script 文字照 iOS 的 `addNote(_:to:)` 保存。
   - **已读**：只标记，不切换（D1、K5）。
   - **推送**：清空字段时写"删除该字段"（K1）。
2. **界面**：
   - 讲道页、视频页（播放器、信息栏、笔记列表、"＋"）、回看页（K15）、编辑讲道页；
   - 新建讲道弹窗、新建视频弹窗、系列选择弹窗、合并对话框。
3. **播放**：
   - YouTube 内嵌播放器：CC、在 YouTube 打开、横屏全屏、「字幕」摘录和时长设置；
   - 音频：Media3 后台播放、通知栏和锁屏控制、悬浮播放条；
   - PDF 用浏览器打开。
4. **入口**：
   - Today：两个快捷按钮；讲道 / 视频行的打开、笔记、合并、听、看、读（日历汇总自动跟着有）；
   - 章节页：讲道标题，以及"⋯"里的看、听；
   - 从 YouTube App 分享链接进 App。
5. **单元测试（Robolectric）**：
   - 解析：讲道经文（中英文书名、只有一章的书、"3, 5:1-3"、`;`、`–`）；各种 YouTube 链接和分享文字；YouTube 标题拆分（包括 K4 的两个例子）。
   - 新建讲道：写入的字段与 iOS 相同（`createdAt` 是本地零点、`preachedOn`、没写经文时的 `chapterRange`）；已读只标不切换；手选系列优先；自动识别规则。
   - 查重：已有讲道、已存为视频（K6）、恢复删过的视频（K8）；隐藏索引行的字段；隐藏索引行不出现在视频界面。
   - 合并：笔记和评论改挂、被合并的那篇删除、系列标脏、目标补齐（K9）、索引行转移；只有没有笔记的讲道能当目标。
   - 删除：笔记、评论、置顶记录、索引行一起处理（K7）。
   - 讲道页写笔记：挂上 `sermonId`；`vN` 按预填的章；锚点按讲道分开去重。
   - 推送：清空的字段写成删除（K1）。
   - Today 讲道行：只有"同一天两篇以上、这篇有笔记"时才有「合并」；过去的日子有笔记时进回看页。
   - 中文界面（`adb` 打不了中文，靠单元测试覆盖）。
6. **变异检查**：把 D1、D2、K4、K6、K9、"`createdAt` 取零点"、"隐藏索引行不显示"逐一改回 iOS 的做法或改错，对应测试都要失败。
7. **模拟器验证**（安卓模拟器 + Firebase 本地模拟器，iOS 格式的数据）：
   - 本机模式：手填新建讲道（带系列、经文），检查已读；在讲道页写笔记、`vN`、感想；编辑；合并；删除。
   - 用 REST 写入 iOS 格式的讲道（带 YouTube、音频、PDF）、视频、隐藏索引行，拉到安卓后检查显示和查重；在安卓新建的文档，字段与 iOS 格式逐项对照。
   - 播放：YouTube 内嵌播放和全屏（要联网）；音频后台播放和通知栏控制（用 Mac 上的本地测试音频文件，经 10.0.2.2 访问）。
   - 从 YouTube 分享（用 `adb` 发分享 intent）、中文界面、深色模式。
   - 只用安卓，不连 iOS。
8. **文档**：`IOS_ISSUES.md` 加 K 节，并更新 D1–D3、H9；`DATA_LAYER.md` 的 "Deliberate differences" 加 K1、K6–K9；本文件补"实现"和"验证"两节。

### S2 Series tab
1. **数据**：分段规则（K17 按你的决定）、排序（K18）、搜索、按书卷分组、"按最近讲道"、未分组的讲道；新建系列（可选书卷）、系列详情（改名、进行中 / 已完成、删除空系列）、删除讲道。
2. **界面**：Series tab 换掉"即将推出"；系列卡片展开后的讲道行（听、看、PDF，长按删除，K19）；系列详情页；空状态（K20）。
3. **单元测试和变异检查**：分段、排序、搜索、分组、开始日期为空时的排序、只有自己建的系列能改。
4. **模拟器验证**：写入 iOS 格式的系列（主题、带书卷、已结束、导入的）后检查显示；新建和编辑系列后对照 Firestore 字段。
5. **文档**：同 S1。

## 6. S1 实现（2026-10-28）

- **数据**（`data/sermon/`）：
  - `SermonReferenceParser`：照 iOS 解析讲道经文，加上 K21、K22 的修正；
  - `YouTubeLinkParser`、`YouTubeSermonTitleParser`（K4）、`YouTubeVideoTitleParser`；
  - `YouTubeWeb`：免密钥的 oEmbed，以及「字幕」用的字幕接口；`TranscriptParser` / `TranscriptSnippet` 照 iOS 拼出"[m:ss] …"；
  - `SermonRules`：日期键（K2）、默认标题、系列识别（§4 问题 3）、合并补齐（K9）、合并对象；
  - `SermonRepository`：手填 / YouTube 新建、编辑（K10、K11）、加入或移出系列、新建系列、标签、删除（K7）、合并（K7、K9）、隐藏索引行（K6）、讲道页写笔记和「字幕」文字；
  - `VideoRepository`：新建（oEmbed，K8）、手填经文、标签、删除（K7）、视频页写笔记；
  - `SeriesList`：系列选择弹窗的排序、搜索（S2 继续用）；
  - `ReadingRepository.markChapterRead`：只标不切换（D1）；
  - `NoteRepository.saveNoteFromContent` 加讲道 / 视频关联；
  - `WireEncoder`：讲道、视频、系列的空字段写"删除该字段"（K1）。
- **界面**（`ui/sermon/`）：
  - `ContainerPage`：讲道页和视频页共用的框架（播放器、信息栏、笔记卡片、"＋"、横屏全屏）；
  - `SermonScreen`、`VideoScreen`、`SermonReaderScreen`（K15）、`SermonEditScreen`（讲道日期、系列「移出」、底部「删除讲道」）；
  - `CreateSermonSheet`、`CreateVideoSheet`、`SeriesPickerSheet`、`MergeSermonDialog`；
  - `SermonDayRow`：Today 和日历汇总的讲道行（打开 / 笔记 / 合并 / 听 / 看 / 读）；
  - `FloatingAudioBar`：主页面在底部 tab 上方，其他页面在页面下方。
- **播放**：
  - YouTube 用 WebView 加载官方 IFrame 播放器，用 YouTube 自己的控制条（播放、拖动、CC）；「字幕」、在 YouTube 打开、全屏三个按钮放在视频下方（iOS 画在视频上面）；
  - 转横屏或按全屏按钮时，同一个播放器铺满屏幕，不重新加载（`MainActivity` 自己处理旋转）；
  - 音频：Media3 `SermonAudioService`（后台播放、通知栏和锁屏控制）+ `SermonAudioPlayer`；
  - PDF 和不是 YouTube 的视频链接用浏览器打开。
- **入口**：Today 的「讲道」「视频」按钮和今日活动、日历汇总、章节页的讲道标题和"⋯"、从 YouTube App 分享（`IncomingShare`）。
- **依赖**：新增 `androidx.media3` 1.11.1（exoplayer、session）。权限：前台媒体播放服务。
- **做的过程中修的问题**：WebView 放进 Compose 后高度是"按内容"，页面按 0 高度排版，YouTube 播放器看不见；改为铺满父布局后正常。调试版打开了 WebView 调试，方便以后排查。

## 7. S1 验证

- **单元测试**：新增 33 个，全部 311 个通过；lint 仍是原来的 51 个警告，没有新增。
  - `SermonParsingTest`：讲道经文（多章、中文、K21、K22、iOS 的边界情况）、各种 YouTube 链接和分享文字、YouTube 标题（含 K4）、视频标题锚点、「字幕」拼句（含中文标点）、JSON3 / XML 字幕、日期键（K2，佛历 / 阿拉伯数字 / 日本年号）、默认标题、系列识别（K3）、合并补齐（K9）和合并对象。
  - `SermonRepositoryTest`：手填新建的字段（`createdAt` 为本地零点、空经文写 `""`）、默认标题加后缀、已读只标不切换（D1）、手选系列优先（D2）、YouTube 新建和索引行、重复链接（D3）、已存为视频的链接（K6）、链接错误、填写的经文决定已读（K5）、编辑清空经文和视频并改日期（K10、K11、K7）、清空字段在 Firestore 被删掉（K1）、删除（K7）、合并（K9、K7）、讲道页笔记带讲道（含 `vN` 和「字幕」文字）、视频新建 / 改经文 / 链接属于讲道 / 恢复删过的视频（K8）/ 删除。
  - `WireEncoderTest` 更新：讲道、系列的空字段改为"删除该字段"。
- **变异检查**：把 D1、D2、K3、K4、K6、K9、"`createdAt` 取零点"、K1、K21、K11、K8 逐一改回 iOS 的做法，对应测试都会失败（11/11）。
- **模拟器**（安卓模拟器 + Firebase 本地模拟器，iOS 格式数据，只用安卓）：
  - 用 REST 写入两篇今天的讲道（一篇带 YouTube、音频、PDF，一篇空的）和一个视频，拉到安卓后 Today 的讲道卡片显示两行、媒体按钮正确。
  - 讲道页：封面、点播放后 YouTube 正常播放；全屏按钮转成横屏铺满，返回后继续播放（同一个播放器，进度没丢）；没有字幕的视频按「字幕」提示"这个视频没有字幕"；"＋"预填 John 4，`v7` 生成 John 4:7 锚点和感想，普通笔记挂在 John 4。
  - 音频：点耳机后悬浮播放条出现并开始播放；回到 Today 后播放条在 tab 上方继续播放；关闭后消失。
  - 合并：有笔记的讲道出现「合并」，对话框写"Move 3 notes from …"；合并后 Firestore 里被合并的讲道变成 `{isDeleted, updatedAt}`，目标讲道补上了视频、音频、PDF、讲员、简介，3 条笔记的 `sermonId` 都改成目标，隐藏索引行指向目标。
  - 编辑：日期选择器不能选今天以后；改到 10 月 25 日、清空经文和视频后，Firestore 里没有了 `bookOrd`、`chapterRange`、`youtubeID`（K1），`preachedOn` 为 20261025，旧索引行变成墓碑；日历 10 月 25 日显示这篇讲道，点「笔记」进回看页，显示经文正文和感想（K15）。
  - 手填新建"1 John 3"并在选择弹窗里新建系列：Firestore 字段与 iOS 一致（`createdAt` 本地零点、`preachedOn`、`bookOrd` 62、`chapters` [3]、`seriesId`；系列有 `sermonIds`、`startedOn`），1 John 3 标为已读，章节页列出这篇讲道（K21）。
  - 新建视频（oEmbed 取到标题、频道、封面）；播放 25 秒后按「字幕」，存下"[0:18] ♪ We're no strangers to love ♪"。
  - 分享：模拟 YouTube 分享，弹窗预填链接、标题和按标题识别的"Genesis 1"；已存过的视频打开原视频并提示（D3）；把已存为视频的链接"添加为讲道"，打开原视频并提示（K6）。
  - 删除视频、删除讲道（编辑页底部）后回到上一页，Firestore 里是墓碑。
  - 中文界面（讲道页、编辑页、删除确认、章节页）和深色模式正常。
- **没有覆盖**：真机（只在模拟器上看过）；和 iOS 一起测；`seriesId` 指向教会导入系列等教会导入相关情况（安卓还没有教会导入）。

