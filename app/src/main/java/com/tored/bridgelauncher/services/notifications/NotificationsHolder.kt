package com.tored.bridgelauncher.services.notifications

import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "NotificationsHolder"

// progress notifications (downloads, media) can update several times per second
private const val POSTED_EVENT_THROTTLE_MS = 500L

sealed interface NotificationListChangeEvent
{
    data class Posted(val notification: StatusBarNotification) : NotificationListChangeEvent
    data class Removed(val key: String) : NotificationListChangeEvent
}

/** Active notifications, fed by [com.tored.bridgelauncher.services.system.BridgeNotificationListenerService]. */
class NotificationsHolder
{
    private val _scope = CoroutineScope(Dispatchers.Main) + SupervisorJob()

    // read by the server endpoints from WebView threads
    private val _keyToNotification = ConcurrentHashMap<String, StatusBarNotification>()
    val notifications: Collection<StatusBarNotification> get() = _keyToNotification.values

    private val _changeEventFlow = MutableSharedFlow<NotificationListChangeEvent>(extraBufferCapacity = 64)
    val changeEventFlow = _changeEventFlow.asSharedFlow()

    // main thread only
    private val _throttleJobs = mutableMapOf<String, Job>()

    operator fun get(key: String): StatusBarNotification? = _keyToNotification[key]


    // region called by the listener service (on the main thread)

    fun notifyListenerConnected(active: Array<StatusBarNotification>)
    {
        Log.d(TAG, "notifyListenerConnected: ${active.size} active notifications")

        val activeKeys = active.map { it.key }.toSet()
        _keyToNotification.keys
            .filter { it !in activeKeys }
            .forEach { notifyRemoved(it) }

        active.forEach { notifyPosted(it) }
    }

    fun notifyListenerDisconnected()
    {
        Log.d(TAG, "notifyListenerDisconnected")
        _keyToNotification.keys.toList().forEach { notifyRemoved(it) }
    }

    fun notifyPosted(sbn: StatusBarNotification)
    {
        _keyToNotification[sbn.key] = sbn
        emitPostedThrottled(sbn.key)
    }

    fun notifyRemoved(key: String)
    {
        if (_keyToNotification.remove(key) != null)
        {
            _throttleJobs.remove(key)?.cancel()
            _changeEventFlow.tryEmit(NotificationListChangeEvent.Removed(key))
        }
    }

    // endregion


    /** Emits right away, then at most once per [POSTED_EVENT_THROTTLE_MS] with the latest version while updates keep coming. */
    private fun emitPostedThrottled(key: String)
    {
        if (_throttleJobs[key]?.isActive == true)
            return // the running job will pick up the latest version

        _throttleJobs[key] = _scope.launch {
            var emitted: StatusBarNotification? = null
            while (true)
            {
                val latest = _keyToNotification[key]
                if (latest == null || latest === emitted)
                    break

                _changeEventFlow.tryEmit(NotificationListChangeEvent.Posted(latest))
                emitted = latest
                delay(POSTED_EVENT_THROTTLE_MS)
            }
            _throttleJobs.remove(key)
        }
    }
}
