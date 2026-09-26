package com.tored.bridgelauncher.api2.bridgetojs.events.alarm

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.alarm.SerializableNextAlarm
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
class NextAlarmChangedEvent(
    val newValue: SerializableNextAlarm?,
) : BridgeEventModel("nextAlarmChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
