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

## Firebase (Phase 1)

1. Firebase Console → add **Android** app, package `com.stoneofhope.stoneofhope`.
2. Download `google-services.json` → `app/google-services.json` (gitignored).
3. Uncomment Firebase plugin + deps in `app/build.gradle.kts`.

## Play Internal testing

Build → **Generate Signed Bundle / APK** → AAB → Play Console → Internal testing track.

Phase 0 does not require Firebase or sync to upload the first build.
