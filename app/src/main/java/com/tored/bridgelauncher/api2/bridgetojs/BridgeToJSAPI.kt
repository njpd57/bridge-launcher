package com.tored.bridgelauncher.api2.bridgetojs

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.tored.bridgelauncher.api2.bridgetojs.events.apps.AppChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.apps.AppInstalledEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.apps.AppRemovedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.lifecycle.AfterResumeEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.lifecycle.BeforePauseEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.lifecycle.NewIntentEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.calendar.CalendarChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.alarm.NextAlarmChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.battery.BatteryChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.connectivity.ConnectivityChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanReadCalendarChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanReadUsageStatsChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanReadContactsChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanCallPhoneChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.contacts.ContactsChangedEvent
import com.tored.bridgelauncher.services.contacts.ContactsHolder
import com.tored.bridgelauncher.api2.bridgetojs.events.media.MediaSessionChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.notifications.NotificationPostedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.notifications.NotificationRemovedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanLockScreenChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanReadNotificationsChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanWriteSystemSettingsChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanAccessNotificationPolicyChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.AutoRotateChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.BluetoothEnabledChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.LocationEnabledChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.WifiEnabledChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.FlashlightChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.MasterSyncChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.RingerModeChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.MusicVolumeChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings.ScreenBrightnessChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.perms.CanRequestSystemNightModeChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.BridgeButtonVisibilityChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.BridgeThemeChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.DrawSystemWallpaperBehindWebViewChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.NavigationBarAppearanceChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.OverscrollEffectsChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.ScreenOrientationChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.settings.StatusBarAppearanceChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.systemuimode.SystemNightModeChangedEvent
import com.tored.bridgelauncher.api2.bridgetojs.events.windowinsets.WindowInsetsChangedEvent
import com.tored.bridgelauncher.api2.shared.BridgeButtonVisibilityStringOptions
import com.tored.bridgelauncher.api2.shared.BridgeThemeStringOptions
import com.tored.bridgelauncher.api2.shared.OverscrollEffectsStringOptions
import com.tored.bridgelauncher.api2.shared.ScreenOrientationStringOptions
import com.tored.bridgelauncher.api2.shared.SystemBarAppearanceStringOptions
import com.tored.bridgelauncher.services.apps.InstalledAppListChangeEvent
import com.tored.bridgelauncher.services.apps.InstalledAppsHolder
import com.tored.bridgelauncher.services.lifecycleevents.LifecycleEventsHolder
import com.tored.bridgelauncher.services.calendar.CalendarHolder
import com.tored.bridgelauncher.services.alarm.AlarmHolder
import com.tored.bridgelauncher.services.battery.BatteryHolder
import com.tored.bridgelauncher.services.connectivity.ConnectivityHolder
import com.tored.bridgelauncher.services.media.MediaSessionsHolder
import com.tored.bridgelauncher.services.notifications.NotificationListChangeEvent
import com.tored.bridgelauncher.services.notifications.NotificationsHolder
import com.tored.bridgelauncher.services.notifications.toSerializable
import com.tored.bridgelauncher.services.perms.PermsHolder
import com.tored.bridgelauncher.services.quicksettings.QuickSettingsHolder
import com.tored.bridgelauncher.services.settings2.BridgeSetting
import com.tored.bridgelauncher.services.settings2.BridgeSettings
import com.tored.bridgelauncher.services.settings2.settingsDataStore
import com.tored.bridgelauncher.services.settings2.useBridgeSettingStateFlow
import com.tored.bridgelauncher.services.uimode.SystemUIModeHolder
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val TAG = BridgeToJSAPI::class.simpleName

class BridgeToJSAPI(
    private val _app: Context,
    private val _apps: InstalledAppsHolder,
    private val _perms: PermsHolder,
    private val _insets: WindowInsetsHolder,
    private val _systemUIMode: SystemUIModeHolder,
    private val _lifecycleEventsHolder: LifecycleEventsHolder,
    private val _notifications: NotificationsHolder,
    private val _quickSettings: QuickSettingsHolder,
    private val _media: MediaSessionsHolder,
    private val _connectivity: ConnectivityHolder,
    private val _calendar: CalendarHolder,
    private val _contacts: ContactsHolder,
    private val _battery: BatteryHolder,
    private val _alarm: AlarmHolder,
)
{
    private val _scope = CoroutineScope(Dispatchers.Main)

    var webView: WebView? = null

    private fun sendBridgeEvent(model: IBridgeEventModel)
    {
        try
        {
            when (val wv = webView)
            {
                null -> Log.w(TAG, "sendBridgeEvent(${model.name}): webView is null, ignoring event")
                else ->
                {
                    wv.evaluateJavascript("if (typeof onBridgeEvent === 'function') onBridgeEvent(${model.getJson()})") { }
                    // this leads to a lot of log spam from windowinsets changes
//                    Log.w(TAG, "sendBridgeEvent(${model.name}): OK")
                }
            }
        }
        catch (ex: Exception)
        {
            Log.e(BridgeToJSAPI::class.simpleName, "sendBridgeEvent(${model.name}): failure", ex)
        }
    }

    private fun startCollectingEvents() = _scope.launch {

        Log.d(TAG, "startCollectingEvents")

        with(_apps)
        {
            Log.d(TAG, "appListChangeEventFlow before onCollect")
            onCollect(appListChangeEventFlow) {
                Log.d(TAG, "appListChangeEventFlow collected: $it")
                when (it)
                {
                    is InstalledAppListChangeEvent.Added ->
                    {
                        if (!it.isFromInitialLoad)
                            AppInstalledEvent(it.newApp.toSerializable())
                        else
                            null
                    }

                    is InstalledAppListChangeEvent.Changed -> AppChangedEvent(it.newApp.toSerializable())
                    is InstalledAppListChangeEvent.Removed -> AppRemovedEvent(it.packageName)
                }
            }
        }

        with(BridgeSettings)
        {
            onCollectSetting(showBridgeButton) { BridgeButtonVisibilityChangedEvent(BridgeButtonVisibilityStringOptions.fromShowBridgeButton(it)) }
            onCollectSetting(drawSystemWallpaperBehindWebView) { DrawSystemWallpaperBehindWebViewChangedEvent(it) }
            onCollectSetting(drawWebViewOverscrollEffects) { OverscrollEffectsChangedEvent(OverscrollEffectsStringOptions.fromDrawWebViewOverscrollEffects(it)) }
            onCollectSetting(lockHomeScreenToPortrait) { ScreenOrientationChangedEvent(ScreenOrientationStringOptions.fromLockHomeScreenToPortrait(it)) }
            onCollectSetting(theme) { BridgeThemeChangedEvent(BridgeThemeStringOptions.fromBridgeTheme(it)) }
            onCollectSetting(statusBarAppearance) { StatusBarAppearanceChangedEvent(SystemBarAppearanceStringOptions.fromSystemBarAppearance(it)) }
            onCollectSetting(navigationBarAppearance) { NavigationBarAppearanceChangedEvent(SystemBarAppearanceStringOptions.fromSystemBarAppearance(it)) }
        }

        with(_perms)
        {
            onCollect(canSetSystemNightModeState) { CanRequestSystemNightModeChangedEvent(it) }
            onCollect(canProjectsLockScreen) { CanLockScreenChangedEvent(it) }
            onCollect(canReadNotificationsState) { CanReadNotificationsChangedEvent(it) }
            onCollect(canWriteSystemSettingsState) { CanWriteSystemSettingsChangedEvent(it) }
            onCollect(canAccessNotificationPolicyState) { CanAccessNotificationPolicyChangedEvent(it) }
            onCollect(canReadCalendarState) { CanReadCalendarChangedEvent(it) }
            onCollect(canReadUsageStatsState) { CanReadUsageStatsChangedEvent(it) }
            onCollect(canReadContactsState) { CanReadContactsChangedEvent(it) }
            onCollect(canCallPhoneState) { CanCallPhoneChangedEvent(it) }
        }

        with(_systemUIMode)
        {
            onCollect(systemNightMode) { SystemNightModeChangedEvent(it) }
        }

        with(_insets)
        {
            stateFlowMap.forEach { (option, stateFlow) ->
                launch {
                    stateFlow.collect { snapshot ->
                        sendBridgeEvent(WindowInsetsChangedEvent.fromSnapshot(option, snapshot))
                    }
                }
            }
        }

        with(_quickSettings)
        {
            onCollect(isFlashlightOn) { FlashlightChangedEvent(it) }
            onCollect(screenBrightness) { ScreenBrightnessChangedEvent(it) }
            onCollect(isAutoRotateOn) { AutoRotateChangedEvent(it) }
            onCollect(isMasterSyncOn) { MasterSyncChangedEvent(it) }
            onCollect(isWifiOn) { WifiEnabledChangedEvent(it) }
            onCollect(isBluetoothOn) { BluetoothEnabledChangedEvent(it) }
            onCollect(isLocationOn) { LocationEnabledChangedEvent(it) }
            onCollect(ringerMode) { RingerModeChangedEvent(it) }
            onCollect(musicVolume) { MusicVolumeChangedEvent(it) }
        }

        with(_contacts)
        {
            onCollect(changes) { ContactsChangedEvent() }
        }

        with(_calendar)
        {
            onCollect(changes) { CalendarChangedEvent() }
        }

        with(_connectivity)
        {
            onCollect(connectivity) { ConnectivityChangedEvent(it) }
        }

        with(_battery)
        {
            onCollect(battery) { BatteryChangedEvent(it) }
        }

        with(_alarm)
        {
            onCollect(nextAlarm) { NextAlarmChangedEvent(it) }
        }

        with(_media)
        {
            onCollect(session) { MediaSessionChangedEvent(it) }
        }

        with(_notifications)
        {
            onCollect(changeEventFlow) {
                when (it)
                {
                    is NotificationListChangeEvent.Posted -> NotificationPostedEvent(it.notification.toSerializable())
                    is NotificationListChangeEvent.Removed -> NotificationRemovedEvent(it.key)
                }
            }
        }

        with(_lifecycleEventsHolder)
        {
            onCollect(homeScreenBeforePause) { BeforePauseEvent() }
            onCollect(homeScreenNewIntent) { NewIntentEvent() }
            onCollect(homeScreenAfterResume) { AfterResumeEvent() }
        }
    }

    private fun <T> CoroutineScope.onCollect(flow: Flow<T>, newValueToEvent: (newValue: T) -> BridgeEventModel?)
    {
        launch {
            flow.collect {
                newValueToEvent(it)?.let { ev ->
                    sendBridgeEvent(ev)
                }
            }
        }
    }

    private fun <TPreference, TResult> CoroutineScope.onCollectSetting(
        setting: BridgeSetting<TPreference, TResult>,
        newValueToEvent: (newValue: TResult) -> BridgeEventModel,
    )
    {
        val flow = useBridgeSettingStateFlow(_app.settingsDataStore, _scope, setting)
        launch {
            flow.collect {
                sendBridgeEvent(newValueToEvent(it))
            }
        }
    }

    fun startup()
    {
        startCollectingEvents()
    }
}