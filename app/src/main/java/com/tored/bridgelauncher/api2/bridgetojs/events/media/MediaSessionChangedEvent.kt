package com.tored.bridgelauncher.api2.bridgetojs.events.media

import com.tored.bridgelauncher.api2.bridgetojs.BridgeEventModel
import com.tored.bridgelauncher.services.media.SerializableMediaSession
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The current media session changed (track, playback state, position jump), or there is none anymore. */
@Serializable
class MediaSessionChangedEvent(
    val session: SerializableMediaSession?,
) : BridgeEventModel("mediaSessionChanged")
{
    override fun getJson() = Json.encodeToString(serializer(), this)
}
