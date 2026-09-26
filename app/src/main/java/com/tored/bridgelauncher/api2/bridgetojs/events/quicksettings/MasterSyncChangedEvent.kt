package com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class MasterSyncChangedEvent(
    val newValue: Boolean,
) : BridgeEventModel("masterSyncChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
