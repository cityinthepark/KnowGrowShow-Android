# 订阅 + 登录/登出 + 云端数据保留（iOS / Android / Web 共用）

制定：2026-11-10。**后端已在本机模拟器搭好并演示通过；iOS、Android 客户端代码还没改**（等你点头）。
后端代码在 `KnowGrowShow-Firebase/functions/`，规则在该仓库 `firestore.rules`（iOS 的 `emulator/firestore.rules` 已同步一份）。

## 1. 已定的决定（2026-11-10 逐条与你确认）

| # | 决定 |
|---|------|
| 1 | **登出只退出本机，不动云端**。云端数据、其他设备都不受影响，不再把其他设备踢下线。 |
| 2 | **删除账号保留为立即彻底删除**（云端数据、缩略图、笔记密钥、订阅记录、Firebase 账号，并通知 RevenueCat 删该用户）。 |
| 3 | **订阅到期后保留 30 天**；超过 30 天仍未续费，由 Cloud Function 清空云端数据。 |
| 4 | **只删数据**：保留 Firebase 账号（邮箱/Google/Apple 登录）和 `private/{uid}/secureKeys/noteKey`。理由：笔记密钥删了再生成，其他设备缓存的旧密钥就和新上传的内容对不上。 |
| 5 | **到期后完全停止同步**（不做只读）。到期当天先推送一次提醒，之后云端不再读写；本机数据照常能用。 |
| 6 | **提醒**：到期时、第 20 天、第 28 天，**服务器推送（FCM）为主**，App 内横幅 + 设置里的「通知」开关；邮件是可选的补充。 |
| 7 | 订阅文档放顶层 **`subscriptions/{uid}`**（不放 `users/{uid}/meta/`）。 |
| 8 | 订阅状态来源用 **RevenueCat**（以后可换，见 §8）。 |
| 9 | **这一步不加付费墙**：没有订阅文档的账号 = "还没上订阅"，永远不清理，同步照常（老用户、现有 3 个用户不受影响）。 |
| 10 | 清理后用户续费：设备发现"云端被清空"时**不登出，直接把本机数据重新上传**（仅订阅有效时）。 |

## 2. 为什么订阅文档放 `subscriptions/{uid}`

- 现有规则 `users/{uid}/{document=**}` 允许本人读写整棵树，放在 `users/{uid}/meta/` 里用户就能自己把状态改成"有效"，要改写那条规则才能防住。
- 到期清理会清空 `users/{uid}`，订阅记录要保留（记录 `purgedAt`、已发过的提醒）。
- 扫描"该清理的人"只需一个单字段查询，不需要集合组索引。

规则（线上与 iOS 模拟器各一份，已加）：

```
match /subscriptions/{userId} {
  allow read: if request.auth != null && request.auth.uid == userId;
  allow write: if false;      // 只有 Cloud Function（Admin SDK）能写
}
```

## 3. 数据：`subscriptions/{uid}`

| 字段 | 含义 |
|------|------|
| `status` | `active` / `expired`。**没有文档 = 从未订阅，永不清理** |
| `expiresAt` | 当前周期到期时间（商店给的） |
| `willRenew` | 是否自动续费（取消续费后仍 `active`，直到 `expiresAt`） |
| `billingIssue` | 扣款失败（商店在重试期） |
| `store` / `productId` | `app_store` / `google_play`；商品 id |
| `expiredAt` | 变成 `expired` 的时间（= 商店给的到期时间），30 天从这里算起 |
| `purgeAfter` | `expiredAt + 30 天`；仅 `expired` 且未清理时有值，否则 `null` |
| `purgedAt` | 云端数据被清空的时间；续费后清回 `null` |
| `lastPurgedAt` | 最近一次被清空的时间，续费后保留，方便客户端知道"云端被清过" |
| `notified.{expired,day20,day28}` | 各档提醒发出的时间（每档只发一次） |
| `lastEventAt` / `lastEventType` / `updatedAt` | 防乱序/重复事件；调试用 |

客户端的读法：`active` 或没有文档 → 同步照常；`expired` 且 `purgedAt == null` → 停止同步 + 横幅（倒计时用 `purgeAfter`）；`expired` 且已清理 → 停止同步 + 横幅"云端备份已清除，续订后本机数据会重新上传"。

## 4. 流程

```
RevenueCat ──webhook──▶ revenueCatWebhook ──▶ subscriptions/{uid}
                              │ (EXPIRATION 时立刻发"已到期"推送)
每天 subscriptionRetentionDaily ──▶ ① 第 20 / 28 天提醒  ② purgeAfter 已过的人：清空云端数据
```

- **webhook**（`revenueCatWebhook`）：HTTP，要求 `Authorization` 头等于密钥 `REVENUECAT_WEBHOOK_AUTH`（本地在 `functions/.secret.local`，上线用 `firebase functions:secrets:set`）；错误密钥 401、非 webhook 400；**主动忽略的事件（TEST、TRANSFER、匿名用户、过期的旧事件）也返回 200**，否则 RevenueCat 会反复重试。事务内处理，重复/乱序事件安全。
- **事件 → 状态**：`INITIAL_PURCHASE / RENEWAL / PRODUCT_CHANGE / UNCANCELLATION / NON_RENEWING_PURCHASE` → `active`（并清掉 `purgeAfter`、提醒记录）；`CANCELLATION / SUBSCRIPTION_PAUSED` → `willRenew=false`（到期前仍有效；退款导致到期时间已过则立即 `expired`）；`BILLING_ISSUE` → 只标记；`EXPIRATION` → `expired`，`purgeAfter = 到期时间 + 30 天`。
- **清理**（`purgeExpiredSubscriptions`）：查 `purgeAfter <= 现在`；删之前**再读一次**状态，已续费就跳过；删 `users/{uid}` 整棵树（所有集合 + `devices` + `meta`/账号 epoch）和 Storage `users/{uid}/` 下全部文件；然后记 `purgedAt`。**保留** Auth 账号、`private/{uid}/secureKeys/noteKey`、`subscriptions/{uid}`。一次最多处理 200 人，幂等。
- **提醒**（`sendExpiryReminders`）：若任务停摆，只发最新的一档，较早的标记为已发。推送文案用本地化 key（`subscription_{expired|day20|day28}_{title|body}`，body 带"还剩几天"参数），服务器不需要知道用户语言。推送发到 `users/{uid}/devices/{deviceId}.fcmToken` 里有令牌的设备。
- **定时**：线上 `subscriptionRetentionDaily`（每 24 小时；**部署需要 Blaze 计划**）。**模拟器不会按时钟跑定时函数**，所以另有仅模拟器可用的 `debugRunSubscriptionJobs`（线上返回 403），`?now=<ISO 时间>` 可拨时钟，`?job=purge|reminders|both`。

## 5. 在模拟器里演示

```bash
# 1) 起模拟器（会先编译 functions；Auth 9099 / Firestore 8080 / Storage 9199 / Functions 5001）
KnowGrowShow/Scripts/start-firebase-emulators.sh
# 2) 另开一个终端
KnowGrowShow-Firebase/functions/scripts/subscriptionDemo.sh
```

脚本会：注册账号 → 写 5 个文档 + 1 张缩略图 → 用 curl 模拟 RevenueCat：购买 → 到期 → 拨时钟到第 21、29 天（各收到一次提醒）→ 第 31 天清理 → 确认云端数据为 0、Auth 账号和笔记密钥还在 → 再跑一次是空操作 → 客户端写 `subscriptions` 被拒（403）→ 续费后恢复 `active`。

**2026-11-10 实测结果**（用独立端口的临时模拟器跑；脚本支持 `AUTH_PORT/FS_PORT/ST_PORT/FN_PORT` 覆盖）：Firestore 文档 5 → 0，Storage 文件 1 → 0，Auth 账号仍在，`noteKey` 仍在，`subscriptions/{uid}` 记 `purgedAt`，`notified` 含 `expired/day20/day28`；规则：读自己 200、写自己 403、读别人 403。状态机另有 14 条单元测试（`cd functions && npm run build && node --test lib/subscriptionState.test.js`，全部通过）。

⚠️ **没有验证的部分**：①模拟器不能真的发推送，`deliverReminder` 在模拟器里只记日志（线上走 `admin.messaging().sendEachForMulticast`，代码未在真机验证）；②RevenueCat 的字段名（`app_user_id`、`expiration_at_ms`、`event_timestamp_ms`、`store` 等）按它官方文档写的，没连过真的 RevenueCat（上线前用它后台的 "Send test event" 和沙盒购买验一遍）；③iOS 的 `firebase.json` + 启动脚本只做了语法检查，我测试用的是同样配置的临时副本（默认端口当时被别的模拟器占着）。

## 6. 客户端要改什么（**还没动，等你点头**）

### 两边都要

1. **登出**：只做本机退出（Android 已有 `AccountService.signOutKeepingLocalData`，它保留同步进度和删除记录，所以重新登录能接着同步）；顺手删掉本机在 `users/{uid}/devices/` 的那条记录和 `fcmToken`。去掉"登出并清空云端"（Android：`AccountService.signOutOfCloud` / `wipeCloud` / `pendingWipeUid` 一整套；`AccountViewModel`、`AccountScreen` 的文案和提示）。
2. **读 `subscriptions/{uid}`**（监听）：按 §3 的读法停止/恢复同步；Account 页显示订阅状态；到期后显示横幅和倒计时。**没有文档按"未上订阅"，同步照常。**
3. **云端被重置的处理**（Android `CloudSync.reconcileAccountEpoch`，iOS `AccountEpoch` 对应处）：订阅 `active` 时检测到重置 → 不登出，把本机所有数据标记为重新上传（相当于 `markAllUserScopedDataForResync`，但不走 `finishLocalSignOut`）；订阅状态未知/其他原因的重置仍按现在的方式处理。**重传前先清掉本机的"已同步墓碑"**（对应 `purgeAllTombstonesForCloudWipe`，云端已空，墓碑没有意义）。**重传不会产生重复**：每个文档 id 都是稳定 id，同 id 整文档覆盖（见 FIRESTORE_SYNC_CONTRACT §1、§2）；多台设备各自重传同样的 id 只会互相覆盖，后 push 的赢。原因：清理会连 epoch 一起删，续费后每台设备都会判定"云端被重置"，不改的话每台设备都会被登出。
4. **设置 → 通知**：一个总开关；打开时才请求系统通知权限、取 FCM 令牌，写进 `users/{uid}/devices/{deviceId}.fcmToken`；关闭/登出时删除。加 6 条本地化文案（`subscription_{expired|day20|day28}_{title|body}`，三种语言），和 §4 的 key 一致。
5. **RevenueCat**（做付费墙那一步时才接）：Firebase 登录成功后 `Purchases.logIn(firebaseUid)`，登出时 `logOut()`；**不要用匿名 id**（匿名用户的事件会被 webhook 忽略）。把购买流程放在自己的 `SubscriptionProvider` 接口后面，其他代码只读 `subscriptions/{uid}`（换服务商的成本因此很低，见 §8）。
6. **"删除账号"**：新增后端函数（见 §7 待办）后接界面，提示"删除账号不会取消订阅，请到 App Store / Google Play 自己取消"。

### 只有 iOS

- `CloudDataWipeService` / "登出云"流程改为本机登出；**iOS 现在登出会清云端并让其他设备掉线，改完不会了**，这是行为变化，Account 页文案要同步改。
- iOS 的 `emulator/firestore.rules` 已加订阅规则；`firebase.json` 已加 functions 配置（source 指向 `../KnowGrowShow-Firebase/functions`），`Scripts/start-firebase-emulators.sh` 改为 `--only auth,firestore,storage,functions` 并在启动前编译 functions。

## 7. 还没做 / 待办

- **`deleteAccount` 函数**（立即彻底删除）：调用者自己的 uid，删 `users/{uid}`、Storage、`private/{uid}`、`subscriptions/{uid}`、Auth 账号，并请求 RevenueCat 删除该用户（具体接口上线前查 RevenueCat 文档确认）。现有的 `endCloudSession`（删笔记密钥 + 让其他设备掉线）从此**不再用于登出**，只适合并入这个函数。
- 邮件提醒（可选）：只给真实邮箱发；Apple "隐藏邮件" 的 `@privaterelay.appleid.com` 地址需先在 Apple 开发者后台登记发信域名/邮箱，否则收不到，建议跳过这类地址。要接邮件服务（如 Firebase 的 Trigger Email 扩展）。
- 上线前：升级 Blaze；`firebase functions:secrets:set REVENUECAT_WEBHOOK_AUTH`；RevenueCat 后台配 webhook 地址（`https://us-central1-knowgrowshow-d3609.cloudfunctions.net/revenueCatWebhook`）和同一个 `Authorization` 值；`subscriptionRetentionDaily` 随部署一起上线。**`functions/README.md` 写着不要整体 `firebase deploy --only functions`（会和 YouTube 抓取双写），部署时用 `firebase deploy --only functions:revenueCatWebhook,functions:subscriptionRetentionDaily` 指定函数。**
- **隐私政策**要写明：订阅由 RevenueCat（数据处理方，数据存美国 AWS）处理，会收到 Firebase 用户 id、设备类型/系统版本、商店购买凭证，**不含邮箱和笔记内容**；订阅到期后云端数据保留 30 天再删除，账号和加密密钥保留；删除账号立即删除全部；App Store 隐私问卷和 Google Play 数据安全表同步填。RevenueCat 自己的数据保留规则（账号关闭后 6 年）也要了解。
- 以后若要让服务器强制拦截到期用户的写入（现在只靠客户端停同步），可在规则里读 `subscriptions/{uid}`，每次读写多一次文档读取，暂不做。

## 8. RevenueCat 备忘（2026-11-10 查的，数字以官网为准）

- 每月追踪收入 2,500 美元以内免费，超过按 **1%**（是按全部金额还是只算超出部分、是否按商店抽成前计算，官网没写清，上线前确认）。
- 备选：Adapty（5,000 美元免费，之后 1%）、Apphud（约 1 万美元免费，未核实官网）、Qonversion（约 7,000～1 万免费，之后 0.6%～0.8%），或直接接 App Store Server Notifications V2 + Google Play 实时通知（无第三方费用，工作量大很多）。
- 换服务商：我们这边的 `subscriptions/{uid}`、清理、提醒、规则、客户端读法都不变，只换"客户端购买 SDK"和"webhook 解析"（`parseRevenueCatBody`，一个函数）；真正的成本是迁移已有订阅者，用户越多越麻烦，所以早换比晚换便宜。过渡期可以两家同时往同一个文档写（取到期时间更晚的）。
- 隐私/安全（官方页面）：SOC 2 Type II；传输用 TLS；数据在美国 AWS；是终端用户数据的"处理方"，DPA 声明不出售/共享（按 CCPA 定义）；分包商清单里有 OpenAI、Anthropic，介意可在正式使用前询问处理的是哪些数据。

## 9. 已知局限（有意取舍）

- **晚到的到期通知会缩短提醒窗口**：30 天从商店给的到期时间算起，不是从我们收到 webhook 的时间算起。
- **漏掉 EXPIRATION 事件**（RevenueCat 自己会重试 5xx）会让该用户一直是 `active`，不会提醒也不会被清理——失败方向是"不删"，是安全的；需要时可加"`active` 但 `expiresAt` 早已过期"的对账。
- 第 28 天提醒只在第 28～30 天之间有一次定时运行的机会；每天一次，所以一定会赶上，但具体几点不固定。
- 同一个 Apple/Google 订阅换 Firebase 账号登录，RevenueCat 会触发 `TRANSFER`，现在忽略该事件；做付费墙时要定规则（订阅跟人走，还是跟 Firebase 账号走）。
- **清理后已删除的内容可能"复活"**：云端的删除记录（墓碑）也被清掉了。设备 A 在到期前删了某条笔记并同步，设备 B 当时没拉到、仍留着它；续费后 B 重传，这条笔记会回来。现在的"登出云"流程也有同样的问题，不是新引入的；要根治需要另外设计（例如到期时让各设备停止同步前最后拉一次）。
- **内容相同但 id 不同的不会去重**（例如两台设备各自新建了一条看起来一样的笔记；契约里"感想 Note 不去重"）。
- 只考虑"一个订阅商品 = 一个权益"；多档位/家庭共享要再扩展。
- Firebase 仓库里 `getNoteEncryptionKey`、`provisionKeyOnSignup`、`endCloudSession` 等至今**未提交**（`git status` 显示 untracked / modified，是别的会话的改动），本次的新文件也都**没有提交**；`admin.ts`（加了 Storage 桶）、`index.ts`、`firestore.rules`、`.secret.local.example` 是在别人未提交改动的基础上追加的几行。
