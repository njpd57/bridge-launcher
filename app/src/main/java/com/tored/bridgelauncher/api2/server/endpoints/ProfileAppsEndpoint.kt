package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.apps.ProfileAppsHolder
import com.tored.bridgelauncher.services.apps.SerializableProfileApp
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeAPIEndpointProfileAppsResponse(
    val apps: List<SerializableProfileApp>,
)

/** The launchable apps of every profile (personal and work), each with the profile it belongs to. */
class ProfileAppsEndpoint(private val _profileApps: ProfileAppsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointProfileAppsResponse.serializer(),
                BridgeAPIEndpointProfileAppsResponse(_profileApps.getApps().map { it.toSerializable() }),
            )
        )
    }
}
