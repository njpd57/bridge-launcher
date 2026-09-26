package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.errorResponse
import com.tored.bridgelauncher.api2.server.jsonResponse
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.usage.SerializableAppUsage
import com.tored.bridgelauncher.services.usage.UsageStatsHolder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BridgeAPIEndpointAppUsageResponse(
    val apps: List<SerializableAppUsage>,
)

class AppUsageEndpoint(private val _usage: UsageStatsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val from = req.url.stringQueryParamOrNull(QUERY_FROM)?.toLongOrNull()
            ?: throw badRequest("The from query parameter must be a time in milliseconds.")
        val to = req.url.stringQueryParamOrNull(QUERY_TO)?.toLongOrNull()
            ?: throw badRequest("The to query parameter must be a time in milliseconds.")
        if (to < from)
            throw badRequest("to must not be before from.")

        if (!_usage.canRead)
            return errorResponse(HTTPStatusCode.Forbidden, "Bridge doesn't have usage access.")

        return jsonResponse(
            Json.encodeToString(BridgeAPIEndpointAppUsageResponse.serializer(), BridgeAPIEndpointAppUsageResponse(_usage.getUsage(from, to)))
        )
    }

    companion object
    {
        const val QUERY_FROM = "from"
        const val QUERY_TO = "to"
    }
}
