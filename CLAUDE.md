# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease        # AAB for Play Store

# Test & Lint
./gradlew test                 # Unit tests
./gradlew connectedAndroidTest # Instrumented tests
./gradlew lint

# Fastlane automation (requires Ruby/Bundler)
bundle exec fastlane buildDebug
bundle exec fastlane buildRelease
bundle exec fastlane buildBundle
bundle exec fastlane test
bundle exec fastlane deployBeta       # Google Play open testing
bundle exec fastlane deployRelease    # Google Play production
bundle exec fastlane deployFirebase   # Firebase App Distribution (internal)
```

## Architecture

Activity-based with lightweight MVVM per screen (Activity + ViewModel + LiveData). No repository layer — activities/ViewModels talk to DAOs directly. The codebase is entirely Kotlin. ViewBinding is enabled.

**Application & base:**
- [MyApp.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/MyApp.kt) — Application class; initializes Google Mobile Ads SDK
- [BaseActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/BaseActivity.kt) — All activities extend this; applies system-bar insets on Android 15+ (target SDK 35 enforces edge-to-edge). The listener is attached directly to `android.R.id.content` and `requestApplyInsets` is called so the initial dispatch is guaranteed.

**Screens (all under `ui/`):**
- [MainActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/MainActivity.kt) — QR/barcode scanner UI using ZXing (`me.dm7.barcodescanner:zxing`). Options menu offers Scan from Gallery (Android Photo Picker, decoded via `MultiFormatReader` in the ViewModel, no runtime permission), History, WA Direct, QR Generator, About. Persists every scan to Room and shows a [ScanResultBottomSheet](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/ScanResultBottomSheet.kt) for the result.
- [QrGeneratorActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/qrgenerator/QrGeneratorActivity.kt) — Generates a QR bitmap from text and shares it via FileProvider.
- [WaDirectActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/wadirect/WaDirectActivity.kt) — Opens `https://wa.me/<number>` with an optional message. Uses `com.hbb20:ccp` for the country code picker.
- [HistoryActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/history/HistoryActivity.kt) — Lists persisted scans (RecyclerView + `ListAdapter` + `DiffUtil`) with per-item Copy / Share / Open (URLs only) / Delete and a global Clear all.
- [AboutActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/about/AboutActivity.kt) — App name and version. No ads.

**Persistence — Room DB (`data/`):**
- [AppDatabase.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/data/AppDatabase.kt) — Singleton `AppDatabase.get(context)` returning the app's `qrbarcode.db`
- [ScanHistoryEntity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/data/ScanHistoryEntity.kt) — `id`, `content`, `format`, `isUrl`, `scannedAt`
- [ScanHistoryDao.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/data/ScanHistoryDao.kt) — `observeAll(): LiveData<List<...>>`, `insert`, `deleteById`, `clear`

**Scan result behavior:** `MainActivity` implements `ZXingScannerView.ResultHandler`. On a successful scan (from either the live camera or a picked image), `MainViewModel.handleScanResult` inserts a `ScanHistoryEntity` and emits a `ScanResult` that the activity shows in a `ScanResultBottomSheet`. The sheet parses the payload via `ScanContentParser` and renders a **type-specific primary action** (Open / Copy password / Dial / Send SMS / Compose email / Open in Maps / Save contact / Add to calendar) plus Copy, Share, and Scan again. Dismissing the sheet calls `ZXingScannerView.resumeCameraPreview` so the next scan needs no navigation.

**QR content parsing:** [model/ScanContent.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/model/ScanContent.kt) is a sealed class covering `Url`, `Wifi`, `Phone`, `Sms`, `Email`, `Geo`, `VCard`, `CalendarEvent`, `Text`. `ScanContentParser.parse(text)` classifies raw scan text by prefix (`WIFI:`, `tel:`, `SMSTO:`/`sms:`, `mailto:`/`MATMSG:`, `geo:`, `BEGIN:VCARD`, `BEGIN:VEVENT`/`BEGIN:VCALENDAR`, http/https) and pulls structured fields. Unrecognised payloads fall through as `Text`.

**AdMob:**
- Ads are **live again** — publisher `pub-8638037215789792` was reinstated on 2026-08-24 after a suspension for invalid traffic. `MobileAds.initialize` runs in [MyApp.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/MyApp.kt), and banners load in MainActivity and QrGeneratorActivity.
- **Banners sit at the BOTTOM of both screens, and must stay there.** The pre-suspension layout put the banner directly above the camera viewfinder, where hands moving to aim at a QR code generated accidental clicks — the most likely cause of the invalid-traffic strike. On MainActivity the flash controls also keep a 24dp gap above the banner. Do not move a banner back to the top.
- Ads land only on MainActivity and QrGeneratorActivity (banner). WA Direct, About, History have no ads — deliberately reduced ad density.
- The App ID lives in the manifest via `@string/AdMob_Application_ID`, which is a build-time `resValue` (not committed as a raw resource) — debug uses Google's test App ID, release reads it from `local.properties` / env var (see [Ad ID sourcing](#ad-id-sourcing) below).
- Banner ad unit IDs come from **native code**. Layouts hold a plain `<FrameLayout>` container, and each activity constructs the `AdView` in code and calls `adView.adUnitId = AppConfig.bannerAdId()` before `loadAd`. The AdMob SDK rejects setting `adUnitId` twice or leaving it out of XML on an inflated `AdView`, so the container pattern is required.
- The interstitial is **intentionally still unwired**: [Utils.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/Utils.kt) exposes `loadAd()` and reads `AppConfig.interstitialAdId()`, but no screen calls it. Interstitials carry the highest invalid-traffic risk, so attach it only behind a frequency cap (e.g. every N scans with a minimum time gap) — never on every screen entry.
- **GDPR consent runs through [ConsentManager.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/ConsentManager.kt)** (UMP). `MobileAds.initialize` deliberately does **not** live in `MyApp` — the Ads SDK must not start until consent resolves, and the form needs an Activity context. Both ad-bearing activities call `ConsentManager.gatherConsent(this) { showBanner() }` and build their `AdView` only inside that callback. Outside the EEA the SDK shows nothing and ads load as normal. See [ADMOB_ROADMAP.md](ADMOB_ROADMAP.md) for the verification matrix and how to re-test the form.

## Ad ID sourcing

Ad unit IDs (banner, interstitial) are compiled into `libnative-lib.so` as preprocessor macros — never stored as string resources.

- [CMakeLists.txt](app/CMakeLists.txt) builds `libnative-lib` from [cpp/native-lib.cpp](app/src/main/cpp/native-lib.cpp)
- [cpp/native-lib.cpp](app/src/main/cpp/native-lib.cpp) exposes `bannerAdId()` and `interstitialAdId()` via JNI using `TOSTRING(x)` on the macro-substituted values
- [AppConfig.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/AppConfig.kt) loads the library and declares the two `external` functions
- `app/build.gradle` reads `banner_ad_id`, `interstitial_ad_id`, `admob_application_id` from `local.properties` or env vars (`BANNER_AD_ID`, `INTERSTITIAL_AD_ID`, `ADMOB_APPLICATION_ID`), passes them into `cppFlags` per build type, and also uses `admob_application_id` as a `resValue` for the manifest App ID.
- Debug always uses Google's public test IDs.
- Release **fails the build** when any of the three secrets is missing (enforced in a `gradle.taskGraph.whenReady` hook so debug-only builds still configure without them). Shipping Google test IDs to production serves no real ads and endangers the publisher account.
- CI writes the three release secrets into `local.properties` in the "Build AAB" job in [android.yml](.github/workflows/android.yml).

## Theming

- Base theme: `Theme.MaterialComponents.DayNight.DarkActionBar` — the app follows the system light/dark setting
- `values-night/themes.xml` swaps `colorPrimaryVariant`/`Secondary` and status bar tint for dark mode
- `com.hbb20:ccp` does not follow the theme, so `app:ccp_contentColor` and `app:ccp_arrowColor` are pinned to `@color/ccp_content` which has a night override in `values-night/colors.xml`

## Key Libraries

| Library | Purpose |
|---|---|
| `me.dm7.barcodescanner:zxing:1.9.8` | QR/barcode scanning |
| `com.google.android.gms:play-services-ads:24.5.0` | AdMob monetization |
| `com.google.firebase:firebase-bom:34.0.0` | Firebase (Analytics) |
| `com.google.android.play:app-update:2.1.0` | In-app update prompts |
| `com.hbb20:ccp:2.7.3` | Country code picker (WA Direct) |
| `androidx.room:room-{runtime,ktx,compiler}:2.6.1` | Scan history persistence (compiler wired via `com.google.devtools.ksp`) |
| `androidx.recyclerview:recyclerview:1.3.2` | History list |
| `androidx.appcompat:appcompat:1.7.0` / `com.google.android.material:material:1.12.0` | AppCompat + Material — versions matter for correct edge-to-edge on Android 15 |

## Build Configuration

- **Min SDK**: 23 / **Target SDK**: 35 (Android 15)
- **Java/Kotlin target**: 17
- R8 minification enabled for release builds — rules in [proguard-rules.pro](app/proguard-rules.pro)
- Release builds are signed via keystore; credentials managed as GitHub Actions secrets
- Native library ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64` — Play Store 64-bit requirement plus 32-bit ARM for legacy devices and x86_64 for emulator testing

## CI/CD

GitHub Actions ([.github/workflows/android.yml](.github/workflows/android.yml)) runs on pushes to `master`, `development`, and `internal-testing/*` branches. Pipeline: setup → build → unit-test → code-analysis → deploy. Fastlane handles the actual build/deploy steps. The "Build AAB" job writes signing + AdMob secrets into `local.properties` before invoking Gradle.
