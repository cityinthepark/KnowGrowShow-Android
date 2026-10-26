# iOS ↔ Android 互通测试（Firebase 本地模拟器）

全程在本机：Firebase Local Emulator Suite（Auth + Firestore + Storage）+ iOS 模拟器 + Android 模拟器。
不需要 Blaze，不碰生产数据。

## 0. 一次性准备

- Android：`cp dev.properties.example dev.properties`，填 `demoAuth.email` / `demoAuth.password`
  （只用于模拟器的测试账号，不要用真实密码）。`dev.properties` 已被 gitignore。
- iOS：Xcode → Edit Scheme → Run → Environment Variables（**只在本机改，不要提交**）：
  - 勾选 `FIREBASE_USE_EMULATORS = 1`
  - 勾选 `FIREBASE_EMULATOR_HOST = 127.0.0.1`
  - `KGS_DEMO_AUTH_EMAIL` / `KGS_DEMO_AUTH_PASSWORD` 改成和 Android `dev.properties` **同一个**测试账号

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
- Android 还没有图片同步、登出云（需要 Cloud Function）、正式的笔记界面——本测试只看笔记正文。
