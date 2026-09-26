package com.tored.bridgelauncher.services.perms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.util.Log
import com.tored.bridgelauncher.services.settings2.BridgeSettings
import com.tored.bridgelauncher.services.settings2.settingsDataStore
import com.tored.bridgelauncher.services.settings2.useBridgeSettingStateFlow
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import com.tored.bridgelauncher.utils.checkCanReadNotifications
import com.tored.bridgelauncher.utils.checkCanSetSystemNightMode
import com.tored.bridgelauncher.utils.checkCanWriteSystemSettings
import com.tored.bridgelauncher.utils.checkStoragePerms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

private val TAG = PermsHolder::class.simpleName

class PermsHolder(
    private val _context: Context,
)
{
    private val _scope = CoroutineScope(Dispatchers.Main)

    private val _hasStoragePermsState = MutableStateFlow(_context.checkStoragePerms())
    val hasStoragePermsState = _hasStoragePermsState.asStateFlow()

    private val _canSetSystemNightModeState = MutableStateFlow(_context.checkCanSetSystemNightMode())
    val canSetSystemNightModeState = _canSetSystemNightModeState.asStateFlow()

    private val _canReadNotificationsState = MutableStateFlow(_context.checkCanReadNotifications())
    val canReadNotificationsState = _canReadNotificationsState.asStateFlow()

    private val _canWriteSystemSettingsState = MutableStateFlow(_context.checkCanWriteSystemSettings())
    val canWriteSystemSettingsState = _canWriteSystemSettingsState.asStateFlow()

    // usage access is an app op, read through the holder that uses it
    var checkCanReadUsageStats: () -> Boolean = { false }
    private val _canReadUsageStatsState = MutableStateFlow(false)
    val canReadUsageStatsState = _canReadUsageStatsState.asStateFlow()

    private fun checkCanReadCalendar() = ContextCompat.checkSelfPermission(_context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    private val _canReadCalendarState = MutableStateFlow(checkCanReadCalendar())
    val canReadCalendarState = _canReadCalendarState.asStateFlow()

    private val _isAccessibilityServiceEnabled = useBridgeSettingStateFlow(_context.settingsDataStore, _scope, BridgeSettings.isAccessibilityServiceEnabled)
    private val _isDeviceAdminEnabled = useBridgeSettingStateFlow(_context.settingsDataStore, _scope, BridgeSettings.isDeviceAdminEnabled)
    private val _allowProjectsToTurnScreenOff = useBridgeSettingStateFlow(_context.settingsDataStore, _scope, BridgeSettings.allowProjectsToTurnScreenOff)

    private fun _getCanProjectsLockScreen(acc: Boolean, adm: Boolean, allow: Boolean): Boolean
    {
        return if (CurrentAndroidVersion.supportsAccessiblityServiceScreenLock())
            acc && allow
        else
            adm && allow
    }

    val canProjectsLockScreen = combine(
        _isAccessibilityServiceEnabled,
        _isDeviceAdminEnabled,
        _allowProjectsToTurnScreenOff
    )
    { acc, adm, allow -> _getCanProjectsLockScreen(acc, adm, allow) }
        .stateIn(
            _scope,
            SharingStarted.Eagerly,
            _getCanProjectsLockScreen(
                _isAccessibilityServiceEnabled.value,
                _isDeviceAdminEnabled.value,
                _allowProjectsToTurnScreenOff.value
            )
        )


    // intended to be called from onResume() - there is no API to listen for permission changes, so checks in onResume it is
    // (the notification listener service also calls this when it gets connected or disconnected)
    fun notifyPermsMightHaveChanged()
    {
        val hasStoragePerms = _context.checkStoragePerms()
        val canSetSystemNightMode = _context.checkCanSetSystemNightMode()
        val canReadNotifications = _context.checkCanReadNotifications()
        val canWriteSystemSettings = _context.checkCanWriteSystemSettings()
        Log.d(TAG, "notifyPermsMightHaveChanged: hasStoragePerms = $hasStoragePerms, canSetSystemNightMode = $canSetSystemNightMode")
        _hasStoragePermsState.value = hasStoragePerms
        _canSetSystemNightModeState.value = canSetSystemNightMode
        _canReadNotificationsState.value = canReadNotifications
        _canWriteSystemSettingsState.value = canWriteSystemSettings
        _canReadCalendarState.value = checkCanReadCalendar()
        _canReadUsageStatsState.value = checkCanReadUsageStats()
    }
}