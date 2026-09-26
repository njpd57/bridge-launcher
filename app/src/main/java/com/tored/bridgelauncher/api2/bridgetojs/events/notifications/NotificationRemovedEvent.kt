package com.tored.bridgelauncher.api2.bridgetojs.events.notifications

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class NotificationRemovedEvent(
    val key: String,
) : BridgeEventModel("notificationRemoved")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
