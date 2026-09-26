package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.services.notifications.NotificationsHolder
import com.tored.bridgelauncher.services.notifications.SerializableNotification
import com.tored.bridgelauncher.services.notifications.toSerializable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeAPIEndpointNotificationsResponse(
    val notifications: List<SerializableNotification>,
)

class NotificationsEndpoint(private val _notifications: NotificationsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointNotificationsResponse.serializer(),
                BridgeAPIEndpointNotificationsResponse(
                    notifications = _notifications.notifications
                        .sortedByDescending { it.postTime }
                        .map { it.toSerializable() },
                )
            )
        )
    }
}
