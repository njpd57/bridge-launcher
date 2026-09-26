package com.tored.bridgelauncher.api2.server.endpoints

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.core.graphics.drawable.toBitmap
import com.tored.bridgelauncher.api2.server.HTTPStatusCode
import com.tored.bridgelauncher.api2.server.IBridgeServerEndpoint
import com.tored.bridgelauncher.api2.server.badRequest
import com.tored.bridgelauncher.api2.server.notFound
import com.tored.bridgelauncher.api2.server.stringQueryParamOrNull
import com.tored.bridgelauncher.services.shortcuts.AppShortcutsHolder
import com.tored.bridgelauncher.utils.CurrentAndroidVersion
import com.tored.bridgelauncher.utils.EncodingStrings
import com.tored.bridgelauncher.utils.q
import java.io.ByteArrayOutputStream

// used when a drawable has no intrinsic size
private const val FALLBACK_ICON_SIZE_PX = 96

class AppShortcutIconsEndpoint(private val _shortcuts: AppShortcutsHolder) : IBridgeServerEndpoint
{
    override suspend fun handle(req: WebResourceRequest): WebResourceResponse
    {
        val packageName = req.url.stringQueryParamOrNull(QUERY_PACKAGE_NAME)
            ?: throw badRequest("No packageName query parameter.")
        val shortcutId = req.url.stringQueryParamOrNull(QUERY_SHORTCUT_ID)
            ?: throw badRequest("No shortcutId query parameter.")

        if (!CurrentAndroidVersion.supportsAppShortcuts())
            throw notFound("App shortcuts need Android 7.1 or newer.")

        val shortcut = _shortcuts.getShortcut(packageName, shortcutId)
            ?: throw notFound("${q(packageName)} has no shortcut ${q(shortcutId)}.")
        val drawable = _shortcuts.getIcon(shortcut)
            ?: throw notFound("Shortcut ${q(shortcutId)} has no icon.")

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
        const val QUERY_PACKAGE_NAME = "packageName"
        const val QUERY_SHORTCUT_ID = "shortcutId"
    }
}
