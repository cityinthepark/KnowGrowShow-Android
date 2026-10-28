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
