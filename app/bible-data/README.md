# Bundled Bible texts

Byte-for-byte copies of the iOS bundle files `KnowGrowShow/Utils/DataFiles/` (last changed in iOS
commit `45420b8`, copied from iOS `b2e0b92`). At build time `GenerateBibleTextDb` (buildSrc) turns
them into the read-only asset `bible/bible_text.db`; the app never parses them at run time.

| File | SHA-256 | Source / licence |
| --- | --- | --- |
| `KJV.json` | `bb891caa5584e51adc635b1baa6c4b0dfaec2fda2ead061620a311a42e4735b1` | getbible.net v2 export of the CrossWire KJV module. Base text public domain outside the UK (Crown letters patent in the UK). |
| `CUV.xml` | `c145fe279001b5813dbd479979d6ee9e1d59d7c91e6b110e68c510588c5a713b` | USFX, 和合本 (1919, Traditional, 神 edition, 31,100 verses). Public domain; not the 1988 新标点和合本. |

If iOS replaces a file, copy it here, update the hash, and bump `BibleTextDatabase.VERSION`.
Verse text must match what iOS stores in `DownloadedVerse.verseContent`, because it is frozen into
`userVerses` and synced (see `docs/TODAY_T2_PLAN.md` §2).
