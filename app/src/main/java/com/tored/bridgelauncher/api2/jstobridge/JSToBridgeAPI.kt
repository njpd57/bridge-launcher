package com.tored.bridgelauncher.api2.jstobridge

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Notification
import android.app.UiModeManager
import android.app.WallpaperManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import com.tored.bridgelauncher.BridgeLauncherApplication
import com.tored.bridgelauncher.api2.server.BridgeServer
import com.tored.bridgelauncher.api2.server.endpoints.AppIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppShortcutIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppShortcutsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.ProfileAppIconsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.CalendarEventsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.AppUsageEndpoint
import com.tored.bridgelauncher.services.usage.UsageStatsHolder
import com.tored.bridgelauncher.services.contacts.ContactsHolder
import com.tored.bridgelauncher.api2.server.endpoints.ContactsEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.ContactPhotosEndpoint
import com.tored.bridgelauncher.utils.startUsageAccessSettingsActivity
import android.app.RemoteInput
import com.tored.bridgelauncher.api2.webview.BridgeRuntimePermissionRequester
import com.tored.bridgelauncher.api2.server.endpoints.IconPackContentEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.IconPacksEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.MediaArtEndpoint
import com.tored.bridgelauncher.api2.server.endpoints.NotificationIconsEndpoint
import com.tored.bridgelauncher.api2.server.getBridgeApiEndpointURL
import com.tored.bridgelauncher.api2.shared.BridgeButtonVisibilityStringOptions
import com.tored.bridgelauncher.api2.shared.BridgeThemeStringOptions
import com.tored.bridgelauncher.api2.shared.DefaultAppRoleStringOptions
import com.tored.bridgelauncher.api2.shared.MediaActionStringOptions
import com.tored.bridgelauncher.api2.shared.OverscrollEffectsStringOptions
import com.tored.bridgelauncher.api2.shared.RingerModeStringOptions
import com.tored.bridgelauncher.api2.shared.ScreenOrientationStringOptions
import com.tored.bridgelauncher.api2.shared.SystemBarAppearanceStringOptions
import com.tored.bridgelauncher.api2.shared.SystemNightModeStringOptions
import com.tored.bridgelauncher.api2.shared.SystemPanelStringOptions
import com.tored.bridgelauncher.services.displayshape.DisplayShapeHolder
import com.tored.bridgelauncher.services.alarm.AlarmHolder
import com.tored.bridgelauncher.services.alarm.SerializableNextAlarm
import com.tored.bridgelauncher.services.battery.BatteryHolder
import com.tored.bridgelauncher.services.battery.SerializableBattery
import com.tored.bridgelauncher.services.connectivity.ConnectivityHolder
import com.tored.bridgelauncher.services.connectivity.SerializableConnectivity
import com.tored.bridgelauncher.services.media.MediaSessionsHolder
import com.tored.bridgelauncher.services.media.SerializableMediaSession
import com.tored.bridgelauncher.services.notifications.NotificationsHolder
import com.tored.bridgelauncher.services.quicksettings.QuickSettingsHolder
import com.tored.bridgelauncher.services.shortcuts.AppShortcutsHolder
import com.tored.bridgelauncher.services.apps.ProfileAppsHolder
import com.tored.bridgelauncher.services.files.FileSaver
import com.tored.bridgelauncher.api2.webview.BridgeFileChooser
import com.tored.bridgelauncher.services.calendar.CalendarHolder
import com.tored.bridgelauncher.services.perms.PermsHolder
import android.content.ContentUris
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.tored.bridgelauncher.services.quicksettings.ScreenBrightness
import com.tored.bridgelauncher.services.settings2.BridgeSetting
import com.tored.bridgelauncher.services.settings2.BridgeSettings
import com.tored.bridgelauncher.services.settings2.getIsBridgeAbleToLockTheScreen
import com.tored.bridgelauncher.services.settings2.setBridgeSetting
import com.tored.bridgelauncher.services.settings2.settingsDataStore
import com.tored.bridgelauncher.services.settings2.useBridgeSettingStateFlow
import com.tored.bridgelauncher.services.system.BridgeLauncherAccessibilityService
import com.tored.bridgelauncher.services.system.BridgeNotificationListenerService
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsHolder
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsOptions
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsSnapshot
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import com.tored.bridgelauncher.utils.checkCanReadNotifications
import com.tored.bridgelauncher.utils.checkCanAccessNotificationPolicy
import com.tored.bridgelauncher.utils.checkCanWriteSystemSettings
import com.tored.bridgelauncher.utils.getIsSystemInNightMode
import com.tored.bridgelauncher.utils.launchApp
import com.tored.bridgelauncher.utils.messageOrDefault
import com.tored.bridgelauncher.utils.openAppInfo
import com.tored.bridgelauncher.utils.openUrl
import com.tored.bridgelauncher.utils.q
import com.tored.bridgelauncher.utils.requestAppUninstall
import com.tored.bridgelauncher.utils.showErrorToast
import com.tored.bridgelauncher.utils.startAndroidSettingsActivity
import com.tored.bridgelauncher.utils.startBridgeAppDrawerActivity
import com.tored.bridgelauncher.utils.startBridgeSettingsActivity
import com.tored.bridgelauncher.utils.startDevConsoleActivity
import com.tored.bridgelauncher.utils.startNotificationAccessSettingsActivity
import com.tored.bridgelauncher.utils.startNotificationPolicyAccessSettingsActivity
import com.tored.bridgelauncher.utils.startWriteSystemSettingsPermissionActivity
import com.tored.bridgelauncher.utils.startWallpaperPickerActivity
import com.tored.bridgelauncher.utils.toPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.json.Json

private const val TAG = "JSToBridge"

class JSToBridgeAPI(
    private val _app: BridgeLauncherApplication,
    private val _battery: BatteryHolder,
    private val _alarm: AlarmHolder,
    private val _windowInsetsHolder: WindowInsetsHolder,
    private val _displayShapeHolder: DisplayShapeHolder,
    private val _notifications: NotificationsHolder,
    private val _quickSettings: QuickSettingsHolder,
    private val _media: MediaSessionsHolder,
    private val _connectivity: ConnectivityHolder,
    private val _shortcuts: AppShortcutsHolder,
    private val _calendar: CalendarHolder,
    private val _perms: PermsHolder,
    private val _usage: UsageStatsHolder,
    private val _contacts: ContactsHolder,
    private val _profileApps: ProfileAppsHolder,
    private val _fileSaver: FileSaver,
)
{
    private val _scope = CoroutineScope(Dispatchers.Main)

    private val _pm = _app.packageManager
    private val _wallman = _app.getSystemService(Context.WALLPAPER_SERVICE) as WallpaperManager
    private val _modeman = _app.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
    private val _dpman = _app.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    var webView: WebView? = null
    var homeScreenContext: Context? = null
    var permissionRequester: BridgeRuntimePermissionRequester? = null
    var fileChooser: BridgeFileChooser? = null


    // SETTING STATES

    private fun <TPreference, TResult> s(setting: BridgeSetting<TPreference, TResult>) = useBridgeSettingStateFlow(_app.settingsDataStore, _scope, setting)
    private val _isDeviceAdminEnabled = s(BridgeSettings.isDeviceAdminEnabled)
    private val _isAccessibilityServiceEnabled = s(BridgeSettings.isAccessibilityServiceEnabled)
    private val _theme = s(BridgeSettings.theme)
    private val _allowProjectsToTurnScreenOff = s(BridgeSettings.allowProjectsToTurnScreenOff)
    private val _statusBarAppearance = s(BridgeSettings.statusBarAppearance)
    private val _navigationBarAppearance = s(BridgeSettings.navigationBarAppearance)
    private val _showBridgeButton = s(BridgeSettings.showBridgeButton)
    private val _drawSystemWallpaperBehindWebView = s(BridgeSettings.drawSystemWallpaperBehindWebView)
    private val _drawWebViewOverscrollEffects = s(BridgeSettings.drawWebViewOverscrollEffects)
    private val _lockHomeScreenToPortrait = s(BridgeSettings.lockHomeScreenToPortrait)

    private var _lastException: Exception? = null
        set(value)
        {
            field = value.also { Log.e(TAG, "Caught exception", value) }
        }


    // region system

    @JavascriptInterface
    fun getAndroidAPILevel() = Build.VERSION.SDK_INT


    @JavascriptInterface
    fun getBridgeVersionCode() = _pm
        .getPackageInfo(_app.packageName, 0)
        .run {
            if (CurrentAndroidVersion.supportsPackageInfoLongVersionCode())
                longVersionCode
            else
                @Suppress("DEPRECATION")
                versionCode.toLong()
        }

    @JavascriptInterface
    fun getBridgeVersionName(): String = _pm.getPackageInfo(_app.packageName, 0).versionName ?: ""

    /**
     * True on forks where window insets report `top`/`left` correctly (see CLAUDE.md's "Known Bridge
     * bugs"). Absent on every earlier build, including stock Bridge, so `bridgeHas('getWindowInsetsSwapFixed')`
     * alone tells a launcher whether it can trust `top`/`left` as reported.
     */
    @JavascriptInterface
    fun getWindowInsetsSwapFixed() = true


    @JavascriptInterface
    fun getLastErrorMessage() = _lastException?.messageOrDefault()

    // endregion


    // region fetch

    @JavascriptInterface
    fun getProjectURL() = BridgeServer.PROJECT_URL

    @JavascriptInterface
    fun getAppsURL() = getBridgeApiEndpointURL(BridgeServer.ENDPOINT_APPS)

    // endregion


    // region apps

    @JvmOverloads
    @JavascriptInterface
    fun requestAppUninstall(packageName: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { requestAppUninstall(packageName) }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenAppInfo(packageName: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { openAppInfo(packageName) }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestLaunchApp(packageName: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { launchApp(packageName) }
    }

    /**
     * JSON `{ apps: [{ packageName, label, userSerial, profile, isPaused }] }`: the apps of every profile, the personal
     * one included. `profile` is `'personal'` or `'work'`; `userSerial` identifies the profile in the `…ProfileApp…` methods.
     */
    @JavascriptInterface
    fun getProfileAppsURL() = getBridgeApiEndpointURL(BridgeServer.ENDPOINT_PROFILE_APPS)

    /** The app's icon in the profile, with the profile's badge (the work briefcase). */
    @JavascriptInterface
    fun getProfileAppIconURL(packageName: String, userSerial: Long) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_PROFILE_APP_ICONS,
            ProfileAppIconsEndpoint.QUERY_PACKAGE_NAME to packageName,
            ProfileAppIconsEndpoint.QUERY_USER_SERIAL to userSerial,
        )

    /** Opens the app in its profile (e.g. work Teams). If work apps are paused, Android asks to turn them on. */
    @JvmOverloads
    @JavascriptInterface
    fun requestLaunchProfileApp(packageName: String, userSerial: Long, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed) { _profileApps.launch(packageName, userSerial) }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenProfileAppInfo(packageName: String, userSerial: Long, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed) { _profileApps.openAppInfo(packageName, userSerial) }
    }

    /** @return package name of the user's default app for [role], or null if there is none (Android would show a chooser). */
    @JavascriptInterface
    fun getDefaultAppPackageName(role: String): String?
    {
        val intent = try
        {
            DefaultAppRoleStringOptions.fromStringOrThrow(role).createIntent()
        }
        catch (ex: Exception)
        {
            _lastException = ex
            throw ex
        }

        val activityInfo = _pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            ?: return null

        // when there is no default, the system chooser (ResolverActivity in the "android" package) is resolved
        return if (activityInfo.packageName == "android" || activityInfo.name.endsWith("ResolverActivity"))
            null
        else
            activityInfo.packageName
    }

    // endregion


    // region urls

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenUrl(url: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { openUrl(url) }
    }

    // endregion


    // region files

    /**
     * Asks where to save [content] with Android's "Save as" dialog, suggesting [fileName], and writes it there.
     * Returns whether the dialog could be opened; the outcome arrives as the `fileSaved` event.
     */
    @JvmOverloads
    @JavascriptInterface
    fun requestSaveFile(fileName: String, content: String, mimeType: String = "application/json", showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            val chooser = fileChooser ?: throw Exception("The home screen isn't ready to save files.")
            _fileSaver.save(chooser, fileName, mimeType, content)
        }
    }

    // endregion


    // region notifications

    @JavascriptInterface
    fun getCanReadNotifications(): Boolean = _app.checkCanReadNotifications()

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenNotificationAccessSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startNotificationAccessSettingsActivity() }
    }

    @JavascriptInterface
    fun getNotificationsURL() = getBridgeApiEndpointURL(BridgeServer.ENDPOINT_NOTIFICATIONS)

    @JvmOverloads
    @JavascriptInterface
    fun getNotificationIconURL(key: String, large: Boolean = false) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_NOTIFICATION_ICONS,
            // notification keys can contain any character
            NotificationIconsEndpoint.QUERY_KEY to Uri.encode(key),
            NotificationIconsEndpoint.QUERY_LARGE to large,
        )

    /** Does what tapping the notification in the shade does: sends its content intent, then dismisses it if it's auto-cancel. */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenNotification(key: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            val notification = getActiveNotificationOrThrow(key).notification
            val contentIntent = notification.contentIntent
                ?: throw Exception("Notification ${q(key)} can't be opened (it has no content intent).")

            contentIntent.send(this, 0, null, null, null, null, pendingIntentSendOptions())

            if (notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
                BridgeNotificationListenerService.instance?.cancelNotification(key)
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestDismissNotification(key: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            val sbn = getActiveNotificationOrThrow(key)
            if (!sbn.isClearable)
                throw Exception("Notification ${q(key)} can't be dismissed.")

            val listener = BridgeNotificationListenerService.instance
                ?: throw Exception("Bridge is not connected to the notification service.")
            listener.cancelNotification(key)
        }
    }

    // Android 14 blocks activity starts from PendingIntents sent by apps that don't opt in
    private fun pendingIntentSendOptions() = if (CurrentAndroidVersion.supportsPendingIntentBackgroundActivityStartMode())
        ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    else
        null

    /** Presses one of the notification's buttons (see `actions` in the notification JSON). */
    @JvmOverloads
    @JavascriptInterface
    fun requestNotificationAction(key: String, actionIndex: Int, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            getNotificationActionOrThrow(key, actionIndex).actionIntent.send(this, 0, null, null, null, null, pendingIntentSendOptions())
        }
    }

    /** Sends [text] through a notification action that takes typed text (a "Reply" button). */
    @JvmOverloads
    @JavascriptInterface
    fun requestReplyToNotification(key: String, actionIndex: Int, text: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            val action = getNotificationActionOrThrow(key, actionIndex)
            val remoteInputs = action.remoteInputs?.filter { it.allowFreeFormInput }
            if (remoteInputs.isNullOrEmpty())
                throw Exception("That action of notification ${q(key)} doesn't take text.")

            val results = Bundle().apply { remoteInputs.forEach { putCharSequence(it.resultKey, text) } }
            val fillIn = Intent()
            RemoteInput.addResultsToIntent(remoteInputs.toTypedArray(), fillIn, results)
            action.actionIntent.send(this, 0, fillIn, null, null, null, pendingIntentSendOptions())
        }
    }

    private fun getNotificationActionOrThrow(key: String, actionIndex: Int): android.app.Notification.Action
    {
        val actions = getActiveNotificationOrThrow(key).notification.actions
        return actions?.getOrNull(actionIndex)
            ?: throw Exception("Notification ${q(key)} has no action $actionIndex.")
    }

    private fun getActiveNotificationOrThrow(key: String) = _notifications[key]
        ?: throw Exception("No active notification with key ${q(key)}.")

    // endregion


    // region connectivity

    /** JSON `{ type, wifiLevel, cellularLevel, cellularDataActivity, dataActivity }`, levels 0 to 4. Fires `connectivityChanged`. */
    @JavascriptInterface
    fun getConnectivity(): String = Json.encodeToString(SerializableConnectivity.serializer(), _connectivity.connectivity.value)

    // endregion


    // region battery

    /** JSON `{ level, isCharging, pluggedType }`, level 0 to 100, `pluggedType` `'ac' | 'usb' | 'wireless' | 'other'` or null. Fires `batteryChanged`. */
    @JavascriptInterface
    fun getBattery(): String = Json.encodeToString(SerializableBattery.serializer(), _battery.battery.value)

    // endregion


    // region alarms

    /** JSON `{ triggerTime, packageName }` (epoch ms) of the next alarm clock set on the device, or `null`. Fires `nextAlarmChanged`. */
    @JavascriptInterface
    fun getNextAlarm(): String = Json.encodeToString(SerializableNextAlarm.serializer().nullable, _alarm.nextAlarm.value)

    /** Opens the list of alarms in whatever clock app handles `AlarmClock.ACTION_SHOW_ALARMS`. */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenAlarms(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) {
            startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS))
        }
    }

    // endregion


    // region media

    /** The current media session as JSON, or `null` (needs notification access). Fires `mediaSessionChanged`. */
    @JavascriptInterface
    fun getMediaSession(): String = Json.encodeToString(SerializableMediaSession.serializer().nullable, _media.session.value)

    /** The current track's art; the URL changes with every track. */
    @JavascriptInterface
    fun getMediaArtURL() =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_MEDIA_ART,
            MediaArtEndpoint.QUERY_VERSION to (_media.session.value?.artVersion ?: 0),
        )

    @JvmOverloads
    @JavascriptInterface
    fun requestMediaAction(action: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            val controller = _media.controller ?: throw Exception("Nothing is playing.")
            val controls = controller.transportControls
            when (MediaActionStringOptions.fromStringOrThrow(action))
            {
                MediaActionStringOptions.Play -> controls.play()
                MediaActionStringOptions.Pause -> controls.pause()
                MediaActionStringOptions.PlayPause ->
                    if (controller.playbackState?.state == android.media.session.PlaybackState.STATE_PLAYING) controls.pause() else controls.play()
                MediaActionStringOptions.Next -> controls.skipToNext()
                MediaActionStringOptions.Previous -> controls.skipToPrevious()
            }
        }
    }

    /** Opens the app that's playing, on its player screen when it says which one that is. */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenMediaApp(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed)
        {
            val controller = _media.controller ?: throw Exception("Nothing is playing.")
            val sessionActivity = controller.sessionActivity
            if (sessionActivity != null)
                sessionActivity.send(this, 0, null, null, null, null, pendingIntentSendOptions())
            else
                launchApp(controller.packageName)
        }
    }

    // endregion


    // region quick settings

    /** Opens the floating panel (Android 10+) or settings screen for things projects can't toggle: Wi-Fi, Bluetooth... */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenSystemPanel(panel: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) {
            startActivity(SystemPanelStringOptions.fromStringOrThrow(panel).createIntent())
        }
    }

    /** Whether Wi-Fi is on (not necessarily connected). Fires `wifiEnabledChanged`. */
    @JavascriptInterface
    fun getWifiEnabled() = _quickSettings.isWifiOn.value

    @JavascriptInterface
    fun getIsBluetoothAvailable() = _quickSettings.isBluetoothAvailable

    /** Fires `bluetoothEnabledChanged` (from Android 12, once the home screen gets the focus back). */
    @JavascriptInterface
    fun getBluetoothEnabled() = _quickSettings.isBluetoothOn.value

    /** Whether location (GPS) is on. Fires `locationEnabledChanged`. */
    @JavascriptInterface
    fun getLocationEnabled() = _quickSettings.isLocationOn.value

    @JavascriptInterface
    fun getIsFlashlightAvailable() = _quickSettings.flashlightCameraId != null

    @JavascriptInterface
    fun getFlashlightOn() = _quickSettings.isFlashlightOn.value

    @JvmOverloads
    @JavascriptInterface
    fun requestSetFlashlightOn(on: Boolean, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed) { _quickSettings.setFlashlightOn(on) }
    }

    /** Whether the user let Bridge "modify system settings", needed for brightness and auto-rotate. */
    @JavascriptInterface
    fun getCanWriteSystemSettings() = _app.checkCanWriteSystemSettings()

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenWriteSystemSettingsPermission(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startWriteSystemSettingsPermissionActivity() }
    }

    /** JSON `{ isAuto: boolean, level: number }`, with level from 0 to 1. */
    @JavascriptInterface
    fun getScreenBrightness() = Json.encodeToString(ScreenBrightness.serializer(), _quickSettings.screenBrightness.value)

    @JvmOverloads
    @JavascriptInterface
    fun requestSetScreenBrightnessAuto(auto: Boolean, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            assertCanWriteSystemSettings()
            _quickSettings.setScreenBrightnessAuto(auto)
        }
    }

    /** Switches to manual brightness at [level] (0 to 1). */
    @JvmOverloads
    @JavascriptInterface
    fun requestSetScreenBrightnessLevel(level: Float, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            assertCanWriteSystemSettings()
            _quickSettings.setScreenBrightnessLevel(level)
        }
    }

    @JavascriptInterface
    fun getAutoRotateOn() = _quickSettings.isAutoRotateOn.value

    @JvmOverloads
    @JavascriptInterface
    fun requestSetAutoRotateOn(on: Boolean, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            assertCanWriteSystemSettings()
            _quickSettings.setAutoRotateOn(on)
        }
    }

    @JavascriptInterface
    fun getMasterSyncOn() = _quickSettings.isMasterSyncOn.value

    @JvmOverloads
    @JavascriptInterface
    fun requestSetMasterSyncOn(on: Boolean, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed) { _quickSettings.setMasterSyncOn(on) }
    }

    private fun assertCanWriteSystemSettings()
    {
        if (!_app.checkCanWriteSystemSettings())
            throw Exception("Bridge needs the \"Modify system settings\" permission for this. Open it with requestOpenWriteSystemSettingsPermission().")
    }

    /** Whether the user gave Bridge "Do Not Disturb access", needed to change the ringer mode. */
    @JavascriptInterface
    fun getCanAccessNotificationPolicy() = _app.checkCanAccessNotificationPolicy()

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenNotificationPolicyAccessSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startNotificationPolicyAccessSettingsActivity() }
    }

    /** `'normal' | 'vibrate' | 'silent'`. Fires `ringerModeChanged`. */
    @JavascriptInterface
    fun getRingerMode(): String = _quickSettings.ringerMode.value.rawValue

    @JvmOverloads
    @JavascriptInterface
    fun requestSetRingerMode(mode: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            assertCanAccessNotificationPolicy()
            _quickSettings.setRingerMode(RingerModeStringOptions.toAudioManagerRingerModeOrThrow(mode))
        }
    }

    /** 0 to 1 (media volume). Fires `musicVolumeChanged`. */
    @JavascriptInterface
    fun getMusicVolume() = _quickSettings.musicVolume.value

    /** No special permission needed. */
    @JvmOverloads
    @JavascriptInterface
    fun requestSetMusicVolume(level: Float, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed) { _quickSettings.setMusicVolume(level) }
    }

    private fun assertCanAccessNotificationPolicy()
    {
        if (!_app.checkCanAccessNotificationPolicy())
            throw Exception("Bridge needs \"Do Not Disturb access\" for this. Open it with requestOpenNotificationPolicyAccessSettings().")
    }

    // endregion


    // region app usage

    /** Whether the user gave Bridge "Usage access". Fires `canReadUsageStatsChanged` (checked when the home screen resumes). */
    @JavascriptInterface
    fun getCanReadUsageStats() = _usage.canRead

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenUsageAccessSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startUsageAccessSettingsActivity() }
    }

    /** JSON `{ apps: [{ packageName, totalTimeMs, openCount, lastTimeUsed }] }` for [from, to) (ms), most used first. 403 without access. */
    @JavascriptInterface
    fun getAppUsageURL(from: Long, to: Long) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_USAGE,
            AppUsageEndpoint.QUERY_FROM to from,
            AppUsageEndpoint.QUERY_TO to to,
        )

    // endregion


    // region contacts

    /** Whether Bridge may read contacts (READ_CONTACTS). Fires `canReadContactsChanged`. */
    @JavascriptInterface
    fun getCanReadContacts() = _contacts.canRead

    @JvmOverloads
    @JavascriptInterface
    fun requestContactsPermission(showToastIfFailed: Boolean = true): Boolean
    {
        if (_contacts.canRead) return true
        return requestRuntimePermission(android.Manifest.permission.READ_CONTACTS, showToastIfFailed) { _contacts.startObservingIfPossible() }
    }

    /**
     * JSON `{ contacts: [{ id, lookupKey, name, starred, hasPhoto, phoneNumbers: [{ number, label, isPrimary }] }] }`:
     * contacts with a phone number, by name. `query` matches names and numbers; empty means all. 403 without permission.
     */
    @JvmOverloads
    @JavascriptInterface
    fun getContactsURL(query: String? = null, starredOnly: Boolean = false, limit: Int = 0) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_CONTACTS,
            ContactsEndpoint.QUERY_QUERY to query?.takeIf { it.isNotBlank() }?.let { Uri.encode(it) },
            ContactsEndpoint.QUERY_STARRED_ONLY to starredOnly,
            ContactsEndpoint.QUERY_LIMIT to limit.takeIf { it > 0 },
        )

    /** The contact's photo; 404 when it has none (check `hasPhoto`). */
    @JavascriptInterface
    fun getContactPhotoURL(lookupKey: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_CONTACT_PHOTOS,
            ContactPhotosEndpoint.QUERY_LOOKUP_KEY to Uri.encode(lookupKey),
        )

    /** Opens the contact's card in the contacts app. */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenContact(lookupKey: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed)
        {
            startActivity(Intent(Intent.ACTION_VIEW, _contacts.getContactUri(lookupKey)))
        }
    }

    /** Whether Bridge may place calls itself (CALL_PHONE). Fires `canCallPhoneChanged`. */
    @JavascriptInterface
    fun getCanCallPhone() = _contacts.canCall

    @JvmOverloads
    @JavascriptInterface
    fun requestCallPhonePermission(showToastIfFailed: Boolean = true): Boolean
    {
        if (_contacts.canCall) return true
        return requestRuntimePermission(android.Manifest.permission.CALL_PHONE, showToastIfFailed)
    }

    /** Calls [number] right away with CALL_PHONE; without it, opens the dialer with the number typed in. */
    @JvmOverloads
    @JavascriptInterface
    fun requestCallPhoneNumber(number: String, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed)
        {
            val uri = Uri.fromParts("tel", number, null)
            startActivity(Intent(if (_contacts.canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri))
        }
    }

    // endregion


    // region calendar

    /** Whether Bridge may read the calendar (READ_CALENDAR). Fires `canReadCalendarChanged`. */
    @JavascriptInterface
    fun getCanReadCalendar() = _calendar.canRead

    /**
     * Shows Android's dialog asking for calendar access (or, if the user refused it for good, opens Bridge's
     * app settings). Returns false only if it couldn't ask; the answer comes as `canReadCalendarChanged`.
     */
    @JvmOverloads
    @JavascriptInterface
    fun requestCalendarPermission(showToastIfFailed: Boolean = true): Boolean
    {
        if (_calendar.canRead) return true
        return requestRuntimePermission(android.Manifest.permission.READ_CALENDAR, showToastIfFailed) { _calendar.startObservingIfPossible() }
    }

    /**
     * Shows Android's dialog for [permission] from the home screen (or Bridge's app settings if it was refused
     * for good). The answer reaches JS as the matching `can…Changed` event, through PermsHolder.
     */
    private fun requestRuntimePermission(permission: String, showToastIfFailed: Boolean, afterAnswer: () -> Unit = {}): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            val requester = permissionRequester ?: throw Exception("The home screen isn't ready to ask for permissions.")
            Handler(Looper.getMainLooper()).post {
                requester.request(permission) {
                    _perms.notifyPermsMightHaveChanged()
                    afterAnswer()
                }
            }
        }
    }

    /** JSON `{ events: [...] }` of event instances overlapping [from, to) (milliseconds), by start time. 403 without permission. */
    @JavascriptInterface
    fun getCalendarEventsURL(from: Long, to: Long) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_CALENDAR_EVENTS,
            CalendarEventsEndpoint.QUERY_FROM to from,
            CalendarEventsEndpoint.QUERY_TO to to,
        )

    /** Opens one occurrence of an event in the calendar app. */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenCalendarEvent(eventId: Long, begin: Long, end: Long, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed)
        {
            startActivity(
                Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId))
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            )
        }
    }

    /** Opens the calendar app at a time (e.g. a day tapped in a month view). */
    @JvmOverloads
    @JavascriptInterface
    fun requestOpenCalendarAt(time: Long, showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed)
        {
            val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time").also { ContentUris.appendId(it, time) }.build()
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    }

    // endregion


    // region app shortcuts

    /** Whether Bridge can read apps' shortcuts: only the default launcher can, on Android 7.1+. */
    @JavascriptInterface
    fun getCanAccessAppShortcuts() = _shortcuts.canAccess

    /** JSON `{ shortcuts: [{ id, shortLabel, longLabel }] }`, in the order launchers show them. */
    @JavascriptInterface
    fun getAppShortcutsURL(packageName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_SHORTCUTS,
            AppShortcutsEndpoint.QUERY_PACKAGE_NAME to packageName,
        )

    @JavascriptInterface
    fun getAppShortcutIconURL(packageName: String, shortcutId: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_SHORTCUT_ICONS,
            AppShortcutIconsEndpoint.QUERY_PACKAGE_NAME to packageName,
            // shortcut ids can contain any character
            AppShortcutIconsEndpoint.QUERY_SHORTCUT_ID to Uri.encode(shortcutId),
        )

    @JvmOverloads
    @JavascriptInterface
    fun requestStartAppShortcut(packageName: String, shortcutId: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed) { _shortcuts.start(packageName, shortcutId) }
    }

    // endregion


    // region icon packs

    @JavascriptInterface
    fun getIconPacksURL(includeItems: Boolean = false): String =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACKS,
            IconPacksEndpoint.QUERY_INCLUDE_ITEMS to includeItems,
        )

    @JavascriptInterface
    fun getIconPackInfoURL(iconPackPackageName: String, includeItems: Boolean = false) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACKS,
            IconPacksEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPacksEndpoint.QUERY_INCLUDE_ITEMS to includeItems,
        )

    @JavascriptInterface
    fun getIconPackAppFilterXMLURL(iconPackPackageName: String, includeItems: Boolean = false) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACKS,
            IconPacksEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPacksEndpoint.QUERY_INCLUDE_ITEMS to includeItems,
        )

    // endregion


    // region icons

    @JavascriptInterface
    fun getDefaultAppIconURL(packageName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_ICONS,
            AppIconsEndpoint.QUERY_PACKAGE_NAME to packageName,
        )

    @JvmOverloads
    @JavascriptInterface
    fun getAppIconURL(appPackageName: String, iconPackPackageName: String? = null) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_ICONS,
            AppIconsEndpoint.QUERY_PACKAGE_NAME to appPackageName,
            AppIconsEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            AppIconsEndpoint.QUERY_NOT_FOUND_BEHAVIOR to AppIconsEndpoint.IconNotFoundBehaviors.Default,
        )

    @JavascriptInterface
    fun getIconPackAppIconURL(iconPackPackageName: String, appPackageName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_APP_ICONS,
            AppIconsEndpoint.QUERY_PACKAGE_NAME to appPackageName,
            AppIconsEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            AppIconsEndpoint.QUERY_NOT_FOUND_BEHAVIOR to AppIconsEndpoint.IconNotFoundBehaviors.Error,
        )

    @JavascriptInterface
    fun getIconPackAppItemURL(iconPackPackageName: String, itemName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACK_CONTENT,
            IconPackContentEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPackContentEndpoint.QUERY_ITEM_NAME to itemName,
        )


    @JavascriptInterface
    fun getIconPackDrawableURL(iconPackPackageName: String, drawableName: String) =
        getBridgeApiEndpointURL(
            BridgeServer.ENDPOINT_ICON_PACK_CONTENT,
            IconPackContentEndpoint.QUERY_ICON_PACK_PACKAGE_NAME to iconPackPackageName,
            IconPackContentEndpoint.QUERY_DRAWABLE_NAME to drawableName,
        )

    // endregion


    // region wallpaper

    @JavascriptInterface
    fun setWallpaperOffsetSteps(xStep: Float, yStep: Float)
    {
        _wallman.setWallpaperOffsetSteps(xStep, yStep)
    }

    @JavascriptInterface
    fun setWallpaperOffsets(x: Float, y: Float)
    {
        val token = webView?.applicationWindowToken

        if (token != null)
        {
            _wallman.setWallpaperOffsets(token, x, y)
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun sendWallpaperTap(x: Float, y: Float, z: Float = 0f)
    {
        val token = webView?.applicationWindowToken
        if (token != null)
        {
            val metrics = _app.resources.displayMetrics
            _wallman.sendWallpaperCommand(
                token,
                WallpaperManager.COMMAND_TAP,
                metrics.toPx(x).toInt(),
                metrics.toPx(y).toInt(),
                metrics.toPx(z).toInt(),
                Bundle.EMPTY
            )
        }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestChangeSystemWallpaper(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startWallpaperPickerActivity() }
    }

    // endregion


    // region bridge button

    @JavascriptInterface
    fun getBridgeButtonVisibility(): String
    {
        return BridgeButtonVisibilityStringOptions.fromShowBridgeButton(_showBridgeButton.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetBridgeButtonVisibility(visibility: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.showBridgeButton,
                BridgeButtonVisibilityStringOptions.showBridgeButtonFromStringOrThrow(visibility),
            )
        }
    }

    // endregion


    // region draw system wallpaper behind webview

    @JavascriptInterface
    fun getDrawSystemWallpaperBehindWebViewEnabled(): Boolean
    {
        return _drawSystemWallpaperBehindWebView.value
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetDrawSystemWallpaperBehindWebViewEnabled(enable: Boolean, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(BridgeSettings.drawSystemWallpaperBehindWebView, enable)
        }
    }

    // endregion


    // region overscroll effects

    @JavascriptInterface
    fun getOverscrollEffects(): String
    {
        return OverscrollEffectsStringOptions.fromDrawWebViewOverscrollEffects(_drawWebViewOverscrollEffects.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetOverscrollEffects(effects: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.drawWebViewOverscrollEffects,
                OverscrollEffectsStringOptions.drawWebViewOverscrollEffectsOrThrow(effects),
            )
        }
    }

    // endregion


    // region screen orientation

    @JavascriptInterface
    fun getScreenOrientation(): String
    {
        return ScreenOrientationStringOptions.fromLockHomeScreenToPortrait(_lockHomeScreenToPortrait.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetScreenOrientation(orientation: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.lockHomeScreenToPortrait,
                ScreenOrientationStringOptions.lockHomeScreenToPortraitOrThrow(orientation),
            )
        }
    }

    // endregion


    // region system night mode

    @JavascriptInterface
    fun getSystemNightMode(): String
    {
        return SystemNightModeStringOptions.fromUiModeManagerNightMode(_modeman.nightMode).rawValue
    }

    @JavascriptInterface
    fun resolveIsSystemInDarkTheme(): Boolean
    {
        return _app.getIsSystemInNightMode()
    }

    @JavascriptInterface
    fun getCanSetSystemNightMode(): Boolean
    {
        return ActivityCompat.checkSelfPermission(_app, "android.permission.MODIFY_DAY_NIGHT_MODE") == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(_app, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
    }

    // alias matching the name declared in Bridge.d.ts and the canRequestSystemNightModeChanged event
    @JavascriptInterface
    fun getCanRequestSystemNightMode(): Boolean = getCanSetSystemNightMode()

    @SuppressLint("WrongConstant")
    @JvmOverloads
    @JavascriptInterface
    fun requestSetSystemNightMode(mode: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            Log.d(TAG, "requestSetSystemNightMode: $mode")

            val modeInt = when (mode)
            {
                "no" -> UiModeManager.MODE_NIGHT_NO
                "yes" -> UiModeManager.MODE_NIGHT_YES
                "auto" -> UiModeManager.MODE_NIGHT_AUTO

                "custom" -> if (CurrentAndroidVersion.supportsNightModeCustom())
                    UiModeManager.MODE_NIGHT_CUSTOM
                else
                    throw Exception("\"custom\" requires API level 30 (Android 11).")

                else -> throw Exception("Mode must be one of ${q("no")}, ${q("yes")}, ${q("auto")} or, from API level 30 (Android 11), ${q("custom")} (got ${q(mode)}).")
            }

            val hasModifyPerm = ActivityCompat.checkSelfPermission(_app, "android.permission.MODIFY_DAY_NIGHT_MODE") == PackageManager.PERMISSION_GRANTED

            if (hasModifyPerm)
            {
                _modeman.nightMode = modeInt
            }
            else
            {
                val hasWriteSecureSettingsPerm = ActivityCompat.checkSelfPermission(_app, Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

                if (hasWriteSecureSettingsPerm)
                {
                    // shoutouts to joaomgcd (Tasker dev) for this workaround!
                    Settings.Secure.putInt(_app.contentResolver, "ui_night_mode", modeInt)
                    _modeman.enableCarMode(UiModeManager.ENABLE_CAR_MODE_ALLOW_SLEEP)
                    _modeman.disableCarMode(0)
                }
                else
                {
                    Toast
                        .makeText(
                            _app,
                            "To set system night mode, Bridge needs the WRITE_SECURE_SETTINGS permission, which can be granted via ADB. "
                                    + "Check the documentation for more information.",
                            Toast.LENGTH_LONG
                        )
                        .show()
                }
            }
        }
    }

    // endregion


    // region Bridge theme

    @JavascriptInterface
    fun getBridgeTheme(): String
    {
        return BridgeThemeStringOptions.fromBridgeTheme(_theme.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetBridgeTheme(theme: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.theme,
                BridgeThemeStringOptions.bridgeThemeFromStringOrThrow(theme),
            )
        }
    }

    // endregion


    // region system bars

    @JavascriptInterface
    fun getStatusBarAppearance(): String
    {
        return SystemBarAppearanceStringOptions.fromSystemBarAppearance(_statusBarAppearance.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetStatusBarAppearance(appearance: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.statusBarAppearance,
                SystemBarAppearanceStringOptions.systemBarAppearanceFromStringOrThrow(appearance)
            )
        }
    }


    @JavascriptInterface
    fun getNavigationBarAppearance(): String
    {
        return SystemBarAppearanceStringOptions.fromSystemBarAppearance(_navigationBarAppearance.value).rawValue
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestSetNavigationBarAppearance(appearance: String, showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryEditPrefs(showToastIfFailed)
        {
            it.setBridgeSetting(
                BridgeSettings.navigationBarAppearance,
                SystemBarAppearanceStringOptions.systemBarAppearanceFromStringOrThrow(appearance)
            )
        }
    }

    // endregion


    // region screen locking

    @JavascriptInterface
    fun getCanLockScreen(): Boolean
    {
        return getIsBridgeAbleToLockTheScreen(
            isAccessibilityServiceEnabled = _isAccessibilityServiceEnabled.value,
            isDeviceAdminEnabled = _isDeviceAdminEnabled.value,
            allowProjectsToTurnScreenOff = _allowProjectsToTurnScreenOff.value,
        )
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestLockScreen(showToastIfFailed: Boolean = true): Boolean
    {
        return _app.tryRun(showToastIfFailed)
        {
            if (!CurrentAndroidVersion.supportsAccessiblityServiceScreenLock() && !_isDeviceAdminEnabled.value)
            {
                throw Exception("Bridge is not a device admin. Visit Bridge Settings to resolve the issue.")
            }
            else if (CurrentAndroidVersion.supportsAccessiblityServiceScreenLock() && !_isAccessibilityServiceEnabled.value)
            {
                throw Exception("Bridge Accessibility Service is not enabled. Visit Bridge Settings to resolve the issue.")
            }

            if (!_allowProjectsToTurnScreenOff.value)
            {
                throw Exception("Projects are not allowed to lock the screen. Visit Bridge Settings to resolve the issue.")
            }

            if (CurrentAndroidVersion.supportsAccessiblityServiceScreenLock())
            {
                if (BridgeLauncherAccessibilityService.instance == null)
                {
                    throw Exception("Cannot access the Bridge Accessibility Service instance. This is a bug.")
                }
                else
                {
                    BridgeLauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
            else
            {
                _dpman.lockNow()
            }
        }
    }

    // endregion


    // region misc actions

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenBridgeSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startBridgeSettingsActivity() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenBridgeAppDrawer(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startBridgeAppDrawerActivity() }
    }

    @JvmOverloads
    @JavascriptInterface
    fun requestOpenDeveloperConsole(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startDevConsoleActivity() }
    }

    // https://stackoverflow.com/a/15582509/6796433
    @JvmOverloads
    @SuppressLint("WrongConstant")
    @JavascriptInterface
    fun requestExpandNotificationShade(showToastIfFailed: Boolean = true): Boolean
    {
        try
        {
            val sbservice: Any = _app.getSystemService("statusbar")
            val statusbarManager = Class.forName("android.app.StatusBarManager")
            val showsb = statusbarManager.getMethod("expandNotificationsPanel")
            showsb.invoke(sbservice)

            return true
        }
        catch (ex: Exception)
        {
            _lastException = ex

            if (showToastIfFailed)
                _app.showErrorToast(ex)

            return false
        }
    }


    @JvmOverloads
    @JavascriptInterface
    fun requestOpenAndroidSettings(showToastIfFailed: Boolean = true): Boolean
    {
        return tryRunInHomescreenContext(showToastIfFailed) { startAndroidSettingsActivity() }
    }

    // endregion


    // region toast

    @JvmOverloads
    @JavascriptInterface
    fun showToast(message: String, long: Boolean = false)
    {
        Toast.makeText(_app, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    // endregion


    // region window insets & cutouts

    private fun getWindowInsetsJson(option: WindowInsetsOptions) = Json.encodeToString(WindowInsetsSnapshot.serializer(), _windowInsetsHolder.stateFlowMap[option]!!.value)

    @JavascriptInterface
    fun getStatusBarsWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.StatusBars)

    @JavascriptInterface
    fun getStatusBarsIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.StatusBarsIgnoringVisibility)


    @JavascriptInterface
    fun getNavigationBarsWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.NavigationBars)

    @JavascriptInterface
    fun getNavigationBarsIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.NavigationBarsIgnoringVisibility)


    @JavascriptInterface
    fun getCaptionBarWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.CaptionBar)

    @JavascriptInterface
    fun getCaptionBarIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.CaptionBarIgnoringVisibility)


    @JavascriptInterface
    fun getSystemBarsWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.SystemBars)

    @JavascriptInterface
    fun getSystemBarsIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.SystemBarsIgnoringVisibility)


    @JavascriptInterface
    fun getImeWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.Ime)

    @JavascriptInterface
    fun getImeAnimationSourceWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.ImeAnimationSource)

    @JavascriptInterface
    fun getImeAnimationTargetWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.ImeAnimationTarget)


    @JavascriptInterface
    fun getTappableElementWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.TappableElement)

    @JavascriptInterface
    fun getTappableElementIgnoringVisibilityWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.TappableElementIgnoringVisibility)


    @JavascriptInterface
    fun getSystemGesturesWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.SystemGestures)

    @JavascriptInterface
    fun getMandatorySystemGesturesWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.MandatorySystemGestures)


    @JavascriptInterface
    fun getDisplayCutoutWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.DisplayCutout)

    @JavascriptInterface
    fun getWaterfallWindowInsets() = getWindowInsetsJson(WindowInsetsOptions.Waterfall)


    @JavascriptInterface
    fun getDisplayCutoutPath() = _displayShapeHolder.displayCutoutPath

    @JavascriptInterface
    fun getDisplayShapePath() = _displayShapeHolder.displayShapePath

    // endregion


    // region helpers

    private fun Context.tryRun(showToastIfFailed: Boolean, f: Context.() -> Unit): Boolean
    {
        return try
        {
            f()
            true
        }
        catch (ex: Exception)
        {
            if (showToastIfFailed)
                showErrorToast(ex)

            _lastException = ex

            false
        }
    }

    /**
     * Runs [f] with a context that starts other apps in their own task, as launchers should (otherwise the
     * app is stacked on top of the home screen in Bridge's task). Prefers the home screen activity, and falls
     * back to the application context if it's not registered at the moment.
     */
    private fun tryRunInHomescreenContext(showToastIfFailed: Boolean, f: Context.() -> Unit): Boolean
    {
        val context = homeScreenContext
            ?: _app.also { Log.w(TAG, "homeScreenContext is null, using the application context") }
        return NewTaskContext(context).tryRun(showToastIfFailed, f)
    }

    private class NewTaskContext(base: Context) : ContextWrapper(base)
    {
        override fun startActivity(intent: Intent) = super.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        override fun startActivity(intent: Intent, options: Bundle?) = super.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options)
    }

    private fun Context.tryEditPrefs(showToastIfFailed: Boolean, f: (MutablePreferences) -> Unit): Boolean
    {
        return tryRun(showToastIfFailed)
        {
            runBlocking {
                settingsDataStore.edit(f)
            }
        }
    }

    // endregion
}