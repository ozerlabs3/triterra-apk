# TriTerra wrapper (reconstructed) + Android Auto — `com.ozerlabs.triterra.auto`

This is a debug-only, **newly built** Capacitor wrapper with an added Android for Cars (POI) car screen. It installs **next to** the owner's app because it has a different applicationId, so it can never replace it. Nothing here touches the root download page, `triterra-prod.apk`, OTA manifests, Firebase config or deploys.

## 1. What was compared with the prod APK, and how

**Download check (binary).** `https://ozerlabs3.github.io/triterra-apk/triterra-prod.apk` and the repo's `../triterra-prod.apk` have the same sha256 (`62b82af2…5218f7`). They are **the same prod file in two places**. That is the only byte-for-byte comparison of the APK as a whole.

**Reconstruction check (config values, not binary).** This wrapper is a **new debug build**. It is **not** binary-identical to prod: its code, resources, signature and dependency versions all differ. I compared these specific parts with `aapt2` (`dump badging`, `dump xmltree`, `dump resources`), `apksigner` and `unzip`:

| Part compared | Prod APK | This wrapper | Match |
|---|---|---|---|
| `assets/capacitor.config.json` values | appName TriTerra, webDir www, `allowMixedContent:false`, FirebaseAuthentication `{skipNativeAuth:true, providers:[google.com]}`, CapacitorUpdater `{autoUpdate:false}` | same values except `appId` and `skipNativeAuth:false` (owner-approved, `.auto` only) | values |
| `assets/capacitor.plugins.json` | 3 entries: `@capacitor-firebase/authentication`, `@capacitor/contacts`, `@capgo/capacitor-updater` | same package names and classpaths (file contents equal) | text |
| `assets/public/**` (web bundle) | `index.html` injects `TRITERRA_ENV="prod"`, `TRITERRA_WRAPPER="android"`; `ota-version.json` 1.0.0 | **copied** from the prod APK by `scripts/from-prod-apk.sh` | copied files |
| Firebase client values (`google_app_id`, `default_web_client_id`, `project_id`=`triterra-921bc`, …) | resources | copied by the same script, read-only | copied values |
| manifest: package / activity | `com.ozerlabs.triterra`, `.MainActivity` (BridgeActivity) | `com.ozerlabs.triterra.auto`, same activity class name | names |
| SDK levels | min 24 / target 36 / compile 36; AGP 8.13.0 (`app-metadata.properties`) | 24 / 36 / 36 (Capacitor 8.6.2 template) | values |
| permissions (`dump badging`) | INTERNET, READ/WRITE_CONTACTS, ACCESS_NETWORK_STATE, READ_GSERVICES, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, DYNAMIC_RECEIVER… | the same list **+ `androidx.car.app.MAP_TEMPLATES`** | list |
| launcher icons (`mipmap/ic_launcher*`) | **Capacitor's default logo**: all 15 PNGs match the Capacitor template pixel for pixel (12 byte-identical, 3 differ only in PNG compression) | TriTerra icon (section 3) | intentionally different |
| signer | `CN=TriTerra, OU=Ozer Labs` (key not available) | Android Debug key | intentionally different |
| app label | TriTerra | **TriTerra Auto** (marks it as the test build) | intentionally different |

**Not compared:** `classes*.dex`, the native library versions inside the plugins, and the plugin npm versions prod was built with. The APK does not record those versions, so I used the current Capacitor 8 builds.

Corrections to the brief:
- Google sign-in is `@capacitor-firebase/authentication`, not "Capacitor Google Auth".
- `TRITERRA_ENV="prod"` is not a value `environmentForHost` honors. Production is selected by the fail-closed localhost fallback.
- The OTA manifest URL in `app.js` (`…/triterra-preview-apk/ota/prod/version.json`) currently returns 404. The `.auto` build would follow it too. OTA is neither promised nor touched.

Web↔native contract the shell relies on:
- `Capacitor.Plugins.FirebaseAuthentication.signInWithGoogle()` → `credential.idToken` → JS `signInWithCredential`.
- `Contacts` (`find` / `pickContact`).
- `CapacitorUpdater` (`notifyAppReady` / `download` / `set`).
- All three are gated on `TRITERRA_WRAPPER`.

## 2. Build

Needs JDK 21, Node 22, and an Android SDK (platform 36, build-tools 35+).

```bash
cd wrapper && npm ci
ANDROID_HOME=/path/to/sdk npm run from-apk     # www/ + Firebase client values from ../triterra-prod.apk
npx cap sync android
cd android && ./gradlew clean assembleDebug testDebugUnitTest
# -> android/app/build/outputs/apk/debug/app-debug.apk
```

There is no release signing config. `www/` and the Firebase values file are gitignored. The values are needed because the FirebaseAuthentication plugin calls `FirebaseAuth.getInstance()` in `load()`, and Capacitor does not catch the resulting exception, so without them the phone app would crash on launch. If `android/app/google-services.json` exists (after the owner registers the app, section 5), the script copies nothing and that file is used instead.

## 3. Launcher icon

Prod has no TriTerra launcher icon: its launcher is Capacitor's default logo (section 1). The TriTerra brand the prod APK does ship is the web app's PWA icon set, `assets/public/icons/triterra-512.png` and `triterra-maskable-512.png`. `scripts/icons-from-prod-apk.py` converts those mechanically into the launcher set:
- adaptive foreground: the maskable icon, scaled so its 80% safe circle maps onto Android's 66/108 dp safe zone;
- adaptive background: `#FFFAF0`, sampled from the maskable icon;
- legacy and round PNGs: resized from the same icons.

No new artwork was created. The test build is marked only by its name, "TriTerra Auto".

## 4. Android Auto module (`app/src/main/java/com/ozerlabs/triterra/car`)

**Manifest minimums:**
- `CarAppService` with `androidx.car.app.CarAppService` + `androidx.car.app.category.POI`;
- `com.google.android.gms.car.application` → `automotive_app_desc.xml` (`<uses name="template"/>`);
- `androidx.car.app.minCarApiLevel` = 1;
- permission `androidx.car.app.MAP_TEMPLATES`;
- library `androidx.car.app:app:1.7.0` (latest stable).

The host validator allows every host on debug builds and the library allowlist otherwise.

**Three states, which are never mixed** (`BranchListScreen`):

| State | When | What the driver sees |
|---|---|---|
| (a) Real data | gate passed, `Result.READY` | PlaceListMapTemplate "טריטרה · העצירות של היום" with today's real stops, or "אין עצירות מתוכננות להיום" |
| (b) Blocked | no native user or gate failed → `SIGN_IN_REQUIRED`; a read failed → `LOAD_FAILED` | MessageTemplate "נדרשת התחברות לטריטרה בטלפון כדי להציג את העצירות של היום" (or "לא ניתן לטעון…") with "נסו שוב". **No stop list.** |
| (c) Demo | **debug builds only**, and only when the driver taps "מצב הדגמה" on (b) | separate screen titled "מצב הדגמה · נתוני דמה", 3 rows named "סניף לדוגמה – …" with text "נתוני דמה" |

The repository can only return real stops or a blocked status (`Result.blocked()` always carries an empty list). Demo data is not a repository result, so it can never stand in for real data. Release builds have no way to reach demo mode.

**Navigation (two taps):** tap a stop row, which opens a PaneTemplate (name + **"נווט"**). "נווט" calls `startCarApp(ACTION_NAVIGATE, geo:lat,lng)`, falling back to `geo:0,0?q=<name>` when there are no coordinates. The host then opens the default navigation app (Waze or Google Maps).

This replaces the previous one-tap rows, which were invalid. `PlaceListMapTemplate.Builder.setItemList` calls `ModelUtils.validateAllNonBrowsableRowsHaveDistance`, which throws `IllegalArgumentException("All non-browsable rows must have a distance span …")` when non-browsable rows outnumber rows that have a `DistanceSpan` (checked by disassembling car-app 1.7.0). A distance needs the car's location, which would need a location permission, so stop rows are now **browsable** and open the detail Pane. That is the standard POI pattern.

**Host content limits:**
- The map list shows at most `ConstraintManager.getContentLimit(CONTENT_LIMIT_TYPE_PLACE_LIST)` rows. That is the limit type for PlaceListMapTemplate; `CONTENT_LIMIT_TYPE_LIST` applies to ListTemplate. Hosts below car API 2 use 6.
- When stops overflow, the last row becomes a browsable "+N עצירות נוספות" that opens a ListTemplate page (limit `CONTENT_LIMIT_TYPE_LIST`).
- If even that overflows, the page ends with a non-clickable note "+N נוספות · יופיעו אחרי סיום עצירות קודמות". ListTemplate allows non-clickable rows.
- The split logic is `TodayPlan.visibleStops` and is unit-tested.

**Host-driven invalidation:**
- `onGetTemplate()` only renders the current state, because the host may call it at any time.
- Loading happens in `reload()`, which is triggered by the constructor, the host's content-refresh callback (`setOnContentRefreshListener`, car API 5+) and "נסו שוב".
- A generation counter drops results from superseded loads, and nothing is invalidated after the screen is destroyed.

## 5. Data layer (contract + appendix, field-region-map main@4bdaab9)

`FirestoreTodayPlanRepository` is **read-only**: it uses only `get()` and has no writes, transactions, Storage calls, Worker/Sheets calls or rules changes. Identity comes only from the signed-in user. Each step runs only after the previous one passes:

1. **Native user.** A Firebase user must exist in the native SDK, which `skipNativeAuth:false` makes possible.
2. **Profile** `users/{uid}`, mirroring `validateProfile`:
   - `active === true`;
   - `schemaVersion` is the integer 1;
   - field `email` matches the account, trimmed and lowercased;
   - `regionId` is present.
3. **Region** `regions/{regionId}`: `active === true`, `schemaVersion` 1, and its `regionId` equals the profile's.
4. **Reads:**
   - `users/{uid}/myDays/{YYYY-MM-DD Asia/Jerusalem}`, consumed as the whole `{state, revision, updatedAt}` envelope; a missing doc means the day hasn't started;
   - `stores where regionId ==`;
   - `users/{uid}/storeOverrides`.
5. **Auth-epoch fence:** the result is dropped if the uid changed during the reads.

**Field mapping, implemented exactly per the appendix and checked against the web code shipped in the APK (`data-quality.js`, `user-stores.js`):**
- **Order:** `state.route` is authoritative and `manualOrder` is never read. `doneIds` and `skippedIds` are dropped, the `currentId` stop is shown first, and ids are compared as `String(id)`.
- **Name:** the stop's inline snapshot `name`, which is already the Hebrew display name.
- **Fresh store:**
  - a base store is dropped if its override has `hidden === true`;
  - an override **with** a truthy `name` patches `lat`/`lng` only when they are real finite numbers (`Number.isFinite`, no string coercion), and otherwise the base value stays;
  - an override **without** a name leaves the store untouched;
  - otherwise it is a user-added store (`userAdded === true`, truthy `name`, finite point), and malformed ones are skipped.
- **Coordinates:** `effectiveCoordinates(fresh, override)`. A `coordinateOverride {siteId, newLat, newLng}` wins only if `siteId === id` exactly and both values are finite; otherwise the store's own point is used. `finite()` rejects `null`, `undefined` and `''`, and coerces numeric strings.
- **Snapshot fallback:** when there is no fresh store (a fixed warehouse or office, or a store hidden after the day started), the stop's own snapshot coordinates are used. The fixed-facility constants are **not** copied into the app.
- The pure logic is in `TodayPlan` and covered by `TodayPlanTest`.

Simplifications: only the coordinate fields of `applyStoreOverrides` are mirrored, because the car shows only the name and a point. `Number()` coercion is mirrored for numbers and numeric strings only (marked `shortcut:` in code). `myDaySettings/preferences` and `branch-hours.json` are not read, because no car feature needs them.

## 6. Auth: what changed and what is still blocked

- **Done (owner-approved, `.auto` only):** `skipNativeAuth: false` in `wrapper/capacitor.config.json`. The native SDK now also holds and persists the user, so the car's Firestore reads can work after a restart and while the phone is locked. Prod config is untouched.
- **Still expected to fail until the console steps below are done.** I did not run it: there is no emulator in the build container. Native Google sign-in in the `.auto` app should be rejected with a developer-console configuration error (Credential Manager "[28444] Developer console is not set up correctly" / `DEVELOPER_ERROR 10`). That is because `triterra-921bc` has no Android app or OAuth client for package `com.ozerlabs.triterra.auto` with the build machine's debug SHA-1. Prod's own registration (package `com.ozerlabs.triterra`, signer SHA-1 `A6:05:05:77:F4:6D:2E:4C:90:32:5B:D6:87:DD:78:9A:8E:28:C0:09`) is inferred from prod sign-in working; I have not seen it in the console. Until sign-in works, the car shows state (b).

### BLOCKED DECISIONS for the owner (none performed)
1. **App registration:** in the Firebase console, project `triterra-921bc`, add an Android app with package `com.ozerlabs.triterra.auto`.
2. **SHA-1:** add the debug SHA-1 of the machine that will build the APK (`./gradlew signingReport`, variant debug). Do not register the SHA-1 from the cloud container (`67:94:BE:03:…:D1:B4`); its debug key is temporary.
3. **google-services.json:** download the new app's file into `wrapper/android/app/` (gitignored). The build then uses it in place of the values copied from the prod APK.
4. **API key restriction check:** in Google Cloud console → APIs & Services → Credentials, check whether the project's Android API key is restricted to specific apps. If it is, add `com.ozerlabs.triterra.auto` with the same SHA-1.
5. **Optional preview path:** project `triterra-preview` uses anonymous sign-in and demo data. Using it would need:
   - (i) an Android app for `com.ozerlabs.triterra.auto` registered in `triterra-preview` (anonymous auth needs no SHA-1);
   - (ii) the preview web bundle (`TRITERRA_ENV="preview"`);
   - (iii) a preview-only gate in the car, since anonymous users have no email and the prod gate correctly rejects them.

   None of this is built.

## 7. Test on the Desktop Head Unit (DHU)
1. Android Studio → SDK Manager → SDK Tools → **Android Auto Desktop Head Unit Emulator** (`$ANDROID_HOME/extras/google/auto/`). On Linux: `chmod +x desktop-head-unit`, plus glibc 2.32+, libc++1 and libc++abi1.
2. `adb install -r app-debug.apk`. It appears as **TriTerra Auto** next to TriTerra.
3. On the phone (Android 9+; on Android 10+ update Android Auto from Play first): Android Auto settings → About → tap **Version** 10× → allow development settings → overflow menu → **Start head unit server**.
4. `adb forward tcp:5277 tcp:5277`, then `./desktop-head-unit --usb` (or without `--usb`).
5. Expected:
   - Open TriTerra Auto: you see the sign-in-required message (state b), because the console registration isn't done.
   - Tap **מצב הדגמה**: you see "מצב הדגמה · נתוני דמה" with 3 sample stops on the map.
   - Tap a stop, then **נווט**: Waze or Maps opens.

A real car needs a Google Play install. Android Auto's "Unknown sources" setting does not cover Car App Library apps.

## 8. Distribution (analysis only — DECISION NEEDED)

| Route | Cost | Car review | Notes |
|---|---|---|---|
| DHU on a computer | free | none | for development and demos; does not reach a real car |
| Play **Internal testing** / **Internal app sharing** | Play developer account: one-time US$25 + identity verification (general knowledge; confirm at signup) | **none** | the cheapest route to a real car; add Android Auto as a form factor |
| Closed testing | same account | non-blocking | |
| Open testing / Production | same account | **blocking** manual car-quality review | a work-stop list is not an explicitly listed POI type, so review is a risk |

Nothing has been done: no account, upload or payment.

## 9. Play car-app quality checklist (POI) — status
- POI category declared; whether a work-stop list fits is the reviewer's call.
- Templates: PlaceListMapTemplate, PaneTemplate, ListTemplate and MessageTemplate only, all allowed for POI. No custom drawing, keyboard, video or long text.
- Host list limits respected (section 4).
- Driver distraction: short rows; navigation takes two taps (row → "נווט"); no forms, photos or tasks in the car.
- Navigation is handed to the default navigation app via `ACTION_NAVIGATE`; there is no in-app turn-by-turn.
- Loading, empty, sign-in-required, failed-load and failed-navigation states are all shown.
- Works with the phone locked: no WebView, and the native session persists thanks to `skipNativeAuth:false` (once sign-in works).
- Branding: TriTerra launcher icon, shown by the host as the APP_ICON header.
- Lint (`:app:lintDebug`): 0 errors. One warning: `hosts_allowlist_sample` is a private library resource (non-debug host allowlist, the approach the docs' samples use).

## 10. Hebrew RTL car UX notes
- The host lays out and mirrors templates from the device locale; the app supplies Hebrew strings as the default `values/`.
- Titles are short and end with a state word ("העצירות של היום" / "מצב הדגמה · נתוני דמה") so the states can't be confused at a glance.
- Real rows show only the Hebrew snapshot name; Latin city keys are not shown.
- Separators are `·`. Numbers in "+N" render left-to-right inside RTL text automatically.
- At most one secondary line and one action ("נווט") per screen.

## Docs used
- https://developer.android.com/training/cars/apps/poi
- https://developer.android.com/training/cars/apps/library/set-up-project
- https://developer.android.com/docs/quality-guidelines/car-app-quality
- https://developer.android.com/training/cars/testing/dhu
- https://developer.android.com/training/cars/testing
- https://developer.android.com/training/cars/distribute
- https://developer.android.com/reference/androidx/car/app/constraints/ConstraintManager
- https://developer.android.com/reference/androidx/car/app/CarContext#startCarApp(android.content.Intent)
