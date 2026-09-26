# CLAUDE.md

Fork of [Bridge Launcher](https://github.com/bridgelauncher/launcher) (upstream by Tored, last upstream release v0.1.0-alpha). Bridge is an Android launcher that hosts a user-chosen web project (HTML/JS/CSS) in a WebView and exposes Android capabilities to it through a JS API.

The goal of this fork is to **add new API capabilities** needed by our web launcher, [njpd57/gingerbread-bridge-launcher](https://github.com/njpd57/gingerbread-bridge-launcher) (branch `dev`, Vue 3 + TS). The roadmap, with a proposed API and implementation notes for each feature, is on Confluence: **"Mejoras propuestas a Bridge (fork)"**, https://quickware.atlassian.net/wiki/spaces/~712020439862fa4a724279bd9c184cf15d81ab/pages/5373953 (in Spanish). Read it before starting a feature and follow its recommended order.

## Fork status (2026-09-26)

Everything below is on `main`, tested on the Flip5 and used by the web launcher (see its `FEATURES.md`). The Confluence page still describes most of it as a proposal.

**Added to the JS API** (all in `JSToBridgeAPI.kt` / `BridgeToJSAPI.kt`, grouped by `// region`):
- Night mode: `getCanRequestSystemNightMode()` (alias of `getCanSetSystemNightMode()`, matching `Bridge.d.ts`).
- Orientation: `getScreenOrientation`, `requestSetScreenOrientation('portrait' | 'unspecified')`, event `screenOrientationChanged`; also a checkbox in Bridge settings (Overlays).
- `<input type="file">` opens Android's picker (`BridgeWebChromeClient.onShowFileChooser`, no JS method).
- URLs and default apps: `requestOpenUrl` (http/https/tel/mailto/sms/smsto/geo only), `getDefaultAppPackageName('dialer' | 'browser' | 'sms' | 'email' | 'camera')`.
- Notifications (needs notification access): `getCanReadNotifications`, `requestOpenNotificationAccessSettings`, `getNotificationsURL`, `getNotificationIconURL`, `requestOpenNotification`, `requestDismissNotification`; events `canReadNotificationsChanged`, `notificationPosted`, `notificationRemoved`. Notifications carry `isMedia` and their `actions` (buttons), run with `requestNotificationAction` / `requestReplyToNotification` (typed replies through `RemoteInput`).
- Quick settings: `requestOpenSystemPanel`, flashlight, brightness (auto / level), auto-rotate, master sync, `getWifiEnabled`, `getBluetoothEnabled`, `getLocationEnabled`, and "Modify system settings" (`getCanWriteSystemSettings`, `requestOpenWriteSystemSettingsPermission`), each with its `…Changed` event.
- Music (needs notification access): `getMediaSession`, `getMediaArtURL`, `requestMediaAction`, `requestOpenMediaApp`, event `mediaSessionChanged`.
- Connectivity: `getConnectivity` (network in use, Wi-Fi and mobile levels 0–4, data activity measured from `TrafficStats`), event `connectivityChanged`.
- App shortcuts: `getCanAccessAppShortcuts`, `getAppShortcutsURL`, `getAppShortcutIconURL`, `requestStartAppShortcut`.
- App usage (special "Usage access"): `getCanReadUsageStats`, `requestOpenUsageAccessSettings`, `getAppUsageURL(from, to)` (time in foreground, opens and last use per app, computed from activity events), event `canReadUsageStatsChanged`.
- Calendar (READ_CALENDAR, asked with Android's dialog): `getCanReadCalendar`, `requestCalendarPermission`, `getCalendarEventsURL`, `requestOpenCalendarEvent`, `requestOpenCalendarAt`; events `canReadCalendarChanged`, `calendarChanged`.
- Contacts (READ_CONTACTS, asked with Android's dialog): `getCanReadContacts`, `requestContactsPermission`, `getContactsURL(query, starredOnly, limit)` (contacts with a phone number, matched by name/number, primary number first), `getContactPhotoURL`, `requestOpenContact`; events `canReadContactsChanged`, `contactsChanged`. Calling (CALL_PHONE, a separate permission): `getCanCallPhone`, `requestCallPhonePermission`, `requestCallPhoneNumber` (calls directly with the permission, otherwise opens the dialer), event `canCallPhoneChanged`. Used by the launcher's idea 37 (contacts in search).

**Fixed:** intermittent `ERR_NAME_NOT_RESOLVED` (the WebView clients were set after the first `loadUrl`); "homeScreenContext is null" after opening another app; `URLWithQueryBuilder.addParams` (the batch overload `getBridgeApiEndpointURL` uses) added every pair regardless of value, so a `null` param (e.g. an empty `getContactsURL` query) rendered as the literal text `null` in the URL instead of being omitted — the endpoint then filtered contacts by the string "null" and returned none. `addParams` now defers to `addParam`, which already skipped nulls. **Also fixed (found by the launcher, also present in upstream):** `WindowInsetsSnapshot.getSnapshot()` passed `(left, top, right, bottom)` positionally to a constructor declared `(top, left, right, bottom)`, so top and left were swapped in every inset (getters and events) — on the Flip5 in portrait the status bar arrived as `left: 33, top: 0`; now passed by name. Insets events were also sent as `ImeWindowInsetsChanged` (the enum's PascalCase name) with the value in `insets`, instead of `imeWindowInsetsChanged` / `newValue` as the API types say; now built from `rawValue` with a `newValue` field. Added `getWindowInsetsSwapFixed()` (always `true`) so launchers can tell a fixed build from stock Bridge, which still has the swap.

**Deprioritized by the user:** 1.6 remote debugging, `requestOpenDarkModeSettings`, a generic `requestStartActivity`. **Also deprioritized:** 3.1 native widgets (the user prefers HTML widgets in the launcher; if revisited, host `AppWidgetHostView`s offscreen, serve snapshots to the WebView and forward taps, rather than overlaying native views). **Remaining from the roadmap:** nothing. **Left, together (user's decision):** updating the Confluence page with the real status. Roadmap item 2.1 (status bar insets) turned out to be the top/left swap, now fixed above.

## Scope rules

- Focus on the new features. Do **not** refactor, rename or "clean up" existing code (the `api2`/`ui2`/`settings2` naming, `HomeScreen2`, etc.) unless a feature requires it.
- Known tech debt, leave alone unless asked: `services/iconpacks/` and `services/iconpackcache/` are near-duplicate packages (each has its own `AppFilterXMLParser` and `InstalledIconPacksHolder`). `BridgeLauncherApplication` wires `iconpackcache`, while some UI code (`AppDrawerVM`, `AppIcon`, `ExportForMock`) still imports `iconpacks`.
- Branch: `main` (there is no `master`).
- **Commits:** Claude commits and pushes this repo once the user confirms a change works on the phone. The web launcher repo is committed by its own Claude session: leave launcher changes uncommitted and list the files (two sessions sharing its git index swept each other's staged files into the wrong commit).

## Build & run

Terminal-only workflow (no Android Studio):

```sh
./gradlew assembleDebug                       # APK -> app/build/outputs/apk/debug/
adb install -r app/build/outputs/apk/debug/*.apk
adb logcat | grep -i bridge                   # Bridge logs
```

- Requires JDK 17 and an Android SDK (compileSdk 34) set via `ANDROID_HOME` or `local.properties` (`sdk.dir=...`). `scripts/setup-dev-env.sh` installs both on Ubuntu (SDK in `~/Android/Sdk`); adb comes from the system package, not the SDK's platform-tools.
- AGP 8.5.2, Gradle 8.9, Kotlin 1.9.24, Compose compiler 1.5.14. minSdk 24, targetSdk 34.
- Test device: Samsung Galaxy Z Flip5 (One UI), connected over adb.
- **adb is only for installing the APK.** Do not use adb to take screenshots, drive the UI (`input`, `uiautomator`), start activities or inspect state to test changes. After installing, hand testing to the user: give them a short checklist of what to try on the phone and what should happen, then wait for their results.
- `WRITE_SECURE_SETTINGS` (needed to set system night mode) can't be requested at runtime; it can only be granted over adb. The user runs `scripts/grant-permissions.sh` once per fresh install. The grant survives `adb install -r` but is lost on uninstall. If a new feature needs another adb-only permission, add it to that script.
- Signing: a self-built APK has a different signature from the released Bridge, so it can't be installed over it. Either uninstall the original, which loses its settings and the `WRITE_SECURE_SETTINGS` grant, or change `applicationId` so both can coexist. Ask before changing `applicationId`.
- Tests are not a priority for now. Don't add them unless asked.

## Architecture

Single module `app/`, package `com.tored.bridgelauncher` (paths below are relative to `app/src/main/java/com/tored/bridgelauncher/`).

- **Manual DI:** every service is constructed in `BridgeLauncherApplication.createServices()` and collected in the `BridgeServices` data class. This is deliberate: no DI library. A new service is added to both places, and started in `startup()` if it needs it. Activities and VMs get it via `(application as BridgeLauncherApplication).services`.
- **`api2/jstobridge/JSToBridgeAPI.kt`:** methods callable from JS as `window.Bridge.*`, registered in `ui2/home/composables/WebViewSetup.kt` via `addJavascriptInterface(jsToBridgeAPI, "Bridge")`.
- **`api2/bridgetojs/`:** events pushed to JS. `BridgeToJSAPI.sendBridgeEvent(model)` calls `onBridgeEvent(json)` in the WebView. There is one event class per event in `events/<group>/`.
- **`api2/server/`:** `BridgeServer` intercepts WebView requests to a virtual host. It serves the project files (`BridgeFileServer`) and JSON/image endpoints (`endpoints/*Endpoint.kt`). Use this for anything large or binary (lists, icons, album art) instead of returning it from a JS interface method.
- **`api2/webview/`:** `BridgeWebViewClient`; `BridgeWebChromeClient`, which forwards console messages and opens the file picker through `BridgeFileChooser`; `BridgeRuntimePermissionRequester`. The last two are implemented by `HomeScreenActivity` and handed over in `HomeScreen2VM.afterCreate`.
- **`services/`:** state holders (apps, perms, insets, UI mode, lifecycle events, …), caches, and system components (accessibility service, device admin, QS tile, broadcast receiver, notification listener).
- **System-created components** (accessibility service, `BridgeNotificationListenerService`) aren't built in `createServices()`. They reach the services through `bridgeLauncherApplication.services`, and expose themselves through a `companion object { var instance }` for actions that need them (e.g. `cancelNotification`). `NotificationsHolder` keeps the active notifications and throttles `Posted` events per key. `QuickSettingsHolder` exposes the toggles apps can still change (flashlight, brightness, auto-rotate, master sync) as flows, observing the system settings; Wi-Fi, Bluetooth, NFC and location can only be opened (`SystemPanelStringOptions`). `MediaSessionsHolder` follows the playing media session; Android only lists sessions to notification listeners, so `BridgeNotificationListenerService` starts and stops it. `QuickSettingsHolder` also reports whether Wi-Fi, Bluetooth and location are on (Bluetooth's broadcast needs a runtime permission from Android 12, so it's re-read when the home screen regains focus: `HomeScreenActivity.onWindowFocusChanged`). `AppShortcutsHolder` wraps `LauncherApps` for apps' shortcuts (only the default launcher may read them, Android 7.1+). `CalendarHolder` reads calendar event instances (READ_CALENDAR) and signals changes. `UsageStatsHolder` reads app usage from `UsageStatsManager` events (usage access is an app op; `PermsHolder.checkCanReadUsageStats` is wired to it). `ConnectivityHolder` reports the network in use, Wi-Fi and mobile signal levels (0 to 4) and mobile data activity, without runtime permissions.
- **`services/settings2/`:** settings stored in DataStore. They are declared in `BridgeSettings` and read as flows with `useBridgeSettingStateFlow`.
- **`ui2/`:** Compose screens (`home`, `appdrawer`, `settings`, `devconsole`, `dirpicker`). Each screen is split into `*VM`, `*Actions`, `*State` and `composables/`. `HomeScreenActivity` hosts the WebView.

## Adding an API capability (the core workflow)

1. **Kotlin:**
   - **Method:** add an `@JavascriptInterface` function to `JSToBridgeAPI.kt`, in the matching `// region`.
     - Action methods are named `request…(…, showToastIfFailed: Boolean = true): Boolean` and wrap their body in `_app.tryRun(showToastIfFailed) { … }`, or `tryRunInHomescreenContext` if they need an Activity context.
     - Getters are named `get…()`.
     - Return complex data as a JSON string (kotlinx.serialization), or expose it as a `get…URL()` pointing to a `BridgeServer` endpoint.
   - **Event:** add a model class in `api2/bridgetojs/events/<group>/`, then emit it from `BridgeToJSAPI.kt`.
   - Declare any new permissions, services or receivers in `AndroidManifest.xml`. Gate version-specific calls with `utils/CurrentAndroidVersion`.
2. **Types and mock:** the web launcher is cloned at `~/Proyectos/api-tester` (the folder name is historical; it has its own CLAUDE.md). The upstream `api`/`api-mock` repos are not forked. Instead, declare new methods there with **exactly the same name** as in Kotlin:
   - in `src/types/bridge-fork.d.ts`, by module augmentation of `JSToBridgeAPI`;
   - in `ForkBridgeMock`, in `src/mock/injectBridgeMockInDev.ts`.
   `BridgeEventMap` upstream is a `type`, so new events can't be added by augmentation. Type them locally where the launcher handles them.
3. **Consumer:** the web launcher must guard every new call with `bridgeHas('methodName')` and fall back gracefully, so it keeps working on stock Bridge. Run `npm run type-check` and `npx vitest run --dir src` there. The user deploys it with `npm run deploy`.
4. **Verify on device:** build, install over adb, then ask the user to test it with a checklist (see "Build & run").

## Code style

Follow the existing style:
- Braces on their own line (Allman) for classes and functions.
- Private fields prefixed with `_` (`_app`, `_scope`).
- Constructor-injected dependencies.
- `private const val TAG = "…"` per file for logging.
- Kotlin official code style otherwise.

## Gotchas

- **Calling a method missing from the JS interface throws** in JS, which once broke loading the whole launcher (`Bridge.d.ts` declared `getCanRequestSystemNightMode`, which upstream lacks; the fork now has it as an alias). Launchers must guard fork methods with `bridgeHas()`. The alias and the `ERR_NAME_NOT_RESOLVED` fix are good upstream PR candidates.
- `@JavascriptInterface` methods run on a WebView background thread, not the main thread. Anything that touches Views or the WebView must be posted to the main thread. Settings writes go through `tryEditPrefs` (which uses `runBlocking`).
- `HomeScreenActivity` declares `configChanges` including `orientation` in the manifest and handles rotation itself. It has no `screenOrientation`.
- **Runtime permissions with Android's dialog** (e.g. READ_CALENDAR) go through `BridgeRuntimePermissionRequester`, registered by `HomeScreenActivity` and handed to `JSToBridgeAPI.permissionRequester` via `HomeScreen2VM.afterCreate`; when a permission was refused for good, it opens Bridge's app settings instead.
- **Start other apps through `tryRunInHomescreenContext`**: it adds `FLAG_ACTIVITY_NEW_TASK` so they get their own task instead of stacking on top of the home screen. `homeScreenContext` is only cleared by the activity that registered it (a new instance can register before the old one is destroyed).
- `WebView.setWebContentsDebuggingEnabled` is static and must be applied before the WebView is created.
