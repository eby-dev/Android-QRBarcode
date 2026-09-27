# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Build
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew bundleRelease        # AAB for Play Store

# Test & Lint
./gradlew test                 # Unit tests (Robolectric; ScanContentParserTest, BatchScanViewModelTest)
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
- [MyApp.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/MyApp.kt) — Application class; deliberately empty (the Ads SDK starts from `ConsentManager`, see AdMob below)
- [BaseActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/BaseActivity.kt) — All activities extend this; applies system-bar insets on Android 15+ (target SDK 35 enforces edge-to-edge). The listener is attached directly to `android.R.id.content` and `requestApplyInsets` is called so the initial dispatch is guaranteed.

**Screens (all under `ui/`):**
- [HomeActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/home/HomeActivity.kt) — **Launcher and task root.** Dashboard with a large Scan card, a full-width Batch scan card, shortcuts to Scan from Gallery / History / WA Direct / QR Generator, and the last 3 scans (`ScanHistoryDao.observeRecent`; tap reopens the result sheet without bumping history, "See all" opens History). Overflow menu: "Open camera on launch" (checkable, stored by [AppPrefs.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/AppPrefs.kt), off by default) and About. Owns the app-wide startup work — GDPR consent, the in-app update check (flexible flow + `InstallStateUpdatedListener`) — and the double-Back exit. When "Open camera on launch" is on, `onCreate` starts the scanner with no transition and defers consent and the update check until the dashboard is actually shown (`onRestart` → `onResume`), so neither pops up behind the camera.
- [MainActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/MainActivity.kt) — QR/barcode scanner UI. Live scanning comes from [BarcodeCamera.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/BarcodeCamera.kt), shared with the batch scanner: a CameraX `LifecycleCameraController` bound to a `PreviewView` (ImageAnalysis use case only), with an `MlKitAnalyzer` running ML Kit barcode scanning on every frame; the same file holds the camera-permission helpers (`hasCameraPermission`, `showCameraPermissionDenied`). The controller provides tap-to-focus and pinch-to-zoom; the flash button toggles the torch and hides on devices without a flash unit. [ViewfinderView](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/ViewfinderView.kt) is a purely visual overlay (ML Kit scans the whole frame). Toolbar has an Up button and a single gallery icon for Scan from Gallery (Android Photo Picker, decoded with ML Kit via `InputImage.fromFilePath`, no runtime permission); Back/Up return to the dashboard. Camera-permission denial shows a snackbar with a Settings action, and `onResume` starts the camera once the permission is granted there. Successful scans vibrate via [Haptics.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/Haptics.kt), tagged as touch feedback, so they follow the system Touch feedback setting (off → no vibration, by design). Persists every scan to Room and shows a [ScanResultBottomSheet](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/ScanResultBottomSheet.kt) for the result.
- [BatchScanActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/batch/BatchScanActivity.kt) — Continuous scanning for stock counts. Camera on top, list below, **no banner**. Each code goes straight into the list with a beep (`ToneGenerator` on the notification stream, so silent mode mutes it) and `Haptics.scanSuccess`, with no result sheet. [BatchScanViewModel](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/batch/BatchScanViewModel.kt) keys items by format + content; a code held in view is added once and counts again only after it has been unseen for `REPEAT_GAP_MS` (1.5 s). The list lives in `SavedStateHandle` (survives process death) and is **not** written to scan history. Export: CSV via FileProvider (`cache/exports/`), built by `BatchCsv` in [BatchItem.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/batch/BatchItem.kt) — UTF-8 BOM for Excel, RFC 4180 quoting, and a leading `'` on cells starting with `= + - @` (CSV injection); or plain text. Back with a non-empty list confirms before discarding.
- [QrGeneratorActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/qrgenerator/QrGeneratorActivity.kt) — Generates a QR bitmap from text and shares it via FileProvider.
- [WaDirectActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/wadirect/WaDirectActivity.kt) — Opens `https://wa.me/<number>` with an optional message. Uses `com.hbb20:ccp` for the country code picker.
- [HistoryActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/history/HistoryActivity.kt) — Lists persisted scans (RecyclerView + `ListAdapter` + `DiffUtil`) with per-item Copy / Share / Open (URLs only) / Delete and a global Clear all.
- [AboutActivity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/about/AboutActivity.kt) — App name and version. No ads.

**Persistence — Room DB (`data/`):**
- [AppDatabase.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/data/AppDatabase.kt) — Singleton `AppDatabase.get(context)` returning the app's `qrbarcode.db`
- [ScanHistoryEntity.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/data/ScanHistoryEntity.kt) — `id`, `content`, `format`, `isUrl`, `scannedAt`
- [ScanHistoryDao.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/data/ScanHistoryDao.kt) — `observeAll()` / `observeRecent(limit)`: `LiveData<List<...>>`, `upsert` (insert, or bump `scannedAt` of an identical content+format row), `deleteById`, `clear`

**Scan result behavior:** [ScanViewModel.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/ScanViewModel.kt) is the shared base of `MainViewModel` and `HomeViewModel`: it owns gallery decoding and `handleScanResult`, which upserts a `ScanHistoryEntity` and emits a `ScanResult`. Activities call `observeScanResults(viewModel)` from [ScanResultUi.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/ScanResultUi.kt), which shows it in a `ScanResultBottomSheet` (and gallery errors as a toast). `showScanResultSheet` flushes pending fragment transactions before its "sheet already open?" check: a dismissed sheet lingers until its removal commit runs, and ML Kit can deliver a result in that gap, which used to leave the scanner paused with no sheet. The sheet parses the payload via `ScanContentParser` and renders a **type-specific primary action** (Open / Copy password / Dial / Send SMS / Compose email / Open in Maps / Save contact / Add to calendar) plus Copy, Share, and Scan again. `MainViewModel.handleScanResult` also sets `isScanningPaused`, so the analyzer ignores frames while the sheet is open (kept in the ViewModel to survive rotation). Dismissing the sheet calls `resumeScanning()` so the next scan needs no navigation; the same code is then ignored for 2 s (`shouldIgnoreLiveScan`) so a code still in view doesn't reopen the sheet instantly. If a sheet can't be shown, the scanner passes `onSheetSkipped = resumeScanning`. Formats are stored under ZXing's `BarcodeFormat` names via `Barcode.formatName()` in [BarcodeFormats.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/ui/main/BarcodeFormats.kt), so history rows from before and after the ML Kit switch match.

**QR content parsing:** [model/ScanContent.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/model/ScanContent.kt) is a sealed class covering `Url`, `Wifi`, `Phone`, `Sms`, `Email`, `Geo`, `VCard`, `CalendarEvent`, `Text`. `ScanContentParser.parse(text)` classifies raw scan text by prefix (`WIFI:`, `tel:`, `SMSTO:`/`sms:`, `mailto:`/`MATMSG:`, `geo:`, `BEGIN:VCARD`, `BEGIN:VEVENT`/`BEGIN:VCALENDAR`, http/https) and pulls structured fields. Unrecognised payloads fall through as `Text`.

**AdMob:**
- Ads are **live again** — publisher `pub-8638037215789792` was reinstated on 2026-08-24 after a suspension for invalid traffic. `MobileAds.initialize` runs from `ConsentManager` once consent resolves, and banners load in HomeActivity, MainActivity and QrGeneratorActivity.
- **Banners sit at the BOTTOM of every screen that has one, and must stay there.** The pre-suspension layout put the banner directly above the camera viewfinder, where hands moving to aim at a QR code generated accidental clicks — the most likely cause of the invalid-traffic strike. On MainActivity the flash controls also keep a 24dp gap above the banner, and on HomeActivity the scrolling content stops 16dp above it. Do not move a banner back to the top.
- Ads land only on HomeActivity, MainActivity and QrGeneratorActivity (banner). WA Direct, About, History and the batch scanner have no ads — deliberately reduced ad density; the batch scanner is all aiming, the hand movement that caused accidental clicks before.
- The App ID lives in the manifest via `@string/AdMob_Application_ID`, which is a build-time `resValue` (not committed as a raw resource) — debug uses Google's test App ID, release reads it from `local.properties` / env var (see [Ad ID sourcing](#ad-id-sourcing) below).
- Banner ad unit IDs come from **native code**. Layouts hold a plain `<FrameLayout>` container, and each activity constructs the `AdView` in code and calls `adView.adUnitId = AppConfig.bannerAdId()` before `loadAd`. The AdMob SDK rejects setting `adUnitId` twice or leaving it out of XML on an inflated `AdView`, so the container pattern is required.
- The interstitial is **intentionally still unwired**: [Utils.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/Utils.kt) exposes `loadAd()` and reads `AppConfig.interstitialAdId()`, but no screen calls it. Interstitials carry the highest invalid-traffic risk, so attach it only behind a frequency cap (e.g. every N scans with a minimum time gap) — never on every screen entry.
- **GDPR consent runs through [ConsentManager.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/ConsentManager.kt)** (UMP). `MobileAds.initialize` deliberately does **not** live in `MyApp` — the Ads SDK must not start until consent resolves, and the form needs an Activity context. Every ad-bearing activity calls `ConsentManager.gatherConsent(this) { showBanner() }` and build their `AdView` only inside that callback. Outside the EEA the SDK shows nothing and ads load as normal. To re-test the form locally, set `admob_test_device_id` in `local.properties` to the hashed id the UMP SDK prints to logcat — that forces EEA geography on debug builds — then clear the app data and relaunch. Remove the line afterwards.

## Ad ID sourcing

Ad unit IDs (banner, interstitial) are compiled into `libnative-lib.so` as preprocessor macros — never stored as string resources.

- [CMakeLists.txt](app/CMakeLists.txt) builds `libnative-lib` from [cpp/native-lib.cpp](app/src/main/cpp/native-lib.cpp)
- [cpp/native-lib.cpp](app/src/main/cpp/native-lib.cpp) exposes `bannerAdId()` and `interstitialAdId()` via JNI using `TOSTRING(x)` on the macro-substituted values
- [AppConfig.kt](app/src/main/java/com/ahmadabuhasan/qrbarcode/utils/AppConfig.kt) loads the library and declares the two `external` functions
- `app/build.gradle` reads `admob_banner_id`, `admob_interstitial_id`, `admob_app_id` from `local.properties` or env vars (`ADMOB_BANNER_ID`, `ADMOB_INTERSTITIAL_ID`, `ADMOB_APP_ID`), passes them into `cppFlags` per build type, and also uses `admob_app_id` as a `resValue` for the manifest App ID. Every AdMob key — including the optional `admob_test_device_id` — carries the `admob_` prefix so they group together.
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
| `androidx.camera:camera-{camera2,lifecycle,view,mlkit-vision}:1.5.3` | Camera preview + analyzer for live scanning. Held at 1.5.x because 1.6.x needs AGP 8.9.1+ (project is on 8.7.3) |
| `com.google.mlkit:barcode-scanning:17.3.0` | Barcode decoding, live and from gallery. Bundled model, so the first scan works offline |
| `com.google.zxing:core:3.5.4` | QR **generation** only (ML Kit can't encode). Declared directly; it used to arrive transitively via the old dm7 scanner |
| `org.robolectric:robolectric:4.17` | Unit tests for code that touches Android classes (e.g. `ScanContentParser` uses `android.net.Uri`) |
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

GitHub Actions ([.github/workflows/android.yml](.github/workflows/android.yml)) runs on pushes to `master` and `development`. `internal-testing/*` branches are **manual only** (Actions → Android CI → Run workflow → pick the branch), which then distributes via Firebase. Any push or manual run on `master` deploys to Play Store production, so `versionCode` must be bumped first. Pipeline: setup → build → unit-test → code-analysis → deploy. Fastlane handles the actual build/deploy steps. The "Build AAB" job writes signing + AdMob secrets into `local.properties` before invoking Gradle.
