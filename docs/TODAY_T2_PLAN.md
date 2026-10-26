# Today T2：经文引用 — 安卓方案

**已确认（2026-10-26）**：按本方案做；NIV/NKJV 只留接口。**状态：已完成**（未提交），验证结果见 §8。

依据（iOS 代码为准）：`BibleReferenceValidation.swift`、`BibleReferenceHelper.swift`、`BibleBookHelper.swift` / `BibleBooks.swift`、
`BibleVerseRangeReader.swift`、`BibleDownloadService` + `BibleXMLParser` + `BibleDownloadManager`、`UserVerseManager.swift`、
`NotesManager.swift`（`saveSeparatedNoteInput` / `saveVerseReference` / `findVerseAnchor` / `savePrayerNote` / `applyBibleAnchor` /
`ensureUserVerses`）、`BibleReferenceField.swift`、`BookPicker.swift`、`NoteInputSheetView.swift`、`PrayerCreate.swift`；
文档 `NOTE_INPUT_BIBLE_REFERENCE_DETECTION.md` §3/§5.1/§5.5、`USER_VERSE_CONTRACT.md`。

## 1. 两个内置译本的版权

| 译本 | 文件 | 结论 |
| --- | --- | --- |
| KJV | `KJV.json`（getbible.net v2，源自 CrossWire KJV 模块，文件内标 GPL） | 正文是 1769 年钦定本，**英国以外为公有领域**；英国境内由王室特许状（letters patent）管辖，印刷/进口需许可，数字 App 是否需要许可来源没有明说。文件里的 GPL 针对 CrossWire 加的 Strong 编号等标注，打包的 JSON 只有纯正文。 |
| 和合本 | `CUV.xml`（eBible USFX 格式，繁体，"神"版，31,100 节） | 1919 年和合本**已过版权期，公有领域**。这份文件**不是** 1988 年《新标点和合本》（新标点版创 1:1 是"起初，神创造天地"，带逗号，香港圣经公会代理版权）；它是"起初神創造天地。"＋「」引号的台湾通行版，与 CrossWire `ChiUn`（标为 Public Domain）同一系。文件本身没写来源，**建议你确认一下当初从哪里下载的**。 |

安卓打包与 iOS 相同的两个文件，不增加新的版权风险。（以上不是法律意见。）

## 2. 内置经文怎么打包

- 把 iOS 的 `KJV.json`、`CUV.xml` **原样复制**到安卓仓库 `bible-data/`（记录 SHA-256，与 iOS 一致）。
- **构建时**由 Gradle 任务生成只读 SQLite 资源 `bible_text.db`（约 9 MB，APK 内压缩后约 4 MB），安卓用一个独立的只读 Room 库
  `BibleTextDatabase`（`createFromAsset`）读取。首次用到经文时系统复制一次文件（不到 1 秒），**首启不解析**。用户数据库不受影响。
- 正文**逐字节与 iOS 一致**：KJV 照存 JSON 的 `text`（含 1,302 节首尾空格，iOS 也原样存）；和合本按 iOS `BibleXMLParser` 的规则
  （`<v>` 到 `<ve/>` 之间的文字，去首尾空白）。用一个调用 iOS 同款 `XMLParser`/`JSONDecoder` 逻辑的 Swift 脚本导出全部 62,202 节，
  和生成的数据库逐节比对，结果写进本文件。
- 读取逻辑照搬 `BibleVerseRangeReader`：版本名不分大小写、先校验章节范围、诗篇/箴言用换行连接其余用空格、合并文字格式
  `"{章}:{节} 正文"`。
- **NIV / NKJV（API.Bible）**：建议本阶段**只留接口**（`OnDemandVerseSource`，当前返回空），不接网络、不要密钥。选这两个译本时
  照 iOS 的空正文路径处理：锚点照写、不建空的 `UserVerse`。
- **译本选择**：照 iOS 首启规则，中文系统默认和合本（`CUV`），其他默认 `KJV`，存本机设置。选择译本的设置页留到 Settings 标签页再做。

## 3. 解析（纯 Kotlin，配单元测试）

| 安卓 | iOS |
| --- | --- |
| `BibleBookMatcher`：`matches` / 别名 / `allPossibleNames` / `getBookOrdSmart`（先按所选译本语言，再英、简、繁） / `isBookNamePrefix` | `BibleBook.matches`、`BibleBookHelper.getBookOrdSmart` |
| `BibleReferenceValidator`：4 条正则、全角标点归一、单章书 "Jude 5"=第 5 节、`.flashCard` / `.chapterAnchored` / `.sermon`（多章） | `BibleReferenceValidator` |
| `BibleReferenceParser.parseFlexible` + `isWithinCanon` | `BibleReferenceHelper` |
| `VerseShortcut`（`v16`、`v3-5`） | `detectSermonVerseShortcut` |
| `BibleFormatter.display` | 同名 |

正则不用 `\d` `\s` `\b` 的默认含义（JVM 单元测试与安卓设备的 ICU 实现不同），改用明确的字符类，行为对齐 iOS（ICU）。
测试用例移植 iOS 的 `BibleReferenceValidatorTests`（36）、`BibleReferenceParsingCoverageTests`（30）、`BibleCanonBoundsTests`、
`BibleReferenceHelperTests`。

## 4. 保存规则（`VerseReferenceRepository`，对应 `NotesManager`）

**新建笔记**（`saveSeparatedNoteInput`，Today 无章节上下文）：
1. 正文开头是 "书卷 章:节[-节]" → **正文优先**，忽略引用框：建 `.bibleVerse` 锚点，其余文字建一条 `.note` 感想，`return`。
2. 正文含 `vN` 且引用框只写到章（如 "John 3"）→ 拼成 "John 3:N"，正文去掉 `vN`。
3. 引用框有内容 → 按节级（`.flashCard`）校验；到节才保存锚点＋感想，只到章则丢弃引用。
4. 其余 → 普通 `.note`（无锚点）。

**新建祷告**（`savePrayerNote`）：正文原样保存为 `.prayer`；引用框到节级才写 `bookOrd/chapterOrd/verseStart/verseEnd/refKey/versionAbbr`；
然后 `ensureUserVerses`。**正文不做经文识别**（iOS 有意如此）。

**写入格式（与 iOS 代码逐字段一致）**：
- 锚点 `content = "·"`，按 `(书,章,起,止,译本, 本地日历日, 关联)` 去重；同条件的已删除锚点会被**恢复**为今天的（iOS 行为）。
- 锚点、感想、祷告都写 `refKey = "VS:书:章:起-止"`、`chapterKey = "CH:书:章"`（契约 §1.1 说不再写，但 iOS 代码仍在写，照代码）。
- 感想不去重、不带标签；锚点带标签（Today 新建笔记没有标签）。
- `UserVerse`：每个所选译本、每节一行，id `{译本}_{书}_{章}_{节}`；`content` 首次非空后永不覆盖；已软删的行恢复；空行可补正文。
- 全部 `needsSync = true`，走现有同步引擎（`userVerses` 推送/拉取已在数据层完成）。

测试移植 iOS `SaveVerseReferenceTests`（15）、`UserVerseTests`（20）、`BibleVerseRangeReaderTests`，并新增 iOS 文档 §7 要求的
"正文引用覆盖引用框" 用例；再用现有 `WireEncoder` 测试确认推送字段与 iOS 一致。

## 5. 界面

- `BibleReferenceField`（Compose）：左边书卷按钮（底部弹出 66 卷列表，主/次语言两列，按所选译本），中间输入框，右边说明按钮；
  绿勾/红叹号、下方提示行（解析后的规范写法 / 错误原因 / 失焦时"还需输入节"），未知书卷和倒序范围有 400 ms 输入宽限。
  文案沿用 iOS 三种语言的字符串。
- 放进现有的新建祷告、新建笔记弹窗。笔记"保存"可用条件同 iOS：正文非空，或引用到节级（只有经文、没有感想也能存）。

## 6. 发现的 iOS 问题（实现时补进 `IOS_ISSUES.md`）

| 问题 | 安卓做法 |
| --- | --- |
| 正文识别（`parseFlexible`）不处理全角"：""－"，中文输入法打"约翰福音3：16"识别不到（引用框却可以） | 正文识别前同样归一全角标点；写入格式不变 |
| 笔记/祷告弹窗的说明页显示的是**讲道**示例（"3, 5:1–3"、"6"），这两个框都要求到节、不支持多章 | 说明页改为节级示例（"John 3:16"、"Jn 3:16-18"、"约 3:16"、"Jude 5"） |
| `findVerseAnchor` 要求 `verseEnd` 精确相等；`verseEnd` 为空的锚点（网页端/旧数据）永远去重不到，会重复建锚点 | 空 `verseEnd` 视为等于 `verseStart` |
| `USER_VERSE_CONTRACT` §1.1 写"新 Note 不写 `refKey`/`chapterKey`"，代码仍在写 | 照代码写（文档问题） |
| KJV.json 有 1,302 节带首尾空格，原样进入 `UserVerse.content` | 照存，保持两端一致（仅记录） |
| （实现时新增）引用框为章、正文"v99"超出范围时，iOS 丢掉引用且"v99"文字也丢了 | 只有能保存的引用才改写，否则正文原样保存 |
| （实现时新增）引用框下方的规范写法提示固定用英文书名 | 用主译本的书名语言 |

以上已写入 `IOS_ISSUES.md`（A3、F1–F6）。

## 7. 交付顺序与验收

1. 解析 + 测试 → 2. 内置经文（Gradle 生成 + Swift 比对）→ 3. 保存规则 + 测试 → 4. 界面 → 5. 模拟器实测：
   在 Android 模拟器 + Firebase 本地模拟器上，分别用"正文引用""引用框""vN""祷告带引用"新建，检查 Emulator UI 里
   `notes`（锚点正文解密后为 "·"、`refKey`/`chapterKey`）和 `userVerses/KJV_43_3_16` 等文档与 iOS 格式一致；
   再导入 iOS 格式的锚点/userVerses 数据，确认安卓去重与显示正确。

不在 T2：NIV/NKJV 联网、译本选择设置页、经文笔记的列表/详情页（Today 方块点进去仍是"即将推出"）、讲道/视频笔记里的引用（T3）。

## 8. 验证结果（2026-10-26）

- **单元测试** 192 个全部通过（新增：解析 61、内置经文读取 10、保存规则 33；对关键规则做了变异测试，均能抓到）。
  `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 通过，lint 无新错误。
- **内置经文**：用照搬 iOS `BibleXMLParser` / `JSONDecoder` 逻辑的 Swift 脚本导出全部 62,202 节，与构建生成的
  `bible_text.db` 逐节比对：缺 0、多 0、不同 0。数据库 9.6 MB，Room 首次打开时从 APK 复制。
- **安卓模拟器**（Medium_Phone_API_36）：引用框（绿勾、"John 3:16-17"提示、失焦"Add a verse."、红色"Matthew has only 28 chapters."）、
  书卷选择、说明页；四种保存路径（引用框到节、正文开头引用、`vN`、祷告带引用）写入的本地数据与 iOS 规则一致。
- **Firebase 本地模拟器**：同步后 `notes` 文档字段（ords、`versionAbbr`、`refKey`、`chapterKey`、`calendarDay`、`schemaVersion` 6、加密正文）
  与 `userVerses` 文档（`KJV_43_3_16` 等，正文不加密、`schemaVersion` 5）与 iOS 推送格式一致；用 CryptoKit 解密正文：锚点为 "·"，
  感想与祷告为用户原文。再写入一条 iOS 格式的锚点和 `userVerses`，安卓拉取后对同一节保存：**复用该锚点**、只新建感想，
  iOS 冻结的正文不被覆盖。
- 未覆盖：中文输入法的实际输入（adb 无法输入中文，已由单元测试覆盖）；与真实 iOS 设备的联合测试。
