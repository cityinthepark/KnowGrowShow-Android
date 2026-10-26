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

Room is the only store the UI reads. Firestore is a transport; local-only use never touches it.

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

Anything else that differs is a bug: the encoder tests list the exact iOS field set per collection.

## Not done yet (next steps)

- Firebase Auth (Google / Apple) and the sign-in UI. Add `app/google-services.json` to enable
  Firebase; without it the app is local-only. **Nothing has run against real Firestore yet.**
- Cloud sign-out ("登出云") / account deletion: needs the `endCloudSession` Cloud Function (Blaze).
- Note image upload/download (Firebase Storage, Blaze).
- UI for conflicts (`syncConflicts`), the device list, and the cloud-reset notice flag.
