package com.tored.bridgelauncher.api2.bridgetojs.events.perms

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class CanReadNotificationsChangedEvent(
    val newValue: Boolean,
) : BridgeEventModel("canReadNotificationsChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
