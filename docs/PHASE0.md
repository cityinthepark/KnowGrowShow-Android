# Phase 0 checklist

Target: **Play Internal testing upload within ~7 days** (58-day reservation window).

## Done in scaffold

- [x] Gradle + Compose project (`com.stoneofhope.stoneofhope`)
- [x] Welcome: 3 intro slides + sync choice + legal links
- [x] Home placeholder after onboarding
- [ ] DataStore: persist `hasCompletedWelcomeOnboarding` (next commit)
- [ ] Notes list stub (Realm in Phase 0b or early Phase 1)

## Welcome / demo media

Intro slides are **placeholder copy** (English only for now). Later:

- **iOS**: SwiftUI + Lottie or bundled MP4 (portrait, SF Symbols aesthetic)
- **Android**: Compose + platform video (`ExoPlayer`) or Lottie — **separate assets**, same storyboard/script

Do not share one video file across platforms unless it is brand-only (no UI chrome).

## Next steps (you)

1. Open project in Android Studio → sync → run on emulator.
2. `git init` + push to new GitHub repo under your account.
3. Play Console → Internal testing → upload first AAB (`versionCode` 1).
4. Optional: register Android app in Firebase (can wait until Phase 1).

## Phase 1 preview

Google Sign-In → Realm models → Firestore push/pull → AES-GCM per `CRYPTO_SYNC_CONTRACT.md` in iOS repo.
