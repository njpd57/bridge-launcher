package com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.api2.shared.RingerModeStringOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class RingerModeChangedEvent(
    val newValue: RingerModeStringOptions,
) : BridgeEventModel("ringerModeChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
