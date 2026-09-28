package com.tored.bridgelauncher.api2.bridgetojs.events.files

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.files.SerializableFileSaveResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The outcome of a `requestSaveFile` call: `{ fileName, result: 'saved' | 'cancelled' | 'failed' }`. */
@Serializable
class FileSavedEvent(
    val newValue: SerializableFileSaveResult,
) : BridgeEventModel("fileSaved")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
