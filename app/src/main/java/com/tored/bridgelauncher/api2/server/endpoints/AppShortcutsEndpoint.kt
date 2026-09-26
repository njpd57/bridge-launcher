package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.shortcuts.AppShortcutsHolder
import com.tored.bridgelauncher.services.shortcuts.SerializableAppShortcut
import com.tored.bridgelauncher.services.shortcuts.toSerializable
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeAPIEndpointAppShortcutsResponse(
    val shortcuts: List<SerializableAppShortcut>,
)

/** An app's shortcuts; an empty list when it has none or Bridge can't read them. */
class AppShortcutsEndpoint(private val _shortcuts: AppShortcutsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val packageName = req.url.stringQueryParamOrNull(QUERY_PACKAGE_NAME)
            ?: throw badRequest("No packageName query parameter.")

        val shortcuts = if (CurrentAndroidVersion.supportsAppShortcuts())
            _shortcuts.getShortcuts(packageName).map { it.toSerializable() }
        else
            emptyList()

        return jsonResponse(
            Json.encodeToString(BridgeAPIEndpointAppShortcutsResponse.serializer(), BridgeAPIEndpointAppShortcutsResponse(shortcuts))
        )
    }

    companion object
    {
        const val QUERY_PACKAGE_NAME = "packageName"
    }
}
