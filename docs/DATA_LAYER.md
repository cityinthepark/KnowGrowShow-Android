# Android data layer (Phase 1)

Source of truth for the wire format stays in the iOS repo:
`docs/FIRESTORE_SYNC_CONTRACT.md`, `docs/CRYPTO_SYNC_CONTRACT.md`, `docs/USER_VERSE_CONTRACT.md`.
Where a contract and the iOS code disagree, this port follows the **iOS code**
(`FirestoreUserDataPush.swift` / `FirestoreUserDataPull.swift`), because that is what is in
production. Known example: tombstones on the wire are `{isDeleted: true, updatedAt}` only
(no `deletedAt`), and the note key lives at `private/{uid}/secureKeys/noteKey`
(CRYPTO_SYNC_CONTRACT §2.3/§3 still show the old `users/{uid}/secure_keys/note_key` path).

## Layout

| Package | What |
| --- | --- |
| `data.crypto` | `StoneOfHopeCrypto` — AES-256-GCM, byte-compatible with iOS (contract §6 vectors in tests) |
| `data.model` | Wire enums (`NoteType`, `MarkStyle`, …) and `ObjectIds` |
| `data.local` | Room: entities (one per iOS Realm class), DAOs, `AppDatabase`, Hilt `DatabaseModule` |
| `data.remote.wire` | SDK-free Firestore mapping: `WireEncoder` (push), `WireDecoder` (pull), `NoteBodyCodec` |
| `data.remote.firestore` | `FirestoreWireAdapter` — the only code that touches Firestore SDK types |
| `data.bible` | Book table (generated from iOS), name matching, `BibleReferenceValidator` (field), `BibleReferenceParser` (note-body shortcut, `vN`), `BibleFormatter`, `BibleVersions`, `BibleAnchor` |
| `data.chapter` | Chapter page: `ChapterNotesBuilder` (chapter notes and verse groups, iOS `ChapterNoteViewModel` / `ChapterNoteDetailsView`), `ChapterSermons` |
| `data.sermon` | Sermons, videos, series: `SermonRepository`, `VideoRepository`, `SermonReferenceParser`, `YouTubeLinkParser`, YouTube title / transcript parsing, `YouTubeWeb` (oEmbed + captions), `SermonRules`, `SeriesList` |
| `data.church` | Online resources / church import (docs/CHURCH_IMPORT_PLAN.md): `ChurchProfile` + `ChurchProfileStore` (Firestore `onlineResourceProfiles` over REST — no SDK, no sign-in, so local-only users read it too — cached, bundled `assets/churches/` fallback), `ChurchSermonImporter` (iOS `ChurchSermonImportService`, repo `manifest.json` → `isUserData = false` series / sermons that never sync), `ChurchFeed` (HTTP GET), `OnlineChurches` ("My churches" in Room `onlineChurches`: first import becomes primary, one primary at a time) |
| `data.bible.text` | `BibleTextDatabase` — read-only bundled KJV/CUV (Room `createFromAsset`), `BibleVerseReader` (iOS `BibleVerseRangeReader`), `OnDemandVerseSource` (NIV/NKJV; not implemented) |

Room is the only store the UI reads. Firestore is a transport; local-only use never touches it.

`AppDatabase` schema changes go through `data.local.Migrations` (version 2 added `onlineChurches`);
`MigrationTest` builds each old version from its exported `app/schemas/…AppDatabase/<n>.json` and
lets Room migrate and validate it.

Bundled Bible text is a second, read-only Room database built at compile time from `app/bible-data/`
(buildSrc `GenerateBibleTextDb`, schema from `app/schemas/…BibleTextDatabase/`). Its verse text is
byte-identical to iOS `DownloadedVerse.verseContent` (checked for all 62,202 verses), because it is
frozen into synced `userVerses`. `AppDatabase.downloadedVerses` stays for future downloaded versions.

## Storage decisions

- **Ids** are 24-char lowercase hex strings (`org.bson.types.ObjectId`); `youtubeVideos` uses the
  video id, `userVerses` the compound `{versionAbbr}_{bookOrd}_{chapterOrd}_{verseOrd}`.
- **Timestamps** are `java.time.Instant`, stored as epoch **microseconds** (Firestore precision),
  so a timestamp pulled from iOS and pushed back is unchanged.
- **Relationships** are stored as they travel on the wire: `tags: List<Tag>` → `tagIds` (JSON
  array column), `NoteComment.note` → `noteId`, `Sermon.series` → `seriesId`,
  `UserVerse.marks` → query `indexedTexts` by `sourceId`.
- **Local `updatedAt`** on notes / flash cards is the domain edit time = wire `contentUpdatedAt`.
  Wire `updatedAt` is the server watermark and is never stored on a row.
- `needsSync` / `lastSyncedAt` are local only and never written to Firestore.
- Bodies are plaintext in Room (contract v1); encrypted only on the wire.

## Sync engine (`data.sync`)

| Android | iOS counterpart |
| --- | --- |
| `CloudSync` (+ built-in runner) | `CloudSync` + `SyncRunner` — the only entry point; runs never overlap |
| `SyncPuller` | `FirestoreUserDataPull` |
| `SyncPusher` | `FirestoreUserDataPush` |
| `SyncPeerRegistry`, `AccountEpoch` | same names (`devices/{deviceId}`, `meta/account`) |
| `SyncConflictDetector` | `SyncConflictDetector` + `SyncConflictManager.logConflict` |
| `LocalSyncOps` | `LocalTombstonePurge`, `PinLog` helpers, `markAllUserScopedDataForResync`, flash-card backfill |
| `CryptoSession` + `NoteKeyProvider` + `KeystoreNoteKeyCache` | `CryptoSession` + `SecureKeyService` + `CryptoSessionKeychain` (12 h TTL) |
| `SyncTriggers`, `PushWorker` | `AppLaunchController` / `AppRootView` scene phases / `SyncNetworkMonitor` / `CryptoGateController` |
| `SyncGuard` | `SyncRunner.isValid` |

Triggers: cold start / sign-in → full sync; foreground / network regained → auto sync (pull only
if a peer's `devices.lastUpdatedAt` moved); background → push via WorkManager (survives process
death, waits for network). A device that is signed out never touches the network.

The engine talks to the backend only through `RemoteStore`, so `SyncEngineTest` runs whole
multi-device scenarios (restore, conflicts, tombstones, K2 cursor hold-back, epoch reset,
peer-gated pulls) against real Room and an in-memory Firestore.

## Deliberate differences from iOS

| | iOS | Android | Why |
| --- | --- | --- | --- |
| Pull of `tagIds` / `noteId` / `seriesId` whose target isn't local yet | dropped (Realm needs the object) | kept as id | out-of-order pulls no longer lose references; UI ignores ids it can't resolve |
| `sermonSeries.sermonIds` order on push | Realm `LinkingObjects` order | `preachedOn, createdAt, id` | deterministic; pull only uses it for membership |
| `userVerses` doc whose id ≠ ords | `precondition` crash | skipped | never crash on remote data |
| "Unchanged since queued" check before clearing `needsSync` | a few fields per table (notes: `updatedAt` only) | whole row | iOS can mark a mid-push edit (e.g. `isPinned`) clean without uploading it |
| Peer watermark after a pull | advanced even if the pull failed | advanced only after a complete pull | a failed / held-back pull is retried on the next auto sync |
| Pull reads | default source | `Source.SERVER` | Firestore cache is memory-only here; a partial cache must never advance the cursor |
| Note hard delete cascade | comments in the note's Realm list | every comment with that `noteId` | pulled comments are never in iOS's list |
| Verse-anchor dedupe with `verseEnd` unset | never matches (duplicate anchor) | read as `verseStart` | IOS_ISSUES F3 |
| Note-body reference detection | ASCII `:` / `-` only | also full-width `：` `－` | IOS_ISSUES F1 |
| `vN` shortcut past the chapter end | reference dropped, `vN` text lost | body kept as typed | IOS_ISSUES F2 |
| Edit note text | re-runs the old detector; "John 3:16 …" turns the note into a verse anchor | text only | IOS_ISSUES G1 |
| Edit comment text to blank | saved | ignored | IOS_ISSUES G10 |
| Chapter page verse details | one anchor of the verse (`.first`) | every anchor of the verse (all days, versions) | IOS_ISSUES H1 |
| Chapter-page note whose field names another chapter | anchored to the page's chapter | anchored to the field's chapter | IOS_ISSUES H5 |
| Reflection written on a chapter verse | only once an anchor exists; on that anchor's version | always; today's anchor in the primary version | IOS_ISSUES H1 |
| Empty optional field of a sermon / video / series, and a note's cleared `answeredAt`, on push | left out of the merge write (old remote value survives) | `FieldValue.delete()` | IOS_ISSUES K1 |
| Sermon reference with a multi-word book ("1 John 3") | not parsed (first word only) | parsed; iOS-parseable input unchanged | IOS_ISSUES K21 |
| Saving a sermon over a chapter read today | chapter toggled to unread | left read | IOS_ISSUES D1 |
| Series auto-detection | same book within 6 months, else newest topical series | same book, 6 months, unfinished, user's own; else none | IOS_ISSUES K3 (user's decision) |
| "Add as sermon" for a link saved as a video | video becomes the sermon's hidden index row | refused; the video opens | IOS_ISSUES K6 |
| Delete / merge sermon | index row and notes' pin logs left behind | removed (merge: index row moves to the target) | IOS_ISSUES K7 |
| Re-adding a deleted video | old row revived with its first `createdAt` | revived with `createdAt` = now | IOS_ISSUES K8 |
| Merge target | gets only the notes | also the source's fields it lacks, tags combined | IOS_ISSUES K9 |
| New video metadata | YouTube Data API (`channelId` set) | oEmbed (no `channelId`) | user's decision (plan §4 question 2) |

Anything else that differs is a bug: the encoder tests list the exact iOS field set per collection.

## Not done yet (next steps)

- Firebase Auth (Google / Apple) and the sign-in UI. Add `app/google-services.json` to enable
  Firebase; without it the app is local-only. **Nothing has run against real Firestore yet.**
- Cloud sign-out ("登出云") / account deletion: needs the `endCloudSession` Cloud Function (Blaze).
- Note image upload/download (Firebase Storage, Blaze).
- UI for conflicts (`syncConflicts`), the device list, and the cloud-reset notice flag.
