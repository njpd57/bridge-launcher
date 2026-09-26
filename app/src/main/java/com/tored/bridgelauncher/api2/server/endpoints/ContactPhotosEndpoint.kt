package com.tored.bridgelauncher.api2.server.endpoints

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.errorResponse
import com.tored.bridgelauncher.api2.server.notFound
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.contacts.ContactsHolder
import com.tored.bridgelauncher.utils.q

/** A contact's photo as stored (usually JPEG). 404 when it has none. */
class ContactPhotosEndpoint(private val _contacts: ContactsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val lookupKey = req.url.stringQueryParamOrNull(QUERY_LOOKUP_KEY)
            ?: throw badRequest("No lookupKey query parameter.")

        if (!_contacts.canRead)
            return errorResponse(HTTPStatusCode.Forbidden, "Bridge doesn't have the contacts permission.")

        val bytes = _contacts.getPhoto(lookupKey)
            ?: throw notFound("Contact ${q(lookupKey)} has no photo.")

        // the WebView is responsible for closing this stream; it sniffs PNG vs JPEG on its own
        return WebResourceResponse("image/jpeg", null, HTTPStatusCode.OK.rawValue, HTTPStatusCode.OK.name, null, bytes.inputStream())
    }

    companion object
    {
        const val QUERY_LOOKUP_KEY = "lookupKey"
    }
}
