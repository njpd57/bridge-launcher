package com.tored.bridgelauncher.api2.bridgetojs.events.apps

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The apps of some profile changed (installed, removed, updated, profile paused or resumed...): fetch `getProfileAppsURL()` again. */
@Serializable
class ProfileAppsChangedEvent : BridgeEventModel("profileAppsChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
