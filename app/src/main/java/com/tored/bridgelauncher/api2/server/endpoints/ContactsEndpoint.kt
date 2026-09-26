package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.errorResponse
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.contacts.ContactsHolder
import com.tored.bridgelauncher.services.contacts.SerializableContact
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeAPIEndpointContactsResponse(
    val contacts: List<SerializableContact>,
)

class ContactsEndpoint(private val _contacts: ContactsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        if (!_contacts.canRead)
            return errorResponse(HTTPStatusCode.Forbidden, "Bridge doesn't have the contacts permission.")

        val query = req.url.stringQueryParamOrNull(QUERY_QUERY)
        val starredOnly = req.url.stringQueryParamOrNull(QUERY_STARRED_ONLY)?.toBooleanStrictOrNull() ?: false
        val limit = req.url.stringQueryParamOrNull(QUERY_LIMIT)?.toIntOrNull()?.takeIf { it > 0 }

        return jsonResponse(
            Json.encodeToString(
                BridgeAPIEndpointContactsResponse.serializer(),
                BridgeAPIEndpointContactsResponse(_contacts.getContacts(query, starredOnly, limit)),
            )
        )
    }

    companion object
    {
        const val QUERY_QUERY = "query"
        const val QUERY_STARRED_ONLY = "starredOnly"
        const val QUERY_LIMIT = "limit"
    }
}
