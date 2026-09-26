package com.tored.bridgelauncher.api2.bridgetojs.events.battery

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.battery.SerializableBattery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class BatteryChangedEvent(
    val newValue: SerializableBattery,
) : BridgeEventModel("batteryChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
