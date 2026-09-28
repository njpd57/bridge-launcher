package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.notFound
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.apps.ProfileAppsHolder
import com.tored.bridgelauncher.utils.EncodingStrings
import com.tored.bridgelauncher.utils.q

/** An app's icon in a given profile, with the profile's badge (the work briefcase). */
class ProfileAppIconsEndpoint(private val _profileApps: ProfileAppsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val packageName = req.url.stringQueryParamOrNull(QUERY_PACKAGE_NAME)
            ?: throw badRequest("No packageName query parameter.")
        val userSerial = req.url.stringQueryParamOrNull(QUERY_USER_SERIAL)?.toLongOrNull()
            ?: throw badRequest("No valid userSerial query parameter.")

        val app = _profileApps.find(packageName, userSerial)
            ?: throw notFound("No app ${q(packageName)} in profile $userSerial.")

        // the WebView is responsible for closing this stream
        return WebResourceResponse(
            "image/png",
            EncodingStrings.UTF8,
            HTTPStatusCode.OK.rawValue,
            HTTPStatusCode.OK.name,
            null,
            _profileApps.getIconPng(app).inputStream(),
        )
    }

    companion object
    {
        const val QUERY_PACKAGE_NAME = "packageName"
        const val QUERY_USER_SERIAL = "userSerial"
    }
}
