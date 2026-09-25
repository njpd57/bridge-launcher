package com.tored.bridgelauncher.api2.bridgetojs.events.settings

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.api2.shared.ScreenOrientationStringOptions
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class ScreenOrientationChangedEvent(
    val newValue: ScreenOrientationStringOptions,
) : BridgeEventModel("screenOrientationChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
