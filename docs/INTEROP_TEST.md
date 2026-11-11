# iOS ↔ Android 互通测试（Firebase 本地模拟器）

全程在本机：Firebase Local Emulator Suite（Auth + Firestore + Storage）+ iOS 模拟器 + Android 模拟器。
不需要 Blaze，不碰生产数据。

## 0. 一次性准备

- Android：`cp dev.properties.example dev.properties`，填 `demoAuth.email` / `demoAuth.password`
  （只用于模拟器的测试账号，不要用真实密码）。`dev.properties` 已被 gitignore。
- iOS：账号密码**不要**写进共享的 `KnowGrowShow` 方案（它在 `xcshareddata` 里，会被提交）。改用一份私有方案：
  1. Product → Scheme → Manage Schemes… → 选中 `KnowGrowShow` → 齿轮 → Duplicate，命名如 `KnowGrowShow (Local)`
  2. 在列表里**取消勾选 Shared**（私有方案存在 `xcuserdata`，已被 gitignore）
  3. Edit Scheme → Run → Environment Variables：
     - `FIREBASE_USE_EMULATORS = 1`、`FIREBASE_EMULATOR_HOST = 127.0.0.1`（勾选）
     - 新增 `KGS_DEMO_AUTH_EMAIL` / `KGS_DEMO_AUTH_PASSWORD` = Android `dev.properties` 里**同一个**测试账号
- iOS 也可以不建方案，从命令行启动（2026-10-28 用的就是这个办法）。用一台专门的模拟器，避免和跑 iOS 测试的会话抢设备：
  ```bash
  xcrun simctl create "KGS Interop iPhone 17" com.apple.CoreSimulator.SimDeviceType.iPhone-17 com.apple.CoreSimulator.SimRuntime.iOS-27-0
  # 在 iOS 仓库里增量编译（用 Xcode 的编译缓存，见 IOS_ISSUES J2）
  xcodebuild -project KnowGrowShow.xcodeproj -scheme KnowGrowShow -destination 'id=<UDID>' build
  xcrun simctl install <UDID> ~/Library/Developer/Xcode/DerivedData/KnowGrowShow-*/Build/Products/Debug-iphonesimulator/KnowGrowShow.app
  P=~/Documents/Development/KnowGrowShow-Android/dev.properties
  SIMCTL_CHILD_FIREBASE_USE_EMULATORS=1 SIMCTL_CHILD_FIREBASE_EMULATOR_HOST=127.0.0.1 \
  SIMCTL_CHILD_KGS_DEMO_AUTH_EMAIL="$(sed -n 's/^demoAuth\.email=//p' $P)" \
  SIMCTL_CHILD_KGS_DEMO_AUTH_PASSWORD="$(sed -n 's/^demoAuth\.password=//p' $P)" \
  xcrun simctl launch --console-pty <UDID> com.stoneofhope.stoneofhope
  ```
  环境变量只对这一次启动有效。**登录后不要点图标打开这个 app**：没有这些变量时它会连正式 Firebase，却还带着本地测试账号的登录状态。测完删掉 app（`xcrun simctl uninstall <UDID> com.stoneofhope.stoneofhope`）。
- iOS 必须带上 `FirebaseEmulatorBootstrap` 里关闭 SSL 的那行（IOS_ISSUES J1），否则连不上本地 Firestore。

## 1. 启动

```bash
cd ~/Documents/Development/KnowGrowShow && ./Scripts/start-firebase-emulators.sh
```

- Emulator UI：<http://127.0.0.1:4000>（看 Auth 用户、Firestore 文档）
- 模拟器数据在停止后清空；要保留可加 `--import=./emulator-data --export-on-exit`
- Android 模拟器访问 Mac 用 `10.0.2.2`，iOS 模拟器用 `127.0.0.1`（已是默认值）

## 2. 登录同一账号

1. **先 Android**：Android Studio 运行 debug → Home →「Account & Sync」→ Sign in。
   模拟器里没有这个账号时会自动创建；首次登录会创建 `private/{uid}/secureKeys/noteKey`。
2. **再 iOS**：Account 页面的演示登录（DEBUG）。同一邮箱 → 同一 `uid` → 同一 `note_key`。

在 Emulator UI → Firestore 确认：`private/{uid}/secureKeys/noteKey` 只有一份；`users/{uid}/devices` 下有两台设备。

## 3. 测试清单

| # | 操作 | 预期 |
| --- | --- | --- |
| 1 | Android 新建笔记 → Sync now | Firestore `users/{uid}/notes/{id}`：有 `iv`/`ciphertext`，**无 `content`**，`schemaVersion: 6` |
| 2 | iOS 下拉刷新（fullSync） | iOS 显示 #1 的笔记，中文 / Emoji 正确 |
| 3 | iOS 新建笔记 → 回后台再回前台 | — |
| 4 | Android Sync now | Android 列表出现 #3 的笔记 |
| 5 | Android 删除 #3 的笔记 → Sync now | Firestore 该文档只剩 `isDeleted` + `updatedAt` |
| 6 | iOS 下拉刷新 | 该笔记从 iOS 消失 |
| 7 | 两端都改同一条笔记，iOS 先同步，Android 再 Sync now | Android 保留本地内容（本地未上传的改动优先）并记录冲突；Android 上传后 iOS 拉到 Android 的版本（后上传者赢） |
| 8 | Android 关网（模拟器飞行模式）新建笔记 → 开网 | 网络恢复后自动同步 |

记录：每步结果、截图、Firestore 文档内容。发现不一致时先看 Emulator UI 里的原始文档，判断是哪一端写错。

## 4. 已知限制

- Android 只有邮箱密码（DEBUG）登录；Google / Apple 登录等生产 `google-services.json` 后再做。
- Android 还没有图片同步、登出云（需要 Cloud Function）。

## 5. 测试记录（2026-10-28）

iOS：当天 `main`（`e4bb313`）加上 J1 那行，装在「KGS Interop iPhone 17」（iOS 27）上；Android：`3242377` 的 debug 包，装在 emulator-5554 上。两端登录同一个测试账号。

| 方向 | 操作 | 结果 |
| --- | --- | --- |
| Android → iOS | iOS 登录后完整同步 | ✅ 安卓建的笔记正文都能解密，中文正确。读经进度一致（路得记 1/4、士师记 1/21 排在前面）。创世记 1:3 锚点：黄色高亮位置正确，3 条感想（含安卓改过的文字），置顶记录 10/20–22、10/24–26（iOS 逐日显示）。已应允的祷告：26 日"Answered"日线，评论带 ★ 和 Joy 标签；安卓删掉的评论在 iOS 不出现 |
| iOS → Android | iOS：给祷告加中文 + Emoji 评论；标记路得记第 2 章；新建正文为"Philippians 4:6 be anxious…"的笔记；置顶一条祷告；删除一条安卓建的评论；然后回后台 | ✅ 安卓回前台自动拉取：评论文字正确；路得记 2/4，第 2 章显示为今天读；腓立比书 4:6 锚点（KJV 正文）+ 感想"be anxious for nothing from iOS"，拆分方式与安卓相同；祷告显示置顶，详情有 28 日的置顶记录；删除的评论消失。iOS 上传的删除标记同样只有 `isDeleted` + `updatedAt` |
| Android → iOS（修改） | 安卓：编辑 iOS 建的评论、给它打 2 星、取消置顶；然后回后台 | ✅ iOS 回前台增量拉取后：评论文字和 ★★ 正确，置顶已取消 |
| 两端 Today | 同一天的活动方块 | ✅ 完全一致：Pinned 1、Starred 1、Bible 1、Prayer 1、Answered Prayers 1、Chapter Read 1（当天给评论打星，两端都算进 Starred） |

§3 清单：1–4 已覆盖；5–6 这次删的是评论而不是笔记（删除标记格式相同）；7（冲突）、8（断网）这次没测。

其他：
- iOS 日志里偶尔有 `GOAWAY … too_many_pings`：本地模拟器嫌 gRPC 心跳太频繁，SDK 会自动重连，不影响结果。
- 在 iOS 模拟器里输入中文：`printf '…' | LANG=en_US.UTF-8 xcrun simctl pbcopy <UDID>`，再在输入框里粘贴；不设 UTF-8 会变成乱码。

## 6. 冲突、离线、密钥缓存过期（2026-11-10，Android 对 Android；iOS 未参与）

§3 的第 7（冲突）、8（断网）项补测。这次**只用 Android**：两台设备在 JVM 单元测试里模拟（各自一个 Room 数据库、一份共享的假 Firestore），再在 Android 模拟器 + Firebase 本地模拟器上走一遍真实界面。iOS 对应行为是读 iOS 代码得出的，没有在 iOS 上跑。

### 6.1 自动化测试（`./gradlew :app:testDebugUnitTest`）

| 测试类 | 覆盖 |
| --- | --- |
| `SyncOfflineAndConflictTest` | 两台设备改不同笔记 → 都保留、无冲突；改同一条 → 后拉取的设备记一条冲突，之后它的 push 覆盖云端（最后 push 的赢，和 iOS 相同），选"用对方版本"能找回；对方删除而本机在编辑 → 不算冲突，编辑把笔记带回来；断网 → 整轮同步什么都不上传，编辑和新笔记保持 dirty，网络恢复后上传，另一台设备拉到；上传中途失败 → 仍 dirty，重试成功；**密钥缓存过期 + 取不到密钥** → 新笔记不上传、别人的更新不套用、拉取游标原地不动，密钥回来后全部补上、游标前进；缓存未过期时离线编辑照常上传；缺密钥时发现的冲突，预览是占位文字，选"对方版本"时会重新读云端并解密 |
| `SyncConflictResolverTest` | 三种处理：保留本机（不需要网络，之后 push 覆盖云端）；用对方版本（不需要网络，写入本机并标 dirty，之后 push 让云端和另一台一致）；两者都保留（新笔记、新 id、不带图片，原笔记保留）；评论、高亮的处理；云端文档没了 / 没登录 → 报错且冲突保持未处理；同一版本不会重复报冲突；云端出现更新的版本才算新冲突 |
| `CloudResetNoticeViewModelTest` | "云端被重置"提示：标志置位 → 显示 → 点 OK 后清除，且仅显示不会清除 |

测试里的设备时钟跟随假服务器的时钟（真机的时钟和服务器相差通常不到秒）。默认的固定时钟比服务器慢 3 小时，会让每次"自己刚推上去的文档"都被误判成冲突。

### 6.2 模拟器上的实测（emulator-5554 + 本地 Firebase 模拟器）

默认端口 8080 当时被别人遗留的 Firestore 模拟器占着，所以自己另起了一套（auth 19099 / firestore 18080 / storage 19199），并给 `dev.properties` 加了端口覆盖项（`firebase.authPort` 等，见 `dev.properties.example`）。

| 场景 | 做法 | 结果 |
| --- | --- | --- |
| 冲突 | 登录测试账号，建笔记"Conflict test v1"并同步；用脚本把云端那条的密文换成"Conflict test REMOTE edit"（更新的 `updatedAt`，模拟另一台设备）；同时在手机上把本机那条改成另一段文字，然后点 Account → Sync now | ✅ 拉取时记下冲突；Settings → Sync 分区的"Sync Conflicts"行出现红色数字 1；点进去列表显示"Note / 本机文字 / 时间"，再点开底部面板，"本设备 / 其他设备"两段文字都对；选"使用其他设备的版本"后列表变成"暂无同步冲突"，随后推送，云端文字变成对方版本 |
| 云端被重置提示 | 把 `showCloudResetNotice` 写成 true 后启动 | ✅ 弹出"已登出"对话框（文案与 iOS 一致），点 OK 后标志清除 |
| 离线编辑 | 关 Wi-Fi（`svc wifi disable`），新建笔记，回后台；云端笔记数不变；开 Wi-Fi | ✅ 离线时云端不变；恢复后约 20 秒内云端多出这条笔记 |
| 密钥缓存过期 + 离线 | 把 `note_key_session` 里该账号的 `expiresAt_…` 改成 1（相当于缓存已过 12 小时），关 Wi-Fi 启动，新建笔记，回后台；再开 Wi-Fi | ✅ 离线时 Account 显示 "Note key: locked"，笔记留在本机、云端不变；恢复网络后重新取到密钥（"unlocked"，缓存重新写入 12 小时），笔记上传（云端笔记数 +1） |

没有在模拟器上测：真正等满 12 小时的过期（`KeystoreNoteKeyCache` 依赖 Android Keystore，JVM 测试跑不了，用改 `expiresAt` 代替）；两台**真**设备同时编辑（用"脚本改云端密文 + 本机编辑"代替）；iOS 参与的冲突。

### 6.3 发现的问题

**Android（已修）**

1. **"使用其他设备的版本"没有东西可用。** 拉取跳过本机 dirty 的行并记下冲突，紧接着的 push 会把本机版本传上去，覆盖云端那份；等用户选"使用对方版本"时，如果像 iOS 那样去云端重新读，读到的只是本机自己的文字。现在记冲突时把对方的完整文字存在冲突记录里（`remotePreview`），"对方版本"/"两者都保留"直接用它：本机行换成对方文字并标 dirty（push 之后云端和对方设备也一致），或新建一条带对方文字的笔记。缺密钥时存的是占位文字，处理时会重新读云端并解密。高亮/下划线没有文字可存，仍然重新读云端（和 iOS 一样）。
2. **处理过的冲突会再冒出来。** 增量拉取用 `>=` 游标，会重读游标处的那份云端文档；用户选完"保留本机"，本机行还没 push 前，下一次拉取又会把同一版本记成新冲突。现在如果这个版本（`remoteUpdatedAt` 不晚于已处理冲突里的）已经处理过就不再记；云端出现更新的版本才算新冲突。
3. （测试环境）`FakeRemoteStore` 缺单文档读取，已补 `RemoteStore.fetchDocument`。

**iOS（只列出，没改）**

- **(a) `SyncConflictResolver` 的 `keepRemote` / `createCopy` 重新读云端文档，但此时云端多半已被本机 push 覆盖**（`CloudSync` 拉取后立刻推送 dirty 行），所以读到的是本机自己的文字，"使用其他设备的版本"等于什么都没变，"创建副本"得到一份和原文相同的副本。冲突记录里其实存了 `remotePreview`，但那只是显示用的短文本快照。建议像 Android 这样把对方的内容存下来，或者让 push 跳过有未处理冲突的行（后者要注意时钟偏差造成的误报冲突会永久卡住 push，所以 Android 没这么做）。
- **(b) 处理完不会抑制重复检测**，同 Android 修复的第 2 条；`SyncConflictDetector` 只和 `lastSyncedAt` 比较，选完"保留本机"后到下次 push 之间可能再生成一条一模一样的冲突。
- **(c) 冲突判断拿服务器的 `updatedAt` 和设备时钟写的 `lastSyncedAt` 比较**，设备时钟比服务器慢时，自己刚推上去的文档在下一次增量拉取（游标重读）里也会被当成"别的设备改了"，只要这一行在两次同步之间又被编辑过。iOS 因为 push 照常覆盖，只是多一条冲突记录；Android 同样存在，没有改动判断规则（会削弱真冲突的检测）。
