package com.tored.bridgelauncher.services.system

import android.content.ComponentName
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.tored.bridgelauncher.utils.bridgeLauncherApplication
import com.tored.bridgelauncher.utils.checkCanReadNotifications

private const val TAG = "NotificationListener"

/** Created by Android once the user grants Bridge notification access. Feeds the NotificationsHolder. */
class BridgeNotificationListenerService : NotificationListenerService()
{
    companion object
    {
        var instance: BridgeNotificationListenerService? = null
            private set
    }

    private val _services get() = bridgeLauncherApplication.services

    override fun onListenerConnected()
    {
        Log.d(TAG, "onListenerConnected")
        instance = this
        _services.notificationsHolder.notifyListenerConnected(activeNotifications ?: emptyArray())
        // media sessions are only listed to notification listeners
        _services.mediaSessionsHolder.startListening()
        _services.storagePermsHolder.notifyPermsMightHaveChanged()
    }

    override fun onListenerDisconnected()
    {
        Log.d(TAG, "onListenerDisconnected")
        instance = null
        _services.notificationsHolder.notifyListenerDisconnected()
        _services.mediaSessionsHolder.stopListening()
        _services.storagePermsHolder.notifyPermsMightHaveChanged()

        // One UI can unbind listeners on its own; ask to be bound again unless access was revoked
        if (checkCanReadNotifications())
            requestRebind(ComponentName(this, BridgeNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?)
    {
        sbn?.let { _services.notificationsHolder.notifyPosted(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?)
    {
        sbn?.let { _services.notificationsHolder.notifyRemoved(it.key) }
    }
}
