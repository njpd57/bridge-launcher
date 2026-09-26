package com.tored.bridgelauncher.api2.bridgetojs.events.quicksettings

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.quicksettings.ScreenBrightness
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class ScreenBrightnessChangedEvent(
    val newValue: ScreenBrightness,
) : BridgeEventModel("screenBrightnessChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
