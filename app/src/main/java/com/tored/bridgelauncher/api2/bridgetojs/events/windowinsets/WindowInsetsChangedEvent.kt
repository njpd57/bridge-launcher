package com.tored.bridgelauncher.api2.bridgetojs.events.windowinsets

import com.tored.bridgelauncher.api2.bridgetojs.IBridgeEventModel
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsOptions
import com.tored.bridgelauncher.services.windowinsetsholder.WindowInsetsSnapshot
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
class WindowInsetsChangedEvent(
    override val name: String,
    val newValue: WindowInsetsSnapshot,
) : IBridgeEventModel
{
    override fun getJson() = Json.encodeToString(this)

    companion object
    {
        // `option.name` is the Kotlin enum entry's PascalCase name (e.g. "Ime"); the API types promise
        // the event as camelCase with the value in `newValue`, so use `rawValue` ("ime") instead.
        fun fromSnapshot(option: WindowInsetsOptions, snapshot: WindowInsetsSnapshot) = WindowInsetsChangedEvent(
            name = "${option.rawValue}WindowInsetsChanged",
            newValue = snapshot,
        )
    }
}