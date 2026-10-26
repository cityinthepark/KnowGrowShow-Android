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

## Deliberate differences from iOS

| | iOS | Android | Why |
| --- | --- | --- | --- |
| Pull of `tagIds` / `noteId` / `seriesId` whose target isn't local yet | dropped (Realm needs the object) | kept as id | out-of-order pulls no longer lose references; UI ignores ids it can't resolve |
| `sermonSeries.sermonIds` order on push | Realm `LinkingObjects` order | `preachedOn, createdAt, id` | deterministic; pull only uses it for membership |
| `userVerses` doc whose id ≠ ords | `precondition` crash | skipped | never crash on remote data |

Anything else that differs is a bug: the encoder tests list the exact iOS field set per collection.

## Not done yet (next steps)

- Sync engine: dirty-row push with fingerprints, pull orchestration in `FirestoreSchema.pullOrder`,
  watermark cursor, `meta/account` epoch, `devices` registry, conflict detection, PinLog daily
  refresh, flash-card legacy `content` → `userVerses` backfill.
- `NoteKeyProvider` (Firestore now, Cloud Function after Blaze) + Android Keystore session cache.
- Firebase Auth. Add `app/google-services.json` to enable; without it the app is local-only.
