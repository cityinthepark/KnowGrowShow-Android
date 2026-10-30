# 在线资源 / 教会导入 — 安卓方案

**状态：用户已确认（2026-10-30）：`churches` 加到 `users/{uid}/settings/preferences`；主教会首个自动、可手动改；档案直接读 Firestore；这一轮只做安卓，iOS 回头同步（见 §4）。可以开始实现。**

依据（iOS 代码为准，只读，路径 `/Users/joyce/Documents/Development/KnowGrowShow`）：
- 界面：`OnlineResourceDetailView`、`SettingsView` 的"在线资源"一行、`AppDistribution`（开关）。
- 导入：`ChurchSermonImportService`（manifest、系列 / 讲道 DTO、`upsertSeries` / `upsertSermon`）、`ChurchSermonImportConfiguration`、`OnlineChurchProfileStore`、`ImportedChurchProfile`、`SermonImportHelper.swift` 里的 `ChurchKeys`、包内 `Utils/DataFiles/riverside.json`、`SermonModels.swift` 里的 `SeriesDTO` / `SermonDTO`。
- 云端格式：`docs/ONLINE_RESOURCE_PROFILE_FIRESTORE.md`、`docs/STAGE2_DEV_PLAN.md` §1、`docs/FIRESTORE_SYNC_CONTRACT.md`、`CloudDataWipeService`、`emulator/firestore.rules`。
- 真实仓库：`https://raw.githubusercontent.com/stoneofhopeapp/sermons-riverside/main/`（`manifest.json`、`20260802.json`、`series_20260802.json` 已实际读过格式）。

## 1. iOS 现在怎么做

### 1.1 入口和界面

- 设置 tab 最上面一段"在线资源"（`AppDistribution.onlineResourcesEnabled`，现在恒为 true）：**写死一行 Riverside Calvary Chapel**（名称 + 副标题），点进 `OnlineResourceDetailView(churchKey: "riverside")`。
- 详情页（导航标题"在线资源"）：教会名（小字）→ Logo 图（包内图片 `riversideLogo`）→ 简介 → **一个大按钮** → 脚注"⚠️ 这个功能需要联网获取最新讲道"。
  - 还没导入过：按钮"导入全部讲道"；导入过：按钮"导入最新更新"。
  - 点后盖一层半透明遮罩 + "正在导入讲道…"；完成弹窗：有新内容 "导入完成：N 个系列，M 篇讲道"，没有则"没有新的讲道"。
  - 详情页没有网站 / YouTube 链接的入口（档案里有 `churchWebsite`、`youtubeHomeLink`，界面没用）。

### 1.2 教会档案（profile）

- 字段（`ImportedChurchProfile`）：`schemaVersion`、`id`、`churchKey`、`isUserData`(false)、`enabled`、`name`、`subtitle`、`intro`、`introLocalizationKey`（没用）、`logoImageName`、`logoURL`、`churchWebsite`、`youtubeHomeLink`、`churchSermonBaseURL`、`sermonImportRepoURL`、`updatedAt`。
- 来源：**只有包内 `riverside.json`**（"Stage 1"）。`OnlineChurchProfileStore` 只读包、放内存缓存；iOS 不读也不写 Firestore 的 `onlineResourceProfiles`（那个集合公开可读、客户端不可写，是给 Web 和将来 Stage 2 用的）。
- `enabled == false` 或没有 `sermonImportRepoURL` 就不能导入。

### 1.3 导入流程（`ChurchSermonImportService`）

1. 读 `{sermonImportRepoURL}manifest.json`：`{ "latestVersion": "20260802", "files": ["20260518.json", …, "series_20260528.json", "series_20260802.json"] }`。版本号是 **8 位数字日期**。
2. **导入全部**（`importInitial`）：manifest 里列的所有文件都导。**导入更新**（`importUpdates`）：本机版本 < `latestVersion` 才继续，只导文件名版本 **大于** 本机版本的文件。
3. 文件分两类：`series_YYYYMMDD.json`（系列数组）、`YYYYMMDD.json`（讲道数组）。**先导全部系列，再导全部讲道**，各自按文件名排序。
4. 每个文件单独下载、解码，**同一文件内所有行在一次事务里写入**。
5. 导完把 `latestVersion` 记到 `UserDefaults["sermonsLastImported_<churchKey>"]`（设备本地）。
6. `hasImported`：有这个版本号，**或者**本机已有 `isUserData=false` 且 `churchKey` 相同的未删除讲道。

**系列行**（`SeriesDTO`）：`id`(24 位 hex)、`churchKey`、`title`、`bookOrd?`、`startedOn`(yyyyMMdd)、`isFinished`。真实文件里还有 `lastPreachedOn`，iOS 不读。按 `id` upsert：写 `title`、`bookOrd`、`startedOn`（要能规范成日期，否则跳过）、`isFinished`、`churchKey`，`isUserData=false`、`needsSync=false`。

**讲道行**（`SermonDTO`）：`id`、`churchKey`、`preachedOn`、`seriesId`、`title`，可选 `preacher`、`intro`、`audioUrl`、`videoUrl`、`youtubeId`、`pdfUrl`、`book`、`bookOrd`、`chapterRange`、`chapters[]`。真实文件里还有 `date`、`thumbnailUrl`，iOS 不读。按 `id` upsert：可选字段**去空白后空串存为空**，`chapters` 整个重写；**`seriesId` 对应的系列本机必须已存在，否则整行跳过**；`isUserData=false`、`needsSync=false`；新建的行 `createdAt` = `preachedOn` 那天。

### 1.4 开关

`AppDistribution.remoteChurchImportEnabled`（国内版 `isChinaLocal` 为只读本地版，不联网导入）。安卓没有国内版，不需要。

### 1.5 云端：iOS 和 Web 现有的格式（重点）

读下来的结论：

- **iOS 没有"用户 profile 文档"，云上也没有 `meta/profile`**。`users/{uid}` 下只有集合（`tags`、`sermonSeries`、`sermons`、`notes`……）和 `meta/account`（只放 epoch）、`devices/{deviceId}`；`users/{uid}` 根文档不存在。**Web 有一个用户级文档 `users/{uid}/settings/preferences`**（`StoneOfHope_Website/web/src/entities/user-settings`），目前只有 `preferredVersions` 和 `updatedAt`，用 `setDoc(..., {merge:true})` 写；iOS / 安卓都不读写它。
- **另外 `docs/ONLINE_RESOURCE_PROFILE_FIRESTORE.md` 已经定义了"用户导入了哪些教会"**：`users/{uid}/onlineResources/{churchKey}`，字段 `schemaVersion`(1)、`churchKey`、`importedAt`(ISO-8601)、`lastImportedVersion`(可选)，"iOS + Web 共用，只存导入信息，显示用的字段去读 `onlineResourceProfiles`"。Web 的 `userOnlineResourcesStore.ts`（旧 mvp 网页）确实在读写它；**iOS 代码里完全没有实现**（搜不到 `onlineResources`），`STAGE2_DEV_PLAN.md` 却写"profile 不同步到 `users/{uid}`"——两份文档互相矛盾。
- 云端规则 `users/{userId}/{document=**}` 是"本人可读写"，任何新路径都能用；iOS 的拉取只读固定的集合列表，**多出来的路径 iOS 完全不会碰**，所以安卓加字段不会弄坏 iOS。
- iOS 的"登出云 / 删除账号"（`CloudDataWipeService.userScopedCollections`）只清已知的集合，**不知道**新路径，所以新路径会在 iOS 清云数据后残留（见 M9）。
- 公共目录 `onlineResourceProfiles/{churchKey}`：公开读、不可写；`churchSeries` / `churchSermons` 是 Web 专用的 Firestore 镜像（安卓不用，和 iOS 一样直接读 Git 仓库）。

## 2. 安卓做法

### 2.1 总体原则

- 和 iOS 一样：讲道 / 系列从 Git 仓库**直接下载到本机**，`isUserData=false`、不上传（安卓的 `SermonEntity.churchKey`、`isUserData` 和上传时只传 `isUserData=1` 的行已经就绪，阶段 3 完成）。
- **不连云也必须能用**：导入、教会列表、主教会全部先存本机；登录后再把"教会列表"同步到云端给 Web。

### 2.2 界面（照 iOS，用 Material）

- **设置 tab**：最上面加"在线资源"一段，一行教会（名称 + 副标题，右箭头；是主教会时在副标题旁加"主教会"小标）→ `OnlineResourceDetailScreen`。**现在只有 Riverside 一个教会**，所以和 iOS 一样先写死一行，但数据按"教会列表"来做，将来多教会只需把行变成列表。
- **详情页**：Logo（档案 `logoImageName` 对应的图放进 `res/drawable-nodpi`，iOS 的 `Riverside.jpg`）、教会名、简介、主按钮（"导入全部讲道" / "导入最新更新"，按本机是否导入过切换）、联网提示、遮罩进度。此外**新增**：
  - "设为主教会"（Material `FilterChip` / 开关，已是主教会时显示"主教会"状态）；
  - 档案里的网站、YouTube 主页两个链接按钮（iOS 有字段没入口，这里只是让它们能用）。
- 完成提示用 `Snackbar` / 弹窗，文案沿用 iOS，**但区分"联网失败"和"没有新内容"**（M3）。

### 2.3 教会档案来源（已确认：读 Firestore）

- 读公开目录 `onlineResourceProfiles/{churchKey}`（`read: true`，不需要登录）——**所有用户都读，包括"只在本机"的**：走 Firestore REST 接口（`https://firestore.googleapis.com/v1/projects/<projectId>/databases/(default)/documents/onlineResourceProfiles`，用 Firebase 本地模拟器时是 `http://<host>:8080/v1/...`），一次普通 HTTP GET，不启动 Firebase SDK、不登录。不用 SDK 是因为启动 Firebase 在安卓里等于"开了云"（账户页显示已连云、同步开始等登录），对"只在本机"的用户不对。2026-10-30 实测 production 不登录可读，`riverside` 文档的仓库地址与包内一致。字段与 `ImportedChurchProfile` 相同；`enabled=false` 的不显示、不能导入。只取 `resourceType` 缺省或为 `church` 的文档（YouTube 频道文档本轮忽略）。
- **读不到也必须能用**：包内带一份 `assets/churches/riverside.json`（与 iOS 包内文件相同，讲道仓库 `https://raw.githubusercontent.com/stoneofhopeapp/sermons-riverside/main/`）当兜底；取不到云端时（离线、模拟器没启动、文档不存在）用上次缓存，没有缓存就用包内的 Riverside，照样从这个仓库导入——和 iOS 现在的做法一样。云端取到后缓存到本机（`app_prefs` 里的 `onlineResourceProfilesCache`，整份目录存 JSON），下次离线先用缓存。
- 设置里"在线资源"一段：**列出云端所有 `enabled` 的教会**（取不到就只显示包内的 Riverside 一行），每行名称 + 副标题，主教会带"主教会"小标；这样将来加教会不用改安卓。iOS 现在是写死一行，安卓这里比 iOS 多走了一步（iOS 的 Stage 2 计划也是这样）。
- 档案里的 `logoURL` 异步加载（没有则用包内 Logo：Riverside 用 iOS 的 `Riverside.jpg`，放 `res/drawable-nodpi`）。若项目没有图片加载库，不新增依赖，用简单的下载 + 内存缓存实现，或只在有包内图时显示。
- 这一块放在 `ChurchProfileSource` 接口后面，便于测试用假数据。

### 2.4 导入逻辑（`ChurchImportRepository`，Kotlin 重写 `ChurchSermonImportService`）

- 联网用 `HttpURLConnection` 或项目里已有的 HTTP 方式（实现前确认，不新增依赖）；JSON 用已有的解析库，**忽略未知字段**（`lastPreachedOn`、`date`、`thumbnailUrl`）。
- 文件名分类、版本比较、"先系列后讲道"、"seriesId 找不到就跳过"、字段清理规则都和 iOS 一致（§1.3）。**一个文件一个 Room 事务**。
- **和 iOS 不同（M1、M2）**：
  1. 版本号只在**所有文件都成功**后才记成 `latestVersion`；有文件没导完（下载 / 解码失败，或其中讲道找不到系列）就记成**最早那个没导完的文件的版本**，下次"导入最新更新"从它开始重试；
  2. 更新时用 `>=` 本机版本：最新那天的文件每次都会重新下载（同一天重发的内容也能拿到；导入是幂等的，没变的行不计数），没有变化就报"没有新的讲道"；
  3. 导入失败（断网、manifest 打不开）返回错误，界面显示"无法连接，请检查网络"，不再和"没有新内容"混在一起。
- 计数：弹窗显示本次**新增 / 更新**的行数（iOS 显示的是"处理的行数"，包括没变的；M4）。
- 讲道的 `tagIds`、笔记等用户数据不会被导入覆盖（upsert 只写 DTO 里的字段）。

### 2.5 本机存储

| 数据 | 存哪 | 备注 |
| --- | --- | --- |
| 每个教会的"已导入版本" | `app_prefs`（SharedPreferences，与项目其它设置相同）键 `sermonsLastImported_<churchKey>`（与 iOS 键名一致） | 设备本地，**永不上云**：别的设备上的版本号不能代表这台设备已导入 |
| "我的教会"列表（`churchKey`、是否主教会、加入时间、本机更新时间、`needsSync`） | **Room 新表 `onlineChurches`**（升 `AppDatabase` 版本并写 Migration，加 schema 导出） | 要参与同步，所以用表而不是偏好 |
| 教会档案缓存 | `app_prefs` 键 `onlineResourceProfilesCache`（JSON） | 离线时先用 |

### 2.6 `churches` 字段（给 Web 用，已确认）

**位置**：`users/{uid}/settings/preferences`（Web 已有的用户级文档），**只用 merge 写自己的字段，绝不碰 `preferredVersions`**。新增字段：

```
churches: [
  { churchKey: "riverside",                      // = onlineResourceProfiles 的文档 id，与公共目录一致
    isPrimary: true,
    importedAt: "2026-10-30T08:00:00Z",          // 第一次导入的时间，ISO-8601
    lastImportedVersion: "20260802" }            // 最近一次在任意设备导入到的版本；Web 展示用，安卓不据此跳过导入
]
primaryChurchKey: "riverside"                     // 与 churches 里 isPrimary 一致，方便 Web 直接读；没有主教会时删掉此字段
churchesUpdatedAt: <服务器时间戳>                 // 不动 Web 已有的 updatedAt（它表示 preferredVersions 的修改）
```

- 元素名 `importedAt`、`lastImportedVersion` 沿用 `onlineResources/{churchKey}` 的字段名，Web 以后可以直接复用解析。
- **只有一个主教会**：第一个导入的教会自动成为主教会；之后在详情页点"设为主教会"可改（原来的自动取消）；移除主教会时，剩下的按 `importedAt` 最早的接任。（本轮不做"移除教会"界面，规则先写进代码和测试里。）
- **合并（多设备）**：以 `churchKey` 为键逐项合并；`importedAt` 取较早的；`isPrimary` 以本机 `updatedAt` 较新的一边为准；推送前先读云端（`Source.SERVER`）再用事务写，写完保证只有一个 `isPrimary`；`primaryChurchKey` 由结果推出。
- **同步时机**：导入完成 / 改主教会后，该教会行 `needsSync=true`，走现有推送队列（只在登录且开启云同步时推；没登录就只留本机，登录后补推）。登录后同步时读这份文档，把云上有、本机没有的教会加进本机列表，**但不会自动下载讲道**（本机显示成"已关注、尚未导入到本机"，主按钮是"导入全部讲道"）。
- **iOS 不读不写这个字段**（iOS 的拉取只读固定集合，`settings/preferences` 完全不碰，所以安卓写它不会影响 iOS）。因此 iOS 用户导入的教会 Web 暂时看不到——回头 iOS 同步时按同一格式补；需要的改动写在 §4 的"给 iOS 的待办"。
- 不再使用 `users/{uid}/onlineResources/{churchKey}`（那是 Web 旧 mvp 在用的格式，安卓不写；Web 新站 `StoneOfHope_Website` 没有读它）。

### 2.7 实现步骤

| 步骤 | 内容 |
| --- | --- |
| **C1 档案与导入核心** | `ChurchProfile` 数据类 + Firestore 读取 / 本机缓存 / 包内兜底、`ChurchSermonImportService` 的 Kotlin 版（manifest / DTO / upsert / 版本过滤 / 错误区分）、DataStore 版本偏好；仓库单元测试（用假 HTTP 返回） |
| **C2 界面** | 设置里"在线资源"一行、详情页、遮罩与结果提示、Logo 资源、三种语言文案 |
| **C3 我的教会与主教会** | `onlineChurches` Room 表 + Migration + DAO、主教会规则、详情页"设为主教会" |
| **C4 云端 `churches`** | `settings/preferences` 的 `churches` 字段编码 / 解码（merge，不碰 `preferredVersions`）、推送（进现有 pusher）、拉取与合并、本机显示"已关注未导入" |

## 3. 发现的 iOS 问题（M 节，写入 IOS_ISSUES.md）

| # | iOS 位置 | 问题 | 影响 | 安卓做法 | iOS 修后安卓要改吗 |
| --- | --- | --- | --- | --- | --- |
| M1 🔴 | `ChurchSermonImportService.importFiles` / `importUpdates` / `importInitial` | 单个文件下载或解码失败只是打印日志、算 0 行，**随后照样把版本号推到 `latestVersion`**。之后"导入最新更新"认为已是最新，那几个文件永远不会再导 | 一次断网 / 超时就让一批讲道永久缺失，用户没有任何提示，也没法补（只能清数据） | 有文件失败就不推进版本，下次重试 | 不用 |
| M2 🟡 | `filterNewFiles`（`version > localVersion`） | 本机版本等于某天时，同一天后来重新发布的文件（文件名不变）不会再被拿到 | 当天补发的讲道漏掉 | 用 `>=` | 不用 |
| M3 🟡 | `OnlineResourceDetailView.finishImport` | manifest 打不开（断网）也返回 (0,0)，弹窗写"没有新的讲道" | 用户以为是最新，其实根本没连上 | 区分"无法连接"和"没有新内容" | 不用 |
| M4 🟡 | `upsertSeries` / `upsertSermon` 的计数 | 数的是"处理的行数"，已存在且没变的行也算；第二次点导入全部也会再报"导入了 N 篇" | 数字不代表新增 | 显示新增 + 更新的行数（没变的不算） | 不用 |
| M5 🟡 | `upsertSermon` | 讲道的 `seriesId` 找不到系列就整行跳过且不再重试（受 M1 影响），系列在更早的文件里下载失败时讲道也一并丢 | 同 M1 | 随 M1 一起处理：失败文件不推进版本 | 不用 |
| M6 🟡 | `OnlineResourceDetailView` | 档案里有 `churchWebsite`、`youtubeHomeLink`，界面没入口 | 字段白存 | 详情页加两个链接按钮 | 不用 |
| M7 🟡 | `ChurchSermonImportService.hasImported` | 本机版本号存在 UserDefaults，Realm 数据被清而 UserDefaults 没清（或反过来）时，按钮文案与实际不一致（显示"导入最新更新"但本机没有讲道） | 用户以为已导入 | 安卓导入前以"本机有没有该教会的讲道 + 版本号"共同判断：有版本号但没有讲道，按"导入全部"处理 | 不用 |
| M8 🟡 | 文档 `STAGE2_DEV_PLAN.md` vs `ONLINE_RESOURCE_PROFILE_FIRESTORE.md` | 一份说"profile 不同步到 `users/{uid}`"，一份定义了 `users/{uid}/onlineResources/{churchKey}`（并说 iOS + Web 同步），iOS 代码里两者都没实现；现在又定了 `settings/preferences.churches` | 三套说法并存，以后 iOS / Web 容易各做各的 | 安卓按 `settings/preferences.churches` 做；这两份 iOS 文档需要改（不由我改 iOS 仓库） | iOS 文档要更新 |
| M9 🟡 | `CloudDataWipeService.userScopedCollections` | 只清已知集合；安卓写了 `settings/preferences.churches` 后，iOS 的"清云数据 / 删除账号"不会清掉 `settings` 这个集合 | 删账号后留下云上残留文档 | 安卓的清除（阶段 5 账号页）会包含 `settings` 集合 | iOS 需把 `settings` 加进清单（Web 的 preferences 也是同样残留） |

不影响数据格式：M1–M7 都是行为修正；M8、M9 要你定了字段位置才有定论。（"导入内容只增不减"：仓库里删除的讲道不会在本机删除，iOS 也没有，安卓同样不做，记为已知限制。）

## 4. 用户已确认的决定（2026-10-30）

1. **`churches` 放哪**：`users/{uid}/settings/preferences` 里加 `churches` 数组（+ `primaryChurchKey`），merge 写；`churchKey` 与 `onlineResourceProfiles` 文档 id 一致。
2. **主教会**：第一个导入的自动成为主教会，之后可在详情页手动改，只能有一个。
3. **教会档案**：直接读 Firestore `onlineResourceProfiles`；包内 Riverside + 本机缓存只作离线兜底（§2.3）。
4. **iOS**：这一轮只做安卓，iOS 回头同步。

**给 iOS 的待办（回头单独做，本轮不动 iOS 仓库）**
- 导入 / 设主教会时，按 §2.6 的格式 merge 写 `users/{uid}/settings/preferences.churches`，拉取时合并；
- 修 M1–M7；
- 档案改为先读 `onlineResourceProfiles`（不登录也能读，"只在本机"用户同样适用；中国版除外，仍只用包内），读不到再用包内 `riverside.json`；
- `CloudDataWipeService.userScopedCollections` 加 `settings`；
- 更新 `STAGE2_DEV_PLAN.md` 与 `ONLINE_RESOURCE_PROFILE_FIRESTORE.md` 里互相矛盾的"用户导入"说法（M8）。

## 5. 步骤与验证

**共同验证**：Robolectric 单元测试 + 变异检查（临时改坏一处看测试会不会失败）；安卓模拟器 + Firebase 本地模拟器实机走一遍；`lint` 不新增警告（现有 51 个）；提交前确认 `dev.properties`、`keystore.properties`、`google-services.json`、`.kotlin/` 没被暂存；只在用户要求时提交到 main，不推送。

- **C1**：单测——文件名分类（`series_` / 8 位数字 / 其他忽略）、版本过滤（`>=`、空版本）、先系列后讲道、`seriesId` 缺失跳过、字段去空白、`chapters` 重写、`createdAt`=`preachedOn`、未知字段忽略、**某个文件失败时版本不推进且下次重试**、断网与"没有新内容"区分、upsert 幂等、导入不覆盖讲道上的用户标签、`isUserData=false` 的行不进推送队列。
- **C2**：单测——详情页状态（未导入 / 已导入 / 导入中 / 失败）；模拟器——设置里点进详情、导入全部、再点导入更新、断网（飞行模式）提示、导入的系列出现在 Series tab、讲道可打开。
- **C3**：单测——第一个自动为主教会、手动改只留一个、移除主教会后接任、Migration 保留旧数据；模拟器——详情页设为主教会、重启后保留。
- **C4**：单测——`churches` 编码 / 解码、合并（两台设备各加一个教会、同时设主教会）、未登录时不推、登录后补推；模拟器 + Firebase 本地模拟器——导入后检查 `users/{uid}/settings/preferences` 文档，换一个"设备"（清本机数据再登录）看到"已关注、尚未导入"；确认 iOS 同账号同步不受影响（iOS 不读此路径）。

## 6. 实现

**C1 档案与导入核心（已完成，未提交，2026-10-30）**：`data.church` 包——
- `ChurchProfile`（iOS `ImportedChurchProfile` 字段，`importBaseURL`；`fromCatalog` 把 Firestore 文档转成档案，`resourceType` / `kind` 不是 `church` 的跳过，文档 id 即 `churchKey`）；
- `ChurchProfileStore`：`refresh()` 先读 Firestore（`RestChurchCatalog`，REST GET、按页读完，所有用户都读，见 §2.3），成功就缓存；读不到用缓存，再读不到用 `assets/churches/riverside.json`（从 iOS 原样复制）；只返回 `enabled` 的，按名字排序；
- `ChurchSermonImporter`：`importAll` / `importUpdates` / `hasImported` / `lastImportedVersion`，结果 `Done`（新增 / 更新的系列与讲道数、没导完的文件）、`Unreachable`（M3）、`NotImportable`；修了 M1、M2、M4、M5、M7；
- `ChurchFeed`（`HttpURLConnection` GET，不加依赖）、DTO 用已有的 kotlinx.serialization（忽略 `lastPreachedOn`、`date`、`thumbnailUrl`）；`SermonDao.countActiveForChurch`。

验证：新增 14 个单测（文件名 / 版本过滤、日期规则、先系列后讲道、字段清理、`createdAt`、不进推送队列、坏行跳过、失败文件重试、同一天重发、只数变化、保留用户标签、断网区分、不可导入、M7；档案：包内 JSON、云端优先并缓存、离线回退、目录文档映射），全部 368 个测试通过；变异检查 10 处改坏全部被测试抓到。用临时测试实际导了一遍真实 Riverside 仓库：1.4 秒导入 138 个系列、1560 篇讲道（1564 行里有 4 篇在两个文件里重复出现），没有文件失败；随后"导入更新"34 毫秒，报告没有新内容。lint 52 个，全部是原有类型（依赖新版本提示、`UseKtx`、`UnusedResources`），新代码里没有；比之前多的 1 个是新出的依赖版本提示。C1 没有界面，模拟器验证放到 C2。

**C1 补充（2026-10-30）**：档案读取从"Firebase 已启动才用 SDK 读"改为 REST，"只在本机"的用户也读得到。新增 3 个单测（REST 页解析含 YouTube 文档过滤与时间戳、翻页、任一页失败回退包内、地址拼法），4 处变异全部被抓到；临时测试实读 production 目录，只得到 `riverside`，仓库地址正确。全部 371 个测试通过，lint 仍是 52 个、新代码里没有。

**C2 界面（已完成，未提交，2026-10-30）**：
- 设置 tab 最上面"线上资源"一段（`OnlineResourcesViewModel`：先显示缓存 / 包内，再用目录刷新），列出所有 `enabled` 的教会：名称 + 副标题 + 右箭头，点进详情；没有教会时整段不显示。"主教会"小标留给 C3。
- 详情页 `OnlineResourceScreen`（路由 `onlineResource/{churchKey}`）照 iOS：小字教会名 → 卡片（Logo、简介、主按钮"导入所有讲道" / "导入最新更新"、联网提示）；卡片下面是网站、YouTube 两个按钮（M6）。Logo 先用包内图（`logoImageName` "riversideLogo" → `res/drawable-nodpi/riverside_logo.jpg`，iOS 的 `Riverside.jpg`），没有包内图才下载 `logoURL`（内存缓存，不加图片库）。
- 导入中盖半透明遮罩 + "正在导入讲道 …"，遮罩挡住点击，返回键和左上角返回在导入期间无效（离开页面会取消导入）。
- 结果弹窗（`ChurchImportResult.message()`）：导入完成 N 个系列、M 篇讲道（系列 / 讲道数用 plurals，英文不会出现 "1 sermons"）/ 没有新的讲道内容 / 无法连接（M3）/ 暂时无法导入；有文件没下载成功时多一行"请点'导入最新更新'再试一次"（M1）。
- 文案三种语言，能对上的直接用 iOS 原文。

验证：新增 3 个单测（`OnlineResourceViewModelTest`：先"导入全部"后按钮变"导入最新更新"、第二次只下载新版本的文件、断网提示且按钮不变、各种结果的文案），4 处变异全部被抓到（第一次有 1 处没抓到，补了"第二次只下载哪些文件"的断言）。全部 374 个测试通过，lint 52 个、新代码里没有。模拟器（emulator-5554，debug 包连 Firebase 本地模拟器）实测：
- 本地模拟器里没有档案 → 用包内 Riverside，设置行、详情页、Logo 正常；
- 导入全部：遮罩出现，约 6 秒后"导入完成：138 series, 1560 sermons"，系列 tab 出现导入的系列；按钮变"导入最新更新"，再点 → "No new sermons found."；
- 关掉 Wi-Fi 和数据再点 → "Couldn't connect…"，按钮不变；网络已恢复；
- 简体中文下设置行"线上资源"、按钮、提示、结果都是中文；测完把 App 语言恢复为跟随系统；
- 在本地模拟器临时加一份副标题不同的 `riverside` 档案 → 设置行显示云端副标题（目录优先）；随后删掉该文档，并清掉设备上缓存的这份测试档案；
- "网站"按钮打开 Chrome。

已知行为：目录读成功但一份教会都没有时（例如本地模拟器是空的），保留上次缓存，不清空——这样空目录不会让教会消失；某教会下架请用 `enabled=false`。
