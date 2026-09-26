# CLAUDE.md

Fork of [Bridge Launcher](https://github.com/bridgelauncher/launcher) (upstream by Tored, last upstream release v0.1.0-alpha). Bridge is an Android launcher that hosts a user-chosen web project (HTML/JS/CSS) in a WebView and exposes Android capabilities to it through a JS API.

The goal of this fork is to **add new API capabilities** needed by our web launcher, [njpd57/gingerbread-bridge-launcher](https://github.com/njpd57/gingerbread-bridge-launcher) (branch `dev`, Vue 3 + TS). The roadmap, with a proposed API and implementation notes for each feature, is on Confluence: **"Mejoras propuestas a Bridge (fork)"**, https://quickware.atlassian.net/wiki/spaces/~712020439862fa4a724279bd9c184cf15d81ab/pages/5373953 (in Spanish). Read it before starting a feature and follow its recommended order.

## Scope rules

- Focus on the new features. Do **not** refactor, rename or "clean up" existing code (the `api2`/`ui2`/`settings2` naming, `HomeScreen2`, etc.) unless a feature requires it.
- Known tech debt, leave alone unless asked: `services/iconpacks/` and `services/iconpackcache/` are near-duplicate packages (each has its own `AppFilterXMLParser` and `InstalledIconPacksHolder`). `BridgeLauncherApplication` wires `iconpackcache`, while some UI code (`AppDrawerVM`, `AppIcon`, `ExportForMock`) still imports `iconpacks`.
- Branch: `main` (there is no `master`).

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
- **`api2/webview/`:** `BridgeWebViewClient`, and `BridgeWebChromeClient`, which currently only forwards console messages.
- **`services/`:** state holders (apps, perms, insets, UI mode, lifecycle events, …), caches, and system components (accessibility service, device admin, QS tile, broadcast receiver, notification listener).
- **System-created components** (accessibility service, `BridgeNotificationListenerService`) aren't built in `createServices()`. They reach the services through `bridgeLauncherApplication.services`, and expose themselves through a `companion object { var instance }` for actions that need them (e.g. `cancelNotification`). `NotificationsHolder` keeps the active notifications and throttles `Posted` events per key. `QuickSettingsHolder` exposes the toggles apps can still change (flashlight, brightness, auto-rotate, master sync) as flows, observing the system settings; Wi-Fi, Bluetooth, NFC and location can only be opened (`SystemPanelStringOptions`). `MediaSessionsHolder` follows the playing media session; Android only lists sessions to notification listeners, so `BridgeNotificationListenerService` starts and stops it. `QuickSettingsHolder` also reports whether Wi-Fi, Bluetooth and location are on (Bluetooth's broadcast needs a runtime permission from Android 12, so it's re-read when the home screen regains focus: `HomeScreenActivity.onWindowFocusChanged`). `AppShortcutsHolder` wraps `LauncherApps` for apps' shortcuts (only the default launcher may read them, Android 7.1+). `CalendarHolder` reads calendar event instances (READ_CALENDAR) and signals changes. `ConnectivityHolder` reports the network in use, Wi-Fi and mobile signal levels (0 to 4) and mobile data activity, without runtime permissions.
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

- **Name mismatch bug:** Kotlin has `getCanSetSystemNightMode()`, but `Bridge.d.ts` declares `getCanRequestSystemNightMode()`. Calling a method missing from the JS interface throws, which broke loading in our launcher. This is roadmap item #1. The fix is to add the `getCanRequestSystemNightMode` alias, and it's a good upstream PR candidate.
- `@JavascriptInterface` methods run on a WebView background thread, not the main thread. Anything that touches Views or the WebView must be posted to the main thread. Settings writes go through `tryEditPrefs` (which uses `runBlocking`).
- `HomeScreenActivity` declares `configChanges` including `orientation` in the manifest and handles rotation itself. It has no `screenOrientation`.
- **Runtime permissions with Android's dialog** (e.g. READ_CALENDAR) go through `BridgeRuntimePermissionRequester`, registered by `HomeScreenActivity` and handed to `JSToBridgeAPI.permissionRequester` via `HomeScreen2VM.afterCreate`; when a permission was refused for good, it opens Bridge's app settings instead.
- **Start other apps through `tryRunInHomescreenContext`**: it adds `FLAG_ACTIVITY_NEW_TASK` so they get their own task instead of stacking on top of the home screen. `homeScreenContext` is only cleared by the activity that registered it (a new instance can register before the old one is destroyed).
- `WebView.setWebContentsDebuggingEnabled` is static and must be applied before the WebView is created.
