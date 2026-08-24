# AdMob Roadmap

Working notes for monetisation in this app. Written 2026-08-24, the day the
publisher account came back from suspension. Update it as steps land.

## Where things stand

Publisher `pub-8638037215789792` was suspended on 2026-07-24 for invalid
traffic (self-clicks) and reinstated on **2026-08-24**.

The suspension is the reason for most of the constraints below. Google's
reinstatement email is explicit that a repeat offence can mean the account is
**permanently disabled**, with earnings from the previous 60 days withheld to
refund advertisers. A reinstated account is watched more closely than a fresh
one, so the strategy here is deliberately conservative: earn less, keep the
account.

### Done

- **Banners live on MainActivity and QrGeneratorActivity**, both at the
  **bottom** of the screen (commit `ca6c52d`, branch `feat/re-enable-admob`).
- **Release builds hard-fail** when an AdMob secret is missing, instead of
  silently shipping Google's test IDs.
- Verified on a physical device: both screens render the 320x50 test banner
  with no crash.

- **UMP / GDPR consent flow** wired up in `utils/ConsentManager.kt`. Verified
  on device in both geographies — see that section for what was tested.

### Not done

- **Interstitial** — deliberately unwired. See the design further down.
- **Publish the GDPR message in the AdMob dashboard** (Privacy & messaging →
  GDPR). The code is ready and falls back safely, but until a message is
  published for this app, European users get the default test form rather
  than your own.

## Rules that must not be broken

These encode why the account was suspended. Changing any of them reopens the
risk that caused it.

1. **Banners stay at the BOTTOM.** The pre-suspension layout put the banner
   directly above the camera viewfinder. Users aiming the phone at a QR code
   brushed the ad with their hands, producing clicks with near-zero dwell
   time — which is what invalid traffic looks like in Google's data. Never
   move a banner back to the top of a scanning screen.

2. **Keep a gap between ads and controls.** MainActivity's flash buttons hold
   a 24dp margin above the banner so a mistimed tap cannot reach it.

3. **Only two screens carry ads.** MainActivity and QrGeneratorActivity.
   WA Direct, History and About stay clean. This is deliberate density
   reduction, not an oversight.

4. **Never click your own ads.** Debug builds use Google's test IDs and are
   safe. Release builds serve real ads — do not tap them on your own device.
   Always test with `assembleDebug`.

5. **WA Direct was considered and rejected** as a banner host. It has a
   country-code picker, a number field and a send button — more tap targets
   in a tighter flow than QR Generator's type-then-tap-once. Moving the
   banner there would trade a static screen for an interactive one without
   increasing revenue.

## Done: UMP / GDPR consent

UMP (User Messaging Platform) is Google's consent SDK. It shows the "can we use
your data for personalised ads?" dialog that EU and UK law (GDPR) requires
before ad personalisation. Google passes that obligation to publishers through
its EU User Consent Policy, so it is an AdMob policy requirement, not just a
legal nicety.

Implemented in `utils/ConsentManager.kt`. Key design points:

- `MobileAds.initialize` was **moved out of `MyApp`** — the Ads SDK must not
  start before consent is resolved, and showing the form needs an Activity
  context. `MyApp` is now an empty Application subclass.
- Both ad-bearing activities call `ConsentManager.gatherConsent(this) { showBanner() }`.
  The banner is only constructed inside that callback, so no ad request can
  precede consent.
- `initialiseAds` is guarded by `AtomicBoolean` and `showBanner()` bails when
  the container already has a child — the callback can legitimately fire twice
  (cached consent, then the background refresh).
- On network/SDK failure the flow falls back to the cached consent state rather
  than leaving the app permanently ad-free. This mirrors Google's reference flow.
- No new dependency: `user-messaging-platform:3.2.0` already arrives
  transitively via `play-services-ads:24.5.0`.

### What was verified on device

| Scenario | Result |
|---|---|
| Non-EEA (normal) | `IABTCF_gdprApplies=0`, no dialog shown, banner loads |
| EEA, first launch | Consent form appears before any ad |
| EEA, "Do not consent" | `IABTCF_PurposeConsents=00000000000` stored; banner still loads |

That last row is correct, not a bug. Under the TCF framework "Do not consent"
means "do not use my data for personalisation" — **not** "show me no ads".
Google may still serve non-personalised ads, which is why `canRequestAds()`
stays true. What would breach GDPR is ignoring `canRequestAds()` and loading
regardless; the code only ever initialises inside that guard.

### Testing the form again

The consent form only appears for EEA users, so to see it on a local device:

1. Run the app once and copy the hashed id from logcat
   (`UserMessagingPlatform: Use new ConsentDebugSettings.Builder().addTestDeviceHashedId("...")`).
2. Put it in `local.properties` as `admob_test_device_id=<hash>`. This forces
   `DEBUG_GEOGRAPHY_EEA` on debug builds only; release ignores it entirely.
3. Rebuild, then `adb shell pm clear com.ahmadabuhasan.qrbarcode` to wipe the
   stored answer, and relaunch.
4. **Remove the line from `local.properties` when done**, or every debug build
   keeps pretending to be in Europe.

## Later: interstitial

**Do not start this until banners have run clean for 2-4 weeks.** If an
interstitial ships now and traffic quality dips, there is no way to tell
whether the banner or the interstitial caused it. A clean baseline first makes
the signal readable.

The loader already exists in `utils/Utils.kt`, fully written but called from
nowhere. That is intentional — interstitials are the highest-risk format,
because they cover the whole screen and users reaching for a small close
button frequently miss and hit the ad instead.

If and when it is wired up, the design agreed on:

- **Trigger at a natural finish point**, not mid-task: when the scan-result
  bottom sheet is dismissed (`onScanResultSheetDismissed()` in MainActivity).
  Never on app launch, never while the camera is active.
- **Layered frequency cap** — all three must hold before showing:

  | Condition | Value | Reason |
  |---|---|---|
  | Scan count | every 5th scan | let the user get value first |
  | Time gap | min 3 minutes | avoids back-to-back during bulk scanning |
  | Launch cooldown | first 60s ad-free | protects the first impression |

- **Preload in the background; skip if not ready.** Never make the user wait
  on an ad. A missed impression beats a sluggish app.
- **Daily ceiling** of 3-4 per user as a backstop for heavy users.

Expected outcome: a typical user (3-4 scans a day) may never see one at all.
Only power users will. That is the intent — an aggressive interstitial might
earn several times more, but a second strike can cost the account plus 60 days
of earnings.

Implementation sketch: an `InterstitialManager` holding counters in
SharedPreferences, roughly 60 lines, plus one call site in MainActivity.

## Before each release

- [ ] AdMob secrets present in `local.properties` or CI env (release build
      fails without them)
- [ ] Tested on device with a **debug** build, not release
- [ ] No new ad placements added to WA Direct, History or About
- [ ] Banners still at the bottom of both ad-bearing screens
