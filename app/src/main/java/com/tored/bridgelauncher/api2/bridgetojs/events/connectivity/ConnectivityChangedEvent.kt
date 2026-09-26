package com.tored.bridgelauncher.api2.bridgetojs.events.connectivity

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.connectivity.SerializableConnectivity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class ConnectivityChangedEvent(
    val newValue: SerializableConnectivity,
) : BridgeEventModel("connectivityChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
