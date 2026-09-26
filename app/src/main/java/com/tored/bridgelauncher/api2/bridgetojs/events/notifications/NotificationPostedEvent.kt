package com.tored.bridgelauncher.api2.bridgetojs.events.notifications

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.notifications.SerializableNotification
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A new notification, or a new version of one with the same key. */
@Serializable
class NotificationPostedEvent(
    val notification: SerializableNotification,
) : BridgeEventModel("notificationPosted")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
