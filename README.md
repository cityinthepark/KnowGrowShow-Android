# KnowGrowShow — Android

Native Kotlin + Jetpack Compose app for **Stone of Hope**. Syncs with the iOS app via Firebase (Phase 1+).

| | |
|--|--|
| **Package** | `com.stoneofhope.stoneofhope` (same as iOS global) |
| **Sibling repos** | [KnowGrowShow](../KnowGrowShow) (iOS), [StoneOfHope_Website](../StoneOfHope_Website) (web + legal pages) |
| **Contracts** | iOS repo `docs/FIRESTORE_SYNC_CONTRACT.md`, `docs/CRYPTO_SYNC_CONTRACT.md` |

## Phase 0 (now)

- Compose shell + Welcome carousel (text slides; video/animation later — **Android-specific assets**)
- Local-only / register choice (Firebase wired in Phase 1)
- Home placeholder → enough for **Play Internal testing** upload

See [docs/PHASE0.md](docs/PHASE0.md).

## Open in Android Studio

1. Install [Android Studio](https://developer.android.com/studio) (Ladybug or newer).
2. **File → Open** → select this folder (`KnowGrowShow-Android`).
3. Let Gradle sync finish (Studio may offer to download SDK / create `local.properties`).
4. Run on emulator or device.

## Git setup (first time)

```bash
cd ~/Documents/Development/KnowGrowShow-Android
git init
git add .
git commit -m "Phase 0: Compose shell and Welcome onboarding"

# On GitHub: New repository → KnowGrowShow-Android (empty, no README)
git remote add origin git@github.com:<your-account>/KnowGrowShow-Android.git
git branch -M main
git push -u origin main
```

Create the **local folder first**, commit, then add GitHub remote — order does not matter much as long as you push before collaborating.

## Firebase

**Local emulator (no Blaze, no production data)** — debug builds only:

1. `cp dev.properties.example dev.properties` and fill in a throwaway test account.
2. Start the emulators from the iOS repo: `./Scripts/start-firebase-emulators.sh`.
3. Run the debug app → Home → **Account & Sync** → Sign in.

iOS ↔ Android test steps: [docs/INTEROP_TEST.md](docs/INTEROP_TEST.md). Data layer and sync design:
[docs/DATA_LAYER.md](docs/DATA_LAYER.md). iOS issues found while porting, and how Android handles them:
[docs/IOS_ISSUES.md](docs/IOS_ISSUES.md).

**Production** (later):

1. Firebase Console → add **Android** app, package `com.stoneofhope.stoneofhope` (+ SHA-1 for Google sign-in).
2. Download `google-services.json` → `app/google-services.json` (gitignored). The build applies the
   google-services plugin automatically when the file exists.

## Play Internal testing

Build → **Generate Signed Bundle / APK** → AAB → Play Console → Internal testing track.

Phase 0 does not require Firebase or sync to upload the first build.
