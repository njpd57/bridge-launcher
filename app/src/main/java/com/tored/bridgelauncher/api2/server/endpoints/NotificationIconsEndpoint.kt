package com.tored.bridgelauncher.api2.server.endpoints

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.graphics.drawable.toBitmap
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.notFound
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.notifications.NotificationsHolder
import com.tored.bridgelauncher.utils.EncodingStrings
import com.tored.bridgelauncher.utils.q
import java.io.ByteArrayOutputStream

// used when a drawable has no intrinsic size (e.g. a plain color)
private const val FALLBACK_ICON_SIZE_PX = 96

class NotificationIconsEndpoint(
    private val _context: Context,
    private val _notifications: NotificationsHolder,
) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val key = req.url.stringQueryParamOrNull(QUERY_KEY)
            ?: throw badRequest("No key query parameter.")

        val sbn = _notifications[key]
            ?: throw notFound("No active notification with key ${q(key)}.")

        val isLarge = req.url.stringQueryParamOrNull(QUERY_LARGE)?.toBooleanStrictOrNull() ?: false

        val icon = if (isLarge) sbn.notification.getLargeIcon() else sbn.notification.smallIcon
        val drawable = icon?.loadDrawable(_context)
            ?: throw notFound("Notification ${q(key)} has no ${if (isLarge) "large" else "small"} icon.")

        val bmp = drawable.toBitmap(
            width = drawable.intrinsicWidth.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX,
            height = drawable.intrinsicHeight.takeIf { it > 0 } ?: FALLBACK_ICON_SIZE_PX,
        )

        // the WebView is responsible for closing this stream
        val readStream = ByteArrayOutputStream().use { writeStream ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, writeStream)
            writeStream.toByteArray().inputStream()
        }

        return WebResourceResponse(
            "image/png",
            EncodingStrings.UTF8,
            HTTPStatusCode.OK.rawValue,
            HTTPStatusCode.OK.name,
            null,
            readStream,
        )
    }

    companion object
    {
        const val QUERY_KEY = "key"
        const val QUERY_LARGE = "large"
    }
}
