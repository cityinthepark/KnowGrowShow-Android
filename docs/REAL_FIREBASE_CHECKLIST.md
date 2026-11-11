# 接入真实 Firebase 的清单（2026-11-10）

到目前为止，Android 的所有云端验证都在 **Firebase 本地模拟器**上做（`dev.properties` 里 `firebase.useEmulators=true`）。
**没有任何一处连过真正的 Firebase 项目 `knowgrowshow-d3609`**：`app/google-services.json` 不存在，真实登录、
FCM 推送、云函数部署都没验证过。这份清单说明要你做什么、Android 这边还差什么代码、怎么逐步验证。

> 本文只是清单：没有部署任何东西，没有创建 / 提交 `google-services.json`（它在 `.gitignore` 里，别提交）。

## 0. Android 现在怎么决定连谁（读 `FirebaseBootstrap.kt` + `app/build.gradle.kts` 的结论）

| 情况 | 结果 |
|------|------|
| 没有 `google-services.json`，也没开模拟器开关 | `configureIfNeeded()` 返回 false，App 只能本机使用（和 iOS 没有 plist 时一样）。**现在的 release 构建就是这样。** |
| 有 `google-services.json`（构建时才会应用 google-services 插件，`app/build.gradle.kts:16`） | `FirebaseApp.initializeApp(context)` 读它，连生产项目。 |
| debug 构建 + `dev.properties` 里 `firebase.useEmulators=true` | 用占位 app id / key 连 Mac 上的模拟器（`firebase.emulatorHost`，默认 `10.0.2.2`；端口默认 9099/8080/9199，可用 `firebase.authPort` / `firestorePort` / `storagePort` 改）。**即使放了 `google-services.json`，只要这个开关开着就仍然连模拟器**，所以连真实项目时要把它关掉（或删掉 `dev.properties`）。 |
| release 构建 | 模拟器开关、演示账号一律为空（`app/build.gradle.kts:50-54`），不可能连模拟器。 |

只有用户在欢迎页选"注册"、或在 Profile → Account 里登录时才会启动 Firebase；"只在本机使用"的用户永远不碰 Firebase。

## 1. 需要你做的（控制台 / 账号）

按顺序做，前面的是后面的前提。

1. **在 Firebase 项目里加 Android 应用**
   控制台 → 项目 `knowgrowshow-d3609` → 项目设置 → 添加应用 → Android。包名填 **`com.stoneofhope.stoneofhope`**（`app/build.gradle.kts` 的 `applicationId`；debug 与 release 同一个包名，没有后缀）。
2. **填 SHA 指纹**（Google 登录必需，缺了会报 `DEVELOPER_ERROR` / code 10）。在应用设置 → "SHA 证书指纹" 里添加：
   - **debug**：`./gradlew :app:signingReport` 看 `debug` 变体，或 `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android`。这台 Mac 的 debug 钥匙当前是
     SHA-1 `46:AB:85:F9:12:28:4D:83:5B:FD:24:C5:AE:BB:45:AB:75:32:5C:99`，
     SHA-256 `82:94:09:DB:A9:B4:E0:72:97:B7:E9:61:FC:B2:B2:8C:E6:93:64:97:36:CB:E6:D9:35:71:0F:36:9D:BE:5D:07`（换电脑 / 重装 Android Studio 会变，以命令输出为准）。
   - **release / 上传钥匙**：`keystore.properties` 指向的那把 keystore（`keytool -list -v -keystore <文件> -alias <别名>`）。
   - **Play App Signing**（上架 Google Play 后）：Play Console → 应用完整性 → 应用签名密钥证书的 SHA-1 / SHA-256，**也要加**，否则从商店装的版本登不了 Google。
3. **下载 `google-services.json`**，放到 `app/google-services.json`（不要提交；`.gitignore` 已忽略）。
   **顺序提醒**：先做完第 2 步和第 4 步，再下载。Google 登录需要的 Web 客户端 ID（`default_web_client_id`）是在"SHA 已登记 + Google 登录提供方已启用"之后才会写进这个文件里的；之前下载的文件里没有，要重新下载。
4. **启用登录方式**：Authentication → Sign-in method → **Google** 打开（填项目支持邮箱）。iOS 已有同一项目，Apple 登录是 iOS/Web 的事，Android 暂不做。
5. **升级 Blaze**（按量付费）。云函数部署、定时任务、Secret 都需要；Firestore / Auth / Storage 本身不需要。详见 `KnowGrowShow-Firebase/functions/README.md` §8。
6. **Firestore / Storage 规则**：生产规则在 `KnowGrowShow-Firebase` 仓库（`firestore.rules`、`storage.rules`）。Android 依赖其中这些路径，部署前先确认它们是线上的最新规则：
   - `users/{uid}/**`（用户数据，本人读写）；
   - `private/{uid}/secureKeys/noteKey`（本人可读 / 创建，不可改 / 删；Android 现在直接读写它，见 `FirestoreNoteKeyProvider`）；
   - Storage `users/{uid}/noteImages/{noteId}/{fileName}`（笔记图片缩略图）；
   - `subscriptions/{uid}`（订阅方案新增，见 `SUBSCRIPTION_RETENTION_PLAN.md` §2；只读不可写）。
   规则从 `KnowGrowShow-Firebase` 部署，**不要**从 iOS 或 Android 仓库部署。

### 1.1 部署哪些函数（只部署指定函数）

依据 `docs/SUBSCRIPTION_RETENTION_PLAN.md` §7 和 `KnowGrowShow-Firebase/functions/README.md`：**不要整体 `firebase deploy --only functions`**（会和现有的 YouTube 抓取双写）。需要上线、且与 Android 有关的是：

| 函数 | 作用 | Android 是否依赖 |
|------|------|------------------|
| `provisionKeyOnSignup` | 新用户注册时服务器端建 `private/{uid}/secureKeys/noteKey` | 不强依赖：Android 登录后也会用事务自己创建（`fetchOrCreate`），规则保证只能建一次。上线后两边谁先到谁赢，结果一致。 |
| `revenueCatWebhook` | RevenueCat → `subscriptions/{uid}` | 订阅功能上线时需要 |
| `subscriptionRetentionDaily` | 每天：到期提醒（FCM）+ 到期 30 天后清空云端数据 | 订阅功能上线时需要 |
| `endCloudSession` / `getNoteEncryptionKey` | 旧的"登出云"/读密钥入口 | Android 当前**没有调用**（`AccountService` 里只有注释提到 `endCloudSession`）；订阅方案还要把登出改成只退出本机，所以先别为 Android 部署 |
| `debugRunSubscriptionJobs` | 仅模拟器 | 不部署 |
| `addNewYoutubeChannel` / `pollYoutubeChannels` | YouTube 抓取 | 与本任务无关，已有自己的流程 |

建议的命令（你来执行；**我没有执行**）：

```bash
cd ~/Documents/Development/KnowGrowShow-Firebase
npm --prefix functions run build
firebase functions:secrets:set REVENUECAT_WEBHOOK_AUTH        # 同一个值要填到 RevenueCat 后台的 webhook Authorization
firebase deploy --only functions:revenueCatWebhook,functions:subscriptionRetentionDaily
# 如要让 provisionKeyOnSignup 也上线： --only functions:provisionKeyOnSignup,... （逗号分隔，不要省略 functions: 前缀）
firebase deploy --only firestore:rules,storage                 # 规则确认无误后；与函数分开部署，方便回滚
```

RevenueCat 后台 webhook 地址：`https://us-central1-knowgrowshow-d3609.cloudfunctions.net/revenueCatWebhook`。

## 2. Android 这边还要改的代码 / 配置

| # | 改动 | 说明 |
|---|------|------|
| 1 | **Google 登录** | 现在 Register 页的 "Continue with Google" 只弹提示 `account_google_unavailable`（`AccountScreen.kt:306`），没有真正实现。要加 Credential Manager + Google ID（`androidx.credentials`、`androidx.credentials:credentials-play-services-auth`、`com.google.android.libraries.identity.googleid`），用 `R.string.default_web_client_id`（google-services 插件从 JSON 生成）拿 ID token，再 `GoogleAuthProvider.getCredential` → `auth.signInWithCredential`；在 `AccountService` 里和 `signInWithEmail` 一样先 `settlePendingWipeBeforeSignIn()`。账号合并规则按 iOS `SocialRegistrationService`。 |
| 2 | **FCM 推送** | 现在没有任何 FCM 代码，`libs.versions.toml` 里也没有 `firebase-messaging`。订阅方案 §6 要求：加依赖 + `FirebaseMessagingService`（`onNewToken` 写 `users/{uid}/devices/{deviceId}.fcmToken`）、通知渠道、Android 13+ 的 `POST_NOTIFICATIONS` 运行时权限、Settings → 通知 开关（打开才请求权限 / 取令牌，关闭或登出删令牌）、6 条三语言文案（`subscription_{expired|day20|day28}_{title|body}`）。`AndroidManifest.xml` 现在没有声明 `POST_NOTIFICATIONS`。 |
| 3 | **订阅状态读取** | 读 `subscriptions/{uid}`：`expired` 就停止同步 + 横幅（订阅方案 §3、§6）。云端被清空后的"重置"处理也要按方案 §6-3 改（`CloudSync.reconcileAccountEpoch`）。 |
| 4 | **release 签名** | 需要 `keystore.properties`（已 gitignore；`release` 配置读它）。没有它 release 构建无法签名。 |
| 5 | **ProGuard / R8** | 现在 `isMinifyEnabled = false`。上架前若开启混淆，要补 Firebase / Room / Hilt 的 keep 规则并重测登录与同步。 |
| 6 | **关掉模拟器开关** | 连真实项目的那次调试，把 `dev.properties` 里 `firebase.useEmulators` 改成 `false`（或临时改名该文件），否则仍连模拟器。 |
| 7 | **debug 的明文网络例外** | `app/src/debug/res/xml/network_security_config.xml` 只在 debug 里放行 10.0.2.2 / localhost 明文；真实 Firebase 都是 HTTPS，不需要改。 |

## 3. 怎么验证（建议顺序，每步通过再做下一步）

> 全程在测试账号上做；先别用真实用户的账号。

1. **构建与启动**：放好 `google-services.json`、关掉模拟器开关，`./gradlew :app:installDebug`。logcat 过滤 `FirebaseBootstrap`：应该**没有** "Firebase emulator …" 那行；Account 页底部的 "Developer (debug build)" 区不再显示 "Firebase emulator · …"。
2. **登录**（实现 Google 登录后）：Profile → Account → Register → Continue with Google。成功后 Account 页显示邮箱、"Linked devices" 里出现本机，Firestore 控制台 `users/{uid}/devices/{deviceId}` 有一行。失败码 10 = SHA 没登记或没重新下载 JSON。
3. **笔记密钥**：Firestore 控制台出现 `private/{uid}/secureKeys/noteKey`；Account 页 "Note key: unlocked"。如果 `provisionKeyOnSignup` 已部署，这个文档是服务器建的；否则是 Android 事务建的（`createdAt` 一致即可）。
4. **同步**：建一条笔记 → 切后台 → Firestore 控制台里 `users/{uid}/notes/{id}` 只有 `iv` / `ciphertext`，没有明文（对照 `docs/INTEROP_TEST.md` 的密文检查）。
5. **与 iOS 互通**：同一账号在 iOS 真机 / 模拟器（指向真实项目）登录，重复 `docs/INTEROP_TEST.md` §5 的三个方向，再补 §6 的冲突 / 离线两项。
6. **规则**：用第二个账号去读第一个账号的 `private/{uid}/...` 与 `users/{uid}/...`，应当 `permission-denied`；对 `noteKey` 做 update / delete 也应被拒绝。
7. **FCM**（实现后）：Settings → 通知 打开 → Firestore 里该设备出现 `fcmToken`；在 Firebase 控制台 Cloud Messaging → "发送测试消息"（填令牌）能在设备上弹出；关闭开关令牌被删。
8. **订阅函数**（部署后）：用 RevenueCat 沙盒用户触发一次 INITIAL_PURCHASE / EXPIRATION，看 `subscriptions/{uid}` 变化；`subscriptionRetentionDaily` 先在模拟器里用 `debugRunSubscriptionJobs?now=` 跑过（订阅方案 §5），再看线上日志。
9. **Storage**：建一条带图片的笔记，控制台 Storage 里出现 `users/{uid}/noteImages/{noteId}/…`；另一台设备能下载缩略图。
10. **回退**：任何一步异常，把 `google-services.json` 移走 / 开回模拟器开关即可回到纯本机 + 模拟器的状态，不影响本机数据。

## 4. 本次没验证 / 做不到的

- 没有连过真实 Firebase、没有 Blaze、没有部署函数、没有 FCM、没有 Google 登录（代码都还没有，见 §2）。
- SHA 指纹只列了这台 Mac 的 debug 钥匙；release / Play 签名指纹需要你提供。
