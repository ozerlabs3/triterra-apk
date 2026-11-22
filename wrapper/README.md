# TriTerra wrapper (reconstructed) + Android Auto — `com.ozerlabs.triterra.auto`

Debug-only reconstruction of the TriTerra Capacitor wrapper, plus an Android for Cars (POI) surface.
It installs **next to** the owner's app (different applicationId) and can never replace it.
Nothing here touches the root download page, `triterra-prod.apk`, OTA manifests, Firebase config or deploys.

## 1. Verified against the published prod APK

Source: `https://ozerlabs3.github.io/triterra-apk/triterra-prod.apk`, sha256
`62b82af2…5218f7`, identical to `../triterra-prod.apk`. Inspected with `aapt2` / `apksigner`.

| Item | Prod APK (verified) | This wrapper |
|---|---|---|
| applicationId | `com.ozerlabs.triterra` | `com.ozerlabs.triterra.auto` |
| namespace / MainActivity | `com.ozerlabs.triterra.MainActivity` (BridgeActivity) | same |
| version | 1.0.0 (code 1) | 1.0 (code 1) |
| min / target / compile SDK | 24 / 36 / 36, AGP 8.13.0 | 24 / 36 / 36 (Capacitor 8.6.2) |
| `capacitor.config.json` | appName TriTerra, webDir www, `allowMixedContent:false`, FirebaseAuthentication `{skipNativeAuth:true, providers:[google.com]}`, CapacitorUpdater `{autoUpdate:false}` | identical except appId |
| plugins (`capacitor.plugins.json`) | `@capacitor-firebase/authentication`, `@capacitor/contacts`, `@capgo/capacitor-updater` | identical (byte-for-byte classpaths) |
| web bundle | `assets/public`, `index.html` injects `globalThis.TRITERRA_ENV="prod"; globalThis.TRITERRA_WRAPPER="android"`; `ota-version.json` = 1.0.0; `version.json` 1.7.5 | the same files, copied from the APK by `scripts/from-prod-apk.sh` |
| Firebase project | `triterra-921bc` (`google_app_id` registered for `com.ozerlabs.triterra`) | same values, copied read-only from the APK (not committed) |
| signer | `CN=TriTerra, OU=Ozer Labs` (key not available) | **Android Debug** key only |
| permissions | INTERNET, READ/WRITE_CONTACTS, ACCESS_NETWORK_STATE, READ_GSERVICES, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, DYNAMIC_RECEIVER… | identical **+ `androidx.car.app.MAP_TEMPLATES`** (the only addition) |

Corrections to the brief:
- Google sign-in is **`@capacitor-firebase/authentication`** (capawesome), not "Capacitor Google Auth".
- The injected value is `TRITERRA_ENV="prod"`. `environmentForHost` only honors `"preview"` or `"production"`, so `"prod"` falls through to the host check: `localhost` → production. The result is still correct because it fails closed, but the pin is not what actually selects production.
- `app.js` points OTA at `https://ozerlabs3.github.io/triterra-preview-apk/ota/prod/version.json` (currently 404). The `.auto` build ships the same bundle, so it would follow that manifest too. OTA is not promised, and its config is not touched.

Web↔native contract the shell relies on:
- `Capacitor.Plugins.FirebaseAuthentication.signInWithGoogle()` → `credential.idToken` → JS `signInWithCredential`.
- `Capacitor.Plugins.Contacts` (`find` / `pickContact`).
- `Capacitor.Plugins.CapacitorUpdater` (`notifyAppReady`, `download`, `set`).
- `TRITERRA_WRAPPER` gates all three.

## 2. Build

Needs JDK 21, Node 22, and an Android SDK (platform 36, build-tools 35+).

```bash
cd wrapper && npm ci
ANDROID_HOME=/path/to/sdk npm run from-apk     # www/ + Firebase client values from ../triterra-prod.apk
npx cap sync android
cd android && ./gradlew assembleDebug testDebugUnitTest
# -> android/app/build/outputs/apk/debug/app-debug.apk
```

No release signing is configured on purpose. `www/` and the Firebase values file are gitignored: their sources of truth are field-region-map and the APK.

Why the Firebase values are copied: the FirebaseAuthentication plugin calls `FirebaseAuth.getInstance()` in `load()`. Capacitor's `registerPlugin` does not catch that `IllegalStateException`, so without a FirebaseApp the phone app would crash on launch. The car surface would not crash.

## 3. Android Auto module (`app/src/main/java/com/ozerlabs/triterra/car`)

- `TriTerraCarAppService` is declared with `androidx.car.app.category.POI`. `minCarApiLevel` is 1, the library is `androidx.car.app:app:1.7.0` (latest stable), and `automotive_app_desc.xml` declares `<uses name="template"/>`. The host validator allows all hosts on debug builds and the library's allowlist otherwise.
- `BranchListScreen` uses **PlaceListMapTemplate only**. The host draws the map, markers and list, so there is no custom drawing and no text entry. One tap on a row calls `startCarApp(ACTION_NAVIGATE, geo:lat,lng)`, or `geo:0,0?q=name` when a stop has no coordinates, and the host opens the driver's default navigation app (Waze or Google Maps). A failure shows a `CarToast`.
- The data layer is `TodayPlanRepository`:
  - `FirestoreTodayPlanRepository` is the real source (section 4).
  - `PlaceholderBranches` holds three demo city-centre points tagged "לדוגמה / נתוני דמה". Debug builds show it **only when the gate fails**, so the car surface renders on the DHU without sign-in.
  - In release builds, a failed gate gives an empty list with "אין עצירות להצגה · ודאו שהתחברתם לטריטרה בטלפון".
- The car surface does not use the WebView and does not depend on sign-in succeeding.

## 4. Data layer (contract: field-region-map main@4bdaab9, v1.8.3)

`FirestoreTodayPlanRepository` is read-only, using `get()` only. Each read happens only after the previous gate passes:
1. A Firebase Auth user must exist. The uid comes from that user; no uid or email is hardcoded.
2. `users/{uid}` must pass `validateProfile` fail-closed: `active === true`, `schemaVersion` the integer 1 (the string "1" is rejected), email match (lowercase), and `regionId` present.
3. `regions/{regionId}` must have `active === true`, `schemaVersion` 1, and `regionId` equal to the profile's.
4. Then three reads: `users/{uid}/myDays/{YYYY-MM-DD Asia/Jerusalem}`, which is read as the whole `{state, revision, updatedAt}` envelope (a missing doc means the day hasn't started); `stores where regionId ==`; and `users/{uid}/storeOverrides`.
5. Auth-epoch fence: the result is dropped if the uid changed during the reads.

It shows `state.route` in order, puts `currentId` first, and drops `doneIds` and `skippedIds`.
- There are **no writes, transactions, Storage calls, Worker/Sheets calls or rules changes** anywhere.
- The pure logic is in `TodayPlan` and is covered by `TodayPlanTest`.

**Verify with code owner** (not covered by the contract, marked in code):
- (a) the profile's email field name (`email` assumed);
- (b) whether a route stop `id` equals the store doc id;
- (c) store coordinate and display fields (`lat`/`lng`/`city`/`name` assumed, as `city-navigation.js` reads them);
- (d) the `storeOverrides` shape (a shallow field patch is assumed);
- (e) whether to project user-added stores and fixed facilities;
- (f) whether `manualOrder` should override `route` order;
- (g) `myDaySettings/preferences` is not read, because no setting is needed yet.

## 5. Auth wall — finding (expected; not observed at runtime)

This container has no KVM, so no emulator could run. The failures below come from reading the code and config, not from a device run.

1. **Native Firebase has no user, even with perfect console setup.** Prod uses `skipNativeAuth:true`, so the native SDK only passes the Google idToken to the WebView's JS SDK and never signs in itself. As a result, `FirebaseAuth.getCurrentUser()` in the car service is always `null`: the gate fails with `not-signed-in`, and debug builds show the placeholder.
2. **Google sign-in in the `.auto` app should fail.** Prod sign-in works, so `triterra-921bc` must have an Android OAuth client for `com.ozerlabs.triterra` with the prod signer's SHA-1 (`A6:05:05:77:F4:6D:2E:4C:90:32:5B:D6:87:DD:78:9A:8E:28:C0:09`, read from the APK). That registration is inferred, not seen in the console. Nothing is registered for package `com.ozerlabs.triterra.auto` with a debug SHA-1, so Credential Manager / Google ID should reject it with a developer-console configuration error (typically `[28444] Developer console is not set up correctly` / `DEVELOPER_ERROR 10`). The web app's error panel will display it under "native sign-in".
3. If the Google Cloud API key `google_api_key` is restricted to Android apps, requests from `com.ozerlabs.triterra.auto` will also be blocked. I could not see whether it is restricted.

### DECISION NEEDED — owner console steps (not performed)
1. Firebase console → `triterra-921bc` → Project settings → General → **Add app → Android**. Package `com.ozerlabs.triterra.auto`, nickname "TriTerra Auto (debug)".
2. Add the **debug SHA-1 of the machine that will build it**. Get it with `cd wrapper/android && ./gradlew signingReport` (variant debug). Do not register the SHA-1 from this cloud container (`67:94:BE:03:…:D1:B4`): its keystore is temporary.
3. Download that app's `google-services.json` into `wrapper/android/app/` (it is gitignored). The script then stops copying prod values, and the Gradle template applies the google-services plugin.
4. If the API key has Android restrictions: Google Cloud console → APIs & Services → Credentials → the key → add `com.ozerlabs.triterra.auto` with the same SHA-1.
5. Decide on **`skipNativeAuth:false` for the `.auto` build only**. This is a one-line change in `wrapper/capacitor.config.json`, so the native SDK also holds the user and the car can read Firestore. Without it, point 1 still blocks the car's real data.

**Preview path** (the sanctioned safe test, also a decision): project `triterra-preview` with anonymous sign-in and demo data.
- It would need an Android app registered there for `com.ozerlabs.triterra.auto`. Anonymous auth needs no SHA-1.
- It would need the preview web bundle (`TRITERRA_ENV="preview"`).
- It would need a preview-mode gate in the car: anonymous users have no email, so the prod gate correctly rejects them.
- None of this is built.

## 6. Test on the Desktop Head Unit (DHU)
1. Android Studio → SDK Manager → SDK Tools → install **Android Auto Desktop Head Unit Emulator** (`$ANDROID_HOME/extras/google/auto/`). On Linux, run `chmod +x desktop-head-unit`; it needs glibc 2.32+, libc++1 and libc++abi1.
2. Install the app with `adb install -r wrapper/android/app/build/outputs/apk/debug/app-debug.apk`. It installs as a new app next to TriTerra.
3. Phone (Android 9+), on Android 10+ with Android Auto updated from Play: open Android Auto settings → About → tap **Version** 10× → allow development settings. Then use the overflow menu → **Start head unit server**.
4. Run `adb forward tcp:5277 tcp:5277`, then `cd $ANDROID_HOME/extras/google/auto && ./desktop-head-unit --usb` (or without `--usb`).
5. In the DHU launcher, open **TriTerra**. You should see "טריטרה · העצירות של היום" with 3 "סניף לדוגמה" rows on the map. Tap a row and Waze or Maps should open on the car screen.

A real car needs a Play install (section 7). Android Auto's "Unknown sources" setting does not apply to Car App Library apps.

## 7. Distribution (analysis only — DECISION NEEDED)

| Route | Cost | Car review | Notes |
|---|---|---|---|
| DHU on a computer | free | none | Free for development and demos. It does not reach a real car. |
| Play **Internal testing** / **Internal app sharing** | Play developer account: one-time US$25 + identity verification (from general knowledge; confirm on Play Console signup) | **none** (form-factor review "None") | Up to about 100 testers (also from general knowledge). This is the cheapest route to a real car. Android Auto must be added as a form factor in Play Console. |
| Closed testing | same account | non-blocking | |
| Open testing / Production | same account | **blocking** manual car-quality review | POI fit for "stores to visit" is not explicitly listed, so review is a risk. |

None of this has been done: no account, no upload, no payment.

## 8. Play car-app quality checklist (POI) — status
- Category: POI declared. Its intended use is "discover and navigate to points of interest". A Play reviewer must accept that a work-stop list fits.
- Templates: only PlaceListMapTemplate (POI-permitted, needs MAP_TEMPLATES ✔). There is no custom UI, video, keyboard or long text.
- Driver distraction: rows have a title plus one short text line, and one tap starts navigation. There are no tasks, forms or photos in the car.
- Navigation goes to the default navigation app via ACTION_NAVIGATE ✔. No turn-by-turn is done in-app.
- Item limit: to do — read `ConstraintManager` (`CONTENT_LIMIT_TYPE_PLACE_LIST`) once real data can exceed about 6 rows.
- Error states: loading (`setLoading`), empty-with-reason message, and a toast when navigation fails ✔.
- Branding: the app icon only (APP_ICON header action). The Capacitor default launcher icon is still in place; the prod adaptive icon was not extracted (to do).
- Must work while the phone is locked. The car surface does not use the WebView, but the Firestore reads need a persisted native user (section 5).

## 9. Hebrew RTL car UX notes
- The car host lays out templates and mirrors them from the device locale; the app supplies only Hebrew strings in `values/` (the default locale).
- Keep titles short (they truncate), and use one line of secondary text (the city). Avoid mixing Latin and Hebrew in one line; store names already come in Hebrew via `hebrewName` on the web.
- Use a middot `·` separator. Numbers and times render left-to-right inside RTL text automatically.
- Use no more than two strings the driver must read at once. The navigation action is the row tap itself, so no extra button is needed.

## Docs used
- https://developer.android.com/training/cars/apps/poi
- https://developer.android.com/training/cars/apps/library/set-up-project (categories, manifest)
- https://developer.android.com/docs/quality-guidelines/car-app-quality
- https://developer.android.com/training/cars/testing/dhu
- https://developer.android.com/training/cars/testing (trusted sources / unknown sources)
- https://developer.android.com/training/cars/distribute (testing tracks, form-factor review)
- https://developer.android.com/reference/androidx/car/app/CarContext#startCarApp(android.content.Intent)
